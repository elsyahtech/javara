package com.elsyah.tech.javara.middleware.security;

import java.util.Arrays;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

import com.elsyah.tech.javara.middleware.config.ApplicationIdentityProperties;
import com.elsyah.tech.javara.service.user.PasswordPolicyService;

@Configuration
@ConditionalOnProperty(
        prefix = "app.features",
        name = "auth-api",
        havingValue = "true",
        matchIfMissing = true)
@EnableConfigurationProperties(PasswordPolicyProperties.class)
public class PasswordPolicyConfiguration {
    private static final Logger LOGGER =
            LoggerFactory.getLogger(PasswordPolicyConfiguration.class);

    @Bean
    PasswordPolicyService passwordPolicyService(
            PasswordPolicyProperties properties,
            ApplicationIdentityProperties applicationIdentity,
            Environment environment) {
        boolean productionProfile =
                environment.acceptsProfiles(Profiles.of("prod", "production", "staging"));
        if (properties.sampleData()
                && (productionProfile
                        || !onlyLocalDevelopmentProfilesAreActive(environment))) {
            throw new IllegalStateException(
                    "Sample password datasets are permitted only with the local, dev, or test "
                            + "profile. Configure approved datasets and set "
                            + "app.security.password-policy.sample-data=false for other profiles.");
        }
        if (!properties.enabled() && productionProfile) {
            throw new IllegalStateException(
                    "Password policy cannot be disabled with the prod, production, or staging "
                            + "profile. Provide both approved password datasets instead.");
        }
        if (properties.enabled() && properties.sampleData()) {
            LOGGER.warn(
                    "{} sample password datasets are active. They demonstrate local setup only "
                            + "and do not provide production password or breach coverage.",
                    applicationIdentity.name());
        }
        if (!properties.enabled()) {
            LOGGER.warn(
                    "Password denylist and breached-password checks are disabled. "
                            + "This setting is only suitable for tests or isolated local development.");
        }
        return new PasswordPolicyService(properties, applicationIdentity);
    }

    private static boolean onlyLocalDevelopmentProfilesAreActive(Environment environment) {
        Set<String> allowedProfiles = Set.of("local", "dev", "test");
        String[] activeProfiles = environment.getActiveProfiles();
        return activeProfiles.length > 0
                && Arrays.stream(activeProfiles).allMatch(allowedProfiles::contains);
    }
}
