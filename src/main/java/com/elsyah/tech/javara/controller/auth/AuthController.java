package com.elsyah.tech.javara.controller.auth;

import java.time.Duration;
import java.util.UUID;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.elsyah.tech.javara.entity.user.UserEntity;
import com.elsyah.tech.javara.middleware.config.ApplicationIdentityProperties;
import com.elsyah.tech.javara.middleware.security.JwtProperties;
import com.elsyah.tech.javara.middleware.security.TokenTransport;
import com.elsyah.tech.javara.request.auth.LoginRequest;
import com.elsyah.tech.javara.request.auth.EmailChangeRequest;
import com.elsyah.tech.javara.request.auth.EmailVerificationResendRequest;
import com.elsyah.tech.javara.request.auth.MfaEmailEnrollmentRequest;
import com.elsyah.tech.javara.request.auth.MfaEmailVerificationRequest;
import com.elsyah.tech.javara.request.auth.MfaLoginOtpVerificationRequest;
import com.elsyah.tech.javara.request.auth.MfaLoginPasskeyVerificationRequest;
import com.elsyah.tech.javara.request.auth.MfaPasskeyEnrollmentOptionsRequest;
import com.elsyah.tech.javara.request.auth.MfaPasskeyRegistrationRequest;
import com.elsyah.tech.javara.request.auth.MfaPreferenceRequest;
import com.elsyah.tech.javara.request.auth.PasswordChangeRequest;
import com.elsyah.tech.javara.request.auth.PasswordResetRequest;
import com.elsyah.tech.javara.request.auth.RefreshTokenRequest;
import com.elsyah.tech.javara.request.auth.RegisterRequest;
import com.elsyah.tech.javara.request.user.UserRequest;
import com.elsyah.tech.javara.response.common.ApiResponse;
import com.elsyah.tech.javara.response.auth.AuthTokenResponse;
import com.elsyah.tech.javara.response.auth.CsrfTokenResponse;
import com.elsyah.tech.javara.response.auth.MfaChallengeResponse;
import com.elsyah.tech.javara.response.auth.MfaStatusResponse;
import com.elsyah.tech.javara.response.auth.PublicRegistrationResponse;
import com.elsyah.tech.javara.response.user.UserResponse;
import com.elsyah.tech.javara.service.auth.AuthService;
import com.elsyah.tech.javara.service.auth.AuthServiceResult;
import com.elsyah.tech.javara.service.auth.MfaService;
import com.elsyah.tech.javara.service.auth.PasswordResetService;
import com.elsyah.tech.javara.service.email.EmailVerificationService;
import com.elsyah.tech.javara.service.common.ServiceResult;
import com.elsyah.tech.javara.service.user.UserService;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private static final String CSRF_ATTRIBUTE = CsrfToken.class.getName();
    private static final String CSRF_HEADER = "X-XSRF-TOKEN";
    private static final String INVALID_PASSWORD_RESET_LINK_MESSAGE =
            "Invalid or expired password reset link";

    private final UserService userService;
    private final AuthService authService;
    private final EmailVerificationService emailVerificationService;
    private final PasswordResetService passwordResetService;
    private final MfaService mfaService;
    private final JwtProperties jwtProperties;
    private final ApplicationIdentityProperties applicationIdentity;

    public AuthController(
            UserService userService,
            AuthService authService,
            EmailVerificationService emailVerificationService,
            PasswordResetService passwordResetService,
            MfaService mfaService,
            JwtProperties jwtProperties,
            ApplicationIdentityProperties applicationIdentity) {
        this.userService = userService;
        this.authService = authService;
        this.emailVerificationService = emailVerificationService;
        this.passwordResetService = passwordResetService;
        this.mfaService = mfaService;
        this.jwtProperties = jwtProperties;
        this.applicationIdentity = applicationIdentity;
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<Void>> register(
            @Valid @RequestBody RegisterRequest request) {
        ServiceResult<UserEntity> result = userService.registerUser(
                new UserRequest(request.email(), request.password(), null));
        if (result.isError() && result.status() != HttpStatus.CONFLICT) {
            return ResponseEntity.status(result.status())
                    .body(ApiResponse.failure(result.status(), result.message()));
        }
        return PublicRegistrationResponse.accepted();
    }

    @GetMapping("/email/verify")
    public ResponseEntity<String> emailVerificationConfirmationPage(
            @RequestParam("token") String token) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) {
            return ResponseEntity.badRequest()
                    .contentType(MediaType.TEXT_PLAIN)
                    .header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .body("Invalid or expired email verification link.");
        }
        String escapedToken = org.springframework.web.util.HtmlUtils.htmlEscape(token);
        String escapedApplicationName =
                org.springframework.web.util.HtmlUtils.htmlEscape(applicationIdentity.name());
        String page = """
                <!doctype html>
                <html lang="en">
                <head><meta charset="utf-8"><meta name="referrer" content="no-referrer">
                <title>Confirm %s email</title></head>
                <body><main><h1>Confirm your email address</h1>
                <p>Select the button to complete verification.</p>
                <form method="post" action="/api/v1/auth/email/verify">
                <input type="hidden" name="token" value="%s">
                <button type="submit">Confirm email</button>
                </form></main></body></html>
                """.formatted(escapedApplicationName, escapedToken);
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header("Referrer-Policy", "no-referrer")
                .header(
                        "Content-Security-Policy",
                        "default-src 'none'; form-action 'self'; style-src 'unsafe-inline'")
                .body(page);
    }

    @PostMapping("/email/verify")
    public ResponseEntity<ApiResponse<Void>> verifyEmail(
            @RequestParam("token") String token) {
        ServiceResult<Void> result = emailVerificationService.verify(token);
        if (result.isError()) {
            return ResponseEntity.status(result.status())
                    .body(ApiResponse.failure(result.status(), result.message()));
        }
        return ResponseEntity.status(result.status())
                .body(ApiResponse.success(result.status(), result.message(), null));
    }

    @PostMapping("/email/verification/resend")
    public ResponseEntity<ApiResponse<Void>> resendEmailVerification(
            @Valid @RequestBody EmailVerificationResendRequest request) {
        emailVerificationService.resendRegistrationToken(request.email());
        HttpStatus status = HttpStatus.ACCEPTED;
        return ResponseEntity.status(status).body(ApiResponse.success(
                status,
                "If the account needs verification, instructions will be sent",
                null));
    }

    @PostMapping("/password/reset/request")
    public ResponseEntity<ApiResponse<Void>> requestPasswordReset(
            @Valid @RequestBody PasswordResetRequest request) {
        ServiceResult<Void> result = passwordResetService.requestReset(request.email());
        if (result.isError()) {
            return ResponseEntity.status(result.status())
                    .body(ApiResponse.failure(result.status(), result.message()));
        }
        return ResponseEntity.status(result.status())
                .body(ApiResponse.success(result.status(), result.message(), null));
    }

    @GetMapping("/password/reset")
    public ResponseEntity<String> passwordResetPage(@RequestParam("token") String token) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) {
            return passwordResetPage(
                    HttpStatus.BAD_REQUEST, INVALID_PASSWORD_RESET_LINK_MESSAGE);
        }
        String escapedToken = org.springframework.web.util.HtmlUtils.htmlEscape(token);
        String escapedApplicationName =
                org.springframework.web.util.HtmlUtils.htmlEscape(applicationIdentity.name());
        String page = """
                <!doctype html>
                <html lang="en">
                <head><meta charset="utf-8"><meta name="referrer" content="no-referrer">
                <title>Reset %s password</title></head>
                <body><main><h1>Reset your password</h1>
                <form method="post" action="/api/v1/auth/password/reset">
                <input type="hidden" name="token" value="%s">
                <label>New password
                <input type="password" name="newPassword" minlength="8" maxlength="72"
                       autocomplete="new-password" required></label>
                <button type="submit">Set password</button>
                </form></main></body></html>
                """.formatted(escapedApplicationName, escapedToken);
        return passwordResetHtmlResponse(HttpStatus.OK, page);
    }

    @PostMapping(
            value = "/password/reset",
            consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<String> completePasswordReset(
            @RequestParam("token") String token,
            @RequestParam("newPassword") String newPassword) {
        var result = passwordResetService.completeReset(token, newPassword);
        if (result.status().is4xxClientError()
                && !INVALID_PASSWORD_RESET_LINK_MESSAGE.equals(result.message())) {
            return passwordResetFormPage(result.status(), result.message(), token);
        }
        return passwordResetPage(result.status(), result.message());
    }

    private ResponseEntity<String> passwordResetFormPage(
            HttpStatus status, String message, String token) {
        String escapedMessage = org.springframework.web.util.HtmlUtils.htmlEscape(message);
        String escapedToken = org.springframework.web.util.HtmlUtils.htmlEscape(token);
        String escapedApplicationName =
                org.springframework.web.util.HtmlUtils.htmlEscape(applicationIdentity.name());
        String page = """
                <!doctype html>
                <html lang="en">
                <head><meta charset="utf-8"><meta name="referrer" content="no-referrer">
                <title>Reset %s password</title></head>
                <body><main><h1>Reset your password</h1>
                <p role="alert">%s</p>
                <form method="post" action="/api/v1/auth/password/reset">
                <input type="hidden" name="token" value="%s">
                <label>New password
                <input type="password" name="newPassword" minlength="8" maxlength="72"
                       autocomplete="new-password" required></label>
                <button type="submit">Set password</button>
                </form></main></body></html>
                """.formatted(
                        escapedApplicationName,
                        escapedMessage,
                        escapedToken);
        return passwordResetHtmlResponse(status, page);
    }

    private ResponseEntity<String> passwordResetPage(
            HttpStatus status, String message) {
        String escapedMessage = org.springframework.web.util.HtmlUtils.htmlEscape(message);
        String escapedApplicationName =
                org.springframework.web.util.HtmlUtils.htmlEscape(applicationIdentity.name());
        String page = """
                <!doctype html>
                <html lang="en"><head><meta charset="utf-8">
                <meta name="referrer" content="no-referrer">
                <title>%s password reset</title></head>
                <body><main><h1>%s</h1></main></body></html>
                """.formatted(escapedApplicationName, escapedMessage);
        return passwordResetHtmlResponse(status, page);
    }

    private static ResponseEntity<String> passwordResetHtmlResponse(
            HttpStatus status, String page) {
        return ResponseEntity.status(status)
                .contentType(MediaType.TEXT_HTML)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header("Referrer-Policy", "no-referrer")
                .header(
                        "Content-Security-Policy",
                        "default-src 'none'; form-action 'self'; style-src 'unsafe-inline'")
                .body(page);
    }

    @PostMapping("/email/change")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<ApiResponse<Void>> changeEmail(
            @AuthenticationPrincipal Jwt principal,
            @Valid @RequestBody EmailChangeRequest request) {
        UUID userId = UUID.fromString(principal.getSubject());
        ServiceResult<Void> result = userService.requestEmailChange(
                userId,
                userId,
                request.currentPassword(),
                request.newEmail(),
                false);
        if (result.isError()) {
            return ResponseEntity.status(result.status())
                    .body(ApiResponse.failure(result.status(), result.message()));
        }
        return ResponseEntity.status(result.status())
                .body(ApiResponse.success(result.status(), result.message(), null));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthTokenResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest servletRequest) {
        AuthServiceResult result = authService.login(request, servletRequest.getRemoteAddr());
        return authenticationResponse(result, "Login successful");
    }

    public ResponseEntity<ApiResponse<AuthTokenResponse>> login(LoginRequest request) {
        AuthServiceResult result = authService.login(request);
        return authenticationResponse(result, "Login successful");
    }

    @PostMapping("/mfa/login/otp/verify")
    public ResponseEntity<ApiResponse<AuthTokenResponse>> verifyMfaOtpLogin(
            @Valid @RequestBody MfaLoginOtpVerificationRequest request,
            HttpServletRequest servletRequest) {
        return authenticationResponse(
                authService.verifyMfaOtpLogin(
                        request.challengeId(), request.code(), servletRequest.getRemoteAddr()),
                "Login successful");
    }

    public ResponseEntity<ApiResponse<AuthTokenResponse>> verifyMfaOtpLogin(
            MfaLoginOtpVerificationRequest request) {
        return authenticationResponse(
                authService.verifyMfaOtpLogin(request.challengeId(), request.code()),
                "Login successful");
    }

    @PostMapping("/mfa/login/passkey/verify")
    public ResponseEntity<ApiResponse<AuthTokenResponse>> verifyMfaPasskeyLogin(
            @Valid @RequestBody MfaLoginPasskeyVerificationRequest request,
            HttpServletRequest servletRequest) {
        return authenticationResponse(
                authService.verifyMfaPasskeyLogin(
                        request.challengeId(), request.credential(),
                        servletRequest.getRemoteAddr()),
                "Login successful");
    }

    public ResponseEntity<ApiResponse<AuthTokenResponse>> verifyMfaPasskeyLogin(
            MfaLoginPasskeyVerificationRequest request) {
        return authenticationResponse(
                authService.verifyMfaPasskeyLogin(
                        request.challengeId(), request.credential()),
                "Login successful");
    }

    @PostMapping("/mfa/login/passkey/enroll")
    public ResponseEntity<ApiResponse<AuthTokenResponse>> completeRequiredPasskeyEnrollment(
            @Valid @RequestBody MfaPasskeyRegistrationRequest request,
            HttpServletRequest servletRequest) {
        return authenticationResponse(
                authService.completeRequiredPasskeyEnrollment(
                        request.challengeId(), request.label(), request.credential(),
                        servletRequest.getRemoteAddr()),
                "Login successful");
    }

    public ResponseEntity<ApiResponse<AuthTokenResponse>> completeRequiredPasskeyEnrollment(
            MfaPasskeyRegistrationRequest request) {
        return authenticationResponse(
                authService.completeRequiredPasskeyEnrollment(
                        request.challengeId(), request.label(), request.credential()),
                "Login successful");
    }

    @GetMapping("/mfa")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<ApiResponse<MfaStatusResponse>> mfaStatus(
            @AuthenticationPrincipal Jwt principal) {
        ServiceResult<MfaStatusResponse> result =
                mfaService.status(UUID.fromString(principal.getSubject()));
        return mfaStatusResponse(result);
    }

    @PostMapping("/mfa/email/enroll")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<ApiResponse<MfaChallengeResponse>> beginEmailMfaEnrollment(
            @AuthenticationPrincipal Jwt principal,
            @Valid @RequestBody MfaEmailEnrollmentRequest request) {
        ServiceResult<MfaChallengeResponse> result = mfaService.beginEmailEnrollment(
                UUID.fromString(principal.getSubject()), request.currentPassword());
        if (result.isError()) {
            return ResponseEntity.status(result.status())
                    .body(ApiResponse.failure(result.status(), result.message()));
        }
        return ResponseEntity.status(result.status())
                .body(ApiResponse.success(result.status(), result.message(), result.data()));
    }

    @PostMapping("/mfa/email/enroll/verify")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<ApiResponse<MfaStatusResponse>> verifyEmailMfaEnrollment(
            @AuthenticationPrincipal Jwt principal,
            @Valid @RequestBody MfaEmailVerificationRequest request) {
        ServiceResult<MfaStatusResponse> result = mfaService.verifyEmailEnrollment(
                UUID.fromString(principal.getSubject()),
                request.challengeId(),
                request.code());
        return mfaStatusResponse(result);
    }

    @PostMapping("/mfa/passkeys/enroll/options")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<ApiResponse<MfaChallengeResponse>> beginPasskeyEnrollment(
            @AuthenticationPrincipal Jwt principal,
            @Valid @RequestBody MfaPasskeyEnrollmentOptionsRequest request) {
        ServiceResult<MfaChallengeResponse> result = mfaService.beginPasskeyEnrollment(
                UUID.fromString(principal.getSubject()), request.currentPassword());
        if (result.isError()) {
            return ResponseEntity.status(result.status())
                    .body(ApiResponse.failure(result.status(), result.message()));
        }
        return ResponseEntity.status(result.status())
                .body(ApiResponse.success(result.status(), result.message(), result.data()));
    }

    @PostMapping("/mfa/passkeys/enroll")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<ApiResponse<MfaStatusResponse>> completePasskeyEnrollment(
            @AuthenticationPrincipal Jwt principal,
            @Valid @RequestBody MfaPasskeyRegistrationRequest request) {
        ServiceResult<MfaStatusResponse> result = mfaService.completePasskeyEnrollment(
                UUID.fromString(principal.getSubject()),
                request.challengeId(),
                request.label(),
                request.credential());
        return mfaStatusResponse(result);
    }

    @PostMapping("/mfa/preference")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<ApiResponse<MfaStatusResponse>> setMfaPreference(
            @AuthenticationPrincipal Jwt principal,
            @Valid @RequestBody MfaPreferenceRequest request) {
        ServiceResult<MfaStatusResponse> result = mfaService.setPreferredMethod(
                UUID.fromString(principal.getSubject()),
                request.method(),
                request.currentPassword());
        return mfaStatusResponse(result);
    }

    @PostMapping("/mfa/disable")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<ApiResponse<MfaStatusResponse>> disableMfaMethod(
            @AuthenticationPrincipal Jwt principal,
            @Valid @RequestBody MfaPreferenceRequest request) {
        ServiceResult<MfaStatusResponse> result = mfaService.disableMethod(
                UUID.fromString(principal.getSubject()),
                request.method(),
                request.currentPassword());
        return mfaStatusResponse(result);
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthTokenResponse>> refresh(
            @RequestBody(required = false) RefreshTokenRequest request,
            HttpServletRequest servletRequest) {
        String refreshToken = refreshToken(request, servletRequest);
        AuthServiceResult result = authService.refresh(refreshToken);
        if (result.isError()) {
            return ResponseEntity.status(result.status())
                    .body(ApiResponse.failure(result.status(), result.message()));
        }

        return tokenResponse(result, result.message());
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @RequestBody(required = false) RefreshTokenRequest request,
            HttpServletRequest servletRequest) {
        String refreshToken = refreshToken(request, servletRequest);
        boolean loggedOut = authService.logout(refreshToken);
        HttpStatus status = loggedOut ? HttpStatus.OK : HttpStatus.UNAUTHORIZED;

        ResponseEntity.BodyBuilder response = ResponseEntity.status(status);
        if (jwtProperties.transport() == TokenTransport.COOKIE) {
            response.header(HttpHeaders.SET_COOKIE, clearCookie(
                    jwtProperties.accessCookieName(), "/", Duration.ZERO).toString());
            response.header(HttpHeaders.SET_COOKIE, clearCookie(
                    jwtProperties.refreshCookieName(), "/api/v1/auth", Duration.ZERO).toString());
        }
        if (!loggedOut) {
            return response.body(ApiResponse.failure(status, "Refresh token is invalid"));
        }
        return response.body(ApiResponse.success(HttpStatus.OK, "Logout successful", null));
    }

    @PostMapping("/password/change")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @AuthenticationPrincipal Jwt principal,
            @Valid @RequestBody PasswordChangeRequest request) {
        ServiceResult<Void> result = userService.changePassword(
                UUID.fromString(principal.getSubject()),
                request.currentPassword(),
                request.newPassword());
        ResponseEntity.BodyBuilder response = ResponseEntity.status(result.status());
        if (result.isError()) {
            return response.body(ApiResponse.failure(result.status(), result.message()));
        }
        if (jwtProperties.transport() == TokenTransport.COOKIE) {
            response.header(HttpHeaders.SET_COOKIE, clearCookie(
                    jwtProperties.accessCookieName(), "/", Duration.ZERO).toString());
            response.header(HttpHeaders.SET_COOKIE, clearCookie(
                    jwtProperties.refreshCookieName(), "/api/v1/auth", Duration.ZERO).toString());
        }
        return response.body(ApiResponse.success(result.status(), result.message(), null));
    }

    @GetMapping("/csrf")
    public ResponseEntity<ApiResponse<CsrfTokenResponse>> csrf(HttpServletRequest request) {
        if (jwtProperties.transport() != TokenTransport.COOKIE) {
            return ResponseEntity.notFound().build();
        }

        CsrfToken token = (CsrfToken) request.getAttribute(CSRF_ATTRIBUTE);
        if (token == null) {
            throw new IllegalStateException("CSRF token was not initialized for cookie transport");
        }
        CsrfTokenResponse data = new CsrfTokenResponse(token.getToken(), CSRF_HEADER);
        return ResponseEntity.ok(ApiResponse.success(
                HttpStatus.OK,
                "CSRF token retrieved successfully",
                data));
    }

    private ResponseEntity<ApiResponse<AuthTokenResponse>> tokenResponse(
            AuthServiceResult result,
            String message) {
        boolean cookieTransport = jwtProperties.transport() == TokenTransport.COOKIE;
        AuthTokenResponse data = new AuthTokenResponse(
                cookieTransport ? null : result.accessToken(),
                cookieTransport ? null : result.refreshToken(),
                cookieTransport ? null : "Bearer",
                jwtProperties.accessTokenTtl().toSeconds(),
                UserResponse.from(result.user()));

        ResponseEntity.BodyBuilder response = ResponseEntity.status(result.status());
        if (cookieTransport) {
            response.header(HttpHeaders.SET_COOKIE, tokenCookie(
                    jwtProperties.accessCookieName(),
                    result.accessToken(),
                    "/",
                    jwtProperties.accessTokenTtl()).toString());
            response.header(HttpHeaders.SET_COOKIE, tokenCookie(
                    jwtProperties.refreshCookieName(),
                    result.refreshToken(),
                    "/api/v1/auth",
                    jwtProperties.refreshTokenTtl()).toString());
        }

        return response.body(ApiResponse.success(result.status(), message, data));
    }

    private ResponseEntity<ApiResponse<AuthTokenResponse>> authenticationResponse(
            AuthServiceResult result, String successMessage) {
        if (result.isError()) {
            return ResponseEntity.status(result.status())
                    .body(ApiResponse.failure(result.status(), result.message()));
        }
        if (result.mfaChallenge() != null) {
            AuthTokenResponse challengeResponse = new AuthTokenResponse(
                    null, null, null, null, null, result.mfaChallenge());
            return ResponseEntity.status(result.status())
                    .body(ApiResponse.success(
                            result.status(), result.message(), challengeResponse));
        }
        return tokenResponse(result, successMessage);
    }

    private static ResponseEntity<ApiResponse<MfaStatusResponse>> mfaStatusResponse(
            ServiceResult<MfaStatusResponse> result) {
        if (result.isError()) {
            return ResponseEntity.status(result.status())
                    .body(ApiResponse.failure(result.status(), result.message()));
        }
        return ResponseEntity.status(result.status())
                .body(ApiResponse.success(result.status(), result.message(), result.data()));
    }

    private String refreshToken(
            RefreshTokenRequest body,
            HttpServletRequest request) {
        if (jwtProperties.transport() == TokenTransport.BEARER) {
            return body == null ? null : body.refreshToken();
        }
        return cookieValue(request, jwtProperties.refreshCookieName());
    }

    private ResponseCookie tokenCookie(
            String name,
            String value,
            String path,
            Duration maxAge) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(jwtProperties.cookieSecure())
                .sameSite(jwtProperties.cookieSameSite())
                .path(path)
                .maxAge(maxAge)
                .build();
    }

    private ResponseCookie clearCookie(String name, String path, Duration maxAge) {
        return tokenCookie(name, "", path, maxAge);
    }

    private static String cookieValue(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }

        String value = null;
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) {
                if (value != null) {
                    return null;
                }
                value = cookie.getValue();
            }
        }
        return value;
    }
}
