package org.cubord.cubordbackend.service;

import org.cubord.cubordbackend.domain.User;
import org.cubord.cubordbackend.domain.UserRole;
import org.cubord.cubordbackend.dto.user.UserResponse;
import org.cubord.cubordbackend.dto.user.UserUpdateRequest;
import org.cubord.cubordbackend.exception.*;
import org.cubord.cubordbackend.repository.UserRepository;
import org.cubord.cubordbackend.security.SecurityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Comprehensive test suite for UserService using the modernized security architecture.
 *
 * <p>These tests verify:</p>
 * <ul>
 *   <li>SecurityService integration for authentication context</li>
 *   <li>Authorization via @PreAuthorize (integration tests verify actual enforcement)</li>
 *   <li>Business logic correctness</li>
 *   <li>Error handling and validation</li>
 * </ul>
 *
 * <p>Note: @PreAuthorize enforcement is not tested in unit tests as it requires
 * Spring Security context. Integration tests should cover authorization scenarios.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserService Tests")
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private SecurityService securityService;

    @InjectMocks
    private UserService userService;

    private UUID sampleUserId;
    private User sampleUser;
    private LocalDateTime fixedTime;

    @BeforeEach
    void setUp() {
        sampleUserId = UUID.randomUUID();
        fixedTime = LocalDateTime.of(2024, 1, 1, 12, 0);

        sampleUser = User.builder()
                .id(sampleUserId)
                .email("test@example.com")
                .displayName("Test User")
                .username("testuser")
                .role(UserRole.USER)
                .createdAt(fixedTime)
                .updatedAt(fixedTime)
                .build();
    }

    // ==================== getCurrentUserDetails Tests ====================

    @Nested
    @DisplayName("getCurrentUserDetails")
    class GetCurrentUserDetailsTests {

        @Test
        @DisplayName("returns current user details from security context")
        void whenUserAuthenticated_returnsUserDetails() {
            // Given
            when(securityService.getCurrentUser()).thenReturn(sampleUser);

            // When
            UserResponse result = userService.getCurrentUserDetails();

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(sampleUserId);
            assertThat(result.getEmail()).isEqualTo("test@example.com");
            assertThat(result.getDisplayName()).isEqualTo("Test User");
            assertThat(result.getUsername()).isEqualTo("testuser");

            verify(securityService).getCurrentUser();
            verifyNoInteractions(userRepository);
        }

        @Test
        @DisplayName("throws AuthenticationRequiredException when not authenticated")
        void whenNotAuthenticated_throwsException() {
            // Given
            when(securityService.getCurrentUser())
                    .thenThrow(new AuthenticationRequiredException("No authenticated user found"));

            // When/Then
            assertThatThrownBy(() -> userService.getCurrentUserDetails())
                    .isInstanceOf(AuthenticationRequiredException.class)
                    .hasMessageContaining("No authenticated user found");

            verify(securityService).getCurrentUser();
        }
    }

    // ==================== getUser Tests ====================

    @Nested
    @DisplayName("getUser")
    class GetUserTests {

        @Test
        @DisplayName("returns user when found and authorized")
        void whenUserFoundAndAuthorized_returnsUser() {
            // Given
            UUID targetUserId = UUID.randomUUID();
            User targetUser = User.builder()
                    .id(targetUserId)
                    .email("target@example.com")
                    .displayName("Target User")
                    .username("targetuser")
                    .role(UserRole.USER)
                    .createdAt(fixedTime)
                    .updatedAt(fixedTime)
                    .build();

            // Note: @PreAuthorize is not enforced in unit tests
            // Authorization is mocked for business logic verification
            when(userRepository.findById(targetUserId)).thenReturn(Optional.of(targetUser));

            // When
            UserResponse result = userService.getUser(targetUserId);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(targetUserId);
            assertThat(result.getEmail()).isEqualTo("target@example.com");
            assertThat(result.getDisplayName()).isEqualTo("Target User");

            verify(userRepository).findById(targetUserId);
        }

        @Test
        @DisplayName("throws NotFoundException when user not found")
        void whenUserNotFound_throwsNotFoundException() {
            // Given
            UUID nonExistentId = UUID.randomUUID();
            when(userRepository.findById(nonExistentId)).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> userService.getUser(nonExistentId))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("User not found with ID");

            verify(userRepository).findById(nonExistentId);
        }

        @Test
        @DisplayName("throws ValidationException when ID is null")
        void whenIdIsNull_throwsValidationException() {
            // When/Then
            assertThatThrownBy(() -> userService.getUser(null))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("User ID cannot be null");

            verifyNoInteractions(userRepository);
        }
    }

    // ==================== getUserByUsername Tests ====================

    @Nested
    @DisplayName("getUserByUsername")
    class GetUserByUsernameTests {

        @Test
        @DisplayName("returns user when found and authorized")
        void whenUserFoundAndAuthorized_returnsUser() {
            // Given
            String username = "testuser";
            when(userRepository.findByUsername(username)).thenReturn(Optional.of(sampleUser));
            when(securityService.canAccessUserProfile(sampleUserId)).thenReturn(true);

            // When
            UserResponse result = userService.getUserByUsername(username);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getUsername()).isEqualTo(username);

            verify(userRepository).findByUsername(username);
            verify(securityService).canAccessUserProfile(sampleUserId);
        }

        @Test
        @DisplayName("throws InsufficientPermissionException when not authorized")
        void whenNotAuthorized_throwsInsufficientPermissionException() {
            // Given
            String username = "testuser";
            UUID currentUserId = UUID.randomUUID();

            when(userRepository.findByUsername(username)).thenReturn(Optional.of(sampleUser));
            when(securityService.canAccessUserProfile(sampleUserId)).thenReturn(false);
            when(securityService.getCurrentUserId()).thenReturn(currentUserId);

            // When/Then
            assertThatThrownBy(() -> userService.getUserByUsername(username))
                    .isInstanceOf(InsufficientPermissionException.class)
                    .hasMessageContaining("You do not have permission to access this user profile");

            verify(userRepository).findByUsername(username);
            verify(securityService).canAccessUserProfile(sampleUserId);
            verify(securityService).getCurrentUserId();
        }

        @Test
        @DisplayName("throws NotFoundException when user not found")
        void whenUserNotFound_throwsNotFoundException() {
            // Given
            String username = "nonexistent";
            when(userRepository.findByUsername(username)).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> userService.getUserByUsername(username))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("User not found with username");

            verify(userRepository).findByUsername(username);
        }

        @Test
        @DisplayName("throws ValidationException when username is null")
        void whenUsernameIsNull_throwsValidationException() {
            // When/Then
            assertThatThrownBy(() -> userService.getUserByUsername(null))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Username cannot be null or blank");

            verifyNoInteractions(userRepository);
        }

        @Test
        @DisplayName("throws ValidationException when username is blank")
        void whenUsernameIsBlank_throwsValidationException() {
            // When/Then
            assertThatThrownBy(() -> userService.getUserByUsername("   "))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Username cannot be null or blank");

            verifyNoInteractions(userRepository);
        }
    }

    // ==================== updateUser Tests ====================

    @Nested
    @DisplayName("updateUser")
    class UpdateUserTests {

        @Test
        @DisplayName("updates display name successfully")
        void whenUpdatingDisplayName_updatesSuccessfully() {
            // Given
            UserUpdateRequest request = UserUpdateRequest.builder()
                    .displayName("Updated Name")
                    .build();

            when(securityService.getCurrentUserId()).thenReturn(sampleUserId);
            when(userRepository.findById(sampleUserId)).thenReturn(Optional.of(sampleUser));
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            // When
            UserResponse result = userService.updateUser(sampleUserId, request);

            // Then
            assertThat(result.getDisplayName()).isEqualTo("Updated Name");

            verify(userRepository).findById(sampleUserId);
            verify(userRepository).save(argThat(user ->
                    user.getDisplayName().equals("Updated Name")));
        }

        @Test
        @DisplayName("updates email successfully when valid and unique")
        void whenUpdatingEmail_updatesSuccessfully() {
            // Given
            UserUpdateRequest request = UserUpdateRequest.builder()
                    .email("newemail@example.com")
                    .build();

            when(securityService.getCurrentUserId()).thenReturn(sampleUserId);
            when(userRepository.findById(sampleUserId)).thenReturn(Optional.of(sampleUser));
            when(userRepository.findByEmail("newemail@example.com")).thenReturn(Optional.empty());
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            // When
            UserResponse result = userService.updateUser(sampleUserId, request);

            // Then
            assertThat(result.getEmail()).isEqualTo("newemail@example.com");

            verify(userRepository).findById(sampleUserId);
            verify(userRepository).findByEmail("newemail@example.com");
            verify(userRepository).save(any(User.class));
        }

        @Test
        @DisplayName("throws ConflictException when email already in use")
        void whenEmailAlreadyInUse_throwsConflictException() {
            // Given
            UUID otherUserId = UUID.randomUUID();
            User otherUser = User.builder()
                    .id(otherUserId)
                    .email("taken@example.com")
                    .build();

            UserUpdateRequest request = UserUpdateRequest.builder()
                    .email("taken@example.com")
                    .build();

            when(securityService.getCurrentUserId()).thenReturn(sampleUserId);
            when(userRepository.findById(sampleUserId)).thenReturn(Optional.of(sampleUser));
            when(userRepository.findByEmail("taken@example.com")).thenReturn(Optional.of(otherUser));

            // When/Then
            assertThatThrownBy(() -> userService.updateUser(sampleUserId, request))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("Email 'taken@example.com' is already in use");

            verify(userRepository).findById(sampleUserId);
            verify(userRepository).findByEmail("taken@example.com");
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("updates username successfully when unique")
        void whenUpdatingUsername_updatesSuccessfully() {
            // Given
            UserUpdateRequest request = UserUpdateRequest.builder()
                    .username("newusername")
                    .build();

            when(securityService.getCurrentUserId()).thenReturn(sampleUserId);
            when(userRepository.findById(sampleUserId)).thenReturn(Optional.of(sampleUser));
            // FIXED: Return Optional instead of boolean
            when(userRepository.findByUsername("newusername")).thenReturn(Optional.empty());
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            // When
            UserResponse result = userService.updateUser(sampleUserId, request);

            // Then
            assertThat(result.getUsername()).isEqualTo("newusername");

            verify(userRepository).findByUsername("newusername");
            verify(userRepository).save(any(User.class));
        }

        @Test
        @DisplayName("throws ConflictException when username already in use")
        void whenUsernameAlreadyInUse_throwsConflictException() {
            // Given
            User existingUser = User.builder()
                    .id(UUID.randomUUID())
                    .username("takenusername")
                    .build();
                
            UserUpdateRequest request = UserUpdateRequest.builder()
                    .username("takenusername")
                    .build();

            when(securityService.getCurrentUserId()).thenReturn(sampleUserId);
            when(userRepository.findById(sampleUserId)).thenReturn(Optional.of(sampleUser));
            // FIXED: Return Optional instead of boolean
            when(userRepository.findByUsername("takenusername")).thenReturn(Optional.of(existingUser));

            // When/Then
            assertThatThrownBy(() -> userService.updateUser(sampleUserId, request))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("Username 'takenusername' is already in use");

            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("throws ValidationException when request is null")
        void whenRequestIsNull_throwsValidationException() {
            // When/Then
            assertThatThrownBy(() -> userService.updateUser(sampleUserId, null))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Update request cannot be null");

            verifyNoInteractions(userRepository);
        }

        @Test
        @DisplayName("throws ValidationException when user ID is null")
        void whenUserIdIsNull_throwsValidationException() {
            // Given
            UserUpdateRequest request = UserUpdateRequest.builder().build();

            // When/Then
            assertThatThrownBy(() -> userService.updateUser(null, request))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("User ID cannot be null");

            verifyNoInteractions(userRepository);
        }

        @Test
        @DisplayName("throws NotFoundException when user not found")
        void whenUserNotFound_throwsNotFoundException() {
            // Given
            UUID nonExistentId = UUID.randomUUID();
            UserUpdateRequest request = UserUpdateRequest.builder()
                    .displayName("New Name")
                    .build();

            when(userRepository.findById(nonExistentId)).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> userService.updateUser(nonExistentId, request))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("User not found with ID");

            verify(userRepository).findById(nonExistentId);
            verify(userRepository, never()).save(any());
        }
    }

    // ==================== patchUser Tests ====================

    @Nested
    @DisplayName("patchUser")
    class PatchUserTests {

        @Test
        @DisplayName("patches display name successfully")
        void whenPatchingDisplayName_updatesSuccessfully() {
            // Given
            Map<String, Object> patchData = new HashMap<>();
            patchData.put("displayName", "Patched Name");

            when(securityService.getCurrentUserId()).thenReturn(sampleUserId);
            when(userRepository.findById(sampleUserId)).thenReturn(Optional.of(sampleUser));
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            // When
            UserResponse result = userService.patchUser(sampleUserId, patchData);

            // Then
            assertThat(result.getDisplayName()).isEqualTo("Patched Name");

            verify(userRepository).save(argThat(user ->
                    user.getDisplayName().equals("Patched Name")));
        }

        @Test
        @DisplayName("patches email successfully")
        void whenPatchingEmail_updatesSuccessfully() {
            // Given
            Map<String, Object> patchData = new HashMap<>();
            patchData.put("email", "patched@example.com");

            when(securityService.getCurrentUserId()).thenReturn(sampleUserId);
            when(userRepository.findById(sampleUserId)).thenReturn(Optional.of(sampleUser));
            when(userRepository.findByEmail("patched@example.com")).thenReturn(Optional.empty());
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            // When
            UserResponse result = userService.patchUser(sampleUserId, patchData);

            // Then
            assertThat(result.getEmail()).isEqualTo("patched@example.com");

            verify(userRepository).findByEmail("patched@example.com");
            verify(userRepository).save(any(User.class));
        }

        @Test
        @DisplayName("patches username successfully")
        void whenPatchingUsername_updatesSuccessfully() {
            // Given
            Map<String, Object> patchData = new HashMap<>();
            patchData.put("username", "patcheduser");

            when(securityService.getCurrentUserId()).thenReturn(sampleUserId);
            when(userRepository.findById(sampleUserId)).thenReturn(Optional.of(sampleUser));
            // FIXED: Return Optional instead of boolean
            when(userRepository.findByUsername("patcheduser")).thenReturn(Optional.empty());
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            // When
            UserResponse result = userService.patchUser(sampleUserId, patchData);

            // Then
            assertThat(result.getUsername()).isEqualTo("patcheduser");

            verify(userRepository).findByUsername("patcheduser");
            verify(userRepository).save(any(User.class));
        }

        @Test
        @DisplayName("patches multiple fields successfully")
        void whenPatchingMultipleFields_updatesSuccessfully() {
            // Given
            Map<String, Object> patchData = new HashMap<>();
            patchData.put("displayName", "New Name");
            patchData.put("username", "newusername");

            when(securityService.getCurrentUserId()).thenReturn(sampleUserId);
            when(userRepository.findById(sampleUserId)).thenReturn(Optional.of(sampleUser));
            when(userRepository.findByUsername("newusername")).thenReturn(Optional.empty());
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            // When
            UserResponse result = userService.patchUser(sampleUserId, patchData);

            // Then
            assertThat(result.getDisplayName()).isEqualTo("New Name");
            assertThat(result.getUsername()).isEqualTo("newusername");

            verify(userRepository).save(any(User.class));
        }

        @Test
        @DisplayName("throws ValidationException for unsupported field")
        void whenPatchingUnsupportedField_throwsValidationException() {
            // Given
            Map<String, Object> patchData = new HashMap<>();
            patchData.put("unsupportedField", "value");

            when(securityService.getCurrentUserId()).thenReturn(sampleUserId);
            when(userRepository.findById(sampleUserId)).thenReturn(Optional.of(sampleUser));

            // When/Then
            assertThatThrownBy(() -> userService.patchUser(sampleUserId, patchData))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Unsupported field for patching: unsupportedField");

            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("throws ValidationException when patch data is null")
        void whenPatchDataIsNull_throwsValidationException() {
            // When/Then
            assertThatThrownBy(() -> userService.patchUser(sampleUserId, null))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Patch data cannot be null or empty");

            verifyNoInteractions(userRepository);
        }

        @Test
        @DisplayName("throws ValidationException when patch data is empty")
        void whenPatchDataIsEmpty_throwsValidationException() {
            // When/Then
            assertThatThrownBy(() -> userService.patchUser(sampleUserId, new HashMap<>()))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Patch data cannot be null or empty");

            verifyNoInteractions(userRepository);
        }
    }

    // ==================== deleteUser Tests ====================

    @Nested
    @DisplayName("deleteUser")
    class DeleteUserTests {

        @Test
        @DisplayName("deletes user successfully")
        void whenDeletingUser_deletesSuccessfully() {
            // Given
            when(securityService.getCurrentUserId()).thenReturn(sampleUserId);
            when(userRepository.findById(sampleUserId)).thenReturn(Optional.of(sampleUser));
            doNothing().when(userRepository).delete(any(User.class));

            // When
            userService.deleteUser(sampleUserId);

            // Then
            verify(userRepository).findById(sampleUserId);
            verify(userRepository).delete(sampleUser);
        }

        @Test
        @DisplayName("throws NotFoundException when user not found")
        void whenUserNotFound_throwsNotFoundException() {
            // Given
            UUID nonExistentId = UUID.randomUUID();
            when(userRepository.findById(nonExistentId)).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> userService.deleteUser(nonExistentId))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("User not found with ID");

            verify(userRepository).findById(nonExistentId);
            verify(userRepository, never()).delete(any());
        }

        @Test
        @DisplayName("throws DataIntegrityException when deletion fails")
        void whenDeletionFails_throwsDataIntegrityException() {
            // Given
            when(securityService.getCurrentUserId()).thenReturn(sampleUserId);
            when(userRepository.findById(sampleUserId)).thenReturn(Optional.of(sampleUser));
            doThrow(new RuntimeException("Foreign key constraint"))
                    .when(userRepository).delete(any(User.class));

            // When/Then
            assertThatThrownBy(() -> userService.deleteUser(sampleUserId))
                    .isInstanceOf(DataIntegrityException.class)
                    .hasMessageContaining("Failed to delete user");

            verify(userRepository).delete(sampleUser);
        }

        @Test
        @DisplayName("throws ValidationException when ID is null")
        void whenIdIsNull_throwsValidationException() {
            // When/Then
            assertThatThrownBy(() -> userService.deleteUser(null))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("User ID cannot be null");

            verifyNoInteractions(userRepository);
        }
    }
}