package com.elsyah.tech.javara.middleware.security;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;

public class CookieOrServiceBearerTokenResolver implements BearerTokenResolver {
    private final CookieBearerTokenResolver cookieTokenResolver;
    private final DefaultBearerTokenResolver bearerTokenResolver = new DefaultBearerTokenResolver();
    private final JwtDecoder jwtDecoder;

    public CookieOrServiceBearerTokenResolver(String accessCookieName, JwtDecoder jwtDecoder) {
        this.cookieTokenResolver = new CookieBearerTokenResolver(accessCookieName);
        this.jwtDecoder = jwtDecoder;
    }

    @Override
    public String resolve(HttpServletRequest request) {
        String bearerToken = bearerTokenResolver.resolve(request);
        if (bearerToken != null && isServiceToken(bearerToken)) {
            return bearerToken;
        }
        return cookieTokenResolver.resolve(request);
    }

    private boolean isServiceToken(String value) {
        try {
            Jwt jwt = jwtDecoder.decode(value);
            return "service".equals(jwt.getClaimAsString("principal_type"));
        } catch (JwtException exception) {
            return false;
        }
    }
}
