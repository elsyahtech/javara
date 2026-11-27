package com.elsyah.tech.javara.middleware.feature;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.features")
public record FeatureModulesProperties(
        Boolean authApi,
        Boolean resourceServer,
        Boolean rateLimiting) {
    public FeatureModulesProperties {
        authApi = authApi == null ? true : authApi;
        resourceServer = resourceServer == null ? true : resourceServer;
        rateLimiting = rateLimiting == null ? true : rateLimiting;
    }
}
