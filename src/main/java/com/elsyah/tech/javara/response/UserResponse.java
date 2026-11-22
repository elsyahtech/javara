package com.elsyah.tech.javara.response;

import java.time.LocalDateTime;

import com.elsyah.tech.javara.entity.UserEntity;
import com.elsyah.tech.javara.entity.UserRole;

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
    String id,
    String email,
    UserRole role,
    Boolean isActive,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    LocalDateTime deletedAt
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