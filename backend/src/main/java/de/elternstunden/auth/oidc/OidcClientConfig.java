package de.elternstunden.auth.oidc;

import de.elternstunden.common.config.AppProperties;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.ClientRegistrations;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;

/**
 * OIDC providers from {@code app.oidc.providers} (7.2, D-23): fixed redirect URI from {@code APP_BASE_URL} (SEC-06),
 * scopes {@code openid profile email} only, PKCE required explicitly, issuer always set so {@code iss} is checked.
 * Without explicit endpoints they are discovered from the issuer. More providers need configuration only (OPEN-15).
 */
@Configuration(proxyBeanMethods = false)
public class OidcClientConfig {

    @Bean
    ClientRegistrationRepository clientRegistrationRepository(AppProperties properties) {
        Map<String, ClientRegistration> registrations = new LinkedHashMap<>();
        properties.oidc().providers().forEach((id, provider) -> {
            if (provider.enabled()) {
                registrations.put(id, registration(id, provider, properties.baseUrl()));
            }
        });
        return registrations::get;
    }

    static ClientRegistration registration(String id, AppProperties.OidcProvider provider, URI baseUrl) {
        ClientRegistration.Builder builder = provider.authorizationUri() == null
                ? ClientRegistrations.fromOidcIssuerLocation(provider.issuer()).registrationId(id)
                : ClientRegistration.withRegistrationId(id)
                        .authorizationUri(provider.authorizationUri().toString())
                        .tokenUri(String.valueOf(provider.tokenUri()))
                        .jwkSetUri(String.valueOf(provider.jwkSetUri()))
                        .issuerUri(provider.issuer())
                        .userNameAttributeName("sub");
        String base = baseUrl.toString().replaceAll("/+$", "");
        return builder.clientId(provider.clientId())
                .clientSecret(provider.clientSecret())
                .clientName(provider.name())
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri(base + "/login/oauth2/code/" + id)
                .scope("openid", "profile", "email")
                .clientSettings(ClientRegistration.ClientSettings.builder()
                        .requireProofKey(true)
                        .build())
                .build();
    }
}
