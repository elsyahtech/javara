package com.elsyah.tech.javara.service.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.elsyah.tech.javara.entity.auth.RefreshTokenEntity;
import com.elsyah.tech.javara.entity.user.UserEntity;
import com.elsyah.tech.javara.middleware.security.JwtProperties;
import com.elsyah.tech.javara.repository.auth.RefreshTokenRepository;
import com.elsyah.tech.javara.repository.user.UserRepository;
import com.elsyah.tech.javara.request.auth.LoginRequest;
import com.elsyah.tech.javara.service.common.ServiceResult;
import com.elsyah.tech.javara.service.user.UserService;

@Service
public class AuthService {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserService userService;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AccessTokenService accessTokenService;
    private final JwtProperties jwtProperties;
    private final Clock clock;

    public AuthService(
            UserService userService,
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            AccessTokenService accessTokenService,
            JwtProperties jwtProperties,
            Clock clock) {
        this.userService = userService;
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.accessTokenService = accessTokenService;
        this.jwtProperties = jwtProperties;
        this.clock = clock;
    }

    @Transactional
    public AuthServiceResult login(LoginRequest request) {
        ServiceResult<UserEntity> login = userService.login(request);
        if (login.isError()) {
            return AuthServiceResult.failure(login.status(), login.message());
        }
        return issueTokens(login.data(), "Login successful");
    }

    @Transactional
    public AuthServiceResult refresh(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return invalidRefreshToken();
        }

        Instant now = clock.instant();
        String tokenHash = hashToken(rawRefreshToken);
        Optional<UUID> tokenUserId = refreshTokenRepository.findUserIdByTokenHash(tokenHash);
        if (tokenUserId.isEmpty()) {
            return invalidRefreshToken();
        }

        UUID userId = tokenUserId.get();
        Optional<UserEntity> optionalUser = userRepository.findByIdForUpdate(userId);
        Optional<RefreshTokenEntity> storedToken =
                refreshTokenRepository.findByTokenHashForUpdate(tokenHash);
        if (storedToken.isEmpty()) {
            return invalidRefreshToken();
        }

        RefreshTokenEntity token = storedToken.get();
        if (token.getRevokedAt() != null) {
            revokeActiveTokens(token.getUserId(), now);
            return invalidRefreshToken();
        }
        if (!token.getExpiresAt().isAfter(now)) {
            token.setRevokedAt(now);
            return invalidRefreshToken();
        }

        if (optionalUser.isEmpty() || !Boolean.TRUE.equals(optionalUser.get().getIsActive())) {
            token.setRevokedAt(now);
            revokeActiveTokens(token.getUserId(), now);
            return invalidRefreshToken();
        }

        token.setRevokedAt(now);
        return issueTokens(optionalUser.get(), "Token refreshed successfully");
    }

    @Transactional
    public boolean logout(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return false;
        }

        Optional<RefreshTokenEntity> storedToken =
                refreshTokenRepository.findByTokenHashForUpdate(hashToken(rawRefreshToken));
        if (storedToken.isEmpty()) {
            return false;
        }

        RefreshTokenEntity token = storedToken.get();
        if (token.getRevokedAt() == null) {
            token.setRevokedAt(clock.instant());
        }
        return true;
    }

    private AuthServiceResult issueTokens(UserEntity user, String message) {
        String rawRefreshToken = newRefreshToken();
        Instant now = clock.instant();
        RefreshTokenEntity storedToken = new RefreshTokenEntity();
        storedToken.setTokenHash(hashToken(rawRefreshToken));
        storedToken.setUserId(user.getId());
        storedToken.setCreatedAt(now);
        storedToken.setExpiresAt(now.plus(jwtProperties.refreshTokenTtl()));
        refreshTokenRepository.save(storedToken);

        return new AuthServiceResult(
                user,
                accessTokenService.issue(user),
                rawRefreshToken,
                HttpStatus.OK,
                message);
    }

    private void revokeActiveTokens(UUID userId, Instant revokedAt) {
        refreshTokenRepository.revokeActiveTokensForUser(userId, revokedAt);
    }

    private static String newRefreshToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hashToken(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static AuthServiceResult invalidRefreshToken() {
        return AuthServiceResult.failure(
                HttpStatus.UNAUTHORIZED,
                "Refresh token is invalid or expired");
    }
}
