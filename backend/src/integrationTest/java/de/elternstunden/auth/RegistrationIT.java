package de.elternstunden.auth;

import static org.assertj.core.api.Assertions.assertThat;

import de.elternstunden.auth.service.AuthCleanupJob;
import de.elternstunden.auth.service.BindingCookie;
import de.elternstunden.support.ApiClient;
import de.elternstunden.support.CapturingMailSender.SentMail;
import de.elternstunden.support.IntegrationTest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.altcha.altcha.v2.Altcha;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Registration via magic link (FS-REG 7.1.4) without family assignment (that follows in M2). */
class RegistrationIT extends IntegrationTest {

    @Autowired
    private AuthCleanupJob cleanup;

    @Test
    void registrationConfirmsTheAddressAndSignsIn() {
        String email = uniqueEmail();
        ApiClient browser = browser();

        assertThat(register(browser, email).status()).isEqualTo(202);
        assertThat(redeem(browser, latestToken(email)).status()).isEqualTo(200);

        ApiClient.Response me = browser.get("/api/v1/me");
        assertThat(me.status()).isEqualTo(200);
        assertThat(me.json().get("email").asString()).isEqualTo(email);
        assertThat(me.json().get("role").asString()).isEqualTo("FAMILY_MEMBER");
        assertThat(me.json().get("assignedToFamily").asBoolean()).isFalse();
    }

    /** AC-REG-10: no access and no assignment before the link is redeemed. */
    @Test
    void noAccessBeforeTheLinkIsRedeemed() {
        String email = uniqueEmail();
        ApiClient browser = browser();
        register(browser, email);
        String token = latestToken(email);

        Map<String, Object> pending = account(email);
        assertThat(pending)
                .containsEntry("status", "PENDING_VERIFICATION")
                .containsEntry("family_id", null)
                .containsEntry("email_verified_at", null);
        assertThat(browser.cookie("__Host-SESSION")).isEmpty();
        assertThat(browser.get("/api/v1/me").status()).isEqualTo(401);

        assertThat(redeem(browser, token).status()).isEqualTo(200);

        Map<String, Object> active = account(email);
        assertThat(active.get("status")).isEqualTo("ACTIVE");
        assertThat(active.get("email_verified_at")).isNotNull();
        assertThat(browser.cookie("__Host-SESSION")).isPresent();
        assertThat(browser.get("/api/v1/me").status()).isEqualTo(200);
    }

    /** AC-REG-02 (cases without matching in M1): identical answers whatever the address is. */
    @Test
    void responsesDoNotRevealWhetherTheAddressExists() {
        String active = uniqueEmail();
        insertUser(active, "FAMILY_MEMBER", "ACTIVE");
        String pending = uniqueEmail();
        insertUser(pending, "FAMILY_MEMBER", "PENDING_VERIFICATION");
        String disabled = uniqueEmail();
        insertUser(disabled, "FAMILY_MEMBER", "DISABLED");

        List<String> fingerprints = new ArrayList<>();
        for (String email : List.of(uniqueEmail(), active, pending, disabled, MASTER_EMAIL)) {
            fingerprints.add(fingerprint(register(browser(), email)));
        }

        assertThat(fingerprints).hasSize(5).allMatch(fingerprints.getFirst()::equals);
        assertThat(fingerprints.getFirst()).startsWith("202|");
    }

    /** AC-REG-06: missing, invalid, expired or reused captcha. */
    @Test
    void captchaMustBeValidFreshAndUnused() throws Exception {
        ApiClient browser = browser();
        String email = uniqueEmail();

        Map<String, Object> withoutCaptcha = new java.util.HashMap<>(registration(email, "x"));
        withoutCaptcha.remove("captcha");
        assertCaptchaInvalid(browser.post("/api/v1/auth/register", withoutCaptcha));
        assertCaptchaInvalid(browser.post("/api/v1/auth/register", registration(email, "bm90LXZhbGlk")));
        assertCaptchaInvalid(browser.post("/api/v1/auth/register", registration(email, expiredSolution())));

        String solution = browser.solveCaptcha();
        assertThat(browser.post("/api/v1/auth/register", registration(email, solution))
                        .status())
                .isEqualTo(202);
        assertCaptchaInvalid(browser.post("/api/v1/auth/register", registration(uniqueEmail(), solution)));
        assertCaptchaInvalid(
                browser.post("/api/v1/auth/magic-link", Map.of("email", uniqueEmail(), "captcha", solution)));
    }

