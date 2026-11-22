package com.elsyah.tech.javara.middleware.ratelimit;

public class RateLimitUnavailableException extends RuntimeException {
    public RateLimitUnavailableException(Throwable cause) {
        super("Rate limiting is temporarily unavailable", cause);
    }
}

