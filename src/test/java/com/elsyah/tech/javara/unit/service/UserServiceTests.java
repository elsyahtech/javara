package com.elsyah.tech.javara.unit.service;

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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;

import com.elsyah.tech.javara.entity.UserEntity;
import com.elsyah.tech.javara.projection.UserSelectColumn;
import com.elsyah.tech.javara.repository.UserRepository;
import com.elsyah.tech.javara.request.UserRequest;
import com.elsyah.tech.javara.request.LoginRequest;
import com.elsyah.tech.javara.service.UserService;
import com.elsyah.tech.javara.service.ServiceResult;
import com.elsyah.tech.javara.utils.security.PasswordHashService;
import com.elsyah.tech.javara.utils.security.PasswordAlgorithm;


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
    // Verifies that an existing email prevents user creation and repository saves.
    @Test
    void createUserWithExistingEmailReturnsConflictResult() {
        UserRepository repository = mock(UserRepository.class);
        UserRequest request = new UserRequest("existing@example.com", "password123", true);
        when(repository.existsByEmail("existing@example.com")).thenReturn(true);
        UserService service = new UserService(repository);

        ServiceResult<UserEntity> result = service.createUser(request);

        assertEquals("Email already exists", result.message());
        assertEquals(HttpStatus.CONFLICT, result.status());
        assertNull(result.data());
        verify(repository, never()).save(any());
    }

    // Verifies that a unique email creates, saves, and returns a new user entity.
    @Test
    void createUserWithUniqueEmailCreatesAndSavesEntity() {
        UserRepository repository = mock(UserRepository.class);
        UserRequest request = new UserRequest("new@example.com", "password123", false);
        when(repository.existsByEmail("new@example.com")).thenReturn(false);
        when(repository.saveAndFlush(any(UserEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        UserService service = new UserService(repository);

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

    // Verifies that find-all returns repository users with a successful status.
    @Test
    void findAllUsersReturnsRepositoryListAndSuccessStatus() {
        UserRepository repository = mock(UserRepository.class);
        UserEntity first = new UserEntity();
        UserEntity second = new UserEntity();
        when(repository.findAll()).thenReturn(List.of(first, second));

        ServiceResult<List<UserEntity>> result = new UserService(repository).findAllUsers();

        assertEquals(HttpStatus.OK, result.status());
        assertEquals("Users retrieved successfully", result.message());
        assertEquals(2, result.data().size());
        assertSame(first, result.data().get(0));
        assertSame(second, result.data().get(1));
    }

    // Verifies that find-all returns not-found when the repository has no users.
    @Test
    void findAllUsersReturnsNotFoundWhenEmpty() {
        UserRepository repository = mock(UserRepository.class);
        when(repository.findAll()).thenReturn(List.of());

        ServiceResult<List<UserEntity>> result = new UserService(repository).findAllUsers();

        assertEquals(HttpStatus.NOT_FOUND, result.status());
        assertEquals("No users found", result.message());
        assertNull(result.data());
    }

    // Verifies that selected-column queries return projection results and success status.
    @Test
    void findAllUsersWithSelectedColumnsReturnsRepositoryProjectionAndStatus() {
        UserRepository repository = mock(UserRepository.class);
        UserSelectColumn projection = mock(UserSelectColumn.class);
        when(repository.findAllUsersWithSelectedColumns()).thenReturn(List.of(projection));

        ServiceResult<List<UserSelectColumn>> result =
                new UserService(repository).findAllUsersWithSelectedColumns();

        assertEquals(HttpStatus.OK, result.status());
        assertEquals(1, result.data().size());
        assertSame(projection, result.data().get(0));
    }

    // Verifies that selected-column retrieval returns not-found when the repository is empty.
    @Test
    void findAllUsersWithSelectedColumnsReturnsNotFoundWhenEmpty() {
        UserRepository repository = mock(UserRepository.class);
        when(repository.findAllUsersWithSelectedColumns()).thenReturn(List.of());

        ServiceResult<List<UserSelectColumn>> result =
                new UserService(repository).findAllUsersWithSelectedColumns();

        assertEquals(HttpStatus.NOT_FOUND, result.status());
        assertEquals("No users found", result.message());
        assertNull(result.data());
    }

    // Verifies that limited and offset pagination delegates to the repository projection query.
    @Test
    void findAllUsersWithLimitAndOffsetReturnsRepositoryProjection() {
        UserRepository repository = mock(UserRepository.class);
        UserSelectColumn projection = mock(UserSelectColumn.class);
        Pageable pageable = PageRequest.of(1, 10);
        when(repository.findAllUsersWithLimitAndOffset(pageable)).thenReturn(List.of(projection));

        ServiceResult<List<UserSelectColumn>> result =
                new UserService(repository).findAllUsersWithLimitAndOffset(pageable);

        assertEquals(HttpStatus.OK, result.status());
        assertEquals(List.of(projection), result.data());
        verify(repository).findAllUsersWithLimitAndOffset(pageable);
    }

    // Verifies that paginated projection queries return not-found when no users exist.
    @Test
    void findAllUsersWithLimitAndOffsetReturnsNotFoundWhenEmpty() {
        UserRepository repository = mock(UserRepository.class);
        Pageable pageable = PageRequest.of(1, 10);
        when(repository.findAllUsersWithLimitAndOffset(pageable)).thenReturn(List.of());

        ServiceResult<List<UserSelectColumn>> result =
                new UserService(repository).findAllUsersWithLimitAndOffset(pageable);

        assertEquals(HttpStatus.NOT_FOUND, result.status());
        assertEquals("No users found", result.message());
        assertNull(result.data());
    }

    // Verifies that a user is returned when the repository finds the requested ID.
    @Test
    void findUserByIdReturnsExistingUser() {
        UserRepository repository = mock(UserRepository.class);
        UserEntity user = new UserEntity();
        user.setId("user-1");
        when(repository.findById("user-1")).thenReturn(Optional.of(user));

        ServiceResult<UserEntity> result = new UserService(repository).findUserById("user-1");

        assertSame(user, result.data());
        assertEquals(HttpStatus.OK, result.status());
    }

    // Verifies that a missing user ID produces a not-found service result.
    @Test
    void findUserByIdReturnsNotFoundWhenUserDoesNotExist() {
        UserRepository repository = mock(UserRepository.class);
        when(repository.findById("missing-id")).thenReturn(Optional.empty());

        ServiceResult<UserEntity> result = new UserService(repository).findUserById("missing-id");

        assertEquals(HttpStatus.NOT_FOUND, result.status());
        assertEquals("User not found with id: missing-id", result.message());
        assertNull(result.data());
    }

    // Verifies that selected-column lookup returns the repository projection.
    @Test
    void findUserByIdWithSelectedColumnsReturnsProjection() {
        UserRepository repository = mock(UserRepository.class);
        UserSelectColumn projection = mock(UserSelectColumn.class);
        when(repository.findUserByIdWithSelectedColumns("user-1")).thenReturn(Optional.of(projection));

        ServiceResult<UserSelectColumn> result =
                new UserService(repository).findUserByIdWithSelectedColumns("user-1");

        assertSame(projection, result.data());
        assertEquals(HttpStatus.OK, result.status());
    }

    // Verifies that selected-column lookup returns not-found for a missing ID.
    @Test
    void findUserByIdWithSelectedColumnsReturnsNotFoundWhenUserDoesNotExist() {
        UserRepository repository = mock(UserRepository.class);
        when(repository.findUserByIdWithSelectedColumns("missing-id")).thenReturn(Optional.empty());

        ServiceResult<UserSelectColumn> result =
                new UserService(repository).findUserByIdWithSelectedColumns("missing-id");

        assertEquals(HttpStatus.NOT_FOUND, result.status());
        assertEquals("User not found with id: missing-id", result.message());
    }

    // Verifies that an existing user is updated with all supplied fields and saved.
    @Test
    void updateUserUpdatesFieldsAndSavesUser() {
        UserRepository repository = mock(UserRepository.class);
        UserEntity user = new UserEntity();
        user.setEmail("old@example.com");
        user.setPassword("oldpassword");
        user.setIsActive(true);
        UserRequest request = new UserRequest("new@example.com", "newpassword", false);
        when(repository.findByIdForUpdate("user-1")).thenReturn(Optional.of(user));
        when(repository.existsByEmailAndIdNot("new@example.com", "user-1")).thenReturn(false);
        when(repository.saveAndFlush(user)).thenReturn(user);

        ServiceResult<UserEntity> result = new UserService(repository).updateUser("user-1", request);

        assertSame(user, result.data());
        assertEquals(HttpStatus.OK, result.status());
        assertEquals("new@example.com", user.getEmail());
        assertTrue(new PasswordHashService().matches("newpassword", user.getPassword()));
        assertFalse(user.getIsActive());
        assertNotNull(user.getUpdatedAt());
        verify(repository).saveAndFlush(user);
    }

    // Verifies that an email used by another user prevents an update.
    @Test
    void updateUserReturnsConflictWhenAnotherUserUsesEmail() {
        UserRepository repository = mock(UserRepository.class);
        UserEntity user = new UserEntity();
        user.setId("user-1");
        user.setEmail("old@example.com");
        UserRequest request = new UserRequest("taken@example.com", "newpassword", true);
        when(repository.findByIdForUpdate("user-1")).thenReturn(Optional.of(user));
        when(repository.existsByEmailAndIdNot("taken@example.com", "user-1")).thenReturn(true);

        ServiceResult<UserEntity> result = new UserService(repository).updateUser("user-1", request);

        assertEquals(HttpStatus.CONFLICT, result.status());
        assertEquals("Email already exists", result.message());
        assertNull(result.data());
        assertEquals("old@example.com", user.getEmail());
        verify(repository, never()).saveAndFlush(any());
    }

    // Verifies that a user may keep its own email during an update.
    @Test
    void updateAllowsKeepingOwnEmail() {
        UserRepository repository = mock(UserRepository.class);
        UserEntity user = new UserEntity();
        user.setId("user-1");
        user.setEmail("current@example.com");
        user.setPassword("oldpassword");
        user.setIsActive(true);
        UserRequest request = new UserRequest("current@example.com", "newpassword", null);
        when(repository.findByIdForUpdate("user-1")).thenReturn(Optional.of(user));
        when(repository.existsByEmailAndIdNot("current@example.com", "user-1")).thenReturn(false);
        when(repository.saveAndFlush(user)).thenReturn(user);

        ServiceResult<UserEntity> result = new UserService(repository).updateUser("user-1", request);

        assertSame(user, result.data());
        assertEquals(HttpStatus.OK, result.status());
        assertEquals("current@example.com", user.getEmail());
        assertTrue(new PasswordHashService().matches("newpassword", user.getPassword()));
        assertTrue(user.getIsActive());
        assertNotNull(user.getUpdatedAt());
        verify(repository).existsByEmailAndIdNot("current@example.com", "user-1");
        verify(repository).saveAndFlush(user);
    }

    // Verifies that updating a missing user returns not-found without saving.
    @Test
    void updateUserReturnsNotFoundWhenUserDoesNotExist() {
        UserRepository repository = mock(UserRepository.class);
        when(repository.findByIdForUpdate("missing-id")).thenReturn(Optional.empty());

        ServiceResult<UserEntity> result =
                new UserService(repository).updateUser("missing-id", null);

        assertEquals(HttpStatus.NOT_FOUND, result.status());
        assertEquals("User not found with id: missing-id", result.message());
        verify(repository, never()).saveAndFlush(any());
    }

    // Verifies that deletion sets the soft-delete timestamp and saves the user.
    @Test
    void deleteUserSetsDeletedAtAndSavesUser() {
        UserRepository repository = mock(UserRepository.class);
        UserEntity user = new UserEntity();
        when(repository.findByIdForUpdate("user-1")).thenReturn(Optional.of(user));

        ServiceResult<UserEntity> result = new UserService(repository).deleteUser("user-1");

        assertEquals(HttpStatus.OK, result.status());
        assertEquals("User deleted successfully", result.message());
        assertNotNull(user.getDeletedAt());
        verify(repository).saveAndFlush(user);
    }

    // Verifies that deleting a missing user returns not-found without saving.
    @Test
    void deleteUserReturnsNotFoundWhenUserDoesNotExist() {
        UserRepository repository = mock(UserRepository.class);
        when(repository.findByIdForUpdate("missing-id")).thenReturn(Optional.empty());

        ServiceResult<UserEntity> result = new UserService(repository).deleteUser("missing-id");

        assertEquals(HttpStatus.NOT_FOUND, result.status());
        assertEquals("User not found with id: missing-id", result.message());
        verify(repository, never()).saveAndFlush(any());
    }

    // Verifies that valid active-user credentials return the user without rewriting a current hash.
    @Test
    void loginReturnsUserWhenCredentialsAreValid() {
        UserRepository repository = mock(UserRepository.class);
        PasswordHashService passwordHashService = new PasswordHashService("ARGON2");
        UserEntity user = new UserEntity();
        user.setId("user-1");
        user.setEmail("user@example.com");
        user.setPassword(passwordHashService.encode("password123"));
        user.setIsActive(true);
        when(repository.findByEmail("user@example.com")).thenReturn(Optional.of(user));

        ServiceResult<UserEntity> result = new UserService(repository, passwordHashService)
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
        user.setId("user-1");
        user.setEmail("legacy@example.com");
        user.setPassword(passwordHashService.encode(
                "password123", PasswordAlgorithm.MD5));
        user.setIsActive(true);
        when(repository.findByEmail("legacy@example.com")).thenReturn(Optional.of(user));
        when(repository.saveAndFlush(user)).thenReturn(user);

        ServiceResult<UserEntity> result = new UserService(repository, passwordHashService)
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
        PasswordHashService passwordHashService = new PasswordHashService("ARGON2");
        UserEntity user = new UserEntity();
        user.setEmail("user@example.com");
        user.setPassword(passwordHashService.encode("password123"));
        user.setIsActive(true);
        when(repository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(repository.findByEmail("missing@example.com")).thenReturn(Optional.empty());
        UserService service = new UserService(repository, passwordHashService);

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
        verify(repository, never()).saveAndFlush(any());
    }

    // Verifies that inactive accounts cannot log in even when the password is correct.
    @Test
    void loginRejectsInactiveUser() {
        UserRepository repository = mock(UserRepository.class);
        PasswordHashService passwordHashService = new PasswordHashService("ARGON2");
        UserEntity user = new UserEntity();
        user.setEmail("inactive@example.com");
        user.setPassword(passwordHashService.encode("password123"));
        user.setIsActive(false);
        when(repository.findByEmail("inactive@example.com")).thenReturn(Optional.of(user));

        ServiceResult<UserEntity> result = new UserService(repository, passwordHashService)
                .login(new LoginRequest("inactive@example.com", "password123"));

        assertEquals(HttpStatus.UNAUTHORIZED, result.status());
        assertEquals("Invalid email or password", result.message());
        verify(repository, never()).saveAndFlush(any());
    }
}
