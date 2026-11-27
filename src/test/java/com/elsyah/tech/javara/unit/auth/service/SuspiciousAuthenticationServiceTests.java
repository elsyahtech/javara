package com.elsyah.tech.javara.unit.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.elsyah.tech.javara.entity.user.UserEntity;
import com.elsyah.tech.javara.middleware.security.JwtProperties;
import com.elsyah.tech.javara.middleware.security.SuspiciousAuthenticationProperties;
import com.elsyah.tech.javara.middleware.security.SuspiciousAuthenticationProperties.Action;
import com.elsyah.tech.javara.middleware.security.SuspiciousAuthenticationProperties.EventSignal;
import com.elsyah.tech.javara.middleware.security.SuspiciousAuthenticationProperties.FailureSignal;
import com.elsyah.tech.javara.middleware.security.TokenTransport;
import com.elsyah.tech.javara.repository.user.UserRepository;
import com.elsyah.tech.javara.service.auth.SuspiciousAuthenticationService;
import com.elsyah.tech.javara.service.email.EmailNotificationService;

class SuspiciousAuthenticationServiceTests {
    private static final Instant NOW = Instant.parse("2026-11-27T08:00:00Z");
    private static final UUID USER_ID =
            UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");

    @Test
    void defaultPolicyEnablesAllSignalsWithConfigurableFailureThresholds() {
        SuspiciousAuthenticationProperties properties =
                new SuspiciousAuthenticationProperties(true, null, null, null, null);

        assertTrue(properties.failedLogin().enabled());
        assertTrue(properties.newSource().enabled());
        assertTrue(properties.failedMfa().enabled());
        assertTrue(properties.securityChange().enabled());
        assertEquals(5, properties.failedLogin().threshold());
        assertEquals(Duration.ofMinutes(10), properties.failedLogin().window());
        assertEquals(Duration.ofMinutes(15), properties.failedLogin().lockDuration());
        assertEquals(Action.NOTIFY, properties.newSource().action());
    }

    @Test
    void repeatedPasswordFailuresLockTemporarilyAtConfiguredThreshold() {
        UserRepository userRepository = mock(UserRepository.class);
        EmailNotificationService emailService = mock(EmailNotificationService.class);
        UserEntity user = user();
        user.setFailedLoginAttempts(1);
        user.setLoginFailureWindowStartedAt(NOW.minus(Duration.ofMinutes(1)));
        SuspiciousAuthenticationService service = service(
                userRepository,
                emailService,
                properties(new FailureSignal(
                        true,
                        2,
                        Duration.ofMinutes(10),
                        Duration.ofMinutes(20),
                        Action.LOCK_AND_NOTIFY),
                        event(Action.NOTIFY)));

        service.recordFailedPassword(user);

        assertEquals(0, user.getFailedLoginAttempts());
        assertNull(user.getLoginFailureWindowStartedAt());
        assertEquals(NOW.plus(Duration.ofMinutes(20)), user.getAccountLockedUntil());
        assertTrue(service.isLocked(user));
        verify(emailService).suspiciousAuthentication(
                "user@example.com",
                "Repeated unsuccessful sign-in attempts were detected. "
                        + "Sign-in is temporarily locked until "
                        + NOW.plus(Duration.ofMinutes(20)) + ".");
        verify(userRepository).save(user);
    }

    @Test
    void expiredFailureWindowStartsAgainAtOne() {
        UserRepository userRepository = mock(UserRepository.class);
        UserEntity user = user();
        user.setFailedLoginAttempts(9);
        user.setLoginFailureWindowStartedAt(NOW.minus(Duration.ofMinutes(10)));
        SuspiciousAuthenticationService service = service(
                userRepository,
                mock(EmailNotificationService.class),
                properties(new FailureSignal(
                        true,
                        5,
                        Duration.ofMinutes(10),
                        Duration.ofMinutes(15),
                        Action.NOTIFY),
                        event(Action.NOTIFY)));

        service.recordFailedPassword(user);

        assertEquals(1, user.getFailedLoginAttempts());
        assertEquals(NOW, user.getLoginFailureWindowStartedAt());
        assertFalse(service.isLocked(user));
    }

    @Test
    void notificationOnlyPolicyAlertsOncePerFailureWindow() {
        EmailNotificationService emailService = mock(EmailNotificationService.class);
        UserEntity user = user();
        SuspiciousAuthenticationService service = service(
                mock(UserRepository.class),
                emailService,
                properties(
                        new FailureSignal(
                                true,
                                1,
                                Duration.ofMinutes(10),
                                Duration.ofMinutes(15),
                                Action.NOTIFY),
                        event(Action.NOTIFY)));

        service.recordFailedPassword(user);
        service.recordFailedPassword(user);

        assertEquals(1, user.getFailedLoginAttempts());
        verify(emailService, times(1)).suspiciousAuthentication(
                "user@example.com",
                "Repeated unsuccessful sign-in attempts were detected.");
    }

