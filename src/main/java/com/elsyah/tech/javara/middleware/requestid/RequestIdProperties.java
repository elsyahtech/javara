package com.elsyah.tech.javara.middleware.requestid;

import java.util.regex.Pattern;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "app.web.request-id")
public record RequestIdProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("X-Request-ID") String headerName) {
    private static final Pattern HEADER_NAME_PATTERN = Pattern.compile("[A-Za-z0-9-]{1,64}");

    public RequestIdProperties {
        if (headerName == null || !HEADER_NAME_PATTERN.matcher(headerName).matches()) {
            throw new IllegalArgumentException(
                    "Request ID header-name must contain only letters, numbers, and hyphens");
        }
    }
}