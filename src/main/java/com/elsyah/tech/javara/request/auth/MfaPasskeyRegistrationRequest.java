package com.elsyah.tech.javara.request.auth;

import java.util.UUID;

import tools.jackson.databind.JsonNode;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record MfaPasskeyRegistrationRequest(
        @NotNull UUID challengeId,
        @NotBlank @Size(max = 100) String label,
        @NotNull JsonNode credential) {}
