package de.elternstunden.common.security;

import de.elternstunden.auth.domain.AppUser;
import de.elternstunden.auth.domain.UserStatus;
import de.elternstunden.auth.repo.AppUserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Keeps sessions in line with the account (7.2 "Sessions folgen dem Kontozustand", D-20): every request reloads
 * status, role and family by id. A disabled or deleted account or an exceeded absolute session duration ends the
 * session (the request then counts as anonymous and gets 401). Role or family changes apply immediately.
 */
public class SessionAccountFilter extends OncePerRequestFilter {

    private final AppUserRepository users;
    private final SessionLogin sessionLogin;
    private final SecurityContextRepository contextRepository;
    private final Clock clock;
    private final SecurityContextHolderStrategy holder = SecurityContextHolder.getContextHolderStrategy();

    public SessionAccountFilter(
            AppUserRepository users,
            SessionLogin sessionLogin,
            SecurityContextRepository contextRepository,
            Clock clock) {
        this.users = users;
        this.sessionLogin = sessionLogin;
        this.contextRepository = contextRepository;
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = holder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AppUserPrincipal principal) {
            refresh(principal, request, response);
        }
        chain.doFilter(request, response);
    }

    private void refresh(AppUserPrincipal principal, HttpServletRequest request, HttpServletResponse response) {
        HttpSession session = request.getSession(false);
        Optional<AppUser> user = users.findById(principal.userId());
        if (session == null
                || user.isEmpty()
                || user.get().status() != UserStatus.ACTIVE
                || absoluteDurationExceeded(principal, user.get())) {
            endSession(session);
            return;
        }
        AppUser current = user.get();
        if (current.role() != principal.role() || !Objects.equals(current.familyId(), principal.familyId())) {
            AppUserPrincipal updated =
                    new AppUserPrincipal(current.id(), current.role(), current.familyId(), principal.authenticatedAt());
            SecurityContext context = holder.createEmptyContext();
            context.setAuthentication(SessionLogin.authentication(updated));
            holder.setContext(context);
            contextRepository.saveContext(context, request, response);
            session.setMaxInactiveInterval(
                    (int) sessionLogin.timeouts(current.role()).idle().toSeconds());
        }
    }

    /** Measured from the login (injected clock), not from the servlet session creation time. */
    private boolean absoluteDurationExceeded(AppUserPrincipal principal, AppUser user) {
        Instant loginAt = principal.authenticatedAt();
        return !clock.instant()
                .isBefore(loginAt.plus(sessionLogin.timeouts(user.role()).absolute()));
    }

    private void endSession(HttpSession session) {
        holder.clearContext();
        if (session != null) {
            session.invalidate();
        }
    }
}
