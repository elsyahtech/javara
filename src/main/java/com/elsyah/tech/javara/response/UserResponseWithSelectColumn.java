package com.elsyah.tech.javara.response;

import java.time.LocalDateTime;

import com.elsyah.tech.javara.projection.UserSelectColumn;

/**
 * UserResponseWithSelectColumn is a record that represents the response structure for user-related data
 * with selected columns. It contains fields for user ID, email, active status, and timestamps for creation,
 * update, and deletion. This class provides a static method to convert a UserSelectColumn projection into
 * a UserResponseWithSelectColumn.
 * 
 * @author Elmansyah
 * @version 1.0.0
 * @since 2026-11-18
 * @contact https://www.linkedin.com/in/elmansyah
 */
public record UserResponseWithSelectColumn(
    String id,
    String email,
    boolean isActive,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    LocalDateTime deletedAt
) {
    public static UserResponseWithSelectColumn from(UserSelectColumn user) {
        return new UserResponseWithSelectColumn(
            user.getId(),
            user.getEmail(),
            user.getIsActive(),
            user.getCreatedAt(),
            user.getUpdatedAt(),
            user.getDeletedAt()
        );
    }
}