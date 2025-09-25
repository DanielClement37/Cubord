package org.cubord.cubordbackend.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when a resource is in an invalid state for the requested operation.
 */
public class ResourceStateException extends CubordException {
    public ResourceStateException(String resource, String currentState, String operation) {
        super("INVALID_RESOURCE_STATE", 
              String.format("%s is in state '%s' and cannot perform operation '%s'", 
                          resource, currentState, operation), 
              HttpStatus.CONFLICT);
    }
    
    public ResourceStateException(String message) {
        super("INVALID_RESOURCE_STATE", message, HttpStatus.CONFLICT);
    }
}
