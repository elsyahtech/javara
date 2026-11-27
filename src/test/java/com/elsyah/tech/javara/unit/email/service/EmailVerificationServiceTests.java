package com.elsyah.tech.javara.unit.email.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;

import com.elsyah.tech.javara.entity.auth.EmailVerificationPurpose;
import com.elsyah.tech.javara.entity.auth.EmailVerificationTokenEntity;
import com.elsyah.tech.javara.entity.user.UserEntity;
import com.elsyah.tech.javara.middleware.config.ApplicationBrandingProperties;
import com.elsyah.tech.javara.middleware.config.ApplicationIdentityProperties;
import com.elsyah.tech.javara.middleware.email.EmailSettings;
import com.elsyah.tech.javara.repository.auth.EmailVerificationTokenRepository;
import com.elsyah.tech.javara.repository.auth.RefreshTokenRepository;
import com.elsyah.tech.javara.repository.user.UserRepository;
import com.elsyah.tech.javara.service.email.EmailNotificationService;
import com.elsyah.tech.javara.service.email.EmailVerificationService;

class EmailVerificationServiceTests {
    private static final Instant NOW = Instant.parse("2026-11-25T12:00:00Z");

    @Test
    void storesOnlyHashAndIssuesRandomVerificationToken() throws Exception {
        EmailVerificationTokenRepository tokenRepository =
                mock(EmailVerificationTokenRepository.class);
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        EmailNotificationService notifications = notifications(publisher);
        EmailVerificationService service = service(tokenRepository, mock(UserRepository.class),
                notifications);
        UserEntity user = user();

        service.issueRegistrationToken(user);

        ArgumentCaptor<EmailVerificationTokenEntity> tokenCaptor =
                forClass(EmailVerificationTokenEntity.class);
        verify(tokenRepository).save(tokenCaptor.capture());
        EmailVerificationTokenEntity storedToken = tokenCaptor.getValue();
        assertEquals(EmailVerificationPurpose.REGISTRATION, storedToken.getPurpose());
        assertTrue(storedToken.getTokenHash().matches("[0-9a-f]{64}"));
        assertNotNull(storedToken.getExpiresAt());
        assertNotEquals("raw-token", storedToken.getTokenHash());
    }

    @Test
    void verificationConsumesTokenAndMarksUserVerified() throws Exception {
        EmailVerificationTokenRepository tokenRepository =
                mock(EmailVerificationTokenRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        EmailVerificationService service = service(
                tokenRepository,
                userRepository,
                notifications(mock(ApplicationEventPublisher.class)));
        UserEntity user = user();
        user.setEmailVerified(false);
        EmailVerificationTokenEntity token = token(user.getId(), "valid-token");
        when(tokenRepository.findByTokenHashForUpdate(hash("valid-token")))
                .thenReturn(Optional.of(token));
        when(userRepository.findByIdForUpdate(user.getId())).thenReturn(Optional.of(user));
        when(userRepository.saveAndFlush(user)).thenReturn(user);

        var result = service.verify("valid-token");

        assertEquals(HttpStatus.OK, result.status());
        assertTrue(user.isEmailVerified());
        assertEquals(NOW, token.getConsumedAt());
        verify(tokenRepository).save(token);
    }

    @Test
    void expiredVerificationTokenIsRejectedWithoutUserMutation() throws Exception {
        EmailVerificationTokenRepository tokenRepository =
                mock(EmailVerificationTokenRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        EmailVerificationService service = service(
                tokenRepository,
                userRepository,
                notifications(mock(ApplicationEventPublisher.class)));
        EmailVerificationTokenEntity token = token(user().getId(), "expired-token");
        token.setExpiresAt(NOW.minusSeconds(1));
        when(tokenRepository.findByTokenHashForUpdate(hash("expired-token")))
                .thenReturn(Optional.of(token));

        var result = service.verify("expired-token");

        assertEquals(HttpStatus.BAD_REQUEST, result.status());
        verify(userRepository, never()).findByIdForUpdate(org.mockito.ArgumentMatchers.any());
    }

    private static EmailVerificationService service(
            EmailVerificationTokenRepository tokenRepository,
            UserRepository userRepository,
            EmailNotificationService notifications) {
        return new EmailVerificationService(
                tokenRepository,
                userRepository,
                mock(RefreshTokenRepository.class),
                notifications,
                settings(),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static EmailSettings settings() {
        return new EmailSettings(
                true,
                Duration.ofHours(24),
                "notify@example.com",
                "https://javara.example.com");
    }

    private static EmailNotificationService notifications(
            ApplicationEventPublisher publisher) {
        return new EmailNotificationService(
                settings(),
                publisher,
                new ApplicationIdentityProperties("Test Application"),
                new ApplicationBrandingProperties("JAVARA"));
    }

    private static UserEntity user() {
        UserEntity user = new UserEntity();
        user.setId(UUID.randomUUID());
        user.setEmail("person@example.com");
        user.setIsActive(true);
        user.setEmailVerified(true);
        return user;
    }

    private static EmailVerificationTokenEntity token(UUID userId, String rawToken)
            throws Exception {
        EmailVerificationTokenEntity token = new EmailVerificationTokenEntity();
        token.setId(UUID.randomUUID());
        token.setUserId(userId);
        token.setTokenHash(hash(rawToken));
        token.setPurpose(EmailVerificationPurpose.REGISTRATION);
        token.setCreatedAt(NOW.minusSeconds(60));
        token.setExpiresAt(NOW.plus(Duration.ofHours(24)));
        return token;
    }

    private static String hash(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
    }
}
