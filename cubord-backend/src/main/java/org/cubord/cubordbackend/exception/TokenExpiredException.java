package org.cubord.cubordbackend.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exception thrown when a JWT token has expired.
 */
@ResponseStatus(HttpStatus.UNAUTHORIZED)
public class TokenExpiredException extends RuntimeException {
    /**
     * Creates a new TokenExpiredException with the default error message.
     */
    public TokenExpiredException() {
        super("JWT token has expired");
    }
}