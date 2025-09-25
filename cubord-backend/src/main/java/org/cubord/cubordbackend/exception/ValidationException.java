package org.cubord.cubordbackend.exception;

import org.springframework.http.HttpStatus;

public class ValidationException extends CubordException {
    public ValidationException(String message) {
        super("VALIDATION_ERROR", message, HttpStatus.BAD_REQUEST);
    }

    public ValidationException(String message, Throwable cause) {
        super("VALIDATION_ERROR", message, HttpStatus.BAD_REQUEST, cause);
    }
}