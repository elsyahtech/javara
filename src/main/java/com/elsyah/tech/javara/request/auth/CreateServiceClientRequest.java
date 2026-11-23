package com.elsyah.tech.javara.request.auth;

import java.util.Set;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

public record CreateServiceClientRequest(
        @NotBlank @Size(max = 100) String name,
        @NotEmpty Set<@NotBlank @Size(max = 100) String> scopes) {
}
