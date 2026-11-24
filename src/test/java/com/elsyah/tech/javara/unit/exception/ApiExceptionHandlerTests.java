package com.elsyah.tech.javara.unit.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import com.elsyah.tech.javara.exception.ApiExceptionHandler;

class ApiExceptionHandlerTests {
    @Test
    void validationResponseDoesNotExposeRejectedPasswordValue() {
        String secret = "sensitive-password-value";
        FieldError fieldError = new FieldError(
                "passwordChangeRequest",
                "newPassword",
                secret,
                false,
                null,
                null,
                "newPassword must be 8-72 characters");
        BindingResult bindingResult = mock(BindingResult.class);
        when(bindingResult.getFieldErrors()).thenReturn(List.of(fieldError));
        MethodArgumentNotValidException exception =
                mock(MethodArgumentNotValidException.class);
        when(exception.getBindingResult()).thenReturn(bindingResult);

        var response = new ApiExceptionHandler().handleRequestValidation(exception);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().getError().contains("Password must be 8-72 characters"));
        assertFalse(response.getBody().getError().contains(secret));
    }
}
