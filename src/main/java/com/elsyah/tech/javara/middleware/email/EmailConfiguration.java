package com.elsyah.tech.javara.middleware.email;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

import com.elsyah.tech.javara.middleware.config.ApplicationIdentityProperties;

@Configuration
@EnableConfigurationProperties(EmailProperties.class)
public class EmailConfiguration {
    @Bean
    EmailSettings emailSettings(
            EmailProperties properties,
            ApplicationIdentityProperties applicationIdentity,
            Environment environment) {
        boolean productionProfile =
                environment.acceptsProfiles(Profiles.of("prod", "production", "staging"));
        boolean authApiEnabled = environment.getProperty(
                "app.features.auth-api", Boolean.class, true);
        if (!properties.enabled() && productionProfile && authApiEnabled) {
            throw new IllegalStateException(
                    "Email delivery cannot be disabled with the prod, production, or staging "
                            + "profile while 'auth-api' is enabled. Configure an SMTP provider "
                            + "using spring.mail, or disable app.features.auth-api.");
        }
        if (properties.verificationTokenTtl().isNegative()
                || properties.verificationTokenTtl().isZero()) {
            throw new IllegalStateException(
                    "app.email.verification-token-ttl must be greater than zero.");
        }
        if (!properties.enabled()) {
            return new EmailSettings(
                    false,
                    properties.verificationTokenTtl(),
                    "",
                    properties.publicBaseUrl());
        }

        String mailHost = environment.getProperty("spring.mail.host");
        if (mailHost == null || mailHost.isBlank()) {
            throw new IllegalStateException(
                    "Email delivery is enabled but spring.mail.host is missing. Configure "
                            + "SMTP host, authentication, and TLS before starting "
                            + applicationIdentity.name() + ".");
        }
        String username = environment.getProperty("spring.mail.username");
        String password = environment.getProperty("spring.mail.password");
        boolean authenticationEnabled = environment.getProperty(
                "spring.mail.properties.mail.smtp.auth", Boolean.class, false);
        if (authenticationEnabled
                && ((username == null || username.isBlank())
                        || (password == null || password.isBlank()))) {
            throw new IllegalStateException(
                    "SMTP authentication is enabled but credentials are missing. Provide "
                            + "spring.mail.username and spring.mail.password through a secret "
                            + "manager or environment variables.");
        }
        boolean startTlsEnabled = environment.getProperty(
                "spring.mail.properties.mail.smtp.starttls.enable", Boolean.class, false);
        boolean sslEnabled = environment.getProperty(
                "spring.mail.properties.mail.smtp.ssl.enable", Boolean.class, false);
        if (productionProfile && !startTlsEnabled && !sslEnabled) {
            throw new IllegalStateException(
                    "SMTP transport security is required with prod, production, or staging. "
                            + "Enable STARTTLS or SMTP SSL.");
        }
        String from = properties.fromAddress();
        if (from == null || from.isBlank()) {
            from = username;
        }
        if (from == null || from.isBlank()) {
            throw new IllegalStateException(
                    "Configure app.email.from-address or spring.mail.username as the sender address.");
        }
        String baseUrl = properties.publicBaseUrl();
        if (baseUrl == null || !baseUrl.matches("^https?://[^\\s]+$")) {
            throw new IllegalStateException(
                    "app.email.public-base-url must be an absolute HTTP or HTTPS URL.");
        }
        if (productionProfile && !baseUrl.startsWith("https://")) {
            throw new IllegalStateException(
                    "app.email.public-base-url must use HTTPS with the prod, production, or "
                            + "staging profile.");
        }
        return new EmailSettings(
                true,
                properties.verificationTokenTtl(),
                from,
                trimTrailingSlash(baseUrl));
    }

    private static String trimTrailingSlash(String value) {
        int end = value.length();
        while (end > 0 && value.charAt(end - 1) == '/') {
            end--;
        }
        return value.substring(0, end);
    }
}
