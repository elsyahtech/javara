package com.elsyah.tech.javara.request.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EmailVerificationResendRequest(
        @NotBlank(message = "email is required")
        @Email(message = "email is not valid")
        @Size(max = 255)
        String email) {}
