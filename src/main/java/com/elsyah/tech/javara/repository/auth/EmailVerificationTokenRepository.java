package com.elsyah.tech.javara.repository.auth;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.elsyah.tech.javara.entity.auth.EmailVerificationPurpose;
import com.elsyah.tech.javara.entity.auth.EmailVerificationTokenEntity;

import jakarta.persistence.LockModeType;

public interface EmailVerificationTokenRepository
        extends JpaRepository<EmailVerificationTokenEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT token
            FROM EmailVerificationTokenEntity token
            WHERE token.tokenHash = :tokenHash
            """)
    Optional<EmailVerificationTokenEntity> findByTokenHashForUpdate(
            @Param("tokenHash") String tokenHash);

    @Modifying
    @Transactional
    @Query("""
            DELETE FROM EmailVerificationTokenEntity token
            WHERE token.userId = :userId AND token.purpose = :purpose
            """)
    int deleteByUserIdAndPurpose(
            @Param("userId") UUID userId,
            @Param("purpose") EmailVerificationPurpose purpose);

    @Modifying
    @Transactional
    @Query("DELETE FROM EmailVerificationTokenEntity token WHERE token.userId = :userId")
    int deleteByUserId(@Param("userId") UUID userId);
}
