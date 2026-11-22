package com.elsyah.tech.javara.middleware.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;

public class CookieBearerTokenResolver implements BearerTokenResolver {
    private final String accessCookieName;

    public CookieBearerTokenResolver(String accessCookieName) {
        this.accessCookieName = accessCookieName;
    }

    @Override
    public String resolve(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }

        String value = null;
        for (Cookie cookie : cookies) {
            if (accessCookieName.equals(cookie.getName())) {
                if (value != null) {
                    return null;
                }
                value = cookie.getValue();
            }
        }
        return value == null || value.isBlank() ? null : value;
    }
}
