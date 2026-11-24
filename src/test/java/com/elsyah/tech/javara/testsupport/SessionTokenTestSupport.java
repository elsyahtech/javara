package com.elsyah.tech.javara.testsupport;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

import com.elsyah.tech.javara.entity.auth.RefreshTokenEntity;
import com.elsyah.tech.javara.entity.user.UserEntity;
import com.elsyah.tech.javara.repository.auth.RefreshTokenRepository;
import com.elsyah.tech.javara.repository.user.UserRepository;
import com.elsyah.tech.javara.service.auth.AccessTokenService;
import com.elsyah.tech.javara.utils.security.PasswordHashService;

public final class SessionTokenTestSupport {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private SessionTokenTestSupport() {}

    public static String issueAccessToken(
            UserEntity user,
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            AccessTokenService accessTokenService,
            PasswordHashService passwordHashService) {
        if (user.getPassword() == null) {
            user.setPassword(passwordHashService.encode("test-only-password"));
        }
        user.setIsActive(true);
        UserEntity persistedUser = userRepository.saveAndFlush(user);

        Instant now = Instant.now();
        UUID sessionId = UUID.randomUUID();
        String rawRefreshToken = newRefreshToken();
        RefreshTokenEntity refreshToken = new RefreshTokenEntity();
        refreshToken.setTokenHash(hash(rawRefreshToken));
        refreshToken.setUserId(persistedUser.getId());
        refreshToken.setSessionId(sessionId);
        refreshToken.setSessionStartedAt(now);
        refreshToken.setLastActivityAt(now);
        refreshToken.setSessionExpiresAt(now.plus(Duration.ofHours(12)));
        refreshToken.setCreatedAt(now);
        refreshToken.setExpiresAt(now.plus(Duration.ofHours(12)));
        refreshTokenRepository.saveAndFlush(refreshToken);

        return accessTokenService.issue(persistedUser, sessionId, now);
    }

    private static String newRefreshToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hash(String rawToken) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
