package com.elsyah.tech.javara.middleware.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.elsyah.tech.javara.middleware.ratelimit.LoginRateLimiter;

@Component
public class LoginRateLimitInterceptor implements HandlerInterceptor {
    private final LoginRateLimiter loginRateLimiter;

    public LoginRateLimitInterceptor(LoginRateLimiter loginRateLimiter) {
        this.loginRateLimiter = loginRateLimiter;
    }

    public boolean preHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler) {
        if ("POST".equalsIgnoreCase(request.getMethod())) {
            loginRateLimiter.checkAllowed(request.getRemoteAddr());
        }
        return true;
    }
}