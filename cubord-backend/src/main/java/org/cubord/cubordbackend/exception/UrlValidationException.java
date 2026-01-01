
package org.cubord.cubordbackend.exception;

/**
 * Exception thrown when URL validation fails due to security concerns.
 * 
 * <p>This exception is used to indicate that a URL has been rejected because:</p>
 * <ul>
 *   <li>It points to an internal/private network address (SSRF prevention)</li>
 *   <li>It's not in the allowed domain whitelist</li>
 *   <li>It uses an unsupported protocol</li>
 *   <li>The URL format is invalid</li>
 * </ul>
 */
public class UrlValidationException extends RuntimeException {

    /**
     * Constructs a new URL validation exception with the specified detail message.
     *
     * @param message the detail message
     */
    public UrlValidationException(String message) {
        super(message);
    }

    /**
     * Constructs a new URL validation exception with the specified detail message and cause.
     *
     * @param message the detail message
     * @param cause the cause
     */
    public UrlValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
