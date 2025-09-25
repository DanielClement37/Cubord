package org.cubord.cubordbackend.dto.error;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.cubord.cubordbackend.exception.CubordException;
import org.cubord.cubordbackend.util.MessageSanitizer;

import java.time.Instant;
import java.util.Map;

/**
 * Standardized error response format for all API errors.
 * Includes correlation ID for distributed tracing and debugging.
 * All messages are sanitized to prevent XSS attacks.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
    @JsonProperty("error_code")
    String errorCode,
    
    @JsonProperty("message")
    String message,
    
    @JsonProperty("timestamp")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
    Instant timestamp,
    
    @JsonProperty("correlation_id")
    String correlationId,
    
    @JsonProperty("details")
    Map<String, Object> details
) {
    /**
     * Creates an ErrorResponse from a CubordException with correlation ID.
     * Sanitizes all user-facing content.
     */
    public static ErrorResponse of(CubordException ex, String correlationId) {
        return new ErrorResponse(
            MessageSanitizer.sanitizeErrorCode(ex.getErrorCode()),
            MessageSanitizer.sanitizeMessage(ex.getMessage()),
            Instant.now(),
            MessageSanitizer.sanitizeCorrelationId(correlationId),
            null
        );
    }
    
    /**
     * Creates an ErrorResponse with additional details.
     * Sanitizes all user-facing content including details.
     */
    public static ErrorResponse withDetails(CubordException ex, String correlationId, Map<String, Object> details) {
        return new ErrorResponse(
            MessageSanitizer.sanitizeErrorCode(ex.getErrorCode()),
            MessageSanitizer.sanitizeMessage(ex.getMessage()),
            Instant.now(),
            MessageSanitizer.sanitizeCorrelationId(correlationId),
            MessageSanitizer.sanitizeValidationDetails(details)
        );
    }
    
    /**
     * Creates a generic error response with custom error code.
     * Sanitizes all user-facing content.
     */
    public static ErrorResponse of(String errorCode, String message, String correlationId) {
        return new ErrorResponse(
            MessageSanitizer.sanitizeErrorCode(errorCode),
            MessageSanitizer.sanitizeMessage(message),
            Instant.now(),
            MessageSanitizer.sanitizeCorrelationId(correlationId),
            null
        );
    }
    
    /**
     * Creates a safe error response that doesn't expose potentially malicious content.
     */
    public static ErrorResponse safe(String errorCode, String correlationId) {
        return new ErrorResponse(
            MessageSanitizer.sanitizeErrorCode(errorCode),
            "An error occurred while processing your request",
            Instant.now(),
            MessageSanitizer.sanitizeCorrelationId(correlationId),
            null
        );
    }
}
