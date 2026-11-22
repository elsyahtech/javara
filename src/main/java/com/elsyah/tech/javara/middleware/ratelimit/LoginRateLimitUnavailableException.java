package com.elsyah.tech.javara.middleware.ratelimit;

public class LoginRateLimitUnavailableException extends RuntimeException {
    public LoginRateLimitUnavailableException(Throwable cause) {
        super("Login rate limiting is temporarily unavailable", cause);
    }
}

