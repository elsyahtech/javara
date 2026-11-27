package com.elsyah.tech.javara.service.auth;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

import javax.crypto.Mac;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.elsyah.tech.javara.entity.user.UserEntity;
import com.elsyah.tech.javara.middleware.security.JwtProperties;
import com.elsyah.tech.javara.middleware.security.SuspiciousAuthenticationProperties;
import com.elsyah.tech.javara.middleware.security.SuspiciousAuthenticationProperties.Action;
import com.elsyah.tech.javara.repository.user.UserRepository;
import com.elsyah.tech.javara.service.email.EmailNotificationService;

@Service
@ConditionalOnProperty(
        prefix = "app.features",
        name = "auth-api",
        havingValue = "true",
        matchIfMissing = true)
public class SuspiciousAuthenticationService {
    private static final Logger LOGGER =
            LoggerFactory.getLogger(SuspiciousAuthenticationService.class);
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final SuspiciousAuthenticationProperties properties;
    private final UserRepository userRepository;
    private final EmailNotificationService emailNotificationService;
    private final JwtProperties jwtProperties;
    private final Clock clock;

    public SuspiciousAuthenticationService(
            SuspiciousAuthenticationProperties properties,
            UserRepository userRepository,
            EmailNotificationService emailNotificationService,
            JwtProperties jwtProperties,
            Clock clock) {
        this.properties = properties;
        this.userRepository = userRepository;
        this.emailNotificationService = emailNotificationService;
        this.jwtProperties = jwtProperties;
        this.clock = clock;
    }

    public boolean isLocked(UserEntity user) {
        return user.getAccountLockedUntil() != null
                && user.getAccountLockedUntil().isAfter(clock.instant());
    }

    public void recordFailedPassword(UserEntity user) {
        if (!properties.enabled() || !properties.failedLogin().enabled() || isLocked(user)) {
            return;
        }
        SuspiciousAuthenticationProperties.FailureSignal signal = properties.failedLogin();
        Instant now = clock.instant();
        boolean thresholdWasReached = withinWindow(
                user.getLoginFailureWindowStartedAt(), signal.window(), now)
                && user.getFailedLoginAttempts() >= signal.threshold();
        int attempts = nextAttempt(
                user.getFailedLoginAttempts(),
                user.getLoginFailureWindowStartedAt(),
                signal.window(),
                signal.threshold(),
                now);
        user.setFailedLoginAttempts(attempts);
        user.setLoginFailureWindowStartedAt(windowStart(
                user.getLoginFailureWindowStartedAt(), signal.window(), now));
        if (attempts >= signal.threshold() && !thresholdWasReached) {
            LOGGER.warn("Suspicious authentication event=FAILED_PASSWORD userId={}",
                    user.getId());
            applyEventAction(
                    user,
                    "Repeated unsuccessful sign-in attempts were detected.",
                    signal.action(),
                    signal.lockDuration());
            if (locks(signal.action())) {
                resetPasswordFailures(user);
            }
        }
        userRepository.save(user);
    }

    public void recordFailedMfa(UUID userId) {
        if (!properties.enabled() || !properties.failedMfa().enabled()) {
            return;
        }
        Optional<UserEntity> optionalUser = userRepository.findByIdForUpdate(userId);
        if (optionalUser.isEmpty()) {
            return;
        }
        UserEntity user = optionalUser.get();
        if (isLocked(user)) {
            return;
        }
        SuspiciousAuthenticationProperties.FailureSignal signal = properties.failedMfa();
        Instant now = clock.instant();
        boolean thresholdWasReached = withinWindow(
                user.getMfaFailureWindowStartedAt(), signal.window(), now)
                && user.getFailedMfaAttempts() >= signal.threshold();
        int attempts = nextAttempt(
                user.getFailedMfaAttempts(),
                user.getMfaFailureWindowStartedAt(),
                signal.window(),
                signal.threshold(),
                now);
        user.setFailedMfaAttempts(attempts);
        user.setMfaFailureWindowStartedAt(windowStart(
                user.getMfaFailureWindowStartedAt(), signal.window(), now));
        if (attempts >= signal.threshold() && !thresholdWasReached) {
            LOGGER.warn("Suspicious authentication event=FAILED_MFA userId={}",
                    user.getId());
            applyEventAction(
                    user,
                    "Repeated unsuccessful multi-factor verification attempts were detected.",
                    signal.action(),
                    signal.lockDuration());
            if (locks(signal.action())) {
                resetMfaFailures(user);
            }
        }
        userRepository.save(user);
    }

