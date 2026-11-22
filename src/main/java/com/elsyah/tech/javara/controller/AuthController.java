package com.elsyah.tech.javara.controller;

import java.time.Duration;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.elsyah.tech.javara.entity.UserEntity;
import com.elsyah.tech.javara.middleware.security.JwtProperties;
import com.elsyah.tech.javara.middleware.security.TokenTransport;
import com.elsyah.tech.javara.request.LoginRequest;
import com.elsyah.tech.javara.request.RefreshTokenRequest;
import com.elsyah.tech.javara.request.RegisterRequest;
import com.elsyah.tech.javara.request.UserRequest;
import com.elsyah.tech.javara.response.ApiResponse;
import com.elsyah.tech.javara.response.AuthTokenResponse;
import com.elsyah.tech.javara.response.CsrfTokenResponse;
import com.elsyah.tech.javara.response.UserResponse;
import com.elsyah.tech.javara.service.AuthService;
import com.elsyah.tech.javara.service.AuthServiceResult;
import com.elsyah.tech.javara.service.ServiceResult;
import com.elsyah.tech.javara.service.UserService;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private static final String CSRF_ATTRIBUTE = CsrfToken.class.getName();
    private static final String CSRF_HEADER = "X-XSRF-TOKEN";

    private final UserService userService;
    private final AuthService authService;
    private final JwtProperties jwtProperties;

    public AuthController(
            UserService userService,
            AuthService authService,
            JwtProperties jwtProperties) {
        this.userService = userService;
        this.authService = authService;
        this.jwtProperties = jwtProperties;
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<UserResponse>> register(
            @Valid @RequestBody RegisterRequest request) {
        ServiceResult<UserEntity> result = userService.createUser(
                new UserRequest(request.email(), request.password(), null));
        if (result.isError()) {
            return ResponseEntity.status(result.status())
                    .body(ApiResponse.failure(result.status(), result.message()));
        }

        UserResponse user = UserResponse.from(result.data());
        return ResponseEntity.status(result.status())
                .body(ApiResponse.success(result.status(), result.message(), user));
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
