package com.elsyah.tech.javara.repository.auth;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.elsyah.tech.javara.entity.auth.MfaChallengeEntity;

import jakarta.persistence.LockModeType;

public interface MfaChallengeRepository extends JpaRepository<MfaChallengeEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM MfaChallengeEntity c WHERE c.id = :id")
    Optional<MfaChallengeEntity> findByIdForUpdate(@Param("id") UUID id);

    @Modifying
    @Transactional
    @Query("DELETE FROM MfaChallengeEntity c WHERE c.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") Instant cutoff);

    @Modifying
    @Transactional
    int deleteByUserId(UUID userId);
}
