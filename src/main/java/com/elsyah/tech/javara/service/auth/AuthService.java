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
import com.elsyah.tech.javara.middleware.security.SessionProperties;
import com.elsyah.tech.javara.repository.auth.RefreshTokenRepository;
import com.elsyah.tech.javara.repository.user.UserRepository;
import com.elsyah.tech.javara.request.auth.LoginRequest;
import com.elsyah.tech.javara.service.common.ServiceResult;
import com.elsyah.tech.javara.service.email.EmailNotificationService;
import com.elsyah.tech.javara.service.user.UserService;

@Service
public class AuthService {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserService userService;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AccessTokenService accessTokenService;
    private final JwtProperties jwtProperties;
    private final SessionProperties sessionProperties;
    private final EmailNotificationService emailNotificationService;
    private final Clock clock;

    public AuthService(
            UserService userService,
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            AccessTokenService accessTokenService,
            JwtProperties jwtProperties,
            SessionProperties sessionProperties,
            EmailNotificationService emailNotificationService,
            Clock clock) {
        this.userService = userService;
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.accessTokenService = accessTokenService;
        this.jwtProperties = jwtProperties;
        this.sessionProperties = sessionProperties;
        this.emailNotificationService = emailNotificationService;
        this.clock = clock;
    }

    @Transactional
    public AuthServiceResult login(LoginRequest request) {
        ServiceResult<UserEntity> login = userService.login(request);
        if (login.isError()) {
            return AuthServiceResult.failure(login.status(), login.message());
        }

        UserEntity user = login.data();
        Optional<UserEntity> lockedUser = userRepository.findByIdForUpdate(user.getId());
        if (lockedUser.isEmpty() || !Boolean.TRUE.equals(lockedUser.get().getIsActive())) {
            return AuthServiceResult.failure(
                    HttpStatus.UNAUTHORIZED,
                    "Invalid email or password");
        }

        Instant now = clock.instant();
        long activeSessions = refreshTokenRepository.countActiveSessions(
                user.getId(),
                now,
                now.minus(sessionProperties.idleTimeout()));
        if (activeSessions >= sessionProperties.maxConcurrentSessions()) {
            return AuthServiceResult.failure(
                    HttpStatus.CONFLICT,
                    "Maximum number of active sessions reached");
        }

        AuthServiceResult result = issueTokens(
                lockedUser.get(),
                UUID.randomUUID(),
                now,
                now.plus(sessionProperties.absoluteLifetime()),
                "Login successful");
        emailNotificationService.newSession(lockedUser.get().getEmail());
        return result;
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
        Instant idleDeadline = now.minus(sessionProperties.idleTimeout());
        if (!token.getExpiresAt().isAfter(now)
                || !token.getSessionExpiresAt().isAfter(now)
                || !token.getLastActivityAt().isAfter(idleDeadline)) {
            token.setRevokedAt(now);
            refreshTokenRepository.revokeActiveTokensForSession(
                    token.getSessionId(), userId, now);
            return invalidRefreshToken();
        }

        if (optionalUser.isEmpty() || !Boolean.TRUE.equals(optionalUser.get().getIsActive())) {
            token.setRevokedAt(now);
            revokeActiveTokens(token.getUserId(), now);
            return invalidRefreshToken();
        }

        token.setRevokedAt(now);
        return issueTokens(
                optionalUser.get(),
                token.getSessionId(),
                token.getSessionStartedAt(),
                token.getSessionExpiresAt(),
                "Token refreshed successfully");
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
            refreshTokenRepository.revokeActiveTokensForSession(
                    token.getSessionId(), token.getUserId(), clock.instant());
        }
        return true;
    }

    private AuthServiceResult issueTokens(
            UserEntity user,
            UUID sessionId,
            Instant sessionStartedAt,
            Instant sessionExpiresAt,
            String message) {
        String rawRefreshToken = newRefreshToken();
        Instant now = clock.instant();
        RefreshTokenEntity storedToken = new RefreshTokenEntity();
        storedToken.setTokenHash(hashToken(rawRefreshToken));
        storedToken.setUserId(user.getId());
        storedToken.setSessionId(sessionId);
        storedToken.setSessionStartedAt(sessionStartedAt);
        storedToken.setLastActivityAt(now);
        storedToken.setSessionExpiresAt(sessionExpiresAt);
        storedToken.setCreatedAt(now);
        storedToken.setExpiresAt(min(
                now.plus(jwtProperties.refreshTokenTtl()),
                sessionExpiresAt));
        refreshTokenRepository.save(storedToken);

        return new AuthServiceResult(
                user,
                accessTokenService.issue(user, sessionId, sessionStartedAt),
                rawRefreshToken,
                HttpStatus.OK,
                message);
    }

    private void revokeActiveTokens(UUID userId, Instant revokedAt) {
        refreshTokenRepository.revokeActiveTokensForUser(userId, revokedAt);
    }

    private static Instant min(Instant first, Instant second) {
        return first.isBefore(second) ? first : second;
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
