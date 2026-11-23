package com.elsyah.tech.javara.service.common;

import org.springframework.http.HttpStatus;

/**
 * ServiceResult is a generic class that encapsulates the result of a service operation.
 * It contains the data returned by the service, the HTTP status code indicating the outcome of the operation, and 
 * a message providing additional context.
 * The class provides a method to check if the result represents an error based on the HTTP status code.
 *
 * @param <T> the type of the data returned by the service
 * @author Elmansyah
 * @version 1.0.0
 * @since 2026-11-18
 * @contact https://www.linkedin.com/in/elmansyah
 */
public record ServiceResult<T>(T data, HttpStatus status, String message) {
    public boolean isError() {
        return status.isError();
    }
}