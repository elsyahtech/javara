package com.elsyah.tech.javara.unit.middleware.interceptor;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.handler.MappedInterceptor;

import com.elsyah.tech.javara.middleware.interceptor.AuthRateLimitInterceptor;
import com.elsyah.tech.javara.middleware.interceptor.AuthRateLimitWebMvcConfiguration;
import com.elsyah.tech.javara.middleware.ratelimit.AuthRateLimiter;
import com.elsyah.tech.javara.middleware.ratelimit.LoginRateLimitProperties;
import com.elsyah.tech.javara.middleware.ratelimit.RefreshRateLimitProperties;
import com.elsyah.tech.javara.middleware.ratelimit.RegisterRateLimitProperties;
import com.elsyah.tech.javara.middleware.ratelimit.SessionReauthRateLimitProperties;

class AuthRateLimitWebMvcConfigurationTests {
    @Test
    void registersAllEnabledAuthEndpoints() {
        AuthRateLimitInterceptor interceptor = interceptor();
        CapturingInterceptorRegistry registry = new CapturingInterceptorRegistry();

        configuration(interceptor, true, true, true).addInterceptors(registry);

        List<Object> registered = registry.registeredInterceptors();
        assertEquals(1, registered.size());
        MappedInterceptor mapped = (MappedInterceptor) registered.get(0);
        assertSame(interceptor, mapped.getInterceptor());
        assertArrayEquals(
                new String[] {
                        "/api/v1/auth/login",
                        "/api/v1/auth/register",
                        "/api/v1/auth/refresh",
                        "/api/v1/auth/password/change",
                        "/api/v1/auth/sessions/*/terminate",
                        "/api/v1/auth/sessions/terminate-all",
                        "/api/v1/admin/users/*/sessions/terminate"
                },
                mapped.getIncludePathPatterns());
    }

    @Test
    void omitsDisabledEndpointPatterns() {
        AuthRateLimitInterceptor interceptor = interceptor();
        CapturingInterceptorRegistry registry = new CapturingInterceptorRegistry();

        configuration(interceptor, false, true, false, false).addInterceptors(registry);

        List<Object> registered = registry.registeredInterceptors();
        assertEquals(1, registered.size());
        MappedInterceptor mapped = (MappedInterceptor) registered.get(0);
        assertArrayEquals(
                new String[] {"/api/v1/auth/register"},
                mapped.getIncludePathPatterns());
    }

    @Test
    void doesNotRegisterInterceptorWhenAllEndpointsAreDisabled() {
        CapturingInterceptorRegistry registry = new CapturingInterceptorRegistry();

        configuration(interceptor(), false, false, false, false).addInterceptors(registry);

        assertEquals(List.of(), registry.registeredInterceptors());
    }

    private static AuthRateLimitInterceptor interceptor() {
        return new AuthRateLimitInterceptor(
                mock(AuthRateLimiter.class),
                new LoginRateLimitProperties(10, Duration.ofMinutes(1), "test:login", true),
                new RegisterRateLimitProperties(5, Duration.ofHours(1), "test:register", true),
                new RefreshRateLimitProperties(30, Duration.ofMinutes(1), "test:refresh", true),
                new SessionReauthRateLimitProperties(
                        5, Duration.ofMinutes(15), "test:session-reauth", true));
    }

    private static AuthRateLimitWebMvcConfiguration configuration(
            AuthRateLimitInterceptor interceptor,
            boolean loginEnabled,
            boolean registerEnabled,
            boolean refreshEnabled) {
        return configuration(
                interceptor,
                loginEnabled,
                registerEnabled,
                refreshEnabled,
                true);
    }

    private static AuthRateLimitWebMvcConfiguration configuration(
            AuthRateLimitInterceptor interceptor,
            boolean loginEnabled,
            boolean registerEnabled,
            boolean refreshEnabled,
            boolean sessionReauthEnabled) {
        return new AuthRateLimitWebMvcConfiguration(
                interceptor,
                new LoginRateLimitProperties(10, Duration.ofMinutes(1), "test:login", loginEnabled),
                new RegisterRateLimitProperties(
                        5, Duration.ofHours(1), "test:register", registerEnabled),
                new RefreshRateLimitProperties(
                        30, Duration.ofMinutes(1), "test:refresh", refreshEnabled),
                new SessionReauthRateLimitProperties(
                        5,
                        Duration.ofMinutes(15),
                        "test:session-reauth",
                        sessionReauthEnabled));
    }

    private static class CapturingInterceptorRegistry extends InterceptorRegistry {
        List<Object> registeredInterceptors() {
            return getInterceptors();
        }
    }
}
