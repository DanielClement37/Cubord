package org.cubord.cubordbackend.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exception thrown when a user attempts to access a resource they don't have permission for.
 */
@ResponseStatus(HttpStatus.FORBIDDEN)
public class ForbiddenException extends RuntimeException {
    /**
     * Creates a new ForbiddenException with the specified error message.
     *
     * @param message The error message describing why access is forbidden
     */
    public ForbiddenException(String message) {
        super(message);
    }
}