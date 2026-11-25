package com.elsyah.tech.javara.middleware.email;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "app.email")
public record EmailProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("24h") Duration verificationTokenTtl,
        @DefaultValue("") String fromAddress,
        @DefaultValue("http://localhost:8080") String publicBaseUrl) {}
