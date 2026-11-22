package com.elsyah.tech.javara.controller;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.RequestParam;

import com.elsyah.tech.javara.service.UserService;
import com.elsyah.tech.javara.request.UserRequest;
import com.elsyah.tech.javara.response.ApiResponse;
import com.elsyah.tech.javara.response.UserResponse;
import com.elsyah.tech.javara.entity.UserEntity;
import com.elsyah.tech.javara.service.ServiceResult;
import com.elsyah.tech.javara.projection.UserSelectColumn;
import com.elsyah.tech.javara.response.UserResponseWithSelectColumn;

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
     * ============================= FINDS ALL USERS ==============================
     * ============================================================================
     * Retrieves a list of all users.
     * 
     * @return a wrapped response containing the users and result metadata
     * 
     * CURL Command:
     * curl -X GET http://localhost:8080/api/v1/users
     * 
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<UserResponse>>> findAllUsers() {
        ServiceResult<List<UserEntity>> result = service.findAllUsers();
        HttpStatus status = result.status();

        if (result.isError()) {
            return ResponseEntity.status(status)
                    .body(ApiResponse.failure(status, result.message()));
        }

        List<UserResponse> data = result.data().stream().map(UserResponse::from).toList();
        ApiResponse<List<UserResponse>> response = ApiResponse
                .success(status, result.message(), data)
                .withField("count", data.size());

        return ResponseEntity.status(status).body(response);
    }

    /**
     * ============================================================================
     * ================== FINDS ALL USERS WITH SELECTED COLUMNS ===================
     * ============================================================================
     * Retrieves a list of all users with only the selected columns.
     *
     * @return a list of UserResponseWithSelectColumn representing all users with selected columns
     * 
     * CURL Command:
     * curl -X GET http://localhost:8080/api/v1/users/findAllWithSelectedColumn
     * 
     */
    @GetMapping("/findAllWithSelectedColumn")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus (HttpStatus.OK)
    public ResponseEntity<ApiResponse<List<UserResponseWithSelectColumn>>> findAllUsersWithSelectedColumns() {
        ServiceResult<List<UserSelectColumn>> result = service.findAllUsersWithSelectedColumns();
        HttpStatus status = result.status();

        if (result.isError()) {
            return ResponseEntity.status(status)
                    .body(ApiResponse.failure(status, result.message()));
        }

        List<UserResponseWithSelectColumn> data = result.data().stream()
                .map(UserResponseWithSelectColumn::from)
                .toList();
        ApiResponse<List<UserResponseWithSelectColumn>> response = ApiResponse
                .success(status, result.message(), data)
                .withField("count", data.size());
        return ResponseEntity.status(status).body(response);
    }

    /**
     * ============================================================================
     * =================== FINDS ALL USERS WITH LIMIT AND OFFSET ==================
     * ============================================================================
     * Retrieves a list of users with pagination support using limit and offset.
     * 
     * @param pageable the Pageable object containing pagination information
     * @return a list of UserResponseWithSelectColumn representing users with selected columns and pagination
     * 
     * CURL Command:
     * curl -X GET "http://localhost:8080/api/v1/users/findAllWithLimitAndOffset?page=0&size=10"
     * 
     */
    @GetMapping("/findAllWithLimitAndOffset")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus (HttpStatus.OK)
    public ResponseEntity<ApiResponse<List<UserResponseWithSelectColumn>>> findAllUsersWithLimitAndOffset(
            @PageableDefault(size = 20) Pageable pageable,
            @RequestParam(name = "size", required = false) Integer requestedSize) {
        if (requestedSize != null
                && (requestedSize < 1 || requestedSize != pageable.getPageSize())) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.failure(
                            HttpStatus.BAD_REQUEST,
                            "Page size must be between 1 and 100"));
        }

        ServiceResult<List<UserSelectColumn>> result = service.findAllUsersWithLimitAndOffset(pageable);
        HttpStatus status = result.status();

        if (result.isError()) {
            return ResponseEntity.status(status)
                    .body(ApiResponse.failure(status, result.message()));
        }

        List<UserResponseWithSelectColumn> data = result.data().stream()
                .map(UserResponseWithSelectColumn::from)
                .toList();
        ApiResponse<List<UserResponseWithSelectColumn>> response = ApiResponse
                .success(status, result.message(), data)
                .withField("count", data.size());
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
    @PreAuthorize("hasRole('ADMIN') or #p0 == authentication.name")
    public ResponseEntity<ApiResponse<UserResponse>> findUserById(@PathVariable String id) {
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
    @PreAuthorize("hasRole('ADMIN') or #p0 == authentication.name")
    public ResponseEntity<ApiResponse<UserResponseWithSelectColumn>> findUserByIdWithSelectedColumns(@PathVariable String id) {
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
     * @param request the UserRequest containing updated user details
     * @return a wrapped response containing the service status, message, and updated user
     * 
     * CURL Command:
     * curl -X PUT http://localhost:8080/api/v1/users/{id} \
     *      -H "Content-Type: application/json" \
     *      -d '{"email": "newemail@example.com", "password": "newpassword", "isActive": true}'
     * 
     */
    @PutMapping("/{id:[0-9a-fA-F-]+}")
    @PreAuthorize("hasRole('ADMIN') or #p0 == authentication.name")
    public ResponseEntity<ApiResponse<UserResponse>> updateUser(
            @PathVariable String id,
            @Valid @RequestBody UserRequest request
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
    @PreAuthorize("hasRole('ADMIN') or #p0 == authentication.name")
    public ResponseEntity<ApiResponse<UserResponse>> deleteUser(@PathVariable String id) {
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

