package com.elsyah.tech.javara.response;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.http.HttpStatus;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * ApiResponse is a generic response wrapper for API responses. 
 * It provides a standardized structure for success and failure responses, including metadata such as developer information, HTTP status code, message, and additional fields.
 * The class supports adding custom fields to the response while ensuring that standard fields are not overridden.
 * 
 * @param <T> the type of the data included in the response
 * @author Elmansyah
 * @version 1.0.0
 * @since 2026-11-18
 * @contact https://www.linkedin.com/in/elmansyah
 */
public final class ApiResponse<T> {
    private static final String DEVELOPER = "Elmansyah";
    private static final String VERSION = "v1";
    private static final String CONTACT = "https://www.linkedin.com/in/elmansyah";
    private static final Set<String> STANDARD_FIELDS = Set.of(
            "developer", "httpCode", "message", "success", "version", "contact", "error", "data");

    private final String developer = DEVELOPER;
    private final int httpCode;
    private final String message;
    private final boolean success;
    private final String version = VERSION;
    private final String contact = CONTACT;
    private final String error;
    private final T data;
    private final Map<String, Object> additionalFields = new LinkedHashMap<>();

    private ApiResponse(HttpStatus status, String message, String error, T data) {
        this.httpCode = status.value();
        this.message = message;
        this.success = status.is2xxSuccessful();
        this.error = error;
        this.data = data;
    }

    public static <T> ApiResponse<T> success(HttpStatus status, String message, T data) {
        if (!status.is2xxSuccessful()) {
            throw new IllegalArgumentException("Success response requires a 2xx status");
        }
        return new ApiResponse<>(status, message, null, data);
    }

    public static <T> ApiResponse<T> failure(HttpStatus status, String error) {
        if (!status.isError()) {
            throw new IllegalArgumentException("Failure response requires an error status");
        }
        return new ApiResponse<>(status, null, error, null);
    }

    public ApiResponse<T> withField(String name, Object value) {
        Objects.requireNonNull(name, "Field name must not be null");
        if (name.isBlank() || STANDARD_FIELDS.contains(name)) {
            throw new IllegalArgumentException("Invalid or reserved response field: " + name);
        }
        additionalFields.put(name, value);
        return this;
    }

    public String getDeveloper() {
        return developer;
    }

    public int getHttpCode() {
        return httpCode;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String getMessage() {
        return message;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getVersion() {
        return version;
    }

    public String getContact() {
        return contact;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String getError() {
        return error;
    }

    public T getData() {
        return data;
    }

    @JsonAnyGetter
    public Map<String, Object> getAdditionalFields() {
        return Collections.unmodifiableMap(additionalFields);
    }
}