    @Test
    void changedSuccessfulSourceIsHashedAndNotifiesWithoutLockByDefault() {
        UserRepository userRepository = mock(UserRepository.class);
        EmailNotificationService emailService = mock(EmailNotificationService.class);
        UserEntity user = user();
        user.setLastSuccessfulSourceHash("a".repeat(64));
        SuspiciousAuthenticationService service = service(
                userRepository, emailService, properties(null, event(Action.NOTIFY)));

        boolean accepted = service.recordSuccessfulSource(user, "198.51.100.10");

        assertTrue(accepted);
        assertNotEquals("198.51.100.10", user.getLastSuccessfulSourceHash());
        assertEquals(64, user.getLastSuccessfulSourceHash().length());
        assertFalse(service.isLocked(user));
        verify(emailService).suspiciousAuthentication(
                "user@example.com",
                "A successful sign-in was detected from a new network address.");
        verify(userRepository).save(user);
    }

    @Test
    void failedMfaAttemptsUseTheirOwnConfiguredLockPolicy() {
        UserRepository userRepository = mock(UserRepository.class);
        EmailNotificationService emailService = mock(EmailNotificationService.class);
        UserEntity user = user();
        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(user));
        SuspiciousAuthenticationService service = service(
                userRepository,
                emailService,
                properties(
                        new FailureSignal(
                                true,
                                1,
                                Duration.ofMinutes(5),
                                Duration.ofMinutes(7),
                                Action.LOCK_AND_NOTIFY),
                        event(Action.NOTIFY)));

        service.recordFailedMfa(USER_ID);

        assertEquals(0, user.getFailedMfaAttempts());
        assertEquals(NOW.plus(Duration.ofMinutes(7)), user.getAccountLockedUntil());
        verify(emailService).suspiciousAuthentication(
                "user@example.com",
                "Repeated unsuccessful multi-factor verification attempts were detected. "
                        + "Sign-in is temporarily locked until "
                        + NOW.plus(Duration.ofMinutes(7)) + ".");
    }

    @Test
    void invalidFailureWindowIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new FailureSignal(
                true,
                5,
                Duration.ofDays(2),
                Duration.ofMinutes(15),
                Action.NOTIFY));
    }

    @Test
    void sameSourceDoesNotNotifyButChangedSourceDoes() {
        EmailNotificationService emailService = mock(EmailNotificationService.class);
        UserEntity user = user();
        SuspiciousAuthenticationService service = service(
                mock(UserRepository.class),
                emailService,
                properties(null, event(Action.NOTIFY)));

        boolean accepted1 = service.recordSuccessfulSource(user, "198.51.100.10");
        boolean accepted2 = service.recordSuccessfulSource(user, "198.51.100.10");

        assertTrue(accepted1);
        assertTrue(accepted2);
        verifyNoInteractions(emailService);

        boolean accepted3 = service.recordSuccessfulSource(user, "198.51.100.11");
        assertTrue(accepted3);

        verify(emailService, times(1)).suspiciousAuthentication(
                "user@example.com",
                "A successful sign-in was detected from a new network address.");
    }

    private static SuspiciousAuthenticationProperties properties(
            FailureSignal failedLogin, EventSignal source) {
        FailureSignal failedMfa = failedLogin == null
                ? null
                : new FailureSignal(
                        true,
                        failedLogin.threshold(),
                        failedLogin.window(),
                        failedLogin.lockDuration(),
                        failedLogin.action());
        return new SuspiciousAuthenticationProperties(
                true,
                failedLogin,
                source,
                failedMfa,
                event(Action.NOTIFY));
    }

    private static EventSignal event(Action action) {
        return new EventSignal(true, action, Duration.ofMinutes(15));
    }

    private static SuspiciousAuthenticationService service(
            UserRepository userRepository,
            EmailNotificationService emailService,
            SuspiciousAuthenticationProperties properties) {
        return new SuspiciousAuthenticationService(
                properties,
                userRepository,
                emailService,
                new JwtProperties(
                        Base64.getEncoder().encodeToString(new byte[32]),
                        TokenTransport.BEARER,
                        Duration.ofMinutes(15),
                        Duration.ofDays(30),
                        true,
                        "Strict",
                        "ACCESS_TOKEN",
                        "REFRESH_TOKEN"),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static UserEntity user() {
        UserEntity user = new UserEntity();
        user.setId(USER_ID);
        user.setEmail("user@example.com");
        user.setIsActive(true);
        return user;
    }
}
