package com.elsyah.tech.javara.unit.middleware.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import com.elsyah.tech.javara.middleware.security.CookieBearerTokenResolver;

class CookieBearerTokenResolverTests {
    // Verifies that the configured access-token cookie is used as the bearer credential.
    @Test
    void resolvesAccessTokenFromCookie() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("ACCESS_TOKEN", "jwt-token"));

        String token = new CookieBearerTokenResolver("ACCESS_TOKEN").resolve(request);

        assertEquals("jwt-token", token);
    }

    // Verifies that duplicate access-token cookies are rejected instead of choosing ambiguously.
    @Test
    void rejectsDuplicateAccessTokenCookies() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(
                new Cookie("ACCESS_TOKEN", "first"),
                new Cookie("ACCESS_TOKEN", "second"));

        String token = new CookieBearerTokenResolver("ACCESS_TOKEN").resolve(request);

        assertNull(token);
    }

    // Verifies that a request without the configured cookie has no bearer credential.
    @Test
    void returnsNullWhenAccessTokenCookieIsMissing() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        assertNull(new CookieBearerTokenResolver("ACCESS_TOKEN").resolve(request));
    }
}
