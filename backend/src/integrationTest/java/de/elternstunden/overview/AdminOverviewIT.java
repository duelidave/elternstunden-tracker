package de.elternstunden.overview;

import static org.assertj.core.api.Assertions.assertThat;

import de.elternstunden.support.ApiClient;
import de.elternstunden.support.IntegrationTest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * Admin overview (FS-ADMOV 7.6, AC-OV). Every test uses its own school year far from other data, so the counts only
 * see the families of the test.
 */
class AdminOverviewIT extends IntegrationTest {

    private ApiClient admin;

    @BeforeEach
    void signIn() {
        admin = signedInAs("ADMIN");
    }

    /** AC-OV-01: "n of m"; 600 confirmed minutes are fulfilled, 599 are not, open minutes do not count. */
    @Test
    void hintBarCountsFamiliesWithMissingMinutes() {
        UUID year = schoolYear("2070/71", "2070-09-01", "2071-08-31");
        UUID done = enrolledFamily(year, "Erfüllt", "Ella");
        UUID almost = enrolledFamily(year, "Knapp", "Kai");
        UUID nothing = enrolledFamily(year, "Leer", "Lea");
        insertEntry(done, year, 600, "CONFIRMED");
        insertEntry(almost, year, 599 - 599 % 15, "CONFIRMED");
        insertEntry(almost, year, 15, "OPEN");
        insertEntry(almost, year, 120, "OPEN");

        JsonNode page = overview("?schoolYearId=" + year);

        assertThat(page.get("familyCount").asInt()).isEqualTo(3);
        assertThat(page.get("missingCount").asInt()).isEqualTo(2);
        assertThat(page.get("requiredMinutes").asInt()).isEqualTo(600);
        assertThat(page.get("schoolYear").get("label").asString()).isEqualTo("2070/71");
        JsonNode first = page.get("content").get(0);
        assertThat(first.get("familyId").asString()).isEqualTo(nothing.toString());
        assertThat(first.get("missingMinutes").asInt()).isEqualTo(600);
        assertThat(row(page, almost).get("missingMinutes").asInt()).isEqualTo(15);
        assertThat(row(page, almost).get("openMinutes").asInt()).isEqualTo(135);
        assertThat(row(page, done).get("missingMinutes").asInt()).isZero();

        JsonNode missing = overview("?schoolYearId=" + year + "&onlyMissing=true");
        assertThat(missing.get("totalElements").asInt()).isEqualTo(2);
        assertThat(missing.get("familyCount").asInt())
                .as("counts ignore the filter")
                .isEqualTo(3);
    }

    /** AC-OV-02: two enrolled children do not double a family or its minutes; families without enrollment are out. */
    @Test
    void siblingsDoNotMultiplyMinutes() {
        UUID year = schoolYear("2071/72", "2071-09-01", "2072-08-31");
        UUID family = enrolledFamily(year, "Geschwister", "Gina");
        UUID second = insertChild(family, "Gustav", "Geschwister", "2019-02-02");
        jdbc.update("INSERT INTO child_enrollment (child_id, school_year_id) VALUES (?, ?)", second, year);
        insertEntry(family, year, 120, "CONFIRMED");
        insertFamilyWithChild("Nicht", "Eingeschrieben", "2018-01-01");

        JsonNode page = overview("?schoolYearId=" + year);

        assertThat(page.get("familyCount").asInt()).isEqualTo(1);
        assertThat(page.get("content")).hasSize(1);
        assertThat(page.get("content").get(0).get("confirmedMinutes").asInt()).isEqualTo(120);
        assertThat(page.get("content").get(0).get("children")).hasSize(2);
    }

    /** AC-OV-03: default missingMinutes descending; every allowed column both ways; unknown fields 400. */
    @Test
    void sortsByEveryAllowedColumn() {
        UUID year = schoolYear("2072/73", "2072-09-01", "2073-08-31");
        UUID anton = enrolledFamily(year, "Anton", "Ada");
        UUID berta = enrolledFamily(year, "Berta", "Bo");
        UUID carla = enrolledFamily(year, "Carla", "Cem");
        insertEntry(anton, year, 300, "CONFIRMED");
        insertEntry(berta, year, 60, "CONFIRMED");
        insertEntry(berta, year, 90, "OPEN");
        insertEntry(carla, year, 45, "OPEN");
        insertMember(carla);
        insertMember(carla);
        insertMember(anton);

        String base = "?schoolYearId=" + year;
        assertThat(ids(overview(base))).containsExactly(carla, berta, anton);
        assertThat(ids(overview(base + "&sort=missingMinutes,asc"))).containsExactly(anton, berta, carla);
        assertThat(ids(overview(base + "&sort=familyLabel,asc"))).containsExactly(anton, berta, carla);
        assertThat(ids(overview(base + "&sort=familyLabel,desc"))).containsExactly(carla, berta, anton);
        assertThat(ids(overview(base + "&sort=children,asc"))).containsExactly(anton, berta, carla);
        assertThat(ids(overview(base + "&sort=children,desc"))).containsExactly(carla, berta, anton);
        assertThat(ids(overview(base + "&sort=confirmedMinutes,desc"))).containsExactly(anton, berta, carla);
        assertThat(ids(overview(base + "&sort=confirmedMinutes,asc"))).containsExactly(carla, berta, anton);
        assertThat(ids(overview(base + "&sort=openMinutes,desc"))).containsExactly(berta, carla, anton);
        assertThat(ids(overview(base + "&sort=openMinutes,asc"))).containsExactly(anton, carla, berta);
        assertThat(ids(overview(base + "&sort=memberCount,desc"))).containsExactly(carla, anton, berta);
        assertThat(ids(overview(base + "&sort=memberCount,asc"))).containsExactly(berta, anton, carla);

        for (String sort : List.of("email,asc", "familyLabel", "missingMinutes,up", "missing_minutes;DROP")) {
            assertThat(admin.get("/api/v1/admin/overview" + base + "&sort=" + sort)
                            .status())
                    .as(sort)
                    .isEqualTo(400);
        }
    }

