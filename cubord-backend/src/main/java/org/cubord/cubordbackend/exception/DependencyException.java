package org.cubord.cubordbackend.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when an operation cannot be completed due to dependent resources.
 */
public class DependencyException extends CubordException {
    public DependencyException(String resource, String dependentResource) {
        super("DEPENDENCY_VIOLATION", 
              String.format("Cannot delete %s because it has dependent %s", 
                          resource, dependentResource), 
              HttpStatus.CONFLICT);
    }
    
    public DependencyException(String message) {
        super("DEPENDENCY_VIOLATION", message, HttpStatus.CONFLICT);
    }
}
