package com.elsyah.tech.javara.service.email;

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

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.elsyah.tech.javara.entity.auth.EmailVerificationPurpose;
import com.elsyah.tech.javara.entity.auth.EmailVerificationTokenEntity;
import com.elsyah.tech.javara.entity.user.UserEntity;
import com.elsyah.tech.javara.middleware.email.EmailSettings;
import com.elsyah.tech.javara.repository.auth.EmailVerificationTokenRepository;
import com.elsyah.tech.javara.repository.user.UserRepository;
import com.elsyah.tech.javara.service.auth.SessionRevocationService;
import com.elsyah.tech.javara.service.common.ServiceResult;

@Service
@ConditionalOnProperty(
        prefix = "app.features",
        name = "auth-api",
        havingValue = "true",
        matchIfMissing = true)
public class EmailVerificationService {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final EmailVerificationTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final SessionRevocationService sessionRevocationService;
    private final EmailNotificationService emailNotificationService;
    private final EmailSettings settings;
    private final Clock clock;

    public EmailVerificationService(
            EmailVerificationTokenRepository tokenRepository,
            UserRepository userRepository,
            SessionRevocationService sessionRevocationService,
            EmailNotificationService emailNotificationService,
            EmailSettings settings,
            Clock clock) {
        this.tokenRepository = tokenRepository;
        this.userRepository = userRepository;
        this.sessionRevocationService = sessionRevocationService;
        this.emailNotificationService = emailNotificationService;
        this.settings = settings;
        this.clock = clock;
    }

    @Transactional
    public void issueRegistrationToken(UserEntity user) {
        issue(user, EmailVerificationPurpose.REGISTRATION, null, user.getEmail());
    }

    @Transactional
    public void issueEmailChangeToken(UserEntity user, String newEmail) {
        issue(user, EmailVerificationPurpose.EMAIL_CHANGE, newEmail, newEmail);
    }

    @Transactional
    public void resendRegistrationToken(String email) {
        if (!emailNotificationService.isEnabled()) {
            return;
        }
        Optional<UserEntity> optionalUser = userRepository.findByEmailForUpdate(email);
        if (optionalUser.isEmpty()) {
            return;
        }
        UserEntity user = optionalUser.get();
        if (Boolean.TRUE.equals(user.getIsActive())
                && user.getDeletedAt() == null
                && !user.isEmailVerified()) {
            issue(user, EmailVerificationPurpose.REGISTRATION, null, user.getEmail());
        }
    }

    @Transactional
    public ServiceResult<Void> verify(String rawToken) {
        if (rawToken == null || rawToken.isBlank() || rawToken.length() > 128) {
            return invalidToken();
        }
        Instant now = clock.instant();
        Optional<EmailVerificationTokenEntity> optionalToken =
                tokenRepository.findByTokenHashForUpdate(hashToken(rawToken));
        if (optionalToken.isEmpty()) {
            return invalidToken();
        }

        EmailVerificationTokenEntity token = optionalToken.get();
        if (token.getConsumedAt() != null || !token.getExpiresAt().isAfter(now)) {
            return invalidToken();
        }
        Optional<UserEntity> optionalUser = userRepository.findByIdForUpdate(token.getUserId());
        if (optionalUser.isEmpty()
                || !Boolean.TRUE.equals(optionalUser.get().getIsActive())
                || optionalUser.get().getDeletedAt() != null) {
            return invalidToken();
        }

        UserEntity user = optionalUser.get();
        String oldEmail = user.getEmail();
        if (token.getPurpose() == EmailVerificationPurpose.REGISTRATION) {
            user.setEmailVerified(true);
        } else {
            String newEmail = token.getTargetEmail();
            if (newEmail == null
                    || !newEmail.equals(user.getPendingEmail())
                    || userRepository.existsByEmailAndIdNot(newEmail, user.getId())) {
                return invalidToken();
            }
            user.setEmail(newEmail);
            user.setPendingEmail(null);
            user.setEmailVerified(true);
            sessionRevocationService.revokeAllForUser(user.getId(), now);
            emailNotificationService.emailChanged(oldEmail);
        }
        user.setUpdatedAt(now);
        userRepository.saveAndFlush(user);
        token.setConsumedAt(now);
        tokenRepository.save(token);
        return new ServiceResult<>(null, HttpStatus.OK, "Email verified successfully");
    }

    private void issue(
            UserEntity user,
            EmailVerificationPurpose purpose,
            String targetEmail,
            String recipient) {
        if (!emailNotificationService.isEnabled()) {
            throw new IllegalStateException(
                    "Email verification cannot be issued because email delivery is disabled. "
                            + "Configure SMTP and set app.email.enabled=true.");
        }
        String rawToken = newToken();
        tokenRepository.deleteByUserIdAndPurpose(user.getId(), purpose);

        Instant now = clock.instant();
        EmailVerificationTokenEntity token = new EmailVerificationTokenEntity();
        token.setId(UUID.randomUUID());
        token.setUserId(user.getId());
        token.setTokenHash(hashToken(rawToken));
        token.setPurpose(purpose);
        token.setTargetEmail(targetEmail);
        token.setCreatedAt(now);
        token.setExpiresAt(now.plus(settings.verificationTokenTtl()));
        tokenRepository.save(token);

        if (purpose == EmailVerificationPurpose.REGISTRATION) {
            emailNotificationService.registrationVerification(recipient, rawToken);
        } else {
            emailNotificationService.emailChangeVerification(recipient, rawToken);
        }
    }

    private static ServiceResult<Void> invalidToken() {
        return new ServiceResult<>(
                null,
                HttpStatus.BAD_REQUEST,
                "Email verification link is invalid or expired");
    }

    private static String newToken() {
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
}
