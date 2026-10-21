package de.elternstunden.contract;

import static org.assertj.core.api.Assertions.assertThat;

import de.elternstunden.support.IntegrationTest;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.parser.OpenAPIV3Parser;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * API-first guard (D-25, SEC-01, AC-SEC-01): the registered handlers under {@code /api} match the operations of the
 * contract one to one, and every operation declares its roles in {@code x-roles}. Operations marked
 * {@code x-dev-only} must have no handler outside the dev profile (AC-DEV-01).
 */
class ContractCoverageIT extends IntegrationTest {

    private static final Set<String> ROLES = Set.of("A", "U", "F", "AD", "M", "OP", "MP");

    private static OpenAPI contract;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    record Endpoint(String method, String path) {}

    record ContractOperation(Endpoint endpoint, Operation operation) {

        boolean devOnly() {
            return operation.getExtensions() != null
                    && Boolean.TRUE.equals(operation.getExtensions().get("x-dev-only"));
        }
    }

    @BeforeAll
    static void readContract() {
        contract = new OpenAPIV3Parser().read(System.getProperty("elternstunden.contract"));
        assertThat(contract).as("contract api/openapi.yaml").isNotNull();
    }

    @Test
    void everyContractOperationHasAHandlerAndNoHandlerIsOutsideTheContract() {
        Set<Endpoint> inContract = contractOperations()
                .filter(op -> !op.devOnly())
                .map(ContractOperation::endpoint)
                .collect(Collectors.toSet());

        Set<Endpoint> implemented = new HashSet<>();
        handlerMapping.getHandlerMethods().keySet().forEach(info -> implemented.addAll(apiEndpoints(info)));

        assertThat(implemented).containsExactlyInAnyOrderElementsOf(inContract);
    }

    @Test
    void devOnlyOperationsHaveNoHandlerOutsideTheDevProfile() {
        Set<Endpoint> devOnly = contractOperations()
                .filter(ContractOperation::devOnly)
                .map(ContractOperation::endpoint)
                .collect(Collectors.toSet());
        Set<Endpoint> implemented = new HashSet<>();
        handlerMapping.getHandlerMethods().keySet().forEach(info -> implemented.addAll(apiEndpoints(info)));

        assertThat(devOnly).isNotEmpty();
        assertThat(implemented).doesNotContainAnyElementsOf(devOnly);
    }

    @Test
    void everyOperationDeclaresKnownRoles() {
        contractOperations().forEach(op -> {
            List<String> roles = roleNames(op.operation());
            assertThat(roles).as("x-roles of %s", op.endpoint()).isNotEmpty();
            assertThat(ROLES).as("x-roles of %s", op.endpoint()).containsAll(roles);
        });
    }

    @Test
    void anonymousGetOperationsAreReachableWithoutLogin() {
        List<ContractOperation> anonymous = contractOperations()
                .filter(op -> op.endpoint().method().equals("GET"))
                .filter(op -> !op.endpoint().path().contains("{"))
                .filter(op -> roleNames(op.operation()).contains("A"))
                .toList();
        for (ContractOperation op : anonymous) {
            int status = browser().get(op.endpoint().path()).status();
            assertThat(status).as("anonymous %s", op.endpoint()).isNotIn(401, 403);
        }
    }

    @Test
    void operationsForSignedInUsersRejectAnonymousCalls() {
        List<ContractOperation> signedIn = contractOperations()
                .filter(op -> !roleNames(op.operation()).contains("A"))
                .filter(op -> !op.endpoint().path().contains("{"))
                .toList();
        assertThat(signedIn).isNotEmpty();
        for (ContractOperation op : signedIn) {
            var client = browser();
            int status = op.endpoint().method().equals("GET")
                    ? client.get(op.endpoint().path()).status()
                    : client.post(op.endpoint().path(), "{}").status();
            assertThat(status).as("anonymous %s", op.endpoint()).isEqualTo(401);
        }
    }

    private static Stream<ContractOperation> contractOperations() {
        String base = contract.getServers().getFirst().getUrl();
        return contract.getPaths().entrySet().stream().flatMap(path -> {
            Map<PathItem.HttpMethod, Operation> operations = path.getValue().readOperationsMap();
            return operations.entrySet().stream()
                    .map(entry -> new ContractOperation(
                            new Endpoint(entry.getKey().name(), base + path.getKey()), entry.getValue()));
        });
    }

    private static Set<Endpoint> apiEndpoints(RequestMappingInfo info) {
        Set<String> methods = info.getMethodsCondition().getMethods().stream()
                .map(RequestMethod::name)
                .collect(Collectors.toSet());
        Set<Endpoint> endpoints = new HashSet<>();
        for (String pattern : info.getPatternValues()) {
            if (pattern.startsWith("/api/")) {
                // A handler without method restriction would accept every method, which the contract never allows
                (methods.isEmpty() ? Set.of("ANY") : methods)
                        .forEach(method -> endpoints.add(new Endpoint(method, pattern)));
            }
        }
        return endpoints;
    }

    private static List<String> roleNames(Operation operation) {
        Object roles = operation.getExtensions() == null
                ? null
                : operation.getExtensions().get("x-roles");
        return roles instanceof List<?> list
                ? list.stream().map(String::valueOf).toList()
                : List.of();
    }
}
