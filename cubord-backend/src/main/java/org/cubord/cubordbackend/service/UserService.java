package org.cubord.cubordbackend.service;

import lombok.RequiredArgsConstructor;
import org.cubord.cubordbackend.domain.User;
import org.cubord.cubordbackend.dto.UserResponse;
import org.cubord.cubordbackend.exception.NotFoundException;
import org.cubord.cubordbackend.repository.UserRepository;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;

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
}