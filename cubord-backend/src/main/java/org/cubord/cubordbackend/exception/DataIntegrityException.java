package org.cubord.cubordbackend.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when data integrity constraints would be violated.
 */
public class DataIntegrityException extends CubordException {
    public DataIntegrityException(String message) {
        super("DATA_INTEGRITY_VIOLATION", message, HttpStatus.CONFLICT);
    }
    
    public DataIntegrityException(String message, Throwable cause) {
        super("DATA_INTEGRITY_VIOLATION", message, HttpStatus.CONFLICT, cause);
    }
}
