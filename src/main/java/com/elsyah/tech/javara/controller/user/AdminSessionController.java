package com.elsyah.tech.javara.controller.user;

import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.elsyah.tech.javara.request.auth.SessionReauthRequest;
import com.elsyah.tech.javara.request.auth.EmailChangeRequest;
import com.elsyah.tech.javara.response.common.ApiResponse;
import com.elsyah.tech.javara.service.auth.SessionManagementService;
import com.elsyah.tech.javara.service.common.ServiceResult;
import com.elsyah.tech.javara.service.user.UserService;

@RestController
@RequestMapping("/api/v1/admin/users")
@ConditionalOnProperty(
        prefix = "app.features",
        name = "auth-api",
        havingValue = "true",
        matchIfMissing = true)
public class AdminSessionController {
    private final SessionManagementService sessionManagementService;
    private final UserService userService;

    public AdminSessionController(
            SessionManagementService sessionManagementService,
            UserService userService) {
        this.sessionManagementService = sessionManagementService;
        this.userService = userService;
    }

    @PostMapping("/{userId}/sessions/terminate")
    @PreAuthorize("hasAuthority('sessions:manage')")
    public ResponseEntity<ApiResponse<Integer>> terminateUserSessions(
            @AuthenticationPrincipal Jwt principal,
            @PathVariable UUID userId,
            @Valid @RequestBody SessionReauthRequest request) {
        ServiceResult<Integer> result = sessionManagementService.terminateUserSessions(
                UUID.fromString(principal.getSubject()),
                userId,
                request.currentPassword());
        if (result.isError()) {
            return ResponseEntity.status(result.status())
                    .body(ApiResponse.failure(result.status(), result.message()));
        }
        return ResponseEntity.status(result.status())
                .body(ApiResponse.success(result.status(), result.message(), result.data()));
    }

    @PostMapping("/{userId}/email/change")
    @PreAuthorize("hasAuthority('sessions:manage')")
    public ResponseEntity<ApiResponse<Void>> requestUserEmailChange(
            @AuthenticationPrincipal Jwt principal,
            @PathVariable UUID userId,
            @Valid @RequestBody EmailChangeRequest request) {
        ServiceResult<Void> result = userService.requestEmailChange(
                UUID.fromString(principal.getSubject()),
                userId,
                request.currentPassword(),
                request.newEmail(),
                true);
        if (result.isError()) {
            return ResponseEntity.status(result.status())
                    .body(ApiResponse.failure(result.status(), result.message()));
        }
        return ResponseEntity.status(result.status())
                .body(ApiResponse.success(result.status(), result.message(), null));
    }
}
