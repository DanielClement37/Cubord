package org.cubord.cubordbackend.controller;

import lombok.RequiredArgsConstructor;
import org.cubord.cubordbackend.dto.UserResponse;
import org.cubord.cubordbackend.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    
    /**
     * Retrieves the currently authenticated user's details.
     * 
     * @param token JWT token of the authenticated user
     * @return ResponseEntity with the user details
     */
    @GetMapping("/me")
    public ResponseEntity<UserResponse> getCurrentUser(JwtAuthenticationToken token) {
        UserResponse user = userService.getCurrentUserDetails(token);
        return ResponseEntity.ok(user);
    }
    
    /**
     * Retrieves a user by their ID.
     * 
     * @param id UUID of the user to retrieve
     * @return ResponseEntity with the user details
     */
    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> getUser(@PathVariable UUID id) {
        UserResponse user = userService.getUser(id);
        return ResponseEntity.ok(user);
    }
    
    /**
     * Retrieves a user by their username.
     * 
     * @param username Username of the user to retrieve
     * @return ResponseEntity with the user details
     */
    @GetMapping("/username/{username}")
    public ResponseEntity<UserResponse> getUserByUsername(@PathVariable String username) {
        UserResponse user = userService.getUserByUsername(username);
        return ResponseEntity.ok(user);
    }
}