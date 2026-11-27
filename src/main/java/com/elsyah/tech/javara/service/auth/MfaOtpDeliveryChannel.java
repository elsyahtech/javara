package com.elsyah.tech.javara.service.auth;

import java.time.Duration;

import com.elsyah.tech.javara.entity.auth.MfaMethod;
import com.elsyah.tech.javara.entity.user.UserEntity;

public interface MfaOtpDeliveryChannel {
    MfaMethod method();

    boolean isEnabled();

    void send(UserEntity user, String code, Duration ttl);
}
