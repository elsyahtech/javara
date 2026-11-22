package com.elsyah.tech.javara.utils.security;

import java.util.Locale;

public enum PasswordAlgorithm {
    ARGON2,
    BCRYPT,
    SHA256,
    MD5;

    public static PasswordAlgorithm fromConfiguration(String algorithm) {
        if (algorithm == null || algorithm.isBlank()) {
            throw new IllegalArgumentException(
                    "app.security.password.algorithm must not be blank");
        }

        try {
            return valueOf(algorithm.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "Unsupported password algorithm '" + algorithm
                            + "'. Supported values are: ARGON2, BCRYPT, SHA256, MD5",
                    exception);
        }
    }
}