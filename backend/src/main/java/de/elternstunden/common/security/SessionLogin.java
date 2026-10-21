package de.elternstunden.common.security;

import de.elternstunden.auth.domain.AppUser;
import de.elternstunden.auth.domain.UserRole;
import de.elternstunden.common.config.AppProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.time.Clock;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.stereotype.Component;

/**
 * The one login code path for magic link, OIDC (M3), master login and the dev role switcher (7.2 "Anmeldung
 * technisch"): new session id (fixation protection), new CSRF token, context saved explicitly, idle timeout per role.
 */
@Component
public class SessionLogin {

    private final SecurityContextRepository contextRepository;
    private final CsrfAuthenticationStrategy csrfStrategy;
    private final AppProperties.Sessions sessions;
    private final Clock clock;
    private final SecurityContextHolderStrategy holder = SecurityContextHolder.getContextHolderStrategy();

    public SessionLogin(
            SecurityContextRepository contextRepository,
            CsrfTokenRepository csrfTokenRepository,
            AppProperties properties,
            Clock clock) {
        this.contextRepository = contextRepository;
        this.csrfStrategy = new CsrfAuthenticationStrategy(csrfTokenRepository);
        this.sessions = properties.sessions();
        this.clock = clock;
    }

    public void login(AppUser user, HttpServletRequest request, HttpServletResponse response) {
        AppUserPrincipal principal = new AppUserPrincipal(user.id(), user.role(), user.familyId(), clock.instant());
        Authentication authentication = authentication(principal);

        // Session fixation: an existing session gets a new id, otherwise a fresh session is created
        if (request.getSession(false) != null) {
            request.changeSessionId();
        }
        HttpSession session = request.getSession(true);
        session.setMaxInactiveInterval((int) timeouts(user.role()).idle().toSeconds());

        csrfStrategy.onAuthentication(authentication, request, response);
        // Render the new CSRF token now, so the login response already carries the new cookie
        if (request.getAttribute(CsrfToken.class.getName()) instanceof CsrfToken token) {
            token.getToken();
        }

        SecurityContext context = holder.createEmptyContext();
        context.setAuthentication(authentication);
        holder.setContext(context);
        contextRepository.saveContext(context, request, response);
    }

    public AppProperties.Timeouts timeouts(UserRole role) {
        return switch (role) {
            case FAMILY_MEMBER -> sessions.familyMember();
            case ADMIN -> sessions.admin();
            case MASTER_ADMIN -> sessions.masterAdmin();
        };
    }

    static Authentication authentication(AppUserPrincipal principal) {
        List<SimpleGrantedAuthority> authorities = principal.role() == UserRole.MASTER_ADMIN
                ? List.of(
                        new SimpleGrantedAuthority(UserRole.MASTER_ADMIN.authority()),
                        new SimpleGrantedAuthority(UserRole.ADMIN.authority()))
                : List.of(new SimpleGrantedAuthority(principal.role().authority()));
        return UsernamePasswordAuthenticationToken.authenticated(principal, null, authorities);
    }
}
