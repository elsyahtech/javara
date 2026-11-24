package com.elsyah.tech.javara.controller.user;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import com.elsyah.tech.javara.service.user.UserService;
import com.elsyah.tech.javara.request.user.UserRequest;
import com.elsyah.tech.javara.request.user.UserUpdateRequest;
import com.elsyah.tech.javara.response.common.ApiResponse;
import com.elsyah.tech.javara.response.user.UserResponse;
import com.elsyah.tech.javara.entity.user.UserEntity;
import com.elsyah.tech.javara.projection.user.UserSelectColumn;
import com.elsyah.tech.javara.service.common.ServiceResult;
import com.elsyah.tech.javara.response.user.UserResponseWithSelectColumn;

/**
 * UserController is a REST controller that handles HTTP requests related to user operations.
 * It provides endpoints for creating, retrieving, updating, and deleting users.
 * The controller interacts with the UserService to perform business logic and database operations.
 * It uses request and response DTOs to encapsulate user data for API communication.
 * @author Elmansyah
 * @version 1.0.0
 * @since 2026-11-18
 * @contact https://www.linkedin.com/in/elmansyah
 */
@RestController 
@RequestMapping("/api/v1/users")
public class UserController {
    private final UserService service;

    public UserController(UserService service) {
        this.service = service;
    }

    /**
     * ============================================================================
     * ============================ CREATES A NEW USER ============================
     * ============================================================================
     * The request is validated, and if successful, a new user is created and 
     * returned in the response.
     *
     * @param request the UserRequest containing user details
     * @return a response with the wrapped ApiResponse containing the created user or an error message
     * 
     * CURL Command:
     * curl -X POST http://localhost:8080/api/v1/users \
     *      -H "Content-Type: application/json" \
     *      -d '{"email": "example@example.com", "password": "dummypassword"}'
     * 
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<UserResponse>> createUser(@Valid @RequestBody UserRequest request) {
        ServiceResult<UserEntity> result = service.createUser(request);
        HttpStatus status = result.status();

        if (result.isError()) {
            return ResponseEntity.status(status)
                    .body(ApiResponse.failure(status, result.message()));
        }

        ApiResponse<UserResponse> response = ApiResponse.success(
                status,
                result.message(),
                UserResponse.from(result.data()));
        return ResponseEntity.status(status).body(response);
    }

    /**
     * ============================================================================
     * ============================= FINDS USERS PAGE ============================
     * ============================================================================
     * Retrieves a page of users with only the fields intended for API responses.
     * 
     * @param page zero-based page index, defaults to 0
     * @param size page size from 1 to 100, defaults to 20
     * @return a wrapped page containing users and pagination metadata
     * 
     * CURL Command:
     * curl -X GET "http://localhost:8080/api/v1/users?page=0&size=20"
     * 
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<UserResponseWithSelectColumn>>> findAllUsers(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        if (page < 0) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.failure(HttpStatus.BAD_REQUEST, "Page must be zero or greater"));
        }
        if (size < 1 || size > 100) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.failure(
                            HttpStatus.BAD_REQUEST,
                            "Page size must be between 1 and 100"));
        }

        ServiceResult<Page<UserSelectColumn>> result =
                service.findAllUsers(PageRequest.of(page, size));
        HttpStatus status = result.status();

        if (result.isError()) {
            return ResponseEntity.status(status)
                    .body(ApiResponse.failure(status, result.message()));
        }

        Page<UserSelectColumn> usersPage = result.data();
        List<UserResponseWithSelectColumn> data = usersPage.getContent().stream()
                .map(UserResponseWithSelectColumn::from)
                .toList();
        ApiResponse<List<UserResponseWithSelectColumn>> response = ApiResponse
                .success(status, result.message(), data)
                .withField("count", data.size())
                .withField("page", usersPage.getNumber())
                .withField("size", usersPage.getSize())
                .withField("totalElements", usersPage.getTotalElements())
                .withField("totalPages", usersPage.getTotalPages())
                .withField("hasNext", usersPage.hasNext())
                .withField("hasPrevious", usersPage.hasPrevious());

        return ResponseEntity.status(status).body(response);
    }

    /**
     * ============================================================================
     * ============================= FINDS USER BY ID =============================
     * ============================================================================
     * Retrieves a user by their ID.
     *
     * @param id the ID of the user to retrieve
     * @return a wrapped response containing the user or a not-found error
     * 
     * CURL Command:
     * curl -X GET http://localhost:8080/api/v1/users/{id}
     * 
     */
    @GetMapping("/{id:[0-9a-fA-F-]+}")
    @PreAuthorize("hasRole('ADMIN') or #p0.toString() == authentication.name")
    public ResponseEntity<ApiResponse<UserResponse>> findUserById(@PathVariable UUID id) {
        ServiceResult<UserEntity> result = service.findUserById(id);
        HttpStatus status = result.status();

        if (result.isError()) {
            return ResponseEntity.status(status)
                    .body(ApiResponse.failure(status, result.message()));
        }

        ApiResponse<UserResponse> response = ApiResponse.success(
                status,
                result.message(),
                UserResponse.from(result.data()));
        return ResponseEntity.status(status).body(response);
    }

