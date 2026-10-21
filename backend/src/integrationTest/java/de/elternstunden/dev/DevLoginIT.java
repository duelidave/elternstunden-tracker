package de.elternstunden.dev;

import static org.assertj.core.api.Assertions.assertThat;

import de.elternstunden.support.ApiClient;
import de.elternstunden.support.IntegrationTest;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Local role switcher (D-26, FS-DEV 7.12, AC-DEV-02) with the dev profile. Runs against its own database in the
 * shared container, so the dev seed never touches the data of the other tests.
 */
@ActiveProfiles({"test", "dev"})
class DevLoginIT extends IntegrationTest {

    private static final String DATABASE = "elternstunden_dev";

    static {
        try (Connection owner = ownerConnection();
                Statement statement = owner.createStatement()) {
            statement.execute("CREATE DATABASE " + DATABASE);
        } catch (SQLException ex) {
            if (!"42P04".equals(ex.getSQLState())) {
                throw new IllegalStateException(ex);
            }
        }
    }

    @DynamicPropertySource
    static void devDatabase(DynamicPropertyRegistry registry) {
        String url = POSTGRES.getJdbcUrl().replaceFirst("/elternstunden(\\?|$)", "/" + DATABASE + "$1");
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.flyway.url", () -> url);
    }

    @ParameterizedTest
    @CsvSource({
        "FAMILY, familie@dev.local, FAMILY_MEMBER, true",
        "PARTNER, partner@dev.local, FAMILY_MEMBER, true",
        "UNASSIGNED, unzugeordnet@dev.local, FAMILY_MEMBER, false",
        "ADMIN, admin@dev.local, ADMIN, false",
        "MASTER, " + MASTER_EMAIL + ", MASTER_ADMIN, false"
    })
    void signsInAsEveryDemoAccount(String account, String email, String role, boolean assigned) {
        ApiClient browser = browser();

        ApiClient.Response response = browser.post("/api/v1/dev/login-as", Map.of("account", account));

        assertThat(response.status()).as(response.body()).isEqualTo(200);
        ApiClient.Response me = browser.get("/api/v1/me");
        assertThat(me.json().get("email").asString()).isEqualTo(email);
        assertThat(me.json().get("role").asString()).isEqualTo(role);
        assertThat(me.json().get("assignedToFamily").asBoolean()).isEqualTo(assigned);
    }

    @Test
    void switchingAccountsRenewsTheSession() {
        ApiClient browser = browser();
        browser.post("/api/v1/dev/login-as", Map.of("account", "FAMILY"));
        String before = browser.cookie("__Host-SESSION").orElseThrow();

        browser.post("/api/v1/dev/login-as", Map.of("account", "ADMIN"));

        assertThat(browser.cookie("__Host-SESSION"))
                .hasValueSatisfying(after -> assertThat(after).isNotEqualTo(before));
        assertThat(browser.get("/api/v1/me").json().get("role").asString()).isEqualTo("ADMIN");
    }

    @Test
    void unknownAccountsAreRejected() {
        ApiClient.Response response = browser().post("/api/v1/dev/login-as", Map.of("account", "ROOT"));

        assertThat(response.status()).isEqualTo(400);
    }
}
