package com.elsyah.tech.javara.request.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminEmergencyPasswordResetRequest(
        @NotBlank(message = "currentPassword is required")
        @Size(max = 72, message = "currentPassword must be at most 72 characters")
        String currentPassword,
        @NotBlank(message = "reason is required")
        @Size(max = 500, message = "reason must be at most 500 characters")
        String reason,
        @NotBlank(message = "notificationMessage is required")
        @Size(max = 2000, message = "notificationMessage must be at most 2000 characters")
        String notificationMessage) {}
