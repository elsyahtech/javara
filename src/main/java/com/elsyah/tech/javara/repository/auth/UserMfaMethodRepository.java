package com.elsyah.tech.javara.repository.auth;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import com.elsyah.tech.javara.entity.auth.MfaMethod;
import com.elsyah.tech.javara.entity.auth.UserMfaMethodEntity;

public interface UserMfaMethodRepository extends JpaRepository<UserMfaMethodEntity, UUID> {
    List<UserMfaMethodEntity> findAllByUserIdAndEnabledTrueOrderByCreatedAtAsc(UUID userId);

    Optional<UserMfaMethodEntity> findByUserIdAndMethod(UUID userId, MfaMethod method);

    Optional<UserMfaMethodEntity> findByUserIdAndMethodAndEnabledTrue(
            UUID userId, MfaMethod method);

    void deleteByUserIdAndMethod(UUID userId, MfaMethod method);

    @Transactional
    void deleteByUserId(UUID userId);
}
