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
        @Min(1) @DefaultValue("10") int maxRequests,
        @NotNull @DefaultValue("1m") Duration window,
        @NotBlank @DefaultValue("javara:login") String keyPrefix,
        @DefaultValue("true") boolean enabled) implements RateLimitSettings {
    public LoginRateLimitProperties {
        if (window != null && (window.isZero() || window.isNegative()
                || window.toMillis() < 1)) {
            throw new IllegalArgumentException("Login rate limit window must be positive");
        }
    }
}
