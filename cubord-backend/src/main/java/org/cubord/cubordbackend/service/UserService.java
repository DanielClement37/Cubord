package org.cubord.cubordbackend.service;

import lombok.RequiredArgsConstructor;
import org.cubord.cubordbackend.domain.User;
import org.cubord.cubordbackend.dto.UserResponse;
import org.cubord.cubordbackend.exception.NotFoundException;
import org.cubord.cubordbackend.repository.UserRepository;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;

    @Transactional
    public User getCurrentUser(JwtAuthenticationToken token) {
        // Use token.getToken().getSubject() instead of token.getName()
        String subject = token.getToken().getSubject();
        if (subject == null) {
            throw new IllegalArgumentException("JWT token does not contain a subject claim");
        }
        
        UUID userId = UUID.fromString(subject);
        return userRepository.findById(userId)
                .orElseGet(() -> createUser(token));
    }

    @Transactional(readOnly = true)
    public UserResponse getCurrentUserDetails(JwtAuthenticationToken token) {
        User user = getCurrentUser(token);
        return mapToResponse(user);
    }

    @Transactional(readOnly = true)
    public UserResponse getUser(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("User not found"));
        return mapToResponse(user);
    }

    @Transactional(readOnly = true)
    public UserResponse getUserByUsername(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new NotFoundException("User not found"));
        return mapToResponse(user);
    }

    private User createUser(JwtAuthenticationToken token) {
        User user = new User();
        user.setId(UUID.fromString(token.getToken().getSubject()));
        user.setEmail(token.getToken().getClaimAsString("email"));
        user.setUsername(extractUsernameFromEmail(token.getToken().getClaimAsString("email")));
        user.setDisplayName(token.getToken().getClaimAsString("name"));
        return userRepository.save(user);
    }

    private String extractUsernameFromEmail(String email) {
        if (email == null) {
            return null;
        }
        return email.split("@")[0];
    }

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