package com.elsyah.tech.javara.response.auth;

import java.time.Instant;
import java.util.Set;

public record ProvisionedServiceClientResponse(
        String clientId,
        String clientSecret,
        String name,
        Set<String> scopes,
        Instant createdAt) {
}
