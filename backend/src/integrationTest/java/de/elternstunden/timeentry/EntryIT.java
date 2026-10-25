package de.elternstunden.timeentry;

import static org.assertj.core.api.Assertions.assertThat;

import de.elternstunden.support.ApiClient;
import de.elternstunden.support.IntegrationTest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Entering and confirming hours (FS-ENTRY 7.4, FS-CONF 7.5; AC-ENTRY, AC-CONF, AC-SEC-10). The tests live in the
 * school year 2060/61 with "today" = 2061-01-15; 2059/60 is closed and 2058 lies in no school year.
 */
class EntryIT extends IntegrationTest {

    private static final String ENTRIES = "/api/v1/me/family/entries";
    private static final String OVERVIEW = "/api/v1/me/family/overview";
    private static final String TODAY = "2061-01-15";

    private UUID year;
    private UUID closedYear;
    private UUID family;
    private ApiClient member;

    @BeforeEach
    void inTheYear2061() {
        clock.setOffsetTo(Instant.parse(TODAY + "T10:00:00Z"));
        year = schoolYear("2060/61", "2060-09-01", "2061-08-31");
        closedYear = schoolYear("2059/60", "2059-09-01", "2060-08-31");
        jdbc.update("UPDATE school_year SET closed_at = now() WHERE id = ?", closedYear);
        family = insertFamilyWithChild("Emil", "Eintrag", "2018-02-03");
        member = familyMember(family);
    }

    /** AC-ENTRY-01: quick entry, open minutes rise, confirmed minutes stay. */
    @Test
    void quickEntryIsOpenAndCountsAsOpenMinutes() {
        ApiClient.Response created = member.post(ENTRIES, entry(TODAY, 60, "Garten aufgeräumt"));

        assertThat(created.status()).isEqualTo(201);
        assertThat(created.json().get("status").asString()).isEqualTo("OPEN");
        assertThat(created.json().get("canEdit").asBoolean()).isTrue();
        assertThat(created.json().has("startTime")).isFalse();
        var overview = member.get(OVERVIEW).json();
        assertThat(overview.get("openMinutes").asInt()).isEqualTo(60);
        assertThat(overview.get("confirmedMinutes").asInt()).isZero();
        assertThat(overview.get("entries").get(0).get("description").asString()).isEqualTo("Garten aufgeräumt");
        assertThat(jdbc.queryForObject(
                        "SELECT school_year_id FROM time_entry WHERE id = ?",
                        UUID.class,
                        UUID.fromString(created.json().get("id").asString())))
                .isEqualTo(year);
    }

    /** AC-ENTRY-02: 15-minute grid from 15 to 720 minutes. */
    @Test
    void durationFollowsTheGrid() {
        for (int minutes : new int[] {20, 0, 735}) {
            assertThat(member.post(ENTRIES, entry("2061-01-02", minutes, "Raster " + minutes))
                            .status())
                    .as("%d min", minutes)
                    .isEqualTo(400);
        }
        assertThat(member.post(ENTRIES, entry("2061-01-02", 15, "Raster 15")).status())
                .isEqualTo(201);
        assertThat(member.post(ENTRIES, entry("2061-01-02", 720, "Raster 720")).status())
                .isEqualTo(201);
    }

    /** AC-ENTRY-03: description required, 3 to 255 characters after trimming. */
    @Test
    void descriptionIsRequired() {
        for (String text : new String[] {"", "     ", "ab", "  ab  ", "x".repeat(256)}) {
            assertThat(member.post(ENTRIES, entry("2061-01-03", 60, text)).status())
                    .as("'%s'", text)
                    .isEqualTo(400);
        }
        ApiClient.Response three = member.post(ENTRIES, entry("2061-01-03", 60, "  abc  "));
        assertThat(three.status()).isEqualTo(201);
        assertThat(three.json().get("description").asString()).isEqualTo("abc");
        assertThat(member.post(ENTRIES, entry("2061-01-03", 60, "x".repeat(255)))
                        .status())
                .isEqualTo(201);
    }

