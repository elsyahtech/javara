package com.elsyah.tech.javara.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * UserRequest is a record that represents the request payload for creating a new user.
 * It contains the user's email and password, along with validation constraints to ensure data integrity.
 * The email field must be a valid email format and cannot be blank, while the password must be between 8 and 72 characters long.
 * This class is used in the UserService to validate incoming user creation requests.
 * @author Elmansyah
 * @version 1.0.0
 * @since 2026-11-18
 * @contact https://www.linkedin.com/in/elmansyah
 */
public record UserRequest(
    @NotBlank (message = "email is required")
    @Email (message = "email is not valid")
    @Size (max = 255)
    String email,

    @NotBlank (message = "password is required")
    @Size (min = 8, max = 72, message = "password must be 8-72 characters")
    String password,
    Boolean isActive
) {}
