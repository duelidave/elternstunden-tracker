package de.elternstunden.user;

import static org.assertj.core.api.Assertions.assertThat;

import de.elternstunden.family.service.ClaimCleanupJob;
import de.elternstunden.support.ApiClient;
import de.elternstunden.support.IntegrationTest;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** User administration, sessions that follow the account and data protection (FS-ADM 7.7, D-20, DP-05). */
class UserAdminIT extends IntegrationTest {

    @Autowired
    private ClaimCleanupJob claimCleanup;

    /** AC-AUTH-18: disabling ends every session at once; enabling allows a new login. */
    @Test
    void disablingEndsAllSessions() {
        String email = uniqueEmail();
        insertUser(email, "FAMILY_MEMBER", "ACTIVE");
        ApiClient phone = signedIn(email);
        ApiClient laptop = signedIn(email);
        ApiClient admin = signedInAs("ADMIN");

        ApiClient.Response disabled = admin.post("/api/v1/admin/users/" + userId(email) + "/disable");

        assertThat(disabled.json().get("status").asString()).isEqualTo("DISABLED");
        assertThat(phone.get("/api/v1/me").status()).isEqualTo(401);
        assertThat(laptop.get("/api/v1/me").status()).isEqualTo(401);
        assertThat(sessionsOf(email)).isZero();

        admin.post("/api/v1/admin/users/" + userId(email) + "/enable");
        assertThat(signedIn(email).get("/api/v1/me").status()).isEqualTo(200);
    }

    /** AC-AUTH-18: role and family changes apply to existing sessions immediately. */
    @Test
    void roleAndFamilyChangesApplyImmediately() {
        String email = uniqueEmail();
        insertUser(email, "FAMILY_MEMBER", "ACTIVE");
        ApiClient member = signedIn(email);
        ApiClient admin = signedInAs("ADMIN");
        assertThat(member.get("/api/v1/admin/dashboard").status()).isEqualTo(403);
        UUID family = insertFamilyWithChild("Rolf", "Rolle", "2018-02-02");

        admin.put("/api/v1/admin/users/" + userId(email) + "/role", Map.of("role", "ADMIN"));
        admin.put("/api/v1/admin/users/" + userId(email) + "/family", Map.of("familyId", family));

        assertThat(member.get("/api/v1/admin/dashboard").status()).isEqualTo(200);
        assertThat(member.get("/api/v1/me").json().get("assignedToFamily").asBoolean())
                .isTrue();

        admin.delete("/api/v1/admin/users/" + userId(email) + "/family");
        admin.put("/api/v1/admin/users/" + userId(email) + "/role", Map.of("role", "FAMILY_MEMBER"));
        assertThat(member.get("/api/v1/admin/dashboard").status()).isEqualTo(403);
        assertThat(member.get("/api/v1/me").json().get("assignedToFamily").asBoolean())
                .isFalse();
    }

    /** AC-AUTH-11: an admin session ends after 30 minutes without activity. */
    @Test
    void adminSessionsEndAfterThirtyIdleMinutes() {
        String email = uniqueEmail();
        insertUser(email, "ADMIN", "ACTIVE");
        ApiClient admin = signedIn(email);
        assertThat(jdbc.queryForObject(
                        "SELECT max_inactive_interval FROM spring_session WHERE principal_name = ?",
                        Integer.class,
                        userId(email).toString()))
                .isEqualTo(30 * 60);

        // Spring Session measures idle time itself, so the last access is moved back in the session table
        long thirtyOneMinutesAgo =
                System.currentTimeMillis() - Duration.ofMinutes(31).toMillis();
        jdbc.update(
                "UPDATE spring_session SET last_access_time = ?, expiry_time = ? WHERE principal_name = ?",
                thirtyOneMinutesAgo,
                thirtyOneMinutesAgo + Duration.ofMinutes(30).toMillis(),
                userId(email).toString());

        assertThat(admin.get("/api/v1/me").status()).isEqualTo(401);
    }

    @Test
    void adminsCanEndAllSessionsOfAUser() {
        String email = uniqueEmail();
        insertUser(email, "FAMILY_MEMBER", "ACTIVE");
        ApiClient member = signedIn(email);
        ApiClient admin = signedInAs("ADMIN");

        assertThat(admin.post("/api/v1/admin/users/" + userId(email) + "/terminate-sessions")
                        .status())
                .isEqualTo(204);

        assertThat(member.get("/api/v1/me").status()).isEqualTo(401);
        assertThat(signedIn(email).get("/api/v1/me").status())
                .as("still active")
                .isEqualTo(200);
    }

