package com.elsyah.tech.javara.service.email;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import com.elsyah.tech.javara.middleware.email.EmailSettings;

@Service
public class EmailNotificationService {
    private static final Logger LOGGER =
            LoggerFactory.getLogger(EmailNotificationService.class);

    private final EmailSettings settings;
    private final ApplicationEventPublisher eventPublisher;

    public EmailNotificationService(
            EmailSettings settings,
            ApplicationEventPublisher eventPublisher) {
        this.settings = settings;
        this.eventPublisher = eventPublisher;
    }

    public boolean isEnabled() {
        return settings.enabled();
    }

    public void registrationVerification(String recipient, String rawToken) {
        verification(
                EmailEventType.REGISTRATION_VERIFICATION,
                recipient,
                rawToken,
                "Verify your JAVARA email address",
                "Verify your email address to activate your JAVARA account:");
    }

    public void emailChangeVerification(String recipient, String rawToken) {
        verification(
                EmailEventType.EMAIL_CHANGE_VERIFICATION,
                recipient,
                rawToken,
                "Confirm your JAVARA email change",
                "Confirm this email address to complete your JAVARA email change:");
    }

    public void passwordChanged(String recipient) {
        publish(
                EmailEventType.PASSWORD_CHANGED,
                recipient,
                "JAVARA password changed",
                "Your JAVARA password was changed. If you did not make this change, "
                        + "contact your administrator immediately.");
    }

    public void emailChanged(String recipient) {
        publish(
                EmailEventType.EMAIL_CHANGED,
                recipient,
                "JAVARA email address changed",
                "Your JAVARA account email address was changed. If you did not make this "
                        + "change, contact your administrator immediately.");
    }

    public void newSession(String recipient) {
        publish(
                EmailEventType.NEW_SESSION,
                recipient,
                "New JAVARA sign-in",
                "A new JAVARA session was created for your account. If you did not sign in, "
                        + "change your password and contact your administrator.");
    }

    private void verification(
            EmailEventType type,
            String recipient,
            String rawToken,
            String subject,
            String introduction) {
        String encodedToken = URLEncoder.encode(rawToken, StandardCharsets.UTF_8);
        String verificationUrl = settings.publicBaseUrl()
                + "/api/v1/auth/email/verify?token="
                + encodedToken;
        publish(type, recipient, subject, introduction + "\n\n" + verificationUrl
                + "\n\nThis single-use link expires in "
                + settings.verificationTokenTtl()
                + ". If you did not request this, ignore this message.");
    }

    private void publish(
            EmailEventType type,
            String recipient,
            String subject,
            String body) {
        if (!settings.enabled()) {
            LOGGER.warn("Email event {} was not queued because email delivery is disabled", type);
            return;
        }
        eventPublisher.publishEvent(new EmailMessageEvent(type, recipient, subject, body));
    }
}
