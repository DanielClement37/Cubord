
package org.cubord.cubordbackend.util;

import org.springframework.web.util.HtmlUtils;

import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Utility class for sanitizing error messages to prevent XSS attacks.
 */
public class MessageSanitizer {
    
    private static final int MAX_MESSAGE_LENGTH = 500;
    private static final int MAX_CORRELATION_ID_LENGTH = 50;
    private static final Pattern SAFE_MESSAGE_PATTERN = Pattern.compile("^[a-zA-Z0-9\\s\\-_.,;:!?()\\[\\]{}@#$%^&*+=<>|/\\\\\"'`~]*$");
    private static final Pattern SAFE_CORRELATION_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9\\-_]{1,50}$");
    
    /**
     * Sanitizes an error message to prevent XSS attacks.
     * Applies HTML escaping and length limits.
     */
    public static String sanitizeMessage(String message) {
        if (message == null) {
            return "An error occurred";
        }
        
        // Truncate if too long
        String truncated = message.length() > MAX_MESSAGE_LENGTH 
            ? message.substring(0, MAX_MESSAGE_LENGTH) + "..." 
            : message;
        
        // HTML escape to prevent XSS
        return HtmlUtils.htmlEscape(truncated);
    }
    
    /**
     * Sanitizes an error code to ensure it only contains safe characters.
     */
    public static String sanitizeErrorCode(String errorCode) {
        if (errorCode == null) {
            return "UNKNOWN_ERROR";
        }
        
        // Only allow alphanumeric, underscores, and hyphens
        return errorCode.replaceAll("[^A-Z0-9_-]", "").toUpperCase();
    }
    
    /**
     * Sanitizes a correlation ID to prevent XSS attacks.
     * Returns a new UUID if the input is not safe.
     */
    public static String sanitizeCorrelationId(String correlationId) {
        if (correlationId == null || correlationId.trim().isEmpty()) {
            return UUID.randomUUID().toString();
        }
        
        String trimmed = correlationId.trim();
        
        // Check length
        if (trimmed.length() > MAX_CORRELATION_ID_LENGTH) {
            return UUID.randomUUID().toString();
        }
        
        // Check if it matches safe pattern
        if (!SAFE_CORRELATION_ID_PATTERN.matcher(trimmed).matches()) {
            return UUID.randomUUID().toString();
        }
        
        // Additional HTML escape as final safety measure
        return HtmlUtils.htmlEscape(trimmed);
    }
    
    /**
     * Sanitizes validation details map to prevent XSS in field names and messages.
     */
    public static Map<String, Object> sanitizeValidationDetails(Map<String, Object> details) {
        if (details == null || details.isEmpty()) {
            return details;
        }
        
        return details.entrySet().stream()
            .collect(java.util.stream.Collectors.toMap(
                entry -> sanitizeFieldName(entry.getKey()),
                entry -> sanitizeMessage(String.valueOf(entry.getValue()))
            ));
    }
    
    /**
     * Sanitizes field names to prevent XSS.
     */
    private static String sanitizeFieldName(String fieldName) {
        if (fieldName == null) {
            return "unknown_field";
        }
        
        // Only allow alphanumeric, dots, underscores, and hyphens for field names
        return fieldName.replaceAll("[^a-zA-Z0-9._-]", "");
    }
    
    /**
     * Creates a safe generic message that doesn't expose sensitive information.
     */
    public static String createSafeGenericMessage(String operationType) {
        if (operationType == null || !SAFE_MESSAGE_PATTERN.matcher(operationType).matches()) {
            return "An error occurred while processing your request";
        }
        return String.format("An error occurred while processing %s", sanitizeMessage(operationType));
    }
}