    /** AC-DP-01: anonymized accounts keep no personal data, tokens, claims or sessions; entries stay. */
    @Test
    void anonymizationRemovesPersonalData() {
        UUID family = insertFamilyWithChild("Dora", "Daten", "2017-02-02");
        String email = uniqueEmail();
        ApiClient member = registeredMember(email, "Dora", "Daten", "2017-02-02");
        UUID id = userId(email);
        UUID year = jdbc.queryForObject(
                "INSERT INTO school_year (label, starts_on, ends_on) VALUES ('Anon 2045', DATE '2045-09-01',"
                        + " DATE '2046-08-31') RETURNING id",
                UUID.class);
        jdbc.update(
                "INSERT INTO time_entry (family_id, school_year_id, created_by, work_date, duration_minutes, description)"
                        + " VALUES (?, ?, ?, DATE '2045-10-01', 60, 'Kuchen gebacken')",
                family,
                year,
                id);
        requestMagicLink(browser(), email);
        processAuthRequests();
        ApiClient admin = signedInAs("ADMIN");

        ApiClient.Response anonymized = admin.post("/api/v1/admin/users/" + id + "/anonymize");

        assertThat(anonymized.status()).isEqualTo(200);
        assertThat(anonymized.json().get("email").asString()).isEqualTo("deleted-" + id + "@invalid");
        assertThat(anonymized.json().get("displayName").asString()).isEqualTo("Gelöschter Nutzer");
        assertThat(anonymized.json().get("status").asString()).isEqualTo("DISABLED");
        assertThat(count("user_token", id)).isZero();
        assertThat(count("registration_claim", id)).isZero();
        assertThat(count("user_identity", id)).isZero();
        assertThat(member.get("/api/v1/me").status()).isEqualTo(401);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM time_entry WHERE created_by = ?", Integer.class, id))
                .isEqualTo(1);
        ApiClient.Response enable = admin.post("/api/v1/admin/users/" + id + "/enable");
        assertThat(enable.json().get("code").asString()).isEqualTo("USER_ANONYMIZED");
    }

    /** AC-DP-02: done and superseded claims go after 30 days, the open claim stays. */
    @Test
    void oldClaimsAreDeletedAfterThirtyDays() {
        String unassigned = uniqueEmail();
        registeredMember(unassigned, "Offen", "Bleibt", "2018-03-03");
        UUID family = insertFamilyWithChild("Erle", "Digt", "2018-04-04");
        String assigned = uniqueEmail();
        registeredMember(assigned, "Erle", "Digt", "2018-04-04");
        assertThat(familyOf(assigned)).isEqualTo(family);

        clock.advance(Duration.ofDays(29));
        claimCleanup.run();
        assertThat(count("registration_claim", userId(assigned))).isEqualTo(1);

        clock.advance(Duration.ofDays(2));
        claimCleanup.run();
        assertThat(count("registration_claim", userId(assigned)))
                .as("resolved, older than 30 days")
                .isZero();
        assertThat(count("registration_claim", userId(unassigned)))
                .as("open claim stays")
                .isEqualTo(1);
    }

    /** D-22 and lock-out protection. */
    @Test
    void theMasterAndTheOwnAccountAreProtected() {
        String email = uniqueEmail();
        insertUser(email, "ADMIN", "ACTIVE");
        ApiClient admin = signedIn(email);
        UUID master = userId(MASTER_EMAIL);

        ApiClient.Response disableMaster = admin.post("/api/v1/admin/users/" + master + "/disable");
        ApiClient.Response disableSelf = admin.post("/api/v1/admin/users/" + userId(email) + "/disable");
        ApiClient.Response demoteSelf =
                admin.put("/api/v1/admin/users/" + userId(email) + "/role", Map.of("role", "FAMILY_MEMBER"));

        assertThat(disableMaster.json().get("code").asString()).isEqualTo("ACCOUNT_MANAGED_BY_CONFIG");
        assertThat(disableSelf.json().get("code").asString()).isEqualTo("OWN_ACCOUNT");
        assertThat(demoteSelf.json().get("code").asString()).isEqualTo("OWN_ACCOUNT");
        assertThat(admin.get("/api/v1/admin/users/" + UUID.randomUUID()).status())
                .isEqualTo(404);
    }

    @Test
    void usersAreSearchableAndFilterable() {
        String email = uniqueEmail();
        insertUser(email, "FAMILY_MEMBER", "DISABLED");
        ApiClient admin = signedInAs("ADMIN");

        ApiClient.Response found = admin.get("/api/v1/admin/users?status=DISABLED&q=" + email.substring(0, 20));

        assertThat(found.status()).isEqualTo(200);
        assertThat(found.json()
                        .get("content")
                        .valueStream()
                        .map(user -> user.get("email").asString()))
                .containsExactly(email);
        assertThat(found.json().get("totalElements").asInt()).isEqualTo(1);
    }

    private int count(String table, UUID userId) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE user_id = ?", Integer.class, userId);
    }

    private int sessionsOf(String email) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM spring_session WHERE principal_name = ?",
                Integer.class,
                userId(email).toString());
    }
}
