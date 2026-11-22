package com.elsyah.tech.javara.unit.middleware.cors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfigurationSource;

import com.elsyah.tech.javara.middleware.cors.CorsModuleConfiguration;

class CorsModuleConfigurationTests {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(CorsModuleConfiguration.class);

    @Test
    void doesNotRegisterCorsWhenDisabled() {
        contextRunner
                .withPropertyValues("app.web.cors.enabled=false")
                .run(context -> {
                    assertFalse(context.containsBean("corsConfigurationSource"));
                });
    }

    @Test
    void registersOnlyConfiguredOriginsWhenEnabled() {
        contextRunner
                .withPropertyValues(
                        "app.web.cors.enabled=true",
                        "app.web.cors.allowed-origins[0]=https://erp.example.com",
                        "app.web.cors.max-age-seconds=300")
                .run(context -> {
                    assertTrue(context.isRunning());
                    CorsConfigurationSource source =
                            context.getBean(CorsConfigurationSource.class);
                    org.springframework.web.cors.CorsConfiguration cors =
                            source.getCorsConfiguration(
                                    new MockHttpServletRequest("GET", "/api/v1/users"));

                    assertNotNull(cors);
                    assertEquals(List.of("https://erp.example.com"), cors.getAllowedOrigins());
                    assertEquals(
                            List.of("GET", "HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"),
                            cors.getAllowedMethods());
                });
    }
}
