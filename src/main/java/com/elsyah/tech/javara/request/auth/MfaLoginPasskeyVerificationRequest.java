package com.elsyah.tech.javara.request.auth;

import java.util.UUID;

import tools.jackson.databind.JsonNode;

import jakarta.validation.constraints.NotNull;

public record MfaLoginPasskeyVerificationRequest(
        @NotNull UUID challengeId,
        @NotNull JsonNode credential) {}
