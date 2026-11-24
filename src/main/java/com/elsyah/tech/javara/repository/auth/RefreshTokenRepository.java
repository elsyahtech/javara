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
import org.springframework.transaction.annotation.Transactional;
import com.elsyah.tech.javara.entity.auth.RefreshTokenEntity;
import jakarta.persistence.LockModeType;

public interface RefreshTokenRepository extends JpaRepository<RefreshTokenEntity, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM RefreshTokenEntity t WHERE t.tokenHash = :tokenHash")
    Optional<RefreshTokenEntity> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    @Query("SELECT t.userId FROM RefreshTokenEntity t WHERE t.tokenHash = :tokenHash")
    Optional<UUID> findUserIdByTokenHash(@Param("tokenHash") String tokenHash);

    List<RefreshTokenEntity> findByUserIdAndRevokedAtIsNull(UUID userId);

    @Transactional
    long deleteByUserId(UUID userId);

    @Query("""
            SELECT COUNT(DISTINCT t.sessionId)
            FROM RefreshTokenEntity t
            WHERE t.userId = :userId
              AND t.revokedAt IS NULL
              AND t.expiresAt > :now
              AND t.sessionExpiresAt > :now
              AND t.lastActivityAt > :idleDeadline
            """)
    long countActiveSessions(
            @Param("userId") UUID userId,
            @Param("now") Instant now,
            @Param("idleDeadline") Instant idleDeadline);

    @Query("""
            SELECT t
            FROM RefreshTokenEntity t
            WHERE t.userId = :userId
              AND t.revokedAt IS NULL
              AND t.expiresAt > :now
              AND t.sessionExpiresAt > :now
              AND t.lastActivityAt > :idleDeadline
            ORDER BY t.sessionStartedAt DESC
            """)
    List<RefreshTokenEntity> findActiveSessions(
            @Param("userId") UUID userId,
            @Param("now") Instant now,
            @Param("idleDeadline") Instant idleDeadline);

    @Modifying
    @Query(value = """
            UPDATE refresh_tokens t
            SET last_activity_at = :now
            FROM users u
            WHERE t.session_id = :sessionId
              AND t.user_id = :userId
              AND t.revoked_at IS NULL
              AND t.expires_at > :now
              AND t.session_expires_at > :now
              AND t.last_activity_at > :idleDeadline
              AND u.id = t.user_id
              AND u.is_active = TRUE
              AND u.deleted_at IS NULL
            """, nativeQuery = true)
    int touchActiveSession(
            @Param("sessionId") UUID sessionId,
            @Param("userId") UUID userId,
            @Param("now") Instant now,
            @Param("idleDeadline") Instant idleDeadline);

    @Modifying
    @Query("""
            UPDATE RefreshTokenEntity t
            SET t.revokedAt = :revokedAt
            WHERE t.sessionId = :sessionId
              AND t.userId = :userId
              AND t.revokedAt IS NULL
            """)
    int revokeActiveTokensForSession(
            @Param("sessionId") UUID sessionId,
            @Param("userId") UUID userId,
            @Param("revokedAt") Instant revokedAt);

    @Modifying
    @Query("""
            UPDATE RefreshTokenEntity t
            SET t.revokedAt = :revokedAt
            WHERE t.userId = :userId AND t.revokedAt IS NULL
            """)
    int revokeActiveTokensForUser(
            @Param("userId") UUID userId,
            @Param("revokedAt") Instant revokedAt);
}
