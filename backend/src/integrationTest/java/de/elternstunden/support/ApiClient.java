package de.elternstunden.support;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.altcha.altcha.v2.Altcha;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * A "browser" for integration tests: keeps its own cookies (Java's CookieManager would drop Secure cookies over
 * plain http), sends the CSRF header like the SPA and can solve the ALTCHA captcha.
 */
public class ApiClient {

    public record Response(int status, HttpHeaders headers, String body) {

        public JsonNode json() {
            return JSON.readTree(body);
        }

        public Optional<String> header(String name) {
            return headers.firstValue(name);
        }

        public List<String> setCookies() {
            return headers.allValues("Set-Cookie");
        }
    }

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final HttpClient http =
            HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
    private final String baseUrl;
    private final Map<String, String> cookies = new LinkedHashMap<>();
    private final Map<String, String> extraHeaders = new HashMap<>();

    public ApiClient(int port) {
        this.baseUrl = "http://localhost:" + port;
    }

    public Response get(String path) {
        return send(HttpRequest.newBuilder(uri(path)).GET());
    }

    public Response post(String path, Object body) {
        String json = body instanceof String text ? text : JSON.writeValueAsString(body);
        return send(HttpRequest.newBuilder(uri(path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json)));
    }

    public Response put(String path, Object body) {
        return send(HttpRequest.newBuilder(uri(path))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(json(body))));
    }

    public Response put(String path) {
        return send(HttpRequest.newBuilder(uri(path)).PUT(HttpRequest.BodyPublishers.noBody()));
    }

    public Response patch(String path, Object body) {
        return send(HttpRequest.newBuilder(uri(path))
                .header("Content-Type", "application/json")
                .method("PATCH", HttpRequest.BodyPublishers.ofString(json(body))));
    }

    public Response delete(String path) {
        return send(HttpRequest.newBuilder(uri(path)).DELETE());
    }

    public Response post(String path) {
        return send(HttpRequest.newBuilder(uri(path)).POST(HttpRequest.BodyPublishers.noBody()));
    }

    /** A request body in another media type, e.g. {@code text/csv}. */
    public Response post(String path, String contentType, String body) {
        return send(HttpRequest.newBuilder(uri(path))
                .header("Content-Type", contentType)
                .POST(HttpRequest.BodyPublishers.ofString(body)));
    }

    private static String json(Object body) {
        return body instanceof String text ? text : JSON.writeValueAsString(body);
    }

    /** First GET so the server sets the XSRF-TOKEN cookie, like the SPA does on start. */
    public ApiClient withCsrf() {
        get("/api/v1/status");
        return this;
    }

    public Optional<String> cookie(String name) {
        return Optional.ofNullable(cookies.get(name));
    }

    public void dropCookie(String name) {
        cookies.remove(name);
    }

    public void setCookie(String name, String value) {
        cookies.put(name, value);
    }

    public void header(String name, String value) {
        extraHeaders.put(name, value);
    }

    public void removeHeader(String name) {
        extraHeaders.remove(name);
    }

    /** All cookies of this browser, e.g. to continue in a second client with the same browser state. */
    public java.util.Map<String, String> cookies() {
        return java.util.Map.copyOf(cookies);
    }

    /** Fetches a challenge and solves it like the widget does; returns the base64 payload. */
    public String solveCaptcha() {
        Response response = get("/api/v1/auth/captcha");
        if (response.status() != 200) {
            throw new AssertionError("captcha endpoint returned " + response.status());
        }
        JsonNode node = response.json();
        JsonNode parameters = node.get("parameters");
        try {
            Altcha.ChallengeParameters challengeParameters = new Altcha.ChallengeParameters(
                    parameters.get("algorithm").asString(),
                    parameters.get("nonce").asString(),
                    parameters.get("salt").asString(),
                    parameters.get("cost").asInt(),
                    parameters.get("keyLength").asInt(),
                    parameters.get("keyPrefix").asString(),
                    parameters.has("keySignature")
                            ? parameters.get("keySignature").asString()
                            : null,
                    null,
                    null,
                    parameters.get("expiresAt").asLong(),
                    null);
            Altcha.Challenge challenge = new Altcha.Challenge(
                    challengeParameters, node.get("signature").asString());
            Altcha.Solution solution = Altcha.solveChallenge(challenge, Altcha.kdf("PBKDF2/SHA-256"));
            String payload = "{\"challenge\":" + JSON.writeValueAsString(node) + ",\"solution\":{\"counter\":"
                    + solution.counter() + ",\"derivedKey\":\"" + solution.derivedKey() + "\",\"time\":1}}";
            return Base64.getEncoder().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new AssertionError("could not solve captcha", ex);
        }
    }

    private Response send(HttpRequest.Builder builder) {
        HttpResponse<String> response = exchange(builder, HttpResponse.BodyHandlers.ofString());
        return new Response(response.statusCode(), response.headers(), response.body());
    }

    /** A GET for a file such as a PDF: the body as bytes. */
    public Download download(String path) {
        HttpResponse<byte[]> response =
                exchange(HttpRequest.newBuilder(uri(path)).GET(), HttpResponse.BodyHandlers.ofByteArray());
        return new Download(response.statusCode(), response.headers(), response.body());
    }

    @SuppressWarnings("ArrayRecordComponent")
    public record Download(int status, HttpHeaders headers, byte[] body) {}

    private <T> HttpResponse<T> exchange(HttpRequest.Builder builder, HttpResponse.BodyHandler<T> handler) {
        if (!cookies.isEmpty()) {
            StringBuilder header = new StringBuilder();
            cookies.forEach((name, value) -> header.append(header.isEmpty() ? "" : "; ")
                    .append(name)
                    .append('=')
                    .append(value));
            builder.header("Cookie", header.toString());
        }
        String csrf = cookies.get("XSRF-TOKEN");
        if (csrf != null && !extraHeaders.containsKey("X-XSRF-TOKEN")) {
            builder.header("X-XSRF-TOKEN", csrf);
        }
        extraHeaders.forEach(builder::header);
        try {
            HttpResponse<T> response = http.send(builder.build(), handler);
            response.headers().allValues("Set-Cookie").forEach(this::storeCookie);
            return response;
        } catch (IOException ex) {
            throw new AssertionError("request failed", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AssertionError("request interrupted", ex);
        }
    }

    private void storeCookie(String setCookie) {
        String[] parts = setCookie.split(";", -1);
        String[] nameValue = parts[0].split("=", 2);
        String name = nameValue[0].trim();
        String value = nameValue.length > 1 ? nameValue[1].trim() : "";
        boolean expired = value.isEmpty();
        for (String attribute : parts) {
            String trimmed = attribute.trim().toLowerCase(java.util.Locale.ROOT);
            if (trimmed.equals("max-age=0") || trimmed.startsWith("expires=thu, 01 jan 1970")) {
                expired = true;
            }
        }
        if (expired) {
            cookies.remove(name);
        } else {
            cookies.put(name, value);
        }
    }

    private URI uri(String path) {
        return URI.create(baseUrl + path);
    }
}
