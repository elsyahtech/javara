package com.elsyah.tech.javara.request.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank(message = "email is required")
        @Email(message = "email is not valid")
        @Size(max = 255)
        String email,

        @NotBlank(message = "password is required")
        @Size(max = 1024, message = "password must be at most 1024 characters")
        String password
) {}
