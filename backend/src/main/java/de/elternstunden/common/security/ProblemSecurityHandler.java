package de.elternstunden.common.security;

import de.elternstunden.common.error.ApiException;
import de.elternstunden.common.error.ErrorCode;
import de.elternstunden.common.web.ApiPaths;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Hands security errors to the MVC exception handling, so 401 and 403 (including CSRF) are rendered by
 * {@code GlobalExceptionHandler} as problem responses. Never redirects to a login page.
 */
@Component
public class ProblemSecurityHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final HandlerExceptionResolver resolver;

    public ProblemSecurityHandler(@Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver) {
        this.resolver = resolver;
    }

    @Override
    public void commence(
            HttpServletRequest request, HttpServletResponse response, AuthenticationException authException) {
        resolver.resolveException(request, response, null, authException);
    }

    /**
     * A half-finished login (OIDC_PENDING, MFA_PENDING) counts as not signed in everywhere else: 401, not 403
     * (AC-OIDC-10, AC-AUTH-23). CSRF errors stay 403.
     */
    @Override
    public void handle(
            HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Object principal = authentication == null ? null : authentication.getPrincipal();
        boolean halfSignedIn = principal instanceof PendingOidc || principal instanceof MfaPending;
        if (halfSignedIn && !(accessDeniedException instanceof CsrfException)) {
            resolver.resolveException(
                    request, response, null, new InsufficientAuthenticationException("Login not finished"));
            return;
        }
        // The family endpoints (x-roles F) tell unassigned accounts why, so the client shows the waiting screen
        boolean unassigned = principal instanceof AppUserPrincipal user && user.familyId() == null;
        if (unassigned
                && !(accessDeniedException instanceof CsrfException)
                && request.getRequestURI().startsWith(ApiPaths.BASE + "/me/family/")) {
            resolver.resolveException(request, response, null, new ApiException(ErrorCode.FAMILY_ASSIGNMENT_PENDING));
            return;
        }
        resolver.resolveException(request, response, null, accessDeniedException);
    }
}
