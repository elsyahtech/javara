package com.elsyah.tech.javara.request.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Null;
import jakarta.validation.constraints.Size;

public record UserUpdateRequest(
        @NotBlank(message = "email is required")
        @Email(message = "email is not valid")
        @Size(max = 255)
        String email,
        Boolean isActive,
        @Null(message = "Use the dedicated password-change endpoint")
        String password) {
    public UserUpdateRequest(String email, Boolean isActive) {
        this(email, isActive, null);
    }
}
