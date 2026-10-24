package de.elternstunden.auth.passkey;

import java.util.Map;
import org.springframework.security.web.webauthn.api.AuthenticatorAssertionResponse;
import org.springframework.security.web.webauthn.api.AuthenticatorAttestationResponse;
import org.springframework.security.web.webauthn.api.PublicKeyCredential;
import org.springframework.security.web.webauthn.jackson.WebauthnJacksonModule;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * WebAuthn data between the API (the JSON of WebAuthn Level 3, passed through unchanged, D-25) and Spring's types.
 * An own mapper with Spring's WebAuthn module; the application's mapper stays as it is.
 */
@Component
class PasskeyJson {

    private final JsonMapper json =
            JsonMapper.builder().addModule(new WebauthnJacksonModule()).build();

    Map<String, Object> toMap(Object options) {
        return json.convertValue(options, new TypeReference<Map<String, Object>>() {});
    }

    PublicKeyCredential<AuthenticatorAttestationResponse> attestation(Map<String, Object> credential) {
        return json.convertValue(
                credential, new TypeReference<PublicKeyCredential<AuthenticatorAttestationResponse>>() {});
    }

    PublicKeyCredential<AuthenticatorAssertionResponse> assertion(Map<String, Object> credential) {
        return json.convertValue(
                credential, new TypeReference<PublicKeyCredential<AuthenticatorAssertionResponse>>() {});
    }
}
