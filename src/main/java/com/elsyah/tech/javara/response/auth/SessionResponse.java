package com.elsyah.tech.javara.response.auth;

import java.time.Instant;
import java.util.UUID;

public record SessionResponse(
        UUID sessionId,
        Instant startedAt,
        Instant lastActivityAt,
        Instant expiresAt,
        boolean current) {}
