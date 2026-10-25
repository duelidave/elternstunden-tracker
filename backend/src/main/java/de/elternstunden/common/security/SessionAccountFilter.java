package de.elternstunden.common.security;

import de.elternstunden.auth.domain.AppUser;
import de.elternstunden.auth.domain.UserRole;
import de.elternstunden.auth.domain.UserStatus;
import de.elternstunden.auth.repo.AppUserRepository;
import de.elternstunden.common.config.AppProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Keeps sessions in line with the account (7.2 "Sessions folgen dem Kontozustand", D-20): every request reloads
 * status, role and family by id. A disabled or deleted account or an exceeded absolute session duration ends the
 * session (the request then counts as anonymous and gets 401). Role or family changes apply immediately. The
 * half-finished states OIDC_PENDING (15 minutes) and MFA_PENDING (10 minutes) end after their lifetime.
 */
public class SessionAccountFilter extends OncePerRequestFilter {

    private final AppUserRepository users;
    private final SessionLogin sessionLogin;
    private final SecurityContextRepository contextRepository;
    private final Duration oidcPendingTtl;
    private final Duration mfaPendingTtl;
    private final Clock clock;
    private final Predicate<AppUser> needsSecondFactor;
    private final SecurityContextHolderStrategy holder = SecurityContextHolder.getContextHolderStrategy();

    public SessionAccountFilter(
            AppUserRepository users,
            SessionLogin sessionLogin,
            SecurityContextRepository contextRepository,
            AppProperties properties,
            Clock clock,
            Predicate<AppUser> needsSecondFactor) {
        this.needsSecondFactor = needsSecondFactor;
        this.users = users;
        this.sessionLogin = sessionLogin;
        this.contextRepository = contextRepository;
        this.oidcPendingTtl = properties.oidc().pendingTtl();
        this.mfaPendingTtl = properties.mfa().pendingTtl();
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = holder.getContext().getAuthentication();
        Object principal = authentication == null ? null : authentication.getPrincipal();
        if (principal instanceof AppUserPrincipal user) {
            refresh(user, request, response);
        } else if (principal instanceof PendingOidc pending && expired(pending.startedAt(), oidcPendingTtl)) {
            endSession(request.getSession(false));
        } else if (principal instanceof MfaPending pending && !mfaStillValid(pending)) {
            endSession(request.getSession(false));
        }
        chain.doFilter(request, response);
    }

    /** MFA_PENDING ends after its lifetime and as soon as the account is no longer an active admin (7.2). */
    private boolean mfaStillValid(MfaPending pending) {
        if (expired(pending.startedAt(), mfaPendingTtl)) {
            return false;
        }
        return users.findById(pending.userId())
                .filter(user -> user.status() == UserStatus.ACTIVE && user.role() == UserRole.ADMIN)
                .isPresent();
    }

    private boolean expired(Instant startedAt, Duration ttl) {
        return !clock.instant().isBefore(startedAt.plus(ttl));
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
        // A promotion to admin with a second factor needs a new login with the code (7.2, AC-AUTH-24)
        if (current.role() == UserRole.ADMIN && principal.role() != UserRole.ADMIN && needsSecondFactor.test(current)) {
            endSession(session);
            return;
        }
        AppUserPrincipal updated =
                new AppUserPrincipal(current.id(), current.role(), current.familyId(), principal.authenticatedAt());
        Authentication fresh = SessionLogin.authentication(updated);
        Authentication stored = holder.getContext().getAuthentication();
        // Also sessions from before a change of the authorities get the current ones
        if (!updated.equals(principal)
                || stored == null
                || !List.copyOf(fresh.getAuthorities()).equals(List.copyOf(stored.getAuthorities()))) {
            SecurityContext context = holder.createEmptyContext();
            context.setAuthentication(fresh);
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
