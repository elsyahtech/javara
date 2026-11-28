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

import com.elsyah.tech.javara.request.auth.AdminEmergencyPasswordResetRequest;
import com.elsyah.tech.javara.request.auth.SupportRecoveryAuthorizationRequest;
import com.elsyah.tech.javara.response.common.ApiResponse;
import com.elsyah.tech.javara.service.auth.PasswordResetService;
import com.elsyah.tech.javara.service.common.ServiceResult;

@RestController
@RequestMapping("/api/v1/admin/users")
@ConditionalOnProperty(
        prefix = "app.features",
        name = "auth-api",
        havingValue = "true",
        matchIfMissing = true)
public class AdminPasswordResetController {
    private final PasswordResetService passwordResetService;

    public AdminPasswordResetController(PasswordResetService passwordResetService) {
        this.passwordResetService = passwordResetService;
    }

    @PostMapping("/{userId}/security/force-password-reset")
    @PreAuthorize("hasAuthority('password-resets:manage')")
    public ResponseEntity<ApiResponse<Void>> forceEmergencyPasswordReset(
            @AuthenticationPrincipal Jwt principal,
            @PathVariable UUID userId,
            @Valid @RequestBody AdminEmergencyPasswordResetRequest request) {
        ServiceResult<Void> result = passwordResetService.forceEmergencyReset(
                UUID.fromString(principal.getSubject()),
                userId,
                request.currentPassword(),
                request.reason(),
                request.notificationMessage());
        if (result.isError()) {
            return ResponseEntity.status(result.status())
                    .body(ApiResponse.failure(result.status(), result.message()));
        }
        return ResponseEntity.status(result.status())
                .body(ApiResponse.success(result.status(), result.message(), null));
    }

    @PostMapping("/{userId}/password/recovery/authorize")
    @PreAuthorize("hasAuthority('password-resets:manage')")
    public ResponseEntity<ApiResponse<Void>> authorizeSupportRecovery(
            @AuthenticationPrincipal Jwt principal,
            @PathVariable UUID userId,
            @Valid @RequestBody SupportRecoveryAuthorizationRequest request) {
        ServiceResult<Void> result = passwordResetService.authorizeSupportRecovery(
                UUID.fromString(principal.getSubject()),
                userId,
                request.currentPassword(),
                request.supportCaseReference());
        if (result.isError()) {
            return ResponseEntity.status(result.status())
                    .body(ApiResponse.failure(result.status(), result.message()));
        }
        return ResponseEntity.status(result.status())
                .body(ApiResponse.success(result.status(), result.message(), null));
    }
}
