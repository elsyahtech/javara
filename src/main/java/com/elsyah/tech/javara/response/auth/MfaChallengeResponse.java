package com.elsyah.tech.javara.response.auth;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.elsyah.tech.javara.entity.auth.MfaMethod;

public record MfaChallengeResponse(
        UUID challengeId,
        MfaMethod method,
        List<MfaMethod> availableMethods,
        String action,
        Object options,
        Instant expiresAt) {}
