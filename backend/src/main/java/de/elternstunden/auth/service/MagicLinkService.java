package de.elternstunden.auth.service;

import de.elternstunden.audit.AuditLog;
import de.elternstunden.auth.domain.AppUser;
import de.elternstunden.auth.domain.UserStatus;
import de.elternstunden.auth.repo.AppUserRepository;
import de.elternstunden.auth.repo.UserTokenRepository;
import de.elternstunden.auth.repo.UserTokenRepository.LockedToken;
import de.elternstunden.common.crypto.Hashing;
import de.elternstunden.common.error.ApiException;
import de.elternstunden.common.error.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Redeeming a magic link (7.2 "Einlösen", SEC-17). Token and account rows are locked, so parallel redemptions of
 * the same token yield exactly one success (AC-AUTH-14) and the cleanup job cannot delete an account that is just
 * being activated (AC-AUTH-21). All failures look the same to the client (SEC-16).
 */
@Service
public class MagicLinkService {

    private final UserTokenRepository tokens;
    private final AppUserRepository users;
    private final AuditLog audit;
    private final Clock clock;

    public MagicLinkService(UserTokenRepository tokens, AppUserRepository users, AuditLog audit, Clock clock) {
        this.tokens = tokens;
        this.users = users;
        this.audit = audit;
        this.clock = clock;
    }

    /**
     * Checks and consumes the token; returns the (now active) account for the session login.
     *
     * @param browserBindingHash hash of the {@code auth_binding} cookie of this browser, empty if it has none
     */
    @Transactional
    public AppUser redeem(String token, boolean confirmOtherDevice, Optional<String> browserBindingHash) {
        Instant now = clock.instant();
        LockedToken locked =
                tokens.findByHashForUpdate(Hashing.sha256Hex(token)).orElseThrow(MagicLinkService::invalid);
        if (locked.usedAt() != null || !now.isBefore(locked.expiresAt())) {
            throw invalid();
        }
        AppUser user = users.findByIdForUpdate(locked.userId()).orElseThrow(MagicLinkService::invalid);
        if (user.isMaster() || user.status() == UserStatus.DISABLED) {
            throw invalid();
        }

        // OPEN-21 interim: soft binding. Another device must confirm first; the token stays valid until then.
        boolean sameBrowser =
                browserBindingHash.map(locked.bindingHash()::equals).orElse(false);
        if (!sameBrowser && !confirmOtherDevice) {
            throw new ApiException(
                    ErrorCode.OTHER_DEVICE_CONFIRMATION_REQUIRED, Map.of("maskedEmail", mask(user.email())));
        }

        tokens.markUsed(locked.id(), now);
        if (user.status() == UserStatus.PENDING_VERIFICATION) {
            users.activate(user.id(), now);
            // M2: apply the claim if it was created in this browser, otherwise discard it (7.1.4 step 7)
            audit.user(user.id(), "USER_ACTIVATED", "app_user", user.id());
        }
        users.touchLogin(user.id(), now);
        audit.write(
                user.id(),
                AuditLog.ActorKind.USER,
                "MAGIC_LINK_REDEEMED",
                "app_user",
                user.id(),
                Map.of("sameBrowser", sameBrowser));
        return users.findById(user.id()).orElseThrow(MagicLinkService::invalid);
    }

    /** {@code anna@example.org} becomes {@code a***@example.org}. */
    static String mask(String email) {
        int at = email.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(at);
    }

    private static ApiException invalid() {
        return new ApiException(ErrorCode.LINK_INVALID);
    }
}
