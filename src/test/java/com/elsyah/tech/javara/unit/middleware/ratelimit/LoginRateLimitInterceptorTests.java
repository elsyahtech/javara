package com.elsyah.tech.javara.unit.middleware.ratelimit;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.elsyah.tech.javara.middleware.interceptor.LoginRateLimitInterceptor;
import com.elsyah.tech.javara.middleware.ratelimit.LoginRateLimiter;

class LoginRateLimitInterceptorTests {
    // Verifies that a login POST is checked using the request's direct client address.
    @Test
    void checksClientAddressForPostRequests() {
        LoginRateLimiter limiter = mock(LoginRateLimiter.class);
        LoginRateLimitInterceptor interceptor = new LoginRateLimitInterceptor(limiter);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.setRemoteAddr("192.0.2.25");

        assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));

        verify(limiter).checkAllowed("192.0.2.25");
    }

    // Verifies that non-POST requests do not consume login rate-limit capacity.
    @Test
    void skipsRateLimitForNonPostRequests() {
        LoginRateLimiter limiter = mock(LoginRateLimiter.class);
        LoginRateLimitInterceptor interceptor = new LoginRateLimitInterceptor(limiter);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("GET");

        assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));

        verifyNoInteractions(limiter);
    }
}
