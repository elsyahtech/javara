package com.elsyah.tech.javara.request.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EmailChangeRequest(
        @NotBlank(message = "currentPassword is required")
        @Size(max = 72, message = "currentPassword must be at most 72 characters")
        String currentPassword,
        @NotBlank(message = "newEmail is required")
        @Email(message = "newEmail is not valid")
        @Size(max = 255, message = "newEmail must be at most 255 characters")
        String newEmail) {}
