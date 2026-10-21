package de.elternstunden.auth.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import de.elternstunden.common.config.AppProperties;
import de.elternstunden.common.error.ApiException;
import de.elternstunden.common.error.ErrorCode;
import java.time.Duration;
import org.altcha.altcha.v2.Altcha;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * ALTCHA proof of work (D-07, SEC-04, 9.1), protocol v2 with PBKDF2/SHA-256. Every solution is verified with the
 * official library: valid signature, not expired, and not used before (replay protection in memory until the
 * challenge has expired; one application instance).
 */
@Service
public class CaptchaService {

    static final String ALGORITHM = "PBKDF2/SHA-256";

    private static final Logger log = LoggerFactory.getLogger(CaptchaService.class);

    private final AppProperties.Captcha settings;
    private final Cache<String, Boolean> usedChallenges;

    public CaptchaService(AppProperties properties) {
        this.settings = properties.auth().captcha();
        this.usedChallenges = Caffeine.newBuilder()
                .maximumSize(100_000)
                .expireAfterWrite(settings.expires().plus(Duration.ofMinutes(1)))
                .build();
    }

    /** A new signed challenge as JSON in the format the widget expects. */
    public String createChallengeJson() {
        try {
            Altcha.CreateChallengeOptions options = new Altcha.CreateChallengeOptions()
                    .algorithm(ALGORITHM)
                    .cost(settings.cost())
                    .hmacSignatureSecret(settings.hmacKey())
                    .expiresInSeconds(settings.expires().toSeconds());
            return Altcha.createChallenge(options).toJson();
        } catch (Exception ex) {
            throw new IllegalStateException("Could not create captcha challenge", ex);
        }
    }

    /** Throws {@code 400 CAPTCHA_INVALID} unless the payload is a valid, fresh, unused solution. */
    public void verify(@Nullable String payload) {
        if (payload == null || payload.isBlank() || !isValid(payload)) {
            throw new ApiException(ErrorCode.CAPTCHA_INVALID);
        }
    }

    private boolean isValid(String payload) {
        try {
            Altcha.VerifySolutionResult result =
                    Altcha.verifySolution(payload, settings.hmacKey(), Altcha.kdf(ALGORITHM));
            if (!result.verified()) {
                return false;
            }
            String signature = Altcha.parsePayload(payload).challenge().signature();
            // putIfAbsent is atomic: of two parallel uses of the same solution only one wins
            return usedChallenges.asMap().putIfAbsent(signature, Boolean.TRUE) == null;
        } catch (Exception ex) {
            log.debug("Captcha payload rejected: {}", ex.getClass().getSimpleName());
            return false;
        }
    }
}
