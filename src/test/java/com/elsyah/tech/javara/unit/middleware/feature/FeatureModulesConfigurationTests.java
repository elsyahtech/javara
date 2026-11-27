package com.elsyah.tech.javara.unit.middleware.feature;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.beans.factory.BeanDefinitionStoreException;

import com.elsyah.tech.javara.middleware.feature.FeatureModulesConfiguration;
import com.elsyah.tech.javara.middleware.feature.FeatureModulesProperties;

class FeatureModulesConfigurationTests {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(FeatureModulesConfiguration.class);

    @Test
    void defaultsAllRuntimeModulesToEnabledForCompatibility() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(FeatureModulesProperties.class))
                    .extracting(
                            FeatureModulesProperties::authApi,
                            FeatureModulesProperties::resourceServer,
                            FeatureModulesProperties::rateLimiting)
                    .containsExactly(true, true, true);
        });
    }

    @Test
    void failsFastWhenAuthenticationApiHasNoResourceServer() {
        contextRunner
                .withPropertyValues(
                        "app.features.auth-api=true",
                        "app.features.resource-server=false",
                        "app.security.service-auth.enabled=false")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(rootCause(context.getStartupFailure()))
                            .isInstanceOf(BeanDefinitionStoreException.class)
                            .hasMessageContaining("auth-api")
                            .hasMessageContaining("resource-server");
                });
    }

    @Test
    void failsFastWhenServiceAuthenticationHasNoResourceServer() {
        contextRunner
                .withPropertyValues(
                        "app.features.auth-api=false",
                        "app.features.resource-server=false",
                        "app.security.service-auth.enabled=true")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(rootCause(context.getStartupFailure()))
                            .isInstanceOf(BeanDefinitionStoreException.class)
                            .hasMessageContaining("service-auth")
                            .hasMessageContaining("resource-server");
                });
    }

    @Test
    void allowsExplicitlyDisablingAllDependentModules() {
        contextRunner
                .withPropertyValues(
                        "app.features.auth-api=false",
                        "app.features.resource-server=false",
                        "app.features.rate-limiting=false",
                        "app.security.service-auth.enabled=false")
                .run(context -> assertThat(context).hasNotFailed());
    }

    private static Throwable rootCause(Throwable throwable) {
        Throwable root = throwable;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        return root;
    }
}
