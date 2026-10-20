package de.elternstunden.support;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

/**
 * Base class for integration tests: the real application against PostgreSQL 18 in a container, with the same two
 * database roles as in docker compose and production (OPS-14). The container is shared by all test classes.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "management.server.port=0")
public abstract class IntegrationTest {

    protected static final String OWNER = "elternstunden";
    protected static final String APP_ROLE = "elternstunden_app";
    private static final String OWNER_PASSWORD = "owner-it";
    private static final String APP_PASSWORD = "app-it";

    protected static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6-alpine")
            .withDatabaseName("elternstunden")
            .withUsername(OWNER)
            .withPassword(OWNER_PASSWORD)
            .withEnv("DB_APP_PASSWORD", APP_PASSWORD)
            .withCopyFileToContainer(
                    MountableFile.forHostPath(System.getProperty("elternstunden.dbInitScript"), 0755),
                    "/docker-entrypoint-initdb.d/01-create-app-role.sh");

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
    }

    /** Direct connection as the owner role, for checks the app role must not be able to do. */
    protected static Connection ownerConnection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), OWNER, OWNER_PASSWORD);
    }
}
