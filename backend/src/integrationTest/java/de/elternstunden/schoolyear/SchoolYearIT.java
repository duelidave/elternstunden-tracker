package de.elternstunden.schoolyear;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.elternstunden.schoolyear.domain.SchoolYear;
import de.elternstunden.schoolyear.service.SchoolYearJob;
import de.elternstunden.schoolyear.service.SchoolYearService;
import de.elternstunden.support.ApiClient;
import de.elternstunden.support.IntegrationTest;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * School years (5.5, AC-SY). The database is shared by all tests, so every test uses its own years far from the
 * dev data.
 */
class SchoolYearIT extends IntegrationTest {

    @Autowired
    private SchoolYearService schoolYears;

    @Autowired
    private SchoolYearJob job;

    /** AC-SY-01: 31 Aug still belongs to the old year, 1 Sep to the new one. */
    @Test
    void aDateBelongsToTheYearThatContainsIt() {
        ApiClient admin = signedInAs("ADMIN");
        UUID first = create(admin, "2030/31", "2030-09-01", "2031-08-31");
        UUID second = create(admin, "2031/32", "2031-09-01", "2032-08-31");

        assertThat(schoolYears.forDate(LocalDate.of(2031, 8, 31)))
                .map(SchoolYear::id)
                .hasValue(first);
        assertThat(schoolYears.forDate(LocalDate.of(2031, 9, 1)))
                .map(SchoolYear::id)
                .hasValue(second);
    }

    /** AC-SY-02: new cut-off dates must not leave existing entries outside. */
    @Test
    void cutOffDatesCannotExcludeExistingEntries() {
        ApiClient admin = signedInAs("ADMIN");
        UUID year = create(admin, "2032/33", "2032-09-01", "2033-08-31");
        UUID family = insertFamilyWithChild("Eva", "Eintrag", "2020-01-01");
        UUID member = insertUser(uniqueEmail(), "FAMILY_MEMBER", "ACTIVE");
        jdbc.update(
                "INSERT INTO time_entry (family_id, school_year_id, created_by, work_date, duration_minutes, description)"
                        + " VALUES (?, ?, ?, DATE '2033-05-01', 60, 'Gartenarbeit')",
                family,
                year,
                member);

        ApiClient.Response shortened =
                admin.patch("/api/v1/admin/school-years/" + year, Map.of("endsOn", "2033-04-30"));
        ApiClient.Response stillFine = admin.patch(
                "/api/v1/admin/school-years/" + year, Map.of("endsOn", "2033-07-31", "requiredMinutes", 480));

        assertThat(shortened.status()).isEqualTo(409);
        assertThat(shortened.json().get("code").asString()).isEqualTo("ENTRIES_OUTSIDE_RANGE");
        assertThat(stillFine.status()).isEqualTo(200);
        assertThat(stillFine.json().get("requiredMinutes").asInt()).isEqualTo(480);
    }

