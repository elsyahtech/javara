package com.elsyah.tech.javara.controller.user;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.elsyah.tech.javara.exception.ApiException;
import com.elsyah.tech.javara.request.user.RoleRequest;
import com.elsyah.tech.javara.request.user.UserRoleAssignmentRequest;
import com.elsyah.tech.javara.response.common.ApiResponse;
import com.elsyah.tech.javara.response.user.RoleResponse;
import com.elsyah.tech.javara.service.user.UserRoleService;

@RestController
@RequestMapping("/api/v1")
@ConditionalOnProperty(
        prefix = "app.features",
        name = "auth-api",
        havingValue = "true",
        matchIfMissing = true)
public class UserRoleController {
    private final UserRoleService userRoleService;

    public UserRoleController(UserRoleService userRoleService) {
        this.userRoleService = userRoleService;
    }

    @GetMapping("/roles")
    @PreAuthorize("hasAuthority('roles:manage')")
    public ApiResponse<List<RoleResponse>> findAllRoles() {
        return ApiResponse.success(
                HttpStatus.OK,
                "Roles retrieved successfully",
                userRoleService.findAllRoles());
    }

    @GetMapping("/roles/permissions")
    @PreAuthorize("hasAuthority('roles:manage')")
    public ApiResponse<List<String>> findAllPermissions() {
        return ApiResponse.success(
                HttpStatus.OK,
                "Permissions retrieved successfully",
                userRoleService.findAllPermissions());
    }

    @PostMapping("/roles")
    @PreAuthorize("hasAuthority('roles:manage')")
    public ResponseEntity<ApiResponse<RoleResponse>> createRole(
            @Valid @RequestBody RoleRequest request) {
        RoleResponse role = userRoleService.createRole(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.success(HttpStatus.CREATED, "Role created successfully", role));
    }

    @PutMapping("/roles/{roleKey}")
    @PreAuthorize("hasAuthority('roles:manage')")
    public ApiResponse<RoleResponse> updateRole(
            @PathVariable String roleKey,
            @Valid @RequestBody RoleRequest request) {
        if (!roleKey.equals(request.key())) {
            throw new ApiException(
                    "Role key cannot be changed",
                    HttpStatus.BAD_REQUEST);
        }
        return ApiResponse.success(
                HttpStatus.OK,
                "Role updated successfully",
                userRoleService.updateRole(roleKey, request));
    }

    @DeleteMapping("/roles/{roleKey}")
    @PreAuthorize("hasAuthority('roles:manage')")
    public ResponseEntity<Void> deleteRole(@PathVariable String roleKey) {
        userRoleService.deleteRole(roleKey);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/users/{userId:[0-9a-fA-F-]+}/roles")
    @PreAuthorize("hasAuthority('users:read') or #p0.toString() == authentication.name")
    public ApiResponse<List<String>> findUserRoles(@PathVariable UUID userId) {
        return ApiResponse.success(
                HttpStatus.OK,
                "User roles retrieved successfully",
                userRoleService.rolesForUser(userId));
    }

    @PutMapping("/users/{userId:[0-9a-fA-F-]+}/roles")
    @PreAuthorize("hasAuthority('roles:manage')")
    public ApiResponse<List<String>> replaceUserRoles(
            @PathVariable UUID userId,
            @Valid @RequestBody UserRoleAssignmentRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        UUID actorUserId;
        try {
            actorUserId = UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException exception) {
            throw new ApiException("User authentication is required", HttpStatus.UNAUTHORIZED);
        }
        userRoleService.replaceUserRoles(userId, actorUserId, request.roles());
        return ApiResponse.success(
                HttpStatus.OK,
                "User roles updated successfully",
                userRoleService.rolesForUser(userId));
    }
}
