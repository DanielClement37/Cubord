package org.cubord.cubordbackend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.cubord.cubordbackend.config.TestSecurityConfig;
import org.cubord.cubordbackend.dto.user.UserResponse;
import org.cubord.cubordbackend.dto.user.UserUpdateRequest;
import org.cubord.cubordbackend.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.result.MockMvcResultHandlers;
import org.springframework.http.MediaType;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserController.class)
@Import(TestSecurityConfig.class)
@ActiveProfiles("test")
class UserControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    // Also need to mock any dependencies of SecurityConfig
    @MockitoBean
    private org.cubord.cubordbackend.security.HouseholdPermissionEvaluator householdPermissionEvaluator;

    private UUID sampleUserId;
    private Jwt jwt;

    @BeforeEach
    void setUp() {
        sampleUserId = UUID.randomUUID();
        String sampleUsername = "testuser";
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
                .expiresAt(Instant.now().plusSeconds(3600)) // Add this to make sure token is valid
                .build();

        // Setup common mocks
        when(userService.getCurrentUserDetails(any(JwtAuthenticationToken.class)))
                .thenReturn(sampleUserResponse);
        when(userService.getUser(eq(sampleUserId)))
                .thenReturn(sampleUserResponse);
    }

    @Test
    @DisplayName("should return 401 when not authenticated")
    void shouldReturn401WhenNotAuthenticated() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized());
        
        verifyNoInteractions(userService);
    }

    @Test
    @DisplayName("should allow access with valid authentication")
    void shouldAllowAccessWithValidAuthentication() throws Exception {
        mockMvc.perform(get("/api/users/me")
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                .andExpect(status().isOk());

        verify(userService).getCurrentUserDetails(any(JwtAuthenticationToken.class));
    }

    @Test
    @DisplayName("should return 401 for expired JWT token")
    void shouldReturn401ForExpiredJwtToken() throws Exception {
        // Create a token that is explicitly expired (in the past)
        Instant expiredTime = Instant.now().minusSeconds(3600);
        
        mockMvc.perform(get("/api/users/me")
                .with(SecurityMockMvcRequestPostProcessors.jwt()
                        .jwt(jwt -> jwt
                                .subject(sampleUserId.toString())
                                .expiresAt(expiredTime)
                                .issuedAt(expiredTime.minusSeconds(3600))
                        )))
                .andDo(MockMvcResultHandlers.print()) // Print detailed results for debugging
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(userService);
    }

    @Test
    @DisplayName("should prevent access to another user's data when restricted")
    void shouldPreventAccessToAnotherUsersData() throws Exception {
        UUID anotherUserId = UUID.randomUUID();

        // With security properly configured, this should return 403 Forbidden
        mockMvc.perform(get("/api/users/" + anotherUserId)
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                .andExpect(status().isForbidden());

        // If access is forbidden, the service shouldn't be called
        verify(userService, never()).getUser(eq(anotherUserId));
    }

    @Test
    @DisplayName("should allow access to own user data")
    void shouldAllowAccessToOwnUserData() throws Exception {
        mockMvc.perform(get("/api/users/" + sampleUserId)
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                .andExpect(status().isOk());

        verify(userService).getUser(eq(sampleUserId));
    }
    
    @Test
    @DisplayName("should prevent access to another user's data by username")
    void shouldPreventAccessToAnotherUserDataByUsername() throws Exception {
        String anotherUsername = "anotheruser";
        UUID anotherUserId = UUID.randomUUID();
        
        UserResponse anotherUserResponse = UserResponse.builder()
                .id(anotherUserId)  // Different ID than the JWT subject
                .username(anotherUsername)
                .email("another@example.com")
                .displayName("Another User")
                .createdAt(LocalDateTime.now())
                .build();
                
        when(userService.getUserByUsername(eq(anotherUsername)))
                .thenReturn(anotherUserResponse);
                
        mockMvc.perform(get("/api/users/username/" + anotherUsername)
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                .andExpect(status().isForbidden());
                
        verify(userService).getUserByUsername(eq(anotherUsername));
    }

    @Test
    @DisplayName("should prevent updating another user's profile")
    void shouldPreventUpdatingAnotherUsersProfile() throws Exception {
        UUID anotherUserId = UUID.randomUUID();
        
        UserUpdateRequest updateRequest = new UserUpdateRequest();
        updateRequest.setDisplayName("Updated Name");
        
        mockMvc.perform(put("/api/users/" + anotherUserId)
                .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                .contentType(MediaType.APPLICATION_JSON)
                .content(new ObjectMapper().writeValueAsString(updateRequest)))
                .andExpect(status().isForbidden());
                
        verify(userService, never()).updateUser(any(), any());
    }

    @Test
    @DisplayName("should prevent deleting another user's account")
    void shouldPreventDeletingAnotherUsersAccount() throws Exception {
        UUID anotherUserId = UUID.randomUUID();
        
        mockMvc.perform(delete("/api/users/" + anotherUserId)
                .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                .andExpect(status().isForbidden());
                
        verify(userService, never()).deleteUser(any());
    }

    @Test
    @DisplayName("should allow updating own user profile")
    void shouldAllowUpdatingOwnUserProfile() throws Exception {
        UserUpdateRequest updateRequest = new UserUpdateRequest();
        updateRequest.setDisplayName("Updated Name");
        
        UserResponse updatedResponse = UserResponse.builder()
            .id(sampleUserId)
            .username("testuser")
            .email("test@example.com")
            .displayName("Updated Name")
            .createdAt(LocalDateTime.now())
            .build();
            
        when(userService.updateUser(eq(sampleUserId), any(UserUpdateRequest.class)))
            .thenReturn(updatedResponse);
            
        mockMvc.perform(put("/api/users/" + sampleUserId)
            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
            .contentType(MediaType.APPLICATION_JSON)
            .content(new ObjectMapper().writeValueAsString(updateRequest)))
            .andExpect(status().isOk());
            
        verify(userService).updateUser(eq(sampleUserId), any(UserUpdateRequest.class));
    }

    @Test
    @DisplayName("should require authentication for updating user profile")
    void shouldRequireAuthenticationForUpdatingUserProfile() throws Exception {
        UserUpdateRequest updateRequest = new UserUpdateRequest();
        updateRequest.setDisplayName("Updated Name");
        
        mockMvc.perform(put("/api/users/" + sampleUserId)
            .contentType(MediaType.APPLICATION_JSON)
            .content(new ObjectMapper().writeValueAsString(updateRequest)))
            .andExpect(status().isUnauthorized());
            
        verifyNoInteractions(userService);
    }
}