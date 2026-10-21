package de.elternstunden.common.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;

/** Shared by the filter chain and {@link SessionLogin}, so explicit logins and filters see the same state. */
@Configuration(proxyBeanMethods = false)
public class SecurityBeans {

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    /** Cookie {@code XSRF-TOKEN}, header {@code X-XSRF-TOKEN} (Lastenheft 8.1). */
    @Bean
    CsrfTokenRepository csrfTokenRepository() {
        return CookieCsrfTokenRepository.withHttpOnlyFalse();
    }
}
