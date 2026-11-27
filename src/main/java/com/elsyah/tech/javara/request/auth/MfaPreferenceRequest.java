package com.elsyah.tech.javara.request.auth;

import com.elsyah.tech.javara.entity.auth.MfaMethod;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record MfaPreferenceRequest(
        @NotNull MfaMethod method,
        @NotBlank @Size(max = 1024) String currentPassword) {}
