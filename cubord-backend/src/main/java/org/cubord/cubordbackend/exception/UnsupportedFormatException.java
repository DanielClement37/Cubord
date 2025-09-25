package org.cubord.cubordbackend.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when an unsupported file format or content type is provided.
 */
public class UnsupportedFormatException extends CubordException {
    public UnsupportedFormatException(String providedFormat, String[] supportedFormats) {
        super("UNSUPPORTED_FORMAT", 
              String.format("Format '%s' is not supported. Supported formats: %s", 
                          providedFormat, String.join(", ", supportedFormats)), 
              HttpStatus.UNSUPPORTED_MEDIA_TYPE);
    }
    
    public UnsupportedFormatException(String message) {
        super("UNSUPPORTED_FORMAT", message, HttpStatus.UNSUPPORTED_MEDIA_TYPE);
    }
}
