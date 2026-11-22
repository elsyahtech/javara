package com.elsyah.tech.javara.middleware.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "app.security.headers")
public record SecurityHeadersProperties(@DefaultValue("true") boolean enabled) {}
