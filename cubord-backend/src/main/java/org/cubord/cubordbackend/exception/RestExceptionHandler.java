package org.cubord.cubordbackend.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.cubord.cubordbackend.dto.error.ErrorResponse;
import org.cubord.cubordbackend.util.MessageSanitizer;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * Global exception handler providing consistent error responses across the application.
 * Implements correlation ID tracking, structured logging, and comprehensive XSS prevention.
 */
@ControllerAdvice
@Slf4j
public class RestExceptionHandler {

    /**
     * Handles all CubordException instances with proper error codes and correlation IDs.
     */
    @ExceptionHandler(CubordException.class)
    public ResponseEntity<ErrorResponse> handleCubordException(CubordException ex, HttpServletRequest request) {
        String correlationId = getSafeCorrelationId(request);

        log.warn("Business exception occurred: {} [correlation_id={}]", ex.getMessage(), correlationId, ex);

        ErrorResponse error = ErrorResponse.of(ex, correlationId);
        return ResponseEntity.status(ex.getHttpStatus()).body(error);
    }

    /**
     * Handles Spring Security access denied exceptions.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDeniedException(AccessDeniedException ex, HttpServletRequest request) {
        String correlationId = getSafeCorrelationId(request);

        log.warn("Access denied: {} [correlation_id={}]", ex.getMessage(), correlationId);

        ErrorResponse error = ErrorResponse.of(
                "ACCESS_DENIED",
                "You don't have permission to access this resource",
                correlationId
        );
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
    }

    /**
     * Handles Bean Validation constraint violations.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolationException(
            ConstraintViolationException ex, HttpServletRequest request) {

        String correlationId = getSafeCorrelationId(request);

        Map<String, Object> details = ex.getConstraintViolations().stream()
                .collect(Collectors.toMap(
                        violation -> violation.getPropertyPath().toString(),
                        violation -> violation.getMessage(),
                        (existing, replacement) -> existing
                ));

        log.debug("Constraint violation: {} [correlation_id={}]", ex.getMessage(), correlationId);

        ValidationException validationEx = new ValidationException("Constraint validation failed");
        ErrorResponse error = ErrorResponse.withDetails(validationEx, correlationId, details);

        return ResponseEntity.badRequest().body(error);
    }

    /**
     * Handles method argument validation errors with detailed field information.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            MethodArgumentNotValidException ex, HttpServletRequest request) {

        String correlationId = getSafeCorrelationId(request);

        Map<String, Object> details = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        fieldError -> fieldError.getDefaultMessage() != null ? fieldError.getDefaultMessage() : "Invalid value",
                        (existing, replacement) -> existing
                ));

        log.debug("Method argument validation failed: {} fields [correlation_id={}]", details.size(), correlationId);

        ValidationException validationEx = new ValidationException("Request validation failed");
        ErrorResponse error = ErrorResponse.withDetails(validationEx, correlationId, details);

        return ResponseEntity.badRequest().body(error);
    }

    /**
     * Handles Spring's ResponseStatusException.
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatusException(
            ResponseStatusException ex, HttpServletRequest request) {

        String correlationId = getSafeCorrelationId(request);

        log.debug("Response status exception: {} [correlation_id={}]", ex.getMessage(), correlationId);

        String safeMessage = ex.getReason() != null && isSafeMessage(ex.getReason()) 
            ? ex.getReason() 
            : "Request processing failed";

        ErrorResponse error = ErrorResponse.of("RESPONSE_STATUS_ERROR", safeMessage, correlationId);

        return ResponseEntity.status(ex.getStatusCode()).body(error);
    }

    /**
     * Handles method argument type mismatch (e.g., invalid UUID format).
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {

        String correlationId = getSafeCorrelationId(request);

        log.debug("Method argument type mismatch: {} [correlation_id={}]", ex.getMessage(), correlationId);

        String parameterName = ex.getName() != null ? MessageSanitizer.sanitizeMessage(ex.getName()) : "parameter";
        String expectedType = ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "valid type";
        
        String message = String.format("Invalid parameter '%s': expected %s", parameterName, expectedType);

        ErrorResponse error = ErrorResponse.of("INVALID_PARAMETER", message, correlationId);

        return ResponseEntity.badRequest().body(error);
    }

    /**
     * Handles missing request parameters.
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParams(
            MissingServletRequestParameterException ex, HttpServletRequest request) {

        String correlationId = getSafeCorrelationId(request);

        log.debug("Missing parameter: {} [correlation_id={}]", ex.getMessage(), correlationId);

        String parameterName = ex.getParameterName() != null 
            ? MessageSanitizer.sanitizeMessage(ex.getParameterName()) 
            : "unknown";
        
        String message = String.format("Missing required parameter: %s", parameterName);
        ErrorResponse error = ErrorResponse.of("MISSING_PARAMETER", message, correlationId);

        return ResponseEntity.badRequest().body(error);
    }

    /**
     * Handles optimistic locking failures from JPA.
     */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLockingFailure(
            ObjectOptimisticLockingFailureException ex, HttpServletRequest request) {
        
        String correlationId = getSafeCorrelationId(request);
        
        log.warn("Optimistic locking failure: {} [correlation_id={}]", ex.getMessage(), correlationId);
        
        OptimisticLockException optimisticEx = new OptimisticLockException("resource", ex);
        ErrorResponse error = ErrorResponse.of(optimisticEx, correlationId);
        
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }
    
