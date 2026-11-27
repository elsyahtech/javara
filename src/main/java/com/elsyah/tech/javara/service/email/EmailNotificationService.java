package com.elsyah.tech.javara.service.email;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import com.elsyah.tech.javara.middleware.config.ApplicationBrandingProperties;
import com.elsyah.tech.javara.middleware.config.ApplicationIdentityProperties;
import com.elsyah.tech.javara.middleware.email.EmailSettings;

@Service
public class EmailNotificationService {
    private static final Logger LOGGER =
            LoggerFactory.getLogger(EmailNotificationService.class);

    private final EmailSettings settings;
    private final ApplicationEventPublisher eventPublisher;
    private final ApplicationIdentityProperties applicationIdentity;
    private final ApplicationBrandingProperties branding;

    public EmailNotificationService(
            EmailSettings settings,
            ApplicationEventPublisher eventPublisher,
            ApplicationIdentityProperties applicationIdentity,
            ApplicationBrandingProperties branding) {
        this.settings = settings;
        this.eventPublisher = eventPublisher;
        this.applicationIdentity = applicationIdentity;
        this.branding = branding;
    }

    public boolean isEnabled() {
        return settings.enabled();
    }

    public void registrationVerification(String recipient, String rawToken) {
        verification(
                EmailEventType.REGISTRATION_VERIFICATION,
                recipient,
                rawToken,
                "Verify your " + applicationIdentity.name() + " email address",
                "Verify your email address to activate your "
                        + applicationIdentity.name() + " account:");
    }

    public void emailChangeVerification(String recipient, String rawToken) {
        verification(
                EmailEventType.EMAIL_CHANGE_VERIFICATION,
                recipient,
                rawToken,
                "Confirm your " + applicationIdentity.name() + " email change",
                "Confirm this email address to complete your "
                        + applicationIdentity.name() + " email change:");
    }

    public void passwordChanged(String recipient) {
        publish(
                EmailEventType.PASSWORD_CHANGED,
                recipient,
                applicationIdentity.name() + " password changed",
                "Your " + applicationIdentity.name()
                        + " password was changed. If you did not make this change, "
                        + "contact your administrator immediately.");
    }

    public void passwordResetLink(
            String recipient, String rawToken, boolean supportAuthorized, Duration tokenTtl) {
        String encodedToken = URLEncoder.encode(rawToken, StandardCharsets.UTF_8);
        String resetUrl = settings.publicBaseUrl()
                + "/api/v1/auth/password/reset?token="
                + encodedToken;
        EmailEventType type = supportAuthorized
                ? EmailEventType.PASSWORD_RESET_SUPPORT_AUTHORIZED
                : EmailEventType.PASSWORD_RESET_REQUESTED;
        String subject = supportAuthorized
                ? applicationIdentity.name() + " account recovery authorized"
                : "Reset your " + applicationIdentity.name() + " password";
        String message = supportAuthorized
                ? "Support has authorized recovery for your "
                        + applicationIdentity.name() + " account."
                : "A password reset was requested for your "
                        + applicationIdentity.name() + " account.";
        publish(
                type,
                recipient,
                subject,
                message + "\n\nChoose a new password using this single-use link:\n"
                        + resetUrl + "\n\nThe link expires in " + tokenTtl
                        + ". If you did not expect this request, contact your administrator.");
    }

    public void emergencyPasswordResetNotice(String recipient, String adminMessage) {
        publish(
                EmailEventType.EMERGENCY_PASSWORD_RESET,
                recipient,
                applicationIdentity.name() + " security alert: password reset",
                applicationIdentity.name()
                        + " has invalidated your password and terminated all active sessions. "
                        + "Sign-in is blocked until support verifies your identity and "
                        + "authorizes account recovery.\n\n"
                        + "Message from your administrator:\n"
                        + adminMessage
                        + "\n\nIf you did not expect this action, contact support using "
                        + "a trusted contact channel.");
    }

    public void emailChanged(String recipient) {
        publish(
                EmailEventType.EMAIL_CHANGED,
                recipient,
                applicationIdentity.name() + " email address changed",
                "Your " + applicationIdentity.name()
                        + " account email address was changed. If you did not make this "
                        + "change, contact your administrator immediately.");
    }

    public void newSession(String recipient) {
        publish(
                EmailEventType.NEW_SESSION,
                recipient,
                "New " + applicationIdentity.name() + " sign-in",
                "A new " + applicationIdentity.name()
                        + " session was created for your account. If you did not sign in, "
                        + "change your password and contact your administrator.");
    }

    public void mfaOneTimeCode(String recipient, String code, Duration tokenTtl) {
        publish(
                EmailEventType.MFA_ONE_TIME_CODE,
                recipient,
                "Your " + applicationIdentity.name() + " verification code",
                "Use this one-time code to complete sign-in or MFA setup: "
                        + code
                        + "\n\nThe code expires in "
                        + tokenTtl
                        + ". No one from " + applicationIdentity.name()
                        + " will ever ask you to share this code. Do not share it with anyone, "
                        + "including anyone claiming to be support. If you did not request this code, "
                        + "you can ignore this message.");
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
        String attribution = branding.poweredBy().isBlank()
                ? ""
                : "\n\nPowered by " + branding.poweredBy();
        eventPublisher.publishEvent(
                new EmailMessageEvent(type, recipient, subject, body + attribution));
    }
}
