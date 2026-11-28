package com.elsyah.tech.javara.service.auth;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.elsyah.tech.javara.repository.auth.RefreshTokenRepository;
import com.elsyah.tech.javara.repository.auth.UserSessionRepository;

@Service
public class SessionRevocationService {
    private final UserSessionRepository userSessionRepository;
    private final RefreshTokenRepository refreshTokenRepository;

    public SessionRevocationService(
            UserSessionRepository userSessionRepository,
            RefreshTokenRepository refreshTokenRepository) {
        this.userSessionRepository = userSessionRepository;
        this.refreshTokenRepository = refreshTokenRepository;
    }

    @Transactional
    public int revokeSession(UUID sessionId, UUID userId, Instant revokedAt) {
        int revokedSessions =
                userSessionRepository.revokeActiveSession(sessionId, userId, revokedAt);
        refreshTokenRepository.revokeActiveTokensForSession(sessionId, userId, revokedAt);
        return revokedSessions;
    }

    @Transactional
    public int revokeAllForUser(UUID userId, Instant revokedAt) {
        int revokedSessions = userSessionRepository.revokeActiveSessionsForUser(userId, revokedAt);
        refreshTokenRepository.revokeActiveTokensForUser(userId, revokedAt);
        return revokedSessions;
    }
}
