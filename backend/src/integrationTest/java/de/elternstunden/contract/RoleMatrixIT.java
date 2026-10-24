package de.elternstunden.contract;

import static org.assertj.core.api.Assertions.assertThat;

import de.elternstunden.auth.service.Totp;
import de.elternstunden.support.ApiClient;
import de.elternstunden.support.FakeOidcProvider;
import de.elternstunden.support.IntegrationTest;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.parser.OpenAPIV3Parser;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * AC-SEC-09: for every operation of the contract and every role, the status follows {@code x-roles}. A role that is
 * not allowed gets 401 (anonymous) or 403 (signed in); an allowed role never does. Path ids are random and bodies
 * empty, so allowed calls end in 400 or 404 without changing anything.
 */
class RoleMatrixIT extends IntegrationTest {

    private static OpenAPI contract;

    /** The roles of 8.2. */
    enum Role {
        ANONYMOUS,
        UNASSIGNED,
        FAMILY,
        ADMIN,
        MASTER,
        OIDC_PENDING,
        MFA_PENDING;

        /** Half-finished logins count as not signed in: 401 like anonymous (AC-OIDC-10, AC-AUTH-23). */
        boolean signedIn() {
            return this == UNASSIGNED || this == FAMILY || this == ADMIN || this == MASTER;
        }

        boolean allowedBy(List<String> roles) {
            return switch (this) {
                case ANONYMOUS -> roles.contains("A");
                case OIDC_PENDING -> roles.contains("A") || roles.contains("OP");
                case MFA_PENDING -> roles.contains("A") || roles.contains("MP");
                case UNASSIGNED -> roles.contains("A") || roles.contains("U");
                case FAMILY -> roles.contains("A") || roles.contains("U") || roles.contains("F");
                case ADMIN -> roles.contains("A") || roles.contains("U") || roles.contains("AD");
                case MASTER ->
                    roles.contains("A") || roles.contains("U") || roles.contains("AD") || roles.contains("M");
            };
        }
    }

    record Call(String method, String path, List<String> roles) {}

    @BeforeAll
    static void readContract() {
        contract = new OpenAPIV3Parser().read(System.getProperty("elternstunden.contract"));
    }

    @Test
    void everyRoleGetsTheStatusItsRolesAllow() {
        Map<Role, ApiClient> sessions = sessions();
        SoftAssertions softly = new SoftAssertions();
        for (Call call : calls()) {
            for (Role role : Role.values()) {
                ApiClient.Response response = send(sessions.get(role), call);
                int status = response.status();
                if (role.allowedBy(call.roles())) {
                    // an allowed role may still need a fresh login for sensitive changes (7.2)
                    boolean reauth = status == 403 && response.body().contains("REAUTH_REQUIRED");
                    // the master is U, but has only password and TOTP (INV-12): no passkeys
                    boolean masterOnlyPassword =
                            role == Role.MASTER && status == 403 && call.path().endsWith("/me/passkeys/options");
                    softly.assertThat(reauth || masterOnlyPassword ? 200 : status)
                            .as("%s %s as %s", call.method(), call.path(), role)
                            .isNotIn(401, 403);
                } else {
                    softly.assertThat(status)
                            .as("%s %s as %s", call.method(), call.path(), role)
                            .isEqualTo(role.signedIn() ? 403 : 401);
                }
            }
        }
        softly.assertAll();
    }

    /**
     * AC-SEC-02 grows with every milestone: each operation for signed-in users that takes a resource id needs an IDOR
     * case (someone else's resource: 404). Where the case lives is noted next to the operation.
     */
    @Test
    void familyOperationsWithIdsHaveIdorCases() {
        Set<String> covered = Set.of(
                // OidcIT.foreignIdentitiesAreNotFound
                "DELETE /api/v1/me/identities/{identityId}",
                // PasskeyIT.ownPasskeysOnly
                "DELETE /api/v1/me/passkeys/{passkeyId}",
                // EntryIT.entriesOfOtherFamiliesAreNotFound
                "PATCH /api/v1/me/family/entries/{entryId}",
                "DELETE /api/v1/me/family/entries/{entryId}");
        List<String> needingCases = calls().stream()
                .filter(call -> call.path().matches(".*\\{[a-zA-Z]+Id}.*"))
                .filter(call -> call.roles().contains("F") || call.roles().contains("U"))
                .map(call -> call.method() + " " + call.path())
                .toList();

        assertThat(needingCases).containsExactlyInAnyOrderElementsOf(covered);
    }

