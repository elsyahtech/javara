package com.elsyah.tech.javara.middleware.security;

import jakarta.validation.constraints.NotBlank;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.security.password-policy")
public record PasswordPolicyProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("false") boolean sampleData,
        @NotBlank @DefaultValue("./config/security/common-passwords.txt")
        String commonPasswordsFile,
        @NotBlank @DefaultValue("./config/security/breached-password-hashes.txt")
        String breachedPasswordHashesFile) {}
