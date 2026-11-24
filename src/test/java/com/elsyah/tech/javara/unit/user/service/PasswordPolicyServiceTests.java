package com.elsyah.tech.javara.unit.user.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.elsyah.tech.javara.middleware.security.PasswordPolicyProperties;
import com.elsyah.tech.javara.service.user.PasswordPolicyService;

class PasswordPolicyServiceTests {
    @TempDir
    Path tempDir;

    @Test
    void rejectsPasswordsInCommonDenylistOrBreachedHashFile() throws Exception {
        Path commonPasswords = writeCommonPasswords();
        Path breachedHashes = tempDir.resolve("breached.txt");
        Files.writeString(
                breachedHashes,
                sha1("compromised-password") + ":12\n",
                StandardCharsets.UTF_8);
        PasswordPolicyService policy = new PasswordPolicyService(
                new PasswordPolicyProperties(
                        true, false, commonPasswords.toString(), breachedHashes.toString()));

        assertFalse(policy.isPasswordAllowed("common-password-0001"));
        assertFalse(policy.isPasswordAllowed("compromised-password"));
        assertTrue(policy.isPasswordAllowed("unique-strong-password"));
    }

    @Test
    void startupErrorClearlyExplainsMissingCommonPasswordDataset() {
        Path missing = tempDir.resolve("missing-common.txt");
        Path breached = tempDir.resolve("breached.txt");

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> new PasswordPolicyService(new PasswordPolicyProperties(
                        true, false, missing.toString(), breached.toString())));

        assertTrue(exception.getMessage().contains("Password policy startup validation failed"));
        assertTrue(exception.getMessage().contains("Common-password denylist is missing or unreadable"));
        assertTrue(exception.getMessage().contains("at least 3,000 distinct passwords"));
        assertTrue(exception.getMessage().contains(
                "app.security.password-policy.common-passwords-file"));
    }

    @Test
    void startupRejectsCommonDenylistWithFewerThanThreeThousandUniqueEntries()
            throws Exception {
        Path commonPasswords = tempDir.resolve("common.txt");
        Files.write(commonPasswords, List.of("password-one", "password-two"), StandardCharsets.UTF_8);
        Path breachedHashes = tempDir.resolve("breached.txt");
        Files.writeString(breachedHashes, sha1("known-bad") + "\n", StandardCharsets.UTF_8);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> new PasswordPolicyService(new PasswordPolicyProperties(
                        true, false, commonPasswords.toString(), breachedHashes.toString())));

        assertTrue(exception.getMessage().contains("has 2 distinct 8-72 character entries"));
        assertTrue(exception.getMessage().contains("at least 3,000 are required"));
    }

    @Test
    void startupRejectsMalformedBreachHashWithoutLoggingItsContents() throws Exception {
        Path commonPasswords = writeCommonPasswords();
        Path breachedHashes = tempDir.resolve("breached.txt");
        Files.writeString(breachedHashes, "not-a-hash\n", StandardCharsets.UTF_8);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> new PasswordPolicyService(new PasswordPolicyProperties(
                        true, false, commonPasswords.toString(), breachedHashes.toString())));

        assertTrue(exception.getMessage().contains("invalid record at line 1"));
        assertTrue(exception.getMessage().contains("Record contents are not included"));
        assertFalse(exception.getMessage().contains("not-a-hash"));
    }

    @Test
    void explicitDisabledModeDoesNotRequireDatasetFiles() {
        PasswordPolicyService policy = new PasswordPolicyService(
                new PasswordPolicyProperties(
                        false,
                        false,
                        tempDir.resolve("missing-common.txt").toString(),
                        tempDir.resolve("missing-breached.txt").toString()));

        assertTrue(policy.isPasswordAllowed("any-valid-password"));
    }

    @Test
    void sampleModeAllowsSmallOperatorEditableDatasets() throws Exception {
        Path commonPasswords = tempDir.resolve("sample-common.txt");
        Files.write(
                commonPasswords,
                List.of("sample-common-password"),
                StandardCharsets.UTF_8);
        Path breachedHashes = tempDir.resolve("sample-breached.txt");
        Files.writeString(
                breachedHashes,
                sha1("sample-breached-password") + "\n",
                StandardCharsets.UTF_8);

        PasswordPolicyService policy = new PasswordPolicyService(
                new PasswordPolicyProperties(
                        true, true, commonPasswords.toString(), breachedHashes.toString()));

        assertFalse(policy.isPasswordAllowed("sample-common-password"));
        assertFalse(policy.isPasswordAllowed("sample-breached-password"));
        assertTrue(policy.isPasswordAllowed("unique-strong-password"));
    }

    private Path writeCommonPasswords() throws IOException {
        Path file = tempDir.resolve("common.txt");
        List<String> entries = java.util.stream.IntStream.range(1, 3_001)
                .mapToObj(index -> "common-password-%04d".formatted(index))
                .toList();
        Files.write(file, entries, StandardCharsets.UTF_8);
        return file;
    }

    private static String sha1(String value) throws Exception {
        return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-1")
                        .digest(value.getBytes(StandardCharsets.UTF_8)));
    }
}
