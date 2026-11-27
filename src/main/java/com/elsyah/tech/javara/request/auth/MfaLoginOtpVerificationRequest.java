package com.elsyah.tech.javara.request.auth;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record MfaLoginOtpVerificationRequest(
        @NotNull UUID challengeId,
        @NotBlank @Pattern(regexp = "[0-9]{8}") String code) {}
