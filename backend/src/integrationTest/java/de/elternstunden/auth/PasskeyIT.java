package de.elternstunden.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.webauthn4j.converter.util.ObjectConverter;
import com.webauthn4j.data.AuthenticatorAssertionResponse;
import com.webauthn4j.data.AuthenticatorAttestationResponse;
import com.webauthn4j.data.PublicKeyCredential;
import com.webauthn4j.data.PublicKeyCredentialCreationOptions;
import com.webauthn4j.data.PublicKeyCredentialRequestOptions;
import com.webauthn4j.data.client.Origin;
import com.webauthn4j.test.authenticator.webauthn.PackedAuthenticator;
import com.webauthn4j.test.authenticator.webauthn.WebAuthnAuthenticatorAdaptor;
import com.webauthn4j.test.client.ClientPlatform;
import de.elternstunden.auth.passkey.PasskeyService;
import de.elternstunden.auth.service.Totp;
import de.elternstunden.support.ApiClient;
import de.elternstunden.support.IntegrationTest;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * Passkeys (7.2, D-30, AC-PASSKEY). A webauthn4j emulator stands in for the device; it answers for the origin of
 * {@code APP_BASE_URL} like a browser on the app would.
 */
class PasskeyIT extends IntegrationTest {

    private static final String ORIGIN = "http://localhost:5173";
    private static final ObjectConverter WEBAUTHN = new ObjectConverter();
    private static final Base64.Encoder BASE64URL = Base64.getUrlEncoder().withoutPadding();

    /** A device with its own authenticator, so passkeys of different tests do not mix. */
    private static ClientPlatform device(String origin) {
        return new ClientPlatform(new Origin(origin), new WebAuthnAuthenticatorAdaptor(new PackedAuthenticator()));
    }

