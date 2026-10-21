package de.elternstunden.auth;

import static org.assertj.core.api.Assertions.assertThat;

import de.elternstunden.support.ApiClient;
import de.elternstunden.support.IntegrationTest;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Sessions after login (D-20, SEC-09, SEC-10, 7.2 "Anmeldung technisch"). */
class SessionIT extends IntegrationTest {

    private static final String SESSION = "__Host-SESSION";

    /** AC-AUTH-13: every login creates a new session id. */
    @Test
    void loginRenewsTheSessionId() {
        String first = uniqueEmail();
        String second = uniqueEmail();
        insertUser(first, "FAMILY_MEMBER", "ACTIVE");
        insertUser(second, "FAMILY_MEMBER", "ACTIVE");
        ApiClient browser = signedIn(first);
        String before = browser.cookie(SESSION).orElseThrow();

        requestMagicLink(browser, second);
        assertThat(redeem(browser, latestToken(second)).status()).isEqualTo(200);

        assertThat(browser.cookie(SESSION))
                .hasValueSatisfying(after -> assertThat(after).isNotEqualTo(before));
        assertThat(browser.get("/api/v1/me").json().get("email").asString()).isEqualTo(second);
    }

    /** The session cookie follows SEC-09. */
    @Test
    void sessionCookieIsHostPrefixedHttpOnlyAndSecure() {
        String email = uniqueEmail();
        insertUser(email, "FAMILY_MEMBER", "ACTIVE");
        ApiClient browser = browser();
        requestMagicLink(browser, email);

        ApiClient.Response response = redeem(browser, latestToken(email));

        assertThat(response.setCookies())
                .anySatisfy(cookie -> assertThat(cookie)
                        .startsWith(SESSION + "=")
                        .contains("HttpOnly", "Secure", "SameSite=Lax", "Path=/")
                        .doesNotContainIgnoringCase("Domain="));
    }

    /** AC-AUTH-19: a family session ends after the absolute duration even when active. */
    @Test
    void sessionsEndAfterTheAbsoluteDuration() {
        String email = uniqueEmail();
        insertUser(email, "FAMILY_MEMBER", "ACTIVE");
        ApiClient browser = signedIn(email);

        clock.advance(Duration.ofDays(89));
        assertThat(browser.get("/api/v1/me").status()).isEqualTo(200);
        clock.advance(Duration.ofDays(1).plusSeconds(1));
        assertThat(browser.get("/api/v1/me").status()).isEqualTo(401);
    }

    /** Disabling an account ends its sessions (7.2 "Sessions folgen dem Kontozustand"). */
    @Test
    void disablingAnAccountEndsItsSessions() {
        String email = uniqueEmail();
        insertUser(email, "FAMILY_MEMBER", "ACTIVE");
        ApiClient browser = signedIn(email);

        jdbc.update("UPDATE app_user SET status = 'DISABLED' WHERE email = ?", email);

        assertThat(browser.get("/api/v1/me").status()).isEqualTo(401);
    }

    /** AC-AUTH-20: the CSRF token changes with the login; the old one no longer works. */
    @Test
    void loginRotatesTheCsrfToken() {
        String email = uniqueEmail();
        insertUser(email, "FAMILY_MEMBER", "ACTIVE");
        ApiClient browser = browser();
        String before = browser.cookie("XSRF-TOKEN").orElseThrow();
        requestMagicLink(browser, email);
        assertThat(redeem(browser, latestToken(email)).status()).isEqualTo(200);
        String after = browser.cookie("XSRF-TOKEN").orElseThrow();
        assertThat(after).isNotEqualTo(before);

        browser.header("X-XSRF-TOKEN", before);
        assertThat(browser.post("/api/v1/auth/logout", Map.of()).status()).isEqualTo(403);
        browser.removeHeader("X-XSRF-TOKEN");
        assertThat(browser.post("/api/v1/auth/logout", Map.of()).status()).isEqualTo(204);
    }

    @Test
    void logoutEndsTheSessionOnTheServer() {
        String email = uniqueEmail();
        insertUser(email, "FAMILY_MEMBER", "ACTIVE");
        ApiClient browser = signedIn(email);
        String session = browser.cookie(SESSION).orElseThrow();

        assertThat(browser.post("/api/v1/auth/logout", Map.of()).status()).isEqualTo(204);
        assertThat(browser.get("/api/v1/me").status()).isEqualTo(401);

        ApiClient stolenCookie = browser();
        stolenCookie.setCookie(SESSION, session);
        assertThat(stolenCookie.get("/api/v1/me").status()).isEqualTo(401);
    }

    /** AC-SEC-03: security headers on API responses (Caddy adds them for the SPA in M7). */
    @Test
    void responsesCarryTheSecurityHeaders() {
        String email = uniqueEmail();
        insertUser(email, "FAMILY_MEMBER", "ACTIVE");
        ApiClient.Response me = signedIn(email).get("/api/v1/me");

        assertThat(me.status()).isEqualTo(200);
        assertThat(me.header("Content-Security-Policy"))
                .hasValueSatisfying(csp ->
                        assertThat(csp).contains("default-src 'self'", "frame-ancestors 'none'", "object-src 'none'"));
        assertThat(me.header("X-Content-Type-Options")).hasValue("nosniff");
        assertThat(me.header("Referrer-Policy")).hasValue("same-origin");
        assertThat(me.header("Permissions-Policy"))
                .hasValueSatisfying(
                        policy -> assertThat(policy).contains("camera=()", "microphone=()", "geolocation=()"));
        assertThat(me.header("X-Frame-Options")).hasValue("DENY");
    }

    /** AC-MASTER-05: an admin gets 403 on master endpoints. */
    @Test
    void adminsCannotReachMasterEndpoints() {
        String admin = uniqueEmail();
        insertUser(admin, "ADMIN", "ACTIVE");
        ApiClient browser = signedIn(admin);

        ApiClient.Response response = browser.get("/api/v1/master/backups/status");

        assertThat(response.status()).isEqualTo(403);
        assertThat(response.json().get("code").asString()).isEqualTo("FORBIDDEN");
    }

    /** AC-DEV-01: without the dev profile the role switcher does not exist. */
    @Test
    void devLoginDoesNotExistOutsideTheDevProfile() {
        ApiClient.Response response = browser().post("/api/v1/dev/login-as", Map.of("account", "ADMIN"));

        assertThat(response.status()).isEqualTo(404);
        assertThat(response.json().get("code").asString()).isEqualTo("NOT_FOUND");
    }
}
