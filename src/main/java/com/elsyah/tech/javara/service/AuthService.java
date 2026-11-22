package com.elsyah.tech.javara.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.elsyah.tech.javara.entity.RefreshTokenEntity;
import com.elsyah.tech.javara.entity.UserEntity;
import com.elsyah.tech.javara.middleware.security.JwtProperties;
import com.elsyah.tech.javara.repository.RefreshTokenRepository;
import com.elsyah.tech.javara.repository.UserRepository;
import com.elsyah.tech.javara.request.LoginRequest;

@Service
public class AuthService {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserService userService;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AccessTokenService accessTokenService;
    private final JwtProperties jwtProperties;

    public AuthService(
            UserService userService,
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            AccessTokenService accessTokenService,
            JwtProperties jwtProperties) {
        this.userService = userService;
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.accessTokenService = accessTokenService;
        this.jwtProperties = jwtProperties;
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

        LocalDateTime now = LocalDateTime.now();
        Optional<RefreshTokenEntity> storedToken =
                refreshTokenRepository.findByTokenHashForUpdate(hashToken(rawRefreshToken));
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

        Optional<UserEntity> optionalUser = userRepository.findById(token.getUserId());
        if (optionalUser.isEmpty() || !Boolean.TRUE.equals(optionalUser.get().getIsActive())) {
            token.setRevokedAt(now);
            revokeActiveTokens(token.getUserId(), now);
            return invalidRefreshToken();
        }

        token.setRevokedAt(now);
        return issueTokens(optionalUser.get(), "Token refreshed successfully");
    }

    @Transactional
    public boolean logout(String userId, String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return false;
        }

        Optional<RefreshTokenEntity> storedToken =
                refreshTokenRepository.findByTokenHashForUpdate(hashToken(rawRefreshToken));
        if (storedToken.isEmpty() || !storedToken.get().getUserId().equals(userId)) {
            return false;
        }

        RefreshTokenEntity token = storedToken.get();
        if (token.getRevokedAt() == null) {
            token.setRevokedAt(LocalDateTime.now());
        }
        return true;
    }

    private AuthServiceResult issueTokens(UserEntity user, String message) {
        String rawRefreshToken = newRefreshToken();
        LocalDateTime now = LocalDateTime.now();
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

    private void revokeActiveTokens(String userId, LocalDateTime revokedAt) {
        List<RefreshTokenEntity> activeTokens =
                refreshTokenRepository.findByUserIdAndRevokedAtIsNull(userId);
        activeTokens.forEach(token -> token.setRevokedAt(revokedAt));
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
