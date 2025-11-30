package org.cubord.cubordbackend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cubord.cubordbackend.domain.User;
import org.cubord.cubordbackend.dto.user.UserResponse;
import org.cubord.cubordbackend.dto.user.UserUpdateRequest;
import org.cubord.cubordbackend.exception.*;
import org.cubord.cubordbackend.repository.UserRepository;
import org.cubord.cubordbackend.security.SecurityService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Service class for managing user-related operations.
 * 
 * <p>This service follows the modernized security architecture where:</p>
 * <ul>
 *   <li>Authentication is handled by Spring Security filters</li>
 *   <li>Authorization is declarative via @PreAuthorize annotations</li>
 *   <li>SecurityService provides business-level security context access</li>
 *   <li>No manual token validation or permission checks in business logic</li>
 * </ul>
 * 
 * <h2>Authorization Rules</h2>
 * <ul>
 *   <li><strong>Read:</strong> Users can view their own profile or profiles of users in shared households</li>
 *   <li><strong>Update:</strong> Users can only modify their own profile</li>
 *   <li><strong>Delete:</strong> Users can only delete their own account</li>
 * </ul>
 *
 * @see SecurityService
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final SecurityService securityService;
    
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,6}$");

    // ==================== Query Operations ====================

    /**
     * Retrieves the current authenticated user's details.
     * 
     * <p>This method leverages SecurityService to get the current user from the security context.
     * If the user doesn't exist in the database (first-time login), SecurityService will create them.</p>
     *
     * @return UserResponse containing the authenticated user's details
     * @throws AuthenticationRequiredException if no authenticated user exists
     */
    @Transactional(readOnly = true)
    public UserResponse getCurrentUserDetails() {
        log.debug("Fetching current user details from security context");
        User currentUser = securityService.getCurrentUser();
        return mapToResponse(currentUser);
    }

    /**
     * Retrieves the current authenticated user's details from JWT token.
     * 
     * @deprecated Use {@link #getCurrentUserDetails()} instead.
     *             This method is maintained for backward compatibility with controllers
     *             and services that haven't been migrated to the new security architecture.
     *             Token-based authentication is now handled by Spring Security filters,
     *             and user resolution should be done through SecurityService without explicit tokens.
     * 
     * @param token JWT authentication token containing user information (ignored in favor of SecurityContext)
     * @return UserResponse containing the authenticated user's details
     * @throws AuthenticationRequiredException if no authenticated user exists
     */
    @Deprecated(since = "2.0", forRemoval = true)
    @Transactional(readOnly = true)
    public UserResponse getCurrentUserDetails(org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken token) {
        log.warn("DEPRECATED: getCurrentUserDetails(JwtAuthenticationToken) called. " +
                "Migrate to getCurrentUserDetails() for improved security architecture. " +
                "The token parameter is ignored - using SecurityContext instead.");
        
        // Delegate to the modern method which uses SecurityService
        return getCurrentUserDetails();
    }

    /**
     * Retrieves the current authenticated user from the JWT token.
     * 
     * @deprecated Use {@link SecurityService#getCurrentUser()} instead.
     *             This method is maintained for backward compatibility with services
     *             that haven't been migrated to the new security architecture.
     *             Token-based authentication is now handled by Spring Security filters,
     *             and user resolution should be done through SecurityService.
     * 
     * @param token JWT authentication token containing user information (ignored in favor of SecurityContext)
     * @return User entity corresponding to the authenticated user
     * @throws AuthenticationRequiredException if the JWT token is invalid or missing
     */
    @Deprecated(since = "2.0", forRemoval = true)
    @Transactional(readOnly = true)
    public User getCurrentUser(org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken token) {
        log.warn("DEPRECATED: getCurrentUser(JwtAuthenticationToken) called. " +
                "Migrate to SecurityService.getCurrentUser() for improved security architecture. " +
                "The token parameter is ignored - using SecurityContext instead.");
        
        // Delegate to SecurityService which handles the same logic
        return securityService.getCurrentUser();
    }

    /**
     * Retrieves a user by their ID.
     * 
     * <p>Authorization: User must be able to access the target user's profile
     * (own profile or shared household member).</p>
     *
     * @param id UUID of the user to retrieve
     * @return UserResponse containing the user's details
     * @throws ValidationException if the provided ID is null
     * @throws NotFoundException if no user with the given ID exists
     * @throws InsufficientPermissionException if the current user cannot access the profile
     */
    @Transactional(readOnly = true)
    @PreAuthorize("@security.canAccessUserProfile(#id)")
    public UserResponse getUser(UUID id) {
        if (id == null) {
            throw new ValidationException("User ID cannot be null");
        }

        log.debug("Fetching user with ID: {}", id);
        
        User user = userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("User not found with ID: " + id));
        
        return mapToResponse(user);
    }

    /**
     * Retrieves a user by their username.
     * 
     * <p>Authorization: User must be able to access the target user's profile
     * (own profile or shared household member). Authorization is checked after finding the user.</p>
     *
     * @param username Username of the user to retrieve
     * @return UserResponse containing the user's details
     * @throws ValidationException if the provided username is null or blank
     * @throws NotFoundException if no user with the given username exists
     * @throws InsufficientPermissionException if the current user cannot access the profile
     */
    @Transactional(readOnly = true)
    public UserResponse getUserByUsername(String username) {
        if (username == null || username.isBlank()) {
            throw new ValidationException("Username cannot be null or blank");
        }

        log.debug("Fetching user with username: {}", username);
        
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new NotFoundException("User not found with username: " + username));
        
        // Authorization check after finding a user (since we need the ID)
        if (!securityService.canAccessUserProfile(user.getId())) {
            log.warn("User {} attempted to access unauthorized profile: {}", 
                    securityService.getCurrentUserId(), user.getId());
            throw new InsufficientPermissionException("You do not have permission to access this user profile");
        }
        
        return mapToResponse(user);
    }

    // ==================== Update Operations ====================

    /**
     * Updates a user with the provided information.
     * 
     * <p>Authorization: Users can only update their own profile.</p>
     *
     * @param id UUID of the user to update
     * @param updateRequest DTO containing the updated user information
     * @return UserResponse containing the updated user's details
     * @throws ValidationException if the provided data is invalid
     * @throws NotFoundException if no user with the given ID exists
     * @throws ConflictException if the email is already in use by another user
     * @throws InsufficientPermissionException if attempting to modify another user's profile
     */
    @Transactional
    @PreAuthorize("@security.canModifyUserProfile(#id)")
    public UserResponse updateUser(UUID id, UserUpdateRequest updateRequest) {
        if (id == null) {
            throw new ValidationException("User ID cannot be null");
        }
        if (updateRequest == null) {
            throw new ValidationException("Update request cannot be null");
        }

        UUID currentUserId = securityService.getCurrentUserId();
        log.debug("User {} updating user profile: {}", currentUserId, id);

        User user = userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("User not found with ID: " + id));

        // Update fields
        if (updateRequest.getDisplayName() != null && !updateRequest.getDisplayName().isBlank()) {
            user.setDisplayName(updateRequest.getDisplayName());
            log.debug("Updated display name for user {}", id);
        }

        if (updateRequest.getEmail() != null && !updateRequest.getEmail().isBlank()) {
            updateEmailIfChanged(user, updateRequest.getEmail());
        }

        if (updateRequest.getUsername() != null && !updateRequest.getUsername().isBlank()) {
            if (!updateRequest.getUsername().equals(user.getUsername())) {
                if (userRepository.findByUsername(updateRequest.getUsername()).isPresent()) {
                    throw new ConflictException("Username '" + updateRequest.getUsername() + "' is already in use");
                }
                user.setUsername(updateRequest.getUsername());
                log.debug("Updated username for user {}", id);
            }
        }

        User savedUser = userRepository.save(user);
        log.info("User {} successfully updated profile: {}", currentUserId, id);
        
        return mapToResponse(savedUser);
    }

    /**
     * Partially updates a user with the provided field values.
     * 
     * <p>Authorization: Users can only patch their own profile.</p>
     * 
     * <p>Supported fields: displayName, email, username</p>
     *
     * @param id UUID of the user to update
     * @param patchData Map containing field names and their new values
     * @return UserResponse containing the updated user's details
     * @throws ValidationException if invalid field values are provided or patch data is null/empty
     * @throws NotFoundException if no user with the given ID exists
     * @throws ConflictException if the email is already in use by another user
     * @throws InsufficientPermissionException if attempting to modify another user's profile
     */
    @Transactional
    @PreAuthorize("@security.canModifyUserProfile(#id)")
    public UserResponse patchUser(UUID id, Map<String, Object> patchData) {
        if (id == null) {
            throw new ValidationException("User ID cannot be null");
        }
        if (patchData == null || patchData.isEmpty()) {
            throw new ValidationException("Patch data cannot be null or empty");
        }

        UUID currentUserId = securityService.getCurrentUserId();
        log.debug("User {} patching user profile: {} with fields: {}", 
                currentUserId, id, patchData.keySet());

        User user = userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("User not found with ID: " + id));

        // Apply patches
        patchData.forEach((field, value) -> {
            switch (field) {
                case "displayName":
                    if (value != null && !value.toString().isBlank()) {
                        user.setDisplayName(value.toString());
                        log.debug("Patched displayName for user {}", id);
                    }
                    break;
                    
                case "email":
                    if (value != null && !value.toString().isBlank()) {
                        updateEmailIfChanged(user, value.toString());
                    }
                    break;
                    
                case "username":
                    if (value != null && !value.toString().isBlank()) {
                        String newUsername = value.toString();
                        if (!newUsername.equals(user.getUsername())) {
                            if (userRepository.findByUsername(newUsername).isPresent()) {
                                throw new ConflictException("Username '" + newUsername + "' is already in use");
                            }
                            user.setUsername(newUsername);
                            log.debug("Patched username for user {}", id);
                        }
                    }
                    break;
                    
                default:
                    log.warn("Attempted to patch unsupported field: {}", field);
                    throw new ValidationException("Unsupported field for patching: " + field);
            }
        });

        User savedUser = userRepository.save(user);
        log.info("User {} successfully patched profile: {}", currentUserId, id);
        
        return mapToResponse(savedUser);
    }

    // ==================== Delete Operations ====================

    /**
     * Deletes a user by their ID.
     * 
     * <p>Authorization: Users can only delete their own account.</p>
     *
     * @param id UUID of the user to delete
     * @throws ValidationException if the provided ID is null
     * @throws NotFoundException if no user with the given ID exists
     * @throws DataIntegrityException if deletion fails due to data constraints
     * @throws InsufficientPermissionException if attempting to delete another user's account
     */
    @Transactional
    @PreAuthorize("@security.canModifyUserProfile(#id)")
    public void deleteUser(UUID id) {
        if (id == null) {
            throw new ValidationException("User ID cannot be null");
        }

        UUID currentUserId = securityService.getCurrentUserId();
        log.debug("User {} deleting user: {}", currentUserId, id);

        User user = userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("User not found with ID: " + id));

        try {
            userRepository.delete(user);
            log.info("User {} successfully deleted account: {}", currentUserId, id);
        } catch (Exception e) {
            log.error("Failed to delete user with ID: {}", id, e);
            throw new DataIntegrityException(
                    "Failed to delete user. User may have associated data that must be removed first.", e);
        }
    }

    // ==================== Helper Methods ====================

    /**
     * Extracts a username from an email address by taking the part before the @ symbol.
     *
     * @param email Email address to extract username from
     * @return Username extracted from email, or null if email is null
     * @throws ValidationException if an email format is invalid
     */
    private String extractUsernameFromEmail(String email) {
        if (email == null) {
            return null;
        }
        
        validateEmail(email);
        
        int atIndex = email.indexOf('@');
        return email.substring(0, atIndex);
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
                .email(user.getEmail())
                .displayName(user.getDisplayName())
                .username(user.getUsername())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }

    /**
     * Validates an email format using a regex pattern.
     *
     * @param email Email to validate
     * @throws ValidationException if an email format is invalid
     */
    private void validateEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new ValidationException("Email cannot be null or blank");
        }
        
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            throw new ValidationException("Invalid email format: " + email);
        }
    }

    /**
     * Checks if an email is already in use by another user.
     *
     * @param newEmail Email to check
     * @param userId ID of the current user (to exclude from the check)
     * @throws ConflictException if email is already in use
     */
    private void checkEmailAlreadyInUse(String newEmail, UUID userId) {
        userRepository.findByEmail(newEmail).ifPresent(existingUser -> {
            if (!existingUser.getId().equals(userId)) {
                throw new ConflictException("Email '" + newEmail + "' is already in use by another user");
            }
        });
    }

    /**
     * Updates user email if it has changed, with validation and conflict checking.
     *
     * @param user User to update
     * @param newEmail New email value
     * @throws ConflictException if email is already in use
     * @throws ValidationException if an email format is invalid
     */
    private void updateEmailIfChanged(User user, String newEmail) {
        if (newEmail == null || newEmail.isBlank()) {
            return;
        }
        
        if (newEmail.equals(user.getEmail())) {
            return; // No change needed
        }
        
        validateEmail(newEmail);
        checkEmailAlreadyInUse(newEmail, user.getId());
        
        user.setEmail(newEmail);
        log.debug("Updated email for user {}", user.getId());
    }
}