package de.elternstunden.auth;

import static org.assertj.core.api.Assertions.assertThat;

import de.elternstunden.support.ApiClient;
import de.elternstunden.support.FakeOidcProvider.Login;
import de.elternstunden.support.FakeOidcProvider.Tamper;
import de.elternstunden.support.IntegrationTest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** Login and registration with an OIDC provider (7.1.5, 7.2, AC-OIDC). */
class OidcIT extends IntegrationTest {

    private static String gmail() {
        return "user-" + UUID.randomUUID() + "@gmail.com";
    }

    private static Login login(String email) {
        return Login.of("sub-" + UUID.randomUUID(), email);
    }

    /** AC-OIDC-01: no account before "Noch ein Schritt"; then account, identity and claim, with a match the family. */
    @Test
    void newUsersCompleteTheRegistrationWithTheChildData() {
        UUID family = insertFamilyWithChild("Olga", "Oidc", "2018-06-06");
        String email = gmail();
        ApiClient browser = browser();

        assertThat(oidcLogin(browser, login(email))).isEqualTo("/register/claim");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM app_user WHERE email = ?", Integer.class, email))
                .isZero();
        ApiClient.Response pending = browser.get("/api/v1/auth/oidc/pending");
        assertThat(pending.json().get("email").asString()).isEqualTo(email);
        assertThat(pending.json().get("displayName").asString()).isEqualTo("Test Person");

        ApiClient.Response completed = browser.post(
                "/api/v1/auth/oidc/complete",
                Map.of(
                        "displayName",
                        "Olga Mutter",
                        "child",
                        Map.of("firstName", "Olga", "lastName", "Oidc", "birthDate", "2018-06-06")));

        assertThat(completed.json().get("status").asString()).isEqualTo("LOGGED_IN");
        assertThat(familyOf(email)).isEqualTo(family);
        assertThat(jdbc.queryForObject("SELECT status::text FROM app_user WHERE email = ?", String.class, email))
                .isEqualTo("ACTIVE");
        assertThat(identities(email)).isEqualTo(1);
        assertThat(browser.get("/api/v1/me")
                        .json()
                        .get("identities")
                        .get(0)
                        .get("provider")
                        .asString())
                .isEqualTo("google");
    }

