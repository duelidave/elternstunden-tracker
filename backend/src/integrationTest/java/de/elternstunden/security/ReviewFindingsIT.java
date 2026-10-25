package de.elternstunden.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.elternstunden.auth.domain.UserRole;
import de.elternstunden.auth.service.AdminTotpService;
import de.elternstunden.auth.service.Totp;
import de.elternstunden.common.config.AppProperties;
import de.elternstunden.common.security.AppUserPrincipal;
import de.elternstunden.support.ApiClient;
import de.elternstunden.support.IntegrationTest;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/** Findings of the security and architecture review (Lastenheft 0.4.10, 9.1, 7.2). */
class ReviewFindingsIT extends IntegrationTest {

    @Autowired
    private AppProperties properties;

    @Autowired
    private AdminTotpService totp;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    /** AC-AUTH-24: a promotion to admin with a second factor ends the session; the next login asks for the code. */
    @Test
    void promotionWithSecondFactorEndsTheSession() {
        String email = uniqueEmail();
        UUID user = insertUser(email, "FAMILY_MEMBER", "ACTIVE");
        ApiClient member = signedIn(email);
        // A former admin who still has TOTP set up
        jdbc.update(
                "UPDATE app_user SET totp_secret_enc = ?, totp_enabled_at = now() WHERE id = ?",
                new byte[] {1, 2, 3},
                user);

        assertThat(signedInAs("ADMIN")
                        .put("/api/v1/admin/users/" + user + "/role", Map.of("role", "ADMIN"))
                        .status())
                .isEqualTo(200);

        assertThat(member.get("/api/v1/me").status()).isEqualTo(401);
    }

    /** 9.1: a TOTP code can be guessed at most 5 times per half login and 10 times per hour and account. */
    @Test
    void secondFactorGuessesAreLimited() {
        String email = uniqueEmail();
        insertUser(email, "ADMIN", "ACTIVE");
        ApiClient admin = signedIn(email);
        String secret = admin.post("/api/v1/me/totp/setup").json().get("secret").asString();
        admin.post("/api/v1/me/totp/confirm", Map.of("code", code(secret)));
        // Only the limits of the guesses below count
        rateLimiter.reset();

        ApiClient first = halfLogin(email);
        for (int i = 0; i < 4; i++) {
            assertThat(first.post("/api/v1/auth/mfa/verify", Map.of("code", "000000"))
                            .status())
                    .isEqualTo(400);
        }
        assertThat(first.post("/api/v1/auth/mfa/verify", Map.of("code", "000000"))
                        .status())
                .as("the fifth wrong code ends the half login")
                .isEqualTo(401);
        assertThat(first.post("/api/v1/auth/mfa/verify", Map.of("code", code(secret)))
                        .status())
                .isEqualTo(401);

        rateLimiter.reset();
        for (int i = 0; i < 10; i++) {
            rateLimiter.tryAcquire(
                    "mfa-user",
                    userId(email).toString(),
                    properties.rateLimits().mfaPerUser());
        }
        assertThat(halfLogin(email)
                        .post("/api/v1/auth/mfa/verify", Map.of("code", code(secret)))
                        .status())
                .as("per account, whatever the IP")
                .isEqualTo(429);
    }

    /** 9.1: request bodies are bounded before they are read. */
    @Test
    void largeAndChunkedBodiesAreRejected() throws Exception {
        ApiClient browser = browser();
        String large = "{\"challengeId\":\"" + UUID.randomUUID() + "\",\"credential\":{\"id\":\"" + "x".repeat(70_000)
                + "\"}}";

        ApiClient.Response tooLarge = browser.post("/api/v1/auth/passkey/login", large);
        assertThat(tooLarge.status()).isEqualTo(413);
        assertThat(tooLarge.json().get("code").asString()).isEqualTo("PAYLOAD_TOO_LARGE");

        HttpResponse<String> chunked = HttpClient.newHttpClient()
                .send(
                        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/auth/passkey/login"))
                                .header("Content-Type", "application/json")
                                .POST(HttpRequest.BodyPublishers.ofInputStream(
                                        () -> new ByteArrayInputStream("{}".getBytes(StandardCharsets.UTF_8))))
                                .build(),
                        HttpResponse.BodyHandlers.ofString());
        assertThat(chunked.statusCode()).isEqualTo(413);
    }

    /** 9.1: the global budget counts solved captchas only; when it is used up the master gets one alarm. */
    @Test
    void globalLimitCountsAfterTheCaptchaAndAlarms() {
        AppProperties.Limit global = properties.rateLimits().magicLinkGlobal();
        for (int i = 0; i < global.max(); i++) {
            rateLimiter.tryAcquire("magic-link-global", "all", global);
        }
        ApiClient browser = browser();

        ApiClient.Response junk =
                browser.post("/api/v1/auth/magic-link", Map.of("email", uniqueEmail(), "captcha", "kein-captcha"));
        assertThat(junk.status())
                .as("without a solved captcha, no global budget is touched")
                .isEqualTo(400);

        assertThat(requestMagicLink(browser, uniqueEmail()).status()).isEqualTo(429);
        assertThat(requestMagicLink(browser(), uniqueEmail()).status()).isEqualTo(429);
        assertThat(mails.sentTo(MASTER_EMAIL))
                .singleElement()
                .satisfies(mail -> assertThat(mail.subject()).isEqualTo("Globales Limit erreicht"));
    }

    /** 9.1: the OIDC entry is limited like the callback; its anonymous session ends after 10 minutes. */
    @Test
    void oidcEntryIsLimited() {
        ApiClient browser = browser();
        ApiClient.Response start = browser.get("/oauth2/authorization/google");
        assertThat(start.status()).isEqualTo(302);
        assertThat(jdbc.queryForObject(
                        "SELECT max_inactive_interval FROM spring_session ORDER BY creation_time DESC LIMIT 1",
                        Integer.class))
                .isEqualTo(600);

        for (int i = 1; i < properties.rateLimits().oidcCallbackPerIp().max(); i++) {
            browser().get("/oauth2/authorization/google");
        }
        assertThat(browser().get("/oauth2/authorization/google").header("Location"))
                .hasValueSatisfying(location -> assertThat(location).endsWith("/login?error=RATE_LIMITED"));
    }

    /** 4.4: admin services check the role themselves, not only the URL rules. */
    @Test
    void adminServicesCheckTheRoleThemselves() {
        AppUserPrincipal family =
                new AppUserPrincipal(UUID.randomUUID(), UserRole.FAMILY_MEMBER, UUID.randomUUID(), Instant.now());
        SecurityContextHolder.getContext()
                .setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                        family,
                        null,
                        List.of(
                                new SimpleGrantedAuthority("ROLE_USER"),
                                new SimpleGrantedAuthority(UserRole.FAMILY_MEMBER.authority()))));

        assertThatThrownBy(() -> totp.reset(family.userId(), UUID.randomUUID()))
                .isInstanceOf(AccessDeniedException.class);
    }

    private ApiClient halfLogin(String email) {
        ApiClient browser = browser();
        requestMagicLink(browser, email);
        ApiClient.Response redeemed = redeem(browser, latestToken(email));
        assertThat(redeemed.json().get("status").asString()).isEqualTo("MFA_REQUIRED");
        return browser;
    }

    private String code(String secret) {
        return Totp.codeAt(Totp.decodeBase32(secret), clock.instant().getEpochSecond() / Totp.STEP_SECONDS);
    }
}
