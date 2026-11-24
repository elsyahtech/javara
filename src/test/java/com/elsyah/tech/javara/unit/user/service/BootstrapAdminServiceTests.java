package com.elsyah.tech.javara.unit.user.service;

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

import com.elsyah.tech.javara.entity.user.UserEntity;
import com.elsyah.tech.javara.entity.user.UserRole;
import com.elsyah.tech.javara.repository.user.UserRepository;
import com.elsyah.tech.javara.service.user.BootstrapAdminService;
import com.elsyah.tech.javara.service.user.PasswordPolicyService;
import com.elsyah.tech.javara.utils.security.PasswordHashService;

class BootstrapAdminServiceTests {
    private static PasswordPolicyService allowedPasswordPolicy() {
        PasswordPolicyService policy = mock(PasswordPolicyService.class);
        when(policy.isPasswordAllowed(org.mockito.ArgumentMatchers.anyString())).thenReturn(true);
        return policy;
    }

    // Verifies that bootstrap creates a hashed-password ADMIN when the email is unused.
    @Test
    void createsInitialAdminWhenEmailIsUnused() {
        UserRepository repository = mock(UserRepository.class);
        PasswordHashService passwordHashService = new PasswordHashService("ARGON2");
        when(repository.findByEmail("admin@example.com")).thenReturn(Optional.empty());

        new BootstrapAdminService(repository, passwordHashService, allowedPasswordPolicy())
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
                () -> new BootstrapAdminService(
                                repository,
                                new PasswordHashService("ARGON2"),
                                allowedPasswordPolicy())
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

        new BootstrapAdminService(
                        repository,
                        new PasswordHashService("ARGON2"),
                        allowedPasswordPolicy())
                .createInitialAdmin("admin@example.com", "admin-password");

        verify(repository, never()).saveAndFlush(existing);
    }

    @Test
    void refusesCommonOrBreachedBootstrapPassword() {
        UserRepository repository = mock(UserRepository.class);
        PasswordPolicyService policy = mock(PasswordPolicyService.class);
        when(repository.findByEmail("admin@example.com")).thenReturn(Optional.empty());
        when(policy.isPasswordAllowed("compromised-password")).thenReturn(false);

        assertThrows(
                IllegalStateException.class,
                () -> new BootstrapAdminService(
                                repository, new PasswordHashService("ARGON2"), policy)
                        .createInitialAdmin("admin@example.com", "compromised-password"));

        verify(repository, never()).saveAndFlush(org.mockito.ArgumentMatchers.any());
    }
}
