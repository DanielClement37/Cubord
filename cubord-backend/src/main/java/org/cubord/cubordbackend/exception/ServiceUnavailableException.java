
package org.cubord.cubordbackend.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when a service is temporarily unavailable.
 */
public class ServiceUnavailableException extends CubordException {
    public ServiceUnavailableException(String message, Throwable cause) {
        super("SERVICE_UNAVAILABLE", message, HttpStatus.SERVICE_UNAVAILABLE, cause);
    }
}
