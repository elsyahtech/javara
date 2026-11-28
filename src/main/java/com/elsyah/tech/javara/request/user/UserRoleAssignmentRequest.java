package com.elsyah.tech.javara.request.user;

import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UserRoleAssignmentRequest(
        @NotEmpty
        @Size(max = 50)
        List<@Pattern(regexp = "^[A-Z][A-Z0-9_]{1,63}$") String> roles) {
    public UserRoleAssignmentRequest {
        roles = roles == null
                ? List.of()
                : Collections.unmodifiableList(new ArrayList<>(roles));
    }
}
