package com.elsyah.tech.javara.middleware.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(
        prefix = "app.features",
        name = "auth-api",
        havingValue = "true",
        matchIfMissing = true)
@EnableConfigurationProperties(SuspiciousAuthenticationProperties.class)
public class SuspiciousAuthenticationConfiguration {
}
