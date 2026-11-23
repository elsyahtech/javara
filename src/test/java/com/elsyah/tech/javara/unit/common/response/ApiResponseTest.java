package com.elsyah.tech.javara.unit.common.response;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.elsyah.tech.javara.response.common.ApiResponse;

class ApiResponseTest {

    // Verifies the fields and status of a successful API response.
    @Test
    void shouldCreateSuccessResponse() {
        ApiResponse<String> response = ApiResponse.success(
                HttpStatus.OK, "User found", "value");

        assertThat(response.getHttpCode()).isEqualTo(200);
        assertThat(response.getMessage()).isEqualTo("User found");
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData()).isEqualTo("value");
        assertThat(response.getError()).isNull();
        assertThat(response.getDeveloper()).isEqualTo("Elmansyah");
        assertThat(response.getVersion()).isEqualTo("v1");
    }

    // Verifies the fields and status of a failure API response.
    @Test
    void shouldCreateFailureResponse() {
        ApiResponse<Void> response = ApiResponse.failure(
                HttpStatus.NOT_FOUND, "User not found");

        assertThat(response.getHttpCode()).isEqualTo(404);
        assertThat(response.getError()).isEqualTo("User not found");
        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getData()).isNull();
        assertThat(response.getMessage()).isNull();
    }

    // Verifies that custom fields can be added to an API response.
    @Test
    void shouldAddAdditionalField() {
        ApiResponse<Void> response = ApiResponse.<Void>failure(
                        HttpStatus.BAD_REQUEST, "Invalid request")
                .withField("traceId", "trace-123");

        assertThat(response.getAdditionalFields())
                .containsEntry("traceId", "trace-123");
    }

    // Verifies that reserved standard fields cannot be overridden.
    @Test
    void shouldRejectReservedAdditionalField() {
        ApiResponse<Void> response = ApiResponse.failure(
                HttpStatus.BAD_REQUEST, "Invalid request");

        assertThatThrownBy(() -> response.withField("data", "override"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid or reserved response field");
    }

    // Verifies that success and failure responses require matching HTTP status families.
    @Test
    void shouldRequireMatchingStatusForSuccessAndFailure() {
        assertThatThrownBy(() -> ApiResponse.success(HttpStatus.BAD_REQUEST, "Bad", "value"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ApiResponse.failure(HttpStatus.OK, "Error"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