    /** AC-OV-04: search ignores case and diacritics, covers label, children, members and e-mail; at least 2 chars. */
    @Test
    void searchFindsFamiliesByAllTheirNames() {
        UUID year = schoolYear("2073/74", "2073-09-01", "2074-08-31");
        UUID mueller = enrolledFamily(year, "Müller", "Mats");
        UUID other = enrolledFamily(year, "Schmidt", "Sina");
        String email = "suche-" + UUID.randomUUID() + "@example.org";
        UUID member = insertUser(email, "FAMILY_MEMBER", "ACTIVE");
        jdbc.update("UPDATE app_user SET family_id = ?, display_name = 'Rosi Rabe' WHERE id = ?", other, member);
        jdbc.update("UPDATE family SET label = 'Haus am See' WHERE id = ?", other);

        String base = "?schoolYearId=" + year + "&q=";
        assertThat(ids(overview(base + "mueller"))).containsExactly(mueller);
        assertThat(ids(overview(base + "MATS"))).containsExactly(mueller);
        assertThat(ids(overview(base + "rabe"))).containsExactly(other);
        assertThat(ids(overview(base + email.substring(0, 20)))).containsExactly(other);
        assertThat(ids(overview(base + "see"))).containsExactly(other);
        assertThat(admin.get("/api/v1/admin/overview" + base + "m").status()).isEqualTo(400);
    }

    /** AC-OV-05 and paging: at most 200 per page. */
    @Test
    void pagesAreLimited() {
        UUID year = schoolYear("2074/75", "2074-09-01", "2075-08-31");
        enrolledFamily(year, "Erste", "Ede");
        enrolledFamily(year, "Zweite", "Zoe");
        enrolledFamily(year, "Dritte", "Dora");

        ApiClient.Response tooBig = admin.get("/api/v1/admin/overview?schoolYearId=" + year + "&size=201");
        assertThat(tooBig.status()).isEqualTo(400);
        assertThat(tooBig.json().get("code").asString()).isEqualTo("PAGE_SIZE_EXCEEDED");

        JsonNode second = overview("?schoolYearId=" + year + "&sort=familyLabel,asc&size=2&page=1");
        assertThat(second.get("totalElements").asInt()).isEqualTo(3);
        assertThat(second.get("totalPages").asInt()).isEqualTo(2);
        assertThat(second.get("content")).hasSize(1);
        assertThat(second.get("content").get(0).get("familyLabel").asString()).isEqualTo("Zweite");
    }

    /** An unknown school year does not exist; family members never see the overview. */
    @Test
    void unknownSchoolYearsAndFamiliesAreRejected() {
        assertThat(admin.get("/api/v1/admin/overview?schoolYearId=" + UUID.randomUUID())
                        .status())
                .isEqualTo(404);
        UUID family = insertFamilyWithChild("Fiona", "Fremd", "2018-05-05");
        assertThat(familyMember(family).get("/api/v1/admin/overview").status()).isEqualTo(403);
    }

    private JsonNode overview(String query) {
        ApiClient.Response response = admin.get("/api/v1/admin/overview" + query);
        assertThat(response.status()).as(response.body()).isEqualTo(200);
        return response.json();
    }

    private static JsonNode row(JsonNode page, UUID familyId) {
        return page.get("content")
                .valueStream()
                .filter(row -> row.get("familyId").asString().equals(familyId.toString()))
                .findFirst()
                .orElseThrow();
    }

    private static List<UUID> ids(JsonNode page) {
        return page.get("content")
                .valueStream()
                .map(row -> UUID.fromString(row.get("familyId").asString()))
                .toList();
    }

    /** A family with one child enrolled in the school year; the child's last name is the family label. */
    private UUID enrolledFamily(UUID year, String lastName, String firstName) {
        UUID family = insertFamilyWithChild(firstName, lastName, "2018-03-03");
        jdbc.update(
                "INSERT INTO child_enrollment (child_id, school_year_id) SELECT id, ? FROM child WHERE family_id = ?",
                year,
                family);
        return family;
    }

    private void insertMember(UUID family) {
        UUID user = insertUser(uniqueEmail(), "FAMILY_MEMBER", "ACTIVE");
        jdbc.update("UPDATE app_user SET family_id = ? WHERE id = ?", family, user);
    }

    private void insertEntry(UUID family, UUID year, int minutes, String status) {
        UUID creator = insertUser(uniqueEmail(), "ADMIN", "ACTIVE");
        boolean confirmed = "CONFIRMED".equals(status);
        jdbc.update(
                "INSERT INTO time_entry (family_id, school_year_id, created_by, work_date, duration_minutes, description,"
                        + " status, confirmed_by, confirmed_at)"
                        + " SELECT ?, id, ?, starts_on, ?, 'Übersicht', CAST(? AS entry_status), ?,"
                        + " CASE WHEN ? THEN now() END FROM school_year WHERE id = ?",
                family,
                creator,
                minutes,
                status,
                confirmed ? creator : null,
                confirmed,
                year);
    }
}
