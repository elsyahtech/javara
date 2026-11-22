package com.elsyah.tech.javara.middleware.interceptor;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.elsyah.tech.javara.middleware.ratelimit.LoginRateLimitProperties;

@Configuration
@EnableConfigurationProperties(LoginRateLimitProperties.class)
public class LoginRateLimitWebMvcConfiguration implements WebMvcConfigurer {
    private final LoginRateLimitInterceptor loginRateLimitInterceptor;
    private final LoginRateLimitProperties properties;

    public LoginRateLimitWebMvcConfiguration(
            LoginRateLimitInterceptor loginRateLimitInterceptor,
            LoginRateLimitProperties properties) {
        this.loginRateLimitInterceptor = loginRateLimitInterceptor;
        this.properties = properties;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        if (properties.enabled()) {
            registry.addInterceptor(loginRateLimitInterceptor)
                    .addPathPatterns("/api/v1/auth/login");
        }
    }
}
