package com.elsyah.tech.javara.exception;

import org.springframework.http.HttpStatus;

/**
 * ApiException is a custom exception class that extends RuntimeException. 
 * It is used to represent API-related errors in the application. 
 * The class includes an HTTP status code to indicate the nature of the error.
 * 
 * @author Elmansyah
 * @version 1.0.0
 * @since 2026-11-18
 * @contact https://www.linkedin.com/in/elmansyah
 */
public class ApiException extends RuntimeException {
    private final HttpStatus status;

    public ApiException(String message, HttpStatus httpStatus) {
        super(message);
        this.status = httpStatus;
    }
    
    public HttpStatus getHttpStatus() {
        return status;
    }
}
