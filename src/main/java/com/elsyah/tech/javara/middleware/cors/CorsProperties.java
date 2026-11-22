package com.elsyah.tech.javara.middleware.cors;

import java.net.URI;
import java.util.List;
import java.util.Locale;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.web.cors")
public record CorsProperties(
        boolean enabled,
        List<String> allowedOrigins,
        List<String> allowedMethods,
        List<String> allowedHeaders,
        List<String> exposedHeaders,
        boolean allowCredentials,
        long maxAgeSeconds) {
    private static final List<String> DEFAULT_METHODS =
            List.of("GET", "HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");
    private static final List<String> DEFAULT_HEADERS =
            List.of("Authorization", "Content-Type", "X-Request-ID", "X-XSRF-TOKEN");
    private static final List<String> DEFAULT_EXPOSED_HEADERS =
            List.of("X-Request-ID", "Retry-After");

    public CorsProperties {
        allowedOrigins = normalizeOrigins(allowedOrigins);
        allowedMethods = copyValues(allowedMethods, DEFAULT_METHODS, "CORS methods");
        allowedHeaders = copyValues(allowedHeaders, DEFAULT_HEADERS, "CORS headers");
        exposedHeaders = copyValues(
                exposedHeaders,
                DEFAULT_EXPOSED_HEADERS,
                "CORS exposed headers");

        if (maxAgeSeconds < 0) {
            throw new IllegalArgumentException("CORS max-age-seconds must not be negative");
        }
        if (enabled && allowedOrigins.isEmpty()) {
            throw new IllegalArgumentException(
                    "CORS requires at least one allowed origin when enabled");
        }
        if (allowCredentials && allowedOrigins.contains("*")) {
            throw new IllegalArgumentException(
                    "CORS credentials cannot be enabled with a wildcard origin");
        }
    }

    private static List<String> normalizeOrigins(List<String> values) {
        List<String> origins = copyValues(values, List.of(), "CORS origins");
        return origins.stream()
                .map(CorsProperties::normalizeOrigin)
                .distinct()
                .toList();
    }

    private static List<String> copyValues(
            List<String> values,
            List<String> defaults,
            String description) {
        List<String> result = values == null ? defaults : values;
        if (result.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException(description + " must not contain blank values");
        }
        return List.copyOf(result);
    }

    private static String normalizeOrigin(String origin) {
        if (origin.equals("*")) {
            return origin;
        }

        final URI uri;
        try {
            uri = URI.create(origin);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid CORS origin: " + origin, exception);
        }

        String scheme = uri.getScheme();
        String path = uri.getRawPath();
        if (scheme == null
                || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))
                || uri.getHost() == null
                || uri.getRawUserInfo() != null
                || uri.getRawQuery() != null
                || uri.getRawFragment() != null
                || (path != null && !path.isEmpty() && !path.equals("/"))) {
            throw new IllegalArgumentException(
                    "CORS origin must be an HTTP(S) origin without a path: " + origin);
        }

        return path == null || path.isEmpty() ? origin : origin.substring(0, origin.length() - 1);
    }

    public List<String> normalizedAllowedMethods() {
        return allowedMethods.stream()
                .map(method -> method.toUpperCase(Locale.ROOT))
                .toList();
    }
}

