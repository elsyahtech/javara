package com.elsyah.tech.javara.response.user;

import java.util.List;

public record RoleResponse(
        String key,
        String displayName,
        String description,
        boolean systemManaged,
        List<String> permissions) {}
