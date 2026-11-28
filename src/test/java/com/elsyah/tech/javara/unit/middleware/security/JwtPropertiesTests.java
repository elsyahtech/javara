package com.elsyah.tech.javara.unit.middleware.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.elsyah.tech.javara.middleware.security.JwtProperties;
import com.elsyah.tech.javara.middleware.security.TokenTransport;

class JwtPropertiesTests {
    @Test
    void defaultsToCookieTransportAndRefreshTokenRotation() {
        JwtProperties properties = new JwtProperties(
                "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);

        assertEquals(TokenTransport.COOKIE, properties.transport());
        assertTrue(properties.refreshTokenRotationEnabled());
    }

    @Test
    void refreshTokenRotationCanBeDisabled() {
        JwtProperties properties = new JwtProperties(
                "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
                TokenTransport.BEARER,
                null,
                null,
                null,
                null,
                null,
                null,
                false);

        assertFalse(properties.refreshTokenRotationEnabled());
    }
}
