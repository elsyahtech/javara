package com.elsyah.tech.javara.middleware.ratelimit;

import java.time.Duration;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.middleware.rate-limit.login")
public record LoginRateLimitProperties(
        @Min(1) int maxRequests,
        @NotNull Duration window,
        @NotBlank String keyPrefix,
        @DefaultValue("true") boolean enabled) {
    public LoginRateLimitProperties {
        if (window != null && (window.isZero() || window.isNegative()
                || window.toMillis() < 1)) {
            throw new IllegalArgumentException("Login rate limit window must be positive");
        }
    }
}
