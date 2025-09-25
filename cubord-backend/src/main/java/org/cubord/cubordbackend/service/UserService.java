package org.cubord.cubordbackend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cubord.cubordbackend.domain.User;
import org.cubord.cubordbackend.dto.user.UserResponse;
import org.cubord.cubordbackend.dto.user.UserUpdateRequest;
import org.cubord.cubordbackend.exception.NotFoundException;
import org.cubord.cubordbackend.repository.HouseholdMemberRepository;
import org.cubord.cubordbackend.repository.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.validation.ValidationException;
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
     * @throws IllegalArgumentException if the JWT token doesn't contain a subject claim
     */
    @Transactional
    public User getCurrentUser(JwtAuthenticationToken token) {
        String subject = token.getToken().getSubject();
        if (subject == null) {
            throw new IllegalArgumentException("JWT token does not contain a subject claim");
        }
        
        UUID userId = UUID.fromString(subject);
        return userRepository.findById(userId)
                .orElseGet(() -> createUser(token));
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
     * @throws NotFoundException if no user with the given ID exists
     */
    @Transactional(readOnly = true)
    public UserResponse getUser(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("User not found"));
        return mapToResponse(user);
    }

    /**
     * Retrieves a user by their username.
     * 
     * @param username Username of the user to retrieve
     * @return UserResponse containing the user's details
     * @throws NotFoundException if no user with the given username exists
     */
    @Transactional(readOnly = true)
    public UserResponse getUserByUsername(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new NotFoundException("User not found"));
        return mapToResponse(user);
    }

    /**
     * Creates a new user from JWT token information.
     * 
     * @param token JWT authentication token containing user information
     * @return Newly created and persisted User entity
     */
    private User createUser(JwtAuthenticationToken token) {
        User user = new User();
        user.setId(UUID.fromString(token.getToken().getSubject()));
        user.setEmail(token.getToken().getClaimAsString("email"));
        user.setUsername(extractUsernameFromEmail(token.getToken().getClaimAsString("email")));
        user.setDisplayName(token.getToken().getClaimAsString("name"));
        user.setHouseholdMembers(new HashSet<>());
        return userRepository.save(user);
    }

    /**
     * Extracts a username from an email address by taking the part before the @ symbol.
     * 
     * @param email Email address to extract username from
     * @return Username extracted from email, or null if email is null
     */
    private String extractUsernameFromEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }

        if (!email.contains("@")) {
            throw new IllegalArgumentException("Invalid email format");
        }

        String username = email.split("@")[0].trim();
        
        // Handle empty username part
        if (username.isEmpty()) {
            return null; // or some default value
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
     * @throws ValidationException if email format is invalid
     */
    private void validateEmail(String email) {
        if (email == null || !EMAIL_PATTERN.matcher(email).matches()) {
            throw new ValidationException("Invalid email format");
        }
    }

    /**
     * Checks if an email is already in use by another user
     * 
     * @param newEmail Email to check
     * @param userId ID of the current user (to exclude from the check)
     * @throws IllegalArgumentException if email is already in use
     */
    private void checkEmailAlreadyInUse(String newEmail, UUID userId) {
        userRepository.findByEmail(newEmail)
                .ifPresent(existingUser -> {
                    if (!existingUser.getId().equals(userId)) {
                        throw new IllegalArgumentException("Email is already in use");
                    }
                });
    }

    /**
     * Updates user email if it has changed
     * 
     * @param user User to update
     * @param newEmail New email value
     * @throws IllegalArgumentException if email is already in use
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
        }
    }

    /**
     * Updates a user with the provided information.
     * 
     * @param id UUID of the user to update
     * @param updateRequest DTO containing the updated user information
     * @return UserResponse containing the updated user's details
     * @throws NotFoundException if no user with the given ID exists
     * @throws IllegalArgumentException if the email is already in use by another user
     * @throws ValidationException if the email format is invalid
     */
    @Transactional
    public UserResponse updateUser(UUID id, UserUpdateRequest updateRequest) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("User not found"));
        
        if (updateRequest.getDisplayName() != null) {
            user.setDisplayName(updateRequest.getDisplayName());
        }
        
        updateEmailIfChanged(user, updateRequest.getEmail());
        
        // Note: Username updates are not supported to maintain consistency with authentication provider
        
        User updatedUser = userRepository.save(user);
        return mapToResponse(updatedUser);
    }

    /**
     * Partially updates a user with the provided field values.
     * 
     * @param id UUID of the user to update
     * @param patchData Map containing field names and their new values
     * @return UserResponse containing the updated user's details
     * @throws NotFoundException if no user with the given ID exists
     * @throws IllegalArgumentException if invalid field values are provided
     * @throws ValidationException if the email format is invalid
     */
    @Transactional
    public UserResponse patchUser(UUID id, Map<String, Object> patchData) {
        // Validate input
        if (patchData == null || patchData.isEmpty()) {
            throw new IllegalArgumentException("Patch data cannot be null or empty");
        }
        
        // Find user by ID
        User user = userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("User not found"));
        
        // Process each field in the patch data
        for (Map.Entry<String, Object> entry : patchData.entrySet()) {
            String field = entry.getKey();
            Object value = entry.getValue();
            
            switch (field) {
                case "displayName":
                    if (value != null) {
                        if (!(value instanceof String)) {
                            throw new IllegalArgumentException("Display name must be a string");
                        }
                        String displayName = (String) value;
                        if (displayName.length() < 2 || displayName.length() > 50) {
                            throw new ValidationException("Display name must be between 2 and 50 characters");
                        }
                        user.setDisplayName(displayName);
                    }
                    break;
                
                case "email":
                    if (value != null) {
                        if (!(value instanceof String)) {
                            throw new IllegalArgumentException("Email must be a string");
                        }
                        updateEmailIfChanged(user, (String) value);
                    }
                    break;
                
                // Username updates are not supported to maintain consistency with authentication provider
                case "username":
                    // Ignore username changes
                    break;
                
                default:
                    // Ignore unknown fields
                    break;
            }
        }
        
        User updatedUser = userRepository.save(user);
        return mapToResponse(updatedUser);
    }

    /**
     * Deletes a user by their ID.
     *
     * @param id UUID of the user to delete
     * @throws NotFoundException if no user with the given ID exists
     */
    @Transactional
    public void deleteUser(UUID id) {
        // Verify the user exists
        User user = userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("User not found"));
        
        // Perform the deletion
        userRepository.delete(user);
    }

    /**
     * Deletes a user by their ID with authorization check.
     *
     * @param id UUID of the user to delete
     * @param currentUserId UUID of the user requesting the deletion
     * @throws NotFoundException if no user with the given ID exists
     * @throws AccessDeniedException if the current user is not authorized to delete the target user
     */
    @Transactional
    public void deleteUser(UUID id, UUID currentUserId) {
        // Authorization check first
        if (!id.equals(currentUserId)) {
            throw new AccessDeniedException("Cannot delete another user's account");
        }
        
        deleteUser(id);
    }
}