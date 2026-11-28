package com.elsyah.tech.javara.repository.auth;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.elsyah.tech.javara.entity.auth.UserSessionEntity;

import jakarta.persistence.LockModeType;

public interface UserSessionRepository extends JpaRepository<UserSessionEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT session
            FROM UserSessionEntity session
            WHERE session.id = :sessionId AND session.userId = :userId
            """)
    Optional<UserSessionEntity> findByIdAndUserIdForUpdate(
            @Param("sessionId") UUID sessionId,
            @Param("userId") UUID userId);

    @Query("""
            SELECT COUNT(session)
            FROM UserSessionEntity session
            WHERE session.userId = :userId
              AND session.revokedAt IS NULL
              AND session.expiresAt > :now
              AND (:enforceIdleTimeout = FALSE OR session.lastActivityAt > :idleDeadline)
            """)
    long countActiveSessions(
            @Param("userId") UUID userId,
            @Param("now") Instant now,
            @Param("enforceIdleTimeout") boolean enforceIdleTimeout,
            @Param("idleDeadline") Instant idleDeadline);

    @Query("""
            SELECT session
            FROM UserSessionEntity session
            WHERE session.userId = :userId
              AND session.revokedAt IS NULL
              AND session.expiresAt > :now
              AND (:enforceIdleTimeout = FALSE OR session.lastActivityAt > :idleDeadline)
            ORDER BY session.startedAt DESC
            """)
    List<UserSessionEntity> findActiveSessions(
            @Param("userId") UUID userId,
            @Param("now") Instant now,
            @Param("enforceIdleTimeout") boolean enforceIdleTimeout,
            @Param("idleDeadline") Instant idleDeadline);

    @Modifying
    @Query("""
            UPDATE UserSessionEntity session
            SET session.lastActivityAt = :now
            WHERE session.id = :sessionId
              AND session.userId = :userId
              AND session.revokedAt IS NULL
              AND session.expiresAt > :now
              AND (:enforceIdleTimeout = FALSE OR session.lastActivityAt > :idleDeadline)
              AND EXISTS (
                  SELECT user.id
                  FROM UserEntity user
                  WHERE user.id = session.userId
                    AND user.isActive = TRUE
                    AND user.deletedAt IS NULL
              )
            """)
    int touchActiveSession(
            @Param("sessionId") UUID sessionId,
            @Param("userId") UUID userId,
            @Param("now") Instant now,
            @Param("enforceIdleTimeout") boolean enforceIdleTimeout,
            @Param("idleDeadline") Instant idleDeadline);

    @Modifying
    @Query("""
            UPDATE UserSessionEntity session
            SET session.revokedAt = :revokedAt
            WHERE session.id = :sessionId
              AND session.userId = :userId
              AND session.revokedAt IS NULL
            """)
    int revokeActiveSession(
            @Param("sessionId") UUID sessionId,
            @Param("userId") UUID userId,
            @Param("revokedAt") Instant revokedAt);

    @Modifying
    @Query("""
            UPDATE UserSessionEntity session
            SET session.revokedAt = :revokedAt
            WHERE session.userId = :userId AND session.revokedAt IS NULL
            """)
    int revokeActiveSessionsForUser(
            @Param("userId") UUID userId,
            @Param("revokedAt") Instant revokedAt);
}
