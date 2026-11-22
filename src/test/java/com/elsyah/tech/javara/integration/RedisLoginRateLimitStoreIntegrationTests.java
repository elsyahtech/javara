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

import com.elsyah.tech.javara.middleware.store.RedisLoginRateLimitStore;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "app.security.jwt.signing-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "app.security.jwt.transport=BEARER",
        "app.security.bootstrap-admin.enabled=false",
        "app.middleware.rate-limit.login.max-requests=1",
        "app.middleware.rate-limit.login.window=30s",
        "app.middleware.rate-limit.login.key-prefix=test:javara:login-rate-integration"
})
class RedisLoginRateLimitStoreIntegrationTests {
    @Autowired
    private RedisLoginRateLimitStore store;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private MockMvc mockMvc;

    @AfterEach
    void clearIntegrationKeys() {
        var keys = redisTemplate.keys("test:javara:login-rate-integration:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
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

    // Verifies that the registered MVC interceptor rejects excess login requests before validation.
    @Test
    void loginEndpointReturns429AfterConfiguredRequestLimit() throws Exception {
        String clientIp = "192.0.2.90";

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/auth/login")
                        .with(request -> {
                            request.setRemoteAddr(clientIp);
                            return request;
                        })
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"\",\"password\":\"\"}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .status().isBadRequest());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/auth/login")
                        .with(request -> {
                            request.setRemoteAddr(clientIp);
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
}