    /** AC-OIDC-02 */
    @Test
    void unverifiedAddressesAreRefused() {
        String email = gmail();

        assertThat(oidcLogin(browser(), login(email).unverified())).isEqualTo("/login?error=OIDC_EMAIL_UNVERIFIED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM app_user WHERE email = ?", Integer.class, email))
                .isZero();
    }

    /** AC-OIDC-03: an authoritative provider links an active account; the magic link keeps working. */
    @Test
    void activeAccountsAreLinkedByAnAuthoritativeAddress() {
        String email = gmail();
        insertUser(email, "FAMILY_MEMBER", "ACTIVE");
        ApiClient browser = browser();

        assertThat(oidcLogin(browser, login(email))).isEqualTo("/");

        assertThat(browser.get("/api/v1/me").json().get("email").asString()).isEqualTo(email);
        assertThat(identities(email)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM audit_log WHERE action = 'IDENTITY_LINKED' AND actor_user_id = ?",
                        Integer.class,
                        userId(email)))
                .isEqualTo(1);
        assertThat(signedIn(email).get("/api/v1/me").status()).isEqualTo(200);
    }

    /** AC-OIDC-04: an unconfirmed account is activated, its claim discarded, the user waits on /pending. */
    @Test
    void unconfirmedAccountsAreActivatedWithoutTheirClaim() {
        insertFamilyWithChild("Paula", "Pending", "2017-07-07");
        String email = gmail();
        register(browser(), email, "Paula", "Pending", "2017-07-07");
        processAuthRequests();
        ApiClient other = browser();

        assertThat(oidcLogin(other, login(email))).isEqualTo("/");

        ApiClient.Response me = other.get("/api/v1/me");
        assertThat(me.json().get("assignedToFamily").asBoolean()).isFalse();
        assertThat(me.json().has("pendingClaim")).isFalse();
        assertThat(jdbc.queryForMap(
                        "SELECT status::text AS status, email_verified_at IS NOT NULL AS verified FROM app_user"
                                + " WHERE email = ?",
                        email))
                .containsEntry("status", "ACTIVE")
                .containsEntry("verified", true);
    }

    /** AC-OIDC-05 and AC-MASTER-03 */
    @Test
    void theMasterCannotUseAProvider() {
        assertThat(oidcLogin(browser(), login(MASTER_EMAIL))).isEqualTo("/login?error=OIDC_MASTER_ONLY");
        assertThat(identities(MASTER_EMAIL)).isZero();
    }

    /** AC-OIDC-06: manipulated tokens are refused; nothing is created. */
    @ParameterizedTest
    @EnumSource(
            value = Tamper.class,
            names = {"WRONG_NONCE", "WRONG_AUDIENCE", "WRONG_ISSUER", "EXPIRED", "WRONG_SIGNATURE"})
    void manipulatedTokensAreRefused(Tamper tamper) {
        String email = gmail();
        ApiClient browser = browser();

        assertThat(oidcLogin(browser, login(email).tamper(tamper))).isEqualTo("/login?error=OIDC_FAILED");
        assertThat(browser.get("/api/v1/auth/oidc/pending").status()).isEqualTo(401);
    }

    /** AC-OIDC-06: a wrong state is refused. */
    @Test
    void aWrongStateIsRefused() {
        ApiClient browser = browser();
        OIDC.nextLogin(login(gmail()));
        String callback = OIDC.authorize(
                browser.get("/oauth2/authorization/google").header("Location").orElseThrow());

        String tampered = callback.replaceAll("state=[^&]+", "state=forged");

        assertThat(spaTarget(browser.get(tampered))).isEqualTo("/login?error=OIDC_FAILED");
    }

    /** AC-OIDC-09: PKCE with S256; a code from another flow (other verifier) is refused. */
    @Test
    void pkceIsRequired() {
        ApiClient first = browser();
        ApiClient second = browser();
        OIDC.nextLogin(login(gmail()));
        String firstCallback = OIDC.authorize(
                first.get("/oauth2/authorization/google").header("Location").orElseThrow());
        assertThat(OIDC.lastAuthorizationRequest())
                .containsEntry("code_challenge_method", "S256")
                .containsKey("code_challenge")
                .containsEntry("scope", "openid profile email");
        String secondCallback = OIDC.authorize(
                second.get("/oauth2/authorization/google").header("Location").orElseThrow());

        // the code of the first flow with the state (and so the verifier) of the second
        String firstCode = firstCallback.replaceAll(".*code=([^&]+).*", "$1");
        String swapped = secondCallback.replaceAll("code=[^&]+", "code=" + firstCode);

        assertThat(spaTarget(second.get(swapped))).isEqualTo("/login?error=OIDC_FAILED");
    }

    /** AC-OIDC-07 and INV-11: the only identity can be removed; the magic link stays. */
    @Test
    void identitiesCanBeRemoved() {
        String email = gmail();
        insertUser(email, "FAMILY_MEMBER", "ACTIVE");
        ApiClient browser = browser();
        oidcLogin(browser, login(email));
        String identity = browser.get("/api/v1/me")
                .json()
                .get("identities")
                .get(0)
                .get("id")
                .asString();

        assertThat(browser.delete("/api/v1/me/identities/" + identity).status()).isEqualTo(204);

        assertThat(identities(email)).isZero();
        assertThat(signedIn(email).get("/api/v1/me").status()).isEqualTo(200);
    }

    /** SEC-08: someone else's identity is not found. */
    @Test
    void foreignIdentitiesAreNotFound() {
        String owner = gmail();
        insertUser(owner, "FAMILY_MEMBER", "ACTIVE");
        ApiClient ownerBrowser = browser();
        oidcLogin(ownerBrowser, login(owner));
        String identity = ownerBrowser
                .get("/api/v1/me")
                .json()
                .get("identities")
                .get(0)
                .get("id")
                .asString();
        ApiClient intruder = signedInAs("FAMILY_MEMBER");

        assertThat(intruder.delete("/api/v1/me/identities/" + identity).status())
                .isEqualTo(404);
        assertThat(identities(owner)).isEqualTo(1);
    }

    /** AC-OIDC-08: neither the database nor the session keeps provider tokens or provider objects. */
    @Test
    void noProviderTokensAreStored() {
        String email = gmail();
        insertUser(email, "FAMILY_MEMBER", "ACTIVE");
        oidcLogin(browser(), login(email));

        List<byte[]> attributes = jdbc.queryForList(
                "SELECT a.attribute_bytes FROM spring_session_attributes a"
                        + " JOIN spring_session s ON s.primary_id = a.session_primary_id WHERE s.principal_name = ?",
                byte[].class,
                userId(email).toString());

        assertThat(attributes).isNotEmpty();
        for (byte[] attribute : attributes) {
            String content = new String(attribute, StandardCharsets.ISO_8859_1);
            assertThat(content)
                    .doesNotContain(
                            "OAuth2AuthorizedClient",
                            "OidcUser",
                            "OAuth2AuthenticationToken",
                            "fake-access-token",
                            "OAuth2AuthorizationRequest");
        }
    }

    /** AC-OIDC-08 also for refused and half-finished logins: the session holds no provider objects. */
    @Test
    void refusedAndPendingLoginsLeaveNoProviderObjectsInTheSession() {
        ApiClient refused = browser();
        oidcLogin(refused, login(gmail()).unverified());
        ApiClient pending = browser();
        oidcLogin(pending, login(gmail()));

        assertThat(sessionAttributes(pending))
                .as("the pending state lives in the session")
                .isNotEmpty();
        for (ApiClient browser : List.of(refused, pending)) {
            for (byte[] attribute : sessionAttributes(browser)) {
                assertThat(new String(attribute, StandardCharsets.ISO_8859_1))
                        .doesNotContain("OidcUser", "OAuth2AuthenticationToken", "fake-access-token");
            }
        }
        assertThat(refused.get("/api/v1/me").status()).isEqualTo(401);
    }

    private List<byte[]> sessionAttributes(ApiClient browser) {
        String sessionId = browser.cookie("__Host-SESSION")
                .map(value -> new String(java.util.Base64.getDecoder().decode(value), StandardCharsets.UTF_8))
                .orElse("none");
        return jdbc.queryForList(
                "SELECT a.attribute_bytes FROM spring_session_attributes a"
                        + " JOIN spring_session s ON s.primary_id = a.session_primary_id WHERE s.session_id = ?",
                byte[].class,
                sessionId);
    }

    /** AC-OIDC-10: the pending state reaches only its own endpoints and ends after 15 minutes. */
    @Test
    void thePendingStateHasNoRights() {
        ApiClient browser = browser();
        oidcLogin(browser, login(gmail()));

        assertThat(browser.get("/api/v1/me").status()).isEqualTo(401);
        assertThat(browser.get("/api/v1/admin/dashboard").status()).isEqualTo(401);
        assertThat(browser.put("/api/v1/me/claim", Map.of("firstName", "A", "lastName", "B", "birthDate", "2018-01-01"))
                        .status())
                .isEqualTo(401);
        assertThat(browser.get("/api/v1/auth/oidc/pending").status()).isEqualTo(200);

        clock.advance(Duration.ofMinutes(16));
        assertThat(browser.get("/api/v1/auth/oidc/pending").status()).isEqualTo(401);
    }

    /** AC-OIDC-11 */
    @Test
    void disabledAccountsAreRefusedByTheirIdentity() {
        String email = gmail();
        insertUser(email, "FAMILY_MEMBER", "ACTIVE");
        Login account = login(email);
        oidcLogin(browser(), account);
        jdbc.update("UPDATE app_user SET status = 'DISABLED' WHERE email = ?", email);

        assertThat(oidcLogin(browser(), account)).isEqualTo("/login?error=OIDC_REJECTED");
    }

    /** AC-OIDC-12: without @gmail.com and without hd, an existing account is not linked by e-mail. */
    @Test
    void nonAuthoritativeAddressesAreNotLinked() {
        String email = uniqueEmail();
        insertUser(email, "FAMILY_MEMBER", "ACTIVE");

        assertThat(oidcLogin(browser(), login(email))).isEqualTo("/login?error=OIDC_NOT_AUTHORITATIVE");
        assertThat(identities(email)).isZero();

        // A Google Workspace account (hd) is authoritative
        assertThat(oidcLogin(browser(), login(email).hostedDomain("example.test")))
                .isEqualTo("/");
        assertThat(identities(email)).isEqualTo(1);
    }

    /** AC-OIDC-13 and 7.2 "Profil": linking needs a fresh login; then it works without the e-mail rule. */
    @Test
    void linkingNeedsAFreshLogin() {
        String email = uniqueEmail();
        insertUser(email, "FAMILY_MEMBER", "ACTIVE");
        ApiClient browser = signedIn(email);

        ApiClient.Response started = browser.post("/api/v1/me/identities/google/link");
        assertThat(started.json().get("redirectUrl").asString()).isEqualTo("/oauth2/authorization/google");
        assertThat(oidcLogin(browser, login("other-" + UUID.randomUUID() + "@example.test")))
                .isEqualTo("/profile?linked=google");
        assertThat(identities(email)).isEqualTo(1);
        assertThat(browser.get("/api/v1/me").json().get("email").asString())
                .as("still the same account")
                .isEqualTo(email);

        clock.advance(Duration.ofMinutes(6));
        ApiClient.Response late = browser.post("/api/v1/me/identities/google/link");
        assertThat(late.status()).isEqualTo(403);
        assertThat(late.json().get("code").asString()).isEqualTo("REAUTH_REQUIRED");
    }

    @Test
    void anIdentityOfAnotherAccountCannotBeLinked() {
        String owner = gmail();
        insertUser(owner, "FAMILY_MEMBER", "ACTIVE");
        Login account = login(owner);
        oidcLogin(browser(), account);
        String email = uniqueEmail();
        insertUser(email, "FAMILY_MEMBER", "ACTIVE");
        ApiClient browser = signedIn(email);

        browser.post("/api/v1/me/identities/google/link");

        assertThat(oidcLogin(browser, account)).isEqualTo("/profile?error=OIDC_IDENTITY_TAKEN");
        assertThat(identities(email)).isZero();
    }

    private int identities(String email) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM user_identity i JOIN app_user u ON u.id = i.user_id WHERE u.email = ?",
                Integer.class,
                email);
    }
}
