package com.elsyah.tech.javara.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import com.elsyah.tech.javara.middleware.ratelimit.AuthRateLimiter;
import com.elsyah.tech.javara.testsupport.PostgresRedisTestSupport;

@SpringBootTest(properties = {
        "app.features.auth-api=false",
        "app.features.resource-server=true",
        "app.features.rate-limiting=true",
        "app.security.service-auth.enabled=false",
        "app.security.jwt.signing-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "app.security.jwt.transport=BEARER",
        "app.security.bootstrap-admin.enabled=false"
})
@AutoConfigureMockMvc
class FeatureModulesIntegrationTests extends PostgresRedisTestSupport {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void keepsResourceServerActiveWhileDisabledModuleEndpointsReturnNotFound() throws Exception {
        assertThat(applicationContext.getBean(JwtDecoder.class)).isNotNull();
        assertThat(applicationContext.getBeansOfType(AuthRateLimiter.class)).isEmpty();

        mockMvc.perform(post("/api/v1/auth/login")
                        .header("Authorization", "Bearer invalid"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/auth/mfa")
                        .header("Authorization", "Bearer invalid"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/users")
                        .header("Authorization", "Bearer invalid"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/roles")
                        .header("Authorization", "Bearer invalid"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/service-clients")
                        .header("Authorization", "Bearer invalid"))
                .andExpect(status().isNotFound());
    }
}
