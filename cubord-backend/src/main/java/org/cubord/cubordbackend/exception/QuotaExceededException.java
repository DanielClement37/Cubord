
package org.cubord.cubordbackend.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when resource quotas are exceeded (e.g., max locations per household).
 */
public class QuotaExceededException extends CubordException {
    public QuotaExceededException(String resource, int currentCount, int maxAllowed) {
        super("QUOTA_EXCEEDED", 
              String.format("Cannot create more %s. Current: %d, Maximum allowed: %d", 
                          resource, currentCount, maxAllowed), 
              HttpStatus.PAYMENT_REQUIRED);
    }
    
    public QuotaExceededException(String message) {
        super("QUOTA_EXCEEDED", message, HttpStatus.PAYMENT_REQUIRED);
    }
}
