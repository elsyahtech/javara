package com.elsyah.tech.javara.service.user;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;

import com.elsyah.tech.javara.entity.user.UserEntity;
import com.elsyah.tech.javara.projection.user.UserSelectColumn;
import com.elsyah.tech.javara.repository.user.UserRepository;
import com.elsyah.tech.javara.service.auth.SessionRevocationService;
import com.elsyah.tech.javara.service.auth.SuspiciousAuthenticationService;
import com.elsyah.tech.javara.service.email.EmailNotificationService;
import com.elsyah.tech.javara.service.email.EmailVerificationService;
import com.elsyah.tech.javara.request.user.UserRequest;
import com.elsyah.tech.javara.request.user.UserUpdateRequest;
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
@ConditionalOnProperty(
        prefix = "app.features",
        name = "auth-api",
        havingValue = "true",
        matchIfMissing = true)
public class UserService {
    private static final int MAX_PAGE_SIZE = 100;

    /**
     * The UserRepository is used for database operations related to users.
     */
    private final UserRepository repository;
    private final SessionRevocationService sessionRevocationService;
    private final PasswordHashService passwordHashService;
    private final PasswordPolicyService passwordPolicyService;
    private final UserRoleService userRoleService;
    private final EmailNotificationService emailNotificationService;
    private final EmailVerificationService emailVerificationService;
    private final SuspiciousAuthenticationService suspiciousAuthenticationService;
    private final Clock clock;

    /**
     * Constructs a new UserService with the specified UserRepository.
     *
     * @param repository the UserRepository to be used for database operations
     */
    public UserService(
            UserRepository repository,
            PasswordHashService passwordHashService,
            SessionRevocationService sessionRevocationService,
            PasswordPolicyService passwordPolicyService,
            UserRoleService userRoleService,
            EmailNotificationService emailNotificationService,
            EmailVerificationService emailVerificationService,
            SuspiciousAuthenticationService suspiciousAuthenticationService,
            Clock clock) {
        this.repository = repository;
        this.passwordHashService = passwordHashService;
        this.sessionRevocationService = sessionRevocationService;
        this.passwordPolicyService = passwordPolicyService;
        this.userRoleService = userRoleService;
        this.emailNotificationService = emailNotificationService;
        this.emailVerificationService = emailVerificationService;
        this.suspiciousAuthenticationService = suspiciousAuthenticationService;
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
        return createUser(request, true);
    }

    @Transactional
    public ServiceResult<UserEntity> registerUser(UserRequest request) {
        if (!emailNotificationService.isEnabled()) {
            return new ServiceResult<>(
                    null,
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Registration is temporarily unavailable because email verification is not configured");
        }
        ServiceResult<UserEntity> result = createUser(request, false);
        if (!result.isError()) {
            emailVerificationService.issueRegistrationToken(result.data());
        }
        return result;
    }

    private ServiceResult<UserEntity> createUser(UserRequest request, boolean emailVerified) {
        if (!passwordPolicyService.isPasswordAllowed(request.password())) {
            return rejectedPassword();
        }
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
        userEntity.setEmailVerified(emailVerified);

        UserEntity createdUser = repository.saveAndFlush(userEntity);
        userRoleService.assignInitialRoles(createdUser.getId(), List.of("USER"));
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
    public ServiceResult<UserEntity> updateUser(UUID id, UserUpdateRequest request) {
        if (request.password() != null) {
            return new ServiceResult<>(
                    null,
                    HttpStatus.BAD_REQUEST,
                    "Use the dedicated password-change endpoint");
        }
        if (request.isActive() != null) {
            repository.lockDefaultTenantForUpdate();
        }
        Optional<UserEntity> optionalUser = repository.findByIdForUpdate(id);
        if (optionalUser.isPresent()) {
            UserEntity userEntity = optionalUser.get();

            if (!userEntity.getEmail().equals(request.email())) {
                return new ServiceResult<>(
                        null,
                        HttpStatus.BAD_REQUEST,
                        "Use the dedicated email-change endpoint to update an email address");
            }
            if (repository.existsByEmailAndIdNot(request.email(), id)) {
                return new ServiceResult<>(null, HttpStatus.CONFLICT, "Email already exists");
            }

            boolean accountDisabled = Boolean.TRUE.equals(userEntity.getIsActive())
                    && Boolean.FALSE.equals(request.isActive());
            boolean accountStatusChanged = request.isActive() != null
                    && !request.isActive().equals(userEntity.getIsActive());
            if (accountDisabled) {
                if (repository.hasPermission(id, "roles:manage")
                        && repository.countActiveRoleManagers() <= 1) {
                    return new ServiceResult<>(
                            null,
                            HttpStatus.CONFLICT,
                            "The final role manager cannot be disabled");
                }
            }
            if (request.isActive() != null) {
                userEntity.setIsActive(request.isActive());
            }
            Instant now = clock.instant();
            userEntity.setUpdatedAt(now);

            UserEntity updatedUser = repository.saveAndFlush(userEntity);
            if (accountStatusChanged) {
                userRoleService.recordAuthorizationChange(id);
            }
            if (accountDisabled) {
                sessionRevocationService.revokeAllForUser(id, now);
            }
            return new ServiceResult<>(updatedUser, HttpStatus.OK, "User updated successfully");
        }

        return new ServiceResult<>(null, HttpStatus.NOT_FOUND, "User not found with id: " + id);
    }

    @Transactional
    public ServiceResult<Void> requestEmailChange(
            UUID actingUserId,
            UUID targetUserId,
            String currentPassword,
            String newEmail,
            boolean administratorRequest) {
        Optional<UserEntity> actingUser = repository.findByIdForUpdate(actingUserId);
        if (actingUser.isEmpty()
                || !Boolean.TRUE.equals(actingUser.get().getIsActive())
                || currentPassword == null
                || !passwordHashService.matches(
                        currentPassword, actingUser.get().getPassword())
                || (administratorRequest
                        ? !repository.hasPermission(actingUserId, "users:manage")
                        : !actingUserId.equals(targetUserId))) {
            return new ServiceResult<>(
                    null, HttpStatus.UNAUTHORIZED, "Re-authentication failed");
        }
        if (!emailNotificationService.isEnabled()) {
            return new ServiceResult<>(
                    null,
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Email change is temporarily unavailable because email delivery is not configured");
        }

        Optional<UserEntity> targetUser = repository.findByIdForUpdate(targetUserId);
        if (targetUser.isEmpty()
                || targetUser.get().getDeletedAt() != null
                || !Boolean.TRUE.equals(targetUser.get().getIsActive())) {
            return new ServiceResult<>(null, HttpStatus.NOT_FOUND, "User not found");
        }
        UserEntity user = targetUser.get();
        if (newEmail.equals(user.getEmail())) {
            return new ServiceResult<>(
                    null, HttpStatus.BAD_REQUEST, "New email must be different");
        }
        if (repository.existsByEmailAndIdNot(newEmail, targetUserId)
                || repository.existsByPendingEmailAndIdNot(newEmail, targetUserId)) {
            return new ServiceResult<>(
                    null, HttpStatus.CONFLICT, "Email is already in use");
        }

        user.setPendingEmail(newEmail);
        user.setUpdatedAt(clock.instant());
        repository.saveAndFlush(user);
        emailVerificationService.issueEmailChangeToken(user, newEmail);
        return new ServiceResult<>(
                null,
                HttpStatus.ACCEPTED,
                "Check the new email address to confirm the change");
    }

    @Transactional
    public ServiceResult<Void> changePassword(
            UUID userId,
            String currentPassword,
            String newPassword) {
        Optional<UserEntity> optionalUser = repository.findByIdForUpdate(userId);
        if (optionalUser.isEmpty()
                || !Boolean.TRUE.equals(optionalUser.get().getIsActive())
                || optionalUser.get().getDeletedAt() != null
                || currentPassword == null
                || !passwordHashService.matches(
                        currentPassword, optionalUser.get().getPassword())) {
            return new ServiceResult<>(
                    null, HttpStatus.UNAUTHORIZED, "Re-authentication failed");
        }

        UserEntity user = optionalUser.get();
        if (passwordHashService.matches(newPassword, user.getPassword())) {
            return new ServiceResult<>(
                    null, HttpStatus.BAD_REQUEST, "New password must be different");
        }
        if (passwordHashService.matchesAny(
                newPassword, user.getCompromisedPasswordHashes())) {
            return new ServiceResult<>(
                    null,
                    HttpStatus.BAD_REQUEST,
                    "This password was previously invalidated during an emergency reset. "
                            + "Choose a different password that you have not used before.");
        }
        if (!passwordPolicyService.isPasswordAllowed(newPassword)) {
            return rejectedPassword();
        }

        Instant now = clock.instant();
        user.setPassword(passwordHashService.encode(newPassword));
        user.setUpdatedAt(now);
        repository.saveAndFlush(user);
        sessionRevocationService.revokeAllForUser(userId, now);
        emailNotificationService.passwordChanged(user.getEmail());
        suspiciousAuthenticationService.securityChange(user, "password was changed");
        return new ServiceResult<>(null, HttpStatus.OK, "Password changed successfully");
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
        repository.lockDefaultTenantForUpdate();
        Optional<UserEntity> optionalUser = repository.findByIdForUpdate(id);
        if (optionalUser.isPresent()) {
            UserEntity deletedUser = optionalUser.get();
            if (repository.hasPermission(id, "roles:manage")
                    && repository.countActiveRoleManagers() <= 1) {
                return new ServiceResult<>(
                        null,
                        HttpStatus.CONFLICT,
                        "The final role manager cannot be deleted");
            }
            Instant now = clock.instant();
            deletedUser.setDeletedAt(now);
            
            repository.saveAndFlush(deletedUser);
            userRoleService.recordAuthorizationChange(id);
            sessionRevocationService.revokeAllForUser(id, now);
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
        if (!Boolean.TRUE.equals(user.getIsActive())
                || !user.isEmailVerified()
                || user.isPasswordResetRequired()
                || !passwordMatches) {
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

    private static <T> ServiceResult<T> rejectedPassword() {
        return new ServiceResult<>(
                null,
                HttpStatus.BAD_REQUEST,
                "Choose a password that is not commonly used or known to be compromised");
    }
}
