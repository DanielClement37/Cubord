// src/test/java/org/cubord/cubordbackend/security/SecurityServiceTest.java
package org.cubord.cubordbackend.security;

import org.cubord.cubordbackend.domain.Household;
import org.cubord.cubordbackend.domain.HouseholdMember;
import org.cubord.cubordbackend.domain.HouseholdRole;
import org.cubord.cubordbackend.domain.User;
import org.cubord.cubordbackend.exception.AuthenticationRequiredException;
import org.cubord.cubordbackend.repository.HouseholdMemberRepository;
import org.cubord.cubordbackend.repository.UserRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SecurityService Tests")
class SecurityServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private HouseholdMemberRepository householdMemberRepository;

    @Mock
    private SecurityContextProvider securityContextProvider;

    @InjectMocks
    private SecurityService securityService;

    private UUID testUserId;
    private UUID testHouseholdId;
    private User testUser;
    private Household testHousehold;

    @BeforeEach
    void setUp() {
        testUserId = UUID.randomUUID();
        testHouseholdId = UUID.randomUUID();
        
        testUser = User.builder()
                .id(testUserId)
                .email("test@example.com")
                .displayName("Test User")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        
        testHousehold = Household.builder()
                .id(testHouseholdId)
                .name("Test Household")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    // ==================== Test Utilities ====================

    private void mockAuthenticatedUser(UUID userId) {
        JwtAuthenticationToken auth = TestSecurityUtils.createJwtAuth(userId);
        when(securityContextProvider.getAuthentication()).thenReturn(auth);
    }

    private void mockAuthenticatedUser(UUID userId, String email, String name) {
        JwtAuthenticationToken auth = TestSecurityUtils.createJwtAuth(userId, email, name);
        when(securityContextProvider.getAuthentication()).thenReturn(auth);
    }

    private void mockUnauthenticated() {
        when(securityContextProvider.getAuthentication()).thenReturn(null);
    }

    private HouseholdMember createMember(UUID userId, UUID householdId, HouseholdRole role) {
        return HouseholdMember.builder()
                .id(UUID.randomUUID())
                .user(User.builder().id(userId).build())
                .household(Household.builder().id(householdId).build())
                .role(role)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    // ==================== Authentication Tests ====================

    @Nested
    @DisplayName("getCurrentUserId")
    class GetCurrentUserIdTests {

        @Test
        @DisplayName("returns user ID when authenticated")
        void whenAuthenticated_returnsUserId() {
            // Given
            mockAuthenticatedUser(testUserId);

            // When
            UUID result = securityService.getCurrentUserId();

            // Then
            assertThat(result).isEqualTo(testUserId);
            verify(securityContextProvider).getAuthentication();
        }

        @Test
        @DisplayName("throws exception when not authenticated")
        void whenNotAuthenticated_throwsException() {
            // Given
            mockUnauthenticated();

            // When/Then
            assertThatThrownBy(() -> securityService.getCurrentUserId())
                    .isInstanceOf(AuthenticationRequiredException.class)
                    .hasMessageContaining("No authenticated user found");
        }

        @Test
        @DisplayName("throws exception when JWT subject is invalid UUID")
        void whenInvalidUuid_throwsException() {
            // Given
            Jwt jwt = org.springframework.security.oauth2.jwt.Jwt.withTokenValue("test")
                    .header("alg", "HS256")
                    .subject("not-a-uuid")
                    .build();
            
            // Create an authenticated token with authorities (3-argument constructor)
            JwtAuthenticationToken auth = new JwtAuthenticationToken(
                    jwt, 
                    java.util.Collections.singletonList(
                            new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER")
                    )
            );
            
            when(securityContextProvider.getAuthentication()).thenReturn(auth);

            // When/Then
            assertThatThrownBy(() -> securityService.getCurrentUserId())
                    .isInstanceOf(AuthenticationRequiredException.class)
                    .hasMessageContaining("Invalid user identifier");
        }

        @Test
        @DisplayName("throws exception when JWT subject is blank")
        void whenBlankSubject_throwsException() {
            // Given
            Jwt jwt = org.springframework.security.oauth2.jwt.Jwt.withTokenValue("test")
                    .header("alg", "HS256")
                    .subject("   ")
                    .build();
            
            // Create an authenticated token with authorities (3-argument constructor)
            JwtAuthenticationToken auth = new JwtAuthenticationToken(
                    jwt, 
                    java.util.Collections.singletonList(
                            new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER")
                    )
            );
            
            when(securityContextProvider.getAuthentication()).thenReturn(auth);

            // When/Then
            assertThatThrownBy(() -> securityService.getCurrentUserId())
                    .isInstanceOf(AuthenticationRequiredException.class)
                    .hasMessageContaining("JWT token missing subject claim");
        }
    }

    @Nested
    @DisplayName("getCurrentUserIdIfPresent")
    class GetCurrentUserIdIfPresentTests {

        @Test
        @DisplayName("returns Optional with ID when authenticated")
        void whenAuthenticated_returnsOptionalWithId() {
            // Given
            mockAuthenticatedUser(testUserId);

            // When
            Optional<UUID> result = securityService.getCurrentUserIdIfPresent();

            // Then
            assertThat(result).contains(testUserId);
        }

        @Test
        @DisplayName("returns empty Optional when not authenticated")
        void whenNotAuthenticated_returnsEmpty() {
            // Given
            mockUnauthenticated();

            // When
            Optional<UUID> result = securityService.getCurrentUserIdIfPresent();

            // Then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("returns empty Optional when authentication is invalid")
        void whenInvalidAuth_returnsEmpty() {
            // Given
            when(securityContextProvider.getAuthentication()).thenReturn(
                    new JwtAuthenticationToken(
                            org.springframework.security.oauth2.jwt.Jwt.withTokenValue("test")
                                    .header("alg", "HS256")
                                    .subject("invalid-uuid")
                                    .build()
                    )
            );

            // When
            Optional<UUID> result = securityService.getCurrentUserIdIfPresent();

            // Then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("getCurrentUser")
    class GetCurrentUserTests {

        @Test
        @DisplayName("returns existing user when found")
        void whenUserExists_returnsUser() {
            // Given
            mockAuthenticatedUser(testUserId);
            when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));

            // When
            User result = securityService.getCurrentUser();

            // Then
            assertThat(result).isEqualTo(testUser);
            verify(userRepository).findById(testUserId);
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("creates new user when not found")
        void whenUserNotFound_createsUser() {
            // Given
            String email = "newuser@example.com";
            String name = "New User";
            mockAuthenticatedUser(testUserId, email, name);
            when(userRepository.findById(testUserId)).thenReturn(Optional.empty());
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            // When
            User result = securityService.getCurrentUser();

            // Then
            assertThat(result.getId()).isEqualTo(testUserId);
            assertThat(result.getEmail()).isEqualTo(email);
            assertThat(result.getDisplayName()).isEqualTo(name);
            verify(userRepository).save(argThat(user ->
                    user.getId().equals(testUserId) &&
                    user.getEmail().equals(email) &&
                    user.getDisplayName().equals(name)
            ));
        }

        @Test
        @DisplayName("creates user with default email when email claim missing")
        void whenEmailMissing_createsUserWithDefaultEmail() {
            // Given
            mockAuthenticatedUser(testUserId, null, "Test User");
            when(userRepository.findById(testUserId)).thenReturn(Optional.empty());
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            // When
            User result = securityService.getCurrentUser();

            // Then
            assertThat(result.getEmail()).isEqualTo(testUserId + "@unknown.com");
        }

        @Test
        @DisplayName("creates user with default name when name claim missing")
        void whenNameMissing_createsUserWithDefaultName() {
            // Given
            mockAuthenticatedUser(testUserId, "test@example.com", null);
            when(userRepository.findById(testUserId)).thenReturn(Optional.empty());
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            // When
            User result = securityService.getCurrentUser();

            // Then
            assertThat(result.getDisplayName()).isEqualTo("User");
        }
    }

    @Nested
    @DisplayName("isAuthenticated")
    class IsAuthenticatedTests {

        @Test
        @DisplayName("returns true when JWT authentication present")
        void whenJwtAuth_returnsTrue() {
            // Given
            mockAuthenticatedUser(testUserId);

            // When
            boolean result = securityService.isAuthenticated();

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("returns false when no authentication")
        void whenNoAuth_returnsFalse() {
            // Given
            mockUnauthenticated();

            // When
            boolean result = securityService.isAuthenticated();

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("returns false when authentication is not JWT")
        void whenNonJwtAuth_returnsFalse() {
            // Given
            when(securityContextProvider.getAuthentication()).thenReturn(
                    new org.springframework.security.authentication.TestingAuthenticationToken(
                            "user", "password"
                    )
            );

            // When
            boolean result = securityService.isAuthenticated();

            // Then
            assertThat(result).isFalse();
        }
    }

    // ==================== Household Access Tests ====================

    @Nested
    @DisplayName("canAccessHousehold")
    class CanAccessHouseholdTests {

        @Test
        @DisplayName("returns true when user is a member")
        void whenMember_returnsTrue() {
            // Given
            mockAuthenticatedUser(testUserId);
            when(householdMemberRepository.existsByHouseholdIdAndUserId(testHouseholdId, testUserId))
                    .thenReturn(true);

            // When
            boolean result = securityService.canAccessHousehold(testHouseholdId);

            // Then
            assertThat(result).isTrue();
            verify(householdMemberRepository).existsByHouseholdIdAndUserId(testHouseholdId, testUserId);
        }

        @Test
        @DisplayName("returns false when user is not a member")
        void whenNotMember_returnsFalse() {
            // Given
            mockAuthenticatedUser(testUserId);
            when(householdMemberRepository.existsByHouseholdIdAndUserId(testHouseholdId, testUserId))
                    .thenReturn(false);

            // When
            boolean result = securityService.canAccessHousehold(testHouseholdId);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("returns false when not authenticated")
        void whenNotAuthenticated_returnsFalse() {
            // Given
            mockUnauthenticated();

            // When
            boolean result = securityService.canAccessHousehold(testHouseholdId);

            // Then
            assertThat(result).isFalse();
            verifyNoInteractions(householdMemberRepository);
        }

        @Test
        @DisplayName("returns false when householdId is null")
        void whenNullHouseholdId_returnsFalse() {

            // When
            boolean result = securityService.canAccessHousehold(null);

            // Then
            assertThat(result).isFalse();
            verifyNoInteractions(householdMemberRepository);
        }
    }

    @Nested
    @DisplayName("canModifyHousehold")
    class CanModifyHouseholdTests {

        @Test
        @DisplayName("returns true when user is OWNER")
        void whenOwner_returnsTrue() {
            // Given
            mockAuthenticatedUser(testUserId);
            HouseholdMember member = createMember(testUserId, testHouseholdId, HouseholdRole.OWNER);
            when(householdMemberRepository.findByHouseholdIdAndUserId(testHouseholdId, testUserId))
                    .thenReturn(Optional.of(member));

            // When
            boolean result = securityService.canModifyHousehold(testHouseholdId);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("returns true when user is ADMIN")
        void whenAdmin_returnsTrue() {
            // Given
            mockAuthenticatedUser(testUserId);
            HouseholdMember member = createMember(testUserId, testHouseholdId, HouseholdRole.ADMIN);
            when(householdMemberRepository.findByHouseholdIdAndUserId(testHouseholdId, testUserId))
                    .thenReturn(Optional.of(member));

            // When
            boolean result = securityService.canModifyHousehold(testHouseholdId);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("returns false when user is MEMBER")
        void whenMember_returnsFalse() {
            // Given
            mockAuthenticatedUser(testUserId);
            HouseholdMember member = createMember(testUserId, testHouseholdId, HouseholdRole.MEMBER);
            when(householdMemberRepository.findByHouseholdIdAndUserId(testHouseholdId, testUserId))
                    .thenReturn(Optional.of(member));

            // When
            boolean result = securityService.canModifyHousehold(testHouseholdId);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("returns false when user is not a member")
        void whenNotMember_returnsFalse() {
            // Given
            mockAuthenticatedUser(testUserId);
            when(householdMemberRepository.findByHouseholdIdAndUserId(testHouseholdId, testUserId))
                    .thenReturn(Optional.empty());

            // When
            boolean result = securityService.canModifyHousehold(testHouseholdId);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("returns false when not authenticated")
        void whenNotAuthenticated_returnsFalse() {
            // Given
            mockUnauthenticated();

            // When
            boolean result = securityService.canModifyHousehold(testHouseholdId);

            // Then
            assertThat(result).isFalse();
            verifyNoInteractions(householdMemberRepository);
        }
    }

    @Nested
    @DisplayName("isHouseholdOwner")
    class IsHouseholdOwnerTests {

        @Test
        @DisplayName("returns true when user is OWNER")
        void whenOwner_returnsTrue() {
            // Given
            mockAuthenticatedUser(testUserId);
            HouseholdMember member = createMember(testUserId, testHouseholdId, HouseholdRole.OWNER);
            when(householdMemberRepository.findByHouseholdIdAndUserId(testHouseholdId, testUserId))
                    .thenReturn(Optional.of(member));

            // When
            boolean result = securityService.isHouseholdOwner(testHouseholdId);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("returns false when user is ADMIN")
        void whenAdmin_returnsFalse() {
            // Given
            mockAuthenticatedUser(testUserId);
            HouseholdMember member = createMember(testUserId, testHouseholdId, HouseholdRole.ADMIN);
            when(householdMemberRepository.findByHouseholdIdAndUserId(testHouseholdId, testUserId))
                    .thenReturn(Optional.of(member));

            // When
            boolean result = securityService.isHouseholdOwner(testHouseholdId);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("returns false when user is MEMBER")
        void whenMember_returnsFalse() {
            // Given
            mockAuthenticatedUser(testUserId);
            HouseholdMember member = createMember(testUserId, testHouseholdId, HouseholdRole.MEMBER);
            when(householdMemberRepository.findByHouseholdIdAndUserId(testHouseholdId, testUserId))
                    .thenReturn(Optional.of(member));

            // When
            boolean result = securityService.isHouseholdOwner(testHouseholdId);

            // Then
            assertThat(result).isFalse();
        }
    }

    // ==================== User Profile Access Tests ====================

    @Nested
    @DisplayName("canAccessUserProfile")
    class CanAccessUserProfileTests {

        @Test
        @DisplayName("returns true for own profile")
        void whenOwnProfile_returnsTrue() {
            // Given
            mockAuthenticatedUser(testUserId);

            // When
            boolean result = securityService.canAccessUserProfile(testUserId);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("returns true when users share household")
        void whenSharedHousehold_returnsTrue() {
            // Given
            UUID otherUserId = UUID.randomUUID();
            mockAuthenticatedUser(testUserId);
            
            HouseholdMember member1 = createMember(testUserId, testHouseholdId, HouseholdRole.MEMBER);
            when(householdMemberRepository.findByUserId(testUserId))
                    .thenReturn(List.of(member1));
            when(householdMemberRepository.existsByHouseholdIdAndUserId(testHouseholdId, otherUserId))
                    .thenReturn(true);

            // When
            boolean result = securityService.canAccessUserProfile(otherUserId);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("returns false when users don't share household")
        void whenNoSharedHousehold_returnsFalse() {
            // Given
            UUID otherUserId = UUID.randomUUID();
            mockAuthenticatedUser(testUserId);
            
            HouseholdMember member1 = createMember(testUserId, testHouseholdId, HouseholdRole.MEMBER);
            when(householdMemberRepository.findByUserId(testUserId))
                    .thenReturn(List.of(member1));
            when(householdMemberRepository.existsByHouseholdIdAndUserId(testHouseholdId, otherUserId))
                    .thenReturn(false);

            // When
            boolean result = securityService.canAccessUserProfile(otherUserId);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("returns false when userId is null")
        void whenNullUserId_returnsFalse() {
            // When
            boolean result = securityService.canAccessUserProfile(null);

            // Then
            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("canModifyUserProfile")
    class CanModifyUserProfileTests {

        @Test
        @DisplayName("returns true for own profile")
        void whenOwnProfile_returnsTrue() {
            // Given
            mockAuthenticatedUser(testUserId);

            // When
            boolean result = securityService.canModifyUserProfile(testUserId);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("returns false for other user's profile")
        void whenOtherProfile_returnsFalse() {
            // Given
            UUID otherUserId = UUID.randomUUID();
            mockAuthenticatedUser(testUserId);

            // When
            boolean result = securityService.canModifyUserProfile(otherUserId);

            // Then
            assertThat(result).isFalse();
        }
    }

    // ==================== Helper Method Tests ====================

    @Nested
    @DisplayName("getCurrentUserMembership")
    class GetCurrentUserMembershipTests {

        @Test
        @DisplayName("returns membership when user is member")
        void whenMember_returnsMembership() {
            // Given
            mockAuthenticatedUser(testUserId);
            HouseholdMember member = createMember(testUserId, testHouseholdId, HouseholdRole.ADMIN);
            when(householdMemberRepository.findByHouseholdIdAndUserId(testHouseholdId, testUserId))
                    .thenReturn(Optional.of(member));

            // When
            Optional<HouseholdMember> result = securityService.getCurrentUserMembership(testHouseholdId);

            // Then
            assertThat(result).contains(member);
        }

        @Test
        @DisplayName("returns empty when user is not member")
        void whenNotMember_returnsEmpty() {
            // Given
            mockAuthenticatedUser(testUserId);
            when(householdMemberRepository.findByHouseholdIdAndUserId(testHouseholdId, testUserId))
                    .thenReturn(Optional.empty());

            // When
            Optional<HouseholdMember> result = securityService.getCurrentUserMembership(testHouseholdId);

            // Then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("returns empty when householdId is null")
        void whenNullHouseholdId_returnsEmpty() {
            // When
            Optional<HouseholdMember> result = securityService.getCurrentUserMembership(null);

            // Then
            assertThat(result).isEmpty();
            verifyNoInteractions(householdMemberRepository);
        }
    }

    @Nested
    @DisplayName("getCurrentUserRole")
    class GetCurrentUserRoleTests {

        @Test
        @DisplayName("returns role when user is member")
        void whenMember_returnsRole() {
            // Given
            mockAuthenticatedUser(testUserId);
            HouseholdMember member = createMember(testUserId, testHouseholdId, HouseholdRole.ADMIN);
            when(householdMemberRepository.findByHouseholdIdAndUserId(testHouseholdId, testUserId))
                    .thenReturn(Optional.of(member));

            // When
            Optional<HouseholdRole> result = securityService.getCurrentUserRole(testHouseholdId);

            // Then
            assertThat(result).contains(HouseholdRole.ADMIN);
        }

        @Test
        @DisplayName("returns empty when user is not member")
        void whenNotMember_returnsEmpty() {
            // Given
            mockAuthenticatedUser(testUserId);
            when(householdMemberRepository.findByHouseholdIdAndUserId(testHouseholdId, testUserId))
                    .thenReturn(Optional.empty());

            // When
            Optional<HouseholdRole> result = securityService.getCurrentUserRole(testHouseholdId);

            // Then
            assertThat(result).isEmpty();
        }
    }
}
