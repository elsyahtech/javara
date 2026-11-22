package com.elsyah.tech.javara.unit.response;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import com.elsyah.tech.javara.entity.UserEntity;
import com.elsyah.tech.javara.projection.UserSelectColumn;
import com.elsyah.tech.javara.response.UserResponse;
import com.elsyah.tech.javara.response.UserResponseWithSelectColumn;

class UserResponseTest {

    // Verifies that a user entity is mapped to the public response record.
    @Test
    void shouldMapUserEntityToUserResponse() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 11, 20, 10, 0);
        LocalDateTime updatedAt = createdAt.plusHours(1);

        UserEntity user = new UserEntity();
        user.setId("user-1");
        user.setEmail("user@example.com");
        user.setPassword("secret123");
        user.setIsActive(true);
        user.setUpdatedAt(updatedAt);

        UserResponse response = UserResponse.from(user);

        assertThat(response.id()).isEqualTo("user-1");
        assertThat(response.email()).isEqualTo("user@example.com");
        assertThat(response.isActive()).isTrue();
        assertThat(response.updatedAt()).isEqualTo(updatedAt);
        assertThat(response.deletedAt()).isNull();
    }

    // Verifies that a selected-column projection is mapped to the public response record.
    @Test
    void shouldMapUserProjectionToUserResponse() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 11, 20, 10, 0);
        UserSelectColumn projection = new UserSelectColumn() {
            public String getId() { return "user-2"; }
            public String getEmail() { return "projection@example.com"; }
            public String getPassword() { return "secret123"; }
            public Boolean getIsActive() { return false; }
            public LocalDateTime getCreatedAt() { return createdAt; }
            public LocalDateTime getUpdatedAt() { return null; }
            public LocalDateTime getDeletedAt() { return null; }
        };

        UserResponseWithSelectColumn response =
                UserResponseWithSelectColumn.from(projection);

        assertThat(response.id()).isEqualTo("user-2");
        assertThat(response.email()).isEqualTo("projection@example.com");
        assertThat(response.isActive()).isFalse();
        assertThat(response.createdAt()).isEqualTo(createdAt);
    }
}
