package com.elsyah.tech.javara.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import com.elsyah.tech.javara.entity.user.UserEntity;
import com.elsyah.tech.javara.entity.user.UserRole;
import com.elsyah.tech.javara.repository.auth.RefreshTokenRepository;
import com.elsyah.tech.javara.repository.user.UserRepository;
import com.elsyah.tech.javara.service.auth.AccessTokenService;
import com.elsyah.tech.javara.service.auth.AuthService;
import com.elsyah.tech.javara.service.auth.AuthServiceResult;
import com.elsyah.tech.javara.testsupport.PostgresRedisTestSupport;
import com.elsyah.tech.javara.testsupport.SessionTokenTestSupport;
import com.elsyah.tech.javara.request.auth.LoginRequest;
import com.elsyah.tech.javara.request.user.UserRequest;
import com.elsyah.tech.javara.request.user.UserUpdateRequest;
import com.elsyah.tech.javara.service.user.UserService;
import com.elsyah.tech.javara.utils.security.PasswordHashService;

@SpringBootTest(properties = {
        "app.security.jwt.signing-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "app.security.jwt.transport=BEARER",
        "app.security.bootstrap-admin.enabled=false"
})
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class SecurityAuthorizationIntegrationTests extends PostgresRedisTestSupport {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccessTokenService accessTokenService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private PasswordHashService passwordHashService;

    @Autowired
    private AuthService authService;

    @Autowired
    private UserService userService;

    @Autowired
    private JwtDecoder jwtDecoder;

    private final List<UUID> createdUserIds = new ArrayList<>();

    @AfterEach
    void cleanUpTestUser() {
        for (UUID userId : createdUserIds) {
            refreshTokenRepository.deleteByUserId(userId);
            userRepository.deleteById(userId);
        }
        createdUserIds.clear();
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
        admin.setEmail("admin-" + UUID.randomUUID() + "@example.com");
        admin.setRole(UserRole.ADMIN);
        String adminToken = tokenFor(admin);

        UserEntity existingUser = new UserEntity();
        existingUser.setId(UUID.randomUUID());
        existingUser.setEmail("authorization-" + existingUser.getId() + "@example.com");
        existingUser.setPassword(passwordHashService.encode("unused-test-password"));
        existingUser.setIsActive(true);
        userRepository.saveAndFlush(existingUser);
        createdUserIds.add(existingUser.getId());

        mockMvc.perform(get("/api/v1/users")
                        .header("Authorization", "Bearer " + adminToken))
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
                .andExpect(status().isOk());

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

    @Test
    void logoutInvalidatesAnAlreadyIssuedAccessToken() throws Exception {
        UserEntity user = createUser("logout-password");
        AuthServiceResult login = authService.login(
                new LoginRequest(user.getEmail(), "logout-password"));
        assertEquals(HttpStatus.OK, login.status());

        mockMvc.perform(get("/api/v1/users/" + user.getId())
                        .header("Authorization", "Bearer " + login.accessToken()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + login.refreshToken() + "\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/users/" + user.getId())
                        .header("Authorization", "Bearer " + login.accessToken()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void disablingAnAccountInvalidatesItsExistingAccessToken() throws Exception {
        UserEntity user = createUser("disable-password");
        AuthServiceResult login = authService.login(
                new LoginRequest(user.getEmail(), "disable-password"));
        assertEquals(HttpStatus.OK, login.status());

        var updateResult = userService.updateUser(
                user.getId(),
                new UserUpdateRequest(user.getEmail(), false));
        assertEquals(HttpStatus.OK, updateResult.status());

        mockMvc.perform(get("/api/v1/users/" + user.getId())
                        .header("Authorization", "Bearer " + login.accessToken()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void userCanReauthenticateToListAndTerminateASession() throws Exception {
        UserEntity user = createUser("session-password");
        AuthServiceResult firstLogin = authService.login(
                new LoginRequest(user.getEmail(), "session-password"));
        AuthServiceResult secondLogin = authService.login(
                new LoginRequest(user.getEmail(), "session-password"));
        UUID firstSessionId = UUID.fromString(
                jwtDecoder.decode(firstLogin.accessToken()).getClaimAsString("sid"));

        MvcResult sessions = mockMvc.perform(get("/api/v1/auth/sessions")
                        .header("Authorization", "Bearer " + secondLogin.accessToken()))
                .andExpect(status().isOk())
                .andReturn();
        org.junit.jupiter.api.Assertions.assertTrue(
                sessions.getResponse().getContentAsString().contains("\"current\":true"));

        mockMvc.perform(post("/api/v1/auth/sessions/" + firstSessionId + "/terminate")
                        .header("Authorization", "Bearer " + secondLogin.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/users/" + user.getId())
                        .header("Authorization", "Bearer " + firstLogin.accessToken()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/sessions/" + firstSessionId + "/terminate")
                        .header("Authorization", "Bearer " + secondLogin.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"session-password\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/users/" + user.getId())
                        .header("Authorization", "Bearer " + firstLogin.accessToken()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/users/" + user.getId())
                        .header("Authorization", "Bearer " + secondLogin.accessToken()))
                .andExpect(status().isOk());
    }

    @Test
    void administratorCanReauthenticateToTerminateAnotherUsersSessions() throws Exception {
        UserEntity admin = createUser("admin-session-password", UserRole.ADMIN);
        UserEntity user = createUser("target-session-password");
        AuthServiceResult adminLogin = authService.login(
                new LoginRequest(admin.getEmail(), "admin-session-password"));
        AuthServiceResult userLogin = authService.login(
                new LoginRequest(user.getEmail(), "target-session-password"));

        mockMvc.perform(post(
                        "/api/v1/admin/users/" + user.getId() + "/sessions/terminate")
                        .header("Authorization", "Bearer " + adminLogin.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"admin-session-password\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/users/" + user.getId())
                        .header("Authorization", "Bearer " + userLogin.accessToken()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void userMustReauthenticateToChangePasswordAndAllExistingSessionsAreRevoked()
            throws Exception {
        UserEntity user = createUser("old-password-123");
        AuthServiceResult login = authService.login(
                new LoginRequest(user.getEmail(), "old-password-123"));
        assertEquals(HttpStatus.OK, login.status());

        mockMvc.perform(post("/api/v1/auth/password/change")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"old-password-123\","
                                + "\"newPassword\":\"new-password-123\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/password/change")
                        .header("Authorization", "Bearer " + login.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"incorrect-password\","
                                + "\"newPassword\":\"new-password-123\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/password/change")
                        .header("Authorization", "Bearer " + login.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"old-password-123\","
                                + "\"newPassword\":\"new-password-123\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/users/" + user.getId())
                        .header("Authorization", "Bearer " + login.accessToken()))
                .andExpect(status().isUnauthorized());
        assertEquals(HttpStatus.UNAUTHORIZED, authService.login(
                new LoginRequest(user.getEmail(), "old-password-123")).status());
        assertEquals(HttpStatus.OK, authService.login(
                new LoginRequest(user.getEmail(), "new-password-123")).status());
    }

    @Test
    void userUpdateEndpointCannotChangePassword(CapturedOutput output) throws Exception {
        UserEntity user = createUser("unchanged-password");
        AuthServiceResult login = authService.login(
                new LoginRequest(user.getEmail(), "unchanged-password"));

        MvcResult result = mockMvc.perform(put("/api/v1/users/" + user.getId())
                        .header("Authorization", "Bearer " + login.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + user.getEmail()
                                + "\",\"password\":\"unverified-new-password\","
                                + "\"isActive\":true}"))
                .andExpect(status().isBadRequest())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertFalse(
                responseBody.contains("unverified-new-password"));
        org.junit.jupiter.api.Assertions.assertTrue(
                responseBody.contains("dedicated password-change endpoint"));
        org.junit.jupiter.api.Assertions.assertFalse(
                output.getAll().contains("unverified-new-password"));

        assertEquals(HttpStatus.OK, authService.login(
                new LoginRequest(user.getEmail(), "unchanged-password")).status());
        assertEquals(HttpStatus.UNAUTHORIZED, authService.login(
                new LoginRequest(user.getEmail(), "unverified-new-password")).status());
    }

    private UserEntity createUser(String password) {
        return createUser(password, UserRole.USER);
    }

    private UserEntity createUser(String password, UserRole role) {
        UserEntity user = new UserEntity();
        user.setId(UUID.randomUUID());
        user.setEmail("session-" + user.getId() + "@example.com");
        user.setPassword(passwordHashService.encode(password));
        user.setIsActive(true);
        user.setRole(role);
        userRepository.saveAndFlush(user);
        createdUserIds.add(user.getId());
        return user;
    }

    private String tokenFor(UUID userId) {
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setEmail("authorization-" + userId + "@example.com");
        user.setRole(UserRole.USER);
        return tokenFor(user);
    }

    private String tokenFor(UserEntity user) {
        createdUserIds.add(user.getId());
        return SessionTokenTestSupport.issueAccessToken(
                user,
                userRepository,
                refreshTokenRepository,
                accessTokenService,
                passwordHashService);
    }
}
