package com.elsyah.tech.javara.service.auth;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.security.oauth2.jwt.Jwt;

import com.elsyah.tech.javara.middleware.security.SessionProperties;
import com.elsyah.tech.javara.repository.auth.UserSessionRepository;

@Service
@ConditionalOnProperty(
        prefix = "app.features",
        name = "resource-server",
        havingValue = "true",
        matchIfMissing = true)
public class SessionTokenValidator {
    private final UserSessionRepository userSessionRepository;
    private final SessionProperties sessionProperties;
    private final Clock clock;

    public SessionTokenValidator(
            UserSessionRepository userSessionRepository,
            SessionProperties sessionProperties,
            Clock clock) {
        this.userSessionRepository = userSessionRepository;
        this.sessionProperties = sessionProperties;
        this.clock = clock;
    }

    @Transactional
    public boolean isActive(Jwt token) {
        String rawSessionId = token.getClaimAsString("sid");
        String subject = token.getSubject();
        if (rawSessionId == null || subject == null) {
            return false;
        }
        try {
            UUID sessionId = UUID.fromString(rawSessionId);
            UUID userId = UUID.fromString(subject);
            Instant now = clock.instant();
            return userSessionRepository.touchActiveSession(
                            sessionId,
                            userId,
                            now,
                            sessionProperties.idleTimeoutEnabled(),
                            now.minus(sessionProperties.idleTimeout()))
                    > 0;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
