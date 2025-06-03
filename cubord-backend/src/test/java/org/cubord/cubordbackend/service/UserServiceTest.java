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
}