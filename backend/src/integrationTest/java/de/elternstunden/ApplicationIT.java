package de.elternstunden;

import static org.assertj.core.api.Assertions.assertThat;

import de.elternstunden.support.IntegrationTest;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalManagementPort;

/** The running application: status page, health probes on the management port, deny by default (OPS-07, SEC-01). */
class ApplicationIT extends IntegrationTest {

    private final HttpClient http =
            HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

    @LocalManagementPort
    private int managementPort;

    @Test
    void statusPageIsPublicMatchesTheContractAndCreatesNoSession() throws Exception {
        HttpResponse<String> response = get(port, "/api/v1/status");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type"))
                .hasValueSatisfying(type -> assertThat(type).startsWith("application/json"));
        assertThat(response.body()).isEqualTo("{\"status\":\"UP\"}");
        assertThat(response.headers().firstValue("X-Trace-Id")).isPresent();
        assertThat(response.headers().allValues("Set-Cookie")).noneMatch(cookie -> cookie.contains("SESSION"));
    }

    @Test
    void livenessAndReadinessAreUpOnTheManagementPort() throws Exception {
        for (String probe : new String[] {"/actuator/health/liveness", "/actuator/health/readiness"}) {
            HttpResponse<String> response = get(managementPort, probe);
            assertThat(response.statusCode()).as(probe).isEqualTo(200);
            assertThat(response.body()).as(probe).contains("\"UP\"");
        }
    }

    @Test
    void onlyHealthIsExposedOnTheManagementPort() throws Exception {
        assertThat(get(managementPort, "/actuator/env").statusCode()).isNotEqualTo(200);
        assertThat(get(managementPort, "/actuator/beans").statusCode()).isNotEqualTo(200);
    }

    @Test
    void actuatorIsNotReachableOnTheApplicationPort() throws Exception {
        assertThat(get(port, "/actuator/health").statusCode()).isNotEqualTo(200);
    }

    @Test
    void unknownApiPathIsDeniedWithProblemAndNoRedirect() throws Exception {
        HttpResponse<String> response = get(port, "/api/v1/does-not-exist");

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.headers().firstValue("Content-Type"))
                .hasValueSatisfying(type -> assertThat(type).startsWith("application/problem+json"));
        assertThat(response.headers().firstValue("Location")).isEmpty();
        assertThat(response.body()).contains("\"code\":\"UNAUTHORIZED\"", "\"type\":\"about:blank\"");
    }

    @Test
    void postWithoutCsrfTokenIsRejected() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/status"))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.body()).contains("\"code\":\"CSRF_INVALID\"");
    }

    private HttpResponse<String> get(int targetPort, String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + targetPort + path))
                .GET()
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
