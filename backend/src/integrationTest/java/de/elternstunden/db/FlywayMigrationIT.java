package de.elternstunden.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import de.elternstunden.support.IntegrationTest;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Schema V1 and grants V2 (Lastenheft 5.3, 5.4, OPS-14). The {@link JdbcTemplate} connects as the app role, like the
 * application. Every test runs in a transaction that is rolled back.
 */
@Transactional
class FlywayMigrationIT extends IntegrationTest {

    private static final String UNIQUE_VIOLATION = "23505";
    private static final String CHECK_VIOLATION = "23514";
    private static final String EXCLUSION_VIOLATION = "23P01";
    private static final String INSUFFICIENT_PRIVILEGE = "42501";

    @Test
    void allMigrationsAreApplied() throws SQLException {
        try (Connection owner = ownerConnection();
                Statement statement = owner.createStatement();
                ResultSet rows = statement.executeQuery(
                        "SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank")) {
            List<String> versions = new java.util.ArrayList<>();
            while (rows.next()) {
                versions.add(rows.getString(1));
            }
            assertThat(versions).containsExactly("1", "2", "3", "4", "5", "6", "7", "8");
        }
    }

    @Test
    void allTablesExistAndAreVisibleToTheAppRole() {
        List<String> tables = jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'", String.class);

        assertThat(tables)
                .contains(
                        "school_year",
                        "family",
                        "child",
                        "child_enrollment",
                        "app_user",
                        "user_identity",
                        "registration_claim",
                        "user_token",
                        "auth_request",
                        "time_entry",
                        "audit_log",
                        "api_client",
                        "spring_session",
                        "spring_session_attributes");
    }

    @Test
    void schoolYearsMustNotOverlap() {
        jdbc.update(
                "INSERT INTO school_year (label, starts_on, ends_on) VALUES ('2026/27', '2026-09-01', '2027-08-31')");

        assertSqlState(
                () -> jdbc.update("INSERT INTO school_year (label, starts_on, ends_on)"
                        + " VALUES ('overlap', '2027-08-31', '2028-08-31')"),
                EXCLUSION_VIOLATION);
    }

    @Test
    void onlyTheMasterHasAPassword() {
        assertSqlState(
                () -> jdbc.update("INSERT INTO app_user (email, display_name, password_hash)"
                        + " VALUES ('family@example.test', 'Family', 'hash')"),
                CHECK_VIOLATION);
    }

    @Test
    void theMasterMustHaveAPassword() {
        assertSqlState(
                () -> jdbc.update("INSERT INTO app_user (email, display_name, role, managed_by_config)"
                        + " VALUES ('master@example.test', 'Master', 'MASTER_ADMIN', true)"),
                CHECK_VIOLATION);
    }

    @Test
    void thereIsAtMostOneMaster() {
        // The config-managed master exists already (MasterAccountSync), so any second one is rejected
        assertThat(jdbc.queryForObject("SELECT count(*) FROM app_user WHERE managed_by_config", Long.class))
                .isEqualTo(1L);

        assertSqlState(() -> insertMaster("second-master@example.test"), UNIQUE_VIOLATION);
    }

    @Test
    void activeAccountsHaveAVerifiedEmail() {
        assertSqlState(
                () -> jdbc.update("INSERT INTO app_user (email, display_name, status)"
                        + " VALUES ('active@example.test', 'Active', 'ACTIVE')"),
                CHECK_VIOLATION);
    }

    @Test
    void entriesFollowTheGridAndTheTextLimit() {
        UUID family = jdbc.queryForObject("INSERT INTO family (label) VALUES ('F') RETURNING id", UUID.class);
        UUID year = jdbc.queryForObject(
                "INSERT INTO school_year (label, starts_on, ends_on) VALUES ('2030/31', '2030-09-01', '2031-08-31')"
                        + " RETURNING id",
                UUID.class);
        UUID user = jdbc.queryForObject(
                "INSERT INTO app_user (email, display_name) VALUES ('entry@example.test', 'Entry') RETURNING id",
                UUID.class);

        assertSqlState(
                () -> jdbc.update(
                        "INSERT INTO time_entry (family_id, school_year_id, created_by, work_date, duration_minutes,"
                                + " description) VALUES (?, ?, ?, '2030-10-01', 20, 'Garten')",
                        family,
                        year,
                        user),
                CHECK_VIOLATION);
        // D-09: at most 255 characters (V6)
        assertSqlState(
                () -> jdbc.update(
                        "INSERT INTO time_entry (family_id, school_year_id, created_by, work_date, duration_minutes,"
                                + " description) VALUES (?, ?, ?, '2030-10-01', 15, ?)",
                        family,
                        year,
                        user,
                        "x".repeat(256)),
                CHECK_VIOLATION);
    }

    @Test
    void theAppRoleCanAppendToTheAuditLog() {
        assertThatCode(() -> jdbc.update(
                        "INSERT INTO audit_log (actor_kind, action, entity_type) VALUES ('SYSTEM', 'TEST', 'test')"))
                .doesNotThrowAnyException();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_log", Long.class))
                .isPositive();
    }

    @Test
    void theAppRoleCannotUpdateTheAuditLog() {
        assertSqlState(() -> jdbc.update("UPDATE audit_log SET action = 'CHANGED'"), INSUFFICIENT_PRIVILEGE);
    }

    @Test
    void theAppRoleCannotDeleteFromTheAuditLog() {
        assertSqlState(() -> jdbc.update("DELETE FROM audit_log"), INSUFFICIENT_PRIVILEGE);
    }

    @Test
    void theAppRoleCannotTruncateTheAuditLog() {
        assertSqlState(() -> jdbc.execute("TRUNCATE audit_log"), INSUFFICIENT_PRIVILEGE);
    }

    @Test
    void theAppRoleCannotChangeTheSchema() {
        assertSqlState(() -> jdbc.execute("CREATE TABLE intruder (id int)"), INSUFFICIENT_PRIVILEGE);
    }

    private void insertMaster(String email) {
        jdbc.update(
                "INSERT INTO app_user (email, display_name, role, status, managed_by_config, password_hash,"
                        + " email_verified_at) VALUES (?, 'Master', 'MASTER_ADMIN', 'ACTIVE', true, 'hash', now())",
                email);
    }

    private static void assertSqlState(Runnable statement, String expectedState) {
        try {
            statement.run();
        } catch (DataAccessException ex) {
            Throwable cause = NestedExceptionUtils.getMostSpecificCause(ex);
            assertThat(cause).isInstanceOf(SQLException.class);
            assertThat(((SQLException) cause).getSQLState()).isEqualTo(expectedState);
            return;
        }
        throw new AssertionError("expected SQL state " + expectedState + " but the statement succeeded");
    }
}
