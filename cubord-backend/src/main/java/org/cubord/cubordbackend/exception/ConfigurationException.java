package org.cubord.cubordbackend.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when there are configuration-related issues.
 */
public class ConfigurationException extends CubordException {
    public ConfigurationException(String configProperty, String issue) {
        super("CONFIGURATION_ERROR", 
              String.format("Configuration issue with '%s': %s", configProperty, issue), 
              HttpStatus.INTERNAL_SERVER_ERROR);
    }
    
    public ConfigurationException(String message) {
        super("CONFIGURATION_ERROR", message, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
