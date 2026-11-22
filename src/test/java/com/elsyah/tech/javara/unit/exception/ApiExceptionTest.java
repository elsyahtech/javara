package com.elsyah.tech.javara.unit.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.dao.DataIntegrityViolationException;
import org.hibernate.exception.ConstraintViolationException;
import java.sql.SQLException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;

import com.elsyah.tech.javara.response.ApiResponse;
import com.elsyah.tech.javara.exception.ApiException;
import com.elsyah.tech.javara.exception.ApiExceptionHandler;
import com.elsyah.tech.javara.middleware.ratelimit.RateLimitExceededException;
import com.elsyah.tech.javara.middleware.ratelimit.RateLimitUnavailableException;

class ApiExceptionTest {

    // Verifies that ApiException preserves its message and HTTP status.
    @Test
    void shouldExposeMessageAndStatus() {
        ApiException exception =
                new ApiException("User not found", HttpStatus.NOT_FOUND);

        assertThat(exception.getMessage()).isEqualTo("User not found");
        assertThat(exception.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // Verifies that the exception handler converts ApiException into a ProblemDetail response.
    @Test
    void shouldCreateProblemDetail() {
        ApiExceptionHandler handler = new ApiExceptionHandler();

        ProblemDetail problem = handler.handleException(
                new ApiException("Forbidden", HttpStatus.FORBIDDEN));

        assertThat(problem.getStatus()).isEqualTo(403);
        assertThat(problem.getDetail()).isEqualTo("Forbidden");
    }

    // Verifies that an unrecognized data integrity violation returns HTTP 500.
    @Test
    void shouldReturnConflictForGenericDataIntegrityViolation() {
        ApiExceptionHandler handler = new ApiExceptionHandler();

        ResponseEntity<ApiResponse<Void>> response = handler
                .handleDataIntegrityViolation(
                        new DataIntegrityViolationException("constraint violation"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getError())
                .isEqualTo("A data integrity constraint was violated");
    }

    // Verifies that the active-email unique constraint is translated to HTTP 409.
    @Test
    void shouldReturnConflictForActiveUserEmailConstraintViolation() {
        ApiExceptionHandler handler = new ApiExceptionHandler();
        ConstraintViolationException constraintViolation =
                new ConstraintViolationException(
                        "duplicate active email",
                        new SQLException("duplicate key"),
                        "idx_users_email_active_unique");
        DataIntegrityViolationException exception =
                new DataIntegrityViolationException(
                        "constraint violation",
                        constraintViolation);

        ResponseEntity<ApiResponse<Void>> response =
                handler.handleDataIntegrityViolation(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getError()).isEqualTo("Email already exists");
    }

    // Verifies that optimistic locking conflicts return HTTP 409.
    @Test
    void shouldReturnConflictForOptimisticLockFailure() {
        ApiExceptionHandler handler = new ApiExceptionHandler();

        ResponseEntity<ApiResponse<Void>> response = handler.handleLockConflict(
                new OptimisticLockingFailureException("stale entity"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getError())
                .isEqualTo("Resource was modified concurrently");
    }

    // Verifies that pessimistic database lock failures return HTTP 409.
    @Test
    void shouldReturnConflictForPessimisticLockFailure() {
        ApiExceptionHandler handler = new ApiExceptionHandler();

        ResponseEntity<ApiResponse<Void>> response = handler.handleLockConflict(
                new PessimisticLockingFailureException("row locked"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getError())
                .isEqualTo("Resource was modified concurrently");
    }

    // Verifies that a rate-limited login returns HTTP 429 and a Retry-After header.
    @Test
    void shouldReturnTooManyRequestsForRateLimit() {
        ApiExceptionHandler handler = new ApiExceptionHandler();

        ResponseEntity<ApiResponse<Void>> response =
                handler.handleRateLimitExceeded(
                        new RateLimitExceededException(1500));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("2");
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getError())
                .isEqualTo("Too many requests. Try again later.");
    }

    // Verifies that Redis failures reject login with HTTP 503.
    @Test
    void shouldReturnServiceUnavailableWhenRateLimitStoreFails() {
        ApiExceptionHandler handler = new ApiExceptionHandler();

        ResponseEntity<ApiResponse<Void>> response =
                handler.handleRateLimitUnavailable(
                        new RateLimitUnavailableException(
                                new IllegalStateException("Redis unavailable")));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getError()).isEqualTo("Request is temporarily unavailable");
    }
}
