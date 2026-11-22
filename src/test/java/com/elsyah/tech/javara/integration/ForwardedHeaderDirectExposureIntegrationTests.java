package com.elsyah.tech.javara.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.elsyah.tech.javara.testsupport.PostgresRedisTestSupport;

@SpringBootTest(properties = {
        "app.security.jwt.signing-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "app.security.jwt.transport=BEARER",
        "app.security.bootstrap-admin.enabled=false",
        "app.middleware.rate-limit.login.max-requests=1",
        "app.middleware.rate-limit.login.window=30s",
        "app.middleware.rate-limit.login.key-prefix=test:javara:direct-exposure:login",
        "server.forward-headers-strategy=none"
})
@AutoConfigureMockMvc
class ForwardedHeaderDirectExposureIntegrationTests extends PostgresRedisTestSupport {
    private static final String RATE_LIMIT_KEY_PATTERN =
            "test:javara:direct-exposure:login:*";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @AfterEach
    void clearRateLimitKeys() {
        var keys = redisTemplate.keys(RATE_LIMIT_KEY_PATTERN);
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    // Verifies that rotating a forwarded IP cannot bypass limits when proxy handling is disabled.
    @Test
    void forwardedHeadersDoNotChangeDirectClientRateLimitKey() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .header("X-Forwarded-For", "203.0.113.10")
                        .with(request -> {
                            request.setRemoteAddr("192.0.2.25");
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/auth/login")
                        .header("X-Forwarded-For", "203.0.113.11")
                        .with(request -> {
                            request.setRemoteAddr("192.0.2.25");
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"\",\"password\":\"\"}"))
                .andExpect(status().isTooManyRequests());
    }
}
