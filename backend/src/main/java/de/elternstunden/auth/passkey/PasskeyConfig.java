package de.elternstunden.auth.passkey;

import de.elternstunden.common.config.AppProperties;
import java.net.URI;
import java.util.Set;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.security.web.webauthn.api.AuthenticatorSelectionCriteria;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialRpEntity;
import org.springframework.security.web.webauthn.api.ResidentKeyRequirement;
import org.springframework.security.web.webauthn.api.UserVerificationRequirement;
import org.springframework.security.web.webauthn.management.JdbcPublicKeyCredentialUserEntityRepository;
import org.springframework.security.web.webauthn.management.JdbcUserCredentialRepository;
import org.springframework.security.web.webauthn.management.PublicKeyCredentialUserEntityRepository;
import org.springframework.security.web.webauthn.management.UserCredentialRepository;
import org.springframework.security.web.webauthn.management.WebAuthnRelyingPartyOperations;
import org.springframework.security.web.webauthn.management.Webauthn4JRelyingPartyOperations;

/**
 * Passkeys (7.2, D-30) with the building blocks of Spring Security: JDBC storage and the relying party. Our own
 * endpoints use them, not Spring's WebAuthn filters. RP-ID is the host of {@code APP_BASE_URL}, the only allowed
 * origin is {@code APP_BASE_URL} itself (SEC-06); passkeys are discoverable and need user verification.
 */
@Configuration(proxyBeanMethods = false)
public class PasskeyConfig {

    @Bean
    PublicKeyCredentialUserEntityRepository passkeyUserEntities(JdbcOperations jdbc) {
        return new JdbcPublicKeyCredentialUserEntityRepository(jdbc);
    }

    @Bean
    UserCredentialRepository passkeyCredentials(JdbcOperations jdbc) {
        return new JdbcUserCredentialRepository(jdbc);
    }

    @Bean
    WebAuthnRelyingPartyOperations relyingParty(
            PublicKeyCredentialUserEntityRepository userEntities,
            UserCredentialRepository credentials,
            AppProperties properties) {
        URI base = properties.baseUrl();
        String origin = base.getScheme() + "://" + base.getHost() + (base.getPort() == -1 ? "" : ":" + base.getPort());
        Webauthn4JRelyingPartyOperations relyingParty = new Webauthn4JRelyingPartyOperations(
                userEntities,
                credentials,
                PublicKeyCredentialRpEntity.builder()
                        .id(base.getHost())
                        .name("Elternstunden")
                        .build(),
                Set.of(origin));
        relyingParty.setCustomizeCreationOptions(
                options -> options.authenticatorSelection(AuthenticatorSelectionCriteria.builder()
                        .residentKey(ResidentKeyRequirement.REQUIRED)
                        .userVerification(UserVerificationRequirement.REQUIRED)
                        .build()));
        relyingParty.setCustomizeRequestOptions(
                options -> options.userVerification(UserVerificationRequirement.REQUIRED));
        return relyingParty;
    }
}