    /** AC-PASSKEY-01, AC-PASSKEY-02: create a passkey in the profile, then sign in without an e-mail. */
    @Test
    void passkeySignsInWithoutEmail() {
        String email = uniqueEmail();
        insertUser(email, "FAMILY_MEMBER", "ACTIVE");
        ClientPlatform device = device(ORIGIN);
        ApiClient.Response created = register(signedIn(email), device, "Mein Handy");

        assertThat(created.status()).isEqualTo(201);
        assertThat(created.json().get("label").asString()).isEqualTo("Mein Handy");

        ApiClient browser = browser();
        ApiClient.Response login = login(browser, device);
        assertThat(login.status()).isEqualTo(200);
        assertThat(login.json().get("status").asString()).isEqualTo("LOGGED_IN");
        assertThat(browser.get("/api/v1/me").json().get("email").asString()).isEqualTo(email);
        JsonNode listed = browser.get("/api/v1/me/passkeys").json();
        assertThat(listed).hasSize(1);
        assertThat(listed.get(0).has("lastUsedAt")).isTrue();
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM audit_log WHERE action = 'PASSKEY_LOGIN' AND actor_user_id = ?",
                        Integer.class,
                        userId(email)))
                .isEqualTo(1);
    }

    /** AC-PASSKEY-01: fresh login only, not for the master, own challenge only, at most 10. */
    @Test
    void creatingNeedsAFreshLoginAndHasLimits() {
        String email = uniqueEmail();
        insertUser(email, "FAMILY_MEMBER", "ACTIVE");
        ApiClient member = signedIn(email);
        ClientPlatform device = device(ORIGIN);

        // Someone else's challenge is no use
        JsonNode foreign =
                signedInAs("FAMILY_MEMBER").post("/api/v1/me/passkeys/options").json();
        ApiClient.Response stolen = member.post("/api/v1/me/passkeys", answer(foreign, device));
        assertThat(stolen.status()).isEqualTo(400);
        assertThat(stolen.json().get("code").asString()).isEqualTo("PASSKEY_INVALID");

        for (int i = 0; i < 10; i++) {
            assertThat(register(member, device(ORIGIN), "Passkey " + i).status())
                    .isEqualTo(201);
        }
        ApiClient.Response eleventh = member.post("/api/v1/me/passkeys/options");
        assertThat(eleventh.status()).isEqualTo(409);
        assertThat(eleventh.json().get("code").asString()).isEqualTo("PASSKEY_LIMIT");

        assertThat(signedInMaster().post("/api/v1/me/passkeys/options").status())
                .isEqualTo(403);

        ApiClient late = signedInAs("FAMILY_MEMBER");
        clock.advance(Duration.ofMinutes(6));
        ApiClient.Response reauth = late.post("/api/v1/me/passkeys/options");
        assertThat(reauth.status()).isEqualTo(403);
        assertThat(reauth.json().get("code").asString()).isEqualTo("REAUTH_REQUIRED");
    }

    /** AC-PASSKEY-02: a passkey is the first factor only; admins with TOTP still need the code. */
    @Test
    void adminsStillNeedTheSecondFactor() {
        String email = uniqueEmail();
        insertUser(email, "ADMIN", "ACTIVE");
        ApiClient admin = signedIn(email);
        String secret = admin.post("/api/v1/me/totp/setup").json().get("secret").asString();
        admin.post(
                "/api/v1/me/totp/confirm",
                Map.of(
                        "code",
                        Totp.codeAt(Totp.decodeBase32(secret), clock.instant().getEpochSecond() / Totp.STEP_SECONDS)));
        ClientPlatform device = device(ORIGIN);
        assertThat(register(signedInAfterMfa(email, secret), device, "Laptop").status())
                .isEqualTo(201);

        ApiClient.Response login = login(browser(), device);

        assertThat(login.json().get("status").asString()).isEqualTo("MFA_REQUIRED");
    }

    /** AC-PASSKEY-03: wrong origin, reused or expired challenge, removed passkey: 401 and no session. */
    @Test
    void invalidAssertionsAreRejected() {
        String email = uniqueEmail();
        insertUser(email, "FAMILY_MEMBER", "ACTIVE");
        ClientPlatform device = device(ORIGIN);
        ApiClient member = signedIn(email);
        register(member, device, "Handy");

        // Another site asks the same authenticator: the origin does not match
        ClientPlatform phishing =
                new ClientPlatform(new Origin("https://evil.example"), device.getAuthenticatorAdaptor());
        assertFailed(login(browser(), phishing));

        ApiClient browser = browser();
        JsonNode options = browser.post("/api/v1/auth/passkey/options").json();
        Map<String, Object> answer = assertion(options, device);
        assertThat(browser.post("/api/v1/auth/passkey/login", answer).status()).isEqualTo(200);
        assertFailed(browser().post("/api/v1/auth/passkey/login", answer));

        ApiClient waiting = browser();
        JsonNode expiring = waiting.post("/api/v1/auth/passkey/options").json();
        clock.advance(Duration.ofMinutes(6));
        assertFailed(waiting.post("/api/v1/auth/passkey/login", assertion(expiring, device)));
        clock.reset();

        String id = member.get("/api/v1/me/passkeys").json().get(0).get("id").asString();
        ApiClient fresh = signedIn(email);
        assertThat(fresh.delete("/api/v1/me/passkeys/" + id).status()).isEqualTo(204);
        assertFailed(login(browser(), device));
    }

    /** AC-PASSKEY-04: disabled accounts do not sign in, anonymizing removes the passkeys. */
    @Test
    void accountStateDecides() {
        String email = uniqueEmail();
        insertUser(email, "FAMILY_MEMBER", "ACTIVE");
        ClientPlatform device = device(ORIGIN);
        register(signedIn(email), device, "Handy");
        UUID user = userId(email);

        jdbc.update("UPDATE app_user SET status = 'DISABLED' WHERE id = ?", user);
        assertFailed(login(browser(), device));

        jdbc.update("UPDATE app_user SET status = 'ACTIVE' WHERE id = ?", user);
        assertThat(signedInAs("ADMIN")
                        .post("/api/v1/admin/users/" + user + "/anonymize")
                        .status())
                .isEqualTo(200);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM user_credentials WHERE user_entity_user_id = ?",
                        Integer.class,
                        PasskeyService.userEntityId(user).toBase64UrlString()))
                .isZero();
        assertFailed(login(browser(), device));
    }

    /** AC-PASSKEY-05: only own passkeys are listed and removed (SEC-08). */
    @Test
    void ownPasskeysOnly() {
        String owner = uniqueEmail();
        insertUser(owner, "FAMILY_MEMBER", "ACTIVE");
        ApiClient ownerBrowser = signedIn(owner);
        register(ownerBrowser, device(ORIGIN), "Handy");
        String id =
                ownerBrowser.get("/api/v1/me/passkeys").json().get(0).get("id").asString();
        ApiClient other = signedInAs("FAMILY_MEMBER");

        assertThat(other.get("/api/v1/me/passkeys").json()).isEmpty();
        assertThat(other.delete("/api/v1/me/passkeys/" + id).status()).isEqualTo(404);
        assertThat(ownerBrowser.get("/api/v1/me/passkeys").json()).hasSize(1);
    }

    private ApiClient signedInAfterMfa(String email, String secret) {
        clock.advance(Duration.ofSeconds(Totp.STEP_SECONDS));
        ApiClient browser = browser();
        requestMagicLink(browser, email);
        redeem(browser, latestToken(email));
        browser.post(
                "/api/v1/auth/mfa/verify",
                Map.of(
                        "code",
                        Totp.codeAt(Totp.decodeBase32(secret), clock.instant().getEpochSecond() / Totp.STEP_SECONDS)));
        return browser;
    }

    private static void assertFailed(ApiClient.Response response) {
        assertThat(response.status()).isEqualTo(401);
        assertThat(response.json().get("code").asString()).isEqualTo("LOGIN_FAILED");
    }

    private static ApiClient.Response register(ApiClient browser, ClientPlatform device, String label) {
        JsonNode options = browser.post("/api/v1/me/passkeys/options").json();
        Map<String, Object> body = answer(options, device);
        body.put("label", label);
        return browser.post("/api/v1/me/passkeys", body);
    }

    /** What the SPA sends after navigator.credentials.create() (WebAuthn Level 3 JSON). */
    private static Map<String, Object> answer(JsonNode challenge, ClientPlatform device) {
        PublicKeyCredentialCreationOptions options = WEBAUTHN.getJsonMapper()
                .readValue(challenge.get("options").toString(), PublicKeyCredentialCreationOptions.class);
        PublicKeyCredential<AuthenticatorAttestationResponse, ?> credential = device.create(options);
        Map<String, Object> response = Map.of(
                "clientDataJSON",
                        BASE64URL.encodeToString(credential.getResponse().getClientDataJSON()),
                "attestationObject",
                        BASE64URL.encodeToString(credential.getResponse().getAttestationObject()),
                "transports", List.of("internal"));
        Map<String, Object> body = new HashMap<>();
        body.put("challengeId", challenge.get("challengeId").asString());
        body.put("credential", credential(credential.getRawId(), response));
        return body;
    }

    private static ApiClient.Response login(ApiClient browser, ClientPlatform device) {
        JsonNode options = browser.post("/api/v1/auth/passkey/options").json();
        return browser.post("/api/v1/auth/passkey/login", assertion(options, device));
    }

    /** What the SPA sends after navigator.credentials.get(). */
    private static Map<String, Object> assertion(JsonNode challenge, ClientPlatform device) {
        PublicKeyCredentialRequestOptions options = WEBAUTHN.getJsonMapper()
                .readValue(challenge.get("options").toString(), PublicKeyCredentialRequestOptions.class);
        PublicKeyCredential<AuthenticatorAssertionResponse, ?> credential = device.get(options);
        AuthenticatorAssertionResponse answer = credential.getResponse();
        Map<String, Object> response = new HashMap<>();
        response.put("clientDataJSON", BASE64URL.encodeToString(answer.getClientDataJSON()));
        response.put("authenticatorData", BASE64URL.encodeToString(answer.getAuthenticatorData()));
        response.put("signature", BASE64URL.encodeToString(answer.getSignature()));
        if (answer.getUserHandle() != null) {
            response.put("userHandle", BASE64URL.encodeToString(answer.getUserHandle()));
        }
        Map<String, Object> body = new HashMap<>();
        body.put("challengeId", challenge.get("challengeId").asString());
        body.put("credential", credential(credential.getRawId(), response));
        return body;
    }

    private static Map<String, Object> credential(byte[] rawId, Map<String, Object> response) {
        String id = BASE64URL.encodeToString(rawId);
        return Map.of(
                "id",
                id,
                "rawId",
                id,
                "type",
                "public-key",
                "response",
                response,
                "clientExtensionResults",
                Map.of(),
                "authenticatorAttachment",
                "platform");
    }
}
