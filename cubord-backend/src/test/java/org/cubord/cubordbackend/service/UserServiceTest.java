package org.cubord.cubordbackend.service;

import org.cubord.cubordbackend.domain.User;
import org.cubord.cubordbackend.dto.user.UserResponse;
import org.cubord.cubordbackend.dto.user.UserUpdateRequest;
import org.cubord.cubordbackend.exception.AuthenticationRequiredException;
import org.cubord.cubordbackend.exception.ConflictException;
import org.cubord.cubordbackend.exception.DataIntegrityException;
import org.cubord.cubordbackend.exception.InsufficientPermissionException;
import org.cubord.cubordbackend.exception.NotFoundException;
import org.cubord.cubordbackend.exception.TokenExpiredException;
import org.cubord.cubordbackend.exception.ValidationException;
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
import java.util.Map;
import java.util.HashMap;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

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
        @DisplayName("should throw AuthenticationRequiredException when token is null")
        void shouldThrowAuthenticationRequiredExceptionWhenTokenIsNull() {
            assertThatThrownBy(() -> userService.getCurrentUser(null))
                    .isInstanceOf(AuthenticationRequiredException.class)
                    .hasMessage("JWT token is required");

            verify(userRepository, never()).findById(any());
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw AuthenticationRequiredException when token has null JWT")
        void shouldThrowAuthenticationRequiredExceptionWhenTokenHasNullJwt() {

            JwtAuthenticationToken mockToken = mock(JwtAuthenticationToken.class);
            when(mockToken.getToken()).thenReturn(null);

            assertThatThrownBy(() -> userService.getCurrentUser(mockToken))
                    .isInstanceOf(AuthenticationRequiredException.class)
                    .hasMessage("JWT token is required");

            verify(userRepository, never()).findById(any());
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw AuthenticationRequiredException when JWT token has no subject")
        void shouldThrowAuthenticationRequiredExceptionWhenTokenHasNoSubject() {
            Jwt invalidJwt = Jwt.withTokenValue("token")
                    .header("alg", "none")
                    .claim("email", "test@example.com")
                    .build();
            JwtAuthenticationToken invalidToken = new JwtAuthenticationToken(invalidJwt);

            assertThatThrownBy(() -> userService.getCurrentUser(invalidToken))
                    .isInstanceOf(AuthenticationRequiredException.class)
                    .hasMessage("JWT token does not contain a subject claim");

            verify(userRepository, never()).findById(any());
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw TokenExpiredException when subject is not a valid UUID")
        void shouldThrowTokenExpiredExceptionWhenSubjectIsInvalidUuid() {
            Jwt invalidJwt = Jwt.withTokenValue("token")
                    .header("alg", "none")
                    .subject("invalid-uuid")
                    .claim("email", "test@example.com")
                    .build();
            JwtAuthenticationToken invalidToken = new JwtAuthenticationToken(invalidJwt);

            assertThatThrownBy(() -> userService.getCurrentUser(invalidToken))
                    .isInstanceOf(TokenExpiredException.class)
                    .hasMessage("Invalid token format: subject is not a valid UUID");

            verify(userRepository, never()).findById(any());
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw DataIntegrityException when user creation fails")
        void shouldThrowDataIntegrityExceptionWhenUserCreationFails() {
            when(userRepository.findById(eq(sampleUserId)))
                    .thenReturn(Optional.empty());
            when(userRepository.save(any(User.class)))
                    .thenThrow(new RuntimeException("Database connection failed"));

            assertThatThrownBy(() -> userService.getCurrentUser(jwtAuthenticationToken))
                    .isInstanceOf(DataIntegrityException.class)
                    .hasMessageContaining("Failed to create user");

            verify(userRepository).findById(eq(sampleUserId));
            verify(userRepository).save(any(User.class));
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
        @DisplayName("should throw ValidationException when user ID is null")
        void shouldThrowValidationExceptionWhenUserIdIsNull() {
            assertThatThrownBy(() -> userService.getUser(null))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("User ID cannot be null");

            verify(userRepository, never()).findById(any());
        }

        @Test
        @DisplayName("should throw NotFoundException when user not found by ID")
        void shouldThrowNotFoundExceptionWhenUserNotFoundById() {
            when(userRepository.findById(eq(sampleUserId)))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.getUser(sampleUserId))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("User with id " + sampleUserId + " not found");

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
        @DisplayName("should throw ValidationException when username is null")
        void shouldThrowValidationExceptionWhenUsernameIsNull() {
            assertThatThrownBy(() -> userService.getUserByUsername(null))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Username cannot be null or blank");

            verify(userRepository, never()).findByUsername(any());
        }

        @Test
        @DisplayName("should throw ValidationException when username is blank")
        void shouldThrowValidationExceptionWhenUsernameIsBlank() {
            assertThatThrownBy(() -> userService.getUserByUsername("   "))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Username cannot be null or blank");

            verify(userRepository, never()).findByUsername(any());
        }

        @Test
        @DisplayName("should throw NotFoundException when user not found by username")
        void shouldThrowNotFoundExceptionWhenUserNotFoundByUsername() {
            when(userRepository.findByUsername(eq(sampleUsername)))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.getUserByUsername(sampleUsername))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("User with username '" + sampleUsername + "' not found");

            verify(userRepository).findByUsername(eq(sampleUsername));
        }
    }

    @Nested
    @DisplayName("extractUsernameFromEmail method")
    class ExtractUsernameFromEmail {

        @Test
        @DisplayName("should extract username part before @ symbol")
        void shouldExtractUsernameFromEmail() throws Exception {
            java.lang.reflect.Method method = UserService.class.getDeclaredMethod("extractUsernameFromEmail", String.class);
            method.setAccessible(true);

            String result = (String) method.invoke(userService, "user123@example.com");
            assertThat(result).isEqualTo("user123");

            String resultWithNullEmail = (String) method.invoke(userService, (String) null);
            assertThat(resultWithNullEmail).isNull();
        }

        @Test
        @DisplayName("should throw ValidationException when email without @ symbol")
        void shouldThrowValidationExceptionWhenEmailWithoutAtSymbol() throws Exception {
            java.lang.reflect.Method method = UserService.class.getDeclaredMethod("extractUsernameFromEmail", String.class);
            method.setAccessible(true);

            assertThatThrownBy(() -> method.invoke(userService, "invalid-email"))
                    .hasCauseInstanceOf(ValidationException.class)
                    .hasRootCauseMessage("Invalid email format: missing @ symbol");
        }

        @Test
        @DisplayName("should throw ValidationException when email has empty username part")
        void shouldThrowValidationExceptionWhenEmailHasEmptyUsernamePart() throws Exception {
            java.lang.reflect.Method method = UserService.class.getDeclaredMethod("extractUsernameFromEmail", String.class);
            method.setAccessible(true);

            assertThatThrownBy(() -> method.invoke(userService, "@example.com"))
                    .hasCauseInstanceOf(ValidationException.class)
                    .hasRootCauseMessage("Invalid email format: empty username part");
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

            assertThat(result.getHouseholdMembers()).isNotNull().isEmpty();

            verify(userRepository).save(any(User.class));
        }
    }

    @Nested
    @DisplayName("updateUser method")
    class UpdateUser {

        @Test
        @DisplayName("should update user when valid data provided")
        void shouldUpdateUserWhenValidDataProvided() {
            UserUpdateRequest updateRequest = new UserUpdateRequest();
            updateRequest.setDisplayName("Updated Name");

            User updatedUser = new User();
            updatedUser.setId(sampleUserId);
            updatedUser.setUsername(sampleUsername);
            updatedUser.setEmail("test@example.com");
            updatedUser.setDisplayName("Updated Name");
            updatedUser.setCreatedAt(LocalDateTime.now().minusDays(7));
            updatedUser.setHouseholdMembers(new HashSet<>());

            when(userRepository.findById(eq(sampleUserId)))
                    .thenReturn(Optional.of(sampleUser));
            when(userRepository.save(any(User.class)))
                    .thenReturn(updatedUser);

            UserResponse result = userService.updateUser(sampleUserId, updateRequest);

            assertThat(result.getId()).isEqualTo(sampleUserId);
            assertThat(result.getDisplayName()).isEqualTo("Updated Name");

            verify(userRepository).findById(eq(sampleUserId));
            verify(userRepository).save(any(User.class));
        }

        @Test
        @DisplayName("should throw ValidationException when user ID is null")
        void shouldThrowValidationExceptionWhenUserIdIsNull() {
            UserUpdateRequest updateRequest = new UserUpdateRequest();
            updateRequest.setDisplayName("Updated Name");

            assertThatThrownBy(() -> userService.updateUser(null, updateRequest))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("User ID cannot be null");

            verify(userRepository, never()).findById(any());
            verify(userRepository, never()).save(any(User.class));
        }

        @Test
        @DisplayName("should throw ValidationException when update request is null")
        void shouldThrowValidationExceptionWhenUpdateRequestIsNull() {
            assertThatThrownBy(() -> userService.updateUser(sampleUserId, null))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Update request cannot be null");

            verify(userRepository, never()).findById(any());
            verify(userRepository, never()).save(any(User.class));
        }

        @Test
        @DisplayName("should throw ValidationException when display name is too short")
        void shouldThrowValidationExceptionWhenDisplayNameTooShort() {
            UserUpdateRequest updateRequest = new UserUpdateRequest();
            updateRequest.setDisplayName("A");

            when(userRepository.findById(eq(sampleUserId)))
                    .thenReturn(Optional.of(sampleUser));

            assertThatThrownBy(() -> userService.updateUser(sampleUserId, updateRequest))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Display name must be between 2 and 50 characters");
        }

        @Test
        @DisplayName("should throw ValidationException when display name is too long")
        void shouldThrowValidationExceptionWhenDisplayNameTooLong() {
            UserUpdateRequest updateRequest = new UserUpdateRequest();
            updateRequest.setDisplayName("A".repeat(51));

            when(userRepository.findById(eq(sampleUserId)))
                    .thenReturn(Optional.of(sampleUser));

            assertThatThrownBy(() -> userService.updateUser(sampleUserId, updateRequest))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Display name must be between 2 and 50 characters");
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
                    .hasMessageContaining("User with id " + sampleUserId + " not found");

            verify(userRepository).findById(eq(sampleUserId));
            verify(userRepository, never()).save(any(User.class));
        }

        @Test
        @DisplayName("should throw ConflictException when email is already in use")
        void shouldThrowConflictExceptionWhenEmailAlreadyInUse() {
            UserUpdateRequest updateRequest = new UserUpdateRequest();
            updateRequest.setEmail("existing@example.com");

            User existingUserWithEmail = new User();
            existingUserWithEmail.setId(UUID.randomUUID());
            existingUserWithEmail.setEmail("existing@example.com");

            when(userRepository.findById(eq(sampleUserId)))
                    .thenReturn(Optional.of(sampleUser));
            when(userRepository.findByEmail("existing@example.com"))
                    .thenReturn(Optional.of(existingUserWithEmail));

            assertThatThrownBy(() -> userService.updateUser(sampleUserId, updateRequest))
                    .isInstanceOf(ConflictException.class)
                    .hasMessage("Email address is already in use by another user");
        }

        @Test
        @DisplayName("should throw ValidationException when email format is invalid")
        void shouldThrowValidationExceptionWhenEmailFormatInvalid() {
            UserUpdateRequest updateRequest = new UserUpdateRequest();
            updateRequest.setEmail("invalid-email");

            when(userRepository.findById(eq(sampleUserId)))
                    .thenReturn(Optional.of(sampleUser));

            assertThatThrownBy(() -> userService.updateUser(sampleUserId, updateRequest))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Invalid email format: must be a valid email address");
        }

        @Test
        @DisplayName("should throw DataIntegrityException when save operation fails")
        void shouldThrowDataIntegrityExceptionWhenSaveOperationFails() {
            UserUpdateRequest updateRequest = new UserUpdateRequest();
            updateRequest.setDisplayName("Updated Name");

            when(userRepository.findById(eq(sampleUserId)))
                    .thenReturn(Optional.of(sampleUser));
            when(userRepository.save(any(User.class)))
                    .thenThrow(new RuntimeException("Database constraint violation"));

            assertThatThrownBy(() -> userService.updateUser(sampleUserId, updateRequest))
                    .isInstanceOf(DataIntegrityException.class)
                    .hasMessageContaining("Failed to update user");
        }
    }

    @Nested
    @DisplayName("patchUser method")
    class PatchUser {

        @Test
        @DisplayName("should partially update user when valid data provided")
        void shouldPartiallyUpdateUserWhenValidDataProvided() {
            Map<String, Object> patchData = new HashMap<>();
            patchData.put("displayName", "Patched Name");

            User patchedUser = new User();
            patchedUser.setId(sampleUserId);
            patchedUser.setUsername(sampleUsername);
            patchedUser.setEmail("test@example.com");
            patchedUser.setDisplayName("Patched Name");
            patchedUser.setCreatedAt(LocalDateTime.now().minusDays(7));
            patchedUser.setHouseholdMembers(new HashSet<>());

            when(userRepository.findById(eq(sampleUserId)))
                    .thenReturn(Optional.of(sampleUser));
            when(userRepository.save(any(User.class)))
                    .thenReturn(patchedUser);

            UserResponse result = userService.patchUser(sampleUserId, patchData);

            assertThat(result.getDisplayName()).isEqualTo("Patched Name");
            assertThat(result.getEmail()).isEqualTo("test@example.com");

            verify(userRepository).findById(eq(sampleUserId));
            verify(userRepository).save(any(User.class));
        }

        @Test
        @DisplayName("should throw ValidationException when user ID is null")
        void shouldThrowValidationExceptionWhenUserIdIsNull() {
            Map<String, Object> patchData = new HashMap<>();
            patchData.put("displayName", "Patched Name");

            assertThatThrownBy(() -> userService.patchUser(null, patchData))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("User ID cannot be null");

            verify(userRepository, never()).findById(any());
            verify(userRepository, never()).save(any(User.class));
        }

        @Test
        @DisplayName("should throw ValidationException when patch data is null")
        void shouldThrowValidationExceptionWhenPatchDataIsNull() {
            assertThatThrownBy(() -> userService.patchUser(sampleUserId, null))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Patch data cannot be null or empty");

            verify(userRepository, never()).findById(any());
            verify(userRepository, never()).save(any(User.class));
        }

        @Test
        @DisplayName("should throw ValidationException when patch data is empty")
        void shouldThrowValidationExceptionWhenPatchDataIsEmpty() {
            Map<String, Object> emptyPatchData = new HashMap<>();

            assertThatThrownBy(() -> userService.patchUser(sampleUserId, emptyPatchData))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Patch data cannot be null or empty");

            verify(userRepository, never()).findById(any());
            verify(userRepository, never()).save(any(User.class));
        }

        @Test
        @DisplayName("should throw ValidationException when display name is not a string")
        void shouldThrowValidationExceptionWhenDisplayNameIsNotString() {
            Map<String, Object> patchData = new HashMap<>();
            patchData.put("displayName", 123);

            when(userRepository.findById(eq(sampleUserId)))
                    .thenReturn(Optional.of(sampleUser));

            assertThatThrownBy(() -> userService.patchUser(sampleUserId, patchData))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Display name must be a string");
        }

        @Test
        @DisplayName("should throw ValidationException when email is not a string")
        void shouldThrowValidationExceptionWhenEmailIsNotString() {
            Map<String, Object> patchData = new HashMap<>();
            patchData.put("email", 123);

            when(userRepository.findById(eq(sampleUserId)))
                    .thenReturn(Optional.of(sampleUser));

            assertThatThrownBy(() -> userService.patchUser(sampleUserId, patchData))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Email must be a string");
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
                    .hasMessageContaining("User with id " + sampleUserId + " not found");

            verify(userRepository).findById(eq(sampleUserId));
            verify(userRepository, never()).save(any(User.class));
        }

        @Test
        @DisplayName("should ignore invalid fields in patch data")
        void shouldIgnoreInvalidFieldsInPatchData() {
            Map<String, Object> patchData = new HashMap<>();
            patchData.put("displayName", "Patched Name");
            patchData.put("invalidField", "value");

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

        @Test
        @DisplayName("should ignore username changes")
        void shouldIgnoreUsernameChanges() {
            Map<String, Object> patchData = new HashMap<>();
            patchData.put("username", "newusername");

            when(userRepository.findById(eq(sampleUserId)))
                    .thenReturn(Optional.of(sampleUser));
            when(userRepository.save(any(User.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            UserResponse result = userService.patchUser(sampleUserId, patchData);

            // Username should remain unchanged
            assertThat(result.getUsername()).isEqualTo(sampleUsername);

            verify(userRepository).save(any(User.class));
        }

        @Test
        @DisplayName("should throw DataIntegrityException when save operation fails")
        void shouldThrowDataIntegrityExceptionWhenSaveOperationFails() {
            Map<String, Object> patchData = new HashMap<>();
            patchData.put("displayName", "Patched Name");

            when(userRepository.findById(eq(sampleUserId)))
                    .thenReturn(Optional.of(sampleUser));
            when(userRepository.save(any(User.class)))
                    .thenThrow(new RuntimeException("Database constraint violation"));

            assertThatThrownBy(() -> userService.patchUser(sampleUserId, patchData))
                    .isInstanceOf(DataIntegrityException.class)
                    .hasMessageContaining("Failed to update user");
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
        @DisplayName("should throw ValidationException when user ID is null")
        void shouldThrowValidationExceptionWhenUserIdIsNull() {
            assertThatThrownBy(() -> userService.deleteUser(null))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("User ID cannot be null");

            verify(userRepository, never()).findById(any());
            verify(userRepository, never()).delete(any(User.class));
        }

        @Test
        @DisplayName("should throw NotFoundException when user not found for deletion")
        void shouldThrowNotFoundExceptionWhenUserNotFoundForDeletion() {
            when(userRepository.findById(eq(sampleUserId)))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.deleteUser(sampleUserId))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("User with id " + sampleUserId + " not found");

            verify(userRepository).findById(eq(sampleUserId));
            verify(userRepository, never()).delete(any(User.class));
        }

        @Test
        @DisplayName("should throw DataIntegrityException when deletion fails")
        void shouldThrowDataIntegrityExceptionWhenDeletionFails() {
            when(userRepository.findById(eq(sampleUserId)))
                    .thenReturn(Optional.of(sampleUser));
            doThrow(new RuntimeException("Foreign key constraint violation"))
                    .when(userRepository).delete(any(User.class));

            assertThatThrownBy(() -> userService.deleteUser(sampleUserId))
                    .isInstanceOf(DataIntegrityException.class)
                    .hasMessageContaining("Failed to delete user");

            verify(userRepository).findById(eq(sampleUserId));
            verify(userRepository).delete(eq(sampleUser));
        }

        @Test
        @DisplayName("should handle cascading deletions of associated entities")
        void shouldHandleCascadingDeletionsOfAssociatedEntities() {
            User userWithMembers = new User();
            userWithMembers.setId(sampleUserId);
            userWithMembers.setUsername(sampleUsername);
            userWithMembers.setEmail("test@example.com");
            userWithMembers.setDisplayName("Test User");

            HouseholdMember member = mock(HouseholdMember.class);
            Set<HouseholdMember> members = new HashSet<>();
            members.add(member);
            userWithMembers.setHouseholdMembers(members);

            when(userRepository.findById(eq(sampleUserId)))
                    .thenReturn(Optional.of(userWithMembers));
            doNothing().when(userRepository).delete(any(User.class));

            userService.deleteUser(sampleUserId);

            verify(userRepository).delete(eq(userWithMembers));
        }
    }

    @Nested
    @DisplayName("deleteUser with authorization method")
    class DeleteUserWithAuthorization {

        @Test
        @DisplayName("should delete user when authorized")
        void shouldDeleteUserWhenAuthorized() {
            when(userRepository.findById(eq(sampleUserId)))
                    .thenReturn(Optional.of(sampleUser));
            doNothing().when(userRepository).delete(any(User.class));

            userService.deleteUser(sampleUserId, sampleUserId);

            verify(userRepository).findById(eq(sampleUserId));
            verify(userRepository).delete(eq(sampleUser));
        }

        @Test
        @DisplayName("should throw ValidationException when user ID is null")
        void shouldThrowValidationExceptionWhenUserIdIsNull() {
            UUID currentUserId = UUID.randomUUID();

            assertThatThrownBy(() -> userService.deleteUser(null, currentUserId))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("User ID cannot be null");

            verify(userRepository, never()).findById(any());
            verify(userRepository, never()).delete(any(User.class));
        }

        @Test
        @DisplayName("should throw ValidationException when current user ID is null")
        void shouldThrowValidationExceptionWhenCurrentUserIdIsNull() {
            assertThatThrownBy(() -> userService.deleteUser(sampleUserId, null))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Current user ID cannot be null");

            verify(userRepository, never()).findById(any());
            verify(userRepository, never()).delete(any(User.class));
        }

        @Test
        @DisplayName("should throw InsufficientPermissionException when trying to delete another user")
        void shouldThrowInsufficientPermissionExceptionWhenTryingToDeleteAnotherUser() {
            UUID differentUserId = UUID.randomUUID();

            assertThatThrownBy(() -> userService.deleteUser(sampleUserId, differentUserId))
                    .isInstanceOf(InsufficientPermissionException.class)
                    .hasMessageContaining("Insufficient permission to perform 'delete' on user account");

            verify(userRepository, never()).findById(any());
            verify(userRepository, never()).delete(any(User.class));
        }
    }
}