package com.elsyah.tech.javara.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.elsyah.tech.javara.entity.RefreshTokenEntity;
import jakarta.persistence.LockModeType;

public interface RefreshTokenRepository extends JpaRepository<RefreshTokenEntity, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM RefreshTokenEntity t WHERE t.tokenHash = :tokenHash")
    Optional<RefreshTokenEntity> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    @Query("SELECT t.userId FROM RefreshTokenEntity t WHERE t.tokenHash = :tokenHash")
    Optional<String> findUserIdByTokenHash(@Param("tokenHash") String tokenHash);

    List<RefreshTokenEntity> findByUserIdAndRevokedAtIsNull(String userId);

    @Modifying
    @Query("""
            UPDATE RefreshTokenEntity t
            SET t.revokedAt = :revokedAt
            WHERE t.userId = :userId AND t.revokedAt IS NULL
            """)
    int revokeActiveTokensForUser(
            @Param("userId") String userId,
            @Param("revokedAt") LocalDateTime revokedAt);
}
