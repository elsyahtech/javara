package com.elsyah.tech.javara.middleware.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.elsyah.tech.javara.middleware.ratelimit.AuthRateLimiter;
import com.elsyah.tech.javara.middleware.ratelimit.LoginRateLimitProperties;
import com.elsyah.tech.javara.middleware.ratelimit.RateLimitSettings;
import com.elsyah.tech.javara.middleware.ratelimit.RefreshRateLimitProperties;
import com.elsyah.tech.javara.middleware.ratelimit.RegisterRateLimitProperties;
import com.elsyah.tech.javara.middleware.ratelimit.SessionReauthRateLimitProperties;

@Component
public class AuthRateLimitInterceptor implements HandlerInterceptor {
    private static final String LOGIN_PATH = "/api/v1/auth/login";
    private static final String REGISTER_PATH = "/api/v1/auth/register";
    private static final String REFRESH_PATH = "/api/v1/auth/refresh";
    private static final String SESSION_PATH_PREFIX = "/api/v1/auth/sessions/";
    private static final String SESSION_TERMINATE_ALL_PATH =
            "/api/v1/auth/sessions/terminate-all";
    private static final String ADMIN_SESSION_PATH_PREFIX = "/api/v1/admin/users/";

    private final AuthRateLimiter rateLimiter;
    private final LoginRateLimitProperties loginProperties;
    private final RegisterRateLimitProperties registerProperties;
    private final RefreshRateLimitProperties refreshProperties;
    private final SessionReauthRateLimitProperties sessionReauthProperties;

    public AuthRateLimitInterceptor(
            AuthRateLimiter rateLimiter,
            LoginRateLimitProperties loginProperties,
            RegisterRateLimitProperties registerProperties,
            RefreshRateLimitProperties refreshProperties,
            SessionReauthRateLimitProperties sessionReauthProperties) {
        this.rateLimiter = rateLimiter;
        this.loginProperties = loginProperties;
        this.registerProperties = registerProperties;
        this.refreshProperties = refreshProperties;
        this.sessionReauthProperties = sessionReauthProperties;
    }

    @Override
    public boolean preHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler) {
        if ("POST".equalsIgnoreCase(request.getMethod())) {
            String contextPath = request.getContextPath();
            String path = request.getRequestURI().substring(contextPath.length());
            RateLimitSettings settings = settingsFor(path);
            if (settings != null && settings.enabled()) {
                rateLimiter.checkAllowed(request.getRemoteAddr(), settings);
            }
        }
        return true;
    }

    private RateLimitSettings settingsFor(String path) {
        return switch (path) {
            case LOGIN_PATH -> loginProperties;
            case REGISTER_PATH -> registerProperties;
            case REFRESH_PATH -> refreshProperties;
            default -> isSessionReauthenticationPath(path)
                    ? sessionReauthProperties
                    : null;
        };
    }

    private static boolean isSessionReauthenticationPath(String path) {
        boolean userSessionTermination = !SESSION_TERMINATE_ALL_PATH.equals(path)
                && path.startsWith(SESSION_PATH_PREFIX)
                && path.endsWith("/terminate");
        boolean administratorSessionTermination = path.startsWith(ADMIN_SESSION_PATH_PREFIX)
                && path.endsWith("/sessions/terminate");
        return SESSION_TERMINATE_ALL_PATH.equals(path)
                || userSessionTermination
                || administratorSessionTermination;
    }
}