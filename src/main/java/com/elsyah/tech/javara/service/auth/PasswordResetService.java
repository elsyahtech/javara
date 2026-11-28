package com.elsyah.tech.javara.service.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.elsyah.tech.javara.entity.auth.AdminPasswordResetAuditEntity;
import com.elsyah.tech.javara.entity.auth.PasswordResetTokenEntity;
import com.elsyah.tech.javara.entity.user.UserEntity;
import com.elsyah.tech.javara.repository.auth.AdminPasswordResetAuditRepository;
import com.elsyah.tech.javara.repository.auth.PasswordResetTokenRepository;
import com.elsyah.tech.javara.repository.user.UserRepository;
import com.elsyah.tech.javara.service.auth.SessionRevocationService;
import com.elsyah.tech.javara.service.common.ServiceResult;
import com.elsyah.tech.javara.service.email.EmailNotificationService;
import com.elsyah.tech.javara.service.user.PasswordPolicyService;
import com.elsyah.tech.javara.utils.security.PasswordHashService;

@Service
@ConditionalOnProperty(
        prefix = "app.features",
        name = "auth-api",
        havingValue = "true",
        matchIfMissing = true)
public class PasswordResetService {
    private static final Logger LOGGER = LoggerFactory.getLogger(PasswordResetService.class);
    private static final Duration TOKEN_TTL = Duration.ofHours(1);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final AdminPasswordResetAuditRepository auditRepository;
    private final SessionRevocationService sessionRevocationService;
    private final PasswordHashService passwordHashService;
    private final PasswordPolicyService passwordPolicyService;
    private final EmailNotificationService emailNotificationService;
    private final SuspiciousAuthenticationService suspiciousAuthenticationService;
    private final Clock clock;

