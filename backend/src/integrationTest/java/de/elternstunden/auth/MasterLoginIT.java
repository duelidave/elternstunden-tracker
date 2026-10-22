package de.elternstunden.auth;

import static org.assertj.core.api.Assertions.assertThat;

import de.elternstunden.support.ApiClient;
import de.elternstunden.support.IntegrationTest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Master login with password and TOTP (D-22, 7.2, AC-MASTER). */
class MasterLoginIT extends IntegrationTest {

    private static final String PATH = "/api/v1/auth/master/login";

    /** AC-MASTER-02: the master signs in even when there is no admin at all. */
    @Test
    void masterSignsInWithPasswordAndTotp() {
        jdbc.update("UPDATE app_user SET role = 'FAMILY_MEMBER' WHERE role = 'ADMIN'");
        ApiClient browser = browser();

        ApiClient.Response response = login(browser, MASTER_EMAIL, MASTER_PASSWORD, currentCode());

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.json().get("role").asString()).isEqualTo("MASTER_ADMIN");
        assertThat(browser.get("/api/v1/me").json().get("role").asString()).isEqualTo("MASTER_ADMIN");
    }

    /** AC-MASTER-06: all failures look the same; a used TOTP code is rejected. */
    @Test
    void failuresAreIndistinguishableAndCodesWorkOnce() {
        String code = currentCode();
        List<String> failures = new ArrayList<>();
        failures.add(answer(login(browser(), MASTER_EMAIL, "wrong-password", code)));
        failures.add(answer(login(browser(), MASTER_EMAIL, MASTER_PASSWORD, wrong(code))));
        failures.add(answer(login(browser(), "nobody@example.test", MASTER_PASSWORD, code)));
        assertThat(failures).allMatch(failures.getFirst()::equals);
        assertThat(failures.getFirst()).startsWith("401|LOGIN_FAILED|");

        rateLimiter.reset();
        assertThat(login(browser(), MASTER_EMAIL, MASTER_PASSWORD, code).status())
                .isEqualTo(200);
        assertThat(answer(login(browser(), MASTER_EMAIL, MASTER_PASSWORD, code)))
                .isEqualTo(failures.getFirst());
    }

    /** AC-MASTER-06: five attempts per IP and 15 minutes. */
    @Test
    void attemptsArePerIpLimited() {
        for (int i = 0; i < 5; i++) {
            assertThat(login(browser(), MASTER_EMAIL, "wrong-password", currentCode())
                            .status())
                    .isEqualTo(401);
        }
        ApiClient.Response limited = login(browser(), MASTER_EMAIL, MASTER_PASSWORD, currentCode());

        assertThat(limited.status()).isEqualTo(429);
        assertThat(limited.header("Retry-After")).isPresent();
    }

    @Test
    void masterLoginNeedsTheCsrfToken() {
        ApiClient browser = browser();
        browser.header("X-XSRF-TOKEN", "wrong");

        assertThat(login(browser, MASTER_EMAIL, MASTER_PASSWORD, currentCode()).status())
                .isEqualTo(403);
    }

    private static ApiClient.Response login(ApiClient browser, String email, String password, String totp) {
        return browser.post(PATH, Map.of("email", email, "password", password, "totp", totp));
    }

    private String currentCode() {
        return freshMasterTotpCode();
    }

    private static String wrong(String code) {
        return code.equals("000000") ? "111111" : "000000";
    }

    private static String answer(ApiClient.Response response) {
        return response.status() + "|" + response.json().get("code").asString() + "|"
                + response.json().get("detail").asString();
    }
}
