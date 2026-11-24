package com.elsyah.tech.javara.service.user;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;

import com.elsyah.tech.javara.entity.user.UserEntity;
import com.elsyah.tech.javara.projection.user.UserSelectColumn;
import com.elsyah.tech.javara.repository.auth.RefreshTokenRepository;
import com.elsyah.tech.javara.repository.user.UserRepository;
import com.elsyah.tech.javara.request.user.UserRequest;
import com.elsyah.tech.javara.request.auth.LoginRequest;
import com.elsyah.tech.javara.utils.security.PasswordHashService;
import com.elsyah.tech.javara.service.common.ServiceResult;

/**
 * Service class for managing user-related operations.
 * This class provides methods to create users and handle user-related business logic.
 * It interacts with the UserRepository to perform database operations.
 * The createUser method checks for email uniqueness and returns a conflict result if the email already exists.
 * The class is annotated with @Service to indicate that it's a service component in the Spring context.
 * @author Elmansyah
 * @version 1.0.0
 * @since 2026-11-18
 * @contact https://www.linkedin.com/in/elmansyah
 */
@Service
public class UserService {
    private static final int MAX_PAGE_SIZE = 100;

    /**
     * The UserRepository is used for database operations related to users.
     */
    private final UserRepository repository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordHashService passwordHashService;
    private final Clock clock;

    /**
     * Constructs a new UserService with the specified UserRepository.
     *
     * @param repository the UserRepository to be used for database operations
     */
    public UserService(
            UserRepository repository,
            PasswordHashService passwordHashService,
            RefreshTokenRepository refreshTokenRepository,
            Clock clock) {
        this.repository = repository;
        this.passwordHashService = passwordHashService;
        this.refreshTokenRepository = refreshTokenRepository;
        this.clock = clock;
    }

    /**
     * ============================================================================
     * ============================ CREATES A NEW USER ============================
     * ============================================================================
     * This method checks if the email already exists in the database and returns a conflict result if it does.
     * If the email is unique, it creates a new UserEntity, sets its properties, 
     * and saves it to the database.
     * 
     *
     * @param request the UserRequest containing user details
     * @return a result containing the created user or a conflict status and message
     */
    @Transactional
    public ServiceResult<UserEntity> createUser(UserRequest request) {
        /* Check if the email already exists */
        if (repository.existsByEmail(request.email())) {
            return new ServiceResult<>(null, HttpStatus.CONFLICT, "Email already exists");
        }

        /* Create a new UserEntity and set its properties */
        UserEntity userEntity = new UserEntity();

        /* Set the properties for the new user */
        userEntity.setId(UUID.randomUUID());
        userEntity.setEmail(request.email());
        userEntity.setPassword(passwordHashService.encode(request.password()));
        userEntity.setIsActive(true);

        UserEntity createdUser = repository.saveAndFlush(userEntity);
        return new ServiceResult<>(
                createdUser,
                HttpStatus.CREATED,
                "User created successfully with id: " + createdUser.getId());
    }

    /**
     * ============================================================================
     * =========================== RETRIEVES USERS PAGE ==========================
     * ============================================================================
     *
     * Retrieves a page of users using only fields exposed by the user projection.
     *
     * @param pageable the requested page and size
     * @return a successful result containing the page, including an empty page when there are no users
     */
    @Transactional (readOnly = true)
    public ServiceResult<Page<UserSelectColumn>> findAllUsers(Pageable pageable) {
        if (pageable.getPageNumber() < 0
                || pageable.getPageSize() < 1
                || pageable.getPageSize() > MAX_PAGE_SIZE) {
            return new ServiceResult<>(
                    null,
                    HttpStatus.BAD_REQUEST,
                    "Page size must be between 1 and " + MAX_PAGE_SIZE);
        }

        Pageable orderedPageable = PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id")));
        Page<UserSelectColumn> users = repository.findAllUsersPage(orderedPageable);
        return new ServiceResult<>(users, HttpStatus.OK, "Users retrieved successfully");
    }

    /**
     * ============================================================================
     * ========================== RETRIEVES A USER BY ID ==========================
     * ============================================================================
     * 
     * This method retrieves a user by their ID and reports a 404 result if it does not exist.
     *
     * @param id the ID of the user to retrieve
     * @return a result containing the user or a not-found status and message
     */
    @Transactional (readOnly = true)
    public ServiceResult<UserEntity> findUserById(UUID id) {
        return repository.findById(id)
                .map(user -> new ServiceResult<>(user, HttpStatus.OK, "User found"))
                .orElseGet(() -> new ServiceResult<>(
                        null,
                        HttpStatus.NOT_FOUND,
                        "User not found with id: " + id));
    }

