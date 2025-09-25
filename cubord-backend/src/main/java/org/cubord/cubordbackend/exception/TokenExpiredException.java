package org.cubord.cubordbackend.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exception thrown when a JWT token has expired.
 */
public class TokenExpiredException extends CubordException {
    public TokenExpiredException(String message) {
        super("TOKEN_EXPIRED", message, HttpStatus.UNAUTHORIZED);
    }

    public TokenExpiredException(String message, Throwable cause) {
        super("TOKEN_EXPIRED", message, HttpStatus.UNAUTHORIZED, cause);
    }
}