package com.elsyah.tech.javara.unit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.elsyah.tech.javara.entity.UserEntity;
import com.elsyah.tech.javara.entity.UserRole;
import com.elsyah.tech.javara.repository.UserRepository;
import com.elsyah.tech.javara.service.BootstrapAdminService;
import com.elsyah.tech.javara.utils.security.PasswordHashService;

class BootstrapAdminServiceTests {
    // Verifies that bootstrap creates a hashed-password ADMIN when the email is unused.
    @Test
    void createsInitialAdminWhenEmailIsUnused() {
        UserRepository repository = mock(UserRepository.class);
        PasswordHashService passwordHashService = new PasswordHashService("ARGON2");
        when(repository.findByEmail("admin@example.com")).thenReturn(Optional.empty());

        new BootstrapAdminService(repository, passwordHashService)
                .createInitialAdmin("admin@example.com", "admin-password");

        ArgumentCaptor<UserEntity> userCaptor = forClass(UserEntity.class);
        verify(repository).saveAndFlush(userCaptor.capture());
        UserEntity admin = userCaptor.getValue();
        assertEquals(UserRole.ADMIN, admin.getRole());
        assertEquals("admin@example.com", admin.getEmail());
        assertTrue(passwordHashService.matches("admin-password", admin.getPassword()));
    }

    // Verifies that bootstrap refuses to promote an existing regular user.
    @Test
    void refusesToPromoteAnExistingUser() {
        UserRepository repository = mock(UserRepository.class);
        UserEntity existing = new UserEntity();
        existing.setRole(UserRole.USER);
        when(repository.findByEmail("user@example.com")).thenReturn(Optional.of(existing));

        assertThrows(
                IllegalStateException.class,
                () -> new BootstrapAdminService(repository, new PasswordHashService("ARGON2"))
                        .createInitialAdmin("user@example.com", "admin-password"));
        verify(repository, never()).saveAndFlush(existing);
    }

    // Verifies that repeating bootstrap for the same ADMIN does not change the stored account.
    @Test
    void existingAdminMakesBootstrapIdempotent() {
        UserRepository repository = mock(UserRepository.class);
        UserEntity existing = new UserEntity();
        existing.setRole(UserRole.ADMIN);
        when(repository.findByEmail("admin@example.com")).thenReturn(Optional.of(existing));

        new BootstrapAdminService(repository, new PasswordHashService("ARGON2"))
                .createInitialAdmin("admin@example.com", "admin-password");

        verify(repository, never()).saveAndFlush(existing);
    }
}
