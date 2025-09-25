
package org.cubord.cubordbackend.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when optimistic locking fails due to concurrent modifications.
 */
public class OptimisticLockException extends CubordException {
    public OptimisticLockException(String resource) {
        super("OPTIMISTIC_LOCK_FAILURE", 
              String.format("The %s has been modified by another user. Please refresh and try again.", 
                          resource), 
              HttpStatus.CONFLICT);
    }
    
    public OptimisticLockException(String message, Throwable cause) {
        super("OPTIMISTIC_LOCK_FAILURE", message, HttpStatus.CONFLICT, cause);
    }
}
