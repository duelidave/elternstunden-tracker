package de.elternstunden.auth;

import static org.assertj.core.api.Assertions.assertThat;

import de.elternstunden.auth.service.Totp;
import de.elternstunden.support.ApiClient;
import de.elternstunden.support.IntegrationTest;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/** AC-AUTH-23 with {@code admin-totp-required=true} (OPEN-09): an admin without TOTP sets it up first. */
@TestPropertySource(properties = "app.security.admin-totp-required=true")
class RequiredTotpIT extends IntegrationTest {

    @Test
    void adminsWithoutTotpSetItUpBeforeTheyGetIn() {
        String email = uniqueEmail();
        insertUser(email, "ADMIN", "ACTIVE");
        ApiClient browser = browser();
        requestMagicLink(browser, email);

        ApiClient.Response redeemed = redeem(browser, latestToken(email));

        assertThat(redeemed.json().get("status").asString()).isEqualTo("MFA_SETUP_REQUIRED");
        assertThat(browser.get("/api/v1/admin/dashboard").status()).isEqualTo(401);
        assertThat(browser.post("/api/v1/auth/mfa/verify", Map.of("code", "123456"))
                        .status())
                .isEqualTo(400);

        String secret =
                browser.post("/api/v1/me/totp/setup").json().get("secret").asString();
        clock.advance(Duration.ofSeconds(Totp.STEP_SECONDS));
        String code = Totp.codeAt(Totp.decodeBase32(secret), clock.instant().getEpochSecond() / Totp.STEP_SECONDS);
        ApiClient.Response confirmed = browser.post("/api/v1/me/totp/confirm", Map.of("code", code));

        assertThat(confirmed.json().get("recoveryCodes").size()).isEqualTo(10);
        assertThat(browser.get("/api/v1/admin/dashboard").status()).isEqualTo(200);
        ApiClient.Response disable = browser.delete("/api/v1/me/totp");
        assertThat(disable.json().get("code").asString()).isEqualTo("TOTP_REQUIRED");
    }
}