    /** AC-ENTRY-04: no future, no date outside the school years, no closed year. */
    @Test
    void dateMustBeInAnOpenSchoolYearAndNotInTheFuture() {
        assertThat(code(member.post(ENTRIES, entry("2061-01-16", 60, "Morgen"))))
                .isEqualTo("DATE_IN_FUTURE");
        assertThat(code(member.post(ENTRIES, entry("2058-03-01", 60, "Kein Jahr"))))
                .isEqualTo("NO_SCHOOL_YEAR");
        ApiClient.Response closed = member.post(ENTRIES, entry("2060-05-01", 60, "Abgeschlossen"));
        assertThat(closed.status()).isEqualTo(409);
        assertThat(code(closed)).isEqualTo("SCHOOL_YEAR_CLOSED");
    }

    /** AC-ENTRY-13: families only in the running school year, admins also in other open ones (D-29, INV-14). */
    @Test
    void familiesBookOnlyInTheRunningSchoolYear() {
        clock.setOffsetTo(Instant.parse("2091-01-15T10:00:00Z"));
        UUID previous = schoolYear("2089/90", "2089-09-01", "2090-08-31");
        schoolYear("2090/91", "2090-09-01", "2091-08-31");
        UUID late = insertFamilyWithChild("Nora", "Nachtrag", "2018-02-02");
        ApiClient parent = familyMember(late);
        UUID creator = jdbc.queryForObject("SELECT id FROM app_user WHERE family_id = ?", UUID.class, late);
        UUID old = jdbc.queryForObject(
                "INSERT INTO time_entry (family_id, school_year_id, created_by, work_date, duration_minutes, description)"
                        + " VALUES (?, ?, ?, DATE '2090-06-01', 60, 'Vorjahr offen') RETURNING id",
                UUID.class,
                late,
                previous,
                creator);

        ApiClient.Response backdated = parent.post(ENTRIES, entry("2090-06-02", 60, "Nachtrag Vorjahr"));
        assertThat(backdated.status()).isEqualTo(400);
        assertThat(code(backdated)).isEqualTo("DATE_OUTSIDE_CURRENT_SCHOOL_YEAR");

        var listed = parent.get(ENTRIES + "?schoolYearId=" + previous).json().get(0);
        assertThat(listed.get("canEdit").asBoolean()).isFalse();
        assertThat(listed.get("canDelete").asBoolean()).isFalse();
        assertThat(parent.get(OVERVIEW + "?schoolYearId=" + previous)
                        .json()
                        .get("entries")
                        .get(0)
                        .get("canEdit")
                        .asBoolean())
                .isFalse();
        ApiClient.Response change = parent.patch(ENTRIES + "/" + old, Map.of("version", 0, "durationMinutes", 30));
        assertThat(change.status()).isEqualTo(409);
        assertThat(code(change)).isEqualTo("SCHOOL_YEAR_NOT_CURRENT");
        assertThat(code(parent.delete(ENTRIES + "/" + old))).isEqualTo("SCHOOL_YEAR_NOT_CURRENT");

        JsonEntry current = JsonEntry.of(parent.post(ENTRIES, entry("2091-01-10", 60, "Laufendes Jahr")));
        ApiClient.Response moved = parent.patch(
                ENTRIES + "/" + current.id(), Map.of("version", current.version(), "workDate", "2090-06-03"));
        assertThat(moved.status()).isEqualTo(400);
        assertThat(code(moved)).isEqualTo("DATE_OUTSIDE_CURRENT_SCHOOL_YEAR");

        ApiClient admin = signedInAs("ADMIN");
        assertThat(admin.post(
                                "/api/v1/admin/families/" + late + "/entries",
                                entry("2090-06-02", 60, "Nachtrag Vorjahr"))
                        .status())
                .isEqualTo(201);
        assertThat(admin.patch("/api/v1/admin/entries/" + old, Map.of("version", 0, "durationMinutes", 90))
                        .status())
                .isEqualTo(200);
        assertThat(minutes(old)).isEqualTo(90);
    }

