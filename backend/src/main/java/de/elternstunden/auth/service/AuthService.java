package de.elternstunden.auth.service;

import de.elternstunden.api.model.MeDto;
import de.elternstunden.audit.AuditLog;
import de.elternstunden.auth.domain.AppUser;
import de.elternstunden.auth.oidc.OidcLoginService;
import de.elternstunden.auth.repo.AppUserRepository;
import de.elternstunden.common.config.AppProperties;
import de.elternstunden.common.error.ApiException;
import de.elternstunden.common.error.ErrorCode;
import de.elternstunden.common.ratelimit.RateLimiter;
import de.elternstunden.common.security.CurrentUser;
import de.elternstunden.common.security.MfaPending;
import de.elternstunden.common.security.SessionLogin;
import de.elternstunden.common.web.ClientIp;
import de.elternstunden.family.domain.ChildData;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/** Orchestrates the session-related auth use cases; controllers stay free of logic (4.4). */
@Service
public class AuthService {

    private final MagicLinkService magicLinks;
    private final MasterLoginService masterLogin;
    private final SessionLogin sessionLogin;
    private final BindingCookie bindingCookie;
    private final RateLimiter rateLimiter;
    private final AuditLog audit;
    private final MeService meService;
    private final LoginFlow loginFlow;
    private final AdminTotpService totp;
    private final AppUserRepository users;
    private final OidcLoginService oidc;
    private final AppProperties.RateLimits limits;

    public AuthService(
            MagicLinkService magicLinks,
            MasterLoginService masterLogin,
            SessionLogin sessionLogin,
            BindingCookie bindingCookie,
            RateLimiter rateLimiter,
            AuditLog audit,
            MeService meService,
            LoginFlow loginFlow,
            AdminTotpService totp,
            AppUserRepository users,
            OidcLoginService oidc,
            AppProperties properties) {
        this.magicLinks = magicLinks;
        this.masterLogin = masterLogin;
        this.sessionLogin = sessionLogin;
        this.bindingCookie = bindingCookie;
        this.rateLimiter = rateLimiter;
        this.audit = audit;
        this.meService = meService;
        this.loginFlow = loginFlow;
        this.totp = totp;
        this.users = users;
        this.oidc = oidc;
        this.limits = properties.rateLimits();
    }

    public LoginFlow.Status redeemMagicLink(
            String token, boolean confirmOtherDevice, HttpServletRequest request, HttpServletResponse response) {
        rateLimiter.acquireOrThrow("redeem-ip", ClientIp.rateLimitKey(request), limits.redeemPerIp());
        AppUser user;
        try {
            user = magicLinks.redeem(token, confirmOtherDevice, bindingCookie.currentHash(request));
        } catch (ApiException ex) {
            if (ex.code() == ErrorCode.LINK_INVALID) {
                audit.system("MAGIC_LINK_INVALID", "user_token", null, Map.of());
            }
            throw ex;
        }
        // Admins with TOTP get only MFA_PENDING here (7.2, AC-AUTH-23)
        return loginFlow.start(user, request, response);
    }

    /** Second step of an admin login: TOTP or recovery code (7.2). */
    public LoginFlow.Status verifyMfa(String code, HttpServletRequest request, HttpServletResponse response) {
        MfaPending pending = CurrentUser.mfaPending();
        rateLimiter.acquireOrThrow("mfa-ip", ClientIp.rateLimitKey(request), limits.mfaPerIp());
        // Per account as well, so changing IPs does not help guessing the code (9.1)
        rateLimiter.acquireOrThrow("mfa-user", pending.userId().toString(), limits.mfaPerUser());
        if (pending.setupRequired() || !totp.verify(pending.userId(), code.strip())) {
            audit.user(pending.userId(), "MFA_FAILED", "app_user", pending.userId());
            if (tooManyFailures(request)) {
                // The half login ends; the next try needs the first factor again
                request.getSession().invalidate();
                throw new ApiException(ErrorCode.UNAUTHORIZED);
            }
            throw new ApiException(ErrorCode.MFA_INVALID);
        }
        AppUser user = users.findById(pending.userId()).orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED));
        sessionLogin.login(user, request, response);
        audit.user(user.id(), "MFA_VERIFIED", "app_user", user.id());
        return LoginFlow.Status.LOGGED_IN;
    }

    static final int MFA_FAILURES_PER_PENDING = 5;
    private static final String MFA_FAILURES = "mfa-failures";

    /** Counts a wrong code in this MFA_PENDING session; true from the fifth one on (9.1). */
    private static boolean tooManyFailures(HttpServletRequest request) {
        HttpSession session = request.getSession();
        int failures = session.getAttribute(MFA_FAILURES) instanceof Integer count ? count + 1 : 1;
        session.setAttribute(MFA_FAILURES, failures);
        return failures >= MFA_FAILURES_PER_PENDING;
    }

    /** Confirms a TOTP setup; in the MFA_PENDING state this completes the login (7.2). */
    public List<String> confirmTotp(String code, HttpServletRequest request, HttpServletResponse response) {
        boolean pending = CurrentUser.isMfaPending();
        UUID userId = pending
                ? CurrentUser.mfaPending().userId()
                : CurrentUser.principal().userId();
        rateLimiter.acquireOrThrow("mfa-ip", ClientIp.rateLimitKey(request), limits.mfaPerIp());
        rateLimiter.acquireOrThrow("mfa-user", userId.toString(), limits.mfaPerUser());
        List<String> codes = totp.confirm(userId, code.strip());
        if (pending) {
            AppUser user = users.findById(userId).orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED));
            sessionLogin.login(user, request, response);
        }
        return codes;
    }

    /** "Noch ein Schritt" (7.1.5): account, identity and claim, then a normal login. */
    public LoginFlow.Status completeOidc(
            String displayName, ChildData child, HttpServletRequest request, HttpServletResponse response) {
        AppUser user = oidc.complete(CurrentUser.pendingOidc(), displayName, child);
        return loginFlow.start(user, request, response);
    }

    public MeDto loginMaster(
            String email, String password, String totp, HttpServletRequest request, HttpServletResponse response) {
        AppUser master = masterLogin.login(email, password, totp, ClientIp.rateLimitKey(request));
        sessionLogin.login(master, request, response);
        return toMe(master);
    }

    /** Dev role switcher (D-26): same login path, without mail and TOTP. */
    public MeDto loginAs(AppUser user, HttpServletRequest request, HttpServletResponse response) {
        sessionLogin.login(user, request, response);
        audit.user(user.id(), "DEV_LOGIN_AS", "app_user", user.id());
        return toMe(user);
    }

    public void logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        SecurityContextHolder.clearContext();
        if (session != null) {
            session.invalidate();
        }
    }

    private MeDto toMe(AppUser user) {
        return meService.toMe(user);
    }
}
