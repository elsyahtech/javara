package com.elsyah.tech.javara.controller.auth;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.elsyah.tech.javara.request.auth.SessionReauthRequest;
import com.elsyah.tech.javara.response.auth.SessionResponse;
import com.elsyah.tech.javara.response.common.ApiResponse;
import com.elsyah.tech.javara.service.auth.SessionManagementService;
import com.elsyah.tech.javara.service.common.ServiceResult;

@RestController
@RequestMapping("/api/v1/auth/sessions")
@ConditionalOnProperty(
        prefix = "app.features",
        name = "auth-api",
        havingValue = "true",
        matchIfMissing = true)
public class SessionController {
    private final SessionManagementService sessionManagementService;

    public SessionController(SessionManagementService sessionManagementService) {
        this.sessionManagementService = sessionManagementService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<SessionResponse>>> listSessions(
            @AuthenticationPrincipal Jwt principal) {
        UUID userId = UUID.fromString(principal.getSubject());
        UUID currentSessionId = UUID.fromString(principal.getClaimAsString("sid"));
        List<SessionResponse> sessions =
                sessionManagementService.listSessions(userId, currentSessionId);
        return ResponseEntity.ok(ApiResponse.success(
                HttpStatus.OK, "Sessions retrieved successfully", sessions));
    }

    @PostMapping("/{sessionId}/terminate")
    public ResponseEntity<ApiResponse<Integer>> terminateSession(
            @AuthenticationPrincipal Jwt principal,
            @PathVariable UUID sessionId,
            @Valid @RequestBody SessionReauthRequest request) {
        ServiceResult<Integer> result = sessionManagementService.terminateSession(
                UUID.fromString(principal.getSubject()),
                sessionId,
                request.currentPassword());
        return response(result);
    }

    @PostMapping("/terminate-all")
    public ResponseEntity<ApiResponse<Integer>> terminateAllSessions(
            @AuthenticationPrincipal Jwt principal,
            @Valid @RequestBody SessionReauthRequest request) {
        ServiceResult<Integer> result = sessionManagementService.terminateAllSessions(
                UUID.fromString(principal.getSubject()),
                request.currentPassword());
        return response(result);
    }

    private static ResponseEntity<ApiResponse<Integer>> response(
            ServiceResult<Integer> result) {
        if (result.isError()) {
            return ResponseEntity.status(result.status())
                    .body(ApiResponse.failure(result.status(), result.message()));
        }
        return ResponseEntity.status(result.status())
                .body(ApiResponse.success(result.status(), result.message(), result.data()));
    }
}
