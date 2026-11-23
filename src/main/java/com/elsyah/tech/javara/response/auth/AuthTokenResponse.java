package com.elsyah.tech.javara.response.auth;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.elsyah.tech.javara.response.user.UserResponse;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuthTokenResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        Long expiresIn,
        UserResponse user) {}
