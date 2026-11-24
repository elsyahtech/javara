package com.elsyah.tech.javara.request.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordChangeRequest(
        @NotBlank(message = "currentPassword is required")
        @Size(max = 72, message = "currentPassword must be at most 72 characters")
        String currentPassword,
        @NotBlank(message = "newPassword is required")
        @Size(min = 8, max = 72, message = "newPassword must be 8-72 characters")
        String newPassword) {}
