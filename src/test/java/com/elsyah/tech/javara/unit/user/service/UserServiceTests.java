package com.elsyah.tech.javara.unit.user.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;

import com.elsyah.tech.javara.entity.user.UserEntity;
import com.elsyah.tech.javara.projection.user.UserSelectColumn;
import com.elsyah.tech.javara.repository.auth.RefreshTokenRepository;
import com.elsyah.tech.javara.repository.user.UserRepository;
import com.elsyah.tech.javara.request.user.UserRequest;
import com.elsyah.tech.javara.request.user.UserUpdateRequest;
import com.elsyah.tech.javara.request.auth.LoginRequest;
import com.elsyah.tech.javara.service.user.PasswordPolicyService;
import com.elsyah.tech.javara.service.user.UserService;
import com.elsyah.tech.javara.service.auth.SuspiciousAuthenticationService;
import com.elsyah.tech.javara.service.common.ServiceResult;
import com.elsyah.tech.javara.service.email.EmailNotificationService;
import com.elsyah.tech.javara.service.email.EmailVerificationService;
import com.elsyah.tech.javara.utils.security.PasswordHashService;


/**
 * Unit tests for the {@link UserService} class.
 * These tests focus on the service layer logic, mocking the repository to isolate the service behavior.
 * Each test verifies the expected behavior of the service methods under various scenarios, including success and error conditions.
 * The tests cover user creation, retrieval, updating, and deletion, ensuring that the service correctly handles business logic and interacts with the repository as expected.
 * The tests also validate the service's response structure, including HTTP status codes and messages, to ensure proper API behavior.
 * Note: The repository is mocked to avoid actual database interactions, allowing for focused testing of the service logic.
 * 
 * @author Elmansyah
 * @version 1.0.0
 * @since 2026-11-18
 * @contact https://www.linkedin.com/in/elmansyah
 */
class UserServiceTests {
    private static final Instant NOW = Instant.parse("2026-11-23T12:00:00Z");

