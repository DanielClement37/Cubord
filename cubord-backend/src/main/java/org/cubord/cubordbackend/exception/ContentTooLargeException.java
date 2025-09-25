
package org.cubord.cubordbackend.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when content exceeds size limits.
 */
public class ContentTooLargeException extends CubordException {
    public ContentTooLargeException(long actualSize, long maxSize) {
        super("CONTENT_TOO_LARGE", 
              String.format("Content size %d bytes exceeds maximum allowed size of %d bytes", 
                          actualSize, maxSize), 
              HttpStatus.PAYLOAD_TOO_LARGE);
    }
    
    public ContentTooLargeException(String message) {
        super("CONTENT_TOO_LARGE", message, HttpStatus.PAYLOAD_TOO_LARGE);
    }
}
