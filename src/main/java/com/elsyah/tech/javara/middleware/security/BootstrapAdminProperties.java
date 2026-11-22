package com.elsyah.tech.javara.middleware.security;

import java.util.regex.Pattern;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security.bootstrap-admin")
public record BootstrapAdminProperties(boolean enabled, String email, String password) {
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    public BootstrapAdminProperties {
        if (enabled) {
            if (email == null || !EMAIL_PATTERN.matcher(email).matches()) {
                throw new IllegalArgumentException(
                        "A valid app.security.bootstrap-admin.email is required when bootstrap is enabled");
            }
            if (password == null || password.length() < 8 || password.length() > 72) {
                throw new IllegalArgumentException(
                        "Bootstrap admin password must be between 8 and 72 characters");
            }
        }
    }
}