package com.elsyah.tech.javara.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.elsyah.tech.javara.entity.UserEntity;
import com.elsyah.tech.javara.service.AccessTokenService;
import com.elsyah.tech.javara.testsupport.PostgresRedisTestSupport;

@SpringBootTest(properties = {
        "app.security.jwt.signing-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "app.security.jwt.transport=COOKIE",
        "app.security.jwt.cookie-secure=false",
        "app.security.bootstrap-admin.enabled=false"
})
@AutoConfigureMockMvc
class CookieSecurityIntegrationTests extends PostgresRedisTestSupport {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccessTokenService accessTokenService;

    // Verifies that cookie mode requires CSRF and accepts the issued CSRF cookie/header pair.
    @Test
    void unsafeRequestsRequireCsrfCookieAndHeader() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"\",\"password\":\"\"}"))
                .andExpect(status().isForbidden());

        MvcResult csrfResult = mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn();
        Cookie csrfResponseCookie = csrfResult.getResponse().getCookie("XSRF-TOKEN");
        String csrfToken = csrfResponseCookie.getValue();

        mockMvc.perform(post("/api/v1/auth/login")
                        .cookie(new Cookie("XSRF-TOKEN", csrfToken))
                        .header("X-XSRF-TOKEN", csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest());

        MvcResult refreshResult = mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(
                                new Cookie("XSRF-TOKEN", csrfToken),
                                new Cookie("ACCESS_TOKEN", "expired-access-token"),
                                new Cookie("REFRESH_TOKEN", "invalid-refresh-token"))
                        .header("X-XSRF-TOKEN", csrfToken))
                .andExpect(status().isUnauthorized())
                .andReturn();
        assertTrue(refreshResult.getResponse().getContentAsString()
                .contains("Refresh token is invalid or expired"));
    }

    // Verifies that the configured access cookie authenticates requests to protected routes.
    @Test
    void accessCookieAuthenticatesProtectedRoutes() throws Exception {
        UserEntity user = new UserEntity();
        user.setId("user-1");
        user.setEmail("user@example.com");
        String token = accessTokenService.issue(user);

        mockMvc.perform(get("/api/v1/users")
                        .cookie(new Cookie("ACCESS_TOKEN", token)))
                .andExpect(status().isForbidden());
    }
}
