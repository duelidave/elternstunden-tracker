package de.elternstunden.support;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.jspecify.annotations.Nullable;

/**
 * A minimal OIDC provider for integration tests (authorization code with PKCE, ID token, JWKS). Every login uses the
 * {@link Login} set with {@link #nextLogin}; its {@link Tamper} produces the manipulated tokens of AC-OIDC-06.
 */
public final class FakeOidcProvider {

    public static final String CLIENT_ID = "test-client";
    public static final String CLIENT_SECRET = "test-secret";

    /** Ways to break the ID token or the flow. */
    public enum Tamper {
        NONE,
        WRONG_NONCE,
        WRONG_AUDIENCE,
        WRONG_ISSUER,
        EXPIRED,
        WRONG_SIGNATURE
    }

    /** The account the provider confirms at the next login. */
    public record Login(
            String subject,
            String email,
            boolean emailVerified,
            @Nullable String name,
            @Nullable String hostedDomain,
            Tamper tamper) {

        public static Login of(String subject, String email) {
            return new Login(subject, email, true, "Test Person", null, Tamper.NONE);
        }

        public Login unverified() {
            return new Login(subject, email, false, name, hostedDomain, tamper);
        }

        public Login hostedDomain(String domain) {
            return new Login(subject, email, emailVerified, name, domain, tamper);
        }

        public Login tamper(Tamper value) {
            return new Login(subject, email, emailVerified, name, hostedDomain, value);
        }
    }

    record Authorization(String nonce, String codeChallenge, String codeChallengeMethod, Login login) {}

    private final HttpServer server;
    private final RSAKey key;
    private final RSAKey otherKey;
    private final Map<String, Authorization> codes = new ConcurrentHashMap<>();
    private volatile Login next = Login.of("subject-1", "someone@gmail.com");
    private volatile @Nullable Map<String, String> lastAuthorizationRequest;

    public FakeOidcProvider() {
        try {
            key = new RSAKeyGenerator(2048).keyID("test-key").generate();
            otherKey = new RSAKeyGenerator(2048).keyID("test-key").generate();
            server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        } catch (Exception ex) {
            throw new IllegalStateException("could not start the fake OIDC provider", ex);
        }
        server.createContext("/issuer/authorize", this::authorize);
        server.createContext("/issuer/token", this::token);
        server.createContext("/issuer/jwks", this::jwks);
        server.start();
    }

    public String issuer() {
        return "http://localhost:" + server.getAddress().getPort() + "/issuer";
    }

    public void nextLogin(Login login) {
        next = login;
    }

    /** The query of the last authorization request, e.g. to check PKCE (AC-OIDC-09). */
    public Map<String, String> lastAuthorizationRequest() {
        Map<String, String> request = lastAuthorizationRequest;
        return request == null ? Map.of() : request;
    }

    /** Plays the browser at the provider: returns the callback path with query, relative to the app. */
    public String authorize(String authorizationUrl) {
        Map<String, String> query = query(URI.create(authorizationUrl).getRawQuery());
        lastAuthorizationRequest = query;
        String code = UUID.randomUUID().toString();
        codes.put(
                code,
                new Authorization(
                        query.getOrDefault("nonce", ""),
                        query.getOrDefault("code_challenge", ""),
                        query.getOrDefault("code_challenge_method", ""),
                        next));
        URI redirect = URI.create(query.get("redirect_uri"));
        return redirect.getRawPath() + "?code=" + encode(code) + "&state=" + encode(query.get("state"));
    }

    private void authorize(HttpExchange exchange) throws IOException {
        String callback = authorize(exchange.getRequestURI().toString());
        exchange.getResponseHeaders().add("Location", callback);
        exchange.sendResponseHeaders(302, -1);
        exchange.close();
    }

    private void token(HttpExchange exchange) throws IOException {
        Map<String, String> form = query(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        Authorization authorization = codes.remove(form.getOrDefault("code", ""));
        if (authorization == null || !pkceMatches(authorization, form.get("code_verifier"))) {
            respond(exchange, 400, "{\"error\":\"invalid_grant\"}");
            return;
        }
        Login login = authorization.login();
        Instant now = Instant.now();
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                .issuer(login.tamper() == Tamper.WRONG_ISSUER ? "http://evil.example" : issuer())
                .subject(login.subject())
                .audience(login.tamper() == Tamper.WRONG_AUDIENCE ? "other-client" : CLIENT_ID)
                .issueTime(Date.from(now.minusSeconds(login.tamper() == Tamper.EXPIRED ? 600 : 0)))
                .expirationTime(
                        Date.from(login.tamper() == Tamper.EXPIRED ? now.minusSeconds(300) : now.plusSeconds(300)))
                .claim("nonce", login.tamper() == Tamper.WRONG_NONCE ? "evil" : authorization.nonce())
                .claim("email", login.email())
                .claim("email_verified", login.emailVerified());
        if (login.name() != null) {
            claims.claim("name", login.name());
        }
        if (login.hostedDomain() != null) {
            claims.claim("hd", login.hostedDomain());
        }
        try {
            SignedJWT jwt = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256)
                            .keyID(key.getKeyID())
                            .build(),
                    claims.build());
            jwt.sign(new RSASSASigner(login.tamper() == Tamper.WRONG_SIGNATURE ? otherKey : key));
            respond(
                    exchange,
                    200,
                    "{\"access_token\":\"fake-access-token\",\"token_type\":\"Bearer\","
                            + "\"expires_in\":300,\"scope\":\"openid profile email\",\"id_token\":\"" + jwt.serialize()
                            + "\"}");
        } catch (Exception ex) {
            respond(exchange, 500, "{\"error\":\"server_error\"}");
        }
    }

    private void jwks(HttpExchange exchange) throws IOException {
        respond(exchange, 200, new JWKSet(List.of(key.toPublicJWK())).toString());
    }

    private static boolean pkceMatches(Authorization authorization, @Nullable String verifier) {
        if (verifier == null || !"S256".equals(authorization.codeChallengeMethod())) {
            return false;
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(digest)
                    .equals(authorization.codeChallenge());
        } catch (Exception ex) {
            return false;
        }
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static Map<String, String> query(@Nullable String raw) {
        Map<String, String> values = new HashMap<>();
        if (raw == null || raw.isEmpty()) {
            return values;
        }
        for (String pair : raw.split("&", -1)) {
            int separator = pair.indexOf('=');
            String name =
                    URLDecoder.decode(separator < 0 ? pair : pair.substring(0, separator), StandardCharsets.UTF_8);
            String value =
                    separator < 0 ? "" : URLDecoder.decode(pair.substring(separator + 1), StandardCharsets.UTF_8);
            values.put(name, value);
        }
        return values;
    }

    private static String encode(@Nullable String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
