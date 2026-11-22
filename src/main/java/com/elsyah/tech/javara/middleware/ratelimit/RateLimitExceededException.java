package com.elsyah.tech.javara.middleware.ratelimit;

public class RateLimitExceededException extends RuntimeException {
    private final long retryAfterSeconds;

    public RateLimitExceededException(long retryAfterMillis) {
        super("Request rate limit exceeded");
        this.retryAfterSeconds = Math.max(1, 1 + (retryAfterMillis - 1) / 1000);
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}


