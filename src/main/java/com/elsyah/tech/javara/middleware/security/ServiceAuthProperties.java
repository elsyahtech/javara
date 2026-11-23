package com.elsyah.tech.javara.middleware.security;

import java.net.URI;
import java.time.Duration;
import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security.service-auth")
public record ServiceAuthProperties(
        Boolean enabled,
        Duration accessTokenTtl,
        Set<String> allowedScopes,
        String issuer) {
    public ServiceAuthProperties {
        enabled = enabled == null ? true : enabled;
        accessTokenTtl = accessTokenTtl == null ? Duration.ofMinutes(5) : accessTokenTtl;
        issuer = issuer == null || issuer.isBlank() ? "http://localhost:8080" : issuer;
        allowedScopes = allowedScopes == null || allowedScopes.isEmpty()
                ? Set.of("service.read")
                : Set.copyOf(allowedScopes);

        if (accessTokenTtl.isZero()
                || accessTokenTtl.isNegative()
                || accessTokenTtl.compareTo(Duration.ofHours(1)) > 0) {
            throw new IllegalArgumentException(
                    "Service access-token-ttl must be greater than zero and at most 1 hour");
        }
        if (allowedScopes.stream().anyMatch(scope ->
                scope == null || !scope.matches("[A-Za-z0-9._:-]{1,100}"))) {
            throw new IllegalArgumentException("Service auth scopes must be valid OAuth scope names");
        }
        try {
            URI issuerUri = URI.create(issuer);
            if (!issuerUri.isAbsolute()
                    || !("http".equalsIgnoreCase(issuerUri.getScheme())
                            || "https".equalsIgnoreCase(issuerUri.getScheme()))
                    || issuerUri.getHost() == null
                    || issuerUri.getQuery() != null
                    || issuerUri.getFragment() != null) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "Service auth issuer must be an absolute HTTP or HTTPS URL", exception);
        }
    }
}
