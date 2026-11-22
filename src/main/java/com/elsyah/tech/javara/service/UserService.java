package com.elsyah.tech.javara.service;

import java.util.UUID;
import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;

import com.elsyah.tech.javara.entity.UserEntity;
import com.elsyah.tech.javara.repository.UserRepository;
import com.elsyah.tech.javara.request.UserRequest;
import com.elsyah.tech.javara.request.LoginRequest;
import com.elsyah.tech.javara.projection.UserSelectColumn;
import com.elsyah.tech.javara.utils.security.PasswordHashService;

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
    /**
     * The UserRepository instance used for database operations related to users.
     */
    private final UserRepository repository;
    private final PasswordHashService passwordHashService;

    /**
     * Constructs a new UserService with the specified UserRepository.
     *
     * @param repository the UserRepository to be used for database operations
     */
    @Autowired
    public UserService(UserRepository repository, PasswordHashService passwordHashService) {
        this.repository = repository;
        this.passwordHashService = passwordHashService;
    }

    public UserService(UserRepository repository) {
        this(repository, new PasswordHashService());
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
    @Transactional (readOnly = false)
    public ServiceResult<UserEntity> createUser(UserRequest request) {
        /* Check if the email already exists */
        if (repository.existsByEmail(request.email())) {
            return new ServiceResult<>(null, HttpStatus.CONFLICT, "Email already exists");
        }

        /* Create a new UserEntity and set its properties */
        UserEntity userEntity = new UserEntity();

        /* Set the properties for the new user */
        userEntity.setId(UUID.randomUUID().toString());
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
     * =========================== RETRIEVES ALL USERS ============================
     * ============================================================================
     *
     * This method retrieves all users from the database and returns them as a list of UserEntity objects.
     * 
     * @return the users with a status and message, or a not-found result when there are no users
     */
    @Transactional (readOnly = true)
    public ServiceResult<List<UserEntity>> findAllUsers() {
        List<UserEntity> users = repository.findAll();

        return users.isEmpty()
                ? new ServiceResult<>(null, HttpStatus.NOT_FOUND, "No users found")
                : new ServiceResult<>(users, HttpStatus.OK, "Users retrieved successfully");
    }

    /**
     * ============================================================================
     * ================ RETRIEVES ALL USERS WITH SELECTED COLUMNS =================
     * ============================================================================
     *
     * This method retrieves all users from the database, 
     * but only returns the selected columns defined in the UserSelectColumn projection.
     * 
     * @return the projections with a status and message, or a not-found result when there are no users
     */
    @Transactional (readOnly = true)
    public ServiceResult<List<UserSelectColumn>> findAllUsersWithSelectedColumns() {
        List<UserSelectColumn> users = repository.findAllUsersWithSelectedColumns();

        return users.isEmpty()
                ? new ServiceResult<>(null, HttpStatus.NOT_FOUND, "No users found")
                : new ServiceResult<>(users, HttpStatus.OK, "Users retrieved successfully");
    }

    /**
     * ============================================================================
     * ================ RETRIEVES ALL USERS WITH LIMIT AND OFFSET =================
     * ============================================================================
     *
     * This method retrieves all users with pagination support.
     * 
     * @param pageable the Pageable object containing pagination information
     * @return the projections with a status and message, or a not-found result when there are no users
     */
    @Transactional (readOnly = true)
    public ServiceResult<List<UserSelectColumn>> findAllUsersWithLimitAndOffset(Pageable pageable) {
        List<UserSelectColumn> users = repository.findAllUsersWithLimitAndOffset(pageable);

        return users.isEmpty()
                ? new ServiceResult<>(null, HttpStatus.NOT_FOUND, "No users found")
                : new ServiceResult<>(users, HttpStatus.OK, "Users retrieved successfully");
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
    public ServiceResult<UserEntity> findUserById(String id) {
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
    public ServiceResult<UserSelectColumn> findUserByIdWithSelectedColumns(String id) {
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
    @Transactional (readOnly = false)
    public ServiceResult<UserEntity> updateUser(String id, UserRequest request) {
        Optional<UserEntity> optionalUser = repository.findByIdForUpdate(id);
        if (optionalUser.isPresent()) {
            UserEntity userEntity = optionalUser.get();

            if (repository.existsByEmailAndIdNot(request.email(), id)) {
                return new ServiceResult<>(null, HttpStatus.CONFLICT, "Email already exists");
            }

            userEntity.setEmail(request.email());
            userEntity.setPassword(passwordHashService.encode(request.password()));
            if (request.isActive() != null) {
                userEntity.setIsActive(request.isActive());
            }
            userEntity.setUpdatedAt(LocalDateTime.now());

            UserEntity updatedUser = repository.saveAndFlush(userEntity);
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
    @Transactional (readOnly = false)
    public ServiceResult<UserEntity> deleteUser(String id) {
        Optional<UserEntity> optionalUser = repository.findByIdForUpdate(id);
        if (optionalUser.isPresent()) {
            UserEntity deletedUser = optionalUser.get();

            deletedUser.setDeletedAt(LocalDateTime.now());
            
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
        Optional<UserEntity> optionalUser = repository.findByEmail(request.email());
        if (optionalUser.isEmpty()) {
            return invalidCredentials();
        }

        UserEntity user = optionalUser.get();
        if (!Boolean.TRUE.equals(user.getIsActive())
                || !passwordHashService.matches(request.password(), user.getPassword())) {
            return invalidCredentials();
        }

        if (passwordHashService.requiresUpgrade(user.getPassword())) {
            user.setPassword(passwordHashService.encode(request.password()));
            user.setUpdatedAt(LocalDateTime.now());
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
