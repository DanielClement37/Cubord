
package org.cubord.cubordbackend.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when a user has access to a resource but lacks specific permissions for an operation.
 * Different from AccessDeniedException which is for complete access denial.
 */
public class InsufficientPermissionException extends CubordException {
    public InsufficientPermissionException(String operation, String resource) {
        super("INSUFFICIENT_PERMISSION", 
              String.format("Insufficient permission to perform '%s' on %s", operation, resource), 
              HttpStatus.FORBIDDEN);
    }
    
    public InsufficientPermissionException(String message) {
        super("INSUFFICIENT_PERMISSION", message, HttpStatus.FORBIDDEN);
    }
}