    private static UserService newService(UserRepository repository) {
        return new UserService(
                repository,
                new PasswordHashService(),
                mock(RefreshTokenRepository.class),
                allowedPasswordPolicy(),
                mock(EmailNotificationService.class),
                mock(EmailVerificationService.class),
                mock(SuspiciousAuthenticationService.class),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static UserService newService(
            UserRepository repository,
            PasswordHashService passwordHashService) {
        return new UserService(
                repository,
                passwordHashService,
                mock(RefreshTokenRepository.class),
                allowedPasswordPolicy(),
                mock(EmailNotificationService.class),
                mock(EmailVerificationService.class),
                mock(SuspiciousAuthenticationService.class),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static PasswordPolicyService allowedPasswordPolicy() {
        PasswordPolicyService passwordPolicyService = mock(PasswordPolicyService.class);
        when(passwordPolicyService.isPasswordAllowed(
                org.mockito.ArgumentMatchers.anyString())).thenReturn(true);
        return passwordPolicyService;
    }

    // Verifies that an existing email prevents user creation and repository saves.
    @Test
    void createUserWithExistingEmailReturnsConflictResult() {
        UserRepository repository = mock(UserRepository.class);
        UserRequest request = new UserRequest("existing@example.com", "password123", true);
        when(repository.existsByEmail("existing@example.com")).thenReturn(true);
        UserService service = newService(repository);

        ServiceResult<UserEntity> result = service.createUser(request);

        assertEquals("Email already exists", result.message());
        assertEquals(HttpStatus.CONFLICT, result.status());
        assertNull(result.data());
        verify(repository, never()).save(any());
    }

    @Test
    void createUserRejectsCommonOrBreachedPassword() {
        UserRepository repository = mock(UserRepository.class);
        PasswordPolicyService passwordPolicyService = mock(PasswordPolicyService.class);
        when(passwordPolicyService.isPasswordAllowed("compromised-password")).thenReturn(false);
        UserService service = new UserService(
                repository,
                new PasswordHashService(),
                mock(RefreshTokenRepository.class),
                passwordPolicyService,
                mock(EmailNotificationService.class),
                mock(EmailVerificationService.class),
                mock(SuspiciousAuthenticationService.class),
                Clock.fixed(NOW, ZoneOffset.UTC));

        ServiceResult<UserEntity> result = service.createUser(
                new UserRequest("new@example.com", "compromised-password", true));

        assertEquals(HttpStatus.BAD_REQUEST, result.status());
        assertEquals(
                "Choose a password that is not commonly used or known to be compromised",
                result.message());
        verifyNoInteractions(repository);
    }

    // Verifies that a unique email creates, saves, and returns a new user entity.
    @Test
    void createUserWithUniqueEmailCreatesAndSavesEntity() {
        UserRepository repository = mock(UserRepository.class);
        UserRequest request = new UserRequest("new@example.com", "password123", false);
        when(repository.existsByEmail("new@example.com")).thenReturn(false);
        when(repository.saveAndFlush(any(UserEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        UserService service = newService(repository);

        ServiceResult<UserEntity> result = service.createUser(request);

        ArgumentCaptor<UserEntity> captor = forClass(UserEntity.class);
        verify(repository).saveAndFlush(captor.capture());
        UserEntity created = captor.getValue();
        assertSame(created, result.data());
        assertEquals(HttpStatus.CREATED, result.status());
        assertEquals("User created successfully with id: " + created.getId(), result.message());
        assertNotNull(created.getId());
        assertEquals("new@example.com", created.getEmail());
        assertTrue(created.getPassword().startsWith("{argon2}") );
        assertTrue(created.getIsActive());
    }

    // Verifies that user listing returns a stable page and preserves the total count.
    @Test
    void findAllUsersReturnsPageWithTotalCountAndStableSort() {
        UserRepository repository = mock(UserRepository.class);
        UserSelectColumn projection = mock(UserSelectColumn.class);
        Pageable pageable = PageRequest.of(1, 10);
        Pageable orderedPageable = PageRequest.of(
                1,
                10,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id")));
        Page<UserSelectColumn> page = new PageImpl<>(List.of(projection), orderedPageable, 21);
        when(repository.findAllUsersPage(orderedPageable)).thenReturn(page);

        ServiceResult<Page<UserSelectColumn>> result = newService(repository).findAllUsers(pageable);

        assertEquals(HttpStatus.OK, result.status());
        assertEquals("Users retrieved successfully", result.message());
        assertEquals(1, result.data().getContent().size());
        assertSame(projection, result.data().getContent().get(0));
        assertEquals(21, result.data().getTotalElements());
        assertEquals(3, result.data().getTotalPages());
        assertEquals(1, result.data().getNumber());
        verify(repository).findAllUsersPage(orderedPageable);
    }

    // Verifies that an empty collection is a successful page with a zero total.
    @Test
    void findAllUsersReturnsEmptyPageWhenNoUsersExist() {
        UserRepository repository = mock(UserRepository.class);
        Pageable pageable = PageRequest.of(0, 20);
        Pageable orderedPageable = PageRequest.of(
                0,
                20,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id")));
        when(repository.findAllUsersPage(orderedPageable))
                .thenReturn(new PageImpl<>(List.of(), orderedPageable, 0));

        ServiceResult<Page<UserSelectColumn>> result = newService(repository).findAllUsers(pageable);

        assertEquals(HttpStatus.OK, result.status());
        assertEquals("Users retrieved successfully", result.message());
        assertNotNull(result.data());
        assertTrue(result.data().isEmpty());
        assertEquals(0, result.data().getTotalElements());
        assertEquals(0, result.data().getTotalPages());
        verify(repository).findAllUsersPage(orderedPageable);
    }

    @Test
    void findAllUsersRejectsPageSizesOutsideAllowedRange() {
        UserRepository repository = mock(UserRepository.class);
        UserService service = newService(repository);

        ServiceResult<Page<UserSelectColumn>> result =
                service.findAllUsers(PageRequest.of(0, 101));

        assertEquals(HttpStatus.BAD_REQUEST, result.status());
        assertEquals("Page size must be between 1 and 100", result.message());
        assertNull(result.data());
        verifyNoInteractions(repository);
    }

    // Verifies that a user is returned when the repository finds the requested ID.
    @Test
    void findUserByIdReturnsExistingUser() {
        UserRepository repository = mock(UserRepository.class);
        UserEntity user = new UserEntity();
        user.setId(uuid("user-1"));
        when(repository.findById(uuid("user-1"))).thenReturn(Optional.of(user));

        ServiceResult<UserEntity> result = newService(repository).findUserById(uuid("user-1"));

        assertSame(user, result.data());
        assertEquals(HttpStatus.OK, result.status());
    }

    // Verifies that a missing user ID produces a not-found service result.
    @Test
    void findUserByIdReturnsNotFoundWhenUserDoesNotExist() {
        UserRepository repository = mock(UserRepository.class);
        when(repository.findById(uuid("missing-id"))).thenReturn(Optional.empty());

        ServiceResult<UserEntity> result = newService(repository).findUserById(uuid("missing-id"));

        assertEquals(HttpStatus.NOT_FOUND, result.status());
        assertEquals("User not found with id: " + uuid("missing-id"), result.message());
        assertNull(result.data());
    }

    // Verifies that selected-column lookup returns the repository projection.
    @Test
    void findUserByIdWithSelectedColumnsReturnsProjection() {
        UserRepository repository = mock(UserRepository.class);
        UserSelectColumn projection = mock(UserSelectColumn.class);
        when(repository.findUserByIdWithSelectedColumns(uuid("user-1"))).thenReturn(Optional.of(projection));

        ServiceResult<UserSelectColumn> result =
                newService(repository).findUserByIdWithSelectedColumns(uuid("user-1"));

        assertSame(projection, result.data());
        assertEquals(HttpStatus.OK, result.status());
    }

    // Verifies that selected-column lookup returns not-found for a missing ID.
    @Test
    void findUserByIdWithSelectedColumnsReturnsNotFoundWhenUserDoesNotExist() {
        UserRepository repository = mock(UserRepository.class);
        when(repository.findUserByIdWithSelectedColumns(uuid("missing-id"))).thenReturn(Optional.empty());

        ServiceResult<UserSelectColumn> result =
                newService(repository).findUserByIdWithSelectedColumns(uuid("missing-id"));

        assertEquals(HttpStatus.NOT_FOUND, result.status());
        assertEquals("User not found with id: " + uuid("missing-id"), result.message());
    }

    // Verifies that an existing user is updated with all supplied fields and saved.
    @Test
    void updateUserUpdatesFieldsAndSavesUser() {
        UserRepository repository = mock(UserRepository.class);
        RefreshTokenRepository refreshTokenRepository = mock(RefreshTokenRepository.class);
        UserEntity user = new UserEntity();
        user.setEmail("old@example.com");
        PasswordHashService passwordHashService = new PasswordHashService();
        user.setPassword(passwordHashService.encode("oldpassword"));
        user.setIsActive(true);
        UserUpdateRequest request = new UserUpdateRequest("old@example.com", false);
        when(repository.findByIdForUpdate(uuid("user-1"))).thenReturn(Optional.of(user));
        when(repository.existsByEmailAndIdNot("old@example.com", uuid("user-1"))).thenReturn(false);
        when(repository.saveAndFlush(user)).thenReturn(user);

        ServiceResult<UserEntity> result = new UserService(
                repository,
                passwordHashService,
                refreshTokenRepository,
                allowedPasswordPolicy(),
                mock(EmailNotificationService.class),
                mock(EmailVerificationService.class),
                mock(SuspiciousAuthenticationService.class),
                Clock.fixed(NOW, ZoneOffset.UTC)).updateUser(uuid("user-1"), request);

        assertSame(user, result.data());
        assertEquals(HttpStatus.OK, result.status());
        assertEquals("old@example.com", user.getEmail());
        assertTrue(passwordHashService.matches("oldpassword", user.getPassword()));
        assertFalse(passwordHashService.matches("newpassword", user.getPassword()));
        assertFalse(user.getIsActive());
        assertEquals(NOW, user.getUpdatedAt());
        verify(repository).saveAndFlush(user);
        verify(refreshTokenRepository)
                .revokeActiveTokensForUser(org.mockito.ArgumentMatchers.eq(uuid("user-1")), any());
    }

    // Verifies that an email used by another user prevents an update.
    @Test
    void updateUserRequiresDedicatedEmailChangeFlow() {
        UserRepository repository = mock(UserRepository.class);
        UserEntity user = new UserEntity();
        user.setId(uuid("user-1"));
        user.setEmail("old@example.com");
        UserUpdateRequest request = new UserUpdateRequest("taken@example.com", true);
        when(repository.findByIdForUpdate(uuid("user-1"))).thenReturn(Optional.of(user));

        ServiceResult<UserEntity> result = newService(repository).updateUser(uuid("user-1"), request);

        assertEquals(HttpStatus.BAD_REQUEST, result.status());
        assertTrue(result.message().contains("dedicated email-change endpoint"));
        assertNull(result.data());
        assertEquals("old@example.com", user.getEmail());
        verify(repository, never()).saveAndFlush(any());
    }

    // Verifies that a user may keep its own email during an update.
    @Test
    void updateAllowsKeepingOwnEmail() {
        UserRepository repository = mock(UserRepository.class);
        UserEntity user = new UserEntity();
        user.setId(uuid("user-1"));
        user.setEmail("current@example.com");
        user.setPassword("oldpassword");
        user.setIsActive(true);
        UserUpdateRequest request = new UserUpdateRequest("current@example.com", null);
        when(repository.findByIdForUpdate(uuid("user-1"))).thenReturn(Optional.of(user));
        when(repository.existsByEmailAndIdNot("current@example.com", uuid("user-1"))).thenReturn(false);
        when(repository.saveAndFlush(user)).thenReturn(user);

        ServiceResult<UserEntity> result = newService(repository).updateUser(uuid("user-1"), request);

        assertSame(user, result.data());
        assertEquals(HttpStatus.OK, result.status());
        assertEquals("current@example.com", user.getEmail());
        assertEquals("oldpassword", user.getPassword());
        assertTrue(user.getIsActive());
        assertEquals(NOW, user.getUpdatedAt());
        verify(repository).existsByEmailAndIdNot("current@example.com", uuid("user-1"));
        verify(repository).saveAndFlush(user);
    }

    @Test
    void profileUpdateDoesNotChangePasswordOrRevokeSessions() {
        UserRepository repository = mock(UserRepository.class);
        RefreshTokenRepository refreshTokenRepository = mock(RefreshTokenRepository.class);
        PasswordHashService passwordHashService = new PasswordHashService();
        UserEntity user = new UserEntity();
        user.setId(uuid("user-1"));
        user.setEmail("current@example.com");
        user.setPassword(passwordHashService.encode("samepassword"));
        user.setIsActive(true);
        UserUpdateRequest request = new UserUpdateRequest("current@example.com", true);
        when(repository.findByIdForUpdate(uuid("user-1"))).thenReturn(Optional.of(user));
        when(repository.existsByEmailAndIdNot("current@example.com", uuid("user-1"))).thenReturn(false);
        when(repository.saveAndFlush(user)).thenReturn(user);

        ServiceResult<UserEntity> result = new UserService(
                repository,
                passwordHashService,
                refreshTokenRepository,
                allowedPasswordPolicy(),
                mock(EmailNotificationService.class),
                mock(EmailVerificationService.class),
                mock(SuspiciousAuthenticationService.class),
                Clock.fixed(NOW, ZoneOffset.UTC)).updateUser(uuid("user-1"), request);

        assertEquals(HttpStatus.OK, result.status());
        assertTrue(passwordHashService.matches("samepassword", user.getPassword()));
        verify(refreshTokenRepository, never()).revokeActiveTokensForUser(any(), any());
    }

    @Test
    void changePasswordRequiresCurrentPasswordAndRevokesSessions() {
        UserRepository repository = mock(UserRepository.class);
        RefreshTokenRepository refreshTokenRepository = mock(RefreshTokenRepository.class);
        PasswordHashService passwordHashService = new PasswordHashService();
        UserEntity user = new UserEntity();
        user.setId(uuid("user-1"));
        user.setPassword(passwordHashService.encode("current-password"));
        user.setIsActive(true);
        when(repository.findByIdForUpdate(user.getId())).thenReturn(Optional.of(user));
        when(repository.saveAndFlush(user)).thenReturn(user);

        UserService service = new UserService(
                repository,
                passwordHashService,
                refreshTokenRepository,
                allowedPasswordPolicy(),
                mock(EmailNotificationService.class),
                mock(EmailVerificationService.class),
                mock(SuspiciousAuthenticationService.class),
                Clock.fixed(NOW, ZoneOffset.UTC));
        ServiceResult<Void> result =
                service.changePassword(user.getId(), "current-password", "new-password-123");

        assertEquals(HttpStatus.OK, result.status());
        assertTrue(passwordHashService.matches("new-password-123", user.getPassword()));
        assertEquals(NOW, user.getUpdatedAt());
        verify(refreshTokenRepository).revokeActiveTokensForUser(user.getId(), NOW);
    }

    @Test
    void changePasswordRejectsIncorrectCurrentPasswordWithoutMutation() {
        UserRepository repository = mock(UserRepository.class);
        RefreshTokenRepository refreshTokenRepository = mock(RefreshTokenRepository.class);
        PasswordHashService passwordHashService = new PasswordHashService();
        UserEntity user = new UserEntity();
        user.setId(uuid("user-1"));
        user.setPassword(passwordHashService.encode("current-password"));
        user.setIsActive(true);
        when(repository.findByIdForUpdate(user.getId())).thenReturn(Optional.of(user));

        ServiceResult<Void> result = new UserService(
                repository,
                passwordHashService,
                refreshTokenRepository,
                allowedPasswordPolicy(),
                mock(EmailNotificationService.class),
                mock(EmailVerificationService.class),
                mock(SuspiciousAuthenticationService.class),
                Clock.fixed(NOW, ZoneOffset.UTC))
                .changePassword(user.getId(), "incorrect-password", "new-password-123");

        assertEquals(HttpStatus.UNAUTHORIZED, result.status());
        assertTrue(passwordHashService.matches("current-password", user.getPassword()));
        verify(repository, never()).saveAndFlush(any());
        verifyNoInteractions(refreshTokenRepository);
    }

    @Test
    void changePasswordRejectsReusingCurrentPassword() {
        UserRepository repository = mock(UserRepository.class);
        RefreshTokenRepository refreshTokenRepository = mock(RefreshTokenRepository.class);
        PasswordHashService passwordHashService = new PasswordHashService();
        UserEntity user = new UserEntity();
        user.setId(uuid("user-1"));
        user.setPassword(passwordHashService.encode("current-password"));
        user.setIsActive(true);
        when(repository.findByIdForUpdate(user.getId())).thenReturn(Optional.of(user));

        ServiceResult<Void> result = new UserService(
                repository,
                passwordHashService,
                refreshTokenRepository,
                allowedPasswordPolicy(),
                mock(EmailNotificationService.class),
                mock(EmailVerificationService.class),
                mock(SuspiciousAuthenticationService.class),
                Clock.fixed(NOW, ZoneOffset.UTC))
                .changePassword(user.getId(), "current-password", "current-password");

        assertEquals(HttpStatus.BAD_REQUEST, result.status());
        verify(repository, never()).saveAndFlush(any());
        verifyNoInteractions(refreshTokenRepository);
    }

    @Test
    void changePasswordRejectsPreviouslyCompromisedPassword() {
        UserRepository repository = mock(UserRepository.class);
        RefreshTokenRepository refreshTokenRepository = mock(RefreshTokenRepository.class);
        PasswordHashService passwordHashService = new PasswordHashService();
        UserEntity user = new UserEntity();
        user.setId(uuid("user-1"));
        user.setPassword(passwordHashService.encode("current-password"));
        user.getCompromisedPasswordHashes()
                .add(passwordHashService.encode("previously-compromised-password"));
        user.setIsActive(true);
        when(repository.findByIdForUpdate(user.getId())).thenReturn(Optional.of(user));

        ServiceResult<Void> result = new UserService(
                repository,
                passwordHashService,
                refreshTokenRepository,
                allowedPasswordPolicy(),
                mock(EmailNotificationService.class),
                mock(EmailVerificationService.class),
                mock(SuspiciousAuthenticationService.class),
                Clock.fixed(NOW, ZoneOffset.UTC))
                .changePassword(
                        user.getId(),
                        "current-password",
                        "previously-compromised-password");

        assertEquals(HttpStatus.BAD_REQUEST, result.status());
        assertTrue(passwordHashService.matches("current-password", user.getPassword()));
        verify(repository, never()).saveAndFlush(any());
        verifyNoInteractions(refreshTokenRepository);
    }

    @Test
    void changePasswordRejectsPasswordInConfiguredDenylist() {
        UserRepository repository = mock(UserRepository.class);
        RefreshTokenRepository refreshTokenRepository = mock(RefreshTokenRepository.class);
        PasswordHashService passwordHashService = new PasswordHashService();
        PasswordPolicyService passwordPolicyService = mock(PasswordPolicyService.class);
        UserEntity user = new UserEntity();
        user.setId(uuid("user-1"));
        user.setPassword(passwordHashService.encode("current-password"));
        user.setIsActive(true);
        when(repository.findByIdForUpdate(user.getId())).thenReturn(Optional.of(user));
        when(passwordPolicyService.isPasswordAllowed("known-compromised-password"))
                .thenReturn(false);

        ServiceResult<Void> result = new UserService(
                repository,
                passwordHashService,
                refreshTokenRepository,
                passwordPolicyService,
                mock(EmailNotificationService.class),
                mock(EmailVerificationService.class),
                mock(SuspiciousAuthenticationService.class),
                Clock.fixed(NOW, ZoneOffset.UTC))
                .changePassword(
                        user.getId(),
                        "current-password",
                        "known-compromised-password");

        assertEquals(HttpStatus.BAD_REQUEST, result.status());
        assertTrue(passwordHashService.matches("current-password", user.getPassword()));
        verify(repository, never()).saveAndFlush(any());
        verifyNoInteractions(refreshTokenRepository);
    }

    // Verifies that updating a missing user returns not-found without saving.
    @Test
    void updateUserReturnsNotFoundWhenUserDoesNotExist() {
        UserRepository repository = mock(UserRepository.class);
        when(repository.findByIdForUpdate(uuid("missing-id"))).thenReturn(Optional.empty());

        ServiceResult<UserEntity> result =
                newService(repository).updateUser(
                        uuid("missing-id"), new UserUpdateRequest("user@example.com", true));

        assertEquals(HttpStatus.NOT_FOUND, result.status());
        assertEquals("User not found with id: " + uuid("missing-id"), result.message());
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void updateUserRejectsPasswordChange() {
        UserRepository repository = mock(UserRepository.class);

        ServiceResult<UserEntity> result = newService(repository).updateUser(
                uuid("user-1"),
                new UserUpdateRequest("user@example.com", true, "unverified-password"));

        assertEquals(HttpStatus.BAD_REQUEST, result.status());
        assertEquals("Use the dedicated password-change endpoint", result.message());
        verifyNoInteractions(repository);
    }

    // Verifies that deletion sets the soft-delete timestamp and saves the user.
    @Test
    void deleteUserSetsDeletedAtAndSavesUser() {
        UserRepository repository = mock(UserRepository.class);
        UserEntity user = new UserEntity();
        when(repository.findByIdForUpdate(uuid("user-1"))).thenReturn(Optional.of(user));

        ServiceResult<UserEntity> result = newService(repository).deleteUser(uuid("user-1"));

        assertEquals(HttpStatus.OK, result.status());
        assertEquals("User deleted successfully", result.message());
        assertEquals(NOW, user.getDeletedAt());
        verify(repository).saveAndFlush(user);
    }

    // Verifies that deleting a missing user returns not-found without saving.
    @Test
    void deleteUserReturnsNotFoundWhenUserDoesNotExist() {
        UserRepository repository = mock(UserRepository.class);
        when(repository.findByIdForUpdate(uuid("missing-id"))).thenReturn(Optional.empty());

        ServiceResult<UserEntity> result = newService(repository).deleteUser(uuid("missing-id"));

        assertEquals(HttpStatus.NOT_FOUND, result.status());
        assertEquals("User not found with id: " + uuid("missing-id"), result.message());
        verify(repository, never()).saveAndFlush(any());
    }

    // Verifies that valid active-user credentials return the user without rewriting a current hash.
    @Test
    void loginReturnsUserWhenCredentialsAreValid() {
        UserRepository repository = mock(UserRepository.class);
        PasswordHashService passwordHashService = new PasswordHashService("ARGON2");
        UserEntity user = new UserEntity();
        user.setId(uuid("user-1"));
        user.setEmail("user@example.com");
        user.setPassword(passwordHashService.encode("password123"));
        user.setIsActive(true);
        when(repository.findByEmailForUpdate("user@example.com")).thenReturn(Optional.of(user));

        ServiceResult<UserEntity> result = newService(repository, passwordHashService)
                .login(new LoginRequest("user@example.com", "password123"));

        assertEquals(HttpStatus.OK, result.status());
        assertEquals("Login successful", result.message());
        assertSame(user, result.data());
        verify(repository, never()).saveAndFlush(any());
    }

    // Verifies that a valid legacy hash is upgraded to the configured default after login.
    @Test
    void loginUpgradesLegacyPasswordHashAfterSuccessfulAuthentication() {
        UserRepository repository = mock(UserRepository.class);
        PasswordHashService passwordHashService = new PasswordHashService("ARGON2");
        UserEntity user = new UserEntity();
        user.setId(uuid("user-1"));
        user.setEmail("legacy@example.com");
        user.setPassword("{md5}482c811da5d5b4bc6d497ffa98491e38");
        user.setIsActive(true);
        when(repository.findByEmailForUpdate("legacy@example.com")).thenReturn(Optional.of(user));
        when(repository.saveAndFlush(user)).thenReturn(user);

        ServiceResult<UserEntity> result = newService(repository, passwordHashService)
                .login(new LoginRequest("legacy@example.com", "password123"));

        assertEquals(HttpStatus.OK, result.status());
        assertTrue(user.getPassword().startsWith("{argon2}"));
        assertTrue(passwordHashService.matches("password123", user.getPassword()));
        assertNotNull(user.getUpdatedAt());
        verify(repository).saveAndFlush(user);
    }

    // Verifies that invalid credentials use one generic unauthorized result.
    @Test
    void loginRejectsUnknownEmailAndWrongPasswordWithSameResult() {
        UserRepository repository = mock(UserRepository.class);
        PasswordHashService passwordHashService = spy(new PasswordHashService("ARGON2"));
        UserEntity user = new UserEntity();
        user.setEmail("user@example.com");
        user.setPassword(passwordHashService.encode("password123"));
        user.setIsActive(true);
        when(repository.findByEmailForUpdate("user@example.com")).thenReturn(Optional.of(user));
        when(repository.findByEmailForUpdate("missing@example.com")).thenReturn(Optional.empty());
        UserService service = newService(repository, passwordHashService);

        ServiceResult<UserEntity> unknownEmail =
                service.login(new LoginRequest("missing@example.com", "password123"));
        ServiceResult<UserEntity> wrongPassword =
                service.login(new LoginRequest("user@example.com", "wrong-password"));

        assertEquals(HttpStatus.UNAUTHORIZED, unknownEmail.status());
        assertEquals(HttpStatus.UNAUTHORIZED, wrongPassword.status());
        assertEquals("Invalid email or password", unknownEmail.message());
        assertEquals(unknownEmail.message(), wrongPassword.message());
        assertNull(unknownEmail.data());
        assertNull(wrongPassword.data());
        verify(passwordHashService).matchesDummyPassword("password123");
        verify(passwordHashService).matches("wrong-password", user.getPassword());
        verify(repository, never()).saveAndFlush(any());
    }

    // Verifies that inactive accounts cannot log in even when the password is correct.
    @Test
    void loginRejectsInactiveUser() {
        UserRepository repository = mock(UserRepository.class);
        PasswordHashService passwordHashService = spy(new PasswordHashService("ARGON2"));
        UserEntity user = new UserEntity();
        user.setEmail("inactive@example.com");
        user.setPassword(passwordHashService.encode("password123"));
        user.setIsActive(false);
        when(repository.findByEmailForUpdate("inactive@example.com")).thenReturn(Optional.of(user));

        ServiceResult<UserEntity> result = newService(repository, passwordHashService)
                .login(new LoginRequest("inactive@example.com", "password123"));

        assertEquals(HttpStatus.UNAUTHORIZED, result.status());
        assertEquals("Invalid email or password", result.message());
        verify(passwordHashService).matches("password123", user.getPassword());
        verify(repository, never()).saveAndFlush(any());
    }

    private static UUID uuid(String value) {
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
    }
}
