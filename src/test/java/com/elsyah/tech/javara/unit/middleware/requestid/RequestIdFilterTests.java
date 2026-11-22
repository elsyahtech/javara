package com.elsyah.tech.javara.unit.middleware.requestid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.elsyah.tech.javara.middleware.requestid.RequestIdFilter;
import com.elsyah.tech.javara.middleware.requestid.RequestIdProperties;

class RequestIdFilterTests {
    // Verifies that a valid incoming request ID is echoed and made available to handlers.
    @Test
    void preservesValidRequestId() throws Exception {
        RequestIdFilter filter = new RequestIdFilter(
                new RequestIdProperties(true, "X-Request-ID"));
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.addHeader("X-Request-ID", "client-request-123");

        filter.doFilter(request, response, (servletRequest, servletResponse) -> {});

        assertEquals("client-request-123", response.getHeader("X-Request-ID"));
        assertEquals(
                "client-request-123",
                request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE));
    }

    // Verifies that malformed incoming request IDs are replaced with a generated UUID.
    @Test
    void replacesMalformedRequestId() throws Exception {
        RequestIdFilter filter = new RequestIdFilter(
                new RequestIdProperties(true, "X-Request-ID"));
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.addHeader("X-Request-ID", "bad\r\nvalue");

        filter.doFilter(request, response, (servletRequest, servletResponse) -> {});

        String requestId = response.getHeader("X-Request-ID");
        assertTrue(isUuid(requestId));
        assertFalse(requestId.contains("\r"));
        assertFalse(requestId.contains("\n"));
    }

    private static boolean isUuid(String value) {
        try {
            UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
