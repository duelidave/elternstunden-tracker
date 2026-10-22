package de.elternstunden.family;

import static org.assertj.core.api.Assertions.assertThat;

import de.elternstunden.support.ApiClient;
import de.elternstunden.support.IntegrationTest;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Children, families and CSV import for admins (FS-ADM 7.7, AC-ADM-01). */
class MasterDataIT extends IntegrationTest {

    private static final String CSV_TYPE = "text/csv";
    private static final String IMPORT = "/api/v1/admin/children/import";

    @Test
    void adminsCreateChildrenInNewAndExistingFamilies() {
        ApiClient admin = signedInAs("ADMIN");

        ApiClient.Response first = admin.post(
                "/api/v1/admin/children",
                Map.of(
                        "firstName",
                        " Jonas ",
                        "lastName",
                        "Größe",
                        "birthDate",
                        "2017-07-07",
                        "newFamilyLabel",
                        "Familie Größe"));
        assertThat(first.status()).isEqualTo(201);
        UUID family = UUID.fromString(first.json().get("familyId").asString());
        ApiClient.Response sibling = admin.post(
                "/api/v1/admin/children",
                Map.of("firstName", "Mia", "lastName", "Größe", "birthDate", "2019-09-19", "familyId", family));

        assertThat(first.json().get("firstName").asString()).isEqualTo("Jonas");
        assertThat(sibling.json().get("familyId").asString()).isEqualTo(family.toString());
        ApiClient.Response detail = admin.get("/api/v1/admin/families/" + family);
        assertThat(detail.json().get("displayLabel").asString()).isEqualTo("Familie Größe");
        assertThat(detail.json().get("children").size()).isEqualTo(2);
        assertThat(jdbc.queryForObject(
                        "SELECT last_name_norm FROM child WHERE id = ?",
                        String.class,
                        UUID.fromString(first.json().get("id").asString())))
                .isEqualTo("groesse");
    }

    @Test
    void renamingAChildUpdatesTheNormalizedNamesForTheMatching() {
        ApiClient admin = signedInAs("ADMIN");
        UUID family = insertFamilyWithChild("Lena", "Alt", "2018-08-08");
        UUID child = jdbc.queryForObject("SELECT id FROM child WHERE family_id = ?", UUID.class, family);

        ApiClient.Response renamed = admin.patch("/api/v1/admin/children/" + child, Map.of("lastName", "Neu-Mann"));

        assertThat(renamed.status()).isEqualTo(200);
        String email = uniqueEmail();
        registeredMember(email, "Lena", "Neu Mann", "2018-08-08");
        assertThat(familyOf(email)).isEqualTo(family);
    }

    @Test
    void externalReferencesAreUnique() {
        ApiClient admin = signedInAs("ADMIN");
        Map<String, Object> child = Map.of(
                "firstName", "Ref", "lastName", "Eins", "birthDate", "2018-01-01", "externalRef", "EXT-UNIQUE-1");
        assertThat(admin.post("/api/v1/admin/children", child).status()).isEqualTo(201);

        ApiClient.Response duplicate = admin.post(
                "/api/v1/admin/children",
                Map.of(
                        "firstName",
                        "Ref",
                        "lastName",
                        "Zwei",
                        "birthDate",
                        "2018-01-02",
                        "externalRef",
                        "EXT-UNIQUE-1"));

        assertThat(duplicate.status()).isEqualTo(409);
        assertThat(duplicate.json().get("code").asString()).isEqualTo("EXTERNAL_REF_TAKEN");
    }

    @Test
    void familiesAreSearchableAndEditable() {
        ApiClient admin = signedInAs("ADMIN");
        UUID family = insertFamilyWithChild("Søren", "Sörensen-Blau", "2016-12-12");

        ApiClient.Response found = admin.get("/api/v1/admin/families?q=SOERENSEN");
        ApiClient.Response updated =
                admin.patch("/api/v1/admin/families/" + family, Map.of("label", "Familie Sörensen", "note", "Notiz"));
        ApiClient.Response cleared = admin.patch("/api/v1/admin/families/" + family, Map.of("note", ""));

        assertThat(found.json()
                        .get("content")
                        .valueStream()
                        .map(f -> f.get("id").asString()))
                .contains(family.toString());
        assertThat(updated.json().get("label").asString()).isEqualTo("Familie Sörensen");
        assertThat(cleared.json().has("note")).isFalse();
        assertThat(cleared.json().get("label").asString()).isEqualTo("Familie Sörensen");
    }

