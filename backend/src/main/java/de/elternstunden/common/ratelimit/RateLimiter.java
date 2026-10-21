package de.elternstunden.common.ratelimit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import de.elternstunden.common.config.AppProperties.Limit;
import de.elternstunden.common.error.RateLimitedException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Component;

/**
 * In-memory fixed-window rate limiter (Lastenheft 9.1). One application instance is enough for this app, so no
 * shared store is needed. Memory is bounded: at most 100,000 keys, idle keys expire after a day. Keys are opaque
 * strings; callers pass hashed e-mails and shortened IPs, never personal data in plain text.
 */
@Component
public class RateLimiter {

    private static final int MAX_KEYS = 100_000;

    private final Clock clock;
    private final Cache<String, Window> windows = Caffeine.newBuilder()
            .maximumSize(MAX_KEYS)
            .expireAfterAccess(Duration.ofDays(1))
            .build();

    public RateLimiter(Clock clock) {
        this.clock = clock;
    }

    /** Counts one event; returns false if the limit for this key is exceeded. */
    public boolean tryAcquire(String bucket, String key, Limit limit) {
        return acquire(bucket, key, limit) == null;
    }

    /** Counts one event and throws 429 with {@code Retry-After} if the limit is exceeded. */
    public void acquireOrThrow(String bucket, String key, Limit limit) {
        Duration retryAfter = acquire(bucket, key, limit);
        if (retryAfter != null) {
            throw new RateLimitedException(retryAfter);
        }
    }

    /** Remaining wait time if the limit is exceeded, otherwise null. */
    private Duration acquire(String bucket, String key, Limit limit) {
        Instant now = clock.instant();
        Window window = windows.asMap().compute(bucket + ':' + key, (k, current) -> {
            if (current == null || !now.isBefore(current.start().plus(limit.window()))) {
                return new Window(now, 1);
            }
            return new Window(current.start(), current.count() + 1);
        });
        if (window.count() <= limit.max()) {
            return null;
        }
        return Duration.between(now, window.start().plus(limit.window()));
    }

    /** For tests: forget all counters. */
    public void reset() {
        windows.invalidateAll();
    }

    private record Window(Instant start, int count) {}
}
