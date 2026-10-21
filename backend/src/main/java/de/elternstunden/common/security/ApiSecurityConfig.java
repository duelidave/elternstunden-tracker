package de.elternstunden.common.security;

import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;

import de.elternstunden.api.AuthApi;
import de.elternstunden.api.DevApi;
import de.elternstunden.api.MeApi;
import de.elternstunden.api.SystemApi;
import de.elternstunden.auth.repo.AppUserRepository;
import de.elternstunden.common.web.ApiPaths;
import jakarta.servlet.DispatcherType;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;

/**
 * Deny by default (SEC-01): only explicitly listed endpoints are reachable, everything else is rejected with a
 * problem response (401 without login, 403 with login). Every rule names the {@code x-roles} of its operation in the
 * API contract; {@code ContractCoverageTest} keeps contract and this configuration in sync.
 */
@Configuration(proxyBeanMethods = false)
public class ApiSecurityConfig {

    /** SEC-10. Spring adds X-Content-Type-Options, X-Frame-Options and (over HTTPS) HSTS by default. */
    static final String CONTENT_SECURITY_POLICY = "default-src 'self'; script-src 'self'; style-src 'self';"
            + " img-src 'self' data:; font-src 'self'; connect-src 'self'; object-src 'none'; base-uri 'self';"
            + " form-action 'self'; frame-ancestors 'none'";

    static final String PERMISSIONS_POLICY = "camera=(), microphone=(), geolocation=()";

    @Bean
    @Order(2)
    SecurityFilterChain apiSecurity(
            HttpSecurity http,
            ProblemSecurityHandler problemHandler,
            SecurityContextRepository contextRepository,
            CsrfTokenRepository csrfTokenRepository,
            AppUserRepository users,
            SessionLogin sessionLogin,
            Clock clock)
            throws Exception {
        String api = ApiPaths.BASE;
        http.authorizeHttpRequests(auth -> auth.dispatcherTypeMatchers(DispatcherType.ERROR)
                        .permitAll()
                        // x-roles: [A]
                        .requestMatchers(GET, api + SystemApi.PATH_GET_STATUS)
                        .permitAll()
                        .requestMatchers(GET, api + AuthApi.PATH_GET_CAPTCHA_CHALLENGE)
                        .permitAll()
                        .requestMatchers(POST, api + AuthApi.PATH_REGISTER_ACCOUNT)
                        .permitAll()
                        .requestMatchers(POST, api + AuthApi.PATH_REQUEST_MAGIC_LINK)
                        .permitAll()
                        .requestMatchers(POST, api + AuthApi.PATH_REDEEM_MAGIC_LINK)
                        .permitAll()
                        .requestMatchers(POST, api + AuthApi.PATH_LOGIN_MASTER)
                        .permitAll()
                        // x-roles: [A], x-dev-only: the handler exists only in the dev profile, otherwise 404
                        .requestMatchers(POST, api + DevApi.PATH_DEV_LOGIN_AS)
                        .permitAll()
                        // x-roles: [U]
                        .requestMatchers(POST, api + AuthApi.PATH_LOGOUT)
                        .authenticated()
                        .requestMatchers(GET, api + MeApi.PATH_GET_ME)
                        .authenticated()
                        .anyRequest()
                        .denyAll())
                // Cookie XSRF-TOKEN, header X-XSRF-TOKEN (Lastenheft 8.1), shared with SessionLogin
                .csrf(csrf -> csrf.spa().csrfTokenRepository(csrfTokenRepository))
                .securityContext(context -> context.securityContextRepository(contextRepository))
                // Sessions are created only by an explicit login (SessionLogin); anonymous requests never get one
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.NEVER))
                .addFilterBefore(
                        new SessionAccountFilter(users, sessionLogin, contextRepository, clock),
                        AuthorizationFilter.class)
                .headers(headers -> headers.contentSecurityPolicy(csp -> csp.policyDirectives(CONTENT_SECURITY_POLICY))
                        .referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.SAME_ORIGIN))
                        .permissionsPolicyHeader(permissions -> permissions.policy(PERMISSIONS_POLICY)))
                .requestCache(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                // Logout is a regular endpoint (POST /auth/logout) returning 204, not Spring's redirecting filter
                .logout(AbstractHttpConfigurer::disable)
                .exceptionHandling(exceptions ->
                        exceptions.authenticationEntryPoint(problemHandler).accessDeniedHandler(problemHandler));
        return http.build();
    }
}
