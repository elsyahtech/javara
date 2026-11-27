package com.elsyah.tech.javara.unit.middleware.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.elsyah.tech.javara.middleware.config.ApplicationIdentityConfiguration;
import com.elsyah.tech.javara.middleware.security.PasswordPolicyConfiguration;
import com.elsyah.tech.javara.service.user.PasswordPolicyService;

class PasswordPolicyConfigurationTests {
    @TempDir
    Path tempDir;

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(
                    PasswordPolicyConfiguration.class,
                    ApplicationIdentityConfiguration.class);

    @Test
    void startupFailsWithActionableMessageWhenRequiredFilesAreMissing() {
        contextRunner
                .withPropertyValues(
                        "app.security.password-policy.enabled=true",
                        "app.security.password-policy.common-passwords-file=/tmp/javara-missing-common",
                        "app.security.password-policy.breached-password-hashes-file=/tmp/javara-missing-breached")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(rootCauseMessage(context.getStartupFailure()))
                            .contains("Password policy startup validation failed")
                            .contains("Common-password denylist is missing or unreadable")
                            .contains("at least 3,000 distinct passwords");
                });
    }

    @Test
    void productionStartupFailsIfPasswordPolicyIsDisabled() {
        contextRunner
                .withPropertyValues(
                        "spring.profiles.active=production",
                        "app.security.password-policy.enabled=false")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(rootCauseMessage(context.getStartupFailure()))
                            .contains("Password policy cannot be disabled")
                            .contains("production");
                });
    }

    @Test
    void sampleDataIsRejectedOutsideLocalDevelopmentProfiles() {
        contextRunner
                .withPropertyValues(
                        "app.security.password-policy.sample-data=true")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(rootCauseMessage(context.getStartupFailure()))
                            .contains("Sample password datasets are permitted only")
                            .contains("local, dev, or test");
                });
    }

    @Test
    void sampleDataIsRejectedInProductionEvenWhenLocalProfileIsAlsoActive() {
        contextRunner
                .withPropertyValues(
                        "spring.profiles.active=local,production",
                        "app.security.password-policy.sample-data=true")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(rootCauseMessage(context.getStartupFailure()))
                            .contains("Sample password datasets are permitted only");
                });
    }

    @Test
    void sampleDataIsRejectedWhenAnyNonDevelopmentProfileIsActive() {
        contextRunner
                .withPropertyValues(
                        "spring.profiles.active=local,custom",
                        "app.security.password-policy.sample-data=true")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(rootCauseMessage(context.getStartupFailure()))
                            .contains("Sample password datasets are permitted only");
                });
    }

    @Test
    void localProfileCanStartWithAndCheckSmallSampleDatasets() throws IOException {
        Path commonPasswords = tempDir.resolve("common-passwords.txt");
        Files.writeString(commonPasswords, "sample-common-password\n");
        Path breachedHashes = tempDir.resolve("breached-password-hashes.txt");
        Files.writeString(breachedHashes, "1a7b1214ef502489c4e3a392da39fa33c101d91f\n");

        contextRunner
                .withPropertyValues(
                        "spring.profiles.active=local",
                        "app.security.password-policy.sample-data=true",
                        "app.security.password-policy.common-passwords-file="
                                + commonPasswords,
                        "app.security.password-policy.breached-password-hashes-file="
                                + breachedHashes)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    PasswordPolicyService policy =
                            context.getBean(PasswordPolicyService.class);
                    assertThat(policy.isPasswordAllowed("sample-common-password")).isFalse();
                    assertThat(policy.isPasswordAllowed("demo-breach-check-password")).isFalse();
                });
    }

    private static String rootCauseMessage(Throwable throwable) {
        Throwable root = throwable;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        return root.getMessage();
    }
}
