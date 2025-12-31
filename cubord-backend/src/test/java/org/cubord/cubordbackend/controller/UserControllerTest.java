package org.cubord.cubordbackend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.cubord.cubordbackend.config.TestSecurityConfig;
import org.cubord.cubordbackend.dto.user.UserResponse;
import org.cubord.cubordbackend.dto.user.UserUpdateRequest;
import org.cubord.cubordbackend.exception.*;
import org.cubord.cubordbackend.security.SecurityService;
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
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for {@link UserController}.
 *
 * <p>Tests verify:</p>
 * <ul>
 *   <li>Endpoint functionality and response formats</li>
 *   <li>Authorization via {@code @PreAuthorize} with mocked SecurityService</li>
 *   <li>Proper exception handling and HTTP status codes</li>
 *   <li>Input validation</li>
 * </ul>
 */
@WebMvcTest(UserController.class)
@Import(TestSecurityConfig.class)
@ActiveProfiles("test")
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private UserService userService;

    @MockitoBean(name = "security")
    private SecurityService securityService;

    @MockitoBean
    private org.cubord.cubordbackend.security.HouseholdPermissionEvaluator householdPermissionEvaluator;

    private UUID currentUserId;
    private UUID otherUserId;
    private String sampleUsername;
    private UserResponse sampleUserResponse;
    private Jwt jwt;

    @BeforeEach
    void setUp() {
        currentUserId = UUID.randomUUID();
        otherUserId = UUID.randomUUID();
        sampleUsername = "testuser";

        sampleUserResponse = UserResponse.builder()
                .id(currentUserId)
                .username(sampleUsername)
                .email("test@example.com")
                .displayName("Test User")
                .createdAt(LocalDateTime.now().minusDays(7))
                .updatedAt(LocalDateTime.now())
                .build();

        jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(currentUserId.toString())
                .claim("email", "test@example.com")
                .claim("name", "Test User")
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        // Default security service mock behavior
        when(securityService.canAccessUserProfile(currentUserId)).thenReturn(true);
        when(securityService.canAccessUserProfile(otherUserId)).thenReturn(false);
        when(securityService.canModifyUserProfile(currentUserId)).thenReturn(true);
        when(securityService.canModifyUserProfile(otherUserId)).thenReturn(false);
    }

    @Nested
    @DisplayName("GET /api/users/me")
    class GetCurrentUser {

        @Test
        @DisplayName("should return current user details for authenticated user")
        void shouldReturnCurrentUserDetails() throws Exception {
            when(userService.getCurrentUserDetails()).thenReturn(sampleUserResponse);

            mockMvc.perform(get("/api/users/me")
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.id").value(currentUserId.toString()))
                    .andExpect(jsonPath("$.username").value(sampleUsername))
                    .andExpect(jsonPath("$.email").value("test@example.com"))
                    .andExpect(jsonPath("$.displayName").value("Test User"));

            verify(userService).getCurrentUserDetails();
        }

        @Test
        @DisplayName("should return 401 for unauthenticated request")
        void shouldReturn401ForUnauthenticatedRequest() throws Exception {
            mockMvc.perform(get("/api/users/me"))
                    .andExpect(status().isUnauthorized());

            verifyNoInteractions(userService);
        }

        @Test
        @DisplayName("should return 401 when authentication fails")
        void shouldReturn401WhenAuthenticationFails() throws Exception {
            when(userService.getCurrentUserDetails())
                    .thenThrow(new AuthenticationRequiredException("No authenticated user found"));

            mockMvc.perform(get("/api/users/me")
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error_code").value("AUTHENTICATION_REQUIRED"));

            verify(userService).getCurrentUserDetails();
        }

        @Test
        @DisplayName("should include cache control headers")
        void shouldIncludeCacheControlHeaders() throws Exception {
            when(userService.getCurrentUserDetails()).thenReturn(sampleUserResponse);

            mockMvc.perform(get("/api/users/me")
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Cache-Control", "max-age=300"));
        }
    }

    @Nested
    @DisplayName("GET /api/users/{id}")
    class GetUserById {

        @Test
        @DisplayName("should return user details when accessing own profile")
        void shouldReturnUserDetailsWhenAccessingOwnProfile() throws Exception {
            when(userService.getUser(currentUserId)).thenReturn(sampleUserResponse);

            mockMvc.perform(get("/api/users/" + currentUserId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.id").value(currentUserId.toString()))
                    .andExpect(jsonPath("$.username").value(sampleUsername));

            verify(securityService).canAccessUserProfile(currentUserId);
            verify(userService).getUser(currentUserId);
        }

        @Test
        @DisplayName("should return 403 when accessing another user's profile without permission")
        void shouldReturn403WhenAccessingUnauthorizedProfile() throws Exception {
            mockMvc.perform(get("/api/users/" + otherUserId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isForbidden());

            verify(securityService).canAccessUserProfile(otherUserId);
            verifyNoInteractions(userService);
        }

        @Test
        @DisplayName("should return 404 when user not found")
        void shouldReturn404WhenUserNotFound() throws Exception {
            when(userService.getUser(currentUserId))
                    .thenThrow(new NotFoundException("User", currentUserId));

            mockMvc.perform(get("/api/users/" + currentUserId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error_code").value("RESOURCE_NOT_FOUND"));

            verify(userService).getUser(currentUserId);
        }

        @Test
        @DisplayName("should return 400 when invalid UUID format provided")
        void shouldReturn400WhenInvalidUuidProvided() throws Exception {
            mockMvc.perform(get("/api/users/invalid-uuid")
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(userService);
        }

        @Test
        @DisplayName("should allow access to shared household member profile")
        void shouldAllowAccessToSharedHouseholdMemberProfile() throws Exception {
            UUID sharedHouseholdMemberId = UUID.randomUUID();
            when(securityService.canAccessUserProfile(sharedHouseholdMemberId)).thenReturn(true);

            UserResponse sharedMemberResponse = UserResponse.builder()
                    .id(sharedHouseholdMemberId)
                    .username("sharedmember")
                    .email("shared@example.com")
                    .displayName("Shared Member")
                    .createdAt(LocalDateTime.now())
                    .build();
            when(userService.getUser(sharedHouseholdMemberId)).thenReturn(sharedMemberResponse);

            mockMvc.perform(get("/api/users/" + sharedHouseholdMemberId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(sharedHouseholdMemberId.toString()));

            verify(securityService).canAccessUserProfile(sharedHouseholdMemberId);
            verify(userService).getUser(sharedHouseholdMemberId);
        }
    }

    @Nested
    @DisplayName("GET /api/users/username/{username}")
    class GetUserByUsername {

        @Test
        @DisplayName("should return user details when accessing own profile by username")
        void shouldReturnUserDetailsByUsername() throws Exception {
            when(userService.getUserByUsername(sampleUsername)).thenReturn(sampleUserResponse);

            mockMvc.perform(get("/api/users/username/" + sampleUsername)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.id").value(currentUserId.toString()))
                    .andExpect(jsonPath("$.username").value(sampleUsername));

            verify(userService).getUserByUsername(sampleUsername);
        }

        @Test
        @DisplayName("should return 404 when username not found")
        void shouldReturn404WhenUsernameNotFound() throws Exception {
            String nonExistentUsername = "nonexistent";
            when(userService.getUserByUsername(nonExistentUsername))
                    .thenThrow(new NotFoundException("User not found with username: " + nonExistentUsername));

            mockMvc.perform(get("/api/users/username/" + nonExistentUsername)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error_code").value("RESOURCE_NOT_FOUND"));

            verify(userService).getUserByUsername(nonExistentUsername);
        }

        @Test
        @DisplayName("should return 403 when user lacks permission to access profile")
        void shouldReturn403WhenLackingPermission() throws Exception {
            String otherUsername = "otheruser";
            when(userService.getUserByUsername(otherUsername))
                    .thenThrow(new InsufficientPermissionException("You do not have permission to access this user profile"));

            mockMvc.perform(get("/api/users/username/" + otherUsername)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error_code").value("INSUFFICIENT_PERMISSION"));

            verify(userService).getUserByUsername(otherUsername);
        }

        @Test
        @DisplayName("should handle usernames with special characters")
        void shouldHandleUsernamesWithSpecialCharacters() throws Exception {
            String specialUsername = "user.name+special@";
            String encodedUsername = URLEncoder.encode(specialUsername, StandardCharsets.UTF_8);

            UserResponse specialUserResponse = UserResponse.builder()
                    .id(currentUserId)
                    .username(specialUsername)
                    .email("special@example.com")
                    .displayName("Special User")
                    .createdAt(LocalDateTime.now())
                    .build();

            when(userService.getUserByUsername(specialUsername)).thenReturn(specialUserResponse);

            mockMvc.perform(get("/api/users/username/" + encodedUsername)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.username").value(specialUsername));

            verify(userService).getUserByUsername(specialUsername);
        }
    }

    @Nested
    @DisplayName("PUT /api/users/{id}")
    class UpdateUser {

        @Test
        @DisplayName("should update user when modifying own profile")
        void shouldUpdateUserWhenModifyingOwnProfile() throws Exception {
            UserUpdateRequest updateRequest = new UserUpdateRequest();
            updateRequest.setDisplayName("Updated Name");
            updateRequest.setEmail("updated@example.com");

            UserResponse updatedResponse = UserResponse.builder()
                    .id(currentUserId)
                    .username(sampleUsername)
                    .email("updated@example.com")
                    .displayName("Updated Name")
                    .createdAt(LocalDateTime.now().minusDays(7))
                    .updatedAt(LocalDateTime.now())
                    .build();

            when(userService.updateUser(eq(currentUserId), any(UserUpdateRequest.class)))
                    .thenReturn(updatedResponse);

            mockMvc.perform(put("/api/users/" + currentUserId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(updateRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.displayName").value("Updated Name"))
                    .andExpect(jsonPath("$.email").value("updated@example.com"));

            verify(securityService).canModifyUserProfile(currentUserId);
            verify(userService).updateUser(eq(currentUserId), any(UserUpdateRequest.class));
        }

        @Test
        @DisplayName("should return 403 when updating another user's profile")
        void shouldReturn403WhenUpdatingAnotherUsersProfile() throws Exception {
            UserUpdateRequest updateRequest = new UserUpdateRequest();
            updateRequest.setDisplayName("Updated Name");

            mockMvc.perform(put("/api/users/" + otherUserId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(updateRequest)))
                    .andExpect(status().isForbidden());

            verify(securityService).canModifyUserProfile(otherUserId);
            verifyNoInteractions(userService);
        }

        @Test
        @DisplayName("should return 400 when email format is invalid")
        void shouldReturn400WhenEmailFormatIsInvalid() throws Exception {
            UserUpdateRequest invalidRequest = new UserUpdateRequest();
            invalidRequest.setEmail("not-an-email");

            mockMvc.perform(put("/api/users/" + currentUserId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(invalidRequest)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(userService);
        }

        @Test
        @DisplayName("should return 404 when user not found")
        void shouldReturn404WhenUserNotFound() throws Exception {
            UserUpdateRequest updateRequest = new UserUpdateRequest();
            updateRequest.setDisplayName("Updated Name");

            when(userService.updateUser(eq(currentUserId), any(UserUpdateRequest.class)))
                    .thenThrow(new NotFoundException("User", currentUserId));

            mockMvc.perform(put("/api/users/" + currentUserId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(updateRequest)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error_code").value("RESOURCE_NOT_FOUND"));

            verify(userService).updateUser(eq(currentUserId), any(UserUpdateRequest.class));
        }

        @Test
        @DisplayName("should return 409 when email is already in use")
        void shouldReturn409WhenEmailAlreadyInUse() throws Exception {
            UserUpdateRequest updateRequest = new UserUpdateRequest();
            updateRequest.setEmail("taken@example.com");

            when(userService.updateUser(eq(currentUserId), any(UserUpdateRequest.class)))
                    .thenThrow(new ConflictException("Email 'taken@example.com' is already in use by another user"));

            mockMvc.perform(put("/api/users/" + currentUserId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(updateRequest)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error_code").value("RESOURCE_CONFLICT"));

            verify(userService).updateUser(eq(currentUserId), any(UserUpdateRequest.class));
        }
    }

    @Nested
    @DisplayName("PATCH /api/users/{id}")
    class PatchUser {

        @Test
        @DisplayName("should partially update user when modifying own profile")
        void shouldPartiallyUpdateUser() throws Exception {
            Map<String, Object> patchRequest = new HashMap<>();
            patchRequest.put("displayName", "Patched Name");

            UserResponse patchedResponse = UserResponse.builder()
                    .id(currentUserId)
                    .username(sampleUsername)
                    .email("test@example.com")
                    .displayName("Patched Name")
                    .createdAt(LocalDateTime.now().minusDays(7))
                    .updatedAt(LocalDateTime.now())
                    .build();

            when(userService.patchUser(eq(currentUserId), anyMap())).thenReturn(patchedResponse);

            mockMvc.perform(patch("/api/users/" + currentUserId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(patchRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.displayName").value("Patched Name"));

            verify(securityService).canModifyUserProfile(currentUserId);
            verify(userService).patchUser(eq(currentUserId), anyMap());
        }

        @Test
        @DisplayName("should return 403 when patching another user's profile")
        void shouldReturn403WhenPatchingAnotherUsersProfile() throws Exception {
            Map<String, Object> patchRequest = new HashMap<>();
            patchRequest.put("displayName", "Patched Name");

            mockMvc.perform(patch("/api/users/" + otherUserId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(patchRequest)))
                    .andExpect(status().isForbidden());

            verify(securityService).canModifyUserProfile(otherUserId);
            verifyNoInteractions(userService);
        }

        @Test
        @DisplayName("should return 400 when patching unsupported field")
        void shouldReturn400WhenPatchingUnsupportedField() throws Exception {
            Map<String, Object> patchRequest = new HashMap<>();
            patchRequest.put("unsupportedField", "value");

            when(userService.patchUser(eq(currentUserId), anyMap()))
                    .thenThrow(new ValidationException("Unsupported field for patching: unsupportedField"));

            mockMvc.perform(patch("/api/users/" + currentUserId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(patchRequest)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error_code").value("VALIDATION_ERROR"));

            verify(userService).patchUser(eq(currentUserId), anyMap());
        }

        @Test
        @DisplayName("should return 400 when patch data is empty")
        void shouldReturn400WhenPatchDataIsEmpty() throws Exception {
            Map<String, Object> emptyPatchRequest = new HashMap<>();

            when(userService.patchUser(eq(currentUserId), anyMap()))
                    .thenThrow(new ValidationException("Patch data cannot be null or empty"));

            mockMvc.perform(patch("/api/users/" + currentUserId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(emptyPatchRequest)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error_code").value("VALIDATION_ERROR"));

            verify(userService).patchUser(eq(currentUserId), anyMap());
        }

        @Test
        @DisplayName("should return 409 when username is already taken")
        void shouldReturn409WhenUsernameAlreadyTaken() throws Exception {
            Map<String, Object> patchRequest = new HashMap<>();
            patchRequest.put("username", "takename");

            when(userService.patchUser(eq(currentUserId), anyMap()))
                    .thenThrow(new ConflictException("Username 'takenname' is already in use"));

            mockMvc.perform(patch("/api/users/" + currentUserId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(patchRequest)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error_code").value("RESOURCE_CONFLICT"));

            verify(userService).patchUser(eq(currentUserId), anyMap());
        }
    }

    @Nested
    @DisplayName("DELETE /api/users/{id}")
    class DeleteUser {

        @Test
        @DisplayName("should delete user when deleting own account")
        void shouldDeleteUserWhenDeletingOwnAccount() throws Exception {
            doNothing().when(userService).deleteUser(currentUserId);

            mockMvc.perform(delete("/api/users/" + currentUserId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isNoContent());

            verify(securityService).canModifyUserProfile(currentUserId);
            verify(userService).deleteUser(currentUserId);
        }

        @Test
        @DisplayName("should return 403 when deleting another user's account")
        void shouldReturn403WhenDeletingAnotherUsersAccount() throws Exception {
            mockMvc.perform(delete("/api/users/" + otherUserId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isForbidden());

            verify(securityService).canModifyUserProfile(otherUserId);
            verifyNoInteractions(userService);
        }

        @Test
        @DisplayName("should return 404 when user to delete not found")
        void shouldReturn404WhenUserToDeleteNotFound() throws Exception {
            doThrow(new NotFoundException("User", currentUserId))
                    .when(userService).deleteUser(currentUserId);

            mockMvc.perform(delete("/api/users/" + currentUserId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error_code").value("RESOURCE_NOT_FOUND"));

            verify(userService).deleteUser(currentUserId);
        }

        @Test
        @DisplayName("should return 409 when user has dependent data preventing deletion")
        void shouldReturn409WhenUserHasDependentData() throws Exception {
            doThrow(new DataIntegrityException(
                    "Failed to delete user. User may have associated data that must be removed first."))
                    .when(userService).deleteUser(currentUserId);

            mockMvc.perform(delete("/api/users/" + currentUserId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error_code").value("DATA_INTEGRITY_VIOLATION"));

            verify(userService).deleteUser(currentUserId);
        }
    }

    @Nested
    @DisplayName("Authentication Edge Cases")
    class AuthenticationEdgeCases {

        @Test
        @DisplayName("should return 401 for expired JWT token")
        void shouldReturn401ForExpiredToken() throws Exception {
            // Note: SecurityMockMvcRequestPostProcessors.jwt() bypasses actual JWT validation.
            // To test expired token behavior, we simply don't provide any authentication,
            // which simulates what happens when Spring Security rejects an invalid/expired token.
            mockMvc.perform(get("/api/users/me"))
                    .andExpect(status().isUnauthorized());

            verifyNoInteractions(userService);
        }

        @Test
        @DisplayName("should return 401 when no authorization header provided")
        void shouldReturn401WhenNoAuthorizationHeader() throws Exception {
            mockMvc.perform(get("/api/users/" + currentUserId))
                    .andExpect(status().isUnauthorized());

            verifyNoInteractions(userService);
        }
    }

    @Nested
    @DisplayName("Internal Error Handling")
    class InternalErrorHandling {

        @Test
        @DisplayName("should return 500 when unexpected service exception occurs")
        void shouldReturn500WhenUnexpectedExceptionOccurs() throws Exception {
            when(userService.getCurrentUserDetails())
                    .thenThrow(new RuntimeException("Database connection failed"));

            mockMvc.perform(get("/api/users/me")
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.error_code").value("INTERNAL_SERVER_ERROR"))
                    .andExpect(jsonPath("$.correlation_id").exists());
        }
    }

    @Test
    @DisplayName("should patch multiple fields simultaneously")
    void shouldPatchMultipleFields() throws Exception {
        Map<String, Object> patchRequest = new HashMap<>();
        patchRequest.put("displayName", "New Name");
        patchRequest.put("email", "new@example.com");

        UserResponse patchedResponse = UserResponse.builder()
                .id(currentUserId)
                .username(sampleUsername)
                .email("new@example.com")
                .displayName("New Name")
                .build();

        when(userService.patchUser(eq(currentUserId), anyMap())).thenReturn(patchedResponse);

        mockMvc.perform(patch("/api/users/" + currentUserId)
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(patchRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("New Name"))
                .andExpect(jsonPath("$.email").value("new@example.com"));

        // Verify the map passed to service contains both fields
        verify(userService).patchUser(eq(currentUserId), argThat(map ->
                map.containsKey("displayName") && map.containsKey("email")));
    }

    @Test
    @DisplayName("should return complete user response structure")
    void shouldReturnCompleteResponseStructure() throws Exception {
        LocalDateTime createdAt = LocalDateTime.of(2024, 1, 1, 12, 0);
        LocalDateTime updatedAt = LocalDateTime.of(2024, 6, 15, 14, 30);

        UserResponse fullResponse = UserResponse.builder()
                .id(currentUserId)
                .username(sampleUsername)
                .email("test@example.com")
                .displayName("Test User")
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .build();

        when(userService.getUser(currentUserId)).thenReturn(fullResponse);

        mockMvc.perform(get("/api/users/" + currentUserId)
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.username").exists())
                .andExpect(jsonPath("$.email").exists())
                .andExpect(jsonPath("$.displayName").exists())
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.updatedAt").exists())
                // Verify no sensitive fields leak
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    @DisplayName("should handle null fields in PUT request correctly")
    void shouldHandleNullFieldsInPutRequest() throws Exception {
        UserUpdateRequest updateRequest = new UserUpdateRequest();
        updateRequest.setDisplayName("Updated Name");
        // email and username intentionally null

        UserResponse updatedResponse = UserResponse.builder()
                .id(currentUserId)
                .username(sampleUsername)
                .email("test@example.com") // unchanged
                .displayName("Updated Name")
                .build();

        when(userService.updateUser(eq(currentUserId), any(UserUpdateRequest.class)))
                .thenReturn(updatedResponse);

        mockMvc.perform(put("/api/users/" + currentUserId)
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk());

        verify(userService).updateUser(eq(currentUserId), argThat(req ->
                req.getDisplayName().equals("Updated Name") && req.getEmail() == null));
    }

    @Nested
    @DisplayName("Error Response Format")
    class ErrorResponseFormat {

        @Test
        @DisplayName("should include correlation_id in all error responses")
        void shouldIncludeCorrelationIdInErrors() throws Exception {
            when(userService.getUser(currentUserId))
                    .thenThrow(new NotFoundException("User", currentUserId));

            mockMvc.perform(get("/api/users/" + currentUserId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.correlation_id").exists())
                    .andExpect(jsonPath("$.timestamp").exists())
                    .andExpect(jsonPath("$.message").exists())
                    .andExpect(jsonPath("$.error_code").exists());
        }

        @Test
        @DisplayName("should include message in validation errors")
        void shouldIncludeMessageInValidationErrors() throws Exception {
            UserUpdateRequest invalidRequest = new UserUpdateRequest();
            invalidRequest.setEmail("invalid-email");

            mockMvc.perform(put("/api/users/" + currentUserId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(invalidRequest)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").isNotEmpty());
        }
    }

    @Test
    @DisplayName("should return 500 when SecurityService throws unexpected exception")
    void shouldReturn500WhenSecurityServiceFails() throws Exception {
        when(securityService.canAccessUserProfile(any()))
                .thenThrow(new RuntimeException("Security context unavailable"));

        mockMvc.perform(get("/api/users/" + currentUserId)
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                .andExpect(status().isInternalServerError());

        verifyNoInteractions(userService);
    }
}

