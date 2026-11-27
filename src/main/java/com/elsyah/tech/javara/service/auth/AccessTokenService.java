package com.elsyah.tech.javara.service.auth;

import java.time.Instant;
import java.time.Clock;
import java.util.List;
import java.util.UUID;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;

import com.elsyah.tech.javara.entity.user.UserEntity;
import com.elsyah.tech.javara.middleware.security.JwtProperties;
import com.elsyah.tech.javara.middleware.security.JwtTokenConfiguration;

@Service
public class AccessTokenService {
    private final JwtEncoder jwtEncoder;
    private final JwtProperties properties;
    private final Clock clock;

    public AccessTokenService(JwtEncoder jwtEncoder, JwtProperties properties, Clock clock) {
        this.jwtEncoder = jwtEncoder;
        this.properties = properties;
        this.clock = clock;
    }

    public String issue(UserEntity user, UUID sessionId, Instant authenticatedAt) {
        return issue(user, sessionId, authenticatedAt, List.of("pwd"));
    }

    public String issue(
            UserEntity user,
            UUID sessionId,
            Instant authenticatedAt,
            List<String> authenticationMethods) {
        Instant issuedAt = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(JwtTokenConfiguration.issuer())
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plus(properties.accessTokenTtl()))
                .subject(user.getId().toString())
                .id(UUID.randomUUID().toString())
                .claim("sid", sessionId.toString())
                .claim("auth_time", authenticatedAt.getEpochSecond())
                .claim("amr", List.copyOf(authenticationMethods))
                .claim("email", user.getEmail())
                .claim("roles", List.of(user.getRole().name()))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
