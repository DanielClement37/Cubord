
package org.cubord.cubordbackend.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when API rate limits are exceeded.
 */
public class RateLimitExceededException extends CubordException {
    public RateLimitExceededException(String operation, int limit, String timeWindow) {
        super("RATE_LIMIT_EXCEEDED", 
              String.format("Rate limit exceeded for '%s'. Limit: %d per %s", 
                          operation, limit, timeWindow), 
              HttpStatus.TOO_MANY_REQUESTS);
    }
    
    public RateLimitExceededException(String message) {
        super("RATE_LIMIT_EXCEEDED", message, HttpStatus.TOO_MANY_REQUESTS);
    }
}
