package com.elsyah.tech.javara.integration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import tools.jackson.databind.ObjectMapper;

import com.elsyah.tech.javara.entity.user.UserEntity;
import com.elsyah.tech.javara.entity.user.UserRole;
import com.elsyah.tech.javara.middleware.security.ApiSecurityConfiguration;
import com.elsyah.tech.javara.service.auth.AccessTokenService;
import com.elsyah.tech.javara.testsupport.PostgresRedisTestSupport;
import com.elsyah.tech.javara.utils.security.PasswordHashService;

@SpringBootTest(properties = {
        "app.security.jwt.signing-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "app.security.jwt.transport=BEARER",
        "app.security.bootstrap-admin.enabled=false",
        "app.security.service-auth.enabled=true"
})
@AutoConfigureMockMvc
class ServiceClientCredentialsIntegrationTests extends PostgresRedisTestSupport {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccessTokenService accessTokenService;

    @Autowired
    private JwtDecoder jwtDecoder;

    @Autowired
    private ApiSecurityConfiguration apiSecurityConfiguration;

    @Autowired
    private RegisteredClientRepository registeredClientRepository;

    @Autowired
    private PasswordHashService passwordHashService;

    @Autowired
    private ObjectMapper objectMapper;

    private String provisionedClientId;

    @AfterEach
    void revokeProvisionedClient() {
        if (provisionedClientId != null) {
            RegisteredClient client = registeredClientRepository.findByClientId(provisionedClientId);
            if (client != null) {
                registeredClientRepository.save(RegisteredClient.from(client)
                        .clientSecret(passwordHashService.bcryptEncoder().encode(UUID.randomUUID().toString()))
                        .clientSecretExpiresAt(java.time.Instant.now())
                        .build());
            }
        }
    }

    @Test
    void adminCanProvisionRotateAndRevokeClientCredentialsWithoutCreatingUserRoles() throws Exception {
        String adminToken = userToken(UserRole.ADMIN);
        mockMvc.perform(post("/api/v1/service-clients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"inventory sync\",\"scopes\":[\"service.read\"]}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/service-clients")
                        .header("Authorization", "Bearer " + userToken(UserRole.USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"inventory sync\",\"scopes\":[\"service.read\"]}"))
                .andExpect(status().isForbidden());

        MvcResult created = mockMvc.perform(post("/api/v1/service-clients")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"inventory sync\",\"scopes\":[\"service.read\"]}"))
                .andExpect(status().isCreated())
                .andReturn();
        var createdData = objectMapper.readTree(created.getResponse().getContentAsString()).path("data");
        provisionedClientId = createdData.path("clientId").asString();
        String firstSecret = createdData.path("clientSecret").asString();
        assertFalse(provisionedClientId.isBlank());
        assertFalse(firstSecret.isBlank());

        RegisteredClient stored = registeredClientRepository.findByClientId(provisionedClientId);
        assertNotNull(stored);
        assertNotEquals(firstSecret, stored.getClientSecret());
        assertTrue(passwordHashService.matches(firstSecret, stored.getClientSecret()));
        assertTrue(stored.getAuthorizationGrantTypes().contains(
                org.springframework.security.oauth2.core.AuthorizationGrantType.CLIENT_CREDENTIALS));
        assertFalse(stored.getAuthorizationGrantTypes().contains(
                org.springframework.security.oauth2.core.AuthorizationGrantType.AUTHORIZATION_CODE));
        assertTrue(stored.getClientAuthenticationMethods().contains(
                org.springframework.security.oauth2.core.ClientAuthenticationMethod.CLIENT_SECRET_BASIC));
        assertFalse(stored.getClientAuthenticationMethods().contains(
                org.springframework.security.oauth2.core.ClientAuthenticationMethod.CLIENT_SECRET_POST));

        mockMvc.perform(post("/oauth2/token")
                        .header("Authorization", basic(provisionedClientId, firstSecret))
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "refresh_token")
                        .param("refresh_token", "unused"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/oauth2/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "client_credentials")
                        .param("client_id", provisionedClientId)
                        .param("client_secret", firstSecret)
                        .param("scope", "service.read"))
                .andExpect(status().isUnauthorized());

        String oldJwt = token(provisionedClientId, firstSecret);
        Jwt decoded = jwtDecoder.decode(oldJwt);
        org.junit.jupiter.api.Assertions.assertEquals("service", decoded.getClaimAsString("principal_type"));
        org.junit.jupiter.api.Assertions.assertEquals(provisionedClientId, decoded.getClaimAsString("client_id"));
        assertNull(decoded.getClaim("roles"));
        assertTrue(decoded.getClaimAsStringList("scope").contains("service.read"));

        AbstractAuthenticationToken serviceAuthentication =
                apiSecurityConfiguration.jwtAuthenticationConverter().convert(decoded);
        assertNotNull(serviceAuthentication);
        assertTrue(serviceAuthentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("SCOPE_service.read")));
        assertTrue(serviceAuthentication.getAuthorities().stream()
                .noneMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN")
                        || authority.getAuthority().equals("ROLE_USER")));

        MvcResult rotated = mockMvc.perform(post("/api/v1/service-clients/{clientId}/rotate", provisionedClientId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        String secondSecret = objectMapper.readTree(rotated.getResponse().getContentAsString())
                .path("data")
                .path("clientSecret")
                .asString();
        assertNotEquals(firstSecret, secondSecret);
        mockMvc.perform(post("/oauth2/token")
                        .header("Authorization", basic(provisionedClientId, firstSecret))
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "client_credentials")
                        .param("scope", "service.read"))
                .andExpect(status().isUnauthorized());

        String currentJwt = token(provisionedClientId, secondSecret);
        mockMvc.perform(get("/api/v1/users")
                        .header("Authorization", "Bearer " + currentJwt))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/v1/service-clients/{clientId}", provisionedClientId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/users")
                        .header("Authorization", "Bearer " + currentJwt))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/oauth2/token")
                        .header("Authorization", basic(provisionedClientId, secondSecret))
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "client_credentials")
                        .param("scope", "service.read"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void requestedScopesMustBeConfiguredForProvisioning() throws Exception {
        mockMvc.perform(post("/api/v1/service-clients")
                        .header("Authorization", "Bearer " + userToken(UserRole.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"unexpected scope\",\"scopes\":[\"service.admin\"]}"))
                .andExpect(status().isBadRequest());
    }

    private String token(String clientId, String secret) throws Exception {
        MvcResult result = mockMvc.perform(post("/oauth2/token")
                        .header("Authorization", basic(clientId, secret))
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "client_credentials")
                        .param("scope", "service.read"))
                .andExpect(status().isOk())
                .andReturn();
        var body = objectMapper.readTree(result.getResponse().getContentAsString());
        org.junit.jupiter.api.Assertions.assertEquals("Bearer", body.path("token_type").asString());
        assertTrue(body.path("expires_in").asLong() > 0);
        assertTrue(body.path("expires_in").asLong() <= 300);
        return body.path("access_token").asString();
    }

    private String userToken(UserRole role) {
        UserEntity user = new UserEntity();
        user.setId(UUID.randomUUID());
        user.setEmail("service-auth-" + UUID.randomUUID() + "@example.com");
        user.setRole(role);
        return accessTokenService.issue(user);
    }

    private static String basic(String clientId, String secret) {
        String credentials = clientId + ":" + secret;
        return "Basic " + java.util.Base64.getEncoder()
                .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }
}
