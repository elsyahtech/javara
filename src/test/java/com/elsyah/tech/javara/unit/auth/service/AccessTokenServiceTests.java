package com.elsyah.tech.javara.unit.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;

import com.elsyah.tech.javara.entity.user.UserEntity;
import com.elsyah.tech.javara.entity.user.UserRole;
import com.elsyah.tech.javara.middleware.security.JwtProperties;
import com.elsyah.tech.javara.middleware.security.JwtTokenConfiguration;
import com.elsyah.tech.javara.middleware.security.TokenTransport;
import com.elsyah.tech.javara.service.auth.AccessTokenService;

class AccessTokenServiceTests {
    // Verifies that issued access tokens contain a subject, role, issuer, and short expiry.
    @Test
    void issueCreatesSignedTokenWithExpectedClaims() {
        JwtProperties properties = jwtProperties();
        JwtTokenConfiguration configuration = new JwtTokenConfiguration();
        JwtEncoder encoder = configuration.jwtEncoder(properties);
        JwtDecoder decoder = configuration.jwtDecoder(properties);
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        AccessTokenService service = new AccessTokenService(
                encoder, properties, Clock.fixed(now, ZoneOffset.UTC));
        UserEntity user = new UserEntity();
        user.setId(uuid("user-1"));
        user.setEmail("user@example.com");
        user.setRole(UserRole.ADMIN);
        UUID sessionId = uuid("session-1");

        Jwt decoded = decoder.decode(service.issue(user, sessionId, now));

        assertEquals(uuid("user-1").toString(), decoded.getSubject());
        assertEquals(now, decoded.getIssuedAt());
        assertEquals(now.plus(Duration.ofMinutes(15)), decoded.getExpiresAt());
        assertEquals("javara", decoded.getClaimAsString("iss"));
        assertEquals(sessionId.toString(), decoded.getClaimAsString("sid"));
        assertEquals(
                now.getEpochSecond(),
                ((Number) decoded.getClaim("auth_time")).longValue());
        assertEquals("user@example.com", decoded.getClaimAsString("email"));
        assertEquals("ADMIN", decoded.getClaimAsStringList("roles").get(0));
        assertEquals(List.of("pwd"), decoded.getClaimAsStringList("amr"));
        assertTrue(Duration.between(decoded.getIssuedAt(), decoded.getExpiresAt())
                .compareTo(Duration.ofMinutes(15)) <= 0);
    }

    // Verifies that configuration rejects signing keys shorter than the HS256 minimum.
    @Test
    void signingKeyMustContainAtLeast256Bits() {
        boolean rejected = false;
        try {
            new JwtProperties(
                    Base64.getEncoder().encodeToString(new byte[31]),
                    TokenTransport.BEARER,
                    Duration.ofMinutes(15),
                    Duration.ofDays(30),
                    true,
                    "Strict",
                    "ACCESS_TOKEN",
                    "REFRESH_TOKEN");
        } catch (IllegalArgumentException exception) {
            rejected = true;
        }

        assertTrue(rejected);
    }

    private static JwtProperties jwtProperties() {
        return new JwtProperties(
                Base64.getEncoder().encodeToString(new byte[32]),
                TokenTransport.BEARER,
                Duration.ofMinutes(15),
                Duration.ofDays(30),
                true,
                "Strict",
                "ACCESS_TOKEN",
                "REFRESH_TOKEN");
    }

    private static UUID uuid(String value) {
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
    }
}
