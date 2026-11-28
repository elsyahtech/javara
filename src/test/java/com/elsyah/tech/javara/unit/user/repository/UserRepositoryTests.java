package com.elsyah.tech.javara.unit.user.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;

import com.elsyah.tech.javara.entity.user.UserEntity;
import com.elsyah.tech.javara.request.user.UserRequest;
import com.elsyah.tech.javara.request.user.UserUpdateRequest;
import com.elsyah.tech.javara.service.user.UserService;
import com.elsyah.tech.javara.repository.user.UserRepository;
import com.elsyah.tech.javara.testsupport.PostgresRedisTestSupport;

/**
 * 
 * UserRepositoryTests
 * This class contains unit tests for the UserRepository class, which is responsible for managing user entities in the database.
 * It uses Spring Boot's testing framework to set up an application context and perform database operations.
 * The tests cover various scenarios, including excluding soft-deleted users from finders and projection queries, 
 * allowing the creation of users with emails of soft-deleted users, checking email existence for active users, and 
 * updating user passwords while preserving the active status.
 * Each test method interacts with the UserRepository and UserService to perform database operations and assert the expected outcomes.
 * The tests ensure that the UserRepository class behaves as expected and enforces the defined business rules for user management.
 * The class is annotated with @SpringBootTest to indicate that it is a Spring Boot test class, and @Transactional to 
 * ensure that each test method runs within a transaction that is rolled back after the test completes.
 * 
 * @author Elmansyah
 * @version 1.0.0
 * @since 2026-11-18
 * @contact https://www.linkedin.com/in/elmansyah
 */
@SpringBootTest(properties = {
        "app.security.jwt.signing-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "app.security.jwt.transport=BEARER",
        "app.security.bootstrap-admin.enabled=false"
})
@Transactional
class UserRepositoryTests extends PostgresRedisTestSupport {

    @Autowired
    private UserRepository repository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private UserService userService;

    @Test
    void demoUserIsNotSeeded() {
        assertTrue(repository.findByEmail("user-dummy@example.com").isEmpty());
    }

    @Test
    void migratedSchemaUsesNativeUuidAndTimezoneAwareTimestamps() {
        assertEquals("uuid", columnDataType("javara_users", "id"));
        assertEquals("timestamp with time zone", columnDataType("javara_users", "created_at"));
        assertEquals("uuid", columnDataType("javara_user_sessions", "user_id"));
        assertEquals("uuid", columnDataType("javara_refresh_tokens", "session_id"));
        assertEquals("timestamp with time zone", columnDataType("javara_refresh_tokens", "expires_at"));
    }

    // Verifies that the database rejects an active duplicate email through the unique partial index.
    @Test
    void rejectsDuplicateEmailForAnotherActiveUser() {
        String email = "duplicate-active-" + UUID.randomUUID() + "@example.com";
        repository.saveAndFlush(user(
                UUID.randomUUID(), email, null));

        org.junit.jupiter.api.Assertions.assertThrows(
                DataIntegrityViolationException.class,
                () -> repository.saveAndFlush(user(
                        UUID.randomUUID(), email, null)));
    }

    // Verifies that a soft-deleted user is hidden from ID, entity, and projection lookups.
    @Test
    void softDeletedUserIsNotReturnedByFinders() {
        UUID deletedId = UUID.randomUUID();
        repository.saveAndFlush(user(
                deletedId,
                "deleted-" + deletedId + "@example.com",
                Instant.now()));
        entityManager.clear();

        assertFalse(repository.findById(deletedId).isPresent());
        assertFalse(repository.findUserByIdWithSelectedColumns(deletedId).isPresent());
        assertFalse(repository.findAll().stream()
                .anyMatch(foundUser -> deletedId.equals(foundUser.getId())));
    }

    // Verifies that soft-deleted users are excluded from entity and projection finders.
    @Test
    void excludesSoftDeletedUsersFromFindersAndProjectionQueries() {
        UUID activeId = UUID.randomUUID();
        UUID deletedId = UUID.randomUUID();

        UserEntity activeUser = user(activeId, "active-" + activeId + "@example.com", null);
        UserEntity deletedUser = user(
                deletedId,
                "deleted-" + deletedId + "@example.com",
                Instant.now());
        repository.saveAll(List.of(activeUser, deletedUser));
        repository.flush();
        entityManager.clear();

        assertTrue(repository.findById(activeId).isPresent());
        assertTrue(repository.findById(deletedId).isEmpty());
        assertFalse(repository.findAll().stream().anyMatch(user -> deletedId.equals(user.getId())));
        assertFalse(repository.findAllUsersPage(PageRequest.of(0, 100)).getContent().stream()
                .anyMatch(user -> deletedId.equals(user.getId())));
    }

    @Test
    void pagedUserProjectionReturnsTotalCountAndExcludesSoftDeletedUsers() {
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        repository.saveAll(List.of(
                user(firstId, "page-first-" + firstId + "@example.com", null),
                user(secondId, "page-second-" + secondId + "@example.com", null),
                user(
                        UUID.randomUUID(),
                        "page-deleted-" + UUID.randomUUID() + "@example.com",
                        Instant.now())));
        repository.flush();
        entityManager.clear();

        var page = repository.findAllUsersPage(PageRequest.of(
                0,
                1,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id"))));

        assertEquals(1, page.getContent().size());
        assertEquals(2, page.getTotalElements());
        assertEquals(2, page.getTotalPages());
    }

    // Verifies that an active user can reuse an email belonging to a soft-deleted user.
    @Test
    void allowsCreatingUserWithEmailOfSoftDeletedUser() {
        String email = "reused-" + UUID.randomUUID() + "@example.com";
        UserEntity deletedUser = user(UUID.randomUUID(), email, Instant.now());
        repository.saveAndFlush(deletedUser);
        entityManager.clear();

        var result = userService.createUser(new UserRequest(email, "unique-password-123", true));

        assertEquals(HttpStatus.CREATED, result.status());
        assertTrue(repository.findByEmail(email).isPresent());
        assertFalse(repository.findByEmail(email).orElseThrow().getId().equals(deletedUser.getId()));
    }

    // Verifies that email uniqueness checks exclude the current user's own ID.
    @Test
    void emailExistsForAnotherActiveUserButNotForSameUser() {
        String email = "duplicate-" + UUID.randomUUID() + "@example.com";
        UUID userId = UUID.randomUUID();
        repository.saveAndFlush(user(userId, email, null));

        assertTrue(repository.existsByEmailAndIdNot(email, UUID.randomUUID()));
        assertFalse(repository.existsByEmailAndIdNot(email, userId));
    }

    // Verifies that a profile update preserves status and never changes the password.
    @Test
    void updatingProfileWithoutIsActivePreservesActiveStatusAndPassword() {
        String email = "password-update-" + UUID.randomUUID() + "@example.com";
        UUID userId = UUID.randomUUID();
        UserEntity existing = user(userId, email, null);
        repository.saveAndFlush(existing);
        String originalPassword = existing.getPassword();
        entityManager.clear();

        var result = userService.updateUser(userId, new UserUpdateRequest(email, null));

        assertEquals(HttpStatus.OK, result.status());
        assertTrue(result.data().getIsActive());
        assertEquals(originalPassword, result.data().getPassword());
        assertNotNull(result.data().getUpdatedAt());
    }

    private static UserEntity user(UUID id, String email, Instant deletedAt) {
        UserEntity user = new UserEntity();
        user.setId(id);
        user.setEmail(email);
        user.setPassword("password123");
        user.setIsActive(true);
        user.setDeletedAt(deletedAt);
        return user;
    }

    private String columnDataType(String table, String column) {
        return (String) entityManager.createNativeQuery("""
                SELECT data_type
                FROM information_schema.columns
                WHERE table_schema = current_schema()
                  AND table_name = :table
                  AND column_name = :column
                """)
                .setParameter("table", table)
                .setParameter("column", column)
                .getSingleResult();
    }
}
