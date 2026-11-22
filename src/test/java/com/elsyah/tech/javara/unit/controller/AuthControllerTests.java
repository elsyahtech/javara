package com.elsyah.tech.javara.unit.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;

import java.time.Duration;
import java.util.Base64;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.elsyah.tech.javara.controller.AuthController;
import com.elsyah.tech.javara.entity.UserEntity;
import com.elsyah.tech.javara.middleware.security.JwtProperties;
import com.elsyah.tech.javara.middleware.security.TokenTransport;
import com.elsyah.tech.javara.request.LoginRequest;
import com.elsyah.tech.javara.request.RegisterRequest;
import com.elsyah.tech.javara.request.UserRequest;
import com.elsyah.tech.javara.response.ApiResponse;
import com.elsyah.tech.javara.response.AuthTokenResponse;
import com.elsyah.tech.javara.response.UserResponse;
import com.elsyah.tech.javara.service.AuthService;
import com.elsyah.tech.javara.service.AuthServiceResult;
import com.elsyah.tech.javara.service.ServiceResult;
import com.elsyah.tech.javara.service.UserService;

class AuthControllerTests {
    // Verifies that bearer login returns both tokens and the safe user profile.
    @Test
    void loginReturnsBearerTokensAndUserProfile() {
        UserService userService = mock(UserService.class);
        AuthService authService = mock(AuthService.class);
        JwtProperties properties = jwtProperties(TokenTransport.BEARER);
        LoginRequest request = new LoginRequest("user@example.com", "password123");
        UserEntity user = user("user-1");
        when(authService.login(request)).thenReturn(new AuthServiceResult(
                user, "access-token", "refresh-token", HttpStatus.OK, "Login successful"));

        ResponseEntity<ApiResponse<AuthTokenResponse>> response =
                new AuthController(userService, authService, properties).login(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("access-token", response.getBody().getData().accessToken());
        assertEquals("refresh-token", response.getBody().getData().refreshToken());
        assertEquals("Bearer", response.getBody().getData().tokenType());
        assertEquals("user-1", response.getBody().getData().user().id());
        verify(authService).login(request);
    }

    // Verifies that cookie login sets secure HttpOnly cookies without returning raw tokens.
    @Test
    void cookieLoginSetsTokensOnlyInCookies() {
        UserService userService = mock(UserService.class);
        AuthService authService = mock(AuthService.class);
        JwtProperties properties = jwtProperties(TokenTransport.COOKIE);
        LoginRequest request = new LoginRequest("user@example.com", "password123");
        when(authService.login(request)).thenReturn(new AuthServiceResult(
                user("user-1"),
                "access-token",
                "refresh-token",
                HttpStatus.OK,
                "Login successful"));

        ResponseEntity<ApiResponse<AuthTokenResponse>> response =
                new AuthController(userService, authService, properties).login(request);

        assertNotNull(response.getBody());
        assertNull(response.getBody().getData().accessToken());
        assertNull(response.getBody().getData().refreshToken());
        assertNull(response.getBody().getData().tokenType());
        assertEquals(2, response.getHeaders().get(HttpHeaders.SET_COOKIE).size());
        assertTrue(response.getHeaders().get(HttpHeaders.SET_COOKIE).stream()
                .allMatch(cookie -> cookie.contains("HttpOnly") && cookie.contains("Secure")));
    }

    // Verifies that public registration always passes a request without client-controlled status.
    @Test
    void registrationCreatesAUserWithServerControlledStatus() {
        UserService userService = mock(UserService.class);
        AuthService authService = mock(AuthService.class);
        RegisterRequest request = new RegisterRequest("new@example.com", "password123");
        UserEntity user = user("user-2");
        when(userService.createUser(new UserRequest("new@example.com", "password123", null)))
                .thenReturn(new ServiceResult<>(user, HttpStatus.CREATED, "User created"));

        ResponseEntity<ApiResponse<UserResponse>> response =
                new AuthController(userService, authService, jwtProperties(TokenTransport.BEARER))
                        .register(request);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("USER", response.getBody().getData().role().name());
        verify(userService).createUser(new UserRequest("new@example.com", "password123", null));
    }

    // Verifies that invalid login payloads are rejected before authentication is attempted.
    @Test
    void loginWithBlankCredentialsReturnsBadRequest() throws Exception {
        UserService userService = mock(UserService.class);
        AuthService authService = mock(AuthService.class);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
                new AuthController(userService, authService, jwtProperties(TokenTransport.BEARER)))
                .build();

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(authService);
    }

