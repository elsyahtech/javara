package com.elsyah.tech.javara.unit.middleware.cors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.elsyah.tech.javara.middleware.cors.CorsProperties;

class CorsPropertiesTests {
    @Test
    void enabledCorsRequiresAnAllowedOrigin() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new CorsProperties(true, List.of(), null, null, null, false, 300));

        assertEquals(
                "CORS requires at least one allowed origin when enabled",
                exception.getMessage());
    }

    @Test
    void credentialsCannotBeCombinedWithWildcardOrigin() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new CorsProperties(true, List.of("*"), null, null, null, true, 300));

        assertEquals(
                "CORS credentials cannot be enabled with a wildcard origin",
                exception.getMessage());
    }

    @Test
    void acceptsAnOriginAndNormalizesItsTrailingSlash() {
        CorsProperties properties = new CorsProperties(
                true,
                List.of("https://erp.example.com/"),
                null,
                null,
                null,
                false,
                300);

        assertEquals(List.of("https://erp.example.com"), properties.allowedOrigins());
    }

    @Test
    void rejectsOriginsContainingAPath() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new CorsProperties(
                        true,
                        List.of("https://erp.example.com/api"),
                        null,
                        null,
                        null,
                        false,
                        300));

        assertEquals(
                "CORS origin must be an HTTP(S) origin without a path: https://erp.example.com/api",
                exception.getMessage());
    }
}
