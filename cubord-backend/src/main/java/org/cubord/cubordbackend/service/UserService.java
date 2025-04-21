package org.cubord.cubordbackend.service;

import lombok.RequiredArgsConstructor;
import org.cubord.cubordbackend.domain.User;
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
        UUID userId = UUID.fromString(token.getName());
        return userRepository.findById(userId)
                .orElseGet(() -> createUser(token));
    }

    private User createUser(JwtAuthenticationToken token) {
        User user = new User();
        user.setId(UUID.fromString(token.getName()));
        user.setEmail(token.getToken().getClaimAsString("email"));
        user.setDisplayName(token.getToken().getClaimAsString("name"));
        return userRepository.save(user);
    }
}