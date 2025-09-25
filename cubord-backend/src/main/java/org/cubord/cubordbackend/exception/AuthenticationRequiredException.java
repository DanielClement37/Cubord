package org.cubord.cubordbackend.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when authentication is required but not provided.
 */
public class AuthenticationRequiredException extends CubordException {
    public AuthenticationRequiredException(String message) {
        super("AUTHENTICATION_REQUIRED", message, HttpStatus.UNAUTHORIZED);
    }
    
    public AuthenticationRequiredException() {
        super("AUTHENTICATION_REQUIRED", "Authentication is required to access this resource", 
              HttpStatus.UNAUTHORIZED);
    }
}
