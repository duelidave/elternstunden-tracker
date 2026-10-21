package de.elternstunden.common.error;

import java.time.Duration;

/** 429 with {@code Retry-After} in seconds (Lastenheft 8.1, 9.1). */
public class RateLimitedException extends ApiException {

    private final Duration retryAfter;

    public RateLimitedException(Duration retryAfter) {
        super(ErrorCode.RATE_LIMITED);
        this.retryAfter = retryAfter;
    }

    /** Seconds until the next attempt is allowed, at least 1. */
    public long retryAfterSeconds() {
        return Math.max(1, (retryAfter.toMillis() + 999) / 1000);
    }
}
