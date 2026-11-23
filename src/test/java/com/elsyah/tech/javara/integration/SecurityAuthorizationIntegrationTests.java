package com.elsyah.tech.javara.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.MockMvc;

import com.elsyah.tech.javara.entity.user.UserEntity;
import com.elsyah.tech.javara.entity.user.UserRole;
import com.elsyah.tech.javara.repository.user.UserRepository;
import com.elsyah.tech.javara.service.auth.AccessTokenService;
import com.elsyah.tech.javara.testsupport.PostgresRedisTestSupport;
import com.elsyah.tech.javara.utils.security.PasswordHashService;

@SpringBootTest(properties = {
        "app.security.jwt.signing-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "app.security.jwt.transport=BEARER",
        "app.security.bootstrap-admin.enabled=false"
})
@AutoConfigureMockMvc
class SecurityAuthorizationIntegrationTests extends PostgresRedisTestSupport {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccessTokenService accessTokenService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordHashService passwordHashService;

    private UUID createdUserId;

    @AfterEach
    void cleanUpTestUser() {
        if (createdUserId != null) {
            userRepository.deleteById(createdUserId);
        }
    }

    // Verifies that the welcome endpoint is public while user endpoints reject anonymous requests.
    @Test
    void welcomeIsPublicAndUsersRequireAuthentication() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/users"))
                .andExpect(status().isUnauthorized());
    }

    // Verifies that an authenticated USER cannot list all accounts.
    @Test
    void ordinaryUserCannotListAllUsers() throws Exception {
        String token = tokenFor(UUID.randomUUID());

        mockMvc.perform(get("/api/v1/users")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    // Verifies that an ADMIN token carries the authority required to list user accounts.
    @Test
    void adminCanListAllUsers() throws Exception {
        UserEntity admin = new UserEntity();
        admin.setId(UUID.randomUUID());
        admin.setEmail("admin@example.com");
        admin.setRole(UserRole.ADMIN);

        UserEntity existingUser = new UserEntity();
        createdUserId = UUID.randomUUID();
        existingUser.setId(createdUserId);
        existingUser.setEmail("authorization-" + createdUserId + "@example.com");
        existingUser.setPassword(passwordHashService.encode("unused-test-password"));
        existingUser.setIsActive(true);
        userRepository.saveAndFlush(existingUser);

        mockMvc.perform(get("/api/v1/users")
                        .header("Authorization", "Bearer " + accessTokenService.issue(admin)))
                .andExpect(status().isOk());
    }

    // Verifies that a USER can access their own route but cannot access another user's route.
    @Test
    void ordinaryUserCanOnlyReadTheirOwnUserId() throws Exception {
        UUID ownId = UUID.randomUUID();
        UUID otherId = UUID.randomUUID();
        String token = tokenFor(ownId);

        mockMvc.perform(get("/api/v1/users/" + ownId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/users/" + otherId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    // Verifies that an invalid access header does not block the public refresh-token flow.
    @Test
    void refreshEndpointIgnoresAnInvalidAccessTokenHeader() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/refresh")
                        .header("Authorization", "Bearer invalid-access-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"invalid-refresh-token\"}"))
                .andExpect(status().isUnauthorized())
                .andReturn();

        org.junit.jupiter.api.Assertions.assertTrue(result.getResponse().getContentAsString()
                .contains("Refresh token is invalid or expired"));
    }

    private String tokenFor(UUID userId) {
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setEmail("user@example.com");
        user.setRole(UserRole.USER);
        return accessTokenService.issue(user);
    }
}
