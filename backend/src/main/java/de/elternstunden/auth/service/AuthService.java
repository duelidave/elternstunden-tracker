package de.elternstunden.auth.service;

import de.elternstunden.api.model.MeDto;
import de.elternstunden.audit.AuditLog;
import de.elternstunden.auth.domain.AppUser;
import de.elternstunden.auth.repo.AppUserRepository;
import de.elternstunden.common.config.AppProperties;
import de.elternstunden.common.error.ApiException;
import de.elternstunden.common.error.ErrorCode;
import de.elternstunden.common.ratelimit.RateLimiter;
import de.elternstunden.common.security.AppUserPrincipal;
import de.elternstunden.common.security.SessionLogin;
import de.elternstunden.common.web.ClientIp;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/** Orchestrates the session-related auth use cases; controllers stay free of logic (4.4). */
@Service
public class AuthService {

    private final MagicLinkService magicLinks;
    private final MasterLoginService masterLogin;
    private final SessionLogin sessionLogin;
    private final BindingCookie bindingCookie;
    private final AppUserRepository users;
    private final RateLimiter rateLimiter;
    private final AuditLog audit;
    private final AppProperties.RateLimits limits;

    public AuthService(
            MagicLinkService magicLinks,
            MasterLoginService masterLogin,
            SessionLogin sessionLogin,
            BindingCookie bindingCookie,
            AppUserRepository users,
            RateLimiter rateLimiter,
            AuditLog audit,
            AppProperties properties) {
        this.magicLinks = magicLinks;
        this.masterLogin = masterLogin;
        this.sessionLogin = sessionLogin;
        this.bindingCookie = bindingCookie;
        this.users = users;
        this.rateLimiter = rateLimiter;
        this.audit = audit;
        this.limits = properties.rateLimits();
    }

    public MeDto redeemMagicLink(
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
        sessionLogin.login(user, request, response);
        return toMe(user);
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

    public MeDto me() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AppUserPrincipal principal)) {
            throw new ApiException(ErrorCode.UNAUTHORIZED);
        }
        return users.findById(principal.userId())
                .map(AuthService::toMe)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED));
    }

    static MeDto toMe(AppUser user) {
        return new MeDto(
                user.id(),
                user.displayName(),
                user.email(),
                MeDto.RoleEnum.fromValue(user.role().name()),
                user.familyId() != null);
    }
}
