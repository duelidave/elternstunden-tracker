package de.elternstunden.auth;

import static org.assertj.core.api.Assertions.assertThat;

import de.elternstunden.auth.service.Totp;
import de.elternstunden.support.ApiClient;
import de.elternstunden.support.IntegrationTest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Second factor for admins (SEC-05, 7.2, AC-AUTH-23). */
class AdminTotpIT extends IntegrationTest {

    private static final String SESSION = "__Host-SESSION";

    /** Sets up TOTP for a signed-in admin; returns the secret and fills the recovery codes. */
    private String enableTotp(ApiClient admin, List<String> recoveryCodes) {
        ApiClient.Response setup = admin.post("/api/v1/me/totp/setup");
        assertThat(setup.status()).as(setup.body()).isEqualTo(200);
        String secret = setup.json().get("secret").asString();
        assertThat(setup.json().get("otpauthUri").asString()).startsWith("otpauth://totp/Elternstunden");
        ApiClient.Response confirmed = admin.post("/api/v1/me/totp/confirm", Map.of("code", code(secret)));
        assertThat(confirmed.status()).as(confirmed.body()).isEqualTo(200);
        confirmed.json().get("recoveryCodes").valueStream().forEach(code -> recoveryCodes.add(code.asString()));
        return secret;
    }

    /** A code of a fresh step: every step counts only once (replay protection). */
    private String code(String secret) {
        clock.advance(Duration.ofSeconds(Totp.STEP_SECONDS));
        return Totp.codeAt(Totp.decodeBase32(secret), clock.instant().getEpochSecond() / Totp.STEP_SECONDS);
    }

    /** First factor via magic link, without the second one. */
    private ApiClient firstFactor(String email) {
        ApiClient browser = browser();
        requestMagicLink(browser, email);
        ApiClient.Response redeemed = redeem(browser, latestToken(email));
        assertThat(redeemed.json().get("status").asString()).isEqualTo("MFA_REQUIRED");
        return browser;
    }

    /** AC-AUTH-23: after the first factor only MFA_PENDING; a valid code gives a full session with a new id. */
    @Test
    void adminsWithTotpNeedTheSecondFactor() {
        String email = uniqueEmail();
        insertUser(email, "ADMIN", "ACTIVE");
        List<String> recovery = new ArrayList<>();
        String secret = enableTotp(signedIn(email), recovery);
        assertThat(recovery).hasSize(10);

        ApiClient browser = firstFactor(email);
        assertThat(browser.get("/api/v1/me").status()).isEqualTo(401);
        assertThat(browser.get("/api/v1/admin/dashboard").status()).isEqualTo(401);
        ApiClient.Response wrong = browser.post("/api/v1/auth/mfa/verify", Map.of("code", "000000"));
        assertThat(wrong.status()).isEqualTo(400);
        assertThat(wrong.json().get("code").asString()).isEqualTo("MFA_INVALID");
        String before = browser.cookie(SESSION).orElseThrow();

        ApiClient.Response verified = browser.post("/api/v1/auth/mfa/verify", Map.of("code", code(secret)));

        assertThat(verified.json().get("status").asString()).isEqualTo("LOGGED_IN");
        assertThat(browser.cookie(SESSION))
                .hasValueSatisfying(after -> assertThat(after).isNotEqualTo(before));
        assertThat(browser.get("/api/v1/admin/dashboard").status()).isEqualTo(200);
        assertThat(browser.get("/api/v1/me").json().get("totpEnabled").asBoolean())
                .isTrue();
    }

    /** AC-AUTH-23: a recovery code works exactly once. */
    @Test
    void recoveryCodesWorkOnce() {
        String email = uniqueEmail();
        insertUser(email, "ADMIN", "ACTIVE");
        List<String> recovery = new ArrayList<>();
        enableTotp(signedIn(email), recovery);
        String code = recovery.getFirst().toLowerCase(java.util.Locale.ROOT);

        ApiClient first = firstFactor(email);
        assertThat(first.post("/api/v1/auth/mfa/verify", Map.of("code", code)).status())
                .isEqualTo(200);

        ApiClient second = firstFactor(email);
        assertThat(second.post("/api/v1/auth/mfa/verify", Map.of("code", code)).status())
                .isEqualTo(400);
    }