    /**
     * Handles data integrity violations from database.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(
            DataIntegrityViolationException ex, HttpServletRequest request) {
        
        String correlationId = getSafeCorrelationId(request);
        
        log.warn("Data integrity violation: {} [correlation_id={}]", ex.getMessage(), correlationId);
        
        DataIntegrityException dataEx = new DataIntegrityException("Data integrity constraint violated", ex);
        ErrorResponse error = ErrorResponse.of(dataEx, correlationId);
        
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }
    
    /**
     * Handles multipart file size exceeded exceptions.
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUploadSizeExceeded(
            MaxUploadSizeExceededException ex, HttpServletRequest request) {
        
        String correlationId = getSafeCorrelationId(request);
        
        log.warn("Upload size exceeded: {} [correlation_id={}]", ex.getMessage(), correlationId);
        
        ContentTooLargeException contentEx = new ContentTooLargeException("Upload exceeds maximum allowed size");
        ErrorResponse error = ErrorResponse.of(contentEx, correlationId);
        
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(error);
    }

    /**
     * Handles authentication required exceptions.
     */
    @ExceptionHandler(AuthenticationRequiredException.class)
    public ResponseEntity<ErrorResponse> handleAuthenticationRequired(
            AuthenticationRequiredException ex, HttpServletRequest request) {

        String correlationId = getSafeCorrelationId(request);

        log.warn("Authentication required: {} [correlation_id={}]", ex.getMessage(), correlationId);

        ErrorResponse error = ErrorResponse.of(ex, correlationId);
        return ResponseEntity.status(ex.getHttpStatus()).body(error);
    }

    /**
     * Handles session expired exceptions.
     */
    @ExceptionHandler(SessionExpiredException.class)
    public ResponseEntity<ErrorResponse> handleSessionExpired(
            SessionExpiredException ex, HttpServletRequest request) {

        String correlationId = getSafeCorrelationId(request);

        log.warn("Session expired: {} [correlation_id={}]", ex.getMessage(), correlationId);

        ErrorResponse error = ErrorResponse.of(ex, correlationId);
        return ResponseEntity.status(ex.getHttpStatus()).body(error);
    }

