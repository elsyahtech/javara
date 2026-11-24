package com.elsyah.tech.javara.middleware.interceptor;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.elsyah.tech.javara.middleware.ratelimit.LoginRateLimitProperties;
import com.elsyah.tech.javara.middleware.ratelimit.RefreshRateLimitProperties;
import com.elsyah.tech.javara.middleware.ratelimit.RegisterRateLimitProperties;
import com.elsyah.tech.javara.middleware.ratelimit.SessionReauthRateLimitProperties;

@Configuration
@EnableConfigurationProperties({
        LoginRateLimitProperties.class,
        RegisterRateLimitProperties.class,
        RefreshRateLimitProperties.class,
        SessionReauthRateLimitProperties.class
})
public class AuthRateLimitWebMvcConfiguration implements WebMvcConfigurer {
    private static final String LOGIN_PATH = "/api/v1/auth/login";
    private static final String REGISTER_PATH = "/api/v1/auth/register";
    private static final String REFRESH_PATH = "/api/v1/auth/refresh";
    private static final String SESSION_TERMINATE_PATH =
            "/api/v1/auth/sessions/*/terminate";
    private static final String SESSION_TERMINATE_ALL_PATH =
            "/api/v1/auth/sessions/terminate-all";
    private static final String ADMIN_SESSION_TERMINATE_PATH =
            "/api/v1/admin/users/*/sessions/terminate";

    private final AuthRateLimitInterceptor rateLimitInterceptor;
    private final LoginRateLimitProperties loginProperties;
    private final RegisterRateLimitProperties registerProperties;
    private final RefreshRateLimitProperties refreshProperties;
    private final SessionReauthRateLimitProperties sessionReauthProperties;

    public AuthRateLimitWebMvcConfiguration(
            AuthRateLimitInterceptor rateLimitInterceptor,
            LoginRateLimitProperties loginProperties,
            RegisterRateLimitProperties registerProperties,
            RefreshRateLimitProperties refreshProperties,
            SessionReauthRateLimitProperties sessionReauthProperties) {
        this.rateLimitInterceptor = rateLimitInterceptor;
        this.loginProperties = loginProperties;
        this.registerProperties = registerProperties;
        this.refreshProperties = refreshProperties;
        this.sessionReauthProperties = sessionReauthProperties;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        List<String> paths = new ArrayList<>(6);
        if (loginProperties.enabled()) {
            paths.add(LOGIN_PATH);
        }
        if (registerProperties.enabled()) {
            paths.add(REGISTER_PATH);
        }
        if (refreshProperties.enabled()) {
            paths.add(REFRESH_PATH);
        }
        if (sessionReauthProperties.enabled()) {
            paths.add(SESSION_TERMINATE_PATH);
            paths.add(SESSION_TERMINATE_ALL_PATH);
            paths.add(ADMIN_SESSION_TERMINATE_PATH);
        }
        if (!paths.isEmpty()) {
            registry.addInterceptor(rateLimitInterceptor).addPathPatterns(paths);
        }
    }
}
