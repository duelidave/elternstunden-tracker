package de.elternstunden.auth.service;

import de.elternstunden.audit.AuditLog;
import de.elternstunden.auth.domain.AppUser;
import de.elternstunden.auth.domain.UserStatus;
import de.elternstunden.auth.repo.AppUserRepository;
import de.elternstunden.common.config.AppProperties;
import de.elternstunden.common.crypto.Hashing;
import de.elternstunden.common.error.ApiException;
import de.elternstunden.common.error.ErrorCode;
import de.elternstunden.common.mail.MailService;
import de.elternstunden.common.ratelimit.RateLimiter;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Master login with password and TOTP (D-22, 7.2 "Master-Login"). Every failure looks the same (SEC-16); the
 * password is always checked, also for unknown e-mails, so timing does not tell them apart. A TOTP step is valid only
 * once (replay protection in memory, one instance). No account lockout, so the emergency access cannot be blocked;
 * many failures trigger an alarm mail to the master instead.
 */
@Service
public class MasterLoginService {

    private static final Logger log = LoggerFactory.getLogger(MasterLoginService.class);

    private final AppProperties.Master master;
    private final AppProperties.RateLimits limits;
    private final AppUserRepository users;
    private final RateLimiter rateLimiter;
    private final AuditLog audit;
    private final MailService mailService;
    private final AuthMails mails;
    private final Clock clock;
    private final Argon2PasswordEncoder encoder = MasterPassword.encoder();
    private final String dummyHash;
    private final AtomicLong lastUsedStep = new AtomicLong(-1);
    private final AtomicReference<Instant> lastAlarm = new AtomicReference<>();

    public MasterLoginService(
            AppProperties properties,
            AppUserRepository users,
            RateLimiter rateLimiter,
            AuditLog audit,
            MailService mailService,
            AuthMails mails,
            Clock clock) {
        this.master = properties.master();
        this.limits = properties.rateLimits();
        this.users = users;
        this.rateLimiter = rateLimiter;
        this.audit = audit;
        this.mailService = mailService;
        this.mails = mails;
        this.clock = clock;
        this.dummyHash = encoder.encode(Hashing.randomToken(24));
    }

    /** Returns the master account or throws {@code 401 LOGIN_FAILED} / {@code 429}. */
    public AppUser login(String email, String password, String totp, String clientIpKey) {
        rateLimiter.acquireOrThrow("master-login-ip", clientIpKey, limits.masterLoginPerIp());
        boolean configured = master.configured() && master.passwordHash() != null && master.totpSecret() != null;

        boolean emailMatches =
                configured && Hashing.normalizeEmail(email).equals(Hashing.normalizeEmail(master.email()));
        // Always verify a hash, so unknown e-mails take as long as wrong passwords
        boolean passwordMatches = encoder.matches(password, configured ? master.passwordHash() : dummyHash);
        long step = configured ? Totp.matchingStep(Totp.decodeBase32(master.totpSecret()), totp, clock.instant()) : -1;

        if (emailMatches && passwordMatches && step >= 0 && consumeStep(step)) {
            Optional<AppUser> account = users.findMaster().filter(user -> user.status() == UserStatus.ACTIVE);
            if (account.isPresent()) {
                users.touchLogin(account.get().id(), clock.instant());
                audit.user(
                        account.get().id(),
                        "MASTER_LOGIN_SUCCEEDED",
                        "app_user",
                        account.get().id());
                return account.get();
            }
        }
        failed();
        throw new ApiException(ErrorCode.LOGIN_FAILED);
    }

    /** A TOTP step counts once; parallel logins with the same code cannot both win. */
    private boolean consumeStep(long step) {
        while (true) {
            long previous = lastUsedStep.get();
            if (step <= previous) {
                return false;
            }
            if (lastUsedStep.compareAndSet(previous, step)) {
                return true;
            }
        }
    }

    private void failed() {
        audit.system("MASTER_LOGIN_FAILED", "app_user", null, Map.of());
        if (rateLimiter.tryAcquire("master-failures", "all", limits.masterFailureAlarm())) {
            return;
        }
        Instant now = clock.instant();
        Instant previous = lastAlarm.get();
        boolean due = previous == null
                || !now.isBefore(previous.plus(limits.masterFailureAlarm().window()));
        if (due && lastAlarm.compareAndSet(previous, now) && master.configured()) {
            try {
                mailService.send(mails.masterLoginAlarm(
                        master.email(), limits.masterFailureAlarm().max()));
            } catch (MailException ex) {
                log.warn(
                        "Master login alarm mail could not be sent ({})",
                        ex.getClass().getSimpleName());
            }
        }
    }
}
