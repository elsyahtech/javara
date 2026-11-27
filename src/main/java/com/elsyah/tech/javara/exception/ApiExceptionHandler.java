package com.elsyah.tech.javara.exception;

import jakarta.servlet.http.HttpServletRequest;

import java.util.stream.Collectors;

import org.hibernate.exception.ConstraintViolationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.elsyah.tech.javara.response.common.ApiResponse;
import com.elsyah.tech.javara.middleware.ratelimit.RateLimitExceededException;
import com.elsyah.tech.javara.middleware.ratelimit.RateLimitUnavailableException;
import com.elsyah.tech.javara.response.auth.PublicRegistrationResponse;

/**
 * ApiExceptionHandler is a global exception handler for the application. 
 * It handles ApiException instances and converts them into ProblemDetail responses, which are standardized error responses for REST APIs.
 * The class uses the @RestControllerAdvice annotation to apply to all controllers in the application.
 * 
 * The handleException method handles ApiException instances, returning a ProblemDetail response with the appropriate HTTP status and error message.
 * 
 * The handleDataIntegrityViolation method handles DataIntegrityViolationException instances, specifically checking for violations of 
 * the unique constraint on the email field for active users. If such a violation is detected, it returns a 409 Conflict response with a relevant error message. 
 * For other data integrity violations, it logs the error and returns a 500 Internal Server Error response.
 * 
 * @author Elmansyah
 * @version 1.0.0
 * @since 2026-11-18
 * @contact https://www.linkedin.com/in/elmansyah
 */
@RestControllerAdvice 
public class ApiExceptionHandler {
    private static final Logger logger = LoggerFactory.getLogger(ApiExceptionHandler.class);
    private static final String ACTIVE_USER_EMAIL_INDEX = "idx_users_email_active_unique";

    @ExceptionHandler(ApiException.class)
    public ProblemDetail handleException(ApiException exception) {
        return ProblemDetail.forStatusAndDetail(exception.getHttpStatus(), exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleRequestValidation(
            MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .map(ApiExceptionHandler::validationMessage)
                .distinct()
                .collect(Collectors.joining("; "));
        if (message.isBlank()) {
            message = "Request validation failed";
        }
        HttpStatus status = HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(ApiResponse.failure(status, message));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrityViolation(
            DataIntegrityViolationException exception,
            HttpServletRequest request) {
        if (violatesActiveUserEmailUniqueness(exception)) {
            if (isPublicRegistrationRequest(request)) {
                return PublicRegistrationResponse.accepted();
            }
            HttpStatus status = HttpStatus.CONFLICT;
            return ResponseEntity.status(status)
                    .body(ApiResponse.failure(status, "Email already exists"));
        }

        logger.error("Unhandled data integrity violation", exception);
        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        return ResponseEntity.status(status)
                .body(ApiResponse.failure(status, "A data integrity constraint was violated"));
    }

    private static boolean isPublicRegistrationRequest(HttpServletRequest request) {
        return "POST".equals(request.getMethod())
                && request.getRequestURI()
                        .equals(request.getContextPath() + "/api/v1/auth/register");
    }

    @ExceptionHandler({
            OptimisticLockingFailureException.class,
            PessimisticLockingFailureException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleLockConflict(RuntimeException exception) {
        logger.warn("Concurrent update conflict", exception);
        HttpStatus status = HttpStatus.CONFLICT;
        return ResponseEntity.status(status)
                .body(ApiResponse.failure(status, "Resource was modified concurrently"));
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleRateLimitExceeded(
            RateLimitExceededException exception) {
        HttpStatus status = HttpStatus.TOO_MANY_REQUESTS;
        return ResponseEntity.status(status)
                .header(HttpHeaders.RETRY_AFTER,
                        Long.toString(exception.getRetryAfterSeconds()))
                .body(ApiResponse.failure(status, "Too many requests. Try again later."));
    }

    @ExceptionHandler(RateLimitUnavailableException.class)
    public ResponseEntity<ApiResponse<Void>> handleRateLimitUnavailable(
            RateLimitUnavailableException exception) {
        logger.error("Rejecting request because Redis rate limiting is unavailable",
                exception.getCause());
        HttpStatus status = HttpStatus.SERVICE_UNAVAILABLE;
        return ResponseEntity.status(status)
                .body(ApiResponse.failure(status, "Request is temporarily unavailable"));
    }

    private static boolean violatesActiveUserEmailUniqueness(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation
                    && ACTIVE_USER_EMAIL_INDEX.equals(violation.getConstraintName())) {
                return true;
            }
        }
        return false;
    }

    private static String validationMessage(FieldError error) {
        if ("userUpdateRequest".equals(error.getObjectName())
                && "password".equals(error.getField())) {
            return "password: Use the dedicated password-change endpoint";
        }
        return switch (error.getField()) {
            case "password" -> "password: Use a password that meets the documented policy";
            case "currentPassword" -> "currentPassword: Provide a valid current password";
            case "newPassword" -> "newPassword: Password must be 8-72 characters";
            default -> error.getField() + ": " + error.getDefaultMessage();
        };
    }
}
