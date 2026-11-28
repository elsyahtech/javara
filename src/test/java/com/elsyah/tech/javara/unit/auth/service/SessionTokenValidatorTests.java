package com.elsyah.tech.javara.unit.auth.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import com.elsyah.tech.javara.middleware.security.SessionProperties;
import com.elsyah.tech.javara.repository.auth.UserSessionRepository;
import com.elsyah.tech.javara.service.auth.SessionTokenValidator;

class SessionTokenValidatorTests {
    private static final Instant NOW = Instant.parse("2026-11-24T12:00:00Z");
    private static final UUID USER_ID = UUID.fromString("95dc92e4-d00e-4e16-bc5f-d37a1fd1d524");
    private static final UUID SESSION_ID = UUID.fromString("e64c4125-9206-41f0-81d5-ae76e838fd2f");

    @Test
    void activeSessionIsTouchedWithConfiguredIdleDeadline() {
        UserSessionRepository repository = mock(UserSessionRepository.class);
        Jwt token = token();
        when(repository.touchActiveSession(
                        SESSION_ID, USER_ID, NOW, true, NOW.minusSeconds(900)))
                .thenReturn(1);
        SessionTokenValidator validator = new SessionTokenValidator(
                repository,
                new SessionProperties(null, null, null, null, null),
                Clock.fixed(NOW, ZoneOffset.UTC));

        assertTrue(validator.isActive(token));

        verify(repository).touchActiveSession(
                SESSION_ID, USER_ID, NOW, true, NOW.minusSeconds(900));
    }

    @Test
    void disabledIdleTimeoutTouchesSessionWithoutEnforcingIdleDeadline() {
        UserSessionRepository repository = mock(UserSessionRepository.class);
        Jwt token = token();
        when(repository.touchActiveSession(
                        SESSION_ID, USER_ID, NOW, false, NOW.minusSeconds(900)))
                .thenReturn(1);
        SessionTokenValidator validator = new SessionTokenValidator(
                repository,
                new SessionProperties(false, null, null, null, null),
                Clock.fixed(NOW, ZoneOffset.UTC));

        assertTrue(validator.isActive(token));

        verify(repository).touchActiveSession(
                SESSION_ID, USER_ID, NOW, false, NOW.minusSeconds(900));
    }

    @Test
    void tokenWithoutSessionClaimIsRejectedWithoutRepositoryAccess() {
        UserSessionRepository repository = mock(UserSessionRepository.class);
        Jwt token = mock(Jwt.class);
        when(token.getSubject()).thenReturn(USER_ID.toString());
        SessionTokenValidator validator = new SessionTokenValidator(
                repository,
                new SessionProperties(null, null, null, null, null),
                Clock.fixed(NOW, ZoneOffset.UTC));

        assertFalse(validator.isActive(token));

        verifyNoInteractions(repository);
    }

    @Test
    void expiredOrRevokedSessionIsRejected() {
        UserSessionRepository repository = mock(UserSessionRepository.class);
        Jwt token = token();
        when(repository.touchActiveSession(
                        SESSION_ID, USER_ID, NOW, true, NOW.minusSeconds(900)))
                .thenReturn(0);
        SessionTokenValidator validator = new SessionTokenValidator(
                repository,
                new SessionProperties(null, null, null, null, null),
                Clock.fixed(NOW, ZoneOffset.UTC));

        assertFalse(validator.isActive(token));
    }

    private static Jwt token() {
        Jwt token = mock(Jwt.class);
        when(token.getClaimAsString("sid")).thenReturn(SESSION_ID.toString());
        when(token.getSubject()).thenReturn(USER_ID.toString());
        return token;
    }
}