    /** AC-SY-03: overlaps are rejected by the service and by the database. */
    @Test
    void overlappingYearsAreRejected() {
        ApiClient admin = signedInAs("ADMIN");
        create(admin, "2033/34", "2033-09-01", "2034-08-31");

        ApiClient.Response overlapping = admin.post(
                "/api/v1/admin/school-years",
                Map.of("label", "2034 kurz", "startsOn", "2034-08-01", "endsOn", "2035-07-31"));
        ApiClient.Response sameLabel = admin.post(
                "/api/v1/admin/school-years",
                Map.of("label", "2033/34", "startsOn", "2050-09-01", "endsOn", "2051-08-31"));

        assertThat(overlapping.status()).isEqualTo(409);
        assertThat(overlapping.json().get("code").asString()).isEqualTo("SCHOOL_YEAR_OVERLAP");
        assertThat(sameLabel.json().get("code").asString()).isEqualTo("SCHOOL_YEAR_LABEL_TAKEN");
        assertThatThrownBy(() -> jdbc.update("INSERT INTO school_year (label, starts_on, ends_on)"
                        + " VALUES ('direkt', DATE '2034-01-01', DATE '2034-12-31')"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    /** AC-SY-04: the job creates the next year 90 days ahead, never twice. */
    @Test
    void theJobCreatesTheNextYearNinetyDaysAhead() {
        clock.setOffsetTo(Instant.parse("2040-06-02T10:00:00Z"));
        assertThat(job.run()).as("91 days ahead: not yet").isEmpty();

        clock.setOffsetTo(Instant.parse("2040-06-03T10:00:00Z"));
        assertThat(job.run()).isPresent();
        assertThat(job.run()).as("no duplicate").isEmpty();

        assertThat(jdbc.queryForMap(
                        "SELECT starts_on, ends_on, required_minutes FROM school_year WHERE label = '2040/41'"))
                .containsEntry("starts_on", java.sql.Date.valueOf("2040-09-01"))
                .containsEntry("ends_on", java.sql.Date.valueOf("2041-08-31"))
                .containsEntry("required_minutes", 600);
    }

    /** OPEN-06: a closed year is read-only until reopened, also for enrollments. */
    @Test
    void closedYearsAreReadOnlyUntilReopened() {
        ApiClient admin = signedInAs("ADMIN");
        UUID year = create(admin, "2034/35", "2034-09-01", "2035-08-31");
        UUID family = insertFamilyWithChild("Cleo", "Closed", "2019-01-01");
        UUID child = jdbc.queryForObject("SELECT id FROM child WHERE family_id = ?", UUID.class, family);

        assertThat(admin.post("/api/v1/admin/school-years/" + year + "/close")
                        .json()
                        .get("closed")
                        .asBoolean())
                .isTrue();
        assertThat(admin.put("/api/v1/admin/children/" + child + "/enrollments/" + year)
                        .status())
                .isEqualTo(409);
        assertThat(admin.patch("/api/v1/admin/school-years/" + year, Map.of("requiredMinutes", 300))
                        .json()
                        .get("code")
                        .asString())
                .isEqualTo("SCHOOL_YEAR_CLOSED");

        admin.post("/api/v1/admin/school-years/" + year + "/reopen");
        assertThat(admin.put("/api/v1/admin/children/" + child + "/enrollments/" + year)
                        .status())
                .isEqualTo(204);
    }

    /** 7.7: "Einschreibung aus Vorjahr übernehmen". */
    @Test
    void enrollmentsAreCopiedFromThePreviousYear() {
        ApiClient admin = signedInAs("ADMIN");
        UUID previous = create(admin, "2036/37", "2036-09-01", "2037-08-31");
        UUID next = create(admin, "2037/38", "2037-09-01", "2038-08-31");
        UUID family = insertFamilyWithChild("Kopie", "Kind", "2018-05-05");
        UUID child = jdbc.queryForObject("SELECT id FROM child WHERE family_id = ?", UUID.class, family);
        admin.put("/api/v1/admin/children/" + child + "/enrollments/" + previous);

        ApiClient.Response copied = admin.post("/api/v1/admin/school-years/" + next + "/enrollments/copy-previous");

        assertThat(copied.json().get("copied").asInt()).isEqualTo(1);
        assertThat(admin.get("/api/v1/admin/children/" + child)
                        .json()
                        .get("enrolledSchoolYearIds")
                        .valueStream()
                        .map(id -> id.asString()))
                .contains(next.toString());
    }

    private static UUID create(ApiClient admin, String label, String startsOn, String endsOn) {
        ApiClient.Response response = admin.post(
                "/api/v1/admin/school-years", Map.of("label", label, "startsOn", startsOn, "endsOn", endsOn));
        assertThat(response.status()).as(response.body()).isEqualTo(201);
        return UUID.fromString(response.json().get("id").asString());
    }
}
