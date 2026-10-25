package de.elternstunden.auth.oidc;

import de.elternstunden.common.config.AppProperties;
import de.elternstunden.common.ratelimit.RateLimiter;
import de.elternstunden.common.web.ClientIp;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 9.1: at most 30 provider entries and 30 callbacks per IP and minute; beyond that back to the login page. The
 * session an anonymous entry creates for state and PKCE ends after 10 minutes.
 */
public class OidcCallbackRateLimitFilter extends OncePerRequestFilter {

    public static final String CALLBACK_PATH = "/login/oauth2/code/";
    public static final String ENTRY_PATH = "/oauth2/authorization/";
    static final int ENTRY_SESSION_SECONDS = 600;

    private final RateLimiter rateLimiter;
    private final AppProperties.Limit limit;
    private final String baseUrl;

    public OidcCallbackRateLimitFilter(RateLimiter rateLimiter, AppProperties properties) {
        this.rateLimiter = rateLimiter;
        this.limit = properties.rateLimits().oidcCallbackPerIp();
        this.baseUrl = properties.baseUrl().toString().replaceAll("/+$", "");
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith(CALLBACK_PATH) && !path.startsWith(ENTRY_PATH);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        boolean entry = request.getRequestURI().startsWith(ENTRY_PATH);
        if (!rateLimiter.tryAcquire(
                entry ? "oidc-entry-ip" : "oidc-callback-ip", ClientIp.rateLimitKey(request), limit)) {
            response.sendRedirect(baseUrl + "/login?error=RATE_LIMITED");
            return;
        }
        chain.doFilter(request, response);
        HttpSession session = request.getSession(false);
        if (entry && session != null && session.isNew()) {
            session.setMaxInactiveInterval(ENTRY_SESSION_SECONDS);
        }
    }
}
