package com.elsyah.tech.javara.unit.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

import com.elsyah.tech.javara.entity.auth.RefreshTokenEntity;
import com.elsyah.tech.javara.entity.user.UserEntity;
import com.elsyah.tech.javara.middleware.security.JwtProperties;
import com.elsyah.tech.javara.middleware.security.SessionProperties;
import com.elsyah.tech.javara.middleware.security.TokenTransport;
import com.elsyah.tech.javara.repository.auth.RefreshTokenRepository;
import com.elsyah.tech.javara.repository.user.UserRepository;
import com.elsyah.tech.javara.request.auth.LoginRequest;
import com.elsyah.tech.javara.service.auth.AccessTokenService;
import com.elsyah.tech.javara.service.auth.AuthService;
import com.elsyah.tech.javara.service.auth.AuthServiceResult;
import com.elsyah.tech.javara.service.common.ServiceResult;
import com.elsyah.tech.javara.service.user.UserService;

class AuthServiceTests {
    private static final Instant NOW = Instant.parse("2026-11-23T12:00:00Z");

    // Verifies that login returns tokens while persisting only a hash of the refresh token.
    @Test
    void loginIssuesAccessAndRefreshTokensAndStoresOnlyRefreshHash() {
        UserService userService = mock(UserService.class);
        UserRepository userRepository = mock(UserRepository.class);
        RefreshTokenRepository refreshRepository = mock(RefreshTokenRepository.class);
        AccessTokenService accessTokenService = mock(AccessTokenService.class);
        AuthService service = service(
                userService, userRepository, refreshRepository, accessTokenService);
        UserEntity user = user(uuid("user-1"));
        LoginRequest request = new LoginRequest("user@example.com", "password123");
        when(userService.login(request)).thenReturn(
                new ServiceResult<>(user, HttpStatus.OK, "Login successful"));
        when(userRepository.findByIdForUpdate(user.getId())).thenReturn(Optional.of(user));
        when(accessTokenService.issue(
                        any(UserEntity.class), any(UUID.class), any(Instant.class)))
                .thenReturn("access-token");

        AuthServiceResult result = service.login(request);

        ArgumentCaptor<RefreshTokenEntity> tokenCaptor = forClass(RefreshTokenEntity.class);
        verify(refreshRepository).save(tokenCaptor.capture());
        RefreshTokenEntity storedToken = tokenCaptor.getValue();
        assertEquals(HttpStatus.OK, result.status());
        assertEquals("access-token", result.accessToken());
        assertNotNull(result.refreshToken());
        assertNotEquals(result.refreshToken(), storedToken.getTokenHash());
        assertTrue(storedToken.getTokenHash().matches("[0-9a-f]{64}"));
        assertEquals(hash(result.refreshToken()), storedToken.getTokenHash());
        assertEquals(uuid("user-1"), storedToken.getUserId());
        assertNotNull(storedToken.getSessionId());
        assertEquals(NOW, storedToken.getSessionStartedAt());
        assertEquals(NOW, storedToken.getLastActivityAt());
        assertEquals(NOW.plus(Duration.ofHours(12)), storedToken.getSessionExpiresAt());
        assertEquals(storedToken.getSessionExpiresAt(), storedToken.getExpiresAt());
    }

    @Test
    void loginRejectsNewSessionWhenConcurrentSessionLimitIsReached() {
        UserService userService = mock(UserService.class);
        UserRepository userRepository = mock(UserRepository.class);
        RefreshTokenRepository refreshRepository = mock(RefreshTokenRepository.class);
        AccessTokenService accessTokenService = mock(AccessTokenService.class);
        AuthService service = service(
                userService, userRepository, refreshRepository, accessTokenService);
        UserEntity user = user(uuid("user-1"));
        LoginRequest request = new LoginRequest("user@example.com", "password123");
        when(userService.login(request)).thenReturn(
                new ServiceResult<>(user, HttpStatus.OK, "Login successful"));
        when(userRepository.findByIdForUpdate(user.getId())).thenReturn(Optional.of(user));
        when(refreshRepository.countActiveSessions(
                        user.getId(),
                        NOW,
                        NOW.minus(Duration.ofMinutes(15))))
                .thenReturn(5L);

        AuthServiceResult result = service.login(request);

        assertEquals(HttpStatus.CONFLICT, result.status());
        assertEquals("Maximum number of active sessions reached", result.message());
        verify(refreshRepository, never()).save(any());
        verifyNoInteractions(accessTokenService);
    }

