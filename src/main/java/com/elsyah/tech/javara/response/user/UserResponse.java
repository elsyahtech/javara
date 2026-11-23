package com.elsyah.tech.javara.response.user;

import java.time.Instant;
import java.util.UUID;

import com.elsyah.tech.javara.entity.user.UserEntity;
import com.elsyah.tech.javara.entity.user.UserRole;

/**
 * UserResponse is a record that represents the response structure for user-related data.
 * It contains fields for user ID, email, active status, and timestamps for creation, update, and deletion.
 * This class provides a static method to convert a UserEntity into a UserResponse.
 * @author Elmansyah
 * @version 1.0.0
 * @since 2026-11-18
 * @contact https://www.linkedin.com/in/elmansyah
 */
public record UserResponse(
    UUID id,
    String email,
    UserRole role,
    Boolean isActive,
    Instant createdAt,
    Instant updatedAt,
    Instant deletedAt
) {
    public static UserResponse from(UserEntity userEntity) {
        return new UserResponse(
            userEntity.getId(),
            userEntity.getEmail(),
            userEntity.getRole(),
            userEntity.getIsActive(),
            userEntity.getCreatedAt(),
            userEntity.getUpdatedAt(),
            userEntity.getDeletedAt()
        );
    }
}