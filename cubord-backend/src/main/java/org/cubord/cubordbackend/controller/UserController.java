package org.cubord.cubordbackend.controller;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.cubord.cubordbackend.dto.UserResponse;
import org.cubord.cubordbackend.exception.NotFoundException;
import org.cubord.cubordbackend.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Validated
public class UserController {
    private static final Logger logger = LoggerFactory.getLogger(UserController.class);

    private final UserService userService;
    
    /**
     * Retrieves the currently authenticated user's details.
     * 
     * @param authentication Current authentication object
     * @return ResponseEntity with the user details
     * @throws ResponseStatusException with status 401 if not authenticated
     */
    @GetMapping("/me")
    public ResponseEntity<UserResponse> getCurrentUser(Authentication authentication) {
        logger.debug("Getting current user details with authentication: {}", 
            authentication != null ? authentication.getClass().getSimpleName() : "null");
            
        if (authentication == null) {
            logger.warn("Authentication is null");
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        
        if (!(authentication instanceof JwtAuthenticationToken token)) {
            logger.warn("Authentication is not JwtAuthenticationToken: {}", authentication.getClass().getName());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Valid JWT authentication required");
        }
        
        // Explicitly check token expiration
        Instant expiration = token.getToken().getExpiresAt();
        if (expiration == null || expiration.isBefore(Instant.now())) {
            logger.warn("JWT token has expired. Expiration: {}, Now: {}", expiration, Instant.now());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "JWT token has expired");
        }
        
        try {
            UserResponse user = userService.getCurrentUserDetails(token);
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.maxAge(5, TimeUnit.MINUTES))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(user);
        } catch (JwtValidationException e) {
            logger.warn("JWT validation failed: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired token", e);
        } catch (Exception e) {
            logger.error("Error retrieving user details", e);
            throw new ResponseStatusException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Error retrieving user details",
                e
            );
        }
    }
    
    /**
     * Retrieves a user by their ID.
     * 
     * @param id UUID of the user to retrieve
     * @param principal Current authenticated principal
     * @return ResponseEntity with the user details
     * @throws ResponseStatusException with status 403 if attempting to access another user's data
     * @throws ResponseStatusException with status 404 if user not found
     */
    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> getUser(@PathVariable @NotNull UUID id, Principal principal) {
        // Check if user is trying to access their own data
        if (principal instanceof JwtAuthenticationToken token) {
            // Explicitly check token expiration
            Instant expiration = token.getToken().getExpiresAt();
            if (expiration == null || expiration.isBefore(Instant.now())) {
                logger.warn("JWT token has expired. Expiration: {}, Now: {}", expiration, Instant.now());
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "JWT token has expired");
            }
            
            String userIdFromToken = token.getToken().getSubject();
            if (!id.toString().equals(userIdFromToken)) {
                logger.warn("Access denied: User {} attempted to access data for user {}", 
                    userIdFromToken, id);
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot access another user's data");
            }
        } else {
            logger.warn("Unauthorized access: Principal is not a JwtAuthenticationToken");
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Valid authentication required");
        }
        
        try {
            UserResponse user = userService.getUser(id);
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.maxAge(5, TimeUnit.MINUTES))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(user);
        } catch (NotFoundException e) {
            logger.info("User not found: {}", id);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
        } catch (Exception e) {
            logger.error("Error retrieving user {}", id, e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error retrieving user", e);
        }
    }
    
    /**
     * Retrieves a user by their username.
     * 
     * @param username Username of the user to retrieve
     * @param principal Current authenticated principal
     * @return ResponseEntity with the user details
     * @throws ResponseStatusException with status 404 if user not found
     */
    @GetMapping("/username/{username}")
    public ResponseEntity<UserResponse> getUserByUsername(
            @PathVariable @NotBlank String username,
            Principal principal) {
        
        try {
            // Check token expiration if it's a JWT token
            if (principal instanceof JwtAuthenticationToken token) {
                Instant expiration = token.getToken().getExpiresAt();
                if (expiration == null || expiration.isBefore(Instant.now())) {
                    logger.warn("JWT token has expired. Expiration: {}, Now: {}", expiration, Instant.now());
                    throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "JWT token has expired");
                }
            }
        
            // URL decode the username to handle special characters
            String decodedUsername = URLDecoder.decode(username, StandardCharsets.UTF_8);
            logger.debug("Looking up user by username: {}", decodedUsername);
            
            UserResponse user = userService.getUserByUsername(decodedUsername);
            
            // Check if user is trying to access their own data
            if (principal instanceof JwtAuthenticationToken token) {
                String userIdFromToken = token.getToken().getSubject();
                if (!user.getId().toString().equals(userIdFromToken)) {
                    logger.warn("Access denied: User {} attempted to access data for username {}", 
                        userIdFromToken, decodedUsername);
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot access another user's data");
                }
            } else {
                logger.warn("Unauthorized access: Principal is not a JwtAuthenticationToken");
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Valid authentication required");
            }
            
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.maxAge(5, TimeUnit.MINUTES))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(user);
        } catch (NotFoundException e) {
            logger.info("User not found by username: {}", username);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
        } catch (ResponseStatusException e) {
            throw e; // Re-throw if it's already a ResponseStatusException
        } catch (Exception e) {
            logger.error("Error retrieving user by username {}", username, e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error retrieving user", e);
        }
    }
    
    /**
     * Exception handler for validation errors.
     * 
     * @param e The exception thrown
     * @return ResponseEntity with error details
     */
    @ExceptionHandler(jakarta.validation.ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleValidationExceptions(jakarta.validation.ConstraintViolationException e) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ErrorResponse("Validation error: " + e.getMessage()));
    }
    
    /**
     * Exception handler for controller-specific exceptions.
     * 
     * @param e The exception thrown
     * @return ResponseEntity with error details
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatusException(ResponseStatusException e) {
        return ResponseEntity
                .status(e.getStatusCode())
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ErrorResponse(e.getReason()));
    }
    
    /**
     * Simple error response class.
     */
    @Getter
    public static class ErrorResponse {
        private final String message;
        
        public ErrorResponse(String message) {
            this.message = message;
        }

    }
}