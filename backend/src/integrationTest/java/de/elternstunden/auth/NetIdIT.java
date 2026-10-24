package de.elternstunden.auth;

import static org.assertj.core.api.Assertions.assertThat;

import de.elternstunden.support.ApiClient;
import de.elternstunden.support.FakeOidcProvider.Login;
import de.elternstunden.support.IntegrationTest;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** netID as second OIDC provider (7.2, D-30, AC-OIDC-14 to 16); in tests it is the fake provider. */
class NetIdIT extends IntegrationTest {

    /** AC-OIDC-14: the e-mail is an essential claim at netID, not at Google; unverified addresses are rejected. */
    @Test
    void netIdAsksForTheEmailAsEssentialClaim() {
        String netid = query(browser().get("/oauth2/authorization/netid"));
        String google = query(browser().get("/oauth2/authorization/google"));

        assertThat(netid)
                .contains(
                        "claims={\"userinfo\":{\"email\":{\"essential\":true},\"email_verified\":{\"essential\":true}}}")
                .contains("code_challenge_method=S256");
        assertThat(google).doesNotContain("claims=");
        assertThat(oidcLogin(browser(), Login.of(subject(), gmx()).unverified(), "netid"))
                .isEqualTo("/login?error=OIDC_EMAIL_UNVERIFIED");
    }

    /** AC-OIDC-15: netID is authoritative for GMX and WEB.DE mailboxes only. */
    @Test
    void netIdLinksOnlyItsOwnMailboxes() {
        String gmx = gmx();
        insertUser(gmx, "FAMILY_MEMBER", "ACTIVE");
        ApiClient browser = browser();
        assertThat(oidcLogin(browser, Login.of(subject(), gmx), "netid")).isEqualTo("/");
        assertThat(browser.get("/api/v1/me").json().get("email").asString()).isEqualTo(gmx);

        String webDe = "netid-" + UUID.randomUUID() + "@WEB.DE";
        insertUser(webDe.toLowerCase(java.util.Locale.ROOT), "FAMILY_MEMBER", "ACTIVE");
        assertThat(oidcLogin(browser(), Login.of(subject(), webDe), "netid")).isEqualTo("/");

        String other = uniqueEmail();
        insertUser(other, "FAMILY_MEMBER", "ACTIVE");
        assertThat(oidcLogin(browser(), Login.of(subject(), other), "netid"))
                .isEqualTo("/login?error=OIDC_NOT_AUTHORITATIVE");
    }

    /** AC-OIDC-16: only providers with a client id, in the order of the configuration. */
    @Test
    void activeProvidersAreListed() {
        ApiClient.Response providers = browser().get("/api/v1/auth/providers");

        assertThat(providers.status()).isEqualTo(200);
        assertThat(providers.json().findValuesAsString("id")).containsExactly("google", "netid");
        assertThat(providers.json().findValuesAsString("name")).containsExactly("Google", "netID");
    }

    private static String query(ApiClient.Response start) {
        assertThat(start.status()).isEqualTo(302);
        return URLDecoder.decode(
                URI.create(start.header("Location").orElseThrow()).getRawQuery(), StandardCharsets.UTF_8);
    }

    private static String gmx() {
        return "netid-" + UUID.randomUUID() + "@gmx.de";
    }

    private static String subject() {
        return "netid-" + UUID.randomUUID();
    }
}
