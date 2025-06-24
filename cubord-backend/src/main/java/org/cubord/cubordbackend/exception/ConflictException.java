
package org.cubord.cubordbackend.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exception thrown when a resource conflict occurs (e.g., duplicate name).
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class ConflictException extends RuntimeException {
    /**
     * Creates a new ConflictException with the specified error message.
     *
     * @param message The error message describing the conflict
     */
    public ConflictException(String message) {
        super(message);
    }
}