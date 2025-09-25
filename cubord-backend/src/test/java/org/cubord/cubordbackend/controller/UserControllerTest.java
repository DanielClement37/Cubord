package org.cubord.cubordbackend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.cubord.cubordbackend.config.TestSecurityConfig;
import org.cubord.cubordbackend.dto.user.UserResponse;
import org.cubord.cubordbackend.dto.user.UserUpdateRequest;
import org.cubord.cubordbackend.exception.NotFoundException;
import org.cubord.cubordbackend.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.result.MockMvcResultHandlers;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.HashMap;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserController.class)
@Import(TestSecurityConfig.class)
@ActiveProfiles("test")
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    // Need to mock any dependencies of SecurityConfig in TestSecurityConfig
    @MockitoBean
    private org.cubord.cubordbackend.security.HouseholdPermissionEvaluator householdPermissionEvaluator;

    private UUID sampleUserId;
    private String sampleUsername;
    private Jwt jwt;

    @BeforeEach
    void setUp() {
        sampleUserId = UUID.randomUUID();
        sampleUsername = "testuser";
        LocalDateTime createdAt = LocalDateTime.now().minusDays(7);

        // Create sample user response
        UserResponse sampleUserResponse = UserResponse.builder()
                .id(sampleUserId)
                .username(sampleUsername)
                .email("test@example.com")
                .displayName("Test User")
                .createdAt(createdAt)
                .build();

        // Create mock JWT token with a future expiration time
        jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(sampleUserId.toString())
                .claim("email", "test@example.com")
                .claim("name", "Test User")
                .expiresAt(Instant.now().plusSeconds(3600)) // Add explicit expiration
                .build();

        // Setup common mocks
        when(userService.getCurrentUserDetails(any(JwtAuthenticationToken.class)))
                .thenReturn(sampleUserResponse);
        when(userService.getUser(eq(sampleUserId)))
                .thenReturn(sampleUserResponse);
        when(userService.getUserByUsername(eq(sampleUsername)))
                .thenReturn(sampleUserResponse);
    }

    @Nested
    @DisplayName("GET /api/users/me")
    class GetCurrentUser {
        @Test
        @DisplayName("should return correct user details")
        void shouldReturnCorrectUserDetails() throws Exception {
            mockMvc.perform(get("/api/users/me")
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.id").value(sampleUserId.toString()))
                    .andExpect(jsonPath("$.username").value(sampleUsername))
                    .andExpect(jsonPath("$.email").value("test@example.com"))
                    .andExpect(jsonPath("$.displayName").value("Test User"));
                    // Don't assert exact datetime string to avoid format issues

            verify(userService).getCurrentUserDetails(any(JwtAuthenticationToken.class));
        }
        
        @Test
        @DisplayName("should return error on service exception")
        void shouldReturnErrorOnServiceException() throws Exception {
            when(userService.getCurrentUserDetails(any(JwtAuthenticationToken.class)))
                .thenThrow(new RuntimeException("Service error"));
                
            mockMvc.perform(get("/api/users/me")
                    .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));
                // Don't assert on specific error message content format
        }
    }

    @Nested
    @DisplayName("GET /api/users/{id}")
    class GetUserById {
        @Test
        @DisplayName("should return user details when valid ID provided")
        void shouldReturnUserDetailsById() throws Exception {
            // Guard against null UUID
            Objects.requireNonNull(sampleUserId, "sampleUserId must not be null");

            mockMvc.perform(get("/api/users/" + sampleUserId)
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.id").value(sampleUserId.toString()))
                    .andExpect(jsonPath("$.username").value(sampleUsername))
                    .andExpect(jsonPath("$.email").value("test@example.com"))
                    .andExpect(jsonPath("$.displayName").value("Test User"));
                    // Don't assert exact datetime string to avoid format issues

            verify(userService).getUser(eq(sampleUserId));
        }

        @Test
        @DisplayName("should return 404 when user with ID not found")
        void shouldReturn404WhenUserNotFound() throws Exception {
            UUID nonExistentId = UUID.randomUUID();
            String errorMessage = "User not found";

            when(userService.getUser(eq(nonExistentId)))
                    .thenThrow(new NotFoundException(errorMessage));

            // Use a JWT with the nonExistentId as subject to bypass the authorization check
            Jwt testJwt = Jwt.withTokenValue("token")
                    .header("alg", "none")
                    .subject(nonExistentId.toString())
                    .expiresAt(Instant.now().plusSeconds(3600))
                    .build();

            mockMvc.perform(get("/api/users/" + nonExistentId)
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(testJwt)))
                    .andExpect(status().isNotFound())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.message").value(errorMessage));

            verify(userService).getUser(eq(nonExistentId));
        }

        @Test
        @DisplayName("should return 400 when invalid UUID format provided")
        void shouldReturn400WhenInvalidUuidProvided() throws Exception {
            mockMvc.perform(get("/api/users/invalid-uuid")
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(userService);
        }
    }

    @Nested
    @DisplayName("GET /api/users/username/{username}")
    class GetUserByUsername {
        @Test
        @DisplayName("should return user details when valid username provided")
        void shouldReturnUserDetailsByUsername() throws Exception {
            // The test should use the same ID for both the JWT subject and the returned user
            // so the authorization check passes
            mockMvc.perform(get("/api/users/username/" + sampleUsername)
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.id").value(sampleUserId.toString()))
                    .andExpect(jsonPath("$.username").value(sampleUsername))
                    .andExpect(jsonPath("$.email").value("test@example.com"))
                    .andExpect(jsonPath("$.displayName").value("Test User"));
                    // Don't assert exact datetime string to avoid format issues

            verify(userService).getUserByUsername(eq(sampleUsername));
        }

        @Test
        @DisplayName("should return 404 when user with username not found")
        void shouldReturn404WhenUsernameNotFound() throws Exception {
            String nonExistentUsername = "nonexistent";
            String errorMessage = "User not found";

            when(userService.getUserByUsername(eq(nonExistentUsername)))
                    .thenThrow(new NotFoundException(errorMessage));

            mockMvc.perform(get("/api/users/username/" + nonExistentUsername)
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isNotFound())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.message").value(errorMessage));

            verify(userService).getUserByUsername(eq(nonExistentUsername));
        }

        @Test
        @DisplayName("should handle usernames with special characters")
        void shouldHandleUsernamesWithSpecialCharacters() throws Exception {
            String specialUsername = "user.name+special@";
            String encodedUsername = URLEncoder.encode(specialUsername, StandardCharsets.UTF_8);

            UserResponse specialUserResponse = UserResponse.builder()
                    .id(sampleUserId) // Use the same ID as in the JWT to pass authorization
                    .username(specialUsername)
                    .email("special@example.com")
                    .displayName("Special User")
                    .createdAt(LocalDateTime.now())
                    .build();

            when(userService.getUserByUsername(eq(specialUsername)))
                    .thenReturn(specialUserResponse);

            mockMvc.perform(get("/api/users/username/" + encodedUsername)
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andDo(MockMvcResultHandlers.print()) // Add this to see detailed response
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.username").value(specialUsername));

            verify(userService).getUserByUsername(eq(specialUsername));
        }
    }

    @Nested
    @DisplayName("PUT /api/users/{id}")
    class UpdateUser {
        @Test
        @DisplayName("should update user when valid data and authorization provided")
        void shouldUpdateUserWhenValidDataAndAuthorizationProvided() throws Exception {
            // Create user update request
            UserUpdateRequest updateRequest = new UserUpdateRequest();
            updateRequest.setDisplayName("Updated Name");
            updateRequest.setEmail("updated@example.com");
            
            // Mock service response
            UserResponse updatedResponse = UserResponse.builder()
                    .id(sampleUserId)
                    .username(sampleUsername)
                    .email("updated@example.com")
                    .displayName("Updated Name")
                    .createdAt(LocalDateTime.now().minusDays(7))
                    .build();
                    
            when(userService.updateUser(eq(sampleUserId), any(UserUpdateRequest.class)))
                    .thenReturn(updatedResponse);
                    
            mockMvc.perform(put("/api/users/" + sampleUserId)
                    .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(new ObjectMapper().writeValueAsString(updateRequest)))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.id").value(sampleUserId.toString()))
                    .andExpect(jsonPath("$.displayName").value("Updated Name"))
                    .andExpect(jsonPath("$.email").value("updated@example.com"));
                    
            verify(userService).updateUser(eq(sampleUserId), any(UserUpdateRequest.class));
        }
        
        @Test
        @DisplayName("should return 403 when updating another user's profile")
        void shouldReturn403WhenUpdatingAnotherUsersProfile() throws Exception {
            UUID anotherUserId = UUID.randomUUID();
            
            UserUpdateRequest updateRequest = new UserUpdateRequest();
            updateRequest.setDisplayName("Updated Name");
            
            mockMvc.perform(put("/api/users/" + anotherUserId)
                    .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(new ObjectMapper().writeValueAsString(updateRequest)))
                    .andExpect(status().isForbidden());
                    
            verify(userService, never()).updateUser(any(UUID.class), any(UserUpdateRequest.class));
        }
        
        @Test
        @DisplayName("should return 400 when update request is invalid")
        void shouldReturn400WhenUpdateRequestIsInvalid() throws Exception {
            // Create invalid request (e.g., email with invalid format)
            UserUpdateRequest invalidRequest = new UserUpdateRequest();
            invalidRequest.setEmail("not-an-email");
            
            mockMvc.perform(put("/api/users/" + sampleUserId)
                    .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(new ObjectMapper().writeValueAsString(invalidRequest)))
                    .andExpect(status().isBadRequest());
                    
            verify(userService, never()).updateUser(any(UUID.class), any(UserUpdateRequest.class));
        }
    }

    @Nested
    @DisplayName("PATCH /api/users/{id}")
    class PatchUser {
        @Test
        @DisplayName("should partially update user when valid data provided")
        void shouldPartiallyUpdateUserWhenValidDataProvided() throws Exception {
            // Create patch request with only displayName
            Map<String, Object> patchRequest = new HashMap<>();
            patchRequest.put("displayName", "Patched Name");
            
            // Mock service response
            UserResponse patchedResponse = UserResponse.builder()
                    .id(sampleUserId)
                    .username(sampleUsername)
                    .email("test@example.com")
                    .displayName("Patched Name")
                    .createdAt(LocalDateTime.now().minusDays(7))
                    .build();
                    
            when(userService.patchUser(eq(sampleUserId), anyMap()))
                    .thenReturn(patchedResponse);
                    
            mockMvc.perform(patch("/api/users/" + sampleUserId)
                    .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(new ObjectMapper().writeValueAsString(patchRequest)))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.displayName").value("Patched Name"));
                    
            verify(userService).patchUser(eq(sampleUserId), anyMap());
        }
        
        @Test
        @DisplayName("should return 403 when patching another user's profile")
        void shouldReturn403WhenPatchingAnotherUsersProfile() throws Exception {
            UUID anotherUserId = UUID.randomUUID();
            
            Map<String, Object> patchRequest = new HashMap<>();
            patchRequest.put("displayName", "Patched Name");
            
            mockMvc.perform(patch("/api/users/" + anotherUserId)
                    .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(new ObjectMapper().writeValueAsString(patchRequest)))
                    .andExpect(status().isForbidden());
                    
            verify(userService, never()).patchUser(any(UUID.class), anyMap());
        }
    }

    @Nested
    @DisplayName("DELETE /api/users/{id}")
    class DeleteUser {
        @Test
        @DisplayName("should delete user when authorized")
        void shouldDeleteUserWhenAuthorized() throws Exception {
            doNothing().when(userService).deleteUser(eq(sampleUserId));
            
            mockMvc.perform(delete("/api/users/" + sampleUserId)
                    .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isNoContent());
                    
            verify(userService).deleteUser(eq(sampleUserId));
        }
        
        @Test
        @DisplayName("should return 403 when deleting another user")
        void shouldReturn403WhenDeletingAnotherUser() throws Exception {
            UUID anotherUserId = UUID.randomUUID();
            
            mockMvc.perform(delete("/api/users/" + anotherUserId)
                    .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isForbidden());
                    
            verify(userService, never()).deleteUser(any(UUID.class));
        }
    }
}