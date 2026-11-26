package com.elsyah.tech.javara.repository.auth;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.elsyah.tech.javara.entity.auth.PasswordResetTokenEntity;

import jakarta.persistence.LockModeType;

public interface PasswordResetTokenRepository
        extends JpaRepository<PasswordResetTokenEntity, UUID> {
    Optional<PasswordResetTokenEntity> findByTokenHash(String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT token
            FROM PasswordResetTokenEntity token
            WHERE token.tokenHash = :tokenHash
            """)
    Optional<PasswordResetTokenEntity> findByTokenHashForUpdate(
            @Param("tokenHash") String tokenHash);

    @Modifying
    @Transactional
    @Query("DELETE FROM PasswordResetTokenEntity token WHERE token.userId = :userId")
    int deleteByUserId(@Param("userId") UUID userId);
}
