package de.elternstunden.common.security;

import static org.springframework.http.HttpMethod.DELETE;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.PATCH;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpMethod.PUT;

import de.elternstunden.api.AuthApi;
import de.elternstunden.api.ChildrenApi;
import de.elternstunden.api.DashboardApi;
import de.elternstunden.api.DevApi;
import de.elternstunden.api.FamiliesApi;
import de.elternstunden.api.MeApi;
import de.elternstunden.api.SchoolYearsApi;
import de.elternstunden.api.SystemApi;
import de.elternstunden.api.UsersApi;
import de.elternstunden.auth.domain.UserRole;
import de.elternstunden.auth.repo.AppUserRepository;
import de.elternstunden.common.web.ApiPaths;
import jakarta.servlet.DispatcherType;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
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
@EnableMethodSecurity
public class ApiSecurityConfig {

    /** SEC-10. Spring adds X-Content-Type-Options, X-Frame-Options and (over HTTPS) HSTS by default. */
    static final String CONTENT_SECURITY_POLICY = "default-src 'self'; script-src 'self'; style-src 'self';"
            + " img-src 'self' data:; font-src 'self'; connect-src 'self'; object-src 'none'; base-uri 'self';"
            + " form-action 'self'; frame-ancestors 'none'";

    private static final String ADMIN = UserRole.ADMIN.authority();

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
                        // x-roles: [U]; only unassigned family members, checked in the service (7.1.7)
                        .requestMatchers(PUT, api + MeApi.PATH_UPDATE_MY_CLAIM)
                        .authenticated()
                        // x-roles: [AD] (MASTER_ADMIN holds the ADMIN authority too)
                        .requestMatchers(GET, api + DashboardApi.PATH_GET_ADMIN_DASHBOARD)
                        .hasAuthority(ADMIN)
                        .requestMatchers(GET, api + SchoolYearsApi.PATH_LIST_SCHOOL_YEARS)
                        .hasAuthority(ADMIN)
                        .requestMatchers(POST, api + SchoolYearsApi.PATH_CREATE_SCHOOL_YEAR)
                        .hasAuthority(ADMIN)
                        .requestMatchers(PATCH, api + SchoolYearsApi.PATH_UPDATE_SCHOOL_YEAR)
                        .hasAuthority(ADMIN)
                        .requestMatchers(POST, api + SchoolYearsApi.PATH_CLOSE_SCHOOL_YEAR)
                        .hasAuthority(ADMIN)
                        .requestMatchers(POST, api + SchoolYearsApi.PATH_REOPEN_SCHOOL_YEAR)
                        .hasAuthority(ADMIN)
                        .requestMatchers(POST, api + SchoolYearsApi.PATH_COPY_PREVIOUS_ENROLLMENTS)
                        .hasAuthority(ADMIN)
                        .requestMatchers(GET, api + FamiliesApi.PATH_LIST_FAMILIES)
                        .hasAuthority(ADMIN)
                        .requestMatchers(GET, api + FamiliesApi.PATH_GET_FAMILY)
                        .hasAuthority(ADMIN)
                        .requestMatchers(PATCH, api + FamiliesApi.PATH_UPDATE_FAMILY)
                        .hasAuthority(ADMIN)
                        .requestMatchers(GET, api + ChildrenApi.PATH_LIST_CHILDREN)
                        .hasAuthority(ADMIN)
                        .requestMatchers(POST, api + ChildrenApi.PATH_CREATE_CHILD)
                        .hasAuthority(ADMIN)
                        .requestMatchers(POST, api + ChildrenApi.PATH_IMPORT_CHILDREN)
                        .hasAuthority(ADMIN)
                        .requestMatchers(GET, api + ChildrenApi.PATH_GET_CHILD)
                        .hasAuthority(ADMIN)
                        .requestMatchers(PATCH, api + ChildrenApi.PATH_UPDATE_CHILD)
                        .hasAuthority(ADMIN)
                        .requestMatchers(PUT, api + ChildrenApi.PATH_ENROLL_CHILD)
                        .hasAuthority(ADMIN)
                        .requestMatchers(DELETE, api + ChildrenApi.PATH_UNENROLL_CHILD)
                        .hasAuthority(ADMIN)
                        .requestMatchers(GET, api + UsersApi.PATH_LIST_USERS)
                        .hasAuthority(ADMIN)
                        .requestMatchers(GET, api + UsersApi.PATH_GET_USER)
                        .hasAuthority(ADMIN)
                        .requestMatchers(PUT, api + UsersApi.PATH_ASSIGN_USER_TO_FAMILY)
                        .hasAuthority(ADMIN)
                        .requestMatchers(DELETE, api + UsersApi.PATH_REMOVE_USER_FROM_FAMILY)
                        .hasAuthority(ADMIN)
                        .requestMatchers(PUT, api + UsersApi.PATH_SET_USER_ROLE)
                        .hasAuthority(ADMIN)
                        .requestMatchers(POST, api + UsersApi.PATH_DISABLE_USER)
                        .hasAuthority(ADMIN)
                        .requestMatchers(POST, api + UsersApi.PATH_ENABLE_USER)
                        .hasAuthority(ADMIN)
                        .requestMatchers(POST, api + UsersApi.PATH_TERMINATE_USER_SESSIONS)
                        .hasAuthority(ADMIN)
                        .requestMatchers(POST, api + UsersApi.PATH_ANONYMIZE_USER)
                        .hasAuthority(ADMIN)
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
