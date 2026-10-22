package de.elternstunden.auth.service;

import de.elternstunden.audit.AuditLog;
import de.elternstunden.auth.domain.AppUser;
import de.elternstunden.common.security.MfaPending;
import de.elternstunden.common.security.SessionLogin;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Clock;
import org.springframework.stereotype.Component;

/**
 * After the first factor (magic link or OIDC): a full login, or for admins with TOTP (or when it is required) only
 * MFA_PENDING (7.2, AC-AUTH-23). The master and the dev role switcher take their own paths.
 */
@Component
public class LoginFlow {

    /** How far the login got. */
    public enum Status {
        LOGGED_IN,
        MFA_REQUIRED,
        MFA_SETUP_REQUIRED
    }

    private final SessionLogin sessionLogin;
    private final AdminTotpService totp;
    private final AuditLog audit;
    private final Clock clock;

    public LoginFlow(SessionLogin sessionLogin, AdminTotpService totp, AuditLog audit, Clock clock) {
        this.sessionLogin = sessionLogin;
        this.totp = totp;
        this.audit = audit;
        this.clock = clock;
    }

    public Status start(AppUser user, HttpServletRequest request, HttpServletResponse response) {
        if (totp.needsSecondFactor(user)) {
            boolean setupRequired = !totp.enabled(user.id());
            sessionLogin.mfaPending(new MfaPending(user.id(), setupRequired, clock.instant()), request, response);
            audit.user(user.id(), "MFA_PENDING", "app_user", user.id());
            return setupRequired ? Status.MFA_SETUP_REQUIRED : Status.MFA_REQUIRED;
        }
        sessionLogin.login(user, request, response);
        return Status.LOGGED_IN;
    }
}
