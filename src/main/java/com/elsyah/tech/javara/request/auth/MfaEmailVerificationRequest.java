package com.elsyah.tech.javara.request.auth;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.NotNull;

public record MfaEmailVerificationRequest(
        @NotNull UUID challengeId,
        @NotBlank @Pattern(regexp = "[0-9]{8}") String code) {}
