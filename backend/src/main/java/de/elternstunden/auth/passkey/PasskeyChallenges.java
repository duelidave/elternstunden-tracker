package de.elternstunden.auth.passkey;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialCreationOptions;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialRequestOptions;
import org.springframework.stereotype.Component;

/**
 * Open WebAuthn challenges (7.2): random, usable once, valid for 5 minutes, in memory (one instance is enough, like
 * the rate limits). A registration challenge belongs to the account that asked for it. Time comes from the clock.
 */
@Component
class PasskeyChallenges {

    static final Duration LIFETIME = Duration.ofMinutes(5);

    private record Registration(UUID userId, PublicKeyCredentialCreationOptions options) {}

    private final Cache<UUID, Object> open;

    PasskeyChallenges(Clock clock) {
        this.open = Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterWrite(LIFETIME)
                .ticker(() -> TimeUnit.MILLISECONDS.toNanos(clock.millis()))
                .build();
    }

    UUID addLogin(PublicKeyCredentialRequestOptions options) {
        return add(options);
    }

    UUID addRegistration(UUID userId, PublicKeyCredentialCreationOptions options) {
        return add(new Registration(userId, options));
    }

    Optional<PublicKeyCredentialRequestOptions> takeLogin(UUID id) {
        return take(id) instanceof PublicKeyCredentialRequestOptions options ? Optional.of(options) : Optional.empty();
    }

    Optional<PublicKeyCredentialCreationOptions> takeRegistration(UUID id, UUID userId) {
        return take(id) instanceof Registration registration
                        && registration.userId().equals(userId)
                ? Optional.of(registration.options())
                : Optional.empty();
    }

    private UUID add(Object value) {
        UUID id = UUID.randomUUID();
        open.put(id, value);
        return id;
    }

    /** Usable once, also with parallel requests (atomic remove); an expired one is gone. */
    private Object take(UUID id) {
        return open.asMap().remove(id);
    }
}
