package de.elternstunden.common.config;

import de.elternstunden.common.config.AppProperties.Limit;
import de.elternstunden.common.config.AppProperties.Timeouts;
import java.net.URI;
import java.time.Duration;
import java.util.Map;

/** AppProperties with the defaults of application.yaml, for unit tests. */
public final class TestProperties {

    private TestProperties() {}

    public static AppProperties withMaster(AppProperties.Master master) {
        return new AppProperties(
                URI.create("http://localhost:5173"),
                new AppProperties.Mail("Elternstunden <no-reply@localhost>"),
                new AppProperties.Security("test-key", false),
                new AppProperties.Auth(
                        Duration.ofMinutes(15),
                        Duration.ofHours(24),
                        Duration.ofDays(30),
                        3,
                        Duration.ofMinutes(1),
                        20,
                        new AppProperties.Captcha("captcha-key", 1, Duration.ofMinutes(5))),
                new AppProperties.RateLimits(
                        limit(30, 1),
                        limit(100, 24),
                        limit(200, 1),
                        limit(20, 1),
                        limit(200, 1),
                        limit(5, 1),
                        limit(1, 24),
                        new Limit(30, Duration.ofMinutes(15)),
                        new Limit(5, Duration.ofMinutes(15)),
                        limit(10, 1),
                        new Limit(30, Duration.ofMinutes(1)),
                        new Limit(5, Duration.ofMinutes(15)),
                        limit(60, 1)),
                new AppProperties.Sessions(
                        new Timeouts(Duration.ofDays(30), Duration.ofDays(90)),
                        new Timeouts(Duration.ofMinutes(30), Duration.ofHours(8)),
                        new Timeouts(Duration.ofMinutes(15), Duration.ofHours(4))),
                master,
                new AppProperties.Claims(5, Duration.ofHours(24), 3, Duration.ofDays(30)),
                new AppProperties.SchoolYears(Duration.ofDays(90)),
                new AppProperties.Oidc(Duration.ofMinutes(15), Duration.ofMinutes(5), Map.of()),
                new AppProperties.Mfa(Duration.ofMinutes(10), 10));
    }

    public static AppProperties defaults() {
        return withMaster(new AppProperties.Master(null, null, null, "Master-Admin"));
    }

    private static Limit limit(int max, int hours) {
        return new Limit(max, Duration.ofHours(hours));
    }
}
