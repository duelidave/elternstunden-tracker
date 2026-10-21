package de.elternstunden.common.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.elternstunden.common.config.AppProperties.Limit;
import de.elternstunden.common.error.RateLimitedException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class RateLimiterTest {

    private static final Limit THREE_PER_MINUTE = new Limit(3, Duration.ofMinutes(1));

    private Instant now = Instant.parse("2026-10-21T10:00:00Z");
    private final RateLimiter limiter = new RateLimiter(new Clock() {
        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    });

    @Test
    void allowsUpToTheLimitPerWindowAndKey() {
        for (int i = 0; i < 3; i++) {
            assertThat(limiter.tryAcquire("bucket", "a", THREE_PER_MINUTE)).isTrue();
        }
        assertThat(limiter.tryAcquire("bucket", "a", THREE_PER_MINUTE)).isFalse();
        assertThat(limiter.tryAcquire("bucket", "b", THREE_PER_MINUTE)).isTrue();
        assertThat(limiter.tryAcquire("other", "a", THREE_PER_MINUTE)).isTrue();
    }

    @Test
    void aNewWindowStartsAfterTheOldOneEnds() {
        for (int i = 0; i < 4; i++) {
            limiter.tryAcquire("bucket", "a", THREE_PER_MINUTE);
        }
        now = now.plusSeconds(60);

        assertThat(limiter.tryAcquire("bucket", "a", THREE_PER_MINUTE)).isTrue();
    }

    @Test
    void throwsWithTheRemainingWaitTime() {
        for (int i = 0; i < 3; i++) {
            limiter.acquireOrThrow("bucket", "a", THREE_PER_MINUTE);
        }
        now = now.plusSeconds(15);

        assertThatThrownBy(() -> limiter.acquireOrThrow("bucket", "a", THREE_PER_MINUTE))
                .isInstanceOfSatisfying(
                        RateLimitedException.class,
                        ex -> assertThat(ex.retryAfterSeconds()).isEqualTo(45));
    }
}
