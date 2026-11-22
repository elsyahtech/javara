package com.elsyah.tech.javara.unit.middleware.requestid;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;

import com.elsyah.tech.javara.middleware.requestid.RequestIdModuleConfiguration;

class RequestIdModuleConfigurationTests {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(RequestIdModuleConfiguration.class);

    @Test
    void registersRequestIdFilterByDefault() {
        contextRunner.run(context -> {
            assertTrue(context.isRunning());
            assertTrue(context.getBeansOfType(FilterRegistrationBean.class)
                    .containsKey("requestIdFilterRegistration"));
        });
    }

    @Test
    void doesNotRegisterRequestIdFilterWhenDisabled() {
        contextRunner
                .withPropertyValues("app.web.request-id.enabled=false")
                .run(context -> {
                    assertFalse(context.containsBean("requestIdFilterRegistration"));
                });
    }
}
