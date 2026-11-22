package com.elsyah.tech.javara.unit.middleware.interceptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.handler.MappedInterceptor;

import com.elsyah.tech.javara.middleware.interceptor.LoginRateLimitInterceptor;
import com.elsyah.tech.javara.middleware.interceptor.LoginRateLimitWebMvcConfiguration;
import com.elsyah.tech.javara.middleware.ratelimit.LoginRateLimitProperties;
import com.elsyah.tech.javara.middleware.ratelimit.LoginRateLimiter;

class LoginRateLimitWebMvcConfigurationTests {
    @Test
    void registersLoginRateLimiterWhenEnabled() {
        LoginRateLimitInterceptor interceptor =
                new LoginRateLimitInterceptor(mock(LoginRateLimiter.class));
        LoginRateLimitProperties properties = new LoginRateLimitProperties(
                10, Duration.ofMinutes(1), "test:login", true);
        CapturingInterceptorRegistry registry = new CapturingInterceptorRegistry();

        new LoginRateLimitWebMvcConfiguration(interceptor, properties).addInterceptors(registry);

        List<Object> registered = registry.registeredInterceptors();
        assertEquals(1, registered.size());
        assertSame(interceptor, ((MappedInterceptor) registered.get(0)).getInterceptor());
    }

    @Test
    void omitsLoginRateLimiterWhenDisabled() {
        LoginRateLimitInterceptor interceptor =
                new LoginRateLimitInterceptor(mock(LoginRateLimiter.class));
        LoginRateLimitProperties properties = new LoginRateLimitProperties(
                10, Duration.ofMinutes(1), "test:login", false);
        CapturingInterceptorRegistry registry = new CapturingInterceptorRegistry();

        new LoginRateLimitWebMvcConfiguration(interceptor, properties).addInterceptors(registry);

        assertEquals(List.of(), registry.registeredInterceptors());
    }

    private static class CapturingInterceptorRegistry extends InterceptorRegistry {
        List<Object> registeredInterceptors() {
            return getInterceptors();
        }
    }
}
