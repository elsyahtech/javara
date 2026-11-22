package com.elsyah.tech.javara.middleware.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.elsyah.tech.javara.service.BootstrapAdminService;

@Component
@ConditionalOnProperty(
        prefix = "app.security.bootstrap-admin",
        name = "enabled",
        havingValue = "true")
public class BootstrapAdminInitializer implements SmartInitializingSingleton {
    private static final Logger logger = LoggerFactory.getLogger(BootstrapAdminInitializer.class);

    private final BootstrapAdminProperties properties;
    private final BootstrapAdminService bootstrapAdminService;

    public BootstrapAdminInitializer(
            BootstrapAdminProperties properties,
            BootstrapAdminService bootstrapAdminService) {
        this.properties = properties;
        this.bootstrapAdminService = bootstrapAdminService;
    }

    @Override
    public void afterSingletonsInstantiated() {
        bootstrapAdminService.createInitialAdmin(properties.email(), properties.password());
        logger.warn(
                "Initial administrator bootstrap completed; remove the bootstrap password from config/application.yml and disable app.security.bootstrap-admin.enabled");
    }
}
