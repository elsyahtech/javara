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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

import com.elsyah.tech.javara.entity.auth.RefreshTokenEntity;
import com.elsyah.tech.javara.entity.auth.MfaMethod;
import com.elsyah.tech.javara.entity.auth.UserSessionEntity;
import com.elsyah.tech.javara.entity.user.UserEntity;
import com.elsyah.tech.javara.middleware.security.JwtProperties;
import com.elsyah.tech.javara.middleware.security.SessionProperties;
import com.elsyah.tech.javara.middleware.security.TokenTransport;
import com.elsyah.tech.javara.repository.auth.RefreshTokenRepository;
import com.elsyah.tech.javara.repository.auth.UserSessionRepository;
import com.elsyah.tech.javara.repository.user.UserRepository;
import com.elsyah.tech.javara.request.auth.LoginRequest;
import com.elsyah.tech.javara.service.auth.AccessTokenService;
import com.elsyah.tech.javara.service.auth.AuthService;
import com.elsyah.tech.javara.service.auth.AuthServiceResult;
import com.elsyah.tech.javara.service.auth.SessionRevocationService;
import com.elsyah.tech.javara.service.auth.MfaService;
import com.elsyah.tech.javara.service.auth.MfaLoginCompletion;
import com.elsyah.tech.javara.service.auth.SuspiciousAuthenticationService;
import com.elsyah.tech.javara.service.common.ServiceResult;
import com.elsyah.tech.javara.service.email.EmailNotificationService;
import com.elsyah.tech.javara.response.auth.MfaChallengeResponse;
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
                        any(UserEntity.class),
                        any(UUID.class),
                        any(Instant.class),
                        any()))
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
    void disabledAbsoluteLifetimeUsesRefreshTokenTtlFromLoginAsFixedSessionExpiry() {
        UserService userService = mock(UserService.class);
        UserRepository userRepository = mock(UserRepository.class);
        RefreshTokenRepository refreshRepository = mock(RefreshTokenRepository.class);
        AccessTokenService accessTokenService = mock(AccessTokenService.class);
        UserEntity user = user(uuid("user-1"));
        LoginRequest request = new LoginRequest("user@example.com", "password123");
        when(userService.login(request)).thenReturn(
                new ServiceResult<>(user, HttpStatus.OK, "Login successful"));
        when(userRepository.findByIdForUpdate(user.getId())).thenReturn(Optional.of(user));
        AuthService service = service(
                userService,
                userRepository,
                refreshRepository,
                accessTokenService,
                mfaService(),
                mock(SuspiciousAuthenticationService.class),
                sessionRepository(),
                Clock.fixed(NOW, ZoneOffset.UTC),
                new SessionProperties(false, null, false, null, null));

        AuthServiceResult result = service.login(request);

        ArgumentCaptor<RefreshTokenEntity> tokenCaptor = forClass(RefreshTokenEntity.class);
        verify(refreshRepository).save(tokenCaptor.capture());
        RefreshTokenEntity storedToken = tokenCaptor.getValue();
        assertEquals(HttpStatus.OK, result.status());
        assertEquals(NOW.plus(Duration.ofDays(30)), storedToken.getSessionExpiresAt());
        assertEquals(storedToken.getSessionExpiresAt(), storedToken.getExpiresAt());
    }

    @Test
    void loginRejectsNewSessionWhenConcurrentSessionLimitIsReached() {
        UserService userService = mock(UserService.class);
        UserRepository userRepository = mock(UserRepository.class);
        RefreshTokenRepository refreshRepository = mock(RefreshTokenRepository.class);
        AccessTokenService accessTokenService = mock(AccessTokenService.class);
        UserEntity user = user(uuid("user-1"));
        LoginRequest request = new LoginRequest("user@example.com", "password123");
        when(userService.login(request)).thenReturn(
                new ServiceResult<>(user, HttpStatus.OK, "Login successful"));
        when(userRepository.findByIdForUpdate(user.getId())).thenReturn(Optional.of(user));
        UserSessionRepository userSessionRepository = sessionRepository();
        when(userSessionRepository.countActiveSessions(
                        user.getId(),
                        NOW,
                        true,
                        NOW.minus(Duration.ofMinutes(15))))
                .thenReturn(5L);
        AuthService service = service(
                userService,
                userRepository,
                refreshRepository,
                accessTokenService,
                mfaService(),
                mock(SuspiciousAuthenticationService.class),
                userSessionRepository,
                Clock.fixed(NOW, ZoneOffset.UTC));

        AuthServiceResult result = service.login(request);

        assertEquals(HttpStatus.CONFLICT, result.status());
        assertEquals("Maximum number of active sessions reached", result.message());
        verify(refreshRepository, never()).save(any());
        verifyNoInteractions(accessTokenService);
    }

    @Test
    void loginReturnsMfaChallengeWithoutIssuingTokens() {
        UserService userService = mock(UserService.class);
        UserRepository userRepository = mock(UserRepository.class);
        RefreshTokenRepository refreshRepository = mock(RefreshTokenRepository.class);
        AccessTokenService accessTokenService = mock(AccessTokenService.class);
        MfaService mfaService = mock(MfaService.class);
        AuthService service = service(
                userService, userRepository, refreshRepository, accessTokenService, mfaService);
        UserEntity user = user(uuid("mfa-user"));
        UUID challengeId = uuid("mfa-challenge");
        MfaChallengeResponse challenge = new MfaChallengeResponse(
                challengeId,
                MfaMethod.EMAIL_OTP,
                List.of(MfaMethod.EMAIL_OTP),
                "VERIFY",
                null,
                NOW.plus(Duration.ofMinutes(5)));
        LoginRequest request = new LoginRequest("user@example.com", "password123");
        when(userService.login(request)).thenReturn(
                new ServiceResult<>(user, HttpStatus.OK, "Login successful"));
        when(userRepository.findByIdForUpdate(user.getId())).thenReturn(Optional.of(user));
        when(mfaService.beginLogin(user, null)).thenReturn(
                new ServiceResult<>(challenge, HttpStatus.ACCEPTED, "MFA verification code sent"));

        AuthServiceResult result = service.login(request);

        assertEquals(HttpStatus.ACCEPTED, result.status());
        assertEquals(challenge, result.mfaChallenge());
        assertNull(result.accessToken());
        assertNull(result.refreshToken());
        verify(refreshRepository, never()).save(any());
        verifyNoInteractions(accessTokenService);
    }

    @Test
    void successfulMfaVerificationIssuesTokensWithAuthenticationMethodClaim() {
        UserService userService = mock(UserService.class);
        UserRepository userRepository = mock(UserRepository.class);
        RefreshTokenRepository refreshRepository = mock(RefreshTokenRepository.class);
        AccessTokenService accessTokenService = mock(AccessTokenService.class);
        MfaService mfaService = mock(MfaService.class);
        AuthService service = service(
                userService, userRepository, refreshRepository, accessTokenService, mfaService);
        UserEntity user = user(uuid("mfa-user"));
        UUID challengeId = uuid("mfa-challenge");
        when(mfaService.verifyLoginOtp(challengeId, "12345678")).thenReturn(
                new ServiceResult<>(
                        new MfaLoginCompletion(user.getId(), MfaMethod.EMAIL_OTP),
                        HttpStatus.OK,
                        "MFA verification successful"));
        when(userRepository.findByIdForUpdate(user.getId())).thenReturn(Optional.of(user));
        when(accessTokenService.issue(
                        org.mockito.ArgumentMatchers.eq(user),
                        any(UUID.class),
                        any(Instant.class),
                        org.mockito.ArgumentMatchers.eq(List.of("pwd", "otp"))))
                .thenReturn("mfa-access-token");

        AuthServiceResult result = service.verifyMfaOtpLogin(challengeId, "12345678");

        ArgumentCaptor<RefreshTokenEntity> tokenCaptor = forClass(RefreshTokenEntity.class);
        verify(refreshRepository).save(tokenCaptor.capture());
        assertEquals(HttpStatus.OK, result.status());
        assertEquals("mfa-access-token", result.accessToken());
        assertEquals("pwd,otp", tokenCaptor.getValue().getAuthenticationMethods());
    }

    // Verifies that a valid refresh token is revoked and replaced with a new token.
    @Test
    void refreshRotatesTokenAndIssuesANewAccessToken() {
        UserService userService = mock(UserService.class);
        UserRepository userRepository = mock(UserRepository.class);
        RefreshTokenRepository refreshRepository = mock(RefreshTokenRepository.class);
        AccessTokenService accessTokenService = mock(AccessTokenService.class);
        RefreshTokenEntity oldToken = storedToken(uuid("user-1"), "old-refresh-token");
        oldToken.getSession().setAuthenticationMethods("pwd,otp");
        UserEntity user = user(uuid("user-1"));
        UserSessionRepository userSessionRepository = sessionRepository();
        when(userSessionRepository.findByIdAndUserIdForUpdate(
                        oldToken.getSessionId(), oldToken.getUserId()))
                .thenReturn(Optional.of(oldToken.getSession()));
        when(refreshRepository.findUserIdByTokenHash(hash("old-refresh-token")))
                .thenReturn(Optional.of(uuid("user-1")));
        when(refreshRepository.findByTokenHashForUpdate(hash("old-refresh-token")))
                .thenReturn(Optional.of(oldToken));
        when(userRepository.findByIdForUpdate(uuid("user-1"))).thenReturn(Optional.of(user));
        when(accessTokenService.issue(
                        user,
                        oldToken.getSessionId(),
                        oldToken.getSessionStartedAt(),
                        List.of("pwd", "otp")))
                .thenReturn("new-access-token");

        AuthServiceResult result = service(
                        userService,
                        userRepository,
                        refreshRepository,
                        accessTokenService,
                        mfaService(),
                        mock(SuspiciousAuthenticationService.class),
                        userSessionRepository,
                        Clock.fixed(NOW, ZoneOffset.UTC))
                .refresh("old-refresh-token");

        ArgumentCaptor<RefreshTokenEntity> tokenCaptor = forClass(RefreshTokenEntity.class);
        verify(refreshRepository).save(tokenCaptor.capture());
        assertEquals(HttpStatus.OK, result.status());
        assertEquals("new-access-token", result.accessToken());
        assertNotEquals("old-refresh-token", result.refreshToken());
        assertNotNull(oldToken.getRevokedAt());
        assertEquals(hash(result.refreshToken()), tokenCaptor.getValue().getTokenHash());
        assertEquals(oldToken.getSessionId(), tokenCaptor.getValue().getSessionId());
        assertEquals(oldToken.getSessionExpiresAt(), tokenCaptor.getValue().getSessionExpiresAt());
        assertEquals("pwd,otp", tokenCaptor.getValue().getAuthenticationMethods());
    }

    @Test
    void repeatedRefreshesPreserveAbsoluteExpiryAndRejectAtBoundary() {
        UserService userService = mock(UserService.class);
        UserRepository userRepository = mock(UserRepository.class);
        RefreshTokenRepository refreshRepository = mock(RefreshTokenRepository.class);
        AccessTokenService accessTokenService = mock(AccessTokenService.class);
        UUID userId = uuid("user-1");
        UUID sessionId = uuid("session-repeated-refresh");
        Instant sessionStartedAt = NOW.minus(Duration.ofHours(11));
        Instant sessionExpiresAt = NOW.plus(Duration.ofHours(1));
        UserEntity user = user(userId);
        UserSessionRepository userSessionRepository = sessionRepository();
        List<RefreshTokenEntity> rotatedTokens = new ArrayList<>();
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(user));
        when(refreshRepository.save(any(RefreshTokenEntity.class))).thenAnswer(invocation -> {
            RefreshTokenEntity savedToken = invocation.getArgument(0);
            rotatedTokens.add(savedToken);
            return savedToken;
        });

        String rawToken = "initial-refresh-token";
        RefreshTokenEntity currentToken = storedToken(userId, rawToken);
        currentToken.getSession().setId(sessionId);
        currentToken.getSession().setStartedAt(sessionStartedAt);
        currentToken.getSession().setExpiresAt(sessionExpiresAt);
        when(userSessionRepository.findByIdAndUserIdForUpdate(sessionId, userId))
                .thenReturn(Optional.of(currentToken.getSession()));

        List<Instant> refreshTimes = List.of(
                NOW,
                NOW.plus(Duration.ofMinutes(14)),
                NOW.plus(Duration.ofMinutes(28)),
                NOW.plus(Duration.ofMinutes(42)),
                NOW.plus(Duration.ofMinutes(56)),
                NOW.plus(Duration.ofMinutes(59)));
        for (int i = 0; i < refreshTimes.size(); i++) {
            Instant refreshTime = refreshTimes.get(i);
            when(refreshRepository.findUserIdByTokenHash(hash(rawToken)))
                    .thenReturn(Optional.of(userId));
            when(refreshRepository.findByTokenHashForUpdate(hash(rawToken)))
                    .thenReturn(Optional.of(currentToken));

            AuthServiceResult result = service(
                            userService,
                            userRepository,
                            refreshRepository,
                            accessTokenService,
                            mfaService(),
                            mock(SuspiciousAuthenticationService.class),
                            userSessionRepository,
                            Clock.fixed(refreshTime, ZoneOffset.UTC))
                    .refresh(rawToken);

            assertEquals(HttpStatus.OK, result.status());
            RefreshTokenEntity rotatedToken = rotatedTokens.get(i);
            assertEquals(sessionId, rotatedToken.getSessionId());
            assertEquals(sessionStartedAt, rotatedToken.getSessionStartedAt());
            assertEquals(sessionExpiresAt, rotatedToken.getSessionExpiresAt());
            assertEquals(sessionExpiresAt, rotatedToken.getExpiresAt());
            currentToken = rotatedToken;
            rawToken = result.refreshToken();
        }

        when(refreshRepository.findUserIdByTokenHash(hash(rawToken)))
                .thenReturn(Optional.of(userId));
        when(refreshRepository.findByTokenHashForUpdate(hash(rawToken)))
                .thenReturn(Optional.of(currentToken));
        AuthServiceResult atAbsoluteExpiry = service(
                        userService,
                        userRepository,
                        refreshRepository,
                        accessTokenService,
                        mfaService(),
                        mock(SuspiciousAuthenticationService.class),
                        userSessionRepository,
                        Clock.fixed(sessionExpiresAt, ZoneOffset.UTC))
                .refresh(rawToken);

        assertEquals(HttpStatus.UNAUTHORIZED, atAbsoluteExpiry.status());
        verify(refreshRepository, times(refreshTimes.size()))
                .save(any(RefreshTokenEntity.class));
        verify(refreshRepository).revokeActiveTokensForSession(
                sessionId, userId, sessionExpiresAt);
    }

    @Test
    void refreshWithAbsoluteLifetimeDisabledDoesNotExtendLoginBasedRefreshExpiry() {
        UserService userService = mock(UserService.class);
        UserRepository userRepository = mock(UserRepository.class);
        RefreshTokenRepository refreshRepository = mock(RefreshTokenRepository.class);
        AccessTokenService accessTokenService = mock(AccessTokenService.class);
        UUID userId = uuid("user-1");
        RefreshTokenEntity token = storedToken(userId, "fixed-refresh-token");
        Instant sessionStartedAt = NOW.minus(Duration.ofDays(23));
        Instant sessionExpiresAt = sessionStartedAt.plus(Duration.ofDays(30));
        token.getSession().setStartedAt(sessionStartedAt);
        token.getSession().setLastActivityAt(NOW.minus(Duration.ofMinutes(1)));
        token.getSession().setExpiresAt(sessionExpiresAt);
        token.setExpiresAt(sessionExpiresAt);
        UserSessionRepository userSessionRepository = sessionRepository();
        when(userSessionRepository.findByIdAndUserIdForUpdate(
                        token.getSessionId(), userId))
                .thenReturn(Optional.of(token.getSession()));
        when(refreshRepository.findUserIdByTokenHash(hash("fixed-refresh-token")))
                .thenReturn(Optional.of(userId));
        when(refreshRepository.findByTokenHashForUpdate(hash("fixed-refresh-token")))
                .thenReturn(Optional.of(token));
        when(userRepository.findByIdForUpdate(userId))
                .thenReturn(Optional.of(user(userId)));

        AuthServiceResult result = service(
                        userService,
                        userRepository,
                        refreshRepository,
                        accessTokenService,
                        mfaService(),
                        mock(SuspiciousAuthenticationService.class),
                        userSessionRepository,
                        Clock.fixed(NOW, ZoneOffset.UTC),
                        new SessionProperties(false, null, false, null, null))
                .refresh("fixed-refresh-token");

        ArgumentCaptor<RefreshTokenEntity> tokenCaptor = forClass(RefreshTokenEntity.class);
        verify(refreshRepository).save(tokenCaptor.capture());
        assertEquals(HttpStatus.OK, result.status());
        assertEquals(sessionExpiresAt, tokenCaptor.getValue().getSessionExpiresAt());
        assertEquals(sessionExpiresAt, tokenCaptor.getValue().getExpiresAt());
    }

    @Test
    void refreshRejectsSessionAfterAbsoluteLifetime() {
        UserService userService = mock(UserService.class);
        UserRepository userRepository = mock(UserRepository.class);
        RefreshTokenRepository refreshRepository = mock(RefreshTokenRepository.class);
        AccessTokenService accessTokenService = mock(AccessTokenService.class);
        RefreshTokenEntity token = storedToken(uuid("user-1"), "expired-session-token");
        token.getSession().setExpiresAt(NOW.minusSeconds(1));
        UserSessionRepository userSessionRepository = sessionRepository();
        when(userSessionRepository.findByIdAndUserIdForUpdate(
                        token.getSessionId(), token.getUserId()))
                .thenReturn(Optional.of(token.getSession()));
        AuthService service = service(
                userService,
                userRepository,
                refreshRepository,
                accessTokenService,
                mfaService(),
                mock(SuspiciousAuthenticationService.class),
                userSessionRepository,
                Clock.fixed(NOW, ZoneOffset.UTC));
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
        RefreshTokenEntity token = storedToken(uuid("user-1"), "idle-session-token");
        token.getSession().setLastActivityAt(NOW.minus(Duration.ofMinutes(16)));
        UserSessionRepository userSessionRepository = sessionRepository();
        when(userSessionRepository.findByIdAndUserIdForUpdate(
                        token.getSessionId(), token.getUserId()))
                .thenReturn(Optional.of(token.getSession()));
        AuthService service = service(
                userService,
                userRepository,
                refreshRepository,
                accessTokenService,
                mfaService(),
                mock(SuspiciousAuthenticationService.class),
                userSessionRepository,
                Clock.fixed(NOW, ZoneOffset.UTC));
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

    @Test
    void refreshSucceedsAfterIdlePeriodWhenIdleTimeoutIsDisabled() {
        UserService userService = mock(UserService.class);
        UserRepository userRepository = mock(UserRepository.class);
        RefreshTokenRepository refreshRepository = mock(RefreshTokenRepository.class);
        AccessTokenService accessTokenService = mock(AccessTokenService.class);
        RefreshTokenEntity token = storedToken(uuid("user-1"), "idle-session-token");
        token.getSession().setLastActivityAt(NOW.minus(Duration.ofHours(2)));
        UserSessionRepository userSessionRepository = sessionRepository();
        when(userSessionRepository.findByIdAndUserIdForUpdate(
                        token.getSessionId(), token.getUserId()))
                .thenReturn(Optional.of(token.getSession()));
        when(refreshRepository.findUserIdByTokenHash(hash("idle-session-token")))
                .thenReturn(Optional.of(token.getUserId()));
        when(refreshRepository.findByTokenHashForUpdate(hash("idle-session-token")))
                .thenReturn(Optional.of(token));
        when(userRepository.findByIdForUpdate(token.getUserId()))
                .thenReturn(Optional.of(user(token.getUserId())));
        AuthService service = service(
                userService,
                userRepository,
                refreshRepository,
                accessTokenService,
                mfaService(),
                mock(SuspiciousAuthenticationService.class),
                userSessionRepository,
                Clock.fixed(NOW, ZoneOffset.UTC),
                new SessionProperties(false, null, null, null, null));

        AuthServiceResult result = service.refresh("idle-session-token");

        assertEquals(HttpStatus.OK, result.status());
        assertEquals(NOW, token.getSession().getLastActivityAt());
        verify(userSessionRepository).save(token.getSession());
        verify(refreshRepository).save(any(RefreshTokenEntity.class));
    }

    @Test
    void refreshKeepsSameRefreshTokenWhenRotationIsDisabled() {
        UserService userService = mock(UserService.class);
        UserRepository userRepository = mock(UserRepository.class);
        RefreshTokenRepository refreshRepository = mock(RefreshTokenRepository.class);
        AccessTokenService accessTokenService = mock(AccessTokenService.class);
        RefreshTokenEntity token = storedToken(uuid("user-1"), "stable-refresh-token");
        UserSessionRepository userSessionRepository = sessionRepository();
        when(userSessionRepository.findByIdAndUserIdForUpdate(
                        token.getSessionId(), token.getUserId()))
                .thenReturn(Optional.of(token.getSession()));
        when(refreshRepository.findUserIdByTokenHash(hash("stable-refresh-token")))
                .thenReturn(Optional.of(token.getUserId()));
        when(refreshRepository.findByTokenHashForUpdate(hash("stable-refresh-token")))
                .thenReturn(Optional.of(token));
        when(userRepository.findByIdForUpdate(token.getUserId()))
                .thenReturn(Optional.of(user(token.getUserId())));
        AuthService service = service(
                userService,
                userRepository,
                refreshRepository,
                accessTokenService,
                mfaService(),
                mock(SuspiciousAuthenticationService.class),
                userSessionRepository,
                Clock.fixed(NOW, ZoneOffset.UTC),
                new SessionProperties(false, null, true, null, null),
                false);

        AuthServiceResult firstRefresh = service.refresh("stable-refresh-token");
        AuthServiceResult secondRefresh = service.refresh("stable-refresh-token");

        assertEquals(HttpStatus.OK, firstRefresh.status());
        assertEquals(HttpStatus.OK, secondRefresh.status());
        assertEquals("stable-refresh-token", firstRefresh.refreshToken());
        assertEquals("stable-refresh-token", secondRefresh.refreshToken());
        assertNull(token.getRevokedAt());
        verify(refreshRepository, never()).save(any(RefreshTokenEntity.class));
    }

    // Verifies that replaying a revoked refresh token revokes the user's other active tokens.
    @Test
    void replayedRefreshTokenRevokesAllActiveTokens() {
        UserService userService = mock(UserService.class);
        UserRepository userRepository = mock(UserRepository.class);
        RefreshTokenRepository refreshRepository = mock(RefreshTokenRepository.class);
        AccessTokenService accessTokenService = mock(AccessTokenService.class);
        RefreshTokenEntity replayed = storedToken(uuid("user-1"), "replayed-token");
        replayed.setRevokedAt(NOW.minus(Duration.ofMinutes(1)));
        UserSessionRepository userSessionRepository = sessionRepository();
        when(userSessionRepository.findByIdAndUserIdForUpdate(
                        replayed.getSessionId(), replayed.getUserId()))
                .thenReturn(Optional.of(replayed.getSession()));
        when(refreshRepository.findUserIdByTokenHash(hash("replayed-token")))
                .thenReturn(Optional.of(uuid("user-1")));
        when(refreshRepository.findByTokenHashForUpdate(hash("replayed-token")))
                .thenReturn(Optional.of(replayed));
        when(userRepository.findByIdForUpdate(uuid("user-1")))
                .thenReturn(Optional.of(user(uuid("user-1"))));

        AuthServiceResult result = service(
                        userService,
                        userRepository,
                        refreshRepository,
                        accessTokenService,
                        mfaService(),
                        mock(SuspiciousAuthenticationService.class),
                        userSessionRepository,
                        Clock.fixed(NOW, ZoneOffset.UTC))
                .refresh("replayed-token");

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

    @Test
    void logoutWithRotatedRefreshTokenStillRevokesItsSession() {
        UserService userService = mock(UserService.class);
        UserRepository userRepository = mock(UserRepository.class);
        RefreshTokenRepository refreshRepository = mock(RefreshTokenRepository.class);
        AccessTokenService accessTokenService = mock(AccessTokenService.class);
        AuthService service = service(
                userService, userRepository, refreshRepository, accessTokenService);
        RefreshTokenEntity token = storedToken(uuid("user-2"), "rotated-refresh-token");
        token.setRevokedAt(NOW.minusSeconds(1));
        when(refreshRepository.findByTokenHashForUpdate(hash("rotated-refresh-token")))
                .thenReturn(Optional.of(token));

        boolean loggedOut = service.logout("rotated-refresh-token");

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

    @Test
    void loginRecordsFailureForActiveVerifiedAccount() {
        UserService userService = mock(UserService.class);
        UserRepository userRepository = mock(UserRepository.class);
        RefreshTokenRepository refreshRepository = mock(RefreshTokenRepository.class);
        AccessTokenService accessTokenService = mock(AccessTokenService.class);
        SuspiciousAuthenticationService suspiciousAuthenticationService =
                mock(SuspiciousAuthenticationService.class);
        AuthService service = service(
                userService,
                userRepository,
                refreshRepository,
                accessTokenService,
                mfaService(),
                suspiciousAuthenticationService);
        LoginRequest request = new LoginRequest("user@example.com", "wrong-password");
        UserEntity user = user(uuid("user-1"));
        when(userService.login(request)).thenReturn(new ServiceResult<>(
                null, HttpStatus.UNAUTHORIZED, "Invalid email or password"));
        when(userRepository.findByEmailForUpdate(request.email()))
                .thenReturn(Optional.of(user));

        AuthServiceResult result = service.login(request);

        assertEquals(HttpStatus.UNAUTHORIZED, result.status());
        verify(suspiciousAuthenticationService).recordFailedPassword(user);
        verify(refreshRepository, never()).save(any());
    }

    @Test
    void activeAccountLockPreventsMfaChallengeAndTokenIssuance() {
        UserService userService = mock(UserService.class);
        UserRepository userRepository = mock(UserRepository.class);
        RefreshTokenRepository refreshRepository = mock(RefreshTokenRepository.class);
        AccessTokenService accessTokenService = mock(AccessTokenService.class);
        MfaService mfaService = mfaService();
        SuspiciousAuthenticationService suspiciousAuthenticationService =
                mock(SuspiciousAuthenticationService.class);
        AuthService service = service(
                userService,
                userRepository,
                refreshRepository,
                accessTokenService,
                mfaService,
                suspiciousAuthenticationService);
        UserEntity user = user(uuid("user-1"));
        LoginRequest request = new LoginRequest("user@example.com", "password123");
        when(userService.login(request)).thenReturn(
                new ServiceResult<>(user, HttpStatus.OK, "Login successful"));
        when(userRepository.findByIdForUpdate(user.getId())).thenReturn(Optional.of(user));
        when(suspiciousAuthenticationService.isLocked(user)).thenReturn(true);

        AuthServiceResult result = service.login(request);

        assertEquals(HttpStatus.UNAUTHORIZED, result.status());
        verify(mfaService, never()).beginLogin(any(UserEntity.class), any());
        verify(refreshRepository, never()).save(any());
        verifyNoInteractions(accessTokenService);
    }

    private static AuthService service(
            UserService userService,
            UserRepository userRepository,
            RefreshTokenRepository refreshRepository,
            AccessTokenService accessTokenService) {
        return service(userService, userRepository, refreshRepository, accessTokenService, mfaService());
    }

    private static AuthService service(
            UserService userService,
            UserRepository userRepository,
            RefreshTokenRepository refreshRepository,
            AccessTokenService accessTokenService,
            MfaService mfaService) {
        SuspiciousAuthenticationService suspiciousAuthenticationService =
                mock(SuspiciousAuthenticationService.class);
        return service(
                userService,
                userRepository,
                refreshRepository,
                accessTokenService,
                mfaService,
                suspiciousAuthenticationService);
    }

    private static AuthService service(
            UserService userService,
            UserRepository userRepository,
            RefreshTokenRepository refreshRepository,
            AccessTokenService accessTokenService,
            MfaService mfaService,
            SuspiciousAuthenticationService suspiciousAuthenticationService) {
        return service(
                userService,
                userRepository,
                refreshRepository,
                accessTokenService,
                mfaService,
                suspiciousAuthenticationService,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static AuthService service(
            UserService userService,
            UserRepository userRepository,
            RefreshTokenRepository refreshRepository,
            AccessTokenService accessTokenService,
            MfaService mfaService,
            SuspiciousAuthenticationService suspiciousAuthenticationService,
            Clock clock) {
        return service(
                userService,
                userRepository,
                refreshRepository,
                accessTokenService,
                mfaService,
                suspiciousAuthenticationService,
                sessionRepository(),
                clock);
    }

    private static AuthService service(
            UserService userService,
            UserRepository userRepository,
            RefreshTokenRepository refreshRepository,
            AccessTokenService accessTokenService,
            MfaService mfaService,
            SuspiciousAuthenticationService suspiciousAuthenticationService,
            UserSessionRepository userSessionRepository,
            Clock clock) {
        return service(
                userService,
                userRepository,
                refreshRepository,
                accessTokenService,
                mfaService,
                suspiciousAuthenticationService,
                userSessionRepository,
                clock,
                new SessionProperties(null, null, null, null, null));
    }

    private static AuthService service(
            UserService userService,
            UserRepository userRepository,
            RefreshTokenRepository refreshRepository,
            AccessTokenService accessTokenService,
            MfaService mfaService,
            SuspiciousAuthenticationService suspiciousAuthenticationService,
            UserSessionRepository userSessionRepository,
            Clock clock,
            SessionProperties sessionProperties) {
        return service(
                userService,
                userRepository,
                refreshRepository,
                accessTokenService,
                mfaService,
                suspiciousAuthenticationService,
                userSessionRepository,
                clock,
                sessionProperties,
                true);
    }

    private static AuthService service(
            UserService userService,
            UserRepository userRepository,
            RefreshTokenRepository refreshRepository,
            AccessTokenService accessTokenService,
            MfaService mfaService,
            SuspiciousAuthenticationService suspiciousAuthenticationService,
            UserSessionRepository userSessionRepository,
            Clock clock,
            SessionProperties sessionProperties,
            boolean refreshTokenRotationEnabled) {
        when(suspiciousAuthenticationService.recordSuccessfulSource(
                        any(UserEntity.class),
                        org.mockito.ArgumentMatchers.isNull()))
                .thenReturn(true);
        return new AuthService(
                userService,
                userRepository,
                refreshRepository,
                userSessionRepository,
                new SessionRevocationService(userSessionRepository, refreshRepository),
                accessTokenService,
                new JwtProperties(
                        Base64.getEncoder().encodeToString(new byte[32]),
                        TokenTransport.BEARER,
                        Duration.ofMinutes(15),
                        Duration.ofDays(30),
                        true,
                        "Strict",
                        "ACCESS_TOKEN",
                        "REFRESH_TOKEN",
                        refreshTokenRotationEnabled),
                sessionProperties,
                mock(EmailNotificationService.class),
                mfaService,
                suspiciousAuthenticationService,
                clock);
    }

    private static UserSessionRepository sessionRepository() {
        UserSessionRepository repository = mock(UserSessionRepository.class);
        when(repository.save(any(UserSessionEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        return repository;
    }

    private static MfaService mfaService() {
        MfaService service = mock(MfaService.class);
        when(service.beginLogin(any(UserEntity.class), org.mockito.ArgumentMatchers.isNull())).thenReturn(
                new ServiceResult<>(null, HttpStatus.OK, "MFA is not enabled"));
        return service;
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
        UserSessionEntity session = new UserSessionEntity();
        session.setId(uuid("session-" + rawToken));
        session.setUserId(userId);
        session.setStartedAt(NOW.minus(Duration.ofMinutes(1)));
        session.setLastActivityAt(NOW.minus(Duration.ofMinutes(1)));
        session.setExpiresAt(NOW.plus(Duration.ofHours(12)));
        session.setAuthenticationMethods("pwd");
        session.setCreatedAt(NOW.minus(Duration.ofMinutes(1)));
        token.setSession(session);
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
