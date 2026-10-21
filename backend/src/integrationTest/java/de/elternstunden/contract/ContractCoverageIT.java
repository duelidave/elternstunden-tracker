package de.elternstunden.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import de.elternstunden.common.error.ProblemDetailsFactory;
import de.elternstunden.common.security.ApiSecurityConfig;
import de.elternstunden.common.security.ProblemSecurityHandler;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.parser.OpenAPIV3Parser;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * API-first guard (D-25, SEC-01): the registered handlers under {@code /api} match the operations of the contract
 * one to one, and every operation declares its roles in {@code x-roles}.
 */
@WebMvcTest
@Import({ApiSecurityConfig.class, ProblemSecurityHandler.class, ProblemDetailsFactory.class})
class ContractCoverageTest {

    private static final Set<String> ROLES = Set.of("A", "U", "F", "AD", "M", "OP", "MP");

    private static OpenAPI contract;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    @Autowired
    private MockMvc mvc;

    record Endpoint(String method, String path) {}

    record ContractOperation(Endpoint endpoint, Operation operation) {}

    @BeforeAll
    static void readContract() {
        contract = new OpenAPIV3Parser().read(System.getProperty("elternstunden.contract"));
        assertThat(contract).as("contract api/openapi.yaml").isNotNull();
    }

    @Test
    void everyContractOperationHasAHandlerAndNoHandlerIsOutsideTheContract() {
        Set<Endpoint> inContract = new HashSet<>();
        contractOperations().forEach(op -> inContract.add(op.endpoint()));

        Set<Endpoint> implemented = new HashSet<>();
        handlerMapping.getHandlerMethods().keySet().forEach(info -> implemented.addAll(apiEndpoints(info)));

        assertThat(implemented).containsExactlyInAnyOrderElementsOf(inContract);
    }

    @Test
    void everyOperationDeclaresKnownRoles() {
        contractOperations().forEach(op -> {
            Object roles = op.operation().getExtensions() == null
                    ? null
                    : op.operation().getExtensions().get("x-roles");
            assertThat(roles).as("x-roles of %s", op.endpoint()).isInstanceOf(List.class);
            assertThat((List<?>) roles).as("x-roles of %s", op.endpoint()).isNotEmpty();
            assertThat(ROLES).as("x-roles of %s", op.endpoint()).containsAll(roleNames((List<?>) roles));
        });
    }

    @Test
    void anonymousGetOperationsAreReachableWithoutLogin() throws Exception {
        List<ContractOperation> anonymous = contractOperations()
                .filter(op -> op.endpoint().method().equals("GET"))
                .filter(op -> !op.endpoint().path().contains("{"))
                .filter(op -> roleNames(roles(op.operation())).contains("A"))
                .toList();
        for (ContractOperation op : anonymous) {
            int status = mvc.perform(get(op.endpoint().path()))
                    .andReturn()
                    .getResponse()
                    .getStatus();
            assertThat(status).as("anonymous %s", op.endpoint()).isNotIn(401, 403);
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
                .collect(java.util.stream.Collectors.toSet());
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

    private static List<?> roles(Operation operation) {
        return operation.getExtensions() != null && operation.getExtensions().get("x-roles") instanceof List<?> list
                ? list
                : List.of();
    }

    private static List<String> roleNames(List<?> roles) {
        return roles.stream().map(String::valueOf).toList();
    }
}
