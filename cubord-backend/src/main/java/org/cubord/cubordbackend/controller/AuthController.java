package org.cubord.cubordbackend.controller;

import lombok.RequiredArgsConstructor;
import org.cubord.cubordbackend.domain.User;
import org.cubord.cubordbackend.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    private final UserService userService;

    /**
     * Retrieves the current authenticated user.
     * 
     * @param authentication Current authentication object
     * @return ResponseEntity with user details or unauthorized status
     */
    @GetMapping("/me")
    public ResponseEntity<User> getCurrentUser(Authentication authentication) {
        if (authentication == null || !(authentication instanceof JwtAuthenticationToken token)) {
            logger.error("Authentication is null or not a JwtAuthenticationToken: {}",
                    authentication != null ? authentication.getClass().getName() : "null");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        logger.debug("Token received: {}", token);
        logger.debug("Subject: {}", token.getToken().getSubject());

        return ResponseEntity.ok(userService.getCurrentUser(token));
    }
}