package de.elternstunden.family;

import static org.assertj.core.api.Assertions.assertThat;

import de.elternstunden.auth.domain.AppUser;
import de.elternstunden.family.domain.ChildData;
import de.elternstunden.family.domain.MatchResult;
import de.elternstunden.family.repo.FamilyRepository;
import de.elternstunden.family.service.ClaimService;
import de.elternstunden.support.ApiClient;
import de.elternstunden.support.IntegrationTest;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

/** Family assignment via child data (FS-REG 7.1, AC-REG). */
class AssignmentIT extends IntegrationTest {

    @Autowired
    private ClaimService claims;

    @Autowired
    private FamilyRepository families;

    @Autowired
    private TransactionTemplate tx;

    /**
     * AC-REG-01 and AC-REG-10: assignment only after the click on the link, in the same browser. The birth date
     * differs from the example in 15.1, because other tests register with that child and would hit its limit.
     */
    @Test
    void matchingChildDataAssignTheFamilyAfterTheLinkIsRedeemed() {
        UUID family = insertFamilyWithChild("Anna", "Müller", "2017-05-23");
        String email = uniqueEmail();
        ApiClient browser = browser();
        register(browser, email, "anna", "mueller", "2017-05-23");
        String token = latestToken(email);

        assertThat(jdbc.queryForMap(
                        "SELECT status::text AS status, family_id, email_verified_at FROM app_user WHERE email = ?",
                        email))
                .containsEntry("status", "PENDING_VERIFICATION")
                .containsEntry("family_id", null)
                .containsEntry("email_verified_at", null);
        assertThat(browser.get("/api/v1/me").status()).isEqualTo(401);

        assertThat(redeem(browser, token).status()).isEqualTo(200);

        assertThat(familyOf(email)).isEqualTo(family);
        assertThat(browser.get("/api/v1/me").json().get("assignedToFamily").asBoolean())
                .isTrue();
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM audit_log WHERE action = 'USER_ASSIGNED_TO_FAMILY'"
                                + " AND entity_id = ? AND details ->> 'method' = 'AUTO_MATCH'",
                        Integer.class,
                        userId(email).toString()))
                .isEqualTo(1);
    }

    /** AC-REG-07: one of two siblings is enough. */
    @Test
    void oneOfTwoSiblingsIsEnough() {
        UUID family = insertFamilyWithChild("Paul", "Sommer", "2016-02-01");
        insertChild(family, "Lotta", "Sommer", "2019-08-15");
        String email = uniqueEmail();

        registeredMember(email, "Lotta", "Sommer", "2019-08-15");

        assertThat(familyOf(email)).isEqualTo(family);
    }

    /** AC-REG-08: the same child data in two families stay unassigned. */
    @Test
    void ambiguousChildDataStayUnassigned() {
        insertFamilyWithChild("Max", "Winter", "2018-01-01");
        insertFamilyWithChild("Max", "Winter", "2018-01-01");
        String email = uniqueEmail();

        registeredMember(email, "Max", "Winter", "2018-01-01");

        assertThat(familyOf(email)).isNull();
        assertThat(openClaimResult(email)).isEqualTo("AMBIGUOUS");
    }

    /** AC-REG-03 and AC-REG-16: the second parent joins, the first one gets the mail, the partner hint goes off. */
    @Test
    void theSecondParentJoinsTheFamilyAndTheFirstIsNotified() {
        UUID family = insertFamilyWithChild("Ida", "Herbst", "2017-11-11");
        String first = uniqueEmail();
        registeredMember(first, "Ida", "Herbst", "2017-11-11");
        assertThat(families.activeMemberCount(family)).isEqualTo(1);
        mails.clear();

        String second = uniqueEmail();
        registeredMember(second, "Ida", "Herbst", "2017-11-11");

        assertThat(familyOf(second)).isEqualTo(family);
        assertThat(families.activeMemberCount(family)).as("partner hint off").isEqualTo(2);
        assertThat(awaitMails(first, 1))
                .singleElement()
                .satisfies(mail -> assertThat(mail.subject()).isEqualTo("Neues Mitglied in deiner Familie"))
                .satisfies(mail -> assertThat(mail.text()).contains("Test Parent wurde deiner Familie zugeordnet"));
        assertThat(mails.sentTo(second)).noneMatch(mail -> mail.subject().startsWith("Neues Mitglied"));
    }

    /** AC-REG-17: a family has at most two users (D-28); a third match stays unassigned for the admins. */
    @Test
    void aFullFamilyTakesNoThirdUser() {
        UUID family = insertFamilyWithChild("Tilda", "Voll", "2016-06-16");
        String first = uniqueEmail();
        String second = uniqueEmail();
        registeredMember(first, "Tilda", "Voll", "2016-06-16");
        registeredMember(second, "Tilda", "Voll", "2016-06-16");
        mails.clear();

        String third = uniqueEmail();
        ApiClient member = registeredMember(third, "Tilda", "Voll", "2016-06-16");

        assertThat(familyOf(third)).isNull();
        assertThat(member.get("/api/v1/me").json().get("assignedToFamily").asBoolean())
                .isFalse();
        assertThat(mails.sentTo(first)).noneMatch(mail -> mail.subject().startsWith("Neues Mitglied"));
        assertThat(jdbc.queryForObject(
                        "SELECT details ->> 'reason' FROM audit_log WHERE action = 'AUTO_ASSIGNMENT_SKIPPED'"
                                + " AND entity_id = ?",
                        String.class,
                        userId(third).toString()))
                .isEqualTo("FAMILY_FULL");

        ApiClient admin = signedInAs("ADMIN");
        ApiClient.Response full =
                admin.put("/api/v1/admin/users/" + userId(third) + "/family", Map.of("familyId", family));
        assertThat(full.status()).isEqualTo(409);
        assertThat(full.json().get("code").asString()).isEqualTo("FAMILY_FULL");

        assertThat(admin.delete("/api/v1/admin/users/" + userId(second) + "/family")
                        .status())
                .isEqualTo(204);
        assertThat(admin.put("/api/v1/admin/users/" + userId(third) + "/family", Map.of("familyId", family))
                        .status())
                .isEqualTo(200);
        assertThat(familyOf(third)).isEqualTo(family);
    }

    /** AC-REG-17: parallel assignments cannot pass the limit (INV-13). */
    @Test
    void parallelAssignmentsKeepTheLimit() throws Exception {
        UUID family = insertFamilyWithChild("Paul", "Parallel", "2016-07-17");
        registeredMember(uniqueEmail(), "Paul", "Parallel", "2016-07-17");
        UUID one = insertUser(uniqueEmail(), "FAMILY_MEMBER", "ACTIVE");
        UUID two = insertUser(uniqueEmail(), "FAMILY_MEMBER", "ACTIVE");
        ApiClient firstAdmin = signedInAs("ADMIN");
        ApiClient secondAdmin = signedInAs("ADMIN");

        List<Callable<Integer>> tasks = List.of(
                () -> firstAdmin
                        .put("/api/v1/admin/users/" + one + "/family", Map.of("familyId", family))
                        .status(),
                () -> secondAdmin
                        .put("/api/v1/admin/users/" + two + "/family", Map.of("familyId", family))
                        .status());
        List<Integer> statuses = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            for (Future<Integer> status : pool.invokeAll(tasks)) {
                statuses.add(status.get());
            }
        }

        assertThat(statuses).containsExactlyInAnyOrder(200, 409);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM app_user WHERE family_id = ?", Integer.class, family))
                .isEqualTo(2);
    }

    /** AC-REG-04: a typo leaves the user unassigned; the admin sees a candidate and assigns. */
    @Test
    void aTypoIsCorrectedByAnAdmin() {
        UUID family = insertFamilyWithChild("Anna", "Müller", "2015-03-03");
        String email = uniqueEmail();
        ApiClient member = registeredMember(email, "Anna", "Mueler", "2015-03-03");
        assertThat(familyOf(email)).isNull();
        assertThat(member.get("/api/v1/me")
                        .json()
                        .get("pendingClaim")
                        .get("lastName")
                        .asString())
                .isEqualTo("Mueler");

        ApiClient admin = signedInAs("ADMIN");
        ApiClient.Response dashboard = admin.get("/api/v1/admin/dashboard");
        assertThat(dashboard.status()).isEqualTo(200);
        var widget = dashboard
                .json()
                .get("unassignedUsers")
                .valueStream()
                .filter(entry -> entry.get("user").get("email").asString().equals(email))
                .findFirst()
                .orElseThrow();
        assertThat(widget.get("claim").get("matchResult").asString()).isEqualTo("NO_MATCH");
        assertThat(widget.get("candidates")
                        .valueStream()
                        .map(c -> c.get("familyId").asString()))
                .contains(family.toString());

        ApiClient.Response assigned =
                admin.put("/api/v1/admin/users/" + userId(email) + "/family", Map.of("familyId", family));

        assertThat(assigned.status()).isEqualTo(200);
        assertThat(member.get("/api/v1/me").json().get("assignedToFamily").asBoolean())
                .isTrue();
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM registration_claim WHERE user_id = ? AND resolved_at IS NOT NULL",
                        Integer.class,
                        userId(email)))
                .isEqualTo(1);
    }

    /** AC-REG-15: a claim from another browser is discarded; a new registration replaces the open claim. */
    @Test
    void claimsApplyOnlyInTheBrowserThatRegistered() {
        insertFamilyWithChild("Emil", "Frost", "2018-04-04");
        String email = uniqueEmail();
        ApiClient registering = browser();
        register(registering, email, "Wrong", "Child", "2018-04-04");
        register(registering, email, "Emil", "Frost", "2018-04-04");
        String token = latestToken(email);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM registration_claim WHERE user_id = ? AND superseded_at IS NULL",
                        Integer.class,
                        userId(email)))
                .as("the second registration replaced the first claim")
                .isEqualTo(1);

        ApiClient other = browser();
        ApiClient.Response response =
                other.post("/api/v1/auth/magic-link/redeem", Map.of("token", token, "confirmOtherDevice", true));

        assertThat(response.status()).isEqualTo(200);
        assertThat(familyOf(email)).isNull();
        assertThat(other.get("/api/v1/me").json().has("pendingClaim")).isFalse();
    }

    /** AC-REG-11 and 7.1.7: first entry is free, then 3 corrections; a match assigns at once. */
    @Test
    void unassignedUsersCorrectTheirDataAtMostThreeTimes() {
        UUID family = insertFamilyWithChild("Nora", "Kühn", "2019-09-09");
        String email = uniqueEmail();
        ApiClient member = registeredMember(email, "Nora", "Kuhn", "2019-09-09");
        assertThat(member.get("/api/v1/me").json().get("claimCorrectionsLeft").asInt())
                .isEqualTo(3);

        ApiClient.Response same = member.put("/api/v1/me/claim", child("Nora", "Kuhn", "2019-09-09"));
        assertThat(same.json().get("claimCorrectionsLeft").asInt())
                .as("the same data again")
                .isEqualTo(3);

        for (int i = 1; i <= 3; i++) {
            ApiClient.Response corrected = member.put("/api/v1/me/claim", child("Nora", "Kuhn" + i, "2019-09-09"));
            assertThat(corrected.status()).isEqualTo(200);
            assertThat(corrected.json().get("claimCorrectionsLeft").asInt()).isEqualTo(3 - i);
        }
        ApiClient.Response exhausted = member.put("/api/v1/me/claim", child("Nora", "Kühn", "2019-09-09"));
        assertThat(exhausted.status()).isEqualTo(409);
        assertThat(exhausted.json().get("code").asString()).isEqualTo("CLAIM_CORRECTION_LIMIT");
        assertThat(member.put("/api/v1/me/claim", child("Nora", "Kuhn3", "2019-09-09"))
                        .status())
                .as("the same data again still matches anew")
                .isEqualTo(200);

        // A discarded claim: the next entry is the first one again and does not count
        jdbc.update("UPDATE registration_claim SET superseded_at = now() WHERE user_id = ?", userId(email));
        ApiClient.Response matched = member.put("/api/v1/me/claim", child("Nora", "Kühn", "2019-09-09"));
        assertThat(matched.status()).isEqualTo(200);
        assertThat(matched.json().get("assignedToFamily").asBoolean()).isTrue();
        assertThat(familyOf(email)).isEqualTo(family);

        ApiClient.Response again = member.put("/api/v1/me/claim", child("Nora", "Kühn", "2019-09-09"));
        assertThat(again.status()).isEqualTo(409);
        assertThat(again.json().get("code").asString()).isEqualTo("ALREADY_ASSIGNED");
    }

    /** AC-REG-05: from the 6th claim for the same child within 24 hours on, THROTTLED without lookup. */
    @Test
    void theSixthClaimForTheSameChildIsThrottled() {
        insertFamilyWithChild("Theo", "Regen", "2016-06-06");
        List<String> emails = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            String email = uniqueEmail();
            emails.add(email);
            register(browser(), email, "Theo", "Regen", "2016-06-06");
        }
        processAuthRequests();

        List<String> results = emails.stream().map(this::openClaimResult).toList();
        assertThat(results.subList(0, 5)).containsOnly("MATCHED");
        assertThat(results.get(5)).isEqualTo("THROTTLED");
    }

    /** AC-REG-13: 10 parallel claims for the same child yield at most 5 that are not THROTTLED. */
    @Test
    void parallelClaimsCannotExceedTheLimit() throws Exception {
        List<UUID> users = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            users.add(insertUser(uniqueEmail(), "FAMILY_MEMBER", "ACTIVE"));
        }
        ChildData child = new ChildData("Parallel", "Kind", LocalDate.of(2017, 1, 1));
        List<Callable<MatchResult>> tasks = users.stream()
                .<Callable<MatchResult>>map(user -> () -> tx.execute(status ->
                        claims.create(user, child, null, clock.instant()).result()))
                .toList();
        List<MatchResult> results = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(10)) {
            for (Future<MatchResult> result : pool.invokeAll(tasks)) {
                results.add(result.get());
            }
        }

        assertThat(results.stream().filter(result -> result != MatchResult.THROTTLED))
                .hasSizeLessThanOrEqualTo(5);
    }

    /** Activation without an open claim leaves the account alone. */
    @Test
    void activationWithoutClaimChangesNothing() {
        String email = uniqueEmail();
        UUID id = insertUser(email, "FAMILY_MEMBER", "ACTIVE");
        tx.executeWithoutResult(status -> claims.onActivation(
                new AppUser(
                        id,
                        email,
                        "Test",
                        de.elternstunden.auth.domain.UserRole.FAMILY_MEMBER,
                        de.elternstunden.auth.domain.UserStatus.ACTIVE,
                        null,
                        false),
                java.util.Optional.empty(),
                clock.instant()));

        assertThat(familyOf(email)).isNull();
    }

    private String openClaimResult(String email) {
        return jdbc.queryForObject(
                "SELECT match_result FROM registration_claim"
                        + " WHERE user_id = ? AND resolved_at IS NULL AND superseded_at IS NULL",
                String.class,
                userId(email));
    }

    private static Map<String, String> child(String first, String last, String birth) {
        return Map.of("firstName", first, "lastName", last, "birthDate", birth);
    }
}
