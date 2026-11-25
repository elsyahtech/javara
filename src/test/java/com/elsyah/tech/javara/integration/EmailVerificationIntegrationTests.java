package com.elsyah.tech.javara.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
import com.elsyah.tech.javara.entity.user.UserEntity;
import com.elsyah.tech.javara.entity.user.UserRole;
import com.elsyah.tech.javara.repository.auth.EmailVerificationTokenRepository;
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
            throw new AssertionError("Verification email did not contain a token link");
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
