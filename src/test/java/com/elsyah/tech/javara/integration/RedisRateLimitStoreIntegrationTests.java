package com.elsyah.tech.javara.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.elsyah.tech.javara.middleware.store.RedisRateLimitStore;
import com.elsyah.tech.javara.testsupport.PostgresRedisTestSupport;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "app.security.jwt.signing-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "app.security.jwt.transport=BEARER",
        "app.security.bootstrap-admin.enabled=false",
        "app.middleware.rate-limit.login.max-requests=1",
        "app.middleware.rate-limit.login.window=30s",
        "app.middleware.rate-limit.login.key-prefix=test:javara:login-rate-integration",
        "app.middleware.rate-limit.register.max-requests=1",
        "app.middleware.rate-limit.register.window=30s",
        "app.middleware.rate-limit.register.key-prefix=test:javara:register-rate-integration",
        "app.middleware.rate-limit.refresh.max-requests=1",
        "app.middleware.rate-limit.refresh.window=30s",
        "app.middleware.rate-limit.refresh.key-prefix=test:javara:refresh-rate-integration",
        "server.request.header.forwarded.strategy=framework"
})
class RedisRateLimitStoreIntegrationTests extends PostgresRedisTestSupport {
    @Autowired
    private RedisRateLimitStore store;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private MockMvc mockMvc;

    @AfterEach
    void clearIntegrationKeys() {
        for (String prefix : List.of(
                "test:javara:login-rate-integration:*",
                "test:javara:register-rate-integration:*",
                "test:javara:refresh-rate-integration:*")) {
            var keys = redisTemplate.keys(prefix);
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        }
    }

    // Verifies that Redis applies the configured limit and expiration to a fixed-window key.
    @Test
    void incrementsCounterAtomicallyAndSetsWindowExpiration() {
        String key = "test:login-rate-limit:" + UUID.randomUUID();
        Duration window = Duration.ofSeconds(30);

        try {
            assertThat(store.incrementAndGetRetryAfterMillis(key, 2, window)).isZero();
            assertThat(store.incrementAndGetRetryAfterMillis(key, 2, window)).isZero();
            long retryAfterMillis = store.incrementAndGetRetryAfterMillis(key, 2, window);

            assertThat(retryAfterMillis).isPositive().isLessThanOrEqualTo(window.toMillis());
            assertThat(redisTemplate.getExpire(key, TimeUnit.MILLISECONDS)).isPositive();
        } finally {
            redisTemplate.delete(key);
        }
    }

    // Verifies that concurrent increments never allow more requests than the configured maximum.
    @Test
    void concurrentRequestsRespectTheAtomicLimit() throws Exception {
        String key = "test:login-rate-limit:" + UUID.randomUUID();
        int maxRequests = 8;
        List<Callable<Long>> requests = new ArrayList<>();
        for (int i = 0; i < 32; i++) {
            requests.add(() -> store.incrementAndGetRetryAfterMillis(
                    key, maxRequests, Duration.ofSeconds(30)));
        }

        try (ExecutorService executor = Executors.newFixedThreadPool(8)) {
            List<Future<Long>> results = executor.invokeAll(requests);
            List<Long> retryAfterMillis = new ArrayList<>();
            for (Future<Long> result : results) {
                retryAfterMillis.add(result.get());
            }

            assertThat(retryAfterMillis.stream().filter(value -> value == 0).count())
                    .isEqualTo(maxRequests);
            assertThat(retryAfterMillis.stream().filter(value -> value > 0).count())
                    .isEqualTo(32 - maxRequests);
        } finally {
            redisTemplate.delete(key);
        }
    }

    // Verifies that framework mode consumes a forwarded address; the proxy must sanitize it.
    @Test
    void loginEndpointUsesForwardedAddressWhenFrameworkStrategyIsEnabled() throws Exception {
        String clientIp = "192.0.2.90";

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/auth/login")
                        .header("X-Forwarded-For", clientIp)
                        .with(request -> {
                            request.setRemoteAddr("10.0.0.10");
                            return request;
                        })
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"\",\"password\":\"\"}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .status().isBadRequest());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/auth/login")
                        .header("X-Forwarded-For", clientIp)
                        .with(request -> {
                            request.setRemoteAddr("10.0.0.11");
                            return request;
                        })
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"\",\"password\":\"\"}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .status().isTooManyRequests())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .header().exists("Retry-After"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.httpCode").value(429));
    }

    @Test
    void registerEndpointReturns429AfterConfiguredRequestLimit() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/auth/register")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"rate-limit-one@example.com","password":"password123"}
                                """))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .status().isServiceUnavailable());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/auth/register")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"rate-limit-two@example.com","password":"password123"}
                                """))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .status().isTooManyRequests())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .header().exists("Retry-After"));
    }

    @Test
    void refreshEndpointReturns429AfterConfiguredRequestLimit() throws Exception {
        String request = """
                {"refreshToken":"invalid-refresh-token"}
                """;
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/auth/refresh")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .status().isUnauthorized());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/auth/refresh")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .status().isTooManyRequests())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .header().exists("Retry-After"));
    }
}
