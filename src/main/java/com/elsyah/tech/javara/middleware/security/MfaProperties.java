package com.elsyah.tech.javara.middleware.security;

import java.time.Duration;
import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import com.elsyah.tech.javara.entity.auth.MfaMethod;

@ConfigurationProperties(prefix = "app.security.mfa")
public record MfaProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("false") boolean required,
        @DefaultValue("EMAIL_OTP") MfaMethod defaultMethod,
        @DefaultValue({"EMAIL_OTP", "WEBAUTHN"}) Set<MfaMethod> allowedMethods,
        @DefaultValue("5m") Duration challengeTtl,
        @DefaultValue("5") int maxAttempts,
        @DefaultValue("localhost") String relyingPartyId,
        @DefaultValue({"http://localhost:8080"}) Set<String> allowedOrigins) {
    public MfaProperties {
        allowedMethods = allowedMethods == null ? Set.of() : Set.copyOf(allowedMethods);
        allowedOrigins = allowedOrigins == null ? Set.of() : Set.copyOf(allowedOrigins);
        if (required && !enabled) {
            throw new IllegalArgumentException(
                    "app.security.mfa.required cannot be true when MFA is disabled");
        }
        if (enabled && allowedMethods.isEmpty()) {
            throw new IllegalArgumentException(
                    "app.security.mfa.allowed-methods must include at least one MFA method");
        }
        if (enabled && !allowedMethods.contains(defaultMethod)) {
            throw new IllegalArgumentException(
                    "app.security.mfa.default-method must be included in allowed-methods");
        }
        if (challengeTtl == null
                || challengeTtl.compareTo(Duration.ofSeconds(30)) < 0
                || challengeTtl.compareTo(Duration.ofMinutes(10)) > 0) {
            throw new IllegalArgumentException(
                    "app.security.mfa.challenge-ttl must be between 30 seconds and 10 minutes");
        }
        if (maxAttempts < 1 || maxAttempts > 10) {
            throw new IllegalArgumentException(
                    "app.security.mfa.max-attempts must be between 1 and 10");
        }
        if (allowedMethods.contains(MfaMethod.WEBAUTHN)) {
            if (relyingPartyId == null || relyingPartyId.isBlank()
                    || allowedOrigins.isEmpty()) {
                throw new IllegalArgumentException(
                        "WebAuthn requires relying-party-id and allowed-origins");
            }
        }
    }
}
