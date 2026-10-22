package de.elternstunden.contract;

import static org.assertj.core.api.Assertions.assertThat;

import de.elternstunden.support.ApiClient;
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

    /** The roles of 8.2 that exist so far; OP and MP follow with M3. */
    enum Role {
        ANONYMOUS,
        UNASSIGNED,
        FAMILY,
        ADMIN,
        MASTER;

        boolean allowedBy(List<String> roles) {
            return switch (this) {
                case ANONYMOUS -> roles.contains("A");
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
                int status = send(sessions.get(role), call);
                if (role.allowedBy(call.roles())) {
                    softly.assertThat(status)
                            .as("%s %s as %s", call.method(), call.path(), role)
                            .isNotIn(401, 403);
                } else {
                    softly.assertThat(status)
                            .as("%s %s as %s", call.method(), call.path(), role)
                            .isEqualTo(role == Role.ANONYMOUS ? 401 : 403);
                }
            }
        }
        softly.assertAll();
    }

    /**
     * AC-SEC-02 grows with every milestone: each operation for family members that takes an id needs an IDOR case
     * (family A accesses family B: 404). The list is empty until M4 brings the entries.
     */
    @Test
    void familyOperationsWithIdsHaveIdorCases() {
        Set<String> covered = Set.of();
        List<String> needingCases = calls().stream()
                .filter(call -> call.path().contains("{"))
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
        return sessions;
    }

    /** All operations except logout (it would end the session) and dev-only ones. */
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

    private static int send(ApiClient client, Call call) {
        String path = call.path().replaceAll("\\{[^}]+}", UUID.randomUUID().toString());
        return switch (call.method()) {
            case "GET" -> client.get(path).status();
            case "POST" -> client.post(path, "{}").status();
            case "PUT" -> client.put(path, "{}").status();
            case "PATCH" -> client.patch(path, "{}").status();
            case "DELETE" -> client.delete(path).status();
            default -> throw new IllegalStateException("unexpected method " + call.method());
        };
    }
}
