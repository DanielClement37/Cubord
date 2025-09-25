
package org.cubord.cubordbackend.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when a service is temporarily unavailable.
 */
public class ServiceUnavailableException extends CubordException {
    public ServiceUnavailableException(String serviceName, String reason) {
        super("SERVICE_UNAVAILABLE", 
              String.format("Service '%s' is currently unavailable: %s", serviceName, reason), 
              HttpStatus.SERVICE_UNAVAILABLE);
    }
    
    public ServiceUnavailableException(String message) {
        super("SERVICE_UNAVAILABLE", message, HttpStatus.SERVICE_UNAVAILABLE);
    }
}
