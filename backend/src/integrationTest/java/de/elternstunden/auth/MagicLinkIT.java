package de.elternstunden.auth;

import static de.elternstunden.auth.RegistrationIT.fingerprint;
import static org.assertj.core.api.Assertions.assertThat;

import de.elternstunden.auth.service.BindingCookie;
import de.elternstunden.common.crypto.Hashing;
import de.elternstunden.support.ApiClient;
import de.elternstunden.support.CapturingMailSender.SentMail;
import de.elternstunden.support.IntegrationTest;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Magic link: request, mail, redemption (FS-AUTH 7.2, SEC-17, AC-AUTH, AC-MAIL-02). */
class MagicLinkIT extends IntegrationTest {

    @Autowired
    private MeterRegistry meters;

    @Test
    void activeAccountSignsInWithTheLink() {
        String email = uniqueEmail();
        insertUser(email, "FAMILY_MEMBER", "ACTIVE");
        ApiClient browser = browser();

        assertThat(requestMagicLink(browser, email).status()).isEqualTo(202);
        ApiClient.Response response = redeem(browser, latestToken(email));

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.json().get("email").asString()).isEqualTo(email);
        assertThat(browser.get("/api/v1/me").status()).isEqualTo(200);
        assertThat(mails.sentTo(email).getLast().subject()).isEqualTo("Dein Anmeldelink");
    }

    /** AC-AUTH-01: identical answer for every kind of address. */
    @Test
    void requestAnswersIdenticallyForEveryCase() {
        String active = uniqueEmail();
        insertUser(active, "FAMILY_MEMBER", "ACTIVE");
        String pending = uniqueEmail();
        insertUser(pending, "FAMILY_MEMBER", "PENDING_VERIFICATION");
        String disabled = uniqueEmail();
        insertUser(disabled, "FAMILY_MEMBER", "DISABLED");

        List<String> fingerprints = new ArrayList<>();
        for (String email : List.of(active, pending, uniqueEmail(), disabled, MASTER_EMAIL)) {
            fingerprints.add(fingerprint(requestMagicLink(browser(), email)));
        }

        assertThat(fingerprints).allMatch(fingerprints.getFirst()::equals);
        assertThat(fingerprints.getFirst()).startsWith("202|");
    }

    /** AC-AUTH-02: a GET never consumes the token; there is no GET endpoint that redeems. */
    @Test
    void prefetchingTheLinkDoesNotConsumeTheToken() {
        String email = uniqueEmail();
        insertUser(email, "FAMILY_MEMBER", "ACTIVE");
        ApiClient browser = browser();
        requestMagicLink(browser, email);
        String token = latestToken(email);

        ApiClient scanner = new ApiClient(port);
        assertThat(scanner.get("/login/link").status()).isNotEqualTo(200);
        assertThat(scanner.get("/api/v1/auth/magic-link/redeem?token=" + token).status())
                .isIn(401, 405);
        assertThat(scanner.cookie("__Host-SESSION")).isEmpty();

        assertThat(redeem(browser, token).status()).isEqualTo(200);
    }

    /** AC-AUTH-03 and AC-AUTH-06: single use; all invalid tokens look the same. */
    @Test
    void tokensWorkOnceAndInvalidTokensLookTheSame() {
        String email = uniqueEmail();
        insertUser(email, "FAMILY_MEMBER", "ACTIVE");
        ApiClient browser = browser();
        requestMagicLink(browser, email);
        String used = latestToken(email);
        assertThat(redeem(browser, used).status()).isEqualTo(200);

        requestMagicLink(browser, email);
        String replaced = latestToken(email);
        requestMagicLink(browser, email);
        String current = latestToken(email);

        requestMagicLink(browser, email);
        String expired = latestToken(email);
        clock.advance(Duration.ofMinutes(16));

        List<String> answers = new ArrayList<>();
        for (String token : List.of(used, replaced, Hashing.randomToken(32), expired)) {
            ApiClient.Response response = redeem(browser(), token);
            assertThat(response.status()).isEqualTo(400);
            assertThat(response.json().get("code").asString()).isEqualTo("LINK_INVALID");
            answers.add(response.json().get("detail").asString() + "|"
                    + response.json().get("title").asString());
        }
        assertThat(answers).allMatch(answers.getFirst()::equals);
        assertThat(current).isNotEqualTo(expired);
    }

    /** AC-AUTH-04: valid for 15 minutes. */
    @Test
    void tokensExpireAfterFifteenMinutes() {
        String email = uniqueEmail();
        insertUser(email, "FAMILY_MEMBER", "ACTIVE");
        ApiClient browser = browser();

        requestMagicLink(browser, email);
        String early = latestToken(email);
        clock.advance(Duration.ofMinutes(14).plusSeconds(59));
        assertThat(redeem(browser, early).status()).isEqualTo(200);

        clock.reset();
        requestMagicLink(browser, email);
        String late = latestToken(email);
        clock.advance(Duration.ofMinutes(15).plusSeconds(1));
        assertThat(redeem(browser, late).json().get("code").asString()).isEqualTo("LINK_INVALID");
    }

    /** AC-AUTH-05: a new request replaces the older token. */
    @Test
    void aNewRequestReplacesTheOlderToken() {
        String email = uniqueEmail();
        insertUser(email, "FAMILY_MEMBER", "ACTIVE");
        ApiClient browser = browser();
        requestMagicLink(browser, email);
        String first = latestToken(email);
        requestMagicLink(browser, email);
        String second = latestToken(email);

        assertThat(redeem(browser, first).json().get("code").asString()).isEqualTo("LINK_INVALID");
        assertThat(redeem(browser, second).status()).isEqualTo(200);
    }

    /** AC-AUTH-07: only the SHA-256 hash is stored; the token has 256 bit. */
    @Test
    void onlyTheHashOfTheTokenIsStored() {
        String email = uniqueEmail();
        UUID user = insertUser(email, "FAMILY_MEMBER", "ACTIVE");
        requestMagicLink(browser(), email);
        String token = latestToken(email);

        String stored = jdbc.queryForObject(
                "SELECT token_hash FROM user_token WHERE user_id = ? AND used_at IS NULL", String.class, user);
        assertThat(stored).isEqualTo(Hashing.sha256Hex(token)).isNotEqualTo(token);
        assertThat(java.util.Base64.getUrlDecoder().decode(token)).hasSizeGreaterThanOrEqualTo(32);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM user_token WHERE token_hash = ? OR binding_hash = ?",
                        Long.class,
                        token,
                        token))
                .isZero();
    }

    /** AC-AUTH-08a: at most five link mails per address and hour; "no account" at most once a day. */
    @Test
    void mailBombingIsLimitedPerAddress() {
        String email = uniqueEmail();
        insertUser(email, "FAMILY_MEMBER", "ACTIVE");
        for (int i = 0; i < 6; i++) {
            ApiClient.Response response = i % 2 == 0 ? requestMagicLink(browser(), email) : register(browser(), email);
            assertThat(response.status()).isEqualTo(202);
        }
        processAuthRequests();
        assertThat(mails.sentTo(email)).hasSize(5);

        String unknown = uniqueEmail();
        requestMagicLink(browser(), unknown);
        requestMagicLink(browser(), unknown);
        processAuthRequests();
        assertThat(mails.sentTo(unknown)).hasSize(1);
    }

    /** AC-AUTH-08b and AC-SEC-05: IP limit with Retry-After; a client-sent X-Forwarded-For changes nothing. */
    @Test
    void requestsArePerIpLimited() {
        ApiClient browser = browser();
        for (int i = 0; i < 20; i++) {
            browser.header("X-Forwarded-For", "203.0.113." + i);
            assertThat(requestMagicLink(browser, uniqueEmail()).status()).isEqualTo(202);
        }
        browser.header("X-Forwarded-For", "198.51.100.7");
        ApiClient.Response limited = requestMagicLink(browser, uniqueEmail());

        assertThat(limited.status()).isEqualTo(429);
        assertThat(limited.json().get("code").asString()).isEqualTo("RATE_LIMITED");
        assertThat(limited.header("Retry-After"))
                .hasValueSatisfying(value -> assertThat(Long.parseLong(value)).isPositive());
    }

    /** AC-AUTH-09: unknown address: no account, no token, a "no account" mail with the registration link. */
    @Test
    void unknownAddressGetsANoAccountMail() {
        String email = uniqueEmail();
        requestMagicLink(browser(), email);
        processAuthRequests();

        assertThat(jdbc.queryForObject("SELECT count(*) FROM app_user WHERE email = ?", Long.class, email))
                .isZero();
        SentMail mail = mails.sentTo(email).getFirst();
        assertThat(mail.subject()).isEqualTo("Kein Konto mit dieser Adresse");
        assertThat(mail.text()).contains("http://localhost:5173/register").doesNotContain("#token=");
    }

    /** AC-AUTH-10 and AC-MASTER-03: no link for the master and for disabled accounts. */
    @Test
    void masterAndDisabledAccountsGetNoLink() {
        String disabled = uniqueEmail();
        UUID disabledId = insertUser(disabled, "FAMILY_MEMBER", "DISABLED");
        requestMagicLink(browser(), disabled);
        requestMagicLink(browser(), MASTER_EMAIL);
        processAuthRequests();

        assertThat(mails.sentTo(disabled)).isEmpty();
        assertThat(mails.sentTo(MASTER_EMAIL)).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM user_token WHERE user_id = ?", Long.class, disabledId))
                .isZero();

        String later = uniqueEmail();
        insertUser(later, "FAMILY_MEMBER", "ACTIVE");
        ApiClient browser = browser();
        requestMagicLink(browser, later);
        String token = latestToken(later);
        jdbc.update("UPDATE app_user SET status = 'DISABLED' WHERE email = ?", later);
        assertThat(redeem(browser, token).json().get("code").asString()).isEqualTo("LINK_INVALID");
    }

    /** AC-AUTH-12: redemption needs the CSRF token. */
    @Test
    void redemptionNeedsTheCsrfToken() {
        String email = uniqueEmail();
        insertUser(email, "FAMILY_MEMBER", "ACTIVE");
        ApiClient browser = browser();
        requestMagicLink(browser, email);
        String token = latestToken(email);

        browser.header("X-XSRF-TOKEN", "wrong");
        ApiClient.Response response = redeem(browser, token);

        assertThat(response.status()).isEqualTo(403);
        assertThat(response.json().get("code").asString()).isEqualTo("CSRF_INVALID");
        browser.removeHeader("X-XSRF-TOKEN");
        assertThat(redeem(browser, token).status()).isEqualTo(200);
    }

    /** AC-AUTH-14: ten parallel redemptions of one token give exactly one session. */
    @Test
    void parallelRedemptionsSucceedExactlyOnce() throws Exception {
        String email = uniqueEmail();
        UUID user = insertUser(email, "FAMILY_MEMBER", "ACTIVE");
        ApiClient browser = browser();
        requestMagicLink(browser, email);
        String token = latestToken(email);

        ExecutorService pool = Executors.newFixedThreadPool(10);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            ApiClient copy = new ApiClient(port);
            browser.cookies().forEach(copy::setCookie);
            results.add(pool.submit(() -> {
                start.await();
                return redeem(copy, token).status();
            }));
        }
        start.countDown();
        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> result : results) {
            statuses.add(result.get());
        }
        pool.shutdown();

        assertThat(statuses).filteredOn(status -> status == 200).hasSize(1);
        assertThat(statuses).filteredOn(status -> status == 400).hasSize(9);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM spring_session WHERE principal_name = ?", Long.class, user.toString()))
                .isEqualTo(1L);
    }

    /** AC-AUTH-15: anonymous auth requests never create a session; cookies are the same in every case. */
    @Test
    void anonymousRequestsNeverCreateASession() {
        String active = uniqueEmail();
        insertUser(active, "FAMILY_MEMBER", "ACTIVE");
        for (String email : List.of(active, uniqueEmail(), MASTER_EMAIL)) {
            for (ApiClient.Response response :
                    List.of(requestMagicLink(browser(), email), register(browser(), email))) {
                assertThat(response.setCookies())
                        .noneSatisfy(cookie -> assertThat(cookie).startsWith("__Host-SESSION"));
                assertThat(response.setCookies())
                        .singleElement()
                        .satisfies(cookie -> assertThat(cookie).startsWith(BindingCookie.NAME + "="));
            }
        }
    }

    /** AC-AUTH-16: response times do not depend on the case (medians within 20 ms). */
    @Test
    void responseTimeDoesNotRevealTheCase() {
        String active = uniqueEmail();
        insertUser(active, "FAMILY_MEMBER", "ACTIVE");
        String pending = uniqueEmail();
        insertUser(pending, "FAMILY_MEMBER", "PENDING_VERIFICATION");
        List<String> cases = List.of(uniqueEmail(), active, pending, MASTER_EMAIL);
        Map<String, List<Long>> timings = new java.util.HashMap<>();
        ApiClient browser = browser();

        for (int round = 0; round < 200; round++) {
            for (String email : cases) {
                rateLimiter.reset();
                String captcha = browser.solveCaptcha();
                long start = System.nanoTime();
                ApiClient.Response response = round % 2 == 0
                        ? browser.post("/api/v1/auth/magic-link", Map.of("email", email, "captcha", captcha))
                        : browser.post("/api/v1/auth/register", registration(email, captcha));
                long elapsed = System.nanoTime() - start;
                assertThat(response.status()).isEqualTo(202);
                if (round >= 20) {
                    timings.computeIfAbsent(email, key -> new ArrayList<>()).add(elapsed);
                }
            }
        }
        jdbc.update("DELETE FROM auth_request");

        List<Long> medians = timings.values().stream().map(MagicLinkIT::median).toList();
        long spread = medians.stream().mapToLong(Long::longValue).max().orElseThrow()
                - medians.stream().mapToLong(Long::longValue).min().orElseThrow();
        assertThat(Duration.ofNanos(spread)).isLessThanOrEqualTo(Duration.ofMillis(20));
    }

    /** AC-AUTH-17: the link base comes from APP_BASE_URL only; the token is in the fragment, never a query. */
    @Test
    void linkBaseIgnoresRequestHeaders() {
        String email = uniqueEmail();
        insertUser(email, "FAMILY_MEMBER", "ACTIVE");
        ApiClient browser = browser();
        browser.header("Host", "evil.example");
        browser.header("X-Forwarded-Host", "evil.example");
        browser.header("X-Forwarded-Proto", "https");
        requestMagicLink(browser, email);
        processAuthRequests();

        SentMail mail = mails.sentTo(email).getFirst();
        assertThat(mail.text())
                .contains("http://localhost:5173/login/link#token=")
                .doesNotContain("evil.example");
        assertThat(mail.text()).doesNotContain("/login/link?");
    }

    /** AC-AUTH-22: another device must confirm first; the token stays valid until then. */
    @Test
    void anotherDeviceHasToConfirm() {
        String email = "anna-" + UUID.randomUUID() + "@example.test";
        insertUser(email, "FAMILY_MEMBER", "ACTIVE");
        ApiClient requesting = browser();
        requestMagicLink(requesting, email);
        String token = latestToken(email);

        ApiClient otherDevice = browser();
        ApiClient.Response prompt = redeem(otherDevice, token);
        assertThat(prompt.status()).isEqualTo(409);
        assertThat(prompt.json().get("code").asString()).isEqualTo("OTHER_DEVICE_CONFIRMATION_REQUIRED");
        assertThat(prompt.json().get("maskedEmail").asString()).isEqualTo("a***@example.test");
        assertThat(otherDevice.cookie("__Host-SESSION")).isEmpty();

        ApiClient.Response confirmed =
                otherDevice.post("/api/v1/auth/magic-link/redeem", Map.of("token", token, "confirmOtherDevice", true));
        assertThat(confirmed.status()).isEqualTo(200);
        assertThat(otherDevice.get("/api/v1/me").status()).isEqualTo(200);
    }

    /** AC-MAIL-02: retries on SMTP failures, at most three attempts and only within 15 minutes. */
    @Test
    void failedMailsAreRetriedWithinLimits() {
        double failuresBefore = meters.counter("auth.mail.failures").count();

        String retried = uniqueEmail();
        insertUser(retried, "FAMILY_MEMBER", "ACTIVE");
        mails.failNext(1);
        assertThat(requestMagicLink(browser(), retried).status()).isEqualTo(202);
        processAuthRequests();
        assertThat(mails.sentTo(retried)).isEmpty();
        clock.advance(Duration.ofMinutes(1));
        processAuthRequests();
        assertThat(mails.sentTo(retried)).hasSize(1);

        clock.reset();
        String exhausted = uniqueEmail();
        insertUser(exhausted, "FAMILY_MEMBER", "ACTIVE");
        mails.failNext(3);
        requestMagicLink(browser(), exhausted);
        for (int attempt = 0; attempt < 4; attempt++) {
            processAuthRequests();
            clock.advance(Duration.ofMinutes(1));
        }
        assertThat(mails.sentTo(exhausted)).isEmpty();

        clock.reset();
        String late = uniqueEmail();
        insertUser(late, "FAMILY_MEMBER", "ACTIVE");
        mails.failNext(1);
        requestMagicLink(browser(), late);
        processAuthRequests();
        clock.advance(Duration.ofMinutes(16));
        processAuthRequests();
        assertThat(mails.sentTo(late)).isEmpty();

        assertThat(jdbc.queryForObject("SELECT count(*) FROM auth_request", Long.class))
                .isZero();
        assertThat(meters.counter("auth.mail.failures").count() - failuresBefore)
                .isEqualTo(5.0);
    }

    private static long median(List<Long> values) {
        List<Long> sorted = values.stream().sorted().toList();
        return sorted.get(sorted.size() / 2);
    }
}
