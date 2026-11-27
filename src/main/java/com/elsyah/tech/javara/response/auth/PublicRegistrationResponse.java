package com.elsyah.tech.javara.response.auth;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.elsyah.tech.javara.response.common.ApiResponse;

public final class PublicRegistrationResponse {
    public static final String MESSAGE =
            "If an account can be created with this email, verification instructions will be sent.";

    private PublicRegistrationResponse() {}

    public static ResponseEntity<ApiResponse<Void>> accepted() {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.success(HttpStatus.ACCEPTED, MESSAGE, null));
    }
}
