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

    /** Direct connection as the owner role, for checks the app role must not be able to do. */
    protected static Connection ownerConnection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), OWNER, OWNER_PASSWORD);
    }
}
