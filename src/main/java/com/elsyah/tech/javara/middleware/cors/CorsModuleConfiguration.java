package com.elsyah.tech.javara.middleware.cors;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CorsProperties.class)
public class CorsModuleConfiguration {
    @Bean
    @ConditionalOnProperty(prefix = "app.web.cors", name = "enabled", havingValue = "true")
    UrlBasedCorsConfigurationSource corsConfigurationSource(CorsProperties properties) {
        org.springframework.web.cors.CorsConfiguration cors =
                new org.springframework.web.cors.CorsConfiguration();
        cors.setAllowedOrigins(properties.allowedOrigins());
        cors.setAllowedMethods(properties.normalizedAllowedMethods());
        cors.setAllowedHeaders(properties.allowedHeaders());
        cors.setExposedHeaders(properties.exposedHeaders());
        cors.setAllowCredentials(properties.allowCredentials());
        cors.setMaxAge(properties.maxAgeSeconds());

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cors);
        return source;
    }
}
