package de.elternstunden.overview;

import static org.assertj.core.api.Assertions.assertThat;

import de.elternstunden.support.ApiClient;
import de.elternstunden.support.IntegrationTest;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** The family's start page (FS-FAM 7.3, AC-FAM). "Today" is 2061-01-15 in the school year 2060/61. */
class FamilyOverviewIT extends IntegrationTest {

    private static final String OVERVIEW = "/api/v1/me/family/overview";

    private UUID year;

    @BeforeEach
    void inTheYear2061() {
        clock.setOffsetTo(Instant.parse("2061-01-15T10:00:00Z"));
        year = schoolYear("2060/61", "2060-09-01", "2061-08-31");
    }

    /** AC-FAM-01: the partner hint shows with exactly one active member. */
    @Test
    void partnerHintWithExactlyOneMember() {
        UUID family = insertFamilyWithChild("Paula", "Partner", "2018-04-05");
        ApiClient first = familyMember(family);
        assertThat(first.get(OVERVIEW).json().get("partnerHint").asBoolean()).isTrue();

        familyMember(family);
        JsonNode overview = first.get(OVERVIEW).json();
        assertThat(overview.get("partnerHint").asBoolean()).isFalse();
        assertThat(overview.get("members")).hasSize(2);
        assertThat(overview.get("members").findValues("isCurrentUser").stream()
                        .filter(JsonNode::asBoolean)
                        .count())
                .isEqualTo(1);
    }

    /** AC-FAM-02, AC-FAM-04: unassigned accounts get 403 FAMILY_ASSIGNMENT_PENDING everywhere. */
    @Test
    void unassignedAccountsSeeNoFamilyData() {
        ApiClient unassigned = signedInAs("FAMILY_MEMBER");
        String entry = "/api/v1/me/family/entries/" + UUID.randomUUID();

        for (ApiClient.Response response : List.of(
                unassigned.get(OVERVIEW),
                unassigned.get("/api/v1/me/family/entries"),
                unassigned.post(
                        "/api/v1/me/family/entries",
                        Map.of("workDate", "2061-01-15", "durationMinutes", 60, "description", "Ohne Familie")),
                unassigned.patch(entry, Map.of("version", 0, "durationMinutes", 30)),
                unassigned.delete(entry))) {
            assertThat(response.status()).isEqualTo(403);
            assertThat(response.json().get("code").asString()).isEqualTo("FAMILY_ASSIGNMENT_PENDING");
        }

        // An assignment applies at once, without a new login (D-20)
        jdbc.update(
                "UPDATE app_user SET family_id = ? WHERE id = (SELECT id FROM app_user ORDER BY created_at DESC LIMIT 1)",
                insertFamilyWithChild("Anna", "Angekommen", "2018-01-01"));
        assertThat(unassigned.get(OVERVIEW).status()).isEqualTo(200);
    }

    /** AC-FAM-03: first names and display names only, no last names, birth dates or e-mails. */
    @Test
    void familySeesNoPersonalDetails() {
        UUID family = insertFamilyWithChild("Greta", "Geheimnachname", "2017-06-07");
        ApiClient member = familyMember(family);
        String email = jdbc.queryForObject("SELECT email FROM app_user WHERE family_id = ?", String.class, family);

        ApiClient.Response response = member.get(OVERVIEW);

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.json().get("children").get(0).get("firstName").asString())
                .isEqualTo("Greta");
        assertThat(response.body())
                .doesNotContain("Geheimnachname")
                .doesNotContain("2017-06-07")
                .doesNotContain(email);
    }

    /** 7.3 rule 1, AC-OV-01: only confirmed minutes count; 600 is fulfilled, 599 is not. */
    @Test
    void progressCountsConfirmedMinutesOnly() {
        UUID family = insertFamilyWithChild("Fritz", "Fortschritt", "2018-08-09");
        ApiClient member = familyMember(family);
        insertEntry(family, 585, "CONFIRMED");
        insertEntry(family, 15, "OPEN");

        JsonNode open = member.get(OVERVIEW).json();
        assertThat(open.get("confirmedMinutes").asInt()).isEqualTo(585);
        assertThat(open.get("openMinutes").asInt()).isEqualTo(15);
        assertThat(open.get("remainingMinutes").asInt()).isEqualTo(15);
        assertThat(open.get("fulfilled").asBoolean()).isFalse();
        assertThat(open.get("canAddEntries").asBoolean()).isTrue();
        assertThat(open.get("schoolYear").get("id").asString()).isEqualTo(year.toString());

        insertEntry(family, 15, "CONFIRMED");
        JsonNode done = member.get(OVERVIEW).json();
        assertThat(done.get("confirmedMinutes").asInt()).isEqualTo(600);
        assertThat(done.get("remainingMinutes").asInt()).isZero();
        assertThat(done.get("fulfilled").asBoolean()).isTrue();
    }

    /** 7.3 rule 2: earlier school years can be looked at, read-only; years that have not started cannot. */
    @Test
    void earlierYearsAreReadOnly() {
        UUID earlier = schoolYear("2059/60", "2059-09-01", "2060-08-31");
        UUID later = schoolYear("2061/62", "2061-09-01", "2062-08-31");
        ApiClient member = familyMember(insertFamilyWithChild("Vera", "Vorjahr", "2016-03-02"));

        JsonNode overview = member.get(OVERVIEW + "?schoolYearId=" + earlier).json();
        assertThat(overview.get("schoolYear").get("label").asString()).isEqualTo("2059/60");
        assertThat(overview.get("canAddEntries").asBoolean()).isFalse();
        assertThat(overview.get("schoolYears").findValuesAsString("label")).contains("2060/61", "2059/60");
        assertThat(overview.get("schoolYears").findValuesAsString("label")).doesNotContain("2061/62");
        assertThat(member.get(OVERVIEW + "?schoolYearId=" + later).status()).isEqualTo(404);
    }

    /** OPEN-04: a family owes hours in a year only if a child is enrolled. */
    @Test
    void obligatedWithAnEnrolledChild() {
        UUID family = insertFamilyWithChild("Otto", "Ohneschule", "2019-01-01");
        ApiClient member = familyMember(family);
        assertThat(member.get(OVERVIEW).json().get("obligated").asBoolean()).isFalse();

        jdbc.update(
                "INSERT INTO child_enrollment (child_id, school_year_id)"
                        + " SELECT id, ? FROM child WHERE family_id = ?",
                year,
                family);
        assertThat(member.get(OVERVIEW).json().get("obligated").asBoolean()).isTrue();
    }

    private void insertEntry(UUID family, int minutes, String status) {
        UUID member = jdbc.queryForObject("SELECT id FROM app_user WHERE family_id = ? LIMIT 1", UUID.class, family);
        boolean confirmed = "CONFIRMED".equals(status);
        jdbc.update(
                "INSERT INTO time_entry (family_id, school_year_id, created_by, work_date, duration_minutes, description,"
                        + " status, confirmed_by, confirmed_at)"
                        + " VALUES (?, ?, ?, DATE '2061-01-10', ?, 'Fortschritt', CAST(? AS entry_status), ?, ?)",
                family,
                year,
                member,
                minutes,
                status,
                confirmed ? member : null,
                confirmed ? java.sql.Timestamp.from(Instant.now()) : null);
    }
}
