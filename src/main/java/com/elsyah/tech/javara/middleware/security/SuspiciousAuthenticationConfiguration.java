package com.elsyah.tech.javara.middleware.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(SuspiciousAuthenticationProperties.class)
public class SuspiciousAuthenticationConfiguration {
}