    @Test
    void privacyAndChildDataAreValidatedBeforeAnythingElse() {
        ApiClient browser = browser();
        Map<String, Object> request = new java.util.HashMap<>(registration(uniqueEmail(), "unused"));
        request.put("privacyAccepted", false);
        request.put("child", Map.of("firstName", "Anna", "lastName", "Müller", "birthDate", "2999-01-01"));

        ApiClient.Response response = browser.post("/api/v1/auth/register", request);

        assertThat(response.status()).isEqualTo(400);
        assertThat(response.json().get("code").asString()).isEqualTo("VALIDATION_FAILED");
        assertThat(response.body())
                .contains("privacyAccepted", "child.birthDate")
                .doesNotContain("2999-01-01");
    }

    /** AC-REG-12 and AC-AUTH-21: cleanup removes only stale, never activated accounts without a valid token. */
    @Test
    void cleanupRemovesStaleUnconfirmedAccountsOnly() {
        String stale = uniqueEmail();
        register(browser(), stale);
        processAuthRequests();
        String withValidToken = uniqueEmail();
        String young = uniqueEmail();
        String oldActive = uniqueEmail();
        insertUser(oldActive, "FAMILY_MEMBER", "ACTIVE");
        register(browser(), young);
        processAuthRequests();

        // stale: 25 h old, its token expired; withValidToken: 25 h old, but a fresh link was just requested
        ageAccount(stale, Duration.ofHours(25));
        ageAccount(oldActive, Duration.ofHours(25));
        jdbc.update(
                "UPDATE user_token SET expires_at = now() - interval '1 minute'"
                        + " WHERE user_id = (SELECT id FROM app_user WHERE email = ?)",
                stale);
        register(browser(), withValidToken);
        processAuthRequests();
        ageAccount(withValidToken, Duration.ofHours(25));

        cleanup.run();

        assertThat(exists(stale)).isFalse();
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM user_token t LEFT JOIN app_user u ON u.id = t.user_id WHERE u.id IS NULL",
                        Long.class))
                .isZero();
        assertThat(exists(withValidToken)).isTrue();
        assertThat(exists(young)).isTrue();
        assertThat(exists(oldActive)).isTrue();
    }

    /** AC-REG-14: two parallel registrations of the same address give one account and no server error. */
    @Test
    void parallelRegistrationsOfTheSameAddressCreateOneAccount() throws Exception {
        String email = uniqueEmail();
        ApiClient first = browser();
        ApiClient second = browser();
        String firstCaptcha = first.solveCaptcha();
        String secondCaptcha = second.solveCaptcha();

        CompletableFuture<ApiClient.Response> a = CompletableFuture.supplyAsync(
                () -> first.post("/api/v1/auth/register", registration(email, firstCaptcha)));
        CompletableFuture<ApiClient.Response> b = CompletableFuture.supplyAsync(
                () -> second.post("/api/v1/auth/register", registration(email, secondCaptcha)));
        assertThat(a.get().status()).isEqualTo(202);
        assertThat(b.get().status()).isEqualTo(202);

        ExecutorService workers = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> runs = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            runs.add(workers.submit(() -> {
                start.await();
                return authWorker.processNext();
            }));
        }
        start.countDown();
        for (Future<Boolean> run : runs) {
            run.get();
        }
        workers.shutdown();
        processAuthRequests();

        assertThat(jdbc.queryForObject("SELECT count(*) FROM app_user WHERE email = ?", Long.class, email))
                .isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM auth_request WHERE email = ?", Long.class, email))
                .isZero();
        assertThat(mails.sentTo(email)).hasSize(2);
    }

    /** AC-MAIL-01: plain text and HTML, link as button and as text, no tracking, the ignore sentence. */
    @Test
    void mailsFollowTheMailRules() {
        String email = uniqueEmail();
        register(browser(), email);
        processAuthRequests();

        SentMail mail = mails.sentTo(email).getFirst();
        String link = "http://localhost:5173/login/link#token=" + mail.token();
        assertThat(mail.subject()).isEqualTo("Bitte bestätige deine Registrierung");
        assertThat(mail.text())
                .contains(link, "15 Minuten", "24 Stunden", "Du hast diese Mail nicht angefordert?")
                .doesNotContain("Anna Test");
        assertThat(mail.html())
                .contains("<a href=\"" + link + "\"", ">" + link + "<", "Du hast diese Mail nicht angefordert?")
                .doesNotContain("<img", "<script", "Anna Test");
    }

    /** The binding cookie is set on registration, HttpOnly and Secure (7.2). */
    @Test
    void registrationSetsTheBindingCookie() {
        ApiClient.Response response = register(browser(), uniqueEmail());

        assertThat(response.setCookies())
                .anySatisfy(cookie -> assertThat(cookie)
                        .startsWith(BindingCookie.NAME + "=")
                        .contains("HttpOnly", "Secure", "SameSite=Lax", "Path=/"))
                .noneSatisfy(cookie -> assertThat(cookie).startsWith("__Host-SESSION"));
    }

    private Map<String, Object> account(String email) {
        return jdbc.queryForMap(
                "SELECT status::text AS status, family_id, email_verified_at FROM app_user WHERE email = ?", email);
    }

    private boolean exists(String email) {
        Long count = jdbc.queryForObject("SELECT count(*) FROM app_user WHERE email = ?", Long.class, email);
        return count != null && count > 0;
    }

    private void ageAccount(String email, Duration age) {
        jdbc.update(
                "UPDATE app_user SET created_at = now() - make_interval(secs => ?) WHERE email = ?",
                (double) age.toSeconds(),
                email);
    }

    private static void assertCaptchaInvalid(ApiClient.Response response) {
        assertThat(response.status()).as(response.body()).isEqualTo(400);
        assertThat(response.json().get("code").asString()).isEqualTo("CAPTCHA_INVALID");
    }

    /** A correctly solved challenge whose expiry lies in the past, signed with the test key. */
    private static String expiredSolution() throws Exception {
        Altcha.Challenge challenge = Altcha.createChallenge(new Altcha.CreateChallengeOptions()
                .algorithm("PBKDF2/SHA-256")
                .cost(1)
                .hmacSignatureSecret("test-altcha-hmac-key-not-secret-0123456789")
                .expiresAt(java.time.Instant.now().minusSeconds(60).getEpochSecond()));
        Altcha.Solution solution = Altcha.solveChallenge(challenge, Altcha.kdf("PBKDF2/SHA-256"));
        String payload = "{\"challenge\":" + challenge.toJson() + ",\"solution\":{\"counter\":" + solution.counter()
                + ",\"derivedKey\":\"" + solution.derivedKey() + "\",\"time\":1}}";
        return java.util.Base64.getEncoder().encodeToString(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /** Status, body and the set of header and cookie names; values like trace ids and dates naturally differ. */
    static String fingerprint(ApiClient.Response response) {
        TreeSet<String> headerNames = new TreeSet<>(response.headers().map().keySet());
        headerNames.removeAll(List.of("date", "x-trace-id", ":status"));
        TreeSet<String> cookieNames = new TreeSet<>();
        response.setCookies().forEach(cookie -> cookieNames.add(cookie.substring(0, cookie.indexOf('='))));
        return response.status() + "|" + response.body() + "|" + headerNames + "|" + cookieNames;
    }
}