    /** AC-ENTRY-05: exact entry with start time. */
    @Test
    void exactEntryKeepsTheStartTime() {
        Map<String, Object> body = entry("2060-10-14", 135, "Laternenfest");
        body.put("startTime", "15:30");

        ApiClient.Response created = member.post(ENTRIES, body);

        assertThat(created.status()).isEqualTo(201);
        assertThat(created.json().get("startTime").asString()).isEqualTo("15:30");
        assertThat(jdbc.queryForMap(
                        "SELECT start_time, duration_minutes FROM time_entry WHERE id = ?",
                        UUID.fromString(created.json().get("id").asString())))
                .containsEntry("start_time", java.sql.Time.valueOf("15:30:00"))
                .containsEntry("duration_minutes", 135);
    }

    /** AC-ENTRY-06: the same entry within two minutes is a duplicate, later it is fine. */
    @Test
    void identicalEntryWithinTwoMinutesIsADuplicate() {
        assertThat(member.post(ENTRIES, entry("2061-01-04", 60, "Doppelt")).status())
                .isEqualTo(201);
        ApiClient.Response again = member.post(ENTRIES, entry("2061-01-04", 60, "Doppelt"));
        assertThat(again.status()).isEqualTo(409);
        assertThat(code(again)).isEqualTo("DUPLICATE_ENTRY");

        clock.advance(java.time.Duration.ofMinutes(3));
        assertThat(member.post(ENTRIES, entry("2061-01-04", 60, "Doppelt")).status())
                .isEqualTo(201);
    }

    /** AC-ENTRY-07: entries of another family do not exist for a member. */
    @Test
    void entriesOfOtherFamiliesAreNotFound() {
        UUID other = insertFamilyWithChild("Fremd", "Familie", "2017-01-01");
        JsonEntry foreign = JsonEntry.of(familyMember(other).post(ENTRIES, entry(TODAY, 60, "Fremder Eintrag")));

        assertThat(member.patch(
                                ENTRIES + "/" + foreign.id(),
                                Map.of("version", foreign.version(), "durationMinutes", 30))
                        .status())
                .isEqualTo(404);
        assertThat(member.delete(ENTRIES + "/" + foreign.id()).status()).isEqualTo(404);
        assertThat(minutes(foreign.id())).isEqualTo(60);
    }

