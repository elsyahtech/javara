package com.elsyah.tech.javara.service.auth;

import org.springframework.http.HttpStatus;

import com.elsyah.tech.javara.entity.user.UserEntity;
import com.elsyah.tech.javara.response.auth.MfaChallengeResponse;

public record AuthServiceResult(
        UserEntity user,
        String accessToken,
        String refreshToken,
        HttpStatus status,
        String message,
        MfaChallengeResponse mfaChallenge) {
    public AuthServiceResult(
            UserEntity user,
            String accessToken,
            String refreshToken,
            HttpStatus status,
            String message) {
        this(user, accessToken, refreshToken, status, message, null);
    }

    public boolean isError() {
        return status.isError();
    }

    public static AuthServiceResult failure(HttpStatus status, String message) {
        return new AuthServiceResult(null, null, null, status, message, null);
    }

    public static AuthServiceResult challenge(MfaChallengeResponse challenge, String message) {
        return new AuthServiceResult(
                null, null, null, HttpStatus.ACCEPTED, message, challenge);
    }
}
