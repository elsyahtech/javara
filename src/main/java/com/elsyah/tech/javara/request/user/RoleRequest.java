package com.elsyah.tech.javara.request.user;

import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RoleRequest(
        @NotBlank
        @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,63}$")
        String key,
        @NotBlank
        @Size(max = 120)
        String displayName,
        @Size(max = 500)
        String description,
        @NotEmpty
        @Size(max = 100)
        List<@NotBlank @Size(max = 100) String> permissions) {
    public RoleRequest {
        permissions = permissions == null
                ? List.of()
                : Collections.unmodifiableList(new ArrayList<>(permissions));
    }
}