    /**
     * Handles insufficient permission exceptions (different from complete access denial).
     */
    @ExceptionHandler(InsufficientPermissionException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientPermission(
            InsufficientPermissionException ex, HttpServletRequest request) {

        String correlationId = getSafeCorrelationId(request);

        log.warn("Insufficient permission: {} [correlation_id={}]", ex.getMessage(), correlationId);

        ErrorResponse error = ErrorResponse.of(ex, correlationId);
        return ResponseEntity.status(ex.getHttpStatus()).body(error);
    }

    /**
     * Handles resource state exceptions.
     */
    @ExceptionHandler(ResourceStateException.class)
    public ResponseEntity<ErrorResponse> handleResourceState(
            ResourceStateException ex, HttpServletRequest request) {

        String correlationId = getSafeCorrelationId(request);

        log.warn("Resource state error: {} [correlation_id={}]", ex.getMessage(), correlationId);

        ErrorResponse error = ErrorResponse.of(ex, correlationId);
        return ResponseEntity.status(ex.getHttpStatus()).body(error);
    }

    /**
     * Handles rate limit exceeded exceptions.
     */
    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ErrorResponse> handleRateLimitExceeded(
            RateLimitExceededException ex, HttpServletRequest request) {

        String correlationId = getSafeCorrelationId(request);

        log.warn("Rate limit exceeded: {} [correlation_id={}]", ex.getMessage(), correlationId);

        ErrorResponse error = ErrorResponse.of(ex, correlationId);
        
        // Add Retry-After header for rate limiting
        return ResponseEntity.status(ex.getHttpStatus())
                .header("Retry-After", "60")
                .body(error);
    }

    /**
     * Handles quota exceeded exceptions.
     */
    @ExceptionHandler(QuotaExceededException.class)
    public ResponseEntity<ErrorResponse> handleQuotaExceeded(
            QuotaExceededException ex, HttpServletRequest request) {

        String correlationId = getSafeCorrelationId(request);

        log.warn("Quota exceeded: {} [correlation_id={}]", ex.getMessage(), correlationId);

        ErrorResponse error = ErrorResponse.of(ex, correlationId);
        return ResponseEntity.status(ex.getHttpStatus()).body(error);
    }

    /**
     * Handles dependency exceptions (e.g., cannot delete due to dependent resources).
     */
    @ExceptionHandler(DependencyException.class)
    public ResponseEntity<ErrorResponse> handleDependency(
            DependencyException ex, HttpServletRequest request) {

        String correlationId = getSafeCorrelationId(request);

        log.warn("Dependency constraint: {} [correlation_id={}]", ex.getMessage(), correlationId);

        ErrorResponse error = ErrorResponse.of(ex, correlationId);
        return ResponseEntity.status(ex.getHttpStatus()).body(error);
    }

    /**
     * Handles unsupported format exceptions.
     */
    @ExceptionHandler(UnsupportedFormatException.class)
    public ResponseEntity<ErrorResponse> handleUnsupportedFormat(
            UnsupportedFormatException ex, HttpServletRequest request) {

        String correlationId = getSafeCorrelationId(request);

        log.warn("Unsupported format: {} [correlation_id={}]", ex.getMessage(), correlationId);

        ErrorResponse error = ErrorResponse.of(ex, correlationId);
        return ResponseEntity.status(ex.getHttpStatus()).body(error);
    }

    /**
     * Handles content too large exceptions.
     */
    @ExceptionHandler(ContentTooLargeException.class)
    public ResponseEntity<ErrorResponse> handleContentTooLarge(
            ContentTooLargeException ex, HttpServletRequest request) {

        String correlationId = getSafeCorrelationId(request);

        log.warn("Content too large: {} [correlation_id={}]", ex.getMessage(), correlationId);

        ErrorResponse error = ErrorResponse.of(ex, correlationId);
        return ResponseEntity.status(ex.getHttpStatus()).body(error);
    }

    /**
     * Handles service unavailable exceptions.
     */
    @ExceptionHandler(ServiceUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleServiceUnavailable(
            ServiceUnavailableException ex, HttpServletRequest request) {

        String correlationId = getSafeCorrelationId(request);

        log.error("Service unavailable: {} [correlation_id={}]", ex.getMessage(), correlationId);

        ErrorResponse error = ErrorResponse.of(ex, correlationId);
        
        // Add Retry-After header for service unavailable
        return ResponseEntity.status(ex.getHttpStatus())
                .header("Retry-After", "300")
                .body(error);
    }

    /**
     * Handles configuration exceptions.
     */
    @ExceptionHandler(ConfigurationException.class)
    public ResponseEntity<ErrorResponse> handleConfiguration(
            ConfigurationException ex, HttpServletRequest request) {

        String correlationId = getSafeCorrelationId(request);

        log.error("Configuration error: {} [correlation_id={}]", ex.getMessage(), correlationId);

        ErrorResponse error = ErrorResponse.of(ex, correlationId);
        return ResponseEntity.status(ex.getHttpStatus()).body(error);
    }

    /**
     * Handles IllegalArgumentException - these should be replaced with specific domain exceptions.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(
            IllegalArgumentException ex, HttpServletRequest request) {

        String correlationId = getSafeCorrelationId(request);

        log.warn("IllegalArgumentException caught - should be replaced with domain exception: {} [correlation_id={}]",
                ex.getMessage(), correlationId);

        ErrorResponse error = ErrorResponse.of("INVALID_ARGUMENT", 
                "Invalid argument provided", correlationId);

        return ResponseEntity.badRequest().body(error);
    }

    /**
     * Handles IllegalStateException - these should be replaced with specific domain exceptions.
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalStateException(
            IllegalStateException ex, HttpServletRequest request) {

        String correlationId = getSafeCorrelationId(request);

        log.warn("IllegalStateException caught - should be replaced with domain exception: {} [correlation_id={}]",
                ex.getMessage(), correlationId);

        ErrorResponse error = ErrorResponse.of("INVALID_STATE", 
                "Invalid operation for current state", correlationId);

        return ResponseEntity.badRequest().body(error);
    }

    /**
     * Handles all unexpected exceptions with proper logging and correlation tracking.
     * This is the catch-all handler and should be the last one.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex, HttpServletRequest request) {
        String correlationId = getSafeCorrelationId(request);

        log.error("Unexpected error occurred [correlation_id={}]", correlationId, ex);

        ErrorResponse error = ErrorResponse.safe("INTERNAL_SERVER_ERROR", correlationId);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }

    /**
     * Extracts and sanitizes correlation ID from request headers.
     * This method ensures the correlation ID is always safe for use in responses.
     */
    private String getSafeCorrelationId(HttpServletRequest request) {
        // Try X-Correlation-ID header first
        String correlationId = request.getHeader("X-Correlation-ID");
        String sanitized = MessageSanitizer.sanitizeCorrelationId(correlationId);
        
        // If sanitization failed (returned UUID), try X-Request-ID
        if (correlationId != null && !correlationId.equals(sanitized) && sanitized.contains("-")) {
            correlationId = request.getHeader("X-Request-ID");
            sanitized = MessageSanitizer.sanitizeCorrelationId(correlationId);
        }
        
        return sanitized;
    }
    
    /**
     * Checks if a message is safe to expose to users.
     */
    private boolean isSafeMessage(String message) {
        if (message == null || message.length() > 200) {
            return false;
        }
        
        String lowerMessage = message.toLowerCase();
        return !lowerMessage.contains("<script") && 
               !lowerMessage.contains("javascript:") && 
               !lowerMessage.contains("onclick") &&
               !lowerMessage.contains("onerror") &&
               !lowerMessage.contains("onload");
    }
}