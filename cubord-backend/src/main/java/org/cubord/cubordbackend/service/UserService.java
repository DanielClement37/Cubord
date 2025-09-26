package org.cubord.cubordbackend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cubord.cubordbackend.domain.User;
import org.cubord.cubordbackend.dto.user.UserResponse;
import org.cubord.cubordbackend.dto.user.UserUpdateRequest;
import org.cubord.cubordbackend.exception.AuthenticationRequiredException;
import org.cubord.cubordbackend.exception.ConflictException;
import org.cubord.cubordbackend.exception.DataIntegrityException;
import org.cubord.cubordbackend.exception.InsufficientPermissionException;
import org.cubord.cubordbackend.exception.NotFoundException;
import org.cubord.cubordbackend.exception.TokenExpiredException;
import org.cubord.cubordbackend.exception.ValidationException;
import org.cubord.cubordbackend.repository.HouseholdMemberRepository;
import org.cubord.cubordbackend.repository.UserRepository;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.UUID;
import java.util.Map;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {
    private final UserRepository userRepository;
    private final HouseholdMemberRepository householdMemberRepository;
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,6}$");

    /**
     * Retrieves the current user from the database or creates a new user if not found.
     * 
     * @param token JWT authentication token containing user information
     * @return User entity corresponding to the authenticated user
     * @throws AuthenticationRequiredException if the JWT token doesn't contain a subject claim
     * @throws TokenExpiredException if the JWT token structure is invalid or corrupted
     */
    @Transactional
    public User getCurrentUser(JwtAuthenticationToken token) {
        if (token == null || token.getToken() == null) {
            throw new AuthenticationRequiredException("JWT token is required");
        }
        
        String subject = token.getToken().getSubject();
        if (subject == null) {
            throw new AuthenticationRequiredException("JWT token does not contain a subject claim");
        }
        
        try {
            UUID userId = UUID.fromString(subject);
            return userRepository.findById(userId)
                    .orElseGet(() -> createUser(token));
        } catch (IllegalArgumentException e) {
            log.error("Invalid UUID format in JWT subject: {}", subject, e);
            throw new TokenExpiredException("Invalid token format: subject is not a valid UUID");
        }
    }

    /**
     * Retrieves current user details as a DTO.
     * 
     * @param token JWT authentication token containing user information
     * @return UserResponse containing the authenticated user's details
     */
    @Transactional(readOnly = true)
    public UserResponse getCurrentUserDetails(JwtAuthenticationToken token) {
        User user = getCurrentUser(token);
        return mapToResponse(user);
    }

    /**
     * Retrieves a user by their ID.
     * 
     * @param id UUID of the user to retrieve
     * @return UserResponse containing the user's details
     * @throws ValidationException if the provided ID is null
     * @throws NotFoundException if no user with the given ID exists
     */
    @Transactional(readOnly = true)
    public UserResponse getUser(UUID id) {
        if (id == null) {
            throw new ValidationException("User ID cannot be null");
        }
        
        User user = userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("User", id));
        return mapToResponse(user);
    }

    /**
     * Retrieves a user by their username.
     * 
     * @param username Username of the user to retrieve
     * @return UserResponse containing the user's details
     * @throws ValidationException if the provided username is null or blank
     * @throws NotFoundException if no user with the given username exists
     */
    @Transactional(readOnly = true)
    public UserResponse getUserByUsername(String username) {
        if (username == null || username.trim().isEmpty()) {
            throw new ValidationException("Username cannot be null or blank");
        }
        
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new NotFoundException("User with username '" + username + "' not found"));
        return mapToResponse(user);
    }

    /**
     * Creates a new user from JWT token information.
     * 
     * @param token JWT authentication token containing user information
     * @return Newly created and persisted User entity
     * @throws DataIntegrityException if user creation fails due to data constraints
     */
    private User createUser(JwtAuthenticationToken token) {
        try {
            User user = new User();
            user.setId(UUID.fromString(token.getToken().getSubject()));
            user.setEmail(token.getToken().getClaimAsString("email"));
            user.setUsername(extractUsernameFromEmail(token.getToken().getClaimAsString("email")));
            user.setDisplayName(token.getToken().getClaimAsString("name"));
            user.setHouseholdMembers(new HashSet<>());
            
            log.info("Creating new user with ID: {} and email: {}", user.getId(), user.getEmail());
            return userRepository.save(user);
        } catch (Exception e) {
            log.error("Failed to create user from JWT token", e);
            throw new DataIntegrityException("Failed to create user: " + e.getMessage(), e);
        }
    }

    /**
     * Extracts a username from an email address by taking the part before the @ symbol.
     * 
     * @param email Email address to extract username from
     * @return Username extracted from email, or null if email is null
     * @throws ValidationException if an email format is invalid
     */
    private String extractUsernameFromEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }

        if (!email.contains("@")) {
            throw new ValidationException("Invalid email format: missing @ symbol");
        }

        String username = email.split("@")[0].trim();
        
        // Handle empty username part
        if (username.isEmpty()) {
            throw new ValidationException("Invalid email format: empty username part");
        }
        
        // Optional: sanitize username (remove special characters, etc.)
        // username = username.replaceAll("[^a-zA-Z0-9_-]", "");
        
        return username;
    }

    /**
     * Maps a User entity to a UserResponse DTO.
     * 
     * @param user User entity to map
     * @return UserResponse containing the user's details
     */
    private UserResponse mapToResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .displayName(user.getDisplayName())
                .createdAt(user.getCreatedAt())
                .build();
    }

    /**
     * Validates an email format
     * 
     * @param email Email to validate
     * @throws ValidationException if an email format is invalid
     */
    private void validateEmail(String email) {
        if (email == null || !EMAIL_PATTERN.matcher(email).matches()) {
            throw new ValidationException("Invalid email format: must be a valid email address");
        }
    }

    /**
     * Checks if an email is already in use by another user
     * 
     * @param newEmail Email to check
     * @param userId ID of the current user (to exclude from the check)
     * @throws ConflictException if email is already in use
     */
    private void checkEmailAlreadyInUse(String newEmail, UUID userId) {
        userRepository.findByEmail(newEmail)
                .ifPresent(existingUser -> {
                    if (!existingUser.getId().equals(userId)) {
                        throw new ConflictException("Email address is already in use by another user");
                    }
                });
    }

    /**
     * Updates user email if it has changed
     * 
     * @param user User to update
     * @param newEmail New email value
     * @throws ConflictException if email is already in use
     * @throws ValidationException if an email format is invalid
     */
    private void updateEmailIfChanged(User user, String newEmail) {
        String currentEmail = user.getEmail();
        // Only check for email conflicts if the email is actually changing
        if (newEmail != null && !newEmail.equals(currentEmail)) {
            validateEmail(newEmail);
            checkEmailAlreadyInUse(newEmail, user.getId());
            
            // TODO: Add confirmation logic for updating the email and figure out how to change it with supabase
            // For now, we'll just update the local record
            user.setEmail(newEmail);
            log.info("Updated email for user {} from {} to {}", user.getId(), currentEmail, newEmail);
        }
    }

    /**
     * Updates a user with the provided information.
     * 
     * @param id UUID of the user to update
     * @param updateRequest DTO containing the updated user information
     * @return UserResponse containing the updated user's details
     * @throws ValidationException if the provided data is invalid
     * @throws NotFoundException if no user with the given ID exists
     * @throws ConflictException if the email is already in use by another user
     */
    @Transactional
    public UserResponse updateUser(UUID id, UserUpdateRequest updateRequest) {
        if (id == null) {
            throw new ValidationException("User ID cannot be null");
        }
        if (updateRequest == null) {
            throw new ValidationException("Update request cannot be null");
        }
        
        User user = userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("User", id));
        
        if (updateRequest.getDisplayName() != null) {
            String displayName = updateRequest.getDisplayName().trim();
            if (displayName.length() < 2 || displayName.length() > 50) {
                throw new ValidationException("Display name must be between 2 and 50 characters");
            }
            user.setDisplayName(displayName);
        }
        
        updateEmailIfChanged(user, updateRequest.getEmail());
        
        // Note: Username updates are not supported to maintain consistency with the authentication provider
        
        try {
            User updatedUser = userRepository.save(user);
            log.info("Updated user with ID: {}", updatedUser.getId());
            return mapToResponse(updatedUser);
        } catch (Exception e) {
            log.error("Failed to update user with ID: {}", id, e);
            throw new DataIntegrityException("Failed to update user: " + e.getMessage(), e);
        }
    }

    /**
     * Partially updates a user with the provided field values.
     * 
     * @param id UUID of the user to update
     * @param patchData Map containing field names and their new values
     * @return UserResponse containing the updated user's details
     * @throws ValidationException if invalid field values are provided or patch data is null/empty
     * @throws NotFoundException if no user with the given ID exists
     * @throws ConflictException if the email is already in use by another user
     */
    @Transactional
    public UserResponse patchUser(UUID id, Map<String, Object> patchData) {
        // Validate input
        if (id == null) {
            throw new ValidationException("User ID cannot be null");
        }
        if (patchData == null || patchData.isEmpty()) {
            throw new ValidationException("Patch data cannot be null or empty");
        }
        
        // Find user by ID
        User user = userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("User", id));
        
        // Process each field in the patch data
        for (Map.Entry<String, Object> entry : patchData.entrySet()) {
            String field = entry.getKey();
            Object value = entry.getValue();
            
            switch (field) {
                case "displayName":
                    if (value != null) {
                        if (!(value instanceof String)) {
                            throw new ValidationException("Display name must be a string");
                        }
                        String displayName = ((String) value).trim();
                        if (displayName.length() < 2 || displayName.length() > 50) {
                            throw new ValidationException("Display name must be between 2 and 50 characters");
                        }
                        user.setDisplayName(displayName);
                    }
                    break;
                
                case "email":
                    if (value != null) {
                        if (!(value instanceof String)) {
                            throw new ValidationException("Email must be a string");
                        }
                        updateEmailIfChanged(user, (String) value);
                    }
                    break;
                
                // Username updates are not supported to maintain consistency with the authentication provider
                case "username":
                    // Ignore username changes silently for backward compatibility
                    log.warn("Attempted to update username for user {}, operation ignored", id);
                    break;
                
                default:
                    // Ignore unknown fields but log for debugging
                    log.debug("Ignoring unknown field '{}' in patch request for user {}", field, id);
                    break;
            }
        }
        
        try {
            User updatedUser = userRepository.save(user);
            log.info("Patched user with ID: {}", updatedUser.getId());
            return mapToResponse(updatedUser);
        } catch (Exception e) {
            log.error("Failed to patch user with ID: {}", id, e);
            throw new DataIntegrityException("Failed to update user: " + e.getMessage(), e);
        }
    }

    /**
     * Deletes a user by their ID.
     *
     * @param id UUID of the user to delete
     * @throws ValidationException if the provided ID is null
     * @throws NotFoundException if no user with the given ID exists
     * @throws DataIntegrityException if deletion fails due to data constraints
     */
    @Transactional
    public void deleteUser(UUID id) {
        if (id == null) {
            throw new ValidationException("User ID cannot be null");
        }
        
        // Verify the user exists
        User user = userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("User", id));
        
        try {
            // Perform the deletion
            userRepository.delete(user);
            log.info("Deleted user with ID: {}", id);
        } catch (Exception e) {
            log.error("Failed to delete user with ID: {}", id, e);
            throw new DataIntegrityException("Failed to delete user: user may have dependent records", e);
        }
    }

    /**
     * Deletes a user by their ID with authorization check.
     *
     * @param id UUID of the user to delete
     * @param currentUserId UUID of the user requesting the deletion
     * @throws NotFoundException if no user with the given ID exists
     * @throws InsufficientPermissionException if the current user is not authorized to delete the target user
     * @throws DataIntegrityException if deletion fails due to data constraints
     */
    @Transactional
    public void deleteUser(UUID id, UUID currentUserId) {
        if (id == null) {
            throw new ValidationException("User ID cannot be null");
        }
        if (currentUserId == null) {
            throw new ValidationException("Current user ID cannot be null");
        }
        
        // Authorization checks first
        if (!id.equals(currentUserId)) {
            throw new InsufficientPermissionException("delete", "user account");
        }
        
        deleteUser(id);
    }
}