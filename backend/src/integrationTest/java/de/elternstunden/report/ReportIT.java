package de.elternstunden.report;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import de.elternstunden.support.ApiClient;
import de.elternstunden.support.IntegrationTest;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

/**
 * Billing PDF (FS-PDF 7.8, AC-PDF, AC-SEC-12). Every test has its own school year far from other data; "today" is set
 * before signing in, because the session duration is measured with the same clock.
 */
class ReportIT extends IntegrationTest {

    private static final String BILLING = "/api/v1/admin/reports/billing";

    /** AC-PDF-01: the interim total list names all families owing hours, without e-mails or birth dates. */
    @Test
    void interimTotalListShowsFamiliesWithoutPersonalDetails() {
        clock.setOffsetTo(Instant.parse("2081-01-15T10:00:00Z"));
        UUID year = schoolYear("2080/81", "2080-09-01", "2081-08-31");
        UUID amsel = enrolledFamily(year, "Amsel", "Anni", "2017-04-05");
        UUID buche = enrolledFamily(year, "Buche", "Bert", "2018-06-07");
        String email = memberOf(amsel);
        insertEntry(amsel, year, "2081-01-10", 600, "Ganzes Soll", true);
        insertEntry(buche, year, "2081-01-11", 90, "Teilweise", true);
        insertFamilyWithChild("Ohne", "Einschreibung", "2016-01-01");
        ApiClient admin = signedInAs("ADMIN");

        ApiClient.Download pdf = admin.download(BILLING + "?scope=ALL&schoolYearId=" + year);

        assertThat(pdf.status()).isEqualTo(200);
        assertThat(pdf.headers().firstValue("Content-Type")).hasValue("application/pdf");
        assertThat(pdf.headers().firstValue("Content-Disposition"))
                .hasValueSatisfying(value -> assertThat(value).contains("elternstunden_2080-81_ALL_2081-01-15.pdf"));
        String text = text(pdf.body());
        assertThat(text)
                .contains("Zwischenstand, Stand 15.01.2081")
                .contains("Schuljahr 2080/81")
                .contains("Anni Amsel", "Bert Buche", "Erfüllt", "Fehlt", "8 h 30 min")
                .contains("2 Familien, davon 1 erfüllt")
                .contains("Seite 1/1")
                .doesNotContain("Einschreibung")
                .doesNotContain(email)
                .doesNotContain("05.04.2017", "2017-04-05", "07.06.2018");
    }

    /** AC-PDF-02: only confirmed entries in the main table, open ones separately and not counted. */
    @Test
    void familyBillingSeparatesOpenEntries() {
        clock.setOffsetTo(Instant.parse("2082-01-15T10:00:00Z"));
        UUID year = schoolYear("2081/82", "2081-09-01", "2082-08-31");
        UUID family = enrolledFamily(year, "Clever", "Cora", "2017-02-02");
        insertEntry(family, year, "2081-10-01", 120, "Laternenfest geholfen", true);
        insertEntry(family, year, "2081-11-01", 45, "Kuchen gebacken", false);
        ApiClient admin = signedInAs("ADMIN");

        ApiClient.Download pdf = admin.download(BILLING + "?scope=FAMILY&familyId=" + family + "&schoolYearId=" + year);

        assertThat(pdf.status()).isEqualTo(200);
        String text = text(pdf.body());
        int confirmed = text.indexOf("Laternenfest geholfen");
        int separator = text.indexOf("Noch nicht bestätigt (zählt nicht)");
        int open = text.indexOf("Kuchen gebacken");
        assertThat(confirmed).isPositive().isLessThan(separator);
        assertThat(open).isGreaterThan(separator);
        assertThat(text).contains("Abrechnung Clever", "Cora Clever", "Fehlt noch 8 h", "01.10.2081");
    }

    /** AC-PDF-03: no final billing of a running school year; afterwards it is the default. */
    @Test
    void finalBillingOnlyForFinishedYears() {
        clock.setOffsetTo(Instant.parse("2083-01-15T10:00:00Z"));
        UUID year = schoolYear("2082/83", "2082-09-01", "2083-08-31");
        enrolledFamily(year, "Dachs", "Dana", "2018-01-01");
        ApiClient running = signedInAs("ADMIN");

        ApiClient.Response early = running.get(BILLING + "?scope=ALL&kind=FINAL&schoolYearId=" + year);
        assertThat(early.status()).isEqualTo(409);
        assertThat(early.json().get("code").asString()).isEqualTo("SCHOOL_YEAR_NOT_FINISHED");

        clock.setOffsetTo(Instant.parse("2083-09-02T10:00:00Z"));
        ApiClient later = signedInAs("ADMIN");
        ApiClient.Download pdf = later.download(BILLING + "?scope=ALL&schoolYearId=" + year);
        assertThat(pdf.status()).isEqualTo(200);
        assertThat(text(pdf.body())).contains("Abschluss Schuljahr 2082/83").doesNotContain("Zwischenstand");
    }

    /** AC-PDF-04: the total list of 1,000 families in at most 5 seconds. */
    @Test
    void totalListOfAThousandFamiliesIsFast() {
        clock.setOffsetTo(Instant.parse("2084-01-15T10:00:00Z"));
        UUID year = schoolYear("2083/84", "2083-09-01", "2084-08-31");
        UUID creator = insertUser(uniqueEmail(), "ADMIN", "ACTIVE");
        jdbc.update("""
                WITH families AS (
                    INSERT INTO family (label) SELECT 'Last ' || n FROM generate_series(1, 1000) n RETURNING id, label),
                children AS (
                    INSERT INTO child (family_id, first_name, last_name, birth_date, first_name_norm, last_name_norm)
                    SELECT id, 'Kind', label, DATE '2018-01-01', 'kind', lower(label) FROM families RETURNING id, family_id),
                enrolled AS (
                    INSERT INTO child_enrollment (child_id, school_year_id) SELECT id, ? FROM children)
                INSERT INTO time_entry (family_id, school_year_id, created_by, work_date, duration_minutes, description,
                                        status, confirmed_by, confirmed_at)
                SELECT family_id, ?, ?, DATE '2083-10-01', 120, 'Last', 'CONFIRMED', ?, now() FROM children
                """, year, year, creator, creator);
        ApiClient admin = signedInAs("ADMIN");

        long start = System.nanoTime();
        ApiClient.Download pdf = admin.download(BILLING + "?scope=ALL&schoolYearId=" + year);
        Duration took = Duration.ofNanos(System.nanoTime() - start);

        assertThat(pdf.status()).isEqualTo(200);
        assertThat(took).isLessThanOrEqualTo(Duration.ofSeconds(5));
        assertThat(text(pdf.body())).contains("1000 Familien, davon 0 erfüllt");
    }

    /** AC-PDF-05: every generation is audited, with parameters only. */
    @Test
    void everyGenerationIsAudited() {
        clock.setOffsetTo(Instant.parse("2085-01-15T10:00:00Z"));
        UUID year = schoolYear("2084/85", "2084-09-01", "2085-08-31");
        UUID family = enrolledFamily(year, "Eule", "Emma", "2017-03-03");
        ApiClient admin = signedInAs("ADMIN");

        admin.download(BILLING + "?scope=ALL&schoolYearId=" + year);
        admin.download(BILLING + "?scope=FAMILY&familyId=" + family + "&schoolYearId=" + year);

        assertThat(jdbc.queryForList(
                        "SELECT details ->> 'scope' FROM audit_log WHERE action = 'REPORT_GENERATED' AND entity_id = ?"
                                + " ORDER BY id",
                        String.class,
                        year.toString()))
                .containsExactly("ALL", "FAMILY");
    }

    /** AC-SEC-12: markup in a description is text, and rendering touches no network. */
    @Test
    void markupStaysTextAndNothingIsLoaded() throws IOException {
        AtomicInteger requests = new AtomicInteger();
        HttpServer trap = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        trap.createContext("/", exchange -> {
            requests.incrementAndGet();
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        trap.start();
        try {
            clock.setOffsetTo(Instant.parse("2086-01-15T10:00:00Z"));
            UUID year = schoolYear("2085/86", "2085-09-01", "2086-08-31");
            UUID family = enrolledFamily(year, "Fuchs", "Fritz", "2017-05-05");
            String url = "http://127.0.0.1:" + trap.getAddress().getPort() + "/leak.png";
            insertEntry(family, year, "2085-10-01", 60, "<img src=\"" + url + "\"/> <b>fett</b>", true);
            insertEntry(family, year, "2085-10-02", 60, "<link rel=\"stylesheet\" href=\"" + url + "\"/>", false);
            ApiClient admin = signedInAs("ADMIN");

            ApiClient.Download pdf =
                    admin.download(BILLING + "?scope=FAMILY&familyId=" + family + "&schoolYearId=" + year);

            assertThat(pdf.status()).isEqualTo(200);
            assertThat(text(pdf.body()).replaceAll("\\s+", "")).contains("<imgsrc=", "<b>fett</b>", "<linkrel=");
            assertThat(requests).hasValue(0);
        } finally {
            trap.stop(0);
        }
    }

    /** FAMILY needs a family that exists; family members get no report. */
    @Test
    void familyScopeNeedsAnExistingFamily() {
        ApiClient admin = signedInAs("ADMIN");
        UUID year = schoolYear("2086/87", "2086-09-01", "2087-08-31");

        assertThat(admin.get(BILLING + "?scope=FAMILY&schoolYearId=" + year).status())
                .isEqualTo(400);
        assertThat(admin.get(BILLING + "?scope=FAMILY&familyId=" + UUID.randomUUID() + "&schoolYearId=" + year)
                        .status())
                .isEqualTo(404);
        assertThat(admin.get(BILLING + "?scope=ALL&schoolYearId=" + UUID.randomUUID())
                        .status())
                .isEqualTo(404);
    }

    private static String text(byte[] pdf) {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            // In reading order on the page, like a person sees it
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return stripper.getText(document);
        } catch (IOException ex) {
            throw new AssertionError("no PDF", ex);
        }
    }

    private UUID enrolledFamily(UUID year, String lastName, String firstName, String birthDate) {
        UUID family = insertFamilyWithChild(firstName, lastName, birthDate);
        jdbc.update(
                "INSERT INTO child_enrollment (child_id, school_year_id) SELECT id, ? FROM child WHERE family_id = ?",
                year,
                family);
        return family;
    }

    private String memberOf(UUID family) {
        String email = uniqueEmail();
        UUID user = insertUser(email, "FAMILY_MEMBER", "ACTIVE");
        jdbc.update("UPDATE app_user SET family_id = ? WHERE id = ?", family, user);
        return email;
    }

    private void insertEntry(UUID family, UUID year, String date, int minutes, String description, boolean confirmed) {
        UUID creator = insertUser(uniqueEmail(), "ADMIN", "ACTIVE");
        jdbc.update(
                "INSERT INTO time_entry (family_id, school_year_id, created_by, work_date, duration_minutes, description,"
                        + " status, confirmed_by, confirmed_at)"
                        + " VALUES (?, ?, ?, CAST(? AS date), ?, ?, CAST(? AS entry_status), ?, ?)",
                family,
                year,
                creator,
                date,
                minutes,
                description,
                confirmed ? "CONFIRMED" : "OPEN",
                confirmed ? creator : null,
                confirmed ? java.sql.Timestamp.from(clock.instant()) : null);
    }
}
