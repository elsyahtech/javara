package com.elsyah.tech.javara.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.test.web.servlet.MockMvc;

import com.elsyah.tech.javara.middleware.security.JwtProperties;
import com.elsyah.tech.javara.testsupport.PostgresRedisTestSupport;
import com.elsyah.tech.javara.middleware.feature.FeatureModulesProperties;

@SpringBootTest(properties = {
        "app.features.auth-api=false",
        "app.features.resource-server=false",
        "app.features.rate-limiting=false",
        "app.security.service-auth.enabled=false",
        "app.security.bootstrap-admin.enabled=false"
})
@AutoConfigureMockMvc
class ResourceServerDisabledIntegrationTests extends PostgresRedisTestSupport {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private FeatureModulesProperties features;

    @Test
    void startsWithoutJwtConfigurationWhenResourceServerIsDisabled() throws Exception {
        assertThat(features.resourceServer()).isFalse();
        assertThat(applicationContext.getBeansOfType(JwtProperties.class))
                .isEmpty();
        mockMvc.perform(get("/")
                        .header("Authorization", "Bearer invalid"))
                .andExpect(status().isOk());
    }
}
