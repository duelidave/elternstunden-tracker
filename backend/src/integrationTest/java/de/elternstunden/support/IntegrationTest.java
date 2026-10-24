package de.elternstunden.support;

import de.elternstunden.auth.service.AuthWorker;
import de.elternstunden.auth.service.MasterPassword;
import de.elternstunden.common.ratelimit.RateLimiter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

/**
 * Base class for integration tests: the real application against PostgreSQL 18 in a container, with the same two
 * database roles as in docker compose and production (OPS-14). The container and the Spring context are shared by
 * all test classes; every test starts with fresh rate limits, real time and no captured mails.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "management.server.port=0")
@ActiveProfiles("test")
@Import(TestBeans.class)
public abstract class IntegrationTest {

    protected static final String OWNER = "elternstunden";
    protected static final String APP_ROLE = "elternstunden_app";
    private static final String OWNER_PASSWORD = "owner-it";
    private static final String APP_PASSWORD = "app-it";

    protected static final String MASTER_EMAIL = "master@test.local";
    protected static final String MASTER_PASSWORD = "master-password-for-tests";
    /** Base32 test secret (RFC 6238 test key "12345678901234567890"). */
    protected static final String MASTER_TOTP_SECRET = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";

    public static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6-alpine")
            .withDatabaseName("elternstunden")
            .withUsername(OWNER)
            .withPassword(OWNER_PASSWORD)
            .withEnv("DB_APP_PASSWORD", APP_PASSWORD)
            .withCopyFileToContainer(
                    MountableFile.forHostPath(System.getProperty("elternstunden.dbInitScript"), 0755),
                    "/docker-entrypoint-initdb.d/01-create-app-role.sh");

    private static final String MASTER_HASH = MasterPassword.encoder().encode(MASTER_PASSWORD);

    /** "Google" in the tests (7.2); shared like the database. */
    protected static final FakeOidcProvider OIDC = new FakeOidcProvider();

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", () -> APP_ROLE);
        registry.add("spring.datasource.password", () -> APP_PASSWORD);
        registry.add("spring.flyway.url", POSTGRES::getJdbcUrl);
        registry.add("spring.flyway.user", () -> OWNER);
        registry.add("spring.flyway.password", () -> OWNER_PASSWORD);
        registry.add("app.master.email", () -> MASTER_EMAIL);
        registry.add("app.master.password-hash", () -> MASTER_HASH);
        registry.add("app.master.totp-secret", () -> MASTER_TOTP_SECRET);
        registry.add("app.oidc.providers.google.client-id", () -> FakeOidcProvider.CLIENT_ID);
        registry.add("app.oidc.providers.google.client-secret", () -> FakeOidcProvider.CLIENT_SECRET);
        registry.add("app.oidc.providers.google.issuer", OIDC::issuer);
        registry.add("app.oidc.providers.google.authorization-uri", () -> OIDC.issuer() + "/authorize");
        registry.add("app.oidc.providers.google.token-uri", () -> OIDC.issuer() + "/token");
        registry.add("app.oidc.providers.google.jwk-set-uri", () -> OIDC.issuer() + "/jwks");
        // netID (D-30) is the same fake provider under its own registration id
        registry.add("app.oidc.providers.netid.client-id", () -> FakeOidcProvider.CLIENT_ID);
        registry.add("app.oidc.providers.netid.client-secret", () -> FakeOidcProvider.CLIENT_SECRET);
        registry.add("app.oidc.providers.netid.issuer", OIDC::issuer);
        registry.add("app.oidc.providers.netid.authorization-uri", () -> OIDC.issuer() + "/authorize");
        registry.add("app.oidc.providers.netid.token-uri", () -> OIDC.issuer() + "/token");
        registry.add("app.oidc.providers.netid.jwk-set-uri", () -> OIDC.issuer() + "/jwks");
    }

    @LocalServerPort
    protected int port;

    @Autowired
    protected MutableClock clock;

    @Autowired
    protected CapturingMailSender mails;

    @Autowired
    protected RateLimiter rateLimiter;

    @Autowired
    protected AuthWorker authWorker;

    @Autowired
    protected JdbcTemplate jdbc;

    @BeforeEach
    void resetSharedState() {
        rateLimiter.reset();
        clock.reset();
        mails.clear();
        while (authWorker.processNext()) {
            // drain leftovers from earlier tests
        }
        mails.clear();
    }

    /** A fresh "browser" with the XSRF-TOKEN cookie already set. */
    protected ApiClient browser() {
        return new ApiClient(port).withCsrf();
    }

    /** Runs the auth worker until the outbox is empty (the scheduler is off in tests). */
    protected void processAuthRequests() {
        while (authWorker.processNext()) {
            // keep going
        }
    }

    protected static String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.test";
    }

    protected static java.util.Map<String, Object> registration(String email, String captcha) {
        return java.util.Map.of(
                "displayName",
                "Anna Test",
                "email",
                email,
                "privacyAccepted",
                true,
                "captcha",
                captcha,
                "child",
                java.util.Map.of("firstName", "Anna", "lastName", "Müller", "birthDate", "2017-05-03"));
    }

    protected ApiClient.Response register(ApiClient browser, String email) {
        return browser.post("/api/v1/auth/register", registration(email, browser.solveCaptcha()));
    }

    protected ApiClient.Response requestMagicLink(ApiClient browser, String email) {
        return browser.post(
                "/api/v1/auth/magic-link", java.util.Map.of("email", email, "captcha", browser.solveCaptcha()));
    }

    protected ApiClient.Response redeem(ApiClient browser, String token) {
        return browser.post("/api/v1/auth/magic-link/redeem", java.util.Map.of("token", token));
    }

    /** Runs the worker and returns the token of the latest mail to the address. */
    protected String latestToken(String email) {
        processAuthRequests();
        java.util.List<CapturingMailSender.SentMail> sent = mails.sentTo(email);
        if (sent.isEmpty()) {
            throw new AssertionError("no mail to " + email);
        }
        return sent.getLast().token();
    }

    /** Registers a new account and redeems the link in the same browser: a signed-in family member. */
    protected ApiClient signedInNewMember(String email) {
        ApiClient browser = browser();
        register(browser, email);
        ApiClient.Response response = redeem(browser, latestToken(email));
        if (response.status() != 200) {
            throw new AssertionError("redeem failed with " + response.status() + ": " + response.body());
        }
        return browser;
    }

    /** Inserts an account directly (e.g. an admin or a disabled user). */
    protected UUID insertUser(String email, String role, String status) {
        return jdbc.queryForObject(
                "INSERT INTO app_user (email, display_name, role, status, email_verified_at)"
                        + " VALUES (?, 'Test', CAST(? AS user_role), CAST(? AS user_status),"
                        + " CASE WHEN ? = 'PENDING_VERIFICATION' THEN NULL ELSE now() END) RETURNING id",
                UUID.class,
                email,
                role,
                status,
                status);
    }

    /** Signs in an existing active account via magic link. */
    protected ApiClient signedIn(String email) {
        ApiClient browser = browser();
        requestMagicLink(browser, email);
        ApiClient.Response response = redeem(browser, latestToken(email));
        if (response.status() != 200) {
            throw new AssertionError("redeem failed with " + response.status() + ": " + response.body());
        }
        return browser;
    }

    /** Signs in a new account with the given role via magic link; returns the browser. */
    protected ApiClient signedInAs(String role) {
        String email = uniqueEmail();
        insertUser(email, role, "ACTIVE");
        return signedIn(email);
    }

    /** The master session via password and TOTP (7.2). */
    protected ApiClient signedInMaster() {
        ApiClient browser = browser();
        String code = freshMasterTotpCode();
        ApiClient.Response response = browser.post(
                "/api/v1/auth/master/login",
                java.util.Map.of("email", MASTER_EMAIL, "password", MASTER_PASSWORD, "totp", code));
        if (response.status() != 200) {
            throw new AssertionError("master login failed with " + response.status() + ": " + response.body());
        }
        return browser;
    }

    private static final java.util.concurrent.atomic.AtomicInteger TOTP_STEPS =
            new java.util.concurrent.atomic.AtomicInteger();

    /**
     * The TOTP code of a fresh step. A used step stays used for the whole application context (replay protection),
     * so every call sets the clock one step further ahead than any call before, across all test classes; 30 s per
     * call keeps the calls inside the rate-limit window.
     */
    protected String freshMasterTotpCode() {
        clock.setOffset(java.time.Duration.ofSeconds(
                de.elternstunden.auth.service.Totp.STEP_SECONDS * TOTP_STEPS.incrementAndGet()));
        return de.elternstunden.auth.service.Totp.codeAt(
                de.elternstunden.auth.service.Totp.decodeBase32(MASTER_TOTP_SECRET),
                clock.instant().getEpochSecond() / de.elternstunden.auth.service.Totp.STEP_SECONDS);
    }

    protected UUID userId(String email) {
        return jdbc.queryForObject("SELECT id FROM app_user WHERE email = ?", UUID.class, email);
    }

    /** A family with one child; names are normalized like the application does (7.1.3). */
    protected UUID insertFamilyWithChild(String firstName, String lastName, String birthDate) {
        UUID familyId = jdbc.queryForObject("INSERT INTO family (label) VALUES (NULL) RETURNING id", UUID.class);
        insertChild(familyId, firstName, lastName, birthDate);
        return familyId;
    }

    protected UUID insertChild(UUID familyId, String firstName, String lastName, String birthDate) {
        return jdbc.queryForObject(
                "INSERT INTO child (family_id, first_name, last_name, birth_date, first_name_norm, last_name_norm)"
                        + " VALUES (?, ?, ?, CAST(? AS date), ?, ?) RETURNING id",
                UUID.class,
                familyId,
                firstName,
                lastName,
                birthDate,
                de.elternstunden.common.text.NameNormalizer.normalize(firstName),
                de.elternstunden.common.text.NameNormalizer.normalize(lastName));
    }

    /** A school year by label, created on first use (the database is shared by all tests). */
    protected UUID schoolYear(String label, String startsOn, String endsOn) {
        return jdbc.queryForList("SELECT id FROM school_year WHERE label = ?", UUID.class, label).stream()
                .findFirst()
                .orElseGet(() -> jdbc.queryForObject(
                        "INSERT INTO school_year (label, starts_on, ends_on) VALUES (?, CAST(? AS date), CAST(? AS date))"
                                + " RETURNING id",
                        UUID.class,
                        label,
                        startsOn,
                        endsOn));
    }

    /** Signs in a new active family member of the given family. */
    protected ApiClient familyMember(UUID familyId) {
        String email = uniqueEmail();
        UUID id = insertUser(email, "FAMILY_MEMBER", "ACTIVE");
        jdbc.update("UPDATE app_user SET family_id = ? WHERE id = ?", familyId, id);
        return signedIn(email);
    }

    /** Registration with explicit child data. */
    protected ApiClient.Response register(ApiClient browser, String email, String first, String last, String birth) {
        return browser.post(
                "/api/v1/auth/register",
                java.util.Map.of(
                        "displayName",
                        "Test Parent",
                        "email",
                        email,
                        "privacyAccepted",
                        true,
                        "captcha",
                        browser.solveCaptcha(),
                        "child",
                        java.util.Map.of("firstName", first, "lastName", last, "birthDate", birth)));
    }

    /** Registers with the child data and redeems the link in the same browser. */
    protected ApiClient registeredMember(String email, String first, String last, String birth) {
        ApiClient browser = browser();
        register(browser, email, first, last, birth);
        ApiClient.Response response = redeem(browser, latestToken(email));
        if (response.status() != 200) {
            throw new AssertionError("redeem failed with " + response.status() + ": " + response.body());
        }
        return browser;
    }

    protected @org.jspecify.annotations.Nullable UUID familyOf(String email) {
        return jdbc.queryForObject("SELECT family_id FROM app_user WHERE email = ?", UUID.class, email);
    }

    /** Waits for asynchronous mails (AsyncMailer). */
    protected java.util.List<CapturingMailSender.SentMail> awaitMails(String email, int count) {
        long deadline = System.nanoTime() + java.time.Duration.ofSeconds(10).toNanos();
        while (System.nanoTime() < deadline) {
            java.util.List<CapturingMailSender.SentMail> sent = mails.sentTo(email);
            if (sent.size() >= count) {
                return sent;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new AssertionError(ex);
            }
        }
        return mails.sentTo(email);
    }

    /**
     * A full provider login in the browser: entry, provider, callback. Returns the redirect target in the SPA (path
     * and query), e.g. {@code /}, {@code /register/claim} or {@code /login?error=...}.
     */
    protected String oidcLogin(ApiClient browser, FakeOidcProvider.Login login) {
        return oidcLogin(browser, login, "google");
    }

    /** A login with the given provider registration, e.g. netid (D-30). */
    protected String oidcLogin(ApiClient browser, FakeOidcProvider.Login login, String provider) {
        OIDC.nextLogin(login);
        ApiClient.Response start = browser.get("/oauth2/authorization/" + provider);
        if (start.status() != 302) {
            throw new AssertionError("no redirect to the provider: " + start.status());
        }
        String callback = OIDC.authorize(start.header("Location").orElseThrow());
        return spaTarget(browser.get(callback));
    }

    /** The SPA path of a redirect from the backend (base URL removed). */
    protected static String spaTarget(ApiClient.Response response) {
        if (response.status() != 302) {
            throw new AssertionError("expected a redirect, got " + response.status() + ": " + response.body());
        }
        return response.header("Location").orElseThrow().replace("http://localhost:5173", "");
    }

    /** Direct connection as the owner role, for checks the app role must not be able to do. */
    protected static Connection ownerConnection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), OWNER, OWNER_PASSWORD);
    }
}
