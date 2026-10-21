package de.elternstunden.common.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.net.URI;
import java.time.Duration;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Application settings ({@code app.*}). Defaults live in {@code application.yaml}; secrets come from the environment
 * (Lastenheft 12.4) and never from this repository.
 */
@Validated
@ConfigurationProperties("app")
public record AppProperties(
        @NotNull URI baseUrl,
        @Valid @NotNull Mail mail,
        @Valid @NotNull Security security,
        @Valid @NotNull Auth auth,
        @Valid @NotNull RateLimits rateLimits,
        @Valid @NotNull Sessions sessions,
        @Valid @NotNull Master master) {

    public record Mail(@NotBlank String from) {}

    /**
     * Security keys and switches.
     *
     * @param encryptionKey key for HMAC rate-limit keys and (from M3) encrypted admin TOTP secrets
     * @param adminTotpRequired OPEN-09: interim false
     */
    public record Security(@NotBlank String encryptionKey, boolean adminTotpRequired) {}

    public record Auth(
            @NotNull Duration tokenTtl,
            @NotNull Duration pendingAccountTtl,
            @NotNull Duration bindingCookieMaxAge,
            @Positive int mailMaxAttempts,
            @NotNull Duration mailRetryDelay,
            @Positive int workerBatchSize,
            @Valid @NotNull Captcha captcha) {}

    /** ALTCHA (D-07, 9.1). {@code cost} is the PBKDF2 work per attempt, tuned to 1 to 3 s on a phone. */
    public record Captcha(
            @NotBlank String hmacKey,
            @Positive int cost,
            @NotNull Duration expires) {}

    /** Rate limits from Lastenheft 9.1, all configurable. */
    public record RateLimits(
            @Valid @NotNull Limit registerPerIpHour,
            @Valid @NotNull Limit registerPerIpDay,
            @Valid @NotNull Limit registerGlobal,
            @Valid @NotNull Limit magicLinkPerIp,
            @Valid @NotNull Limit magicLinkGlobal,
            @Valid @NotNull Limit mailPerTarget,
            @Valid @NotNull Limit noAccountMailPerTarget,
            @Valid @NotNull Limit redeemPerIp,
            @Valid @NotNull Limit masterLoginPerIp,
            @Valid @NotNull Limit masterFailureAlarm) {}

    public record Limit(@Positive int max, @NotNull Duration window) {}

    /** Session timeouts per role (D-20). */
    public record Sessions(
            @Valid @NotNull Timeouts familyMember,
            @Valid @NotNull Timeouts admin,
            @Valid @NotNull Timeouts masterAdmin) {}

    public record Timeouts(@NotNull Duration idle, @NotNull Duration absolute) {}

    /** The config-managed master admin (D-22, 7.9). Empty email = no master (local development without it). */
    public record Master(
            @Nullable String email,
            @Nullable String passwordHash,
            @Nullable String totpSecret,
            @NotBlank String displayName) {

        public boolean configured() {
            return email != null && !email.isBlank();
        }
    }
}
