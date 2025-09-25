package org.cubord.cubordbackend.exception;

import org.springframework.http.HttpStatus;

public class ExternalServiceException extends CubordException {
    public ExternalServiceException(String service, String message, Throwable cause) {
        super("EXTERNAL_SERVICE_ERROR",
                String.format("External service %s error: %s", service, message),
                HttpStatus.SERVICE_UNAVAILABLE, cause);
    }

    public ExternalServiceException(String service, String message) {
        super("EXTERNAL_SERVICE_ERROR",
                String.format("External service %s error: %s", service, message),
                HttpStatus.SERVICE_UNAVAILABLE);
    }
}
