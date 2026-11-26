package com.elsyah.tech.javara.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.elsyah.tech.javara.entity.auth.EmailVerificationTokenEntity;
import com.elsyah.tech.javara.entity.auth.PasswordResetTokenEntity;
import com.elsyah.tech.javara.entity.user.UserEntity;
import com.elsyah.tech.javara.entity.user.UserRole;
import com.elsyah.tech.javara.repository.auth.AdminPasswordResetAuditRepository;
import com.elsyah.tech.javara.repository.auth.EmailVerificationTokenRepository;
import com.elsyah.tech.javara.repository.auth.PasswordResetTokenRepository;
import com.elsyah.tech.javara.repository.auth.RefreshTokenRepository;
import com.elsyah.tech.javara.repository.user.UserRepository;
import com.elsyah.tech.javara.service.auth.AuthService;
import com.elsyah.tech.javara.service.auth.AuthServiceResult;
import com.elsyah.tech.javara.service.email.EmailDelivery;
import com.elsyah.tech.javara.service.email.EmailEventType;
import com.elsyah.tech.javara.service.email.EmailMessageEvent;
import com.elsyah.tech.javara.service.email.EmailVerificationService;
import com.elsyah.tech.javara.service.user.UserService;
import com.elsyah.tech.javara.testsupport.PostgresRedisTestSupport;
import com.elsyah.tech.javara.utils.security.PasswordHashService;
import com.elsyah.tech.javara.request.auth.LoginRequest;

@SpringBootTest(properties = {
        "app.security.jwt.signing-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "app.security.jwt.transport=BEARER",
        "app.security.bootstrap-admin.enabled=false",
        "app.middleware.rate-limit.session-reauth.max-requests=100",
        "app.email.enabled=true",
        "app.email.from-address=notifications@example.com",
        "app.email.public-base-url=http://localhost:8080",
        "spring.mail.host=localhost",
        "spring.mail.username=test@example.com",
        "spring.mail.password=test-only",
        "spring.mail.properties.mail.smtp.auth=true",
        "spring.mail.properties.mail.smtp.starttls.enable=true"
})
@AutoConfigureMockMvc
@Import(EmailVerificationIntegrationTests.TestMailConfiguration.class)
class EmailVerificationIntegrationTests extends PostgresRedisTestSupport {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private EmailVerificationTokenRepository verificationTokenRepository;

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    private AdminPasswordResetAuditRepository passwordResetAuditRepository;

    @Autowired
    private PasswordHashService passwordHashService;

    @Autowired
    private AuthService authService;

    @Autowired
    private UserService userService;

    @Autowired
    private RecordingEmailDelivery emailDelivery;

    private final List<UUID> createdUserIds = new ArrayList<>();

    @AfterEach
    void cleanUpUsers() {
        for (UUID userId : createdUserIds) {
            passwordResetAuditRepository.deleteByActorUserId(userId);
            passwordResetAuditRepository.deleteByTargetUserId(userId);
            passwordResetTokenRepository.deleteByUserId(userId);
            verificationTokenRepository.deleteByUserId(userId);
            refreshTokenRepository.deleteByUserId(userId);
            userRepository.deleteById(userId);
        }
        createdUserIds.clear();
        emailDelivery.clear();
    }

