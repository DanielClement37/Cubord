
package org.cubord.cubordbackend.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception thrown when a resource conflict occurs (e.g., duplicate name).
 */
public class ConflictException extends CubordException {
    public ConflictException(String message) {
        super("RESOURCE_CONFLICT", message, HttpStatus.CONFLICT);
    }

    public ConflictException(String message, Throwable cause) {
        super("RESOURCE_CONFLICT", message, HttpStatus.CONFLICT, cause);
    }
}