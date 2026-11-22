package com.elsyah.tech.javara.middleware.ratelimit;

public class LoginRateLimitExceededException extends RuntimeException {
    private final long retryAfterSeconds;

    public LoginRateLimitExceededException(long retryAfterMillis) {
        super("Login request rate limit exceeded");
        this.retryAfterSeconds = Math.max(1, 1 + (retryAfterMillis - 1) / 1000);
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}


