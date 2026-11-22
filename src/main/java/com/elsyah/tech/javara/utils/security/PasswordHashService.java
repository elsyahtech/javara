package com.elsyah.tech.javara.utils.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

/**
 * Encodes and verifies passwords while supporting modern and legacy formats.
 */
@Service
public class PasswordHashService {
    private static final String DUMMY_PASSWORD = "JAVARA_DUMMY_PASSWORD_TIMING_ONLY";
    private static final ConcurrentMap<PasswordAlgorithm, String> DUMMY_PASSWORD_HASHES =
            new ConcurrentHashMap<>();

    private final Argon2PasswordEncoder argon2 =
            Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    private final BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder(12);
    private final PasswordAlgorithm defaultAlgorithm;

    @Autowired
    public PasswordHashService(
            @Value("${app.security.password.algorithm:ARGON2}") String algorithm) {
        this.defaultAlgorithm = PasswordAlgorithm.fromConfiguration(algorithm);
        dummyPasswordHash();
    }

    public PasswordHashService() {
        this(PasswordAlgorithm.ARGON2.name());
    }

    public String encode(String rawPassword) {
        return encode(rawPassword, defaultAlgorithm);
    }

    public String encode(String rawPassword, PasswordAlgorithm algorithm) {
        if (rawPassword == null) {
            throw new IllegalArgumentException("Raw password must not be null");
        }
        if (algorithm == null) {
            throw new IllegalArgumentException("Password algorithm must not be null");
        }

        return switch (algorithm) {
            case ARGON2 -> "{argon2}" + argon2.encode(rawPassword);
            case BCRYPT -> "{bcrypt}" + bcrypt.encode(rawPassword);
        };
    }

    public boolean matches(String rawPassword, String storedPassword) {
        if (rawPassword == null || storedPassword == null) {
            return false;
        }

        try {
            if (storedPassword.startsWith("{argon2}")) {
                return argon2.matches(
                        rawPassword,
                        storedPassword.substring("{argon2}".length()));
            }

            if (storedPassword.startsWith("{bcrypt}")) {
                return bcrypt.matches(
                        rawPassword,
                        storedPassword.substring("{bcrypt}".length()));
            }

            if (storedPassword.startsWith("$2")) {
                return bcrypt.matches(rawPassword, storedPassword);
            }

            if (storedPassword.startsWith("$argon2")) {
                return argon2.matches(rawPassword, storedPassword);
            }

            if (storedPassword.startsWith("{sha256}") || storedPassword.startsWith("sha256:")) {
                String digest = stripPrefix(storedPassword, "{sha256}", "sha256:");
                return constantTimeEquals(digest, sha256(rawPassword));
            }

            if (storedPassword.startsWith("{md5}") || storedPassword.startsWith("md5:")) {
                String digest = stripPrefix(storedPassword, "{md5}", "md5:");
                return constantTimeEquals(digest, md5(rawPassword));
            }

            if (isHex(storedPassword, 64)) {
                return constantTimeEquals(storedPassword, sha256(rawPassword));
            }

            if (isHex(storedPassword, 32)) {
                return constantTimeEquals(storedPassword, md5(rawPassword));
            }

            return false;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    public boolean matchesDummyPassword(String rawPassword) {
        return matches(rawPassword, dummyPasswordHash());
    }

    public boolean requiresUpgrade(String storedPassword) {
        if (storedPassword == null) {
            return true;
        }
        return !storedPassword.startsWith(prefixFor(defaultAlgorithm));
    }

    public boolean matchesAndRequiresUpgrade(String rawPassword, String storedPassword) {
        return matches(rawPassword, storedPassword) && requiresUpgrade(storedPassword);
    }

    public PasswordAlgorithm getDefaultAlgorithm() {
        return defaultAlgorithm;
    }

    private static String prefixFor(PasswordAlgorithm algorithm) {
        return switch (algorithm) {
            case ARGON2 -> "{argon2}";
            case BCRYPT -> "{bcrypt}";
        };
    }

    private String dummyPasswordHash() {
        return DUMMY_PASSWORD_HASHES.computeIfAbsent(
                defaultAlgorithm,
                algorithm -> encode(DUMMY_PASSWORD, algorithm));
    }

    private static String stripPrefix(String value, String... prefixes) {
        for (String prefix : prefixes) {
            if (value.startsWith(prefix)) {
                return value.substring(prefix.length()).toLowerCase(Locale.ROOT);
            }
        }
        return value.toLowerCase(Locale.ROOT);
    }

    private static boolean constantTimeEquals(String left, String right) {
        if (left == null || right == null) {
            return false;
        }
        byte[] leftBytes = left.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8);
        byte[] rightBytes = right.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(leftBytes, rightBytes);
    }

    private static String sha256(String value) {
        return digest("SHA-256", value);
    }

    private static String md5(String value) {
        return digest("MD5", value);
    }

    private static String digest(String algorithm, String value) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance(algorithm);
            byte[] bytes = messageDigest.digest(
                    value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(bytes.length * 2);
            for (byte oneByte : bytes) {
                hex.append(String.format(Locale.ROOT, "%02x", oneByte & 0xff));
            }
            return hex.toString();
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Unsupported password digest: " + algorithm, exception);
        }
    }

    private static boolean isHex(String value, int length) {
        if (value == null || value.length() != length) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            if (!((character >= '0' && character <= '9')
                    || (character >= 'a' && character <= 'f')
                    || (character >= 'A' && character <= 'F'))) {
                return false;
            }
        }
        return true;
    }

    public PasswordEncoder argon2Encoder() {
        return argon2;
    }

    public PasswordEncoder bcryptEncoder() {
        return bcrypt;
    }
}
