package com.elsyah.tech.javara.request.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SessionReauthRequest(
        @NotBlank(message = "currentPassword is required")
        @Size(max = 72, message = "currentPassword must be at most 72 characters")
        String currentPassword) {}
