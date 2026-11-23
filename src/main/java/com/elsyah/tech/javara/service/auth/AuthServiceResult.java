package com.elsyah.tech.javara.service.auth;

import org.springframework.http.HttpStatus;

import com.elsyah.tech.javara.entity.user.UserEntity;

public record AuthServiceResult(
        UserEntity user,
        String accessToken,
        String refreshToken,
        HttpStatus status,
        String message) {
    public boolean isError() {
        return status.isError();
    }

    public static AuthServiceResult failure(HttpStatus status, String message) {
        return new AuthServiceResult(null, null, null, status, message);
    }
}
