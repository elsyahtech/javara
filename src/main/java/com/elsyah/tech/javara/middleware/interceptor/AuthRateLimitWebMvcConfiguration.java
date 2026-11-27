package com.elsyah.tech.javara.middleware.interceptor;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.elsyah.tech.javara.middleware.ratelimit.LoginRateLimitProperties;
import com.elsyah.tech.javara.middleware.ratelimit.RefreshRateLimitProperties;
import com.elsyah.tech.javara.middleware.ratelimit.RegisterRateLimitProperties;
import com.elsyah.tech.javara.middleware.ratelimit.SessionReauthRateLimitProperties;

@Configuration
@ConditionalOnProperty(
        prefix = "app.features",
        name = "rate-limiting",
        havingValue = "true",
        matchIfMissing = true)
@ConditionalOnProperty(
        prefix = "app.features",
        name = "auth-api",
        havingValue = "true",
        matchIfMissing = true)
@EnableConfigurationProperties({
        LoginRateLimitProperties.class,
        RegisterRateLimitProperties.class,
        RefreshRateLimitProperties.class,
        SessionReauthRateLimitProperties.class
})
public class AuthRateLimitWebMvcConfiguration implements WebMvcConfigurer {
    private static final String LOGIN_PATH = "/api/v1/auth/login";
    private static final String MFA_LOGIN_OTP_PATH =
            "/api/v1/auth/mfa/login/otp/verify";
    private static final String MFA_LOGIN_PASSKEY_PATH =
            "/api/v1/auth/mfa/login/passkey/verify";
    private static final String MFA_LOGIN_PASSKEY_ENROLL_PATH =
            "/api/v1/auth/mfa/login/passkey/enroll";
    private static final String REGISTER_PATH = "/api/v1/auth/register";
    private static final String EMAIL_VERIFICATION_RESEND_PATH =
            "/api/v1/auth/email/verification/resend";
    private static final String EMAIL_VERIFICATION_PATH = "/api/v1/auth/email/verify";
    private static final String PASSWORD_RESET_PATH = "/api/v1/auth/password/reset";
    private static final String PASSWORD_RESET_REQUEST_PATH =
            "/api/v1/auth/password/reset/request";
    private static final String REFRESH_PATH = "/api/v1/auth/refresh";
    private static final String PASSWORD_CHANGE_PATH = "/api/v1/auth/password/change";
    private static final String EMAIL_CHANGE_PATH = "/api/v1/auth/email/change";
    private static final String MFA_EMAIL_ENROLL_PATH = "/api/v1/auth/mfa/email/enroll";
    private static final String MFA_EMAIL_VERIFY_PATH = "/api/v1/auth/mfa/email/enroll/verify";
    private static final String MFA_PASSKEY_OPTIONS_PATH =
            "/api/v1/auth/mfa/passkeys/enroll/options";
    private static final String MFA_PASSKEY_ENROLL_PATH =
            "/api/v1/auth/mfa/passkeys/enroll";
    private static final String MFA_PREFERENCE_PATH = "/api/v1/auth/mfa/preference";
    private static final String MFA_DISABLE_PATH = "/api/v1/auth/mfa/disable";
    private static final String SESSION_TERMINATE_PATH =
            "/api/v1/auth/sessions/*/terminate";
    private static final String SESSION_TERMINATE_ALL_PATH =
            "/api/v1/auth/sessions/terminate-all";
    private static final String ADMIN_SESSION_TERMINATE_PATH =
            "/api/v1/admin/users/*/sessions/terminate";
    private static final String ADMIN_EMAIL_CHANGE_PATH =
            "/api/v1/admin/users/*/email/change";
    private static final String ADMIN_EMERGENCY_PASSWORD_RESET_PATH =
            "/api/v1/admin/users/*/security/force-password-reset";
    private static final String ADMIN_RECOVERY_AUTHORIZATION_PATH =
            "/api/v1/admin/users/*/password/recovery/authorize";

    private final AuthRateLimitInterceptor rateLimitInterceptor;
    private final LoginRateLimitProperties loginProperties;
    private final RegisterRateLimitProperties registerProperties;
    private final RefreshRateLimitProperties refreshProperties;
    private final SessionReauthRateLimitProperties sessionReauthProperties;

    public AuthRateLimitWebMvcConfiguration(
            AuthRateLimitInterceptor rateLimitInterceptor,
            LoginRateLimitProperties loginProperties,
            RegisterRateLimitProperties registerProperties,
            RefreshRateLimitProperties refreshProperties,
            SessionReauthRateLimitProperties sessionReauthProperties) {
        this.rateLimitInterceptor = rateLimitInterceptor;
        this.loginProperties = loginProperties;
        this.registerProperties = registerProperties;
        this.refreshProperties = refreshProperties;
        this.sessionReauthProperties = sessionReauthProperties;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        List<String> paths = new ArrayList<>(10);
        if (loginProperties.enabled()) {
            paths.add(LOGIN_PATH);
            paths.add(MFA_LOGIN_OTP_PATH);
            paths.add(MFA_LOGIN_PASSKEY_PATH);
            paths.add(MFA_LOGIN_PASSKEY_ENROLL_PATH);
        }
        if (registerProperties.enabled()) {
            paths.add(REGISTER_PATH);
            paths.add(EMAIL_VERIFICATION_RESEND_PATH);
            paths.add(EMAIL_VERIFICATION_PATH);
        }
        if (refreshProperties.enabled()) {
            paths.add(REFRESH_PATH);
        }
        if (sessionReauthProperties.enabled()) {
            paths.add(PASSWORD_CHANGE_PATH);
            paths.add(EMAIL_CHANGE_PATH);
            paths.add(MFA_EMAIL_ENROLL_PATH);
            paths.add(MFA_EMAIL_VERIFY_PATH);
            paths.add(MFA_PASSKEY_OPTIONS_PATH);
            paths.add(MFA_PASSKEY_ENROLL_PATH);
            paths.add(MFA_PREFERENCE_PATH);
            paths.add(MFA_DISABLE_PATH);
            paths.add(SESSION_TERMINATE_PATH);
            paths.add(SESSION_TERMINATE_ALL_PATH);
            paths.add(ADMIN_SESSION_TERMINATE_PATH);
            paths.add(ADMIN_EMAIL_CHANGE_PATH);
            paths.add(ADMIN_EMERGENCY_PASSWORD_RESET_PATH);
            paths.add(ADMIN_RECOVERY_AUTHORIZATION_PATH);
            paths.add(PASSWORD_RESET_PATH);
            paths.add(PASSWORD_RESET_REQUEST_PATH);
        }
        if (!paths.isEmpty()) {
            registry.addInterceptor(rateLimitInterceptor).addPathPatterns(paths);
        }
    }
}
