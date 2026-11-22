package com.elsyah.tech.javara.unit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

import com.elsyah.tech.javara.utils.security.PasswordHashService;
import com.elsyah.tech.javara.utils.security.PasswordAlgorithm;

/**
 * 
 * PasswordHashServiceTests
 */
class PasswordHashServiceTests {
    private final PasswordHashService passwordHashService =
            new PasswordHashService();

    // Verifies that new passwords are encoded with Argon2.
    @Test
    void shouldEncodeNewPasswordWithArgon2() {
        String hash = passwordHashService.encode("password123");

        assertThat(hash).startsWith("{argon2}");
        assertThat(passwordHashService.matches("password123", hash)).isTrue();
        assertThat(passwordHashService.matches("wrong-password", hash)).isFalse();
    }

    // Verifies that the configured algorithm is used by the default encode method.
    @Test
    void shouldUseConfiguredBcryptForNewPasswords() {
        PasswordHashService bcryptService = new PasswordHashService("bcrypt");

        String hash = bcryptService.encode("password123");

        assertThat(bcryptService.getDefaultAlgorithm())
                .isEqualTo(PasswordAlgorithm.BCRYPT);
        assertThat(hash).startsWith("{bcrypt}");
        assertThat(bcryptService.matches("password123", hash)).isTrue();
    }

    // Verifies that unsupported algorithm names fail during configuration.
    @Test
    void shouldRejectUnsupportedConfiguredAlgorithm() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new PasswordHashService("unsupported"));
    }

    // Verifies that an existing BCrypt password can still be verified.
    @Test
    void shouldMatchLegacyBcryptPassword() {
        String hash = "{bcrypt}" + passwordHashService.bcryptEncoder()
                .encode("password123");

        assertThat(passwordHashService.matches("password123", hash)).isTrue();
        assertThat(passwordHashService.requiresUpgrade(hash)).isTrue();
    }

    // Verifies that raw BCrypt hashes remain readable for migration compatibility.
    @Test
    void shouldMatchRawBcryptPassword() {
        String hash = passwordHashService.bcryptEncoder().encode("password123");

        assertThat(passwordHashService.matches("password123", hash)).isTrue();
    }

    // Verifies that BCrypt can be selected explicitly for password encoding.
    @Test
    void shouldEncodePasswordWithExplicitBcrypt() {
        String hash = passwordHashService.encode(
                "password123",
                PasswordAlgorithm.BCRYPT);

        assertThat(hash).startsWith("{bcrypt}");
        assertThat(passwordHashService.matches("password123", hash)).isTrue();
    }

    // Verifies SHA256 compatibility using a prefixed legacy hash.
    @Test
    void shouldMatchPrefixedSha256Password() {
        String hash = passwordHashService.encode(
                "password123",
                PasswordAlgorithm.SHA256);

        assertThat(passwordHashService.matches("password123", hash)).isTrue();
        assertThat(passwordHashService.requiresUpgrade(hash)).isTrue();
    }

    // Verifies MD5 compatibility using a prefixed legacy hash.
    @Test
    void shouldMatchPrefixedMd5Password() {
        String hash = passwordHashService.encode(
                "password123",
                PasswordAlgorithm.MD5);

        assertThat(passwordHashService.matches("password123", hash)).isTrue();
        assertThat(passwordHashService.requiresUpgrade(hash)).isTrue();
    }

    // Verifies that malformed or unsupported password strings never authenticate.
    @Test
    void shouldRejectMalformedHash() {
        assertThat(passwordHashService.matches("password123", "{unknown}value"))
                .isFalse();
    }

    private static String sha256(String value) {
        return digest(value, "SHA-256");
    }

    private static String md5(String value) {
        return digest(value, "MD5");
    }

    private static String digest(String value, String algorithm) {
        try {
            java.security.MessageDigest messageDigest =
                    java.security.MessageDigest.getInstance(algorithm);
            byte[] bytes = messageDigest.digest(
                    value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte oneByte : bytes) {
                result.append(String.format("%02x", oneByte & 0xff));
            }
            return result.toString();
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