    /**
     * ============================================================================
     * =============== RETRIEVES A USER BY ID WITH SELECTED COLUMNS ===============
     * ============================================================================
     *
     * This method retrieves a user by their ID, returning only the specified columns.
     * Reports a 404 result if no user exists with the given ID.
     * 
     * @param id the ID of the user to retrieve
     * @return a result containing the projection or a not-found status and message
     */
    @Transactional (readOnly = true)
    public ServiceResult<UserSelectColumn> findUserByIdWithSelectedColumns(UUID id) {
        return repository.findUserByIdWithSelectedColumns(id)
                .map(user -> new ServiceResult<>(user, HttpStatus.OK, "User found"))
                .orElseGet(() -> new ServiceResult<>(
                        null,
                        HttpStatus.NOT_FOUND,
                        "User not found with id: " + id));
    }

    /**
     * ============================================================================
     * ============================== UPDATES A USER ==============================
     * ============================================================================
     * 
     * This method updates an existing user's information based on the provided ID and UserRequest.
     *
     * @param id the ID of the user to update
     * @param request the UserRequest object containing the updated user data
     * @return a result containing the updated user or a not-found status and message
     */
    @Transactional
    public ServiceResult<UserEntity> updateUser(UUID id, UserRequest request) {
        Optional<UserEntity> optionalUser = repository.findByIdForUpdate(id);
        if (optionalUser.isPresent()) {
            UserEntity userEntity = optionalUser.get();

            if (repository.existsByEmailAndIdNot(request.email(), id)) {
                return new ServiceResult<>(null, HttpStatus.CONFLICT, "Email already exists");
            }

            boolean passwordChanged =
                    !passwordHashService.matches(request.password(), userEntity.getPassword());
            userEntity.setEmail(request.email());
            userEntity.setPassword(passwordHashService.encode(request.password()));
            if (request.isActive() != null) {
                userEntity.setIsActive(request.isActive());
            }
            Instant now = clock.instant();
            userEntity.setUpdatedAt(now);

            UserEntity updatedUser = repository.saveAndFlush(userEntity);
            if (passwordChanged) {
                refreshTokenRepository.revokeActiveTokensForUser(id, now);
            }
            return new ServiceResult<>(updatedUser, HttpStatus.OK, "User updated successfully");
        }

        return new ServiceResult<>(null, HttpStatus.NOT_FOUND, "User not found with id: " + id);
    }

    /**
     * ============================================================================
     * ============================== DELETES A USER ==============================
     * ============================================================================
     * 
     * This method performs a soft delete of a user by their ID. Instead of removing the user from the database, 
     * it sets the deletedAt timestamp to the current time, indicating that the user has been deleted. 
     * Reports a 404 result if the user with the specified ID does not exist.
     * 
     * @param id the ID of the user to delete
     * @return a result containing the operation status and message
     */
    @Transactional
    public ServiceResult<UserEntity> deleteUser(UUID id) {
        Optional<UserEntity> optionalUser = repository.findByIdForUpdate(id);
        if (optionalUser.isPresent()) {
            UserEntity deletedUser = optionalUser.get();

            deletedUser.setDeletedAt(clock.instant());
            
            repository.saveAndFlush(deletedUser);
            return new ServiceResult<>(deletedUser, HttpStatus.OK, "User deleted successfully");
        }

        return new ServiceResult<>(null, HttpStatus.NOT_FOUND, "User not found with id: " + id);
    }

    /**
     * ============================================================================
     * ================================ USER LOGIN ================================
     * ============================================================================ 
     * 
     * This method performs
     * 
     * @param request
     * @return
     */
    @Transactional
    public ServiceResult<UserEntity> login(LoginRequest request) {
        Optional<UserEntity> optionalUser = repository.findByEmailForUpdate(request.email());
        if (optionalUser.isEmpty()) {
            passwordHashService.matchesDummyPassword(request.password());
            return invalidCredentials();
        }

        UserEntity user = optionalUser.get();
        boolean passwordMatches =
                passwordHashService.matches(request.password(), user.getPassword());
        if (!Boolean.TRUE.equals(user.getIsActive()) || !passwordMatches) {
            return invalidCredentials();
        }

        if (passwordHashService.requiresUpgrade(user.getPassword())) {
            user.setPassword(passwordHashService.encode(request.password()));
            user.setUpdatedAt(clock.instant());
            user = repository.saveAndFlush(user);
        }

        return new ServiceResult<>(user, HttpStatus.OK, "Login successful");
    }

    private static ServiceResult<UserEntity> invalidCredentials() {
        return new ServiceResult<>(
                null,
                HttpStatus.UNAUTHORIZED,
                "Invalid email or password");
    }
}
