package com.elsyah.tech.javara.unit.service;

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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

import com.elsyah.tech.javara.entity.RefreshTokenEntity;
import com.elsyah.tech.javara.entity.UserEntity;
import com.elsyah.tech.javara.middleware.security.JwtProperties;
import com.elsyah.tech.javara.middleware.security.TokenTransport;
import com.elsyah.tech.javara.repository.RefreshTokenRepository;
import com.elsyah.tech.javara.repository.UserRepository;
import com.elsyah.tech.javara.request.LoginRequest;
import com.elsyah.tech.javara.service.AccessTokenService;
import com.elsyah.tech.javara.service.AuthService;
import com.elsyah.tech.javara.service.AuthServiceResult;
import com.elsyah.tech.javara.service.ServiceResult;
import com.elsyah.tech.javara.service.UserService;

class AuthServiceTests {
    // Verifies that login returns tokens while persisting only a hash of the refresh token.
    @Test
    void loginIssuesAccessAndRefreshTokensAndStoresOnlyRefreshHash() {
        UserService userService = mock(UserService.class);
        UserRepository userRepository = mock(UserRepository.class);
        RefreshTokenRepository refreshRepository = mock(RefreshTokenRepository.class);
        AccessTokenService accessTokenService = mock(AccessTokenService.class);
        AuthService service = service(
                userService, userRepository, refreshRepository, accessTokenService);
        UserEntity user = user("user-1");
        LoginRequest request = new LoginRequest("user@example.com", "password123");
        when(userService.login(request)).thenReturn(
                new ServiceResult<>(user, HttpStatus.OK, "Login successful"));
        when(accessTokenService.issue(user)).thenReturn("access-token");

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
        assertEquals("user-1", storedToken.getUserId());
        assertTrue(storedToken.getExpiresAt().isAfter(LocalDateTime.now()));
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
        RefreshTokenEntity oldToken = storedToken("user-1", "old-refresh-token");
        UserEntity user = user("user-1");
        when(refreshRepository.findUserIdByTokenHash(hash("old-refresh-token")))
                .thenReturn(Optional.of("user-1"));
        when(refreshRepository.findByTokenHashForUpdate(hash("old-refresh-token")))
                .thenReturn(Optional.of(oldToken));
        when(userRepository.findByIdForUpdate("user-1")).thenReturn(Optional.of(user));
        when(accessTokenService.issue(user)).thenReturn("new-access-token");

        AuthServiceResult result = service.refresh("old-refresh-token");

        ArgumentCaptor<RefreshTokenEntity> tokenCaptor = forClass(RefreshTokenEntity.class);
        verify(refreshRepository).save(tokenCaptor.capture());
        assertEquals(HttpStatus.OK, result.status());
        assertEquals("new-access-token", result.accessToken());
        assertNotEquals("old-refresh-token", result.refreshToken());
        assertNotNull(oldToken.getRevokedAt());
        assertEquals(hash(result.refreshToken()), tokenCaptor.getValue().getTokenHash());
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
        RefreshTokenEntity replayed = storedToken("user-1", "replayed-token");
        replayed.setRevokedAt(LocalDateTime.now().minusMinutes(1));
        when(refreshRepository.findUserIdByTokenHash(hash("replayed-token")))
                .thenReturn(Optional.of("user-1"));
        when(refreshRepository.findByTokenHashForUpdate(hash("replayed-token")))
                .thenReturn(Optional.of(replayed));
        when(userRepository.findByIdForUpdate("user-1"))
                .thenReturn(Optional.of(user("user-1")));

        AuthServiceResult result = service.refresh("replayed-token");

        assertEquals(HttpStatus.UNAUTHORIZED, result.status());
        assertNull(result.accessToken());
        verify(refreshRepository).revokeActiveTokensForUser(
                org.mockito.ArgumentMatchers.eq("user-1"),
                org.mockito.ArgumentMatchers.any(LocalDateTime.class));
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
        RefreshTokenEntity token = storedToken("user-2", "refresh-token");
        when(refreshRepository.findByTokenHashForUpdate(hash("refresh-token")))
                .thenReturn(Optional.of(token));

        boolean loggedOut = service.logout("refresh-token");

        assertTrue(loggedOut);
        assertNotNull(token.getRevokedAt());
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
                        "REFRESH_TOKEN"));
    }

    private static UserEntity user(String id) {
        UserEntity user = new UserEntity();
        user.setId(id);
        user.setEmail("user@example.com");
        user.setIsActive(true);
        return user;
    }

    private static RefreshTokenEntity storedToken(String userId, String rawToken) {
        RefreshTokenEntity token = new RefreshTokenEntity();
        token.setTokenHash(hash(rawToken));
        token.setUserId(userId);
        token.setCreatedAt(LocalDateTime.now().minusMinutes(1));
        token.setExpiresAt(LocalDateTime.now().plusDays(1));
        return token;
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