    // Verifies that cookie mode cannot be used with an insecure SameSite=None cookie.
    @Test
    void cookiePropertiesRejectSameSiteNoneWithoutSecureCookies() {
        boolean rejected = false;
        try {
            new JwtProperties(
                    Base64.getEncoder().encodeToString(new byte[32]),
                    TokenTransport.COOKIE,
                    Duration.ofMinutes(15),
                    Duration.ofDays(30),
                    false,
                    "None",
                    "ACCESS_TOKEN",
                    "REFRESH_TOKEN");
        } catch (IllegalArgumentException exception) {
            rejected = true;
        }

        assertTrue(rejected);
    }

    // Verifies that bearer transport does not emit cookie headers.
    @Test
    void bearerLoginDoesNotSetCookies() {
        UserService userService = mock(UserService.class);
        AuthService authService = mock(AuthService.class);
        LoginRequest request = new LoginRequest("user@example.com", "password123");
        when(authService.login(request)).thenReturn(new AuthServiceResult(
                user("user-1"),
                "access-token",
                "refresh-token",
                HttpStatus.OK,
                "Login successful"));

        ResponseEntity<ApiResponse<AuthTokenResponse>> response =
                new AuthController(userService, authService, jwtProperties(TokenTransport.BEARER))
                        .login(request);

        assertNull(response.getHeaders().get(HttpHeaders.SET_COOKIE));
    }

    // Verifies that cookie logout clears client cookies even when refresh-token revocation fails.
    @Test
    void cookieLogoutClearsCookiesWhenRefreshTokenIsInvalid() {
        UserService userService = mock(UserService.class);
        AuthService authService = mock(AuthService.class);
        when(authService.logout(null)).thenReturn(false);
        MockHttpServletRequest request = new MockHttpServletRequest();

        ResponseEntity<ApiResponse<Void>> response = new AuthController(
                userService,
                authService,
                jwtProperties(TokenTransport.COOKIE))
                .logout(null, request);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals(2, response.getHeaders().get(HttpHeaders.SET_COOKIE).size());
        assertTrue(response.getHeaders().get(HttpHeaders.SET_COOKIE).stream()
                .allMatch(cookie -> cookie.contains("Max-Age=0")));
    }

    // Verifies that cookie logout passes the refresh cookie without requiring access-token authentication.
    @Test
    void cookieLogoutUsesRefreshCookieWithoutAuthentication() {
        UserService userService = mock(UserService.class);
        AuthService authService = mock(AuthService.class);
        when(authService.logout("refresh-token")).thenReturn(true);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("REFRESH_TOKEN", "refresh-token"));

        ResponseEntity<ApiResponse<Void>> response = new AuthController(
                userService,
                authService,
                jwtProperties(TokenTransport.COOKIE))
                .logout(null, request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(authService).logout("refresh-token");
    }

    private static UserEntity user(String id) {
        UserEntity user = new UserEntity();
        user.setId(id);
        user.setEmail("user@example.com");
        user.setIsActive(true);
        return user;
    }

    private static JwtProperties jwtProperties(TokenTransport transport) {
        return new JwtProperties(
                Base64.getEncoder().encodeToString(new byte[32]),
                transport,
                Duration.ofMinutes(15),
                Duration.ofDays(30),
                true,
                "Strict",
                "ACCESS_TOKEN",
                "REFRESH_TOKEN");
    }
}