    private Map<Role, ApiClient> sessions() {
        Map<Role, ApiClient> sessions = new LinkedHashMap<>();
        sessions.put(Role.ANONYMOUS, browser());
        sessions.put(Role.UNASSIGNED, signedInAs("FAMILY_MEMBER"));
        String family = uniqueEmail();
        insertUser(family, "FAMILY_MEMBER", "ACTIVE");
        jdbc.update(
                "UPDATE app_user SET family_id = ? WHERE email = ?",
                insertFamilyWithChild("Matrix", "Kind", "2018-01-01"),
                family);
        sessions.put(Role.FAMILY, signedIn(family));
        sessions.put(Role.ADMIN, signedInAs("ADMIN"));
        sessions.put(Role.MASTER, signedInMaster());
        ApiClient oidcPending = browser();
        oidcLogin(
                oidcPending,
                FakeOidcProvider.Login.of("matrix-" + UUID.randomUUID(), "matrix-" + UUID.randomUUID() + "@gmail.com"));
        sessions.put(Role.OIDC_PENDING, oidcPending);
        sessions.put(Role.MFA_PENDING, mfaPending());
        return sessions;
    }

    /** An admin with TOTP after the first factor. */
    private ApiClient mfaPending() {
        String email = uniqueEmail();
        insertUser(email, "ADMIN", "ACTIVE");
        ApiClient admin = signedIn(email);
        String secret = admin.post("/api/v1/me/totp/setup").json().get("secret").asString();
        clock.advance(java.time.Duration.ofSeconds(Totp.STEP_SECONDS));
        admin.post(
                "/api/v1/me/totp/confirm",
                Map.of(
                        "code",
                        Totp.codeAt(Totp.decodeBase32(secret), clock.instant().getEpochSecond() / Totp.STEP_SECONDS)));
        ApiClient pending = browser();
        requestMagicLink(pending, email);
        redeem(pending, latestToken(email));
        return pending;
    }

    /** All operations except logout (it would end the session) and dev-only ones; PUT/POST/PATCH with an empty body. */
    private static List<Call> calls() {
        String base = contract.getServers().getFirst().getUrl();
        List<Call> calls = new ArrayList<>();
        contract.getPaths().forEach((path, item) -> {
            for (Map.Entry<PathItem.HttpMethod, Operation> entry :
                    item.readOperationsMap().entrySet()) {
                Operation operation = entry.getValue();
                boolean devOnly = operation.getExtensions() != null
                        && Boolean.TRUE.equals(operation.getExtensions().get("x-dev-only"));
                if (devOnly || "logout".equals(operation.getOperationId())) {
                    continue;
                }
                Object roles = operation.getExtensions() == null
                        ? null
                        : operation.getExtensions().get("x-roles");
                calls.add(new Call(
                        entry.getKey().name(),
                        base + path,
                        roles instanceof List<?> list
                                ? list.stream().map(String::valueOf).toList()
                                : List.of()));
            }
        });
        return calls;
    }

    private static ApiClient.Response send(ApiClient client, Call call) {
        String path = call.path().replaceAll("\\{[^}]+}", UUID.randomUUID().toString());
        return switch (call.method()) {
            case "GET" -> client.get(path);
            case "POST" -> client.post(path, "{}");
            case "PUT" -> client.put(path, "{}");
            case "PATCH" -> client.patch(path, "{}");
            case "DELETE" -> client.delete(path);
            default -> throw new IllegalStateException("unexpected method " + call.method());
        };
    }
}
