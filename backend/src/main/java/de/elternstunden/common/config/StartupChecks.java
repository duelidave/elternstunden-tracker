package de.elternstunden.common.config;

import java.util.Set;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/**
 * 9.1 and 12.4: the app runs only with one of the profiles dev, test or prod, and in prod only with keys of at least
 * 32 characters that are not the examples from .env.example (a weak key would let anyone decrypt TOTP secrets from a
 * dump or forge captcha signatures).
 */
@Component
class StartupChecks implements ApplicationListener<ApplicationStartedEvent> {

    static final int MIN_KEY_LENGTH = 32;
    static final Set<String> EXAMPLE_KEYS = Set.of("local-dev-encryption-key", "local-dev-altcha-key");

    private final Environment environment;
    private final AppProperties properties;

    StartupChecks(Environment environment, AppProperties properties) {
        this.environment = environment;
        this.properties = properties;
    }

    @Override
    public void onApplicationEvent(ApplicationStartedEvent event) {
        if (!environment.acceptsProfiles(Profiles.of("dev | test | prod"))) {
            throw new IllegalStateException("Start with one of the profiles dev, test or prod");
        }
        if (environment.acceptsProfiles(Profiles.of("prod"))) {
            requireStrong("APP_ENCRYPTION_KEY", properties.security().encryptionKey());
            requireStrong("ALTCHA_HMAC_KEY", properties.auth().captcha().hmacKey());
        }
    }

    static void requireStrong(String name, String key) {
        if (key.length() < MIN_KEY_LENGTH || EXAMPLE_KEYS.contains(key)) {
            throw new IllegalStateException(name + " must have at least 32 random characters in production");
        }
    }
}
