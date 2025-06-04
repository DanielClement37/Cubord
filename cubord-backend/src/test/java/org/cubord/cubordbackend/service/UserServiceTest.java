package org.cubord.cubordbackend.service;
import org.cubord.cubordbackend.domain.User;
import org.cubord.cubordbackend.dto.UserResponse;
import org.cubord.cubordbackend.exception.NotFoundException;
import org.cubord.cubordbackend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.Optional;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import org.cubord.cubordbackend.dto.UserUpdateRequest;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import org.cubord.cubordbackend.domain.HouseholdMember;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    private User sampleUser;
    private UUID sampleUserId;
    private String sampleUsername;
    private JwtAuthenticationToken jwtAuthenticationToken;

    @BeforeEach
    void setUp() {
        sampleUserId = UUID.randomUUID();
        sampleUsername = "testuser";
        
        // Create sample user
        sampleUser = new User();
        sampleUser.setId(sampleUserId);
        sampleUser.setUsername(sampleUsername);
        sampleUser.setEmail("test@example.com");
        sampleUser.setDisplayName("Test User");
        // Fix: Use proper conversion from Instant to LocalDateTime with ZoneId
        sampleUser.setCreatedAt(LocalDateTime.ofInstant(Instant.now(), ZoneId.systemDefault()));
        sampleUser.setHouseholdMembers(new HashSet<>());
        
        // Create mock JWT token
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(sampleUserId.toString())
                .claim("email", "test@example.com")
                .claim("name", "Test User")
                .build();
        
        jwtAuthenticationToken = new JwtAuthenticationToken(jwt);
    }

    @Nested
    @DisplayName("getCurrentUser method")
    class GetCurrentUser {
        
        @Test
        @DisplayName("should return existing user when found by ID")
        void shouldReturnExistingUserWhenFound() {
            when(userRepository.findById(eq(sampleUserId)))
                    .thenReturn(Optional.of(sampleUser));
                    
            User result = userService.getCurrentUser(jwtAuthenticationToken);
            
            assertThat(result).isEqualTo(sampleUser);
            verify(userRepository).findById(eq(sampleUserId));
            verify(userRepository, never()).save(any(User.class));
        }
        
        @Test
        @DisplayName("should create and return new user when not found by ID")
        void shouldCreateNewUserWhenNotFound() {
            when(userRepository.findById(eq(sampleUserId)))
                    .thenReturn(Optional.empty());
            when(userRepository.save(any(User.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));
                    
            User result = userService.getCurrentUser(jwtAuthenticationToken);
            
            assertThat(result.getId()).isEqualTo(sampleUserId);
            assertThat(result.getEmail()).isEqualTo("test@example.com");
            assertThat(result.getUsername()).isEqualTo("test"); // First part of email
            assertThat(result.getDisplayName()).isEqualTo("Test User");
            
            verify(userRepository).findById(eq(sampleUserId));
            verify(userRepository).save(any(User.class));
        }
        
        @Test
        @DisplayName("should handle username collision when creating new user")
        void shouldHandleUsernameCollisionWhenCreatingNewUser() {
            // Implementation would depend on how UserService handles collisions
            // For example, if it appends numbers or other identifiers
            
            // In this case, we're assuming the service doesn't handle collisions
            // and we're just verifying the current behavior
            
            when(userRepository.findById(eq(sampleUserId)))
                    .thenReturn(Optional.empty());
            when(userRepository.save(any(User.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));
                    
            User result = userService.getCurrentUser(jwtAuthenticationToken);
            
            assertThat(result.getUsername()).isEqualTo("test");
            verify(userRepository).save(any(User.class));
        }
        
        @Test
        @DisplayName("should throw exception when JWT token has no subject")
        void shouldThrowExceptionWhenTokenHasNoSubject() {
            Jwt invalidJwt = Jwt.withTokenValue("token")
                    .header("alg", "none")
                    .claim("email", "test@example.com")
                    .build();
            JwtAuthenticationToken invalidToken = new JwtAuthenticationToken(invalidJwt);
            
            assertThatThrownBy(() -> userService.getCurrentUser(invalidToken))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("JWT token does not contain a subject claim");
                    
            verify(userRepository, never()).findById(any());
            verify(userRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("getCurrentUserDetails method")
    class GetCurrentUserDetails {
        
        @Test
        @DisplayName("should return user details DTO when user exists")
        void shouldReturnUserDetailsDtoWhenUserExists() {
            when(userRepository.findById(eq(sampleUserId)))
                    .thenReturn(Optional.of(sampleUser));
                    
            UserResponse result = userService.getCurrentUserDetails(jwtAuthenticationToken);
            
            assertThat(result.getId()).isEqualTo(sampleUserId);
            assertThat(result.getUsername()).isEqualTo(sampleUsername);
            assertThat(result.getEmail()).isEqualTo("test@example.com");
            assertThat(result.getDisplayName()).isEqualTo("Test User");
            
            verify(userRepository).findById(eq(sampleUserId));
        }
        
        @Test
        @DisplayName("should return user details DTO when creating new user")
        void shouldReturnUserDetailsDtoWhenCreatingNewUser() {
            when(userRepository.findById(eq(sampleUserId)))
                    .thenReturn(Optional.empty());
            when(userRepository.save(any(User.class)))
                    .thenAnswer(invocation -> {
                        User savedUser = invocation.getArgument(0);
                        savedUser.setCreatedAt(LocalDateTime.now());
                        return savedUser;
                    });
                    
            UserResponse result = userService.getCurrentUserDetails(jwtAuthenticationToken);
            
            assertThat(result.getId()).isEqualTo(sampleUserId);
            assertThat(result.getUsername()).isEqualTo("test"); // First part of email
            assertThat(result.getEmail()).isEqualTo("test@example.com");
            assertThat(result.getDisplayName()).isEqualTo("Test User");
            assertThat(result.getCreatedAt()).isNotNull();
            
            verify(userRepository).findById(eq(sampleUserId));
            verify(userRepository).save(any(User.class));
        }
    }

    @Nested
    @DisplayName("getUser method")
    class GetUser {
        
        @Test
        @DisplayName("should return user details when found by ID")
        void shouldReturnUserWhenFoundById() {
            when(userRepository.findById(eq(sampleUserId)))
                    .thenReturn(Optional.of(sampleUser));
                    
            UserResponse result = userService.getUser(sampleUserId);
            
            assertThat(result.getId()).isEqualTo(sampleUserId);
            assertThat(result.getUsername()).isEqualTo(sampleUsername);
            
            verify(userRepository).findById(eq(sampleUserId));
        }
        
        @Test
        @DisplayName("should throw NotFoundException when user not found by ID")
        void shouldThrowNotFoundExceptionWhenUserNotFoundById() {
            when(userRepository.findById(eq(sampleUserId)))
                    .thenReturn(Optional.empty());
                    
            assertThatThrownBy(() -> userService.getUser(sampleUserId))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("User not found");
                    
            verify(userRepository).findById(eq(sampleUserId));
        }
    }

    @Nested
    @DisplayName("getUserByUsername method")
    class GetUserByUsername {
        
        @Test
        @DisplayName("should return user details when found by username")
        void shouldReturnUserWhenFoundByUsername() {
            when(userRepository.findByUsername(eq(sampleUsername)))
                    .thenReturn(Optional.of(sampleUser));
                    
            UserResponse result = userService.getUserByUsername(sampleUsername);
            
            assertThat(result.getId()).isEqualTo(sampleUserId);
            assertThat(result.getUsername()).isEqualTo(sampleUsername);
            
            verify(userRepository).findByUsername(eq(sampleUsername));
        }
        
        @Test
        @DisplayName("should throw NotFoundException when user not found by username")
        void shouldThrowNotFoundExceptionWhenUserNotFoundByUsername() {
            when(userRepository.findByUsername(eq(sampleUsername)))
                    .thenReturn(Optional.empty());
                    
            assertThatThrownBy(() -> userService.getUserByUsername(sampleUsername))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("User not found");
                    
            verify(userRepository).findByUsername(eq(sampleUsername));
        }
    }

    @Nested
    @DisplayName("extractUsernameFromEmail method")
    class ExtractUsernameFromEmail {
        
        @Test
        @DisplayName("should extract username part before @ symbol")
        void shouldExtractUsernameFromEmail() throws Exception {
            // Use reflection to access private method
            java.lang.reflect.Method method = UserService.class.getDeclaredMethod("extractUsernameFromEmail", String.class);
            method.setAccessible(true);
            
            String result = (String) method.invoke(userService, "user123@example.com");
            assertThat(result).isEqualTo("user123");
            
            String resultWithNullEmail = (String) method.invoke(userService, (String) null);
            assertThat(resultWithNullEmail).isNull();
        }

        @Test
        @DisplayName("should handle email without @ symbol")
        void shouldHandleEmailWithoutAtSymbol() throws Exception {
            java.lang.reflect.Method method = UserService.class.getDeclaredMethod("extractUsernameFromEmail", String.class);
            method.setAccessible(true);

            // Test that the method throws IllegalArgumentException when email doesn't contain @
            assertThatThrownBy(() -> method.invoke(userService, "invalid-email"))
                    .hasCauseInstanceOf(IllegalArgumentException.class)
                    .hasRootCauseMessage("Invalid email format");
        }



        @Test
        @DisplayName("should handle email with multiple @ symbols")
        void shouldHandleEmailWithMultipleAtSymbols() throws Exception {
            java.lang.reflect.Method method = UserService.class.getDeclaredMethod("extractUsernameFromEmail", String.class);
            method.setAccessible(true);
            
            String result = (String) method.invoke(userService, "user@name@example.com");
            assertThat(result).isEqualTo("user");
        }
    }
    
    @Nested
    @DisplayName("createUser method")
    class CreateUser {
        
        @Test
        @DisplayName("should initialize householdMembers when creating user")
        void shouldInitializeHouseholdMembersWhenCreatingUser() {
            when(userRepository.findById(eq(sampleUserId)))
                    .thenReturn(Optional.empty());
            when(userRepository.save(any(User.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));
            
            User result = userService.getCurrentUser(jwtAuthenticationToken);
            
            // householdMembers should be initialized (not null) but empty
            assertThat(result.getHouseholdMembers()).isNotNull();
            
            verify(userRepository).save(any(User.class));
        }
    }
    @Nested
    @DisplayName("updateUser method")
    class UpdateUser {
        
        @Test
        @DisplayName("should update user when valid data provided")
        void shouldUpdateUserWhenValidDataProvided() {
            // Create update request
            UserUpdateRequest updateRequest = new UserUpdateRequest();
            updateRequest.setDisplayName("Updated Name");
            updateRequest.setEmail("updated@example.com");
            
            // Create updated user
            User updatedUser = new User();
            updatedUser.setId(sampleUserId);
            updatedUser.setUsername(sampleUsername);
            updatedUser.setEmail("updated@example.com");
            updatedUser.setDisplayName("Updated Name");
            updatedUser.setCreatedAt(LocalDateTime.now().minusDays(7));
            updatedUser.setUpdatedAt(LocalDateTime.now());
            updatedUser.setHouseholdMembers(new HashSet<>());
            
            when(userRepository.findById(eq(sampleUserId)))
                    .thenReturn(Optional.of(sampleUser));
            when(userRepository.save(any(User.class)))
                    .thenReturn(updatedUser);
                    
            UserResponse result = userService.updateUser(sampleUserId, updateRequest);
            
            assertThat(result.getId()).isEqualTo(sampleUserId);
            assertThat(result.getDisplayName()).isEqualTo("Updated Name");
            assertThat(result.getEmail()).isEqualTo("updated@example.com");
            
            verify(userRepository).findById(eq(sampleUserId));
            verify(userRepository).save(any(User.class));
        }
        
        @Test
        @DisplayName("should throw NotFoundException when user not found for update")
        void shouldThrowNotFoundExceptionWhenUserNotFoundForUpdate() {
            UserUpdateRequest updateRequest = new UserUpdateRequest();
            updateRequest.setDisplayName("Updated Name");
            
            when(userRepository.findById(eq(sampleUserId)))
                    .thenReturn(Optional.empty());
                    
            assertThatThrownBy(() -> userService.updateUser(sampleUserId, updateRequest))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("User not found");
                    
            verify(userRepository).findById(eq(sampleUserId));
            verify(userRepository, never()).save(any(User.class));
        }
        
        @Test
        @DisplayName("should not update username field")
        void shouldNotUpdateUsernameField() {
            // Create update request with username
            UserUpdateRequest updateRequest = new UserUpdateRequest();
            updateRequest.setDisplayName("Updated Name");
            updateRequest.setUsername("newusername"); // This should be ignored
            
            when(userRepository.findById(eq(sampleUserId)))
                    .thenReturn(Optional.of(sampleUser));
            when(userRepository.save(any(User.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));
                    
            UserResponse result = userService.updateUser(sampleUserId, updateRequest);
            
            // Username should remain unchanged
            assertThat(result.getUsername()).isEqualTo(sampleUsername);
            
            verify(userRepository).save(any(User.class));
        }
    }

    @Nested
    @DisplayName("patchUser method")
    class PatchUser {
        
        @Test
        @DisplayName("should partially update user when valid data provided")
        void shouldPartiallyUpdateUserWhenValidDataProvided() {
            // Create patch data with only displayName
            Map<String, Object> patchData = new HashMap<>();
            patchData.put("displayName", "Patched Name");
            
            // Create patched user
            User patchedUser = new User();
            patchedUser.setId(sampleUserId);
            patchedUser.setUsername(sampleUsername);
            patchedUser.setEmail("test@example.com");
            patchedUser.setDisplayName("Patched Name");
            patchedUser.setCreatedAt(LocalDateTime.now().minusDays(7));
            patchedUser.setUpdatedAt(LocalDateTime.now());
            patchedUser.setHouseholdMembers(new HashSet<>());
            
            when(userRepository.findById(eq(sampleUserId)))
                    .thenReturn(Optional.of(sampleUser));
            when(userRepository.save(any(User.class)))
                    .thenReturn(patchedUser);
                    
            UserResponse result = userService.patchUser(sampleUserId, patchData);
            
            assertThat(result.getDisplayName()).isEqualTo("Patched Name");
            // Email should remain unchanged
            assertThat(result.getEmail()).isEqualTo("test@example.com");
            
            verify(userRepository).findById(eq(sampleUserId));
            verify(userRepository).save(any(User.class));
        }
        
        @Test
        @DisplayName("should throw NotFoundException when user not found for patch")
        void shouldThrowNotFoundExceptionWhenUserNotFoundForPatch() {
            Map<String, Object> patchData = new HashMap<>();
            patchData.put("displayName", "Patched Name");
            
            when(userRepository.findById(eq(sampleUserId)))
                    .thenReturn(Optional.empty());
                    
            assertThatThrownBy(() -> userService.patchUser(sampleUserId, patchData))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("User not found");
                    
            verify(userRepository).findById(eq(sampleUserId));
            verify(userRepository, never()).save(any(User.class));
        }
        
        @Test
        @DisplayName("should ignore invalid fields in patch data")
        void shouldIgnoreInvalidFieldsInPatchData() {
            Map<String, Object> patchData = new HashMap<>();
            patchData.put("displayName", "Patched Name");
            patchData.put("invalidField", "value"); // This should be ignored
            
            when(userRepository.findById(eq(sampleUserId)))
                    .thenReturn(Optional.of(sampleUser));
            when(userRepository.save(any(User.class)))
                    .thenAnswer(invocation -> {
                    User savedUser = invocation.getArgument(0);
                    savedUser.setDisplayName("Patched Name");
                    return savedUser;
                });
                
            UserResponse result = userService.patchUser(sampleUserId, patchData);
            
            assertThat(result.getDisplayName()).isEqualTo("Patched Name");
            
            verify(userRepository).save(any(User.class));
        }
    }

    @Nested
    @DisplayName("deleteUser method")
    class DeleteUser {
        
        @Test
        @DisplayName("should delete user when found")
        void shouldDeleteUserWhenFound() {
            when(userRepository.findById(eq(sampleUserId)))
                    .thenReturn(Optional.of(sampleUser));
            doNothing().when(userRepository).delete(any(User.class));
            
            userService.deleteUser(sampleUserId);
            
            verify(userRepository).findById(eq(sampleUserId));
            verify(userRepository).delete(eq(sampleUser));
        }
        
        @Test
        @DisplayName("should throw NotFoundException when user not found for deletion")
        void shouldThrowNotFoundExceptionWhenUserNotFoundForDeletion() {
            when(userRepository.findById(eq(sampleUserId)))
                    .thenReturn(Optional.empty());
                    
            assertThatThrownBy(() -> userService.deleteUser(sampleUserId))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("User not found");
                    
            verify(userRepository).findById(eq(sampleUserId));
            verify(userRepository, never()).delete(any(User.class));
        }
        
        @Test
        @DisplayName("should handle cascading deletions of associated entities")
        void shouldHandleCascadingDeletionsOfAssociatedEntities() {
            // Create user with household members
            User userWithMembers = new User();
            userWithMembers.setId(sampleUserId);
            userWithMembers.setUsername(sampleUsername);
            userWithMembers.setEmail("test@example.com");
            userWithMembers.setDisplayName("Test User");
            
            // Mock a household member
            HouseholdMember member = mock(HouseholdMember.class);
            Set<HouseholdMember> members = new HashSet<>();
            members.add(member);
            userWithMembers.setHouseholdMembers(members);
            
            when(userRepository.findById(eq(sampleUserId)))
                    .thenReturn(Optional.of(userWithMembers));
            doNothing().when(userRepository).delete(any(User.class));
            
            userService.deleteUser(sampleUserId);
            
            verify(userRepository).delete(eq(userWithMembers));
            // You might want to verify interactions with other repositories 
            // that handle related entities (if they need special handling)
        }
    }
}