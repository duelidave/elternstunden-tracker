package de.elternstunden.common.security;

import de.elternstunden.api.SystemApi;
import de.elternstunden.common.web.ApiPaths;
import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Deny by default (SEC-01): only explicitly listed endpoints are reachable, everything else is rejected with a
 * problem response (401 without login, 403 with login). Every operation in the API contract declares its roles in
 * {@code x-roles}; {@code ContractCoverageTest} keeps contract and this configuration in sync.
 */
@Configuration(proxyBeanMethods = false)
public class ApiSecurityConfig {

    @Bean
    @Order(2)
    SecurityFilterChain apiSecurity(HttpSecurity http, ProblemSecurityHandler problemHandler) throws Exception {
        http.authorizeHttpRequests(auth -> auth.dispatcherTypeMatchers(DispatcherType.ERROR)
                        .permitAll()
                        // x-roles: [A]
                        .requestMatchers(HttpMethod.GET, ApiPaths.BASE + SystemApi.PATH_GET_STATUS)
                        .permitAll()
                        .anyRequest()
                        .denyAll())
                // Cookie XSRF-TOKEN, header X-XSRF-TOKEN (Lastenheft 8.1)
                .csrf(csrf -> csrf.spa())
                // Anonymous requests never create a session (SEC-09); login flows follow in M1
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.NEVER))
                .requestCache(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .exceptionHandling(exceptions ->
                        exceptions.authenticationEntryPoint(problemHandler).accessDeniedHandler(problemHandler));
        return http.build();
    }
}