    /** 7.2: every TOTP step counts once. */
    @Test
    void aTotpCodeCannotBeReplayed() {
        String email = uniqueEmail();
        insertUser(email, "ADMIN", "ACTIVE");
        String secret = enableTotp(signedIn(email), new ArrayList<>());
        String code = code(secret);

        assertThat(firstFactor(email)
                        .post("/api/v1/auth/mfa/verify", Map.of("code", code))
                        .status())
                .isEqualTo(200);
        assertThat(firstFactor(email)
                        .post("/api/v1/auth/mfa/verify", Map.of("code", code))
                        .status())
                .isEqualTo(400);
    }

    /** The second factor applies after OIDC as well (7.2). */
    @Test
    void theSecondFactorAppliesAfterAProviderLogin() {
        String email = "admin-" + java.util.UUID.randomUUID() + "@gmail.com";
        insertUser(email, "ADMIN", "ACTIVE");
        enableTotp(signedIn(email), new ArrayList<>());

        ApiClient browser = browser();
        String target =
                oidcLogin(browser, de.elternstunden.support.FakeOidcProvider.Login.of("admin-sub-" + email, email));

        assertThat(target).isEqualTo("/login/mfa");
        assertThat(browser.get("/api/v1/me").status()).isEqualTo(401);
    }

    /** MFA_PENDING ends after 10 minutes. */
    @Test
    void thePendingStateExpires() {
        String email = uniqueEmail();
        insertUser(email, "ADMIN", "ACTIVE");
        String secret = enableTotp(signedIn(email), new ArrayList<>());
        ApiClient browser = firstFactor(email);

        clock.advance(Duration.ofMinutes(11));

        assertThat(browser.post("/api/v1/auth/mfa/verify", Map.of("code", code(secret)))
                        .status())
                .isEqualTo(401);
    }

    /** 7.2: disabling needs a fresh login; another admin can reset it. */
    @Test
    void disablingNeedsAFreshLoginAndAdminsCanReset() {
        String email = uniqueEmail();
        insertUser(email, "ADMIN", "ACTIVE");
        ApiClient admin = signedIn(email);
        enableTotp(admin, new ArrayList<>());

        clock.advance(Duration.ofMinutes(6));
        ApiClient.Response stale = admin.delete("/api/v1/me/totp");
        assertThat(stale.status()).isEqualTo(403);
        assertThat(stale.json().get("code").asString()).isEqualTo("REAUTH_REQUIRED");

        ApiClient colleague = signedInAs("ADMIN");
        assertThat(colleague
                        .delete("/api/v1/admin/users/" + userId(email) + "/totp")
                        .status())
                .isEqualTo(204);
        assertThat(colleague
                        .get("/api/v1/admin/users/" + userId(email))
                        .json()
                        .get("user")
                        .get("totpEnabled")
                        .asBoolean())
                .isFalse();
        // without TOTP the magic link logs in at once again
        requestMagicLink(browser(), email);
        ApiClient again = browser();
        requestMagicLink(again, email);
        assertThat(redeem(again, latestToken(email)).json().get("status").asString())
                .isEqualTo("LOGGED_IN");
    }

    /** INV-12: the master's TOTP comes from the configuration only. */
    @Test
    void theMasterHasNoTotpSetup() {
        ApiClient master = signedInMaster();

        ApiClient.Response setup = master.post("/api/v1/me/totp/setup");

        assertThat(setup.status()).isEqualTo(409);
        assertThat(setup.json().get("code").asString()).isEqualTo("ACCOUNT_MANAGED_BY_CONFIG");
    }

    /** Family members have no TOTP. */
    @Test
    void familyMembersCannotSetUpTotp() {
        assertThat(signedInAs("FAMILY_MEMBER").post("/api/v1/me/totp/setup").status())
                .isEqualTo(403);
    }
}