    @Test
    void pageSizeIsLimited() {
        ApiClient admin = signedInAs("ADMIN");

        ApiClient.Response tooLarge = admin.get("/api/v1/admin/children?size=201");

        assertThat(tooLarge.status()).isEqualTo(400);
        assertThat(tooLarge.json().get("code").asString()).isEqualTo("PAGE_SIZE_EXCEEDED");
        assertThat(admin.get("/api/v1/admin/children?size=200").status()).isEqualTo(200);
    }

    /** AC-ADM-01: the dry run writes nothing, a second import is idempotent. */
    @Test
    void importIsPreviewedAndIdempotent() {
        ApiClient admin = signedInAs("ADMIN");
        String csv = """
                first_name;last_name;birth_date;family_key;external_ref
                Ole;Import;2017-03-01;fam-a;IMP-1
                Pia;Import;01.04.2019;fam-a;
                Quinn;Anders;2018-05-05;fam-b;IMP-3
                """;
        int childrenBefore = count("child");

        ApiClient.Response preview = admin.post(IMPORT + "?dryRun=true", CSV_TYPE, csv);
        assertThat(preview.status()).isEqualTo(200);
        assertThat(preview.json().get("created").asInt()).isEqualTo(3);
        assertThat(preview.json().get("applied").asBoolean()).isFalse();
        assertThat(count("child")).as("dry run wrote nothing").isEqualTo(childrenBefore);

        ApiClient.Response imported = admin.post(IMPORT, CSV_TYPE, csv);
        assertThat(imported.json().get("applied").asBoolean()).isTrue();
        assertThat(count("child")).isEqualTo(childrenBefore + 3);
        assertThat(jdbc.queryForObject(
                        "SELECT count(DISTINCT family_id) FROM child WHERE last_name = 'Import'", Integer.class))
                .as("same family_key, same family")
                .isEqualTo(1);

        ApiClient.Response again = admin.post(IMPORT, CSV_TYPE, csv);
        assertThat(again.json().get("unchanged").asInt()).isEqualTo(3);
        assertThat(again.json().get("created").asInt()).isZero();
        assertThat(count("child")).isEqualTo(childrenBefore + 3);

        ApiClient.Response renamed = admin.post(IMPORT, CSV_TYPE, csv.replace("Ole;Import", "Ole-Jan;Import"));
        assertThat(renamed.json().get("updated").asInt()).isEqualTo(1);
    }

    /** AC-ADM-01: a faulty file changes nothing and names the faulty lines. */
    @Test
    void aFaultyImportChangesNothing() {
        ApiClient admin = signedInAs("ADMIN");
        String csv = """
                first_name;last_name;birth_date;family_key;external_ref
                Rita;Fehler;2017-03-01;fam-x;
                Sam;Fehler;31.02.2018;fam-x;
                ;Fehler;2018-01-01;fam-x;
                Tom;Fehler;2018-01-01
                """;
        int childrenBefore = count("child");

        ApiClient.Response result = admin.post(IMPORT, CSV_TYPE, csv);

        assertThat(result.status()).isEqualTo(200);
        assertThat(result.json().get("applied").asBoolean()).isFalse();
        assertThat(result.json()
                        .get("errors")
                        .valueStream()
                        .map(error -> error.get("line").asInt() + ":"
                                + error.get("code").asString()))
                .containsExactly("3:INVALID_DATE", "4:MISSING_VALUE", "5:INVALID_COLUMNS");
        assertThat(count("child")).isEqualTo(childrenBefore);
        assertThat(admin.post(IMPORT, CSV_TYPE, "name;geburtstag\nA;B\n")
                        .json()
                        .get("errors")
                        .get(0)
                        .get("code")
                        .asString())
                .isEqualTo("INVALID_HEADER");
    }

    /** AC-ADM-01: files over 1 MB are rejected with 413. */
    @Test
    void largeFilesAreRejected() {
        ApiClient admin = signedInAs("ADMIN");
        String csv = "first_name;last_name;birth_date;family_key;external_ref\n" + "A;B;2018-01-01;k;\n".repeat(70_000);

        ApiClient.Response result = admin.post(IMPORT, CSV_TYPE, csv);

        assertThat(result.status()).isEqualTo(413);
        assertThat(result.json().get("code").asString()).isEqualTo("PAYLOAD_TOO_LARGE");
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
    }
}
