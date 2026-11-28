package com.elsyah.tech.javara.unit.user.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.elsyah.tech.javara.entity.user.UserEntity;
import com.elsyah.tech.javara.entity.user.UserRole;
import com.elsyah.tech.javara.middleware.config.ApplicationIdentityProperties;
import com.elsyah.tech.javara.repository.user.UserRepository;
import com.elsyah.tech.javara.service.user.BootstrapAdminService;
import com.elsyah.tech.javara.service.user.PasswordPolicyService;
import com.elsyah.tech.javara.service.user.UserRoleService;
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
        UserRoleService roleService = mock(UserRoleService.class);
        PasswordHashService passwordHashService = new PasswordHashService("ARGON2");
        when(repository.findByEmail("admin@example.com")).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        new BootstrapAdminService(
                        repository,
                        passwordHashService,
                        allowedPasswordPolicy(),
                        roleService,
                        applicationIdentity())
                .createInitialAdmin("admin@example.com", "admin-password");

        ArgumentCaptor<UserEntity> userCaptor = forClass(UserEntity.class);
        verify(repository).saveAndFlush(userCaptor.capture());
        UserEntity admin = userCaptor.getValue();
        assertEquals("admin@example.com", admin.getEmail());
        assertTrue(admin.isEmailVerified());
        assertTrue(passwordHashService.matches("admin-password", admin.getPassword()));
        verify(roleService).assignInitialRoles(admin.getId(), List.of("ADMIN"));
    }

    // Verifies that bootstrap refuses to promote an existing regular user.
    @Test
    void refusesToPromoteAnExistingUser() {
        UserRepository repository = mock(UserRepository.class);
        UserRoleService roleService = mock(UserRoleService.class);
        UserEntity existing = new UserEntity();
        when(repository.findByEmail("user@example.com")).thenReturn(Optional.of(existing));

        assertThrows(
                IllegalStateException.class,
                () -> new BootstrapAdminService(
                                repository,
                                new PasswordHashService("ARGON2"),
                                allowedPasswordPolicy(),
                                roleService,
                                applicationIdentity())
                        .createInitialAdmin("user@example.com", "admin-password"));
        verify(repository, never()).saveAndFlush(existing);
    }

    // Verifies that repeating bootstrap for the same ADMIN does not change the stored account.
    @Test
    void existingAdminMakesBootstrapIdempotent() {
        UserRepository repository = mock(UserRepository.class);
        UserRoleService roleService = mock(UserRoleService.class);
        UserEntity existing = new UserEntity();
        when(repository.findByEmail("admin@example.com")).thenReturn(Optional.of(existing));
        when(repository.hasPermission(null, "users:manage")).thenReturn(true);

        new BootstrapAdminService(
                        repository,
                        new PasswordHashService("ARGON2"),
                        allowedPasswordPolicy(),
                        roleService,
                        applicationIdentity())
                .createInitialAdmin("admin@example.com", "admin-password");

        verify(repository, never()).saveAndFlush(existing);
    }

    @Test
    void refusesCommonOrBreachedBootstrapPassword() {
        UserRepository repository = mock(UserRepository.class);
        UserRoleService roleService = mock(UserRoleService.class);
        PasswordPolicyService policy = mock(PasswordPolicyService.class);
        when(repository.findByEmail("admin@example.com")).thenReturn(Optional.empty());
        when(policy.isPasswordAllowed("compromised-password")).thenReturn(false);

        assertThrows(
                IllegalStateException.class,
                () -> new BootstrapAdminService(
                                repository,
                                new PasswordHashService("ARGON2"),
                                policy,
                                roleService,
                                applicationIdentity())
                        .createInitialAdmin("admin@example.com", "compromised-password"));

        verify(repository, never()).saveAndFlush(org.mockito.ArgumentMatchers.any());
    }

    private static ApplicationIdentityProperties applicationIdentity() {
        return new ApplicationIdentityProperties("Test Application");
    }
}
