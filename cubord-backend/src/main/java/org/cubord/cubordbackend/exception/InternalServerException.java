package org.cubord.cubordbackend.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception for internal server errors that should not expose implementation details.
 */
public class InternalServerException extends CubordException {
    public InternalServerException(String message) {
        super("INTERNAL_SERVER_ERROR", message, HttpStatus.INTERNAL_SERVER_ERROR);
    }
    
    public InternalServerException(String message, Throwable cause) {
        super("INTERNAL_SERVER_ERROR", message, HttpStatus.INTERNAL_SERVER_ERROR, cause);
    }
}