    // Verifies that a valid refresh token is revoked and replaced with a new token.
    @Test
    void refreshRotatesTokenAndIssuesANewAccessToken() {
        UserService userService = mock(UserService.class);
        UserRepository userRepository = mock(UserRepository.class);
        RefreshTokenRepository refreshRepository = mock(RefreshTokenRepository.class);
        AccessTokenService accessTokenService = mock(AccessTokenService.class);
        AuthService service = service(
                userService, userRepository, refreshRepository, accessTokenService);
        RefreshTokenEntity oldToken = storedToken(uuid("user-1"), "old-refresh-token");
        UserEntity user = user(uuid("user-1"));
        when(refreshRepository.findUserIdByTokenHash(hash("old-refresh-token")))
                .thenReturn(Optional.of(uuid("user-1")));
        when(refreshRepository.findByTokenHashForUpdate(hash("old-refresh-token")))
                .thenReturn(Optional.of(oldToken));
        when(userRepository.findByIdForUpdate(uuid("user-1"))).thenReturn(Optional.of(user));
        when(accessTokenService.issue(
                        user,
                        oldToken.getSessionId(),
                        oldToken.getSessionStartedAt()))
                .thenReturn("new-access-token");

        AuthServiceResult result = service.refresh("old-refresh-token");

        ArgumentCaptor<RefreshTokenEntity> tokenCaptor = forClass(RefreshTokenEntity.class);
        verify(refreshRepository).save(tokenCaptor.capture());
        assertEquals(HttpStatus.OK, result.status());
        assertEquals("new-access-token", result.accessToken());
        assertNotEquals("old-refresh-token", result.refreshToken());
        assertNotNull(oldToken.getRevokedAt());
        assertEquals(hash(result.refreshToken()), tokenCaptor.getValue().getTokenHash());
        assertEquals(oldToken.getSessionId(), tokenCaptor.getValue().getSessionId());
        assertEquals(oldToken.getSessionExpiresAt(), tokenCaptor.getValue().getSessionExpiresAt());
    }

    @Test
    void refreshRejectsSessionAfterAbsoluteLifetime() {
        UserService userService = mock(UserService.class);
        UserRepository userRepository = mock(UserRepository.class);
        RefreshTokenRepository refreshRepository = mock(RefreshTokenRepository.class);
        AccessTokenService accessTokenService = mock(AccessTokenService.class);
        AuthService service = service(
                userService, userRepository, refreshRepository, accessTokenService);
        RefreshTokenEntity token = storedToken(uuid("user-1"), "expired-session-token");
        token.setSessionExpiresAt(NOW.minusSeconds(1));
        when(refreshRepository.findUserIdByTokenHash(hash("expired-session-token")))
                .thenReturn(Optional.of(token.getUserId()));
        when(refreshRepository.findByTokenHashForUpdate(hash("expired-session-token")))
                .thenReturn(Optional.of(token));
        when(userRepository.findByIdForUpdate(token.getUserId()))
                .thenReturn(Optional.of(user(token.getUserId())));

        AuthServiceResult result = service.refresh("expired-session-token");

        assertEquals(HttpStatus.UNAUTHORIZED, result.status());
        verify(refreshRepository).revokeActiveTokensForSession(
                token.getSessionId(), token.getUserId(), NOW);
        verify(refreshRepository, never()).save(any());
    }

    @Test
    void refreshRejectsIdleSession() {
        UserService userService = mock(UserService.class);
        UserRepository userRepository = mock(UserRepository.class);
        RefreshTokenRepository refreshRepository = mock(RefreshTokenRepository.class);
        AccessTokenService accessTokenService = mock(AccessTokenService.class);
        AuthService service = service(
                userService, userRepository, refreshRepository, accessTokenService);
        RefreshTokenEntity token = storedToken(uuid("user-1"), "idle-session-token");
        token.setLastActivityAt(NOW.minus(Duration.ofMinutes(16)));
        when(refreshRepository.findUserIdByTokenHash(hash("idle-session-token")))
                .thenReturn(Optional.of(token.getUserId()));
        when(refreshRepository.findByTokenHashForUpdate(hash("idle-session-token")))
                .thenReturn(Optional.of(token));
        when(userRepository.findByIdForUpdate(token.getUserId()))
                .thenReturn(Optional.of(user(token.getUserId())));

        AuthServiceResult result = service.refresh("idle-session-token");

        assertEquals(HttpStatus.UNAUTHORIZED, result.status());
        verify(refreshRepository).revokeActiveTokensForSession(
                token.getSessionId(), token.getUserId(), NOW);
        verify(refreshRepository, never()).save(any());
    }

    // Verifies that replaying a revoked refresh token revokes the user's other active tokens.
    @Test
    void replayedRefreshTokenRevokesAllActiveTokens() {
        UserService userService = mock(UserService.class);
        UserRepository userRepository = mock(UserRepository.class);
        RefreshTokenRepository refreshRepository = mock(RefreshTokenRepository.class);
        AccessTokenService accessTokenService = mock(AccessTokenService.class);
        AuthService service = service(
                userService, userRepository, refreshRepository, accessTokenService);
        RefreshTokenEntity replayed = storedToken(uuid("user-1"), "replayed-token");
        replayed.setRevokedAt(NOW.minus(Duration.ofMinutes(1)));
        when(refreshRepository.findUserIdByTokenHash(hash("replayed-token")))
                .thenReturn(Optional.of(uuid("user-1")));
        when(refreshRepository.findByTokenHashForUpdate(hash("replayed-token")))
                .thenReturn(Optional.of(replayed));
        when(userRepository.findByIdForUpdate(uuid("user-1")))
                .thenReturn(Optional.of(user(uuid("user-1"))));

        AuthServiceResult result = service.refresh("replayed-token");

        assertEquals(HttpStatus.UNAUTHORIZED, result.status());
        assertNull(result.accessToken());
        verify(refreshRepository).revokeActiveTokensForUser(
                org.mockito.ArgumentMatchers.eq(uuid("user-1")),
                org.mockito.ArgumentMatchers.any(Instant.class));
        verify(refreshRepository, never()).save(any());
    }

    // Verifies that a refresh token alone is sufficient to revoke its session.
    @Test
    void logoutRevokesTokenWithoutUserId() {
        UserService userService = mock(UserService.class);
        UserRepository userRepository = mock(UserRepository.class);
        RefreshTokenRepository refreshRepository = mock(RefreshTokenRepository.class);
        AccessTokenService accessTokenService = mock(AccessTokenService.class);
        AuthService service = service(
                userService, userRepository, refreshRepository, accessTokenService);
        RefreshTokenEntity token = storedToken(uuid("user-2"), "refresh-token");
        when(refreshRepository.findByTokenHashForUpdate(hash("refresh-token")))
                .thenReturn(Optional.of(token));

        boolean loggedOut = service.logout("refresh-token");

        assertTrue(loggedOut);
        verify(refreshRepository).revokeActiveTokensForSession(
                token.getSessionId(), token.getUserId(), NOW);
    }

    // Verifies that failed login credentials do not create a refresh-token record.
    @Test
    void failedLoginDoesNotIssueTokens() {
        UserService userService = mock(UserService.class);
        UserRepository userRepository = mock(UserRepository.class);
        RefreshTokenRepository refreshRepository = mock(RefreshTokenRepository.class);
        AccessTokenService accessTokenService = mock(AccessTokenService.class);
        AuthService service = service(
                userService, userRepository, refreshRepository, accessTokenService);
        LoginRequest request = new LoginRequest("user@example.com", "wrong-password");
        when(userService.login(request)).thenReturn(new ServiceResult<>(
                null, HttpStatus.UNAUTHORIZED, "Invalid email or password"));

        AuthServiceResult result = service.login(request);

        assertEquals(HttpStatus.UNAUTHORIZED, result.status());
        assertNull(result.refreshToken());
        verify(refreshRepository, never()).save(any());
    }

    private static AuthService service(
            UserService userService,
            UserRepository userRepository,
            RefreshTokenRepository refreshRepository,
            AccessTokenService accessTokenService) {
        return new AuthService(
                userService,
                userRepository,
                refreshRepository,
                accessTokenService,
                new JwtProperties(
                        Base64.getEncoder().encodeToString(new byte[32]),
                        TokenTransport.BEARER,
                        Duration.ofMinutes(15),
                        Duration.ofDays(30),
                        true,
                        "Strict",
                        "ACCESS_TOKEN",
                        "REFRESH_TOKEN"),
                new SessionProperties(null, null, null),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static UserEntity user(UUID id) {
        UserEntity user = new UserEntity();
        user.setId(id);
        user.setEmail("user@example.com");
        user.setIsActive(true);
        return user;
    }

    private static RefreshTokenEntity storedToken(UUID userId, String rawToken) {
        RefreshTokenEntity token = new RefreshTokenEntity();
        token.setTokenHash(hash(rawToken));
        token.setUserId(userId);
        token.setSessionId(uuid("session-" + rawToken));
        token.setSessionStartedAt(NOW.minus(Duration.ofMinutes(1)));
        token.setLastActivityAt(NOW.minus(Duration.ofMinutes(1)));
        token.setSessionExpiresAt(NOW.plus(Duration.ofHours(12)));
        token.setCreatedAt(NOW.minus(Duration.ofMinutes(1)));
        token.setExpiresAt(NOW.plus(Duration.ofDays(1)));
        return token;
    }

    private static UUID uuid(String value) {
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
