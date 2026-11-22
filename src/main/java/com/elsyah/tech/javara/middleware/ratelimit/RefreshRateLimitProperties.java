package com.elsyah.tech.javara.middleware.ratelimit;

import java.time.Duration;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.middleware.rate-limit.refresh")
public record RefreshRateLimitProperties(
        @Min(1) @DefaultValue("30") int maxRequests,
        @NotNull @DefaultValue("1m") Duration window,
        @NotBlank @DefaultValue("javara:refresh") String keyPrefix,
        @DefaultValue("true") boolean enabled) implements RateLimitSettings {
    public RefreshRateLimitProperties {
        if (window != null && (window.isZero() || window.isNegative()
                || window.toMillis() < 1)) {
            throw new IllegalArgumentException("Refresh rate limit window must be positive");
        }
    }
}
