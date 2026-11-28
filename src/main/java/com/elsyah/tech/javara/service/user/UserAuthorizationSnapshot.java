package com.elsyah.tech.javara.service.user;

import java.util.List;

public record UserAuthorizationSnapshot(List<String> roles, List<String> authorities) {
    public UserAuthorizationSnapshot {
        roles = List.copyOf(roles);
        authorities = List.copyOf(authorities);
    }
}