    public void resetPasswordFailures(UserEntity user) {
        user.setFailedLoginAttempts(0);
        user.setLoginFailureWindowStartedAt(null);
    }

    public void resetMfaFailures(UserEntity user) {
        user.setFailedMfaAttempts(0);
        user.setMfaFailureWindowStartedAt(null);
    }

    public boolean recordSuccessfulSource(UserEntity user, String remoteAddress) {
        if (!properties.enabled()
                || !properties.newSource().enabled()
                || remoteAddress == null
                || remoteAddress.isBlank()
                || remoteAddress.length() > 64) {
            return true;
        }

        String sourceHash = hashSource(remoteAddress);
        String previousSourceHash = user.getLastSuccessfulSourceHash();
        user.setLastSuccessfulSourceHash(sourceHash);
        if (previousSourceHash != null && !previousSourceHash.equals(sourceHash)) {
            LOGGER.warn("Suspicious authentication event=NEW_SOURCE userId={}", user.getId());
            applyEventAction(
                    user,
                    "A successful sign-in was detected from a new network address.",
                    properties.newSource().action(),
                    properties.newSource().lockDuration());
        }
        userRepository.save(user);
        return !isLocked(user);
    }

    public void securityChange(UserEntity user, String change) {
        if (!properties.enabled() || !properties.securityChange().enabled()) {
            return;
        }
        Action action = properties.securityChange().action();
        LOGGER.info("Authentication security event=SECURITY_CHANGE userId={} change={}",
                user.getId(), change);
        if (action == Action.TEMPORARY_LOCK || action == Action.LOCK_AND_NOTIFY) {
            user.setAccountLockedUntil(clock.instant().plus(
                    properties.securityChange().lockDuration()));
        }
        if ((action == Action.NOTIFY || action == Action.LOCK_AND_NOTIFY)
                && (!"password was changed".equals(change)
                        || action == Action.LOCK_AND_NOTIFY)) {
            String notification = change;
            if (locks(action)) {
                notification += "; sign-in is temporarily locked until "
                        + user.getAccountLockedUntil();
            }
            emailNotificationService.securitySettingsChanged(
                    user.getEmail(), notification);
        }
        userRepository.save(user);
    }

    private void applyEventAction(
            UserEntity user, String detail, Action action, Duration lockDuration) {
        Instant now = clock.instant();
        boolean lock = action == Action.TEMPORARY_LOCK || action == Action.LOCK_AND_NOTIFY;
        if (lock) {
            user.setAccountLockedUntil(now.plus(lockDuration));
        }
        if (action == Action.LOCK_AND_NOTIFY) {
            emailNotificationService.suspiciousAuthentication(
                    user.getEmail(),
                    detail + " Sign-in is temporarily locked until "
                            + user.getAccountLockedUntil() + ".");
        } else if (action == Action.NOTIFY) {
            emailNotificationService.suspiciousAuthentication(user.getEmail(), detail);
        }
    }

    private String hashSource(String source) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(jwtProperties.secretKey());
            mac.update("javara-auth-source:".getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(mac.doFinal(source.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException(
                    "Unable to fingerprint the authentication source", exception);
        }
    }

    private static int nextAttempt(
            int currentAttempts,
            Instant windowStartedAt,
            Duration window,
            int threshold,
            Instant now) {
        if (windowStartedAt == null || !windowStartedAt.plus(window).isAfter(now)) {
            return 1;
        }
        return currentAttempts >= threshold ? threshold : currentAttempts + 1;
    }

    private static boolean withinWindow(
            Instant windowStartedAt, Duration window, Instant now) {
        return windowStartedAt != null && windowStartedAt.plus(window).isAfter(now);
    }

    private static Instant windowStart(
            Instant currentStart, Duration window, Instant now) {
        return currentStart == null || !currentStart.plus(window).isAfter(now)
                ? now
                : currentStart;
    }

    private static boolean locks(Action action) {
        return action == Action.TEMPORARY_LOCK || action == Action.LOCK_AND_NOTIFY;
    }
}