    public PasswordResetService(
            UserRepository userRepository,
            PasswordResetTokenRepository tokenRepository,
            AdminPasswordResetAuditRepository auditRepository,
            SessionRevocationService sessionRevocationService,
            PasswordHashService passwordHashService,
            PasswordPolicyService passwordPolicyService,
            EmailNotificationService emailNotificationService,
            SuspiciousAuthenticationService suspiciousAuthenticationService,
            Clock clock) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.auditRepository = auditRepository;
        this.sessionRevocationService = sessionRevocationService;
        this.passwordHashService = passwordHashService;
        this.passwordPolicyService = passwordPolicyService;
        this.emailNotificationService = emailNotificationService;
        this.suspiciousAuthenticationService = suspiciousAuthenticationService;
        this.clock = clock;
    }

    @Transactional
    public ServiceResult<Void> requestReset(String email) {
        if (!emailNotificationService.isEnabled()) {
            LOGGER.warn("Password reset request was not processed because email delivery is disabled");
            return acceptedRequest();
        }

        Optional<UserEntity> optionalUser = userRepository.findByEmailForUpdate(email);
        if (optionalUser.isPresent()) {
            UserEntity user = optionalUser.get();
            if (Boolean.TRUE.equals(user.getIsActive())
                    && user.isEmailVerified()
                    && !user.isPasswordResetSupportRequired()) {
                String rawToken = issueToken(user.getId(), clock.instant());
                emailNotificationService.passwordResetLink(
                        user.getEmail(), rawToken, false, TOKEN_TTL);
            }
        }
        return acceptedRequest();
    }

    @Transactional
    public ServiceResult<Void> forceEmergencyReset(
            UUID actorUserId,
            UUID targetUserId,
            String currentPassword,
            String reason,
            String notificationMessage) {
        ServiceResult<LockedUsers> locked = lockAdminAndTarget(
                actorUserId, targetUserId, currentPassword);
        if (locked.isError()) {
            return failure(locked.status(), locked.message());
        }

        UserEntity target = locked.data().target();
        if (!Boolean.TRUE.equals(target.getIsActive())) {
            return failure(HttpStatus.CONFLICT, "Cannot reset password for an inactive user");
        }
        Instant now = clock.instant();
        target.getCompromisedPasswordHashes().add(target.getPassword());
        target.setPassword(passwordHashService.encode(newSecret()));
        target.setPasswordResetRequired(true);
        target.setPasswordResetSupportRequired(true);
        target.setUpdatedAt(now);
        userRepository.saveAndFlush(target);
        sessionRevocationService.revokeAllForUser(targetUserId, now);
        tokenRepository.deleteByUserId(targetUserId);
        audit(
                actorUserId,
                targetUserId,
                "EMERGENCY_RANDOMIZED",
                reason,
                now);
        String message =
                "Emergency credential reset applied; sessions revoked and account locked. "
                        + "Verify notification delivery.";
        if (target.isEmailVerified()) {
            emailNotificationService.emergencyPasswordResetNotice(
                    target.getEmail(), notificationMessage);
        } else {
            LOGGER.warn(
                    "Emergency credential reset applied without email notice because the "
                            + "target address is unverified");
            message = "Emergency credential reset applied; sessions revoked and account locked. "
                    + "No verified email address is available; contact the user through a "
                    + "trusted support channel.";
        }
        return new ServiceResult<>(
                null,
                HttpStatus.ACCEPTED,
                message);
    }

    @Transactional
    public ServiceResult<Void> authorizeSupportRecovery(
            UUID actorUserId,
            UUID targetUserId,
            String currentPassword,
            String supportCaseReference) {
        ServiceResult<LockedUsers> locked = lockAdminAndTarget(
                actorUserId, targetUserId, currentPassword);
        if (locked.isError()) {
            return failure(locked.status(), locked.message());
        }

        UserEntity target = locked.data().target();
        if (!Boolean.TRUE.equals(target.getIsActive())
                || !target.isEmailVerified()
                || !target.isPasswordResetRequired()
                || !target.isPasswordResetSupportRequired()) {
            return failure(HttpStatus.CONFLICT,
                    "The account is not awaiting support-authorized recovery");
        }
        if (!emailNotificationService.isEnabled()) {
            return failure(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Recovery email delivery is unavailable; the account remains locked");
        }

        Instant now = clock.instant();
        String rawToken = issueToken(targetUserId, now);
        audit(
                actorUserId,
                targetUserId,
                "SUPPORT_AUTHORIZED",
                supportCaseReference,
                now);
        emailNotificationService.passwordResetLink(
                target.getEmail(), rawToken, true, TOKEN_TTL);
        return new ServiceResult<>(
                null,
                HttpStatus.ACCEPTED,
                "A one-time recovery link was sent to the verified email address");
    }

    @Transactional
    public ServiceResult<Void> completeReset(String rawToken, String newPassword) {
        if (rawToken == null || !rawToken.matches("[A-Za-z0-9_-]{43}")) {
            return invalidToken();
        }
        String tokenHash = hash(rawToken);
        Optional<PasswordResetTokenEntity> unlockedToken =
                tokenRepository.findByTokenHash(tokenHash);
        if (unlockedToken.isEmpty()) {
            return invalidToken();
        }
        UUID userId = unlockedToken.get().getUserId();
        Optional<UserEntity> lockedUser = userRepository.findByIdForUpdate(userId);
        if (lockedUser.isEmpty()) {
            return invalidToken();
        }
        Optional<PasswordResetTokenEntity> lockedToken =
                tokenRepository.findByTokenHashForUpdate(tokenHash);
        if (lockedToken.isEmpty()) {
            return invalidToken();
        }

        Instant now = clock.instant();
        PasswordResetTokenEntity token = lockedToken.get();
        UserEntity user = lockedUser.get();
        if (!token.getUserId().equals(userId)
                || token.getConsumedAt() != null
                || !token.getExpiresAt().isAfter(now)
                || !Boolean.TRUE.equals(user.getIsActive())
                || !user.isEmailVerified()) {
            return invalidToken();
        }
        if (newPassword == null || newPassword.length() < 8 || newPassword.length() > 72) {
            return failure(HttpStatus.BAD_REQUEST, "Password must be 8-72 characters");
        }
        if (passwordHashService.matches(newPassword, user.getPassword())) {
            return failure(HttpStatus.BAD_REQUEST, "New password must be different");
        }
        if (passwordHashService.matchesAny(
                newPassword, user.getCompromisedPasswordHashes())) {
            return failure(
                    HttpStatus.BAD_REQUEST,
                    "This password was previously invalidated during an emergency reset. "
                            + "Choose a different password that you have not used before.");
        }
        if (!passwordPolicyService.isPasswordAllowed(newPassword)) {
            return failure(HttpStatus.BAD_REQUEST,
                    "Password does not meet the security policy");
        }

        user.setPassword(passwordHashService.encode(newPassword));
        user.setPasswordResetRequired(false);
        user.setPasswordResetSupportRequired(false);
        user.setUpdatedAt(now);
        userRepository.saveAndFlush(user);
        sessionRevocationService.revokeAllForUser(userId, now);
        token.setConsumedAt(now);
        tokenRepository.save(token);
        audit(userId, userId, "RECOVERY_COMPLETED", null, now);
        emailNotificationService.passwordChanged(user.getEmail());
        suspiciousAuthenticationService.securityChange(user, "password was changed");
        return new ServiceResult<>(null, HttpStatus.OK, "Password reset successfully");
    }

    private ServiceResult<LockedUsers> lockAdminAndTarget(
            UUID actorUserId,
            UUID targetUserId,
            String currentPassword) {
        List<UUID> lockOrder = actorUserId.equals(targetUserId)
                ? List.of(actorUserId)
                : List.of(actorUserId, targetUserId).stream().sorted().toList();
        UserEntity actor = null;
        UserEntity target = null;
        for (UUID id : lockOrder) {
            Optional<UserEntity> locked = userRepository.findByIdForUpdate(id);
            if (locked.isEmpty()) {
                if (id.equals(actorUserId)) {
                    return new ServiceResult<>(
                            null, HttpStatus.UNAUTHORIZED, "Authentication is required");
                }
                return new ServiceResult<>(null, HttpStatus.NOT_FOUND, "User not found");
            }
            if (id.equals(actorUserId)) {
                actor = locked.get();
            }
            if (id.equals(targetUserId)) {
                target = locked.get();
            }
        }
        if (!Boolean.TRUE.equals(actor.getIsActive())
                || !userRepository.hasPermission(actor.getId(), "password-resets:manage")
                || !passwordHashService.matches(currentPassword, actor.getPassword())) {
            return new ServiceResult<>(
                    null, HttpStatus.UNAUTHORIZED, "Authentication is required");
        }
        return new ServiceResult<>(
                new LockedUsers(actor, target), HttpStatus.OK, "Administrator verified");
    }

    private String issueToken(UUID userId, Instant now) {
        String rawToken = newToken();
        tokenRepository.deleteByUserId(userId);
        PasswordResetTokenEntity token = new PasswordResetTokenEntity();
        token.setId(UUID.randomUUID());
        token.setUserId(userId);
        token.setTokenHash(hash(rawToken));
        token.setCreatedAt(now);
        token.setExpiresAt(now.plus(TOKEN_TTL));
        tokenRepository.save(token);
        return rawToken;
    }

    private void audit(
            UUID actorUserId,
            UUID targetUserId,
            String action,
            String reason,
            Instant now) {
        AdminPasswordResetAuditEntity event = new AdminPasswordResetAuditEntity();
        event.setId(UUID.randomUUID());
        event.setActorUserId(actorUserId);
        event.setTargetUserId(targetUserId);
        event.setAction(action);
        event.setReason(reason);
        event.setCreatedAt(now);
        auditRepository.save(event);
    }

    private static ServiceResult<Void> acceptedRequest() {
        return new ServiceResult<>(
                null,
                HttpStatus.ACCEPTED,
                "If the account can be recovered, instructions will be sent");
    }

    private static String newToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String newSecret() {
        return newToken();
    }

    private static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static ServiceResult<Void> invalidToken() {
        return failure(HttpStatus.BAD_REQUEST, "Invalid or expired password reset link");
    }

    private static ServiceResult<Void> failure(HttpStatus status, String message) {
        return new ServiceResult<>(null, status, message);
    }

    private record LockedUsers(UserEntity actor, UserEntity target) {}
}