    @Test
    void registrationRequiresOneTimeEmailVerificationBeforeLogin() throws Exception {
        String email = "verify-" + UUID.randomUUID() + "@example.com";
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"unique-password-123"}
                                """.formatted(email)))
                .andExpect(status().isCreated());

        UserEntity user = userRepository.findByEmail(email).orElseThrow();
        createdUserIds.add(user.getId());
        assertFalse(user.isEmailVerified());
        assertEquals(
                HttpStatus.UNAUTHORIZED,
                authService.login(new LoginRequest(email, "unique-password-123")).status());

        EmailMessageEvent verification = emailDelivery.last(EmailEventType.REGISTRATION_VERIFICATION);
        String token = tokenFromBody(verification.body());
        EmailVerificationTokenEntity storedToken = verificationTokenRepository.findAll().stream()
                .filter(item -> item.getUserId().equals(user.getId()))
                .findFirst()
                .orElseThrow();
        assertStoredHashMatchesToken(storedToken, token);

        mockMvc.perform(get("/api/v1/auth/email/verify").param("token", token))
                .andExpect(status().isOk())
                .andExpect(result -> assertTrue(result.getResponse().getContentAsString()
                        .contains("Confirm email")));
        assertFalse(userRepository.findById(user.getId()).orElseThrow().isEmailVerified());

        mockMvc.perform(post("/api/v1/auth/email/verify").param("token", token))
                .andExpect(status().isOk());
        assertTrue(userRepository.findById(user.getId()).orElseThrow().isEmailVerified());
        assertEquals(
                HttpStatus.OK,
                authService.login(new LoginRequest(email, "unique-password-123")).status());
        assertNotNull(emailDelivery.last(EmailEventType.NEW_SESSION));
    }

    @Test
    void emailChangeKeepsOldAddressUntilNewAddressIsVerifiedAndNotifiesOldAddress()
            throws Exception {
        UserEntity user = createVerifiedUser("current-password-123");
        String newEmail = "changed-" + UUID.randomUUID() + "@example.com";

        var result = userService.requestEmailChange(
                user.getId(),
                user.getId(),
                "current-password-123",
                newEmail,
                false);

        assertEquals(HttpStatus.ACCEPTED, result.status());
        UserEntity pending = userRepository.findById(user.getId()).orElseThrow();
        assertEquals("verified-" + user.getId() + "@example.com", pending.getEmail());
        assertEquals(newEmail, pending.getPendingEmail());
        EmailMessageEvent confirmation =
                emailDelivery.last(EmailEventType.EMAIL_CHANGE_VERIFICATION);
        assertEquals(newEmail, confirmation.recipient());
        String token = tokenFromBody(confirmation.body());

        mockMvc.perform(post("/api/v1/auth/email/verify").param("token", token))
                .andExpect(status().isOk());

        UserEntity updated = userRepository.findById(user.getId()).orElseThrow();
        assertEquals(newEmail, updated.getEmail());
        assertNull(updated.getPendingEmail());
        EmailMessageEvent notice = emailDelivery.last(EmailEventType.EMAIL_CHANGED);
        assertEquals("verified-" + user.getId() + "@example.com", notice.recipient());
    }

    @Test
    void passwordChangeSendsSecurityNoticeAfterCommit() {
        UserEntity user = createVerifiedUser("current-password-123");

        var result = userService.changePassword(
                user.getId(), "current-password-123", "new-password-123");

        assertEquals(HttpStatus.OK, result.status());
        EmailMessageEvent notice = emailDelivery.last(EmailEventType.PASSWORD_CHANGED);
        assertEquals(user.getEmail(), notice.recipient());
    }

    @Test
    void emergencyPasswordRandomizationLocksAccountUntilSupportAuthorizedRecovery()
            throws Exception {
        UserEntity admin = createVerifiedUser("admin-current-password-123");
        admin.setRole(UserRole.ADMIN);
        userRepository.saveAndFlush(admin);
        UserEntity target = createVerifiedUser("target-current-password-123");
        var adminLogin = authService.login(
                new LoginRequest(admin.getEmail(), "admin-current-password-123"));
        var targetLogin = authService.login(
                new LoginRequest(target.getEmail(), "target-current-password-123"));
        assertEquals(HttpStatus.OK, adminLogin.status());
        assertEquals(HttpStatus.OK, targetLogin.status());

        String oldPasswordHash = target.getPassword();
        mockMvc.perform(post(
                                "/api/v1/admin/users/{userId}/security/force-password-reset",
                                target.getId())
                        .header("Authorization", "Bearer " + adminLogin.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "currentPassword":"admin-current-password-123",
                                  "reason":"Suspected credential compromise",
                                  "notificationMessage":"We detected activity that may put your account at risk. Contact support using the verified phone number on file."
                                }
                                """))
                .andExpect(status().isAccepted());

        UserEntity forced = userRepository.findById(target.getId()).orElseThrow();
        assertTrue(forced.isPasswordResetRequired());
        assertTrue(forced.isPasswordResetSupportRequired());
        assertNotEquals(oldPasswordHash, forced.getPassword());
        assertEquals(
                1,
                passwordResetAuditRepository.findAll().stream()
                        .filter(audit -> target.getId().equals(audit.getTargetUserId())
                                && "EMERGENCY_RANDOMIZED".equals(audit.getAction())
                                && "Suspected credential compromise".equals(audit.getReason()))
                        .count());
        mockMvc.perform(get("/api/v1/auth/sessions")
                        .header("Authorization", "Bearer " + targetLogin.accessToken()))
                .andExpect(status().isUnauthorized());
        assertEquals(
                HttpStatus.UNAUTHORIZED,
                authService.login(new LoginRequest(
                        target.getEmail(), "target-current-password-123")).status());

        mockMvc.perform(post("/api/v1/auth/password/reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s"}
                                """.formatted(target.getEmail())))
                .andExpect(status().isAccepted());
        assertTrue(passwordResetTokenRepository.findAll().stream()
                .noneMatch(item -> item.getUserId().equals(target.getId())));
        assertFalse(emailDelivery.has(EmailEventType.PASSWORD_RESET_REQUESTED));

        EmailMessageEvent emergencyNotice =
                emailDelivery.last(EmailEventType.EMERGENCY_PASSWORD_RESET);
        assertEquals(target.getEmail(), emergencyNotice.recipient());
        assertTrue(emergencyNotice.body().contains("We detected activity"));
        assertFalse(emergencyNotice.body().contains("token="));
        assertFalse(emergencyNotice.body().contains("target-current-password-123"));

        mockMvc.perform(post(
                                "/api/v1/admin/users/{userId}/password/recovery/authorize",
                                target.getId())
                        .header("Authorization", "Bearer " + adminLogin.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "currentPassword":"admin-current-password-123",
                                  "supportCaseReference":"SUP-1234"
                                }
                                """))
                .andExpect(status().isAccepted());
        assertTrue(userRepository.findById(target.getId()).orElseThrow()
                .isPasswordResetRequired());

        EmailMessageEvent resetEmail =
                emailDelivery.last(EmailEventType.PASSWORD_RESET_SUPPORT_AUTHORIZED);
        String token = tokenFromBody(resetEmail.body());
        PasswordResetTokenEntity storedToken = passwordResetTokenRepository.findAll().stream()
                .filter(item -> item.getUserId().equals(target.getId()))
                .findFirst()
                .orElseThrow();
        assertStoredHashMatchesToken(storedToken, token);
        mockMvc.perform(post("/api/v1/auth/password/reset")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("token", token)
                        .param("newPassword", "target-current-password-123"))
                .andExpect(status().isBadRequest())
                .andExpect(result -> {
                    String page = result.getResponse().getContentAsString();
                    assertTrue(page.contains(
                            "This password was previously invalidated during an emergency reset."));
                    assertTrue(page.contains("name=\"newPassword\""));
                    assertTrue(page.contains("name=\"token\" value=\"" + token + "\""));
                    assertTrue(page.contains(">Set password</button>"));
                    assertFalse(page.contains("target-current-password-123"));
                });
        assertNull(passwordResetTokenRepository.findByTokenHash(
                storedToken.getTokenHash()).orElseThrow().getConsumedAt());
        mockMvc.perform(get("/api/v1/auth/password/reset").param("token", token))
                .andExpect(status().isOk())
                .andExpect(result -> assertTrue(result.getResponse().getContentAsString()
                        .contains("Set password")));
        mockMvc.perform(post("/api/v1/auth/password/reset")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("token", token)
                        .param("newPassword", "target-new-password-456"))
                .andExpect(status().isOk());

        assertFalse(userRepository.findById(target.getId()).orElseThrow()
                .isPasswordResetRequired());
        assertFalse(userRepository.findById(target.getId()).orElseThrow()
                .isPasswordResetSupportRequired());
        assertNotNull(passwordResetTokenRepository.findByTokenHash(
                storedToken.getTokenHash()).orElseThrow().getConsumedAt());
        mockMvc.perform(post("/api/v1/auth/password/reset")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("token", token)
                        .param("newPassword", "target-another-password-789"))
                .andExpect(status().isBadRequest());
        assertEquals(
                HttpStatus.UNAUTHORIZED,
                authService.login(new LoginRequest(
                        target.getEmail(), "target-current-password-123")).status());
        assertEquals(
                HttpStatus.OK,
                authService.login(new LoginRequest(
                        target.getEmail(), "target-new-password-456")).status());
        assertEquals(
                HttpStatus.BAD_REQUEST,
                userService.changePassword(
                        target.getId(),
                        "target-new-password-456",
                        "target-current-password-123").status());
    }

    @Test
    void userInitiatedPasswordResetKeepsSessionsUntilUserCompletesReset() throws Exception {
        UserEntity target = createVerifiedUser("target-current-password-123");
        var targetLogin = authService.login(
                new LoginRequest(target.getEmail(), "target-current-password-123"));

        mockMvc.perform(post("/api/v1/auth/password/reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s"}
                                """.formatted(target.getEmail())))
                .andExpect(status().isAccepted());

        assertFalse(userRepository.findById(target.getId()).orElseThrow()
                .isPasswordResetRequired());
        mockMvc.perform(get("/api/v1/auth/sessions")
                        .header("Authorization", "Bearer " + targetLogin.accessToken()))
                .andExpect(status().isOk());
        assertEquals(
                HttpStatus.OK,
                authService.login(new LoginRequest(
                        target.getEmail(), "target-current-password-123")).status());

        String token = tokenFromBody(
                emailDelivery.last(EmailEventType.PASSWORD_RESET_REQUESTED).body());
        mockMvc.perform(post("/api/v1/auth/password/reset")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("token", token)
                        .param("newPassword", "target-new-password-456"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/auth/sessions")
                        .header("Authorization", "Bearer " + targetLogin.accessToken()))
                .andExpect(status().isUnauthorized());
        assertEquals(
                HttpStatus.UNAUTHORIZED,
                authService.login(new LoginRequest(
                        target.getEmail(), "target-current-password-123")).status());
        assertEquals(
                HttpStatus.OK,
                authService.login(new LoginRequest(
                        target.getEmail(), "target-new-password-456")).status());
    }

    @Test
    void expiredInvalidAndShortPasswordResetAttemptsAreRejected() throws Exception {
        UserEntity target = createVerifiedUser("target-current-password-123");
        mockMvc.perform(post("/api/v1/auth/password/reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s"}
                                """.formatted(target.getEmail())))
                .andExpect(status().isAccepted());

        String token = tokenFromBody(
                emailDelivery.last(EmailEventType.PASSWORD_RESET_REQUESTED).body());
        PasswordResetTokenEntity storedToken = passwordResetTokenRepository.findAll().stream()
                .filter(item -> item.getUserId().equals(target.getId()))
                .findFirst()
                .orElseThrow();
        mockMvc.perform(post("/api/v1/auth/password/reset")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("token", token)
                        .param("newPassword", "short"))
                .andExpect(status().isBadRequest());
        assertNull(passwordResetTokenRepository.findByTokenHash(
                storedToken.getTokenHash()).orElseThrow().getConsumedAt());

        storedToken.setExpiresAt(java.time.Instant.now().minusSeconds(1));
        passwordResetTokenRepository.saveAndFlush(storedToken);
        mockMvc.perform(post("/api/v1/auth/password/reset")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("token", token)
                        .param("newPassword", "target-new-password-456"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/auth/password/reset")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("token", "A".repeat(43))
                        .param("newPassword", "target-new-password-456"))
                .andExpect(status().isBadRequest());
        assertEquals(
                HttpStatus.OK,
                authService.login(new LoginRequest(
                        target.getEmail(), "target-current-password-123")).status());
    }

    private UserEntity createVerifiedUser(String password) {
        UserEntity user = new UserEntity();
        user.setId(UUID.randomUUID());
        user.setEmail("verified-" + user.getId() + "@example.com");
        user.setPassword(passwordHashService.encode(password));
        user.setIsActive(true);
        user.setEmailVerified(true);
        user.setRole(UserRole.USER);
        userRepository.saveAndFlush(user);
        createdUserIds.add(user.getId());
        return user;
    }

    private static String tokenFromBody(String body) {
        String marker = "token=";
        int tokenStart = body.indexOf(marker);
        if (tokenStart < 0) {
            throw new AssertionError("Email did not contain a token link");
        }
        int valueStart = tokenStart + marker.length();
        int valueEnd = body.indexOf('\n', valueStart);
        return body.substring(valueStart, valueEnd < 0 ? body.length() : valueEnd);
    }

    private static void assertStoredHashMatchesToken(
            EmailVerificationTokenEntity storedToken,
            String rawToken) throws Exception {
        String hash = java.util.HexFormat.of().formatHex(
                java.security.MessageDigest.getInstance("SHA-256")
                        .digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        assertTrue(storedToken.getTokenHash().matches("[0-9a-f]{64}"));
        org.junit.jupiter.api.Assertions.assertEquals(hash, storedToken.getTokenHash());
    }

    private void assertStoredHashMatchesToken(
            PasswordResetTokenEntity storedToken,
            String rawToken) throws Exception {
        String hash = java.util.HexFormat.of().formatHex(
                java.security.MessageDigest.getInstance("SHA-256")
                        .digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        assertTrue(storedToken.getTokenHash().matches("[0-9a-f]{64}"));
        org.junit.jupiter.api.Assertions.assertEquals(hash, storedToken.getTokenHash());
    }

    @TestConfiguration
    static class TestMailConfiguration {
        @Bean
        @Primary
        RecordingEmailDelivery recordingEmailDelivery() {
            return new RecordingEmailDelivery();
        }
    }

    static class RecordingEmailDelivery implements EmailDelivery {
        private final List<EmailMessageEvent> sentMessages = new CopyOnWriteArrayList<>();

        @Override
        public void send(String recipient, String subject, String body) {
            sentMessages.add(new EmailMessageEvent(
                    classify(subject), recipient, subject, body));
        }

        EmailMessageEvent last(EmailEventType type) {
            return sentMessages.stream()
                    .filter(message -> message.type() == type)
                    .reduce((first, second) -> second)
                    .orElseThrow(() -> new AssertionError("Missing email event " + type));
        }

        boolean has(EmailEventType type) {
            return sentMessages.stream().anyMatch(message -> message.type() == type);
        }

        void clear() {
            sentMessages.clear();
        }

        private static EmailEventType classify(String subject) {
            if (subject.contains("Verify your JAVARA")) {
                return EmailEventType.REGISTRATION_VERIFICATION;
            }
            if (subject.contains("Confirm your JAVARA")) {
                return EmailEventType.EMAIL_CHANGE_VERIFICATION;
            }
            if (subject.contains("security alert: password reset")) {
                return EmailEventType.EMERGENCY_PASSWORD_RESET;
            }
            if (subject.contains("account recovery authorized")) {
                return EmailEventType.PASSWORD_RESET_SUPPORT_AUTHORIZED;
            }
            if (subject.contains("Reset your JAVARA password")) {
                return EmailEventType.PASSWORD_RESET_REQUESTED;
            }
            if (subject.contains("password changed")) {
                return EmailEventType.PASSWORD_CHANGED;
            }
            if (subject.contains("email address changed")) {
                return EmailEventType.EMAIL_CHANGED;
            }
            return EmailEventType.NEW_SESSION;
        }
    }
}
