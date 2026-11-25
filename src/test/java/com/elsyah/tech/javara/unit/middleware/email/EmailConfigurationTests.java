package com.elsyah.tech.javara.unit.middleware.email;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.elsyah.tech.javara.middleware.email.EmailConfiguration;
import com.elsyah.tech.javara.middleware.email.EmailSettings;

class EmailConfigurationTests {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(EmailConfiguration.class);

    @Test
    void createsProviderNeutralSettingsFromSmtpConfiguration() {
        contextRunner
                .withPropertyValues(
                        "app.email.enabled=true",
                        "app.email.public-base-url=https://javara.example.com",
                        "spring.mail.host=smtp.example.com",
                        "spring.mail.username=notify@example.com",
                        "spring.mail.password=test-only",
                        "spring.mail.properties.mail.smtp.auth=true",
                        "spring.mail.properties.mail.smtp.starttls.enable=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(EmailSettings.class))
                            .extracting(EmailSettings::fromAddress)
                            .isEqualTo("notify@example.com");
                });
    }

    @Test
    void failsClearlyWhenEmailIsEnabledWithoutSmtpHost() {
        contextRunner
                .withPropertyValues("app.email.enabled=true")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(rootCauseMessage(context.getStartupFailure()))
                            .contains("Email delivery is enabled")
                            .contains("spring.mail.host");
                });
    }

    @Test
    void productionRequiresHttpsForVerificationLinks() {
        contextRunner
                .withPropertyValues(
                        "spring.profiles.active=production",
                        "app.email.public-base-url=http://javara.example.com",
                        "spring.mail.host=smtp.example.com",
                        "spring.mail.username=notify@example.com",
                        "spring.mail.password=test-only",
                        "spring.mail.properties.mail.smtp.auth=true",
                        "spring.mail.properties.mail.smtp.starttls.enable=true")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(rootCauseMessage(context.getStartupFailure()))
                            .contains("must use HTTPS");
                });
    }

    @Test
    void productionCannotDisableEmailDelivery() {
        contextRunner
                .withPropertyValues(
                        "spring.profiles.active=staging",
                        "app.email.enabled=false")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(rootCauseMessage(context.getStartupFailure()))
                            .contains("Email delivery cannot be disabled");
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
