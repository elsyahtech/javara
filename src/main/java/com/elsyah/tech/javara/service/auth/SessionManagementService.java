package com.elsyah.tech.javara.service.auth;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.elsyah.tech.javara.entity.auth.UserSessionEntity;
import com.elsyah.tech.javara.middleware.security.SessionProperties;
import com.elsyah.tech.javara.repository.auth.UserSessionRepository;
import com.elsyah.tech.javara.repository.user.UserRepository;
import com.elsyah.tech.javara.response.auth.SessionResponse;
import com.elsyah.tech.javara.service.common.ServiceResult;
import com.elsyah.tech.javara.utils.security.PasswordHashService;

@Service
@ConditionalOnProperty(
        prefix = "app.features",
        name = "auth-api",
        havingValue = "true",
        matchIfMissing = true)
public class SessionManagementService {
    private static final Logger LOGGER =
            LoggerFactory.getLogger(SessionManagementService.class);

    private final UserSessionRepository userSessionRepository;
    private final SessionRevocationService sessionRevocationService;
    private final UserRepository userRepository;
    private final PasswordHashService passwordHashService;
    private final SessionProperties sessionProperties;
    private final Clock clock;

    public SessionManagementService(
            UserSessionRepository userSessionRepository,
            SessionRevocationService sessionRevocationService,
            UserRepository userRepository,
            PasswordHashService passwordHashService,
            SessionProperties sessionProperties,
            Clock clock) {
        this.userSessionRepository = userSessionRepository;
        this.sessionRevocationService = sessionRevocationService;
        this.userRepository = userRepository;
        this.passwordHashService = passwordHashService;
        this.sessionProperties = sessionProperties;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<SessionResponse> listSessions(UUID userId, UUID currentSessionId) {
        Instant now = clock.instant();
        return userSessionRepository.findActiveSessions(
                        userId,
                        now,
                        sessionProperties.idleTimeoutEnabled(),
                        now.minus(sessionProperties.idleTimeout()))
                .stream()
                .map(token -> toResponse(token, currentSessionId))
                .toList();
    }

    @Transactional
    public ServiceResult<Integer> terminateSession(
            UUID userId,
            UUID sessionId,
            String currentPassword) {
        if (!reauthenticate(userId, currentPassword)) {
            return reauthenticationFailed();
        }

        int revokedSessions =
                sessionRevocationService.revokeSession(sessionId, userId, clock.instant());
        if (revokedSessions == 0) {
            return new ServiceResult<>(
                    null, HttpStatus.NOT_FOUND, "Active session was not found");
        }
        return new ServiceResult<>(1, HttpStatus.OK, "Session terminated successfully");
    }

    @Transactional
    public ServiceResult<Integer> terminateAllSessions(
            UUID userId,
            String currentPassword) {
        if (!reauthenticate(userId, currentPassword)) {
            return reauthenticationFailed();
        }

        int revokedSessions = sessionRevocationService.revokeAllForUser(userId, clock.instant());
        return new ServiceResult<>(
                revokedSessions, HttpStatus.OK, "Sessions terminated successfully");
    }

    @Transactional
    public ServiceResult<Integer> terminateUserSessions(
            UUID administratorId,
            UUID targetUserId,
            String currentPassword) {
        if (!reauthenticate(administratorId, currentPassword)) {
            return reauthenticationFailed();
        }
        if (userRepository.findByIdForUpdate(targetUserId).isEmpty()) {
            return new ServiceResult<>(
                    null, HttpStatus.NOT_FOUND, "User was not found");
        }

        int revokedSessions =
                sessionRevocationService.revokeAllForUser(targetUserId, clock.instant());
        LOGGER.info(
                "Administrator {} terminated {} sessions for user {}",
                administratorId,
                revokedSessions,
                targetUserId);
        return new ServiceResult<>(
                revokedSessions, HttpStatus.OK, "User sessions terminated successfully");
    }

    private boolean reauthenticate(UUID userId, String currentPassword) {
        return currentPassword != null
                && userRepository.findByIdForUpdate(userId)
                        .filter(user -> Boolean.TRUE.equals(user.getIsActive()))
                        .filter(user -> passwordHashService.matches(
                                currentPassword, user.getPassword()))
                        .isPresent();
    }

    private static ServiceResult<Integer> reauthenticationFailed() {
        return new ServiceResult<>(
                null, HttpStatus.UNAUTHORIZED, "Re-authentication failed");
    }

    private static SessionResponse toResponse(
            UserSessionEntity session,
            UUID currentSessionId) {
        return new SessionResponse(
                session.getId(),
                session.getStartedAt(),
                session.getLastActivityAt(),
                session.getExpiresAt(),
                session.getId().equals(currentSessionId));
    }
}
