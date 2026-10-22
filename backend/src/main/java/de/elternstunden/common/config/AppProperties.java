package de.elternstunden.common.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
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
        @Valid @NotNull Master master,
        @Valid @NotNull Claims claims,
        @Valid @NotNull SchoolYears schoolYears,
        @Valid @NotNull Oidc oidc,
        @Valid @NotNull Mfa mfa) {

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
            @Valid @NotNull Limit masterFailureAlarm,
            @Valid @NotNull Limit oidcCallbackPerIp,
            @Valid @NotNull Limit mfaPerIp,
            @Valid @NotNull Limit entryPerUser) {}

    public record Limit(@Positive int max, @NotNull Duration window) {}

    /** Session timeouts per role (D-20). */
    public record Sessions(
            @Valid @NotNull Timeouts familyMember,
            @Valid @NotNull Timeouts admin,
            @Valid @NotNull Timeouts masterAdmin) {}

    public record Timeouts(@NotNull Duration idle, @NotNull Duration absolute) {}

    /**
     * Family assignment via child data (7.1.4, 7.1.7, DP-05).
     *
     * @param maxPerTarget claims per child within {@code window} before THROTTLED (9.1)
     * @param maxCorrections corrections of the child data per account on /pending
     * @param retention resolved and superseded claims are deleted after this time
     */
    public record Claims(
            @Positive int maxPerTarget,
            @NotNull Duration window,
            @Positive int maxCorrections,
            @NotNull Duration retention) {}

    /**
     * School years (5.5).
     *
     * @param leadTime the job creates the next school year this long before it starts
     */
    public record SchoolYears(@NotNull Duration leadTime) {}

    /**
     * OIDC login (7.2, D-23). A provider without client id is switched off.
     *
     * @param pendingTtl lifetime of the OIDC_PENDING state ("Noch ein Schritt")
     * @param reauthWindow linking a provider or disabling TOTP needs a login at most this old
     */
    public record Oidc(
            @NotNull Duration pendingTtl, @NotNull Duration reauthWindow, Map<String, @Valid OidcProvider> providers) {

        public Oidc {
            providers = providers == null ? Map.of() : Map.copyOf(providers);
        }
    }

    /**
     * One OIDC provider. Without explicit endpoints they are discovered from the issuer at start.
     *
     * @param trustEmailForLinking whether the provider is authoritative for the e-mail address (7.1.5)
     */
    public record OidcProvider(
            @NotBlank String name,
            @Nullable String clientId,
            @Nullable String clientSecret,
            @NotBlank String issuer,
            @Nullable URI authorizationUri,
            @Nullable URI tokenUri,
            @Nullable URI jwkSetUri,
            @NotNull EmailTrust trustEmailForLinking) {

        public boolean enabled() {
            return clientId != null && !clientId.isBlank();
        }
    }

    /** 7.1.5: Google only for @gmail.com or with the hd claim; other providers never link by e-mail. */
    public enum EmailTrust {
        NEVER,
        GMAIL_OR_HD
    }

    /**
     * Second factor for admins (SEC-05, 7.2).
     *
     * @param pendingTtl lifetime of the MFA_PENDING state
     * @param recoveryCodes number of one-time recovery codes
     */
    public record Mfa(
            @NotNull Duration pendingTtl, @Positive int recoveryCodes) {}

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
