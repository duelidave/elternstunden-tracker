package de.elternstunden.auth.service;

import de.elternstunden.audit.AuditLog;
import de.elternstunden.auth.domain.AppUser;
import de.elternstunden.auth.domain.UserRole;
import de.elternstunden.auth.repo.AppUserRepository;
import de.elternstunden.auth.repo.TotpRepository;
import de.elternstunden.common.config.AppProperties;
import de.elternstunden.common.crypto.Hashing;
import de.elternstunden.common.crypto.SecretCipher;
import de.elternstunden.common.error.ApiException;
import de.elternstunden.common.error.ErrorCode;
import de.elternstunden.common.security.AdminOnly;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Second factor for admins (SEC-05, 7.2, OPEN-09): TOTP with an encrypted secret, 10 one-time recovery codes stored as
 * hashes, ±1 step tolerance and every step valid once per admin. The master's TOTP comes from the configuration only.
 */
@Service
public class AdminTotpService {

    static final int SECRET_BYTES = 20;
    private static final String RECOVERY_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    /** A new secret for the authenticator app. */
    public record Setup(String secret, String otpauthUri) {}

    private final AppUserRepository users;
    private final TotpRepository totp;
    private final SecretCipher cipher;
    private final AuditLog audit;
    private final AppProperties properties;
    private final Clock clock;
    /** Replay protection per admin: the last accepted step (one instance, 7.2). */
    private final Map<UUID, Long> lastUsedStep = new ConcurrentHashMap<>();

    public AdminTotpService(
            AppUserRepository users,
            TotpRepository totp,
            SecretCipher cipher,
            AuditLog audit,
            AppProperties properties,
            Clock clock) {
        this.users = users;
        this.totp = totp;
        this.cipher = cipher;
        this.audit = audit;
        this.properties = properties;
        this.clock = clock;
    }

    public boolean required() {
        // OPEN-09: interim false, switching is configuration only
        return properties.security().adminTotpRequired();
    }

    public boolean enabled(UUID userId) {
        return totp.enabled(userId);
    }

    /** Admins need the second factor if they set it up, or if it is required (7.2). */
    public boolean needsSecondFactor(AppUser user) {
        return user.role() == UserRole.ADMIN && (required() || enabled(user.id()));
    }

    @Transactional
    public Setup setup(UUID userId) {
        AppUser user = admin(userId);
        if (totp.enabled(userId)) {
            throw new ApiException(ErrorCode.TOTP_ALREADY_ENABLED);
        }
        byte[] secret = new byte[SECRET_BYTES];
        RANDOM.nextBytes(secret);
        totp.storePending(userId, cipher.encrypt(secret), clock.instant());
        String base32 = Totp.encodeBase32(secret);
        String label = URLEncoder.encode("Elternstunden:" + user.email(), StandardCharsets.UTF_8)
                .replace("+", "%20");
        return new Setup(
                base32,
                "otpauth://totp/" + label + "?secret=" + base32
                        + "&issuer=Elternstunden&algorithm=SHA1&digits=6&period=30");
    }

    /** Activates the pending secret and returns the recovery codes, visible only now. */
    @Transactional
    public List<String> confirm(UUID userId, String code) {
        admin(userId);
        TotpRepository.StoredSecret stored = totp.secret(userId)
                .filter(secret -> !secret.enabled())
                .orElseThrow(() -> new ApiException(ErrorCode.TOTP_SETUP_MISSING));
        if (!acceptTotp(userId, cipher.decrypt(stored.encrypted()), code)) {
            throw new ApiException(ErrorCode.MFA_INVALID);
        }
        Instant now = clock.instant();
        totp.enable(userId, now);
        List<String> codes = new ArrayList<>();
        for (int i = 0; i < properties.mfa().recoveryCodes(); i++) {
            codes.add(recoveryCode());
        }
        totp.replaceRecoveryCodes(
                userId, codes.stream().map(AdminTotpService::hash).toList(), now);
        audit.user(userId, "TOTP_ENABLED", "app_user", userId);
        return codes;
    }

    /** The second step of an admin login: a TOTP code or an unused recovery code. */
    @Transactional
    public boolean verify(UUID userId, String code) {
        Optional<TotpRepository.StoredSecret> stored = totp.secret(userId).filter(TotpRepository.StoredSecret::enabled);
        if (stored.isEmpty()) {
            return false;
        }
        if (code.length() == 6 && code.chars().allMatch(Character::isDigit)) {
            return acceptTotp(userId, cipher.decrypt(stored.get().encrypted()), code);
        }
        boolean used = totp.useRecoveryCode(userId, hash(code), clock.instant());
        if (used) {
            audit.user(userId, "MFA_RECOVERY_CODE_USED", "app_user", userId);
        }
        return used;
    }

    /** Only with a fresh login and only if TOTP is not required (7.2). */
    @Transactional
    public void disable(UUID userId, Instant authenticatedAt) {
        admin(userId);
        if (clock.instant().isAfter(authenticatedAt.plus(properties.oidc().reauthWindow()))) {
            throw new ApiException(ErrorCode.REAUTH_REQUIRED);
        }
        if (required()) {
            throw new ApiException(ErrorCode.TOTP_REQUIRED);
        }
        totp.clear(userId, clock.instant());
        lastUsedStep.remove(userId);
        audit.user(userId, "TOTP_DISABLED", "app_user", userId);
    }

    /** An admin resets the TOTP of another admin, e.g. after a lost phone (7.2). */
    @Transactional
    @AdminOnly
    public void reset(UUID actor, UUID userId) {
        AppUser user = users.findByIdForUpdate(userId).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        if (user.isMaster()) {
            throw new ApiException(ErrorCode.ACCOUNT_MANAGED_BY_CONFIG);
        }
        if (actor.equals(userId)) {
            throw new ApiException(ErrorCode.OWN_ACCOUNT);
        }
        totp.clear(userId, clock.instant());
        lastUsedStep.remove(userId);
        audit.user(actor, "TOTP_RESET", "app_user", userId);
    }

    private boolean acceptTotp(UUID userId, byte[] secret, String code) {
        long step = Totp.matchingStep(secret, code, clock.instant());
        if (step < 0) {
            return false;
        }
        // Every step counts once per admin; of parallel requests with the same code only one wins
        AtomicBoolean accepted = new AtomicBoolean();
        lastUsedStep.compute(userId, (id, previous) -> {
            if (previous == null || step > previous) {
                accepted.set(true);
                return step;
            }
            return previous;
        });
        return accepted.get();
    }

    private AppUser admin(UUID userId) {
        AppUser user = users.findById(userId).orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED));
        if (user.isMaster()) {
            throw new ApiException(ErrorCode.ACCOUNT_MANAGED_BY_CONFIG);
        }
        if (user.role() != UserRole.ADMIN) {
            throw new ApiException(ErrorCode.FORBIDDEN);
        }
        return user;
    }

    /** 10 characters without look-alikes, shown as XXXXX-XXXXX. */
    private static String recoveryCode() {
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            if (i == 5) {
                code.append('-');
            }
            code.append(RECOVERY_ALPHABET.charAt(RANDOM.nextInt(RECOVERY_ALPHABET.length())));
        }
        return code.toString();
    }

    /** Case, spaces and dashes do not matter when entering a recovery code. */
    static String hash(String code) {
        return Hashing.sha256Hex(code.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT));
    }
}
