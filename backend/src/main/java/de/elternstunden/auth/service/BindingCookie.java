package de.elternstunden.auth.service;

import de.elternstunden.common.config.AppProperties;
import de.elternstunden.common.crypto.Hashing;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * The {@code __Host-auth_binding} cookie (7.2): a random value per browser. Its SHA-256 is stored with requests,
 * tokens and claims, so the server can tell whether a link is redeemed in the requesting browser (OPEN-21) and apply
 * child data only there (7.1.4 step 7). Whether the cookie is set depends only on the browser, never on the e-mail.
 */
@Component
public class BindingCookie {

    public static final String NAME = "__Host-auth_binding";
    private static final int VALUE_BYTES = 32;

    private final Duration maxAge;

    public BindingCookie(AppProperties properties) {
        this.maxAge = properties.auth().bindingCookieMaxAge();
    }

    /** Hash of the browser's binding value; creates and sets the cookie if the browser has none yet. */
    public String ensureHash(HttpServletRequest request, HttpServletResponse response) {
        String value = read(request).orElseGet(() -> {
            String created = Hashing.randomToken(VALUE_BYTES);
            ResponseCookie cookie = ResponseCookie.from(NAME, created)
                    .httpOnly(true)
                    .secure(true)
                    .sameSite("Lax")
                    .path("/")
                    .maxAge(maxAge)
                    .build();
            response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
            return created;
        });
        return Hashing.sha256Hex(value);
    }

    /** Hash of the browser's binding value, empty if the browser sent none. */
    public Optional<String> currentHash(HttpServletRequest request) {
        return read(request).map(Hashing::sha256Hex);
    }

    private static Optional<String> read(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        return Arrays.stream(cookies)
                .filter(cookie -> NAME.equals(cookie.getName()))
                .map(Cookie::getValue)
                .filter(value -> value != null && !value.isBlank())
                .findFirst();
    }
}
