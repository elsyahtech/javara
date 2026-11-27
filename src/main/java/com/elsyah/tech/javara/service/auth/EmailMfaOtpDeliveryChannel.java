package com.elsyah.tech.javara.service.auth;

import java.time.Duration;

import org.springframework.stereotype.Component;

import com.elsyah.tech.javara.entity.auth.MfaMethod;
import com.elsyah.tech.javara.entity.user.UserEntity;
import com.elsyah.tech.javara.service.email.EmailNotificationService;

@Component
public class EmailMfaOtpDeliveryChannel implements MfaOtpDeliveryChannel {
    private final EmailNotificationService emailNotificationService;

    public EmailMfaOtpDeliveryChannel(EmailNotificationService emailNotificationService) {
        this.emailNotificationService = emailNotificationService;
    }

    @Override
    public MfaMethod method() {
        return MfaMethod.EMAIL_OTP;
    }

    @Override
    public boolean isEnabled() {
        return emailNotificationService.isEnabled();
    }

    @Override
    public void send(UserEntity user, String code, Duration ttl) {
        emailNotificationService.mfaOneTimeCode(user.getEmail(), code, ttl);
    }
}
