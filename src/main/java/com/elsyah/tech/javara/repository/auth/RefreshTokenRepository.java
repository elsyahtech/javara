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
    @Query("""
            SELECT token
            FROM RefreshTokenEntity token
            JOIN FETCH token.session
            WHERE token.session.userId = :userId
              AND token.revokedAt IS NULL
            """)
    List<RefreshTokenEntity> findByUserIdAndRevokedAtIsNull(@Param("userId") UUID userId);

    @Modifying
    @Transactional
    @Query("DELETE FROM RefreshTokenEntity t WHERE t.session.userId = :userId")
    int deleteByUserId(@Param("userId") UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM RefreshTokenEntity t WHERE t.tokenHash = :tokenHash")
    Optional<RefreshTokenEntity> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    @Query("SELECT t.session.userId FROM RefreshTokenEntity t WHERE t.tokenHash = :tokenHash")
    Optional<UUID> findUserIdByTokenHash(@Param("tokenHash") String tokenHash);

    @Modifying
    @Query("""
            UPDATE RefreshTokenEntity t
            SET t.revokedAt = :revokedAt
            WHERE t.session.id = :sessionId
              AND t.session.userId = :userId
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
            WHERE t.session.userId = :userId AND t.revokedAt IS NULL
            """)
    int revokeActiveTokensForUser(
            @Param("userId") UUID userId,
            @Param("revokedAt") Instant revokedAt);
}
