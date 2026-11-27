package com.elsyah.tech.javara.service.email;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.elsyah.tech.javara.middleware.email.EmailSettings;

@Component
@ConditionalOnProperty(
        prefix = "app.email",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class SmtpEmailDelivery implements EmailDelivery {
    private final JavaMailSender mailSender;
    private final EmailSettings settings;

    public SmtpEmailDelivery(JavaMailSender mailSender, EmailSettings settings) {
        this.mailSender = mailSender;
        this.settings = settings;
    }

    @Override
    public void send(String recipient, String subject, String body) {
        if (!settings.enabled()) {
            return;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(settings.fromAddress());
        message.setTo(recipient);
        message.setSubject(subject);
        message.setText(body);
        mailSender.send(message);
    }
}
