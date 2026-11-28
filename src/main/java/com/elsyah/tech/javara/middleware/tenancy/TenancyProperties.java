package com.elsyah.tech.javara.middleware.tenancy;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.tenancy")
public record TenancyProperties(TenancyMode mode) {
    public TenancyProperties {
        mode = mode == null ? TenancyMode.SINGLE : mode;
    }
}
