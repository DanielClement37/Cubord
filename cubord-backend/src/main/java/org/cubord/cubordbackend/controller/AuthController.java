package org.cubord.cubordbackend.controller;

import lombok.RequiredArgsConstructor;
import org.cubord.cubordbackend.domain.User;
import org.cubord.cubordbackend.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final UserService userService;

    @GetMapping("/me")
    public ResponseEntity<User> getCurrentUser(@AuthenticationPrincipal JwtAuthenticationToken token) {
        return ResponseEntity.ok(userService.getCurrentUser(token));
    }
}