package com.elsyah.tech.javara.request.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MfaPasskeyEnrollmentOptionsRequest(
        @NotBlank @Size(max = 1024) String currentPassword) {}
