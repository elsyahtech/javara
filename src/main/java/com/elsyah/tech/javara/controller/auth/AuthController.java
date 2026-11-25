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
import com.elsyah.tech.javara.middleware.security.JwtProperties;
import com.elsyah.tech.javara.middleware.security.TokenTransport;
import com.elsyah.tech.javara.request.auth.LoginRequest;
import com.elsyah.tech.javara.request.auth.EmailChangeRequest;
import com.elsyah.tech.javara.request.auth.EmailVerificationResendRequest;
import com.elsyah.tech.javara.request.auth.PasswordChangeRequest;
import com.elsyah.tech.javara.request.auth.RefreshTokenRequest;
import com.elsyah.tech.javara.request.auth.RegisterRequest;
import com.elsyah.tech.javara.request.user.UserRequest;
import com.elsyah.tech.javara.response.common.ApiResponse;
import com.elsyah.tech.javara.response.auth.AuthTokenResponse;
import com.elsyah.tech.javara.response.auth.CsrfTokenResponse;
import com.elsyah.tech.javara.response.user.UserResponse;
import com.elsyah.tech.javara.service.auth.AuthService;
import com.elsyah.tech.javara.service.auth.AuthServiceResult;
import com.elsyah.tech.javara.service.email.EmailVerificationService;
import com.elsyah.tech.javara.service.common.ServiceResult;
import com.elsyah.tech.javara.service.user.UserService;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private static final String CSRF_ATTRIBUTE = CsrfToken.class.getName();
    private static final String CSRF_HEADER = "X-XSRF-TOKEN";

    private final UserService userService;
    private final AuthService authService;
    private final EmailVerificationService emailVerificationService;
    private final JwtProperties jwtProperties;

    public AuthController(
            UserService userService,
            AuthService authService,
            EmailVerificationService emailVerificationService,
            JwtProperties jwtProperties) {
        this.userService = userService;
        this.authService = authService;
        this.emailVerificationService = emailVerificationService;
        this.jwtProperties = jwtProperties;
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<UserResponse>> register(
            @Valid @RequestBody RegisterRequest request) {
        ServiceResult<UserEntity> result = userService.registerUser(
                new UserRequest(request.email(), request.password(), null));
        if (result.isError()) {
            return ResponseEntity.status(result.status())
                    .body(ApiResponse.failure(result.status(), result.message()));
        }

        UserResponse user = UserResponse.from(result.data());
        return ResponseEntity.status(result.status())
                .body(ApiResponse.success(result.status(), result.message(), user));
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
        String page = """
                <!doctype html>
                <html lang="en">
                <head><meta charset="utf-8"><meta name="referrer" content="no-referrer">
                <title>Confirm JAVARA email</title></head>
                <body><main><h1>Confirm your email address</h1>
                <p>Select the button to complete verification.</p>
                <form method="post" action="/api/v1/auth/email/verify">
                <input type="hidden" name="token" value="%s">
                <button type="submit">Confirm email</button>
                </form></main></body></html>
                """.formatted(escapedToken);
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
            @Valid @RequestBody LoginRequest request) {
        AuthServiceResult result = authService.login(request);
        if (result.isError()) {
            return ResponseEntity.status(result.status())
                    .body(ApiResponse.failure(result.status(), result.message()));
        }

        return tokenResponse(result, "Login successful");
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
