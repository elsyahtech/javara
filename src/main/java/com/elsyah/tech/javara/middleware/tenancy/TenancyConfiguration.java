package com.elsyah.tech.javara.middleware.tenancy;

import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.ApplicationRunner;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(TenancyProperties.class)
public class TenancyConfiguration {
    @Bean
    static BeanFactoryPostProcessor tenancyModeValidator(Environment environment) {
        return beanFactory -> {
            String configuredMode = environment.getProperty("app.tenancy.mode", "SINGLE");
            if ("SHARED".equalsIgnoreCase(configuredMode)) {
                throw new BeanDefinitionStoreException(
                        "Tenancy mode 'SHARED' is not ready for runtime use. JAVARA currently "
                                + "supports 'SINGLE' only; shared multi-tenant requests require "
                                + "tenant selection, tenant-scoped authorization, and complete "
                                + "data isolation. Set app.tenancy.mode=SINGLE.");
            }
            if (!"SINGLE".equalsIgnoreCase(configuredMode)) {
                throw new BeanDefinitionStoreException(
                        "Invalid app.tenancy.mode '" + configuredMode
                                + "'. Supported values are SINGLE and SHARED.");
            }
        };
    }

    @Bean
    public ApplicationRunner tenancyModeStartupCheck(
            JdbcTemplate jdbcTemplate, TenancyProperties properties) {
        return arguments -> {
            jdbcTemplate.update(
                    """
                    INSERT INTO javara_installation_settings (setting_id, tenancy_mode)
                    VALUES (1, ?)
                    ON CONFLICT (setting_id) DO NOTHING
                    """,
                    properties.mode().name());
            String persistedMode = jdbcTemplate.queryForObject(
                    "SELECT tenancy_mode FROM javara_installation_settings WHERE setting_id = 1",
                    String.class);
            if (!properties.mode().name().equals(persistedMode)) {
                throw new IllegalStateException(
                        "Configured tenancy mode '" + properties.mode()
                                + "' does not match the database's recorded mode '"
                                + persistedMode
                                + "'. Changing tenancy mode requires an explicit data migration; "
                                + "restore the original setting or migrate the database first.");
            }
        };
    }
}
