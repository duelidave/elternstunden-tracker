package de.elternstunden.auth.oidc;

import de.elternstunden.common.config.AppProperties;
import de.elternstunden.common.ratelimit.RateLimiter;
import de.elternstunden.common.web.ClientIp;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;

/** 9.1: at most 30 provider callbacks per IP and minute; beyond that back to the login page. */
public class OidcCallbackRateLimitFilter extends OncePerRequestFilter {

    public static final String CALLBACK_PATH = "/login/oauth2/code/";

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
        return !request.getRequestURI().startsWith(CALLBACK_PATH);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!rateLimiter.tryAcquire("oidc-callback-ip", ClientIp.rateLimitKey(request), limit)) {
            response.sendRedirect(baseUrl + "/login?error=RATE_LIMITED");
            return;
        }
        chain.doFilter(request, response);
    }
}
