package com.elsyah.tech.javara.middleware.security;

import java.time.Duration;
import java.util.Base64;
import java.util.Locale;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security.jwt")
public record JwtProperties(
        String signingKey,
        TokenTransport transport,
        Duration accessTokenTtl,
        Duration refreshTokenTtl,
        Boolean cookieSecure,
        String cookieSameSite,
        String accessCookieName,
        String refreshCookieName,
        Boolean refreshTokenRotationEnabled) {
    public JwtProperties {
        transport = transport == null ? TokenTransport.COOKIE : transport;
        accessTokenTtl = accessTokenTtl == null ? Duration.ofMinutes(15) : accessTokenTtl;
        refreshTokenTtl = refreshTokenTtl == null ? Duration.ofDays(30) : refreshTokenTtl;
        cookieSecure = cookieSecure == null ? true : cookieSecure;
        cookieSameSite = cookieSameSite == null ? "Strict" : normalizeSameSite(cookieSameSite);
        accessCookieName = accessCookieName == null ? "ACCESS_TOKEN" : accessCookieName;
        refreshCookieName = refreshCookieName == null ? "REFRESH_TOKEN" : refreshCookieName;
        refreshTokenRotationEnabled =
                refreshTokenRotationEnabled == null || refreshTokenRotationEnabled;

        if (signingKey == null || signingKey.isBlank()) {
            throw new IllegalArgumentException(
                    "app.security.jwt.signing-key must be configured with a base64-encoded key");
        }
        if (accessTokenTtl.isZero() || accessTokenTtl.isNegative()
                || accessTokenTtl.compareTo(Duration.ofHours(1)) > 0) {
            throw new IllegalArgumentException(
                    "JWT access-token-ttl must be greater than zero and at most 1 hour");
        }
        if (refreshTokenTtl.compareTo(accessTokenTtl) <= 0
                || refreshTokenTtl.compareTo(Duration.ofDays(90)) > 0) {
            throw new IllegalArgumentException(
                    "JWT refresh-token-ttl must exceed access-token-ttl and be at most 90 days");
        }
        if (!accessCookieName.matches("[A-Za-z0-9_-]+")
                || !refreshCookieName.matches("[A-Za-z0-9_-]+")
                || accessCookieName.equals(refreshCookieName)) {
            throw new IllegalArgumentException("JWT cookie names must be distinct valid HTTP token names");
        }
        if ("None".equals(cookieSameSite) && !cookieSecure) {
            throw new IllegalArgumentException(
                    "JWT cookies with SameSite=None must also set cookie-secure=true");
        }

        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(signingKey);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "app.security.jwt.signing-key must be valid base64", exception);
        }
        if (keyBytes.length < 32) {
            throw new IllegalArgumentException(
                    "app.security.jwt.signing-key must decode to at least 32 bytes");
        }
    }

    public SecretKey secretKey() {
        return new SecretKeySpec(Base64.getDecoder().decode(signingKey), "HmacSHA256");
    }

    private static String normalizeSameSite(String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "strict" -> "Strict";
            case "lax" -> "Lax";
            case "none" -> "None";
            default -> throw new IllegalArgumentException(
                    "JWT cookie-same-site must be Strict, Lax, or None");
        };
    }
}
