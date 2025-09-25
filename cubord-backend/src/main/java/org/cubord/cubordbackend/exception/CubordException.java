package org.cubord.cubordbackend.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Base exception for all Cubord-specific business exceptions.
 * Provides consistent error handling with error codes and HTTP status mappings.
 */
@Getter
public abstract class CubordException extends RuntimeException {
    private final String errorCode;
    private final HttpStatus httpStatus;

    protected CubordException(String errorCode, String message, HttpStatus httpStatus) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }

    protected CubordException(String errorCode, String message, HttpStatus httpStatus, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }

}