    /**
     * ============================================================================
     * ================== FINDS USER BY ID WITH SELECTED COLUMNS ==================
     * ============================================================================
     * Retrieves a user by their ID with only the selected columns.
     * 
     * @param id the ID of the user to retrieve
     * @return a ResponseEntity containing the UserResponseWithSelectColumn representing the found user, or a 404 Not Found response if not found
     * 
     * CURL Command:
     * curl -X GET http://localhost:8080/api/v1/users/findUserByIdWithSelectedColumns/{id}
     * 
     */
    @GetMapping("/findUserByIdWithSelectedColumns/{id:[0-9a-fA-F-]+}")
    @PreAuthorize("hasRole('ADMIN') or #p0.toString() == authentication.name")
    public ResponseEntity<ApiResponse<UserResponseWithSelectColumn>> findUserByIdWithSelectedColumns(
            @PathVariable UUID id) {
        ServiceResult<UserSelectColumn> result = service.findUserByIdWithSelectedColumns(id);
        HttpStatus status = result.status();

        if (result.isError()) {
            return ResponseEntity.status(status)
                    .body(ApiResponse.failure(status, result.message()));
        }

        ApiResponse<UserResponseWithSelectColumn> response = ApiResponse.success(
                status,
                result.message(),
                UserResponseWithSelectColumn.from(result.data()));
        return ResponseEntity.status(status).body(response);
    }

    /**
     * ============================================================================
     * ============================== UPDATES A USER ==============================
     * ============================================================================
     * Updates an existing user by their ID.
     *
     * @param id the ID of the user to update
     * @param request the UserUpdateRequest containing mutable profile details
     * @return a wrapped response containing the service status, message, and updated user
     * 
     * CURL Command:
     * curl -X PUT http://localhost:8080/api/v1/users/{id} \
     *      -H "Content-Type: application/json" \
     *      -d '{"email": "newemail@example.com", "isActive": true}'
     * 
     */
    @PutMapping("/{id:[0-9a-fA-F-]+}")
    @PreAuthorize("hasRole('ADMIN') or #p0.toString() == authentication.name")
    public ResponseEntity<ApiResponse<UserResponse>> updateUser(
            @PathVariable UUID id,
            @Valid @RequestBody UserUpdateRequest request
        ) {
        ServiceResult<UserEntity> result = service.updateUser(id, request);
        HttpStatus status = result.status();

        if (result.isError()) {
            return ResponseEntity.status(status)
                    .body(ApiResponse.failure(status, result.message()));
        }

        ApiResponse<UserResponse> response = ApiResponse.success(
                status,
                result.message(),
                UserResponse.from(result.data()));
        return ResponseEntity.status(status).body(response);
    }

    /**
     * ============================================================================
     * ============================== DELETES A USER ==============================
     * ============================================================================
     * Deletes an existing user by their ID.
     *
     * @param id the ID of the user to delete
     * @return a ResponseEntity with a wrapped response containing the service status, message, and deleted user
     * 
     * CURL Command:
     * curl -X DELETE http://localhost:8080/api/v1/users/{id}
     * 
     */
    @DeleteMapping("/{id:[0-9a-fA-F-]+}")
    @PreAuthorize("hasRole('ADMIN') or #p0.toString() == authentication.name")
    public ResponseEntity<ApiResponse<UserResponse>> deleteUser(@PathVariable UUID id) {
        ServiceResult<UserEntity> result = service.deleteUser(id);
        HttpStatus status = result.status();

        if (result.isError()) {
            return ResponseEntity.status(status)
                    .body(ApiResponse.failure(status, result.message()));
        }

        ApiResponse<UserResponse> response = ApiResponse.success(
                status,
                result.message(),
                UserResponse.from(result.data()));
        return ResponseEntity.status(status).body(response);
    }

}

