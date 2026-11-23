package com.elsyah.tech.javara.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.servlet.http.Cookie;

import java.util.UUID;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

import tools.jackson.databind.ObjectMapper;

import com.elsyah.tech.javara.entity.user.UserEntity;
import com.elsyah.tech.javara.request.auth.LoginRequest;
import com.elsyah.tech.javara.service.auth.AuthService;
import com.elsyah.tech.javara.service.auth.AccessTokenService;
import com.elsyah.tech.javara.testsupport.PostgresRedisTestSupport;
import com.elsyah.tech.javara.repository.auth.RefreshTokenRepository;
import com.elsyah.tech.javara.repository.user.UserRepository;
import com.elsyah.tech.javara.utils.security.PasswordHashService;

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

    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private PasswordHashService passwordHashService;

    @Autowired
    private RegisteredClientRepository registeredClientRepository;

    @Autowired
    private ObjectMapper objectMapper;

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
        user.setId(UUID.randomUUID());
        user.setEmail("user@example.com");
        String token = accessTokenService.issue(user);

        mockMvc.perform(get("/api/v1/users")
                        .cookie(new Cookie("ACCESS_TOKEN", token)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/users")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void serviceBearerWorksInCookieModeWithoutAcceptingUserBearerTokens() throws Exception {
        String clientId = "cookie-service-" + UUID.randomUUID();
        String clientSecret = UUID.randomUUID().toString();
        RegisteredClient client = RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId(clientId)
                .clientSecret(passwordHashService.bcryptEncoder().encode(clientSecret))
                .clientName("cookie-mode service")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .scope("service.read")
                .build();
        registeredClientRepository.save(client);

        String basic = java.util.Base64.getEncoder().encodeToString(
                (clientId + ":" + clientSecret).getBytes(StandardCharsets.UTF_8));
        MvcResult result = mockMvc.perform(post("/oauth2/token")
                        .header("Authorization", "Basic " + basic)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "client_credentials")
                        .param("scope", "service.read"))
                .andExpect(status().isOk())
                .andReturn();
        String token = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("access_token")
                .asString();

        mockMvc.perform(get("/api/v1/users")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    // Verifies that an expired access cookie does not prevent logout with a valid refresh cookie.
    @Test
    void logoutRevokesRefreshTokenWhenAccessCookieIsExpired() throws Exception {
        String email = UUID.randomUUID() + "@example.com";
        String password = "logout-test-password";
        UserEntity user = new UserEntity();
        user.setId(UUID.randomUUID());
        user.setEmail(email);
        user.setPassword(passwordHashService.encode(password));
        userRepository.saveAndFlush(user);

        String refreshToken = authService.login(new LoginRequest(email, password)).refreshToken();

        MvcResult csrfResult = mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn();
        String csrfToken = csrfResult.getResponse().getCookie("XSRF-TOKEN").getValue();

        mockMvc.perform(post("/api/v1/auth/logout")
                        .cookie(
                                new Cookie("XSRF-TOKEN", csrfToken),
                                new Cookie("ACCESS_TOKEN", "expired-access-token"),
                                new Cookie("REFRESH_TOKEN", refreshToken))
                        .header("X-XSRF-TOKEN", csrfToken))
                .andExpect(status().isOk());

        assertTrue(refreshTokenRepository
                .findByUserIdAndRevokedAtIsNull(user.getId())
                .isEmpty());
    }
}