    /** AC-ENTRY-08, AC-CONF-01: confirmed entries are locked for the family, confirming twice is fine. */
    @Test
    void confirmedEntriesAreLockedForTheFamily() {
        JsonEntry entry = JsonEntry.of(member.post(ENTRIES, entry(TODAY, 120, "Bestätigt")));
        ApiClient admin = signedInAs("ADMIN");

        ApiClient.Response confirmed = confirm(admin, entry.id(), entry.version());
        assertThat(confirmed.status()).isEqualTo(200);
        assertThat(confirmed.json().get("status").asString()).isEqualTo("CONFIRMED");
        assertThat(confirmed.json().get("confirmedAt")).isNotNull();
        assertThat(confirmed.json().get("version").asInt()).isEqualTo(entry.version() + 1);
        ApiClient.Response twice = confirm(admin, entry.id(), entry.version());
        assertThat(twice.status()).isEqualTo(200);
        assertThat(twice.json().get("version").asInt()).isEqualTo(entry.version() + 1);
        assertThat(member.get(OVERVIEW).json().get("confirmedMinutes").asInt()).isEqualTo(120);

        ApiClient.Response patch =
                member.patch(ENTRIES + "/" + entry.id(), Map.of("version", entry.version() + 1, "durationMinutes", 60));
        assertThat(patch.status()).isEqualTo(409);
        assertThat(code(patch)).isEqualTo("ENTRY_CONFIRMED");
        assertThat(code(member.delete(ENTRIES + "/" + entry.id()))).isEqualTo("ENTRY_CONFIRMED");
        var listed = member.get(ENTRIES).json().get(0);
        assertThat(listed.get("canEdit").asBoolean()).isFalse();
        assertThat(listed.get("canDelete").asBoolean()).isFalse();
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM audit_log WHERE action = 'ENTRY_CONFIRMED' AND entity_id = ?",
                        Integer.class,
                        entry.id().toString()))
                .isEqualTo(1);
    }

    /** AC-ENTRY-09: own open entries can be changed and deleted; deleted ones neither count nor show. */
    @Test
    void ownOpenEntriesCanBeChangedAndDeleted() {
        JsonEntry entry = JsonEntry.of(member.post(ENTRIES, entry(TODAY, 60, "Kuchen gebacken")));
        Map<String, Object> change = new HashMap<>();
        change.put("version", entry.version());
        change.put("durationMinutes", 90);
        change.put("startTime", "09:15");

        ApiClient.Response changed = member.patch(ENTRIES + "/" + entry.id(), change);

        assertThat(changed.status()).isEqualTo(200);
        assertThat(changed.json().get("durationMinutes").asInt()).isEqualTo(90);
        assertThat(changed.json().get("startTime").asString()).isEqualTo("09:15");
        assertThat(changed.json().get("description").asString()).isEqualTo("Kuchen gebacken");
        int version = changed.json().get("version").asInt();
        assertThat(version).isEqualTo(entry.version() + 1);

        ApiClient.Response withoutTime =
                member.patch(ENTRIES + "/" + entry.id(), Map.of("version", version, "startTime", ""));
        assertThat(withoutTime.json().has("startTime")).isFalse();

        assertThat(member.delete(ENTRIES + "/" + entry.id()).status()).isEqualTo(204);
        var overview = member.get(OVERVIEW).json();
        assertThat(overview.get("openMinutes").asInt()).isZero();
        assertThat(overview.get("entries")).isEmpty();
        assertThat(member.delete(ENTRIES + "/" + entry.id()).status()).isEqualTo(404);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM audit_log WHERE entity_id = ? AND action IN ('ENTRY_UPDATED', 'ENTRY_DELETED')",
                        Integer.class,
                        entry.id().toString()))
                .isEqualTo(3);
    }

    /** AC-ENTRY-10: two changes based on the same version, one wins. */
    @Test
    void concurrentChangesConflict() throws Exception {
        JsonEntry entry = JsonEntry.of(member.post(ENTRIES, entry(TODAY, 60, "Gleichzeitig")));
        ApiClient second = copyOf(member);

        List<Integer> statuses = parallel(
                () -> member.patch(
                                ENTRIES + "/" + entry.id(), Map.of("version", entry.version(), "durationMinutes", 30))
                        .status(),
                () -> second.patch(
                                ENTRIES + "/" + entry.id(), Map.of("version", entry.version(), "durationMinutes", 45))
                        .status());

        assertThat(statuses).containsExactlyInAnyOrder(200, 409);
        ApiClient.Response stale =
                member.patch(ENTRIES + "/" + entry.id(), Map.of("version", entry.version(), "durationMinutes", 15));
        assertThat(code(stale)).isEqualTo("CONCURRENT_MODIFICATION");
    }

    /** AC-ENTRY-11: a double click creates one entry. */
    @Test
    void doubleClickCreatesOneEntry() throws Exception {
        ApiClient second = copyOf(member);

        List<Integer> statuses = parallel(
                () -> member.post(ENTRIES, entry(TODAY, 30, "Doppelklick")).status(),
                () -> second.post(ENTRIES, entry(TODAY, 30, "Doppelklick")).status());

        assertThat(statuses).containsExactlyInAnyOrder(201, 409);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM time_entry WHERE family_id = ? AND description = 'Doppelklick'",
                        Integer.class,
                        family))
                .isEqualTo(1);
    }

    /** AC-ENTRY-12: a family patch with the version from before a bulk confirmation fails and changes nothing. */
    @Test
    void patchAfterBulkConfirmationIsAConflict() {
        JsonEntry entry = JsonEntry.of(member.post(ENTRIES, entry(TODAY, 60, "Vor der Bestätigung")));
        confirmAll(signedInAs("ADMIN"), entry.id());

        ApiClient.Response stale =
                member.patch(ENTRIES + "/" + entry.id(), Map.of("version", entry.version(), "durationMinutes", 30));

        assertThat(stale.status()).isEqualTo(409);
        assertThat(code(stale)).isEqualTo("CONCURRENT_MODIFICATION");
        assertThat(jdbc.queryForMap(
                        "SELECT status::text AS status, duration_minutes FROM time_entry WHERE id = ?", entry.id()))
                .containsEntry("status", "CONFIRMED")
                .containsEntry("duration_minutes", 60);
    }

    /** AC-CONF-02, AC-CONF-05: bulk confirmation skips confirmed entries and closed years. */
    @Test
    void bulkConfirmationSkipsConfirmedAndClosed() {
        UUID first = JsonEntry.of(member.post(ENTRIES, entry("2061-01-10", 60, "Eins")))
                .id();
        UUID second = JsonEntry.of(member.post(ENTRIES, entry("2061-01-11", 60, "Zwei")))
                .id();
        UUID third = JsonEntry.of(member.post(ENTRIES, entry("2061-01-12", 60, "Drei")))
                .id();
        UUID inClosedYear = insertEntry(closedYear, "2060-06-01");
        ApiClient admin = signedInAs("ADMIN");

        var all = confirmAll(admin, first, second, third).json();
        assertThat(all.get("confirmed").asInt()).isEqualTo(3);
        assertThat(all.get("skipped").asInt()).isZero();

        var again = confirmAll(admin, first, inClosedYear).json();
        assertThat(again.get("confirmed").asInt()).isZero();
        assertThat(again.get("skipped").asInt()).isEqualTo(2);
        assertThat(code(confirm(admin, inClosedYear, 0))).isEqualTo("SCHOOL_YEAR_CLOSED");
        assertThat(jdbc.queryForObject("SELECT status::text FROM time_entry WHERE id = ?", String.class, inClosedYear))
                .isEqualTo("OPEN");
        assertThat(member.get(OVERVIEW).json().get("confirmedMinutes").asInt()).isEqualTo(180);
    }

    /** AC-CONF-06: only the version the admin saw is confirmed; a changed entry stays open with its new content. */
    @Test
    void onlyTheSeenVersionIsConfirmed() {
        JsonEntry entry = JsonEntry.of(member.post(ENTRIES, entry(TODAY, 60, "Gesehen")));
        JsonEntry other = JsonEntry.of(member.post(ENTRIES, entry(TODAY, 30, "Auch gesehen")));
        ApiClient admin = signedInAs("ADMIN");
        Map<String, Object> seenEntry = seen(entry.id());
        Map<String, Object> seenOther = seen(other.id());

        // The family changes both entries after the admin loaded the list
        member.patch(ENTRIES + "/" + entry.id(), Map.of("version", entry.version(), "durationMinutes", 720));
        member.patch(ENTRIES + "/" + other.id(), Map.of("version", other.version(), "durationMinutes", 600));

        ApiClient.Response single = confirm(admin, entry.id(), entry.version());
        assertThat(single.status()).isEqualTo(409);
        assertThat(code(single)).isEqualTo("CONCURRENT_MODIFICATION");
        var bulk = admin.post("/api/v1/admin/entries/confirm", Map.of("entries", List.of(seenEntry, seenOther)))
                .json();
        assertThat(bulk.get("confirmed").asInt()).isZero();
        assertThat(bulk.get("skipped").asInt()).isEqualTo(2);
        assertThat(jdbc.queryForMap(
                        "SELECT status::text AS status, duration_minutes FROM time_entry WHERE id = ?", entry.id()))
                .containsEntry("status", "OPEN")
                .containsEntry("duration_minutes", 720);
    }

    /** AC-CONF-03: an admin changing a confirmed entry reopens it, with an audit entry. */
    @Test
    void adminChangeReopensAConfirmedEntry() {
        JsonEntry entry = JsonEntry.of(member.post(ENTRIES, entry(TODAY, 120, "Zu lang")));
        ApiClient admin = signedInAs("ADMIN");
        int version = confirm(admin, entry.id(), entry.version())
                .json()
                .get("version")
                .asInt();

        ApiClient.Response corrected =
                admin.patch("/api/v1/admin/entries/" + entry.id(), Map.of("version", version, "durationMinutes", 90));

        assertThat(corrected.status()).isEqualTo(200);
        assertThat(corrected.json().get("status").asString()).isEqualTo("OPEN");
        assertThat(corrected.json().has("confirmedAt")).isFalse();
        var overview = member.get(OVERVIEW).json();
        assertThat(overview.get("confirmedMinutes").asInt()).isZero();
        assertThat(overview.get("openMinutes").asInt()).isEqualTo(90);
        assertThat(jdbc.queryForObject(
                        "SELECT details ->> 'reopened' FROM audit_log WHERE action = 'ENTRY_UPDATED' AND entity_id = ?",
                        String.class,
                        entry.id().toString()))
                .isEqualTo("true");
    }

    /** AC-CONF-04: family members cannot confirm. */
    @Test
    void familyMembersCannotConfirm() {
        JsonEntry entry = JsonEntry.of(member.post(ENTRIES, entry(TODAY, 60, "Selbst bestätigt")));

        assertThat(confirm(member, entry.id(), entry.version()).status()).isEqualTo(403);
        assertThat(confirmAll(member, entry.id()).status()).isEqualTo(403);
    }

    /** AC-SEC-10: no mass assignment, unknown or read-only fields are rejected and nothing changes. */
    @Test
    void readOnlyFieldsCannotBePatched() {
        JsonEntry entry = JsonEntry.of(member.post(ENTRIES, entry(TODAY, 60, "Massenzuweisung")));
        UUID other = insertFamilyWithChild("Ziel", "Familie", "2016-01-01");

        for (Map.Entry<String, Object> field : Map.<String, Object>of(
                        "status", "CONFIRMED",
                        "familyId", other.toString(),
                        "confirmedBy", UUID.randomUUID().toString(),
                        "createdBy", UUID.randomUUID().toString())
                .entrySet()) {
            ApiClient.Response response = member.patch(
                    ENTRIES + "/" + entry.id(),
                    Map.of("version", entry.version(), "description", "Geändert", field.getKey(), field.getValue()));
            assertThat(response.status()).as(field.getKey()).isEqualTo(400);
        }
        assertThat(jdbc.queryForMap(
                        "SELECT status::text AS status, family_id, description, version FROM time_entry WHERE id = ?",
                        entry.id()))
                .containsEntry("status", "OPEN")
                .containsEntry("family_id", family)
                .containsEntry("description", "Massenzuweisung")
                .containsEntry("version", entry.version());
    }

    /** 7.5: the list to confirm, oldest first, filtered by family and period; the dashboard counts them. */
    @Test
    void adminsSeeOpenEntriesOldestFirst() {
        UUID older = JsonEntry.of(member.post(ENTRIES, entry("2061-01-14", 60, "Älter")))
                .id();
        clock.advance(java.time.Duration.ofSeconds(5));
        UUID newer = JsonEntry.of(member.post(ENTRIES, entry("2061-01-02", 30, "Neuer")))
                .id();
        ApiClient admin = signedInAs("ADMIN");

        var page = admin.get("/api/v1/admin/entries?status=OPEN&familyId=" + family)
                .json();
        assertThat(page.get("totalElements").asInt()).isEqualTo(2);
        assertThat(page.get("content").get(0).get("id").asString()).isEqualTo(older.toString());
        assertThat(page.get("content").get(0).get("familyLabel").asString()).isEqualTo("Eintrag");
        var period = admin.get("/api/v1/admin/entries?familyId=" + family + "&from=2061-01-10&to=2061-01-31")
                .json();
        assertThat(period.get("totalElements").asInt()).isEqualTo(1);
        assertThat(period.get("content").get(0).get("id").asString()).isEqualTo(older.toString());
        assertThat(admin.get("/api/v1/admin/dashboard")
                        .json()
                        .get("openEntryCount")
                        .asInt())
                .isGreaterThanOrEqualTo(2);

        admin.delete("/api/v1/admin/entries/" + newer);
        assertThat(admin.get("/api/v1/admin/entries?familyId=" + family)
                        .json()
                        .get("totalElements")
                        .asInt())
                .isEqualTo(1);
    }

    /** Admins may enter hours for any family (INV-02). */
    @Test
    void adminsCanEnterHoursForAFamily() {
        ApiClient admin = signedInAs("ADMIN");

        ApiClient.Response created =
                admin.post("/api/v1/admin/families/" + family + "/entries", entry(TODAY, 45, "Nachgetragen"));

        assertThat(created.status()).isEqualTo(201);
        assertThat(created.json().get("familyId").asString()).isEqualTo(family.toString());
        assertThat(member.get(OVERVIEW).json().get("openMinutes").asInt()).isEqualTo(45);
        assertThat(admin.post("/api/v1/admin/families/" + UUID.randomUUID() + "/entries", entry(TODAY, 45, "Niemand"))
                        .status())
                .isEqualTo(404);
    }

    /** 7.4 rule 6: at most 60 entries per user and hour. */
    @Test
    void entriesAreRateLimited() {
        for (int i = 0; i < 60; i++) {
            assertThat(member.post(ENTRIES, entry(TODAY, 15, "Eintrag " + i)).status())
                    .isEqualTo(201);
        }
        assertThat(member.post(ENTRIES, entry(TODAY, 15, "Eintrag 61")).status())
                .isEqualTo(429);
    }

    private record JsonEntry(UUID id, int version) {
        static JsonEntry of(ApiClient.Response response) {
            if (response.status() != 201) {
                throw new AssertionError("create failed with " + response.status() + ": " + response.body());
            }
            return new JsonEntry(
                    UUID.fromString(response.json().get("id").asString()),
                    response.json().get("version").asInt());
        }
    }

    private static Map<String, Object> entry(String date, int minutes, String description) {
        Map<String, Object> body = new HashMap<>();
        body.put("workDate", date);
        body.put("durationMinutes", minutes);
        body.put("description", description);
        return body;
    }

    private UUID insertEntry(UUID schoolYearId, String date) {
        UUID createdBy = jdbc.queryForObject("SELECT id FROM app_user WHERE family_id = ? LIMIT 1", UUID.class, family);
        return jdbc.queryForObject(
                "INSERT INTO time_entry (family_id, school_year_id, created_by, work_date, duration_minutes, description)"
                        + " VALUES (?, ?, ?, CAST(? AS date), 60, 'Altes Jahr') RETURNING id",
                UUID.class,
                family,
                schoolYearId,
                createdBy,
                date);
    }

    private static ApiClient.Response confirm(ApiClient client, UUID id, int version) {
        return client.post("/api/v1/admin/entries/" + id + "/confirm", Map.of("version", version));
    }

    /** Bulk confirmation of entries in their current version, as the admin list shows them. */
    private ApiClient.Response confirmAll(ApiClient client, UUID... ids) {
        return client.post(
                "/api/v1/admin/entries/confirm",
                Map.of("entries", java.util.Arrays.stream(ids).map(this::seen).toList()));
    }

    private Map<String, Object> seen(UUID id) {
        return Map.of(
                "id", id.toString(),
                "version", jdbc.queryForObject("SELECT version FROM time_entry WHERE id = ?", Integer.class, id));
    }

    private int minutes(UUID entryId) {
        return jdbc.queryForObject("SELECT duration_minutes FROM time_entry WHERE id = ?", Integer.class, entryId);
    }

    private static String code(ApiClient.Response response) {
        return response.json().get("code").asString();
    }

    /** A second client with the same cookies, for parallel requests. */
    private ApiClient copyOf(ApiClient original) {
        ApiClient copy = new ApiClient(port);
        original.cookies().forEach(copy::setCookie);
        return copy;
    }

    /** Runs both calls at the same time. */
    private static List<Integer> parallel(Callable<Integer> first, Callable<Integer> second) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<Integer>> futures = executor.invokeAll(List.of(first, second));
            List<Integer> results = new ArrayList<>();
            for (Future<Integer> future : futures) {
                results.add(future.get());
            }
            return results;
        } finally {
            executor.shutdownNow();
        }
    }
}
