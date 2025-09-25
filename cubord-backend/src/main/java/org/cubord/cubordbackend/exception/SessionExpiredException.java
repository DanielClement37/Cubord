
package org.cubord.cubordbackend.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when a user session has expired.
 */
public class SessionExpiredException extends CubordException {
    public SessionExpiredException(String message) {
        super("SESSION_EXPIRED", message, HttpStatus.UNAUTHORIZED);
    }
    
    public SessionExpiredException() {
        super("SESSION_EXPIRED", "Your session has expired. Please log in again.", 
              HttpStatus.UNAUTHORIZED);
    }
}
