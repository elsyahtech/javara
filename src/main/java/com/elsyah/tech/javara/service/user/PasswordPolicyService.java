package com.elsyah.tech.javara.service.user;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.InvalidPathException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import com.elsyah.tech.javara.middleware.security.PasswordPolicyProperties;

public class PasswordPolicyService {
    private static final int MIN_COMMON_PASSWORDS = 3_000;
    private static final Pattern HASH_PATTERN =
            Pattern.compile("[0-9a-f]{40}(?::[1-9][0-9]*)?", Pattern.CASE_INSENSITIVE);

    private final boolean enabled;
    private final Set<String> commonPasswords;
    private final Set<String> breachedPasswordHashes;

    public PasswordPolicyService(PasswordPolicyProperties properties) {
        this.enabled = properties.enabled();
        boolean sampleData = properties.sampleData();
        if (!enabled) {
            commonPasswords = Set.of();
            breachedPasswordHashes = Set.of();
            return;
        }

        commonPasswords = loadCommonPasswords(
                configuredPath(
                        properties.commonPasswordsFile(),
                        "app.security.password-policy.common-passwords-file"),
                sampleData);
        breachedPasswordHashes =
                loadBreachedPasswordHashes(configuredPath(
                        properties.breachedPasswordHashesFile(),
                        "app.security.password-policy.breached-password-hashes-file"));
    }

    public boolean isPasswordAllowed(String password) {
        if (password == null) {
            return false;
        }
        if (!enabled) {
            return true;
        }
        return !commonPasswords.contains(password)
                && !breachedPasswordHashes.contains(sha1(password));
    }

    private static Set<String> loadCommonPasswords(Path configuredPath, boolean sampleData) {
        Path path = configuredPath.toAbsolutePath().normalize();
        if (!Files.isRegularFile(path) || !Files.isReadable(path)) {
            throw startupFailure(
                    "Common-password denylist is missing or unreadable at '%s'. Provide a UTF-8 "
                            .formatted(path)
                            + "file with at least 3,000 distinct passwords and configure "
                            + "app.security.password-policy.common-passwords-file.");
        }

        Set<String> passwords = new HashSet<>();
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            boolean firstLine = true;
            while ((line = reader.readLine()) != null) {
                if (firstLine) {
                    line = stripBom(line);
                    firstLine = false;
                }
                if (line.length() >= 8 && line.length() <= 72 && !line.isBlank()) {
                    passwords.add(line);
                }
            }
        } catch (IOException exception) {
            throw startupFailure(
                    "Could not read common-password denylist at '%s'. Check file permissions "
                            .formatted(path)
                            + "and UTF-8 encoding.",
                    exception);
        }

        if (sampleData && passwords.isEmpty()) {
            throw startupFailure(
                    "Sample common-password file at '%s' contains no valid 8-72 character "
                            .formatted(path)
                            + "entries. Restore the example data or configure another file.");
        }
        if (!sampleData && passwords.size() < MIN_COMMON_PASSWORDS) {
            throw startupFailure(
                    ("Common-password denylist at '%s' has %d distinct 8-72 character entries; "
                            + "at least 3,000 are required. Add an approved dataset before "
                            + "starting JAVARA.")
                            .formatted(path, passwords.size()));
        }
        return Set.copyOf(passwords);
    }

    private static Set<String> loadBreachedPasswordHashes(Path configuredPath) {
        Path path = configuredPath.toAbsolutePath().normalize();
        if (!Files.isRegularFile(path) || !Files.isReadable(path)) {
            throw startupFailure(
                    "Breached-password hash dataset is missing or unreadable at '%s'. Provide a "
                            .formatted(path)
                            + "UTF-8 file containing one SHA-1 hash per line (optionally followed "
                            + "by ':count') and configure "
                            + "app.security.password-policy.breached-password-hashes-file.");
        }

        Set<String> hashes = new HashSet<>();
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.isBlank()) {
                    continue;
                }
                if (!HASH_PATTERN.matcher(line).matches()) {
                    throw startupFailure(
                            ("Breached-password hash dataset at '%s' has an invalid record at "
                                    + "line %d. Expected a 40-character hexadecimal SHA-1 hash, "
                                    + "optionally followed by ':count'. Record contents are not "
                                    + "included in this error.")
                                    .formatted(path, lineNumber));
                }
                hashes.add(line.substring(0, 40).toUpperCase(Locale.ROOT));
            }
        } catch (IOException exception) {
            throw startupFailure(
                    "Could not read breached-password hash dataset at '%s'. Check file permissions "
                            .formatted(path)
                            + "and UTF-8 encoding.",
                    exception);
        }

        if (hashes.isEmpty()) {
            throw startupFailure(
                    "Breached-password hash dataset at '%s' contains no valid hashes. Provide an "
                            .formatted(path)
                            + "approved dataset before starting JAVARA.");
        }
        return Set.copyOf(hashes);
    }

    private static String stripBom(String value) {
        return value.startsWith("\uFEFF") ? value.substring(1) : value;
    }

    private static Path configuredPath(String value, String propertyName) {
        try {
            return Path.of(value);
        } catch (InvalidPathException exception) {
            throw startupFailure(
                    "Configured dataset path for %s is not a valid filesystem path."
                            .formatted(propertyName),
                    exception);
        }
    }

    private static String sha1(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-1")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().withUpperCase().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-1 is not available for password lookup", exception);
        }
    }

    private static IllegalStateException startupFailure(String message) {
        return new IllegalStateException("Password policy startup validation failed: " + message);
    }

    private static IllegalStateException startupFailure(String message, Throwable cause) {
        return new IllegalStateException("Password policy startup validation failed: " + message, cause);
    }
}
