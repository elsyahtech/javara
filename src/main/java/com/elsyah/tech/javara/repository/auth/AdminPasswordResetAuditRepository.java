package com.elsyah.tech.javara.repository.auth;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

import com.elsyah.tech.javara.entity.auth.AdminPasswordResetAuditEntity;

public interface AdminPasswordResetAuditRepository
        extends JpaRepository<AdminPasswordResetAuditEntity, UUID> {
    @Modifying
    @Transactional
    void deleteByActorUserId(UUID actorUserId);

    @Modifying
    @Transactional
    void deleteByTargetUserId(UUID targetUserId);
}
