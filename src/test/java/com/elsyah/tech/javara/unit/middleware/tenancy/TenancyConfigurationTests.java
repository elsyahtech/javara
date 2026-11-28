package com.elsyah.tech.javara.unit.middleware.tenancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;

import com.elsyah.tech.javara.middleware.tenancy.TenancyConfiguration;
import com.elsyah.tech.javara.middleware.tenancy.TenancyMode;
import com.elsyah.tech.javara.middleware.tenancy.TenancyProperties;

class TenancyConfigurationTests {
    @Test
    void defaultsToSingleOrganizationMode() {
        assertThat(new TenancyProperties(null).mode()).isEqualTo(TenancyMode.SINGLE);
    }

    @Test
    void sharedModeFailsFastWithAnExplicitReadinessMessage() {
        new ApplicationContextRunner()
                .withUserConfiguration(TenancyConfiguration.class)
                .withBean(JdbcTemplate.class, () -> mock(JdbcTemplate.class))
                .withPropertyValues("app.tenancy.mode=SHARED")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(rootCause(context.getStartupFailure()))
                            .isInstanceOf(BeanDefinitionStoreException.class)
                            .hasMessageContaining("SHARED")
                            .hasMessageContaining("SINGLE");
                });
    }

    @Test
    void persistsSingleModeAndRejectsDatabaseModeChanges() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(
                        "SELECT tenancy_mode FROM javara_installation_settings WHERE setting_id = 1",
                        String.class))
                .thenReturn("SINGLE");

        TenancyConfiguration configuration = new TenancyConfiguration();
        configuration.tenancyModeStartupCheck(
                        jdbcTemplate, new TenancyProperties(TenancyMode.SINGLE))
                .run(mock(ApplicationArguments.class));

        verify(jdbcTemplate).update(
                """
                INSERT INTO javara_installation_settings (setting_id, tenancy_mode)
                VALUES (1, ?)
                ON CONFLICT (setting_id) DO NOTHING
                """,
                "SINGLE");

        when(jdbcTemplate.queryForObject(
                        "SELECT tenancy_mode FROM javara_installation_settings WHERE setting_id = 1",
                        String.class))
                .thenReturn("SHARED");
        assertThrows(
                IllegalStateException.class,
                () -> configuration.tenancyModeStartupCheck(
                                jdbcTemplate, new TenancyProperties(TenancyMode.SINGLE))
                        .run(mock(ApplicationArguments.class)));
    }

    private static Throwable rootCause(Throwable throwable) {
        Throwable root = throwable;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        return root;
    }
}
