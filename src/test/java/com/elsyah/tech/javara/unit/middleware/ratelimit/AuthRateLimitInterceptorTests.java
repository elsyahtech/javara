package com.elsyah.tech.javara.unit.middleware.ratelimit;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.elsyah.tech.javara.middleware.interceptor.AuthRateLimitInterceptor;
import com.elsyah.tech.javara.middleware.ratelimit.AuthRateLimiter;
import com.elsyah.tech.javara.middleware.ratelimit.LoginRateLimitProperties;
import com.elsyah.tech.javara.middleware.ratelimit.RefreshRateLimitProperties;
import com.elsyah.tech.javara.middleware.ratelimit.RegisterRateLimitProperties;

class AuthRateLimitInterceptorTests {
    @Test
    void checksLoginPostUsingClientAddress() {
        AuthRateLimiter limiter = mock(AuthRateLimiter.class);
        LoginRateLimitProperties login = new LoginRateLimitProperties(
                10, java.time.Duration.ofMinutes(1), "test:login", true);
        RegisterRateLimitProperties register = new RegisterRateLimitProperties(
                5, java.time.Duration.ofHours(1), "test:register", true);
        RefreshRateLimitProperties refresh = new RefreshRateLimitProperties(
                30, java.time.Duration.ofMinutes(1), "test:refresh", true);
        AuthRateLimitInterceptor interceptor =
                new AuthRateLimitInterceptor(limiter, login, register, refresh);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.setRequestURI("/api/v1/auth/login");
        request.setRemoteAddr("192.0.2.25");

        assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));

        verify(limiter).checkAllowed("192.0.2.25", login);
    }

    @Test
    void checksRegisterAndRefreshUsingTheirOwnPolicies() {
        AuthRateLimiter limiter = mock(AuthRateLimiter.class);
        LoginRateLimitProperties login = new LoginRateLimitProperties(
                10, java.time.Duration.ofMinutes(1), "test:login", true);
        RegisterRateLimitProperties register = new RegisterRateLimitProperties(
                5, java.time.Duration.ofHours(1), "test:register", true);
        RefreshRateLimitProperties refresh = new RefreshRateLimitProperties(
                30, java.time.Duration.ofMinutes(1), "test:refresh", true);
        AuthRateLimitInterceptor interceptor =
                new AuthRateLimitInterceptor(limiter, login, register, refresh);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.setRemoteAddr("192.0.2.25");
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.setRequestURI("/api/v1/auth/register");
        assertTrue(interceptor.preHandle(request, response, new Object()));
        request.setRequestURI("/api/v1/auth/refresh");
        assertTrue(interceptor.preHandle(request, response, new Object()));

        verify(limiter).checkAllowed("192.0.2.25", register);
        verify(limiter).checkAllowed("192.0.2.25", refresh);
    }

    @Test
    void skipsNonPostRequests() {
        AuthRateLimiter limiter = mock(AuthRateLimiter.class);
        AuthRateLimitInterceptor interceptor = new AuthRateLimitInterceptor(
                limiter,
                new LoginRateLimitProperties(
                        10, java.time.Duration.ofMinutes(1), "test:login", true),
                new RegisterRateLimitProperties(
                        5, java.time.Duration.ofHours(1), "test:register", true),
                new RefreshRateLimitProperties(
                        30, java.time.Duration.ofMinutes(1), "test:refresh", true));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("GET");
        request.setRequestURI("/api/v1/auth/login");

        assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));

        verifyNoInteractions(limiter);
    }
}
