package com.elsyah.tech.javara.projection.user;

import java.time.Instant;
import java.util.UUID;

/**
 * UserSelectColumn is a projection interface for selecting specific columns from the UserEntity.
 * It defines getter methods for the selected columns, allowing for efficient retrieval of user data.
 * This interface is used in conjunction with Spring Data JPA to fetch only the necessary fields from the database.
 * @author Elmansyah
 * @version 1.0.0
 * @since 2026-11-18
 * @contact https://www.linkedin.com/in/elmansyah
 */
public interface UserSelectColumn {
    UUID getId();
    String getEmail();
    Boolean getIsActive();
    Instant getCreatedAt();
    Instant getUpdatedAt();
    Instant getDeletedAt();
}
