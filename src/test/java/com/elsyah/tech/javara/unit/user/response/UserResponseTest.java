package com.elsyah.tech.javara.unit.user.response;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.elsyah.tech.javara.entity.user.UserEntity;
import com.elsyah.tech.javara.projection.user.UserSelectColumn;
import com.elsyah.tech.javara.response.user.UserResponse;
import com.elsyah.tech.javara.response.user.UserResponseWithSelectColumn;

class UserResponseTest {

    // Verifies that a user entity is mapped to the public response record.
    @Test
    void shouldMapUserEntityToUserResponse() {
        Instant createdAt = Instant.parse("2026-11-20T10:00:00Z");
        Instant updatedAt = createdAt.plusSeconds(3600);

        UserEntity user = new UserEntity();
        user.setId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        user.setEmail("user@example.com");
        user.setPassword("secret123");
        user.setIsActive(true);
        user.setUpdatedAt(updatedAt);

        UserResponse response = UserResponse.from(user);

        assertThat(response.id()).isEqualTo(user.getId());
        assertThat(response.email()).isEqualTo("user@example.com");
        assertThat(response.isActive()).isTrue();
        assertThat(response.updatedAt()).isEqualTo(updatedAt);
        assertThat(response.deletedAt()).isNull();
    }

    // Verifies that a selected-column projection is mapped to the public response record.
    @Test
    void shouldMapUserProjectionToUserResponse() {
        Instant createdAt = Instant.parse("2026-11-20T10:00:00Z");
        UserSelectColumn projection = new UserSelectColumn() {
            public UUID getId() { return UUID.fromString("00000000-0000-0000-0000-000000000002"); }
            public String getEmail() { return "projection@example.com"; }
            public String getPassword() { return "secret123"; }
            public Boolean getIsActive() { return false; }
            public Instant getCreatedAt() { return createdAt; }
            public Instant getUpdatedAt() { return null; }
            public Instant getDeletedAt() { return null; }
        };

        UserResponseWithSelectColumn response =
                UserResponseWithSelectColumn.from(projection);

        assertThat(response.id()).isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000002"));
        assertThat(response.email()).isEqualTo("projection@example.com");
        assertThat(response.isActive()).isFalse();
        assertThat(response.createdAt()).isEqualTo(createdAt);
    }
}
