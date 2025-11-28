package org.cubord.cubordbackend.service;

import org.cubord.cubordbackend.domain.Household;
import org.cubord.cubordbackend.domain.HouseholdMember;
import org.cubord.cubordbackend.domain.HouseholdRole;
import org.cubord.cubordbackend.domain.User;
import org.cubord.cubordbackend.dto.householdMember.HouseholdMemberRequest;
import org.cubord.cubordbackend.dto.householdMember.HouseholdMemberResponse;
import org.cubord.cubordbackend.exception.*;
import org.cubord.cubordbackend.repository.HouseholdMemberRepository;
import org.cubord.cubordbackend.repository.HouseholdRepository;
import org.cubord.cubordbackend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Household Member Service Tests")
class HouseholdMemberServiceTest {

    @Mock
    private HouseholdRepository householdRepository;
    @Mock
    private HouseholdMemberRepository householdMemberRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private UserService userService;
    @Mock
    private JwtAuthenticationToken validToken;
    @Mock
    private JwtAuthenticationToken invalidToken;

    @InjectMocks
    private HouseholdMemberService householdMemberService;

    // Test data
    private User testUser;
    private User testUser2;
    private User testUser3;
    private Household testHousehold;
    private HouseholdMember ownerMember;
    private HouseholdMember adminMember;
    private HouseholdMember regularMember;
    private HouseholdMemberRequest testMemberRequest;
    private UUID userId;
    private UUID userId2;
    private UUID userId3;
    private UUID householdId;
    private UUID memberId;
    private UUID memberId2;
    private UUID memberId3;

    @BeforeEach
    void setUp() {
        reset(householdRepository, householdMemberRepository, userRepository, userService, validToken, invalidToken);

        // Initialize IDs
        userId = UUID.randomUUID();
        userId2 = UUID.randomUUID();
        userId3 = UUID.randomUUID();
        householdId = UUID.randomUUID();
        memberId = UUID.randomUUID();
        memberId2 = UUID.randomUUID();
        memberId3 = UUID.randomUUID();

        // Create test users
        testUser = new User();
        testUser.setId(userId);
        testUser.setUsername("owner");
        testUser.setEmail("owner@example.com");
        testUser.setDisplayName("Owner User");

        testUser2 = new User();
        testUser2.setId(userId2);
        testUser2.setUsername("admin");
        testUser2.setEmail("admin@example.com");
        testUser2.setDisplayName("Admin User");

        testUser3 = new User();
        testUser3.setId(userId3);
        testUser3.setUsername("member");
        testUser3.setEmail("member@example.com");
        testUser3.setDisplayName("Member User");

        // Create test household
        testHousehold = new Household();
        testHousehold.setId(householdId);
        testHousehold.setName("Test Household");
        testHousehold.setCreatedAt(LocalDateTime.now());
        testHousehold.setUpdatedAt(LocalDateTime.now());

        // Create household members
        ownerMember = new HouseholdMember();
        ownerMember.setId(memberId);
        ownerMember.setUser(testUser);
        ownerMember.setHousehold(testHousehold);
        ownerMember.setRole(HouseholdRole.OWNER);
        ownerMember.setCreatedAt(LocalDateTime.now());
        ownerMember.setUpdatedAt(LocalDateTime.now());

        adminMember = new HouseholdMember();
        adminMember.setId(memberId2);
        adminMember.setUser(testUser2);
        adminMember.setHousehold(testHousehold);
        adminMember.setRole(HouseholdRole.ADMIN);
        adminMember.setCreatedAt(LocalDateTime.now());
        adminMember.setUpdatedAt(LocalDateTime.now());

        regularMember = new HouseholdMember();
        regularMember.setId(memberId3);
        regularMember.setUser(testUser3);
        regularMember.setHousehold(testHousehold);
        regularMember.setRole(HouseholdRole.MEMBER);
        regularMember.setCreatedAt(LocalDateTime.now());
        regularMember.setUpdatedAt(LocalDateTime.now());

        // Create test request
        testMemberRequest = new HouseholdMemberRequest();
        testMemberRequest.setUserId(userId3);
        testMemberRequest.setRole(HouseholdRole.MEMBER);
    }

    // Helper methods for authentication setup
    private void setupOwnerAuthentication() {
        when(userService.getCurrentUser(eq(validToken))).thenReturn(testUser);
    }

    private void setupAdminAuthentication() {
        when(userService.getCurrentUser(eq(validToken))).thenReturn(testUser2);
    }

    private void setupMemberAuthentication() {
        when(userService.getCurrentUser(eq(validToken))).thenReturn(testUser3);
    }

    private void setupInvalidAuthentication() {
        when(userService.getCurrentUser(eq(invalidToken)))
                .thenThrow(new NotFoundException("User not found"));
    }

    @Nested
    @DisplayName("Add Member Tests")
    class AddMemberTests {

        @Test
        @DisplayName("Should throw ValidationException when request is null")
        void shouldThrowValidationExceptionWhenRequestIsNull() {
            assertThatThrownBy(() -> householdMemberService.addMemberToHousehold(householdId, null, validToken))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("request cannot be null");

            verify(userService, never()).getCurrentUser(any(JwtAuthenticationToken.class));
            verify(householdMemberRepository, never()).save(any(HouseholdMember.class));
        }

        @Test
        @DisplayName("Should throw ValidationException when token is null")
        void shouldThrowValidationExceptionWhenTokenIsNull() {
            assertThatThrownBy(() -> householdMemberService.addMemberToHousehold(householdId, testMemberRequest, null))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("token cannot be null");

            verify(userService, never()).getCurrentUser(any(JwtAuthenticationToken.class));
            verify(householdMemberRepository, never()).save(any(HouseholdMember.class));
        }

        @Test
        @DisplayName("Should throw ValidationException when householdId is null")
        void shouldThrowValidationExceptionWhenHouseholdIdIsNull() {

            assertThatThrownBy(() -> householdMemberService.addMemberToHousehold(null, testMemberRequest, validToken))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Household ID cannot be null");

            verify(householdRepository, never()).findById(any(UUID.class));
            verify(householdMemberRepository, never()).save(any(HouseholdMember.class));
        }

        @Test
        @DisplayName("Should add member successfully when user is owner")
        void shouldAddMemberSuccessfullyWhenUserIsOwner() {
            setupOwnerAuthentication();
            when(householdRepository.findById(householdId)).thenReturn(Optional.of(testHousehold));
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, userId))
                    .thenReturn(Optional.of(ownerMember));
            when(userRepository.findById(userId3)).thenReturn(Optional.of(testUser3));
            when(householdMemberRepository.existsByHouseholdIdAndUserId(householdId, userId3)).thenReturn(false);
            when(householdMemberRepository.save(any(HouseholdMember.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            HouseholdMemberResponse response = householdMemberService.addMemberToHousehold(
                    householdId, testMemberRequest, validToken);

            assertThat(response).isNotNull();
            assertThat(response.getUserId()).isEqualTo(userId3);
            assertThat(response.getHouseholdId()).isEqualTo(householdId);
            assertThat(response.getRole()).isEqualTo(HouseholdRole.MEMBER);
            verify(householdMemberRepository).save(any(HouseholdMember.class));
        }

        @Test
        @DisplayName("Should add member successfully when user is admin")
        void shouldAddMemberSuccessfullyWhenUserIsAdmin() {
            setupAdminAuthentication();
            when(householdRepository.findById(householdId)).thenReturn(Optional.of(testHousehold));
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, userId2))
                    .thenReturn(Optional.of(adminMember));
            when(userRepository.findById(userId3)).thenReturn(Optional.of(testUser3));
            when(householdMemberRepository.existsByHouseholdIdAndUserId(householdId, userId3)).thenReturn(false);
            when(householdMemberRepository.save(any(HouseholdMember.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            HouseholdMemberResponse response = householdMemberService.addMemberToHousehold(
                    householdId, testMemberRequest, validToken);

            assertThat(response).isNotNull();
            assertThat(response.getUserId()).isEqualTo(userId3);
            assertThat(response.getHouseholdId()).isEqualTo(householdId);
            assertThat(response.getRole()).isEqualTo(HouseholdRole.MEMBER);
            verify(householdMemberRepository).save(any(HouseholdMember.class));
        }

        @Test
        @DisplayName("Should throw NotFoundException when household not found")
        void shouldThrowNotFoundExceptionWhenHouseholdNotFound() {
            setupOwnerAuthentication();
            when(householdRepository.findById(householdId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> householdMemberService.addMemberToHousehold(
                    householdId, testMemberRequest, validToken))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("Household not found");

            verify(householdMemberRepository, never()).save(any(HouseholdMember.class));
        }

        @Test
        @DisplayName("Should throw NotFoundException when current user is not a member")
        void shouldThrowNotFoundExceptionWhenCurrentUserNotMember() {
            setupOwnerAuthentication();
            when(householdRepository.findById(householdId)).thenReturn(Optional.of(testHousehold));
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, userId))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> householdMemberService.addMemberToHousehold(
                    householdId, testMemberRequest, validToken))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("not a member");

            verify(householdMemberRepository, never()).save(any(HouseholdMember.class));
        }

        @Test
        @DisplayName("Should throw InsufficientPermissionException when user is regular member")
        void shouldThrowInsufficientPermissionExceptionWhenUserIsRegularMember() {
            setupMemberAuthentication();
            when(householdRepository.findById(householdId)).thenReturn(Optional.of(testHousehold));
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, userId3))
                    .thenReturn(Optional.of(regularMember));

            assertThatThrownBy(() -> householdMemberService.addMemberToHousehold(
                    householdId, testMemberRequest, validToken))
                    .isInstanceOf(InsufficientPermissionException.class)
                    .hasMessageContaining("don't have permission to add members");

            verify(householdMemberRepository, never()).save(any(HouseholdMember.class));
        }

        @Test
        @DisplayName("Should throw NotFoundException when user to add not found")
        void shouldThrowNotFoundExceptionWhenUserToAddNotFound() {
            setupOwnerAuthentication();
            when(householdRepository.findById(householdId)).thenReturn(Optional.of(testHousehold));
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, userId))
                    .thenReturn(Optional.of(ownerMember));
            when(userRepository.findById(userId3)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> householdMemberService.addMemberToHousehold(
                    householdId, testMemberRequest, validToken))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("User not found");

            verify(householdMemberRepository, never()).save(any(HouseholdMember.class));
        }

        @Test
        @DisplayName("Should throw ConflictException when user is already a member")
        void shouldThrowConflictExceptionWhenUserAlreadyMember() {
            setupOwnerAuthentication();
            when(householdRepository.findById(householdId)).thenReturn(Optional.of(testHousehold));
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, userId))
                    .thenReturn(Optional.of(ownerMember));
            when(userRepository.findById(userId3)).thenReturn(Optional.of(testUser3));
            when(householdMemberRepository.existsByHouseholdIdAndUserId(householdId, userId3)).thenReturn(true);

            assertThatThrownBy(() -> householdMemberService.addMemberToHousehold(
                    householdId, testMemberRequest, validToken))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("already a member");

            verify(householdMemberRepository, never()).save(any(HouseholdMember.class));
        }

        @Test
        @DisplayName("Should throw ValidationException when attempting to set role to OWNER")
        void shouldThrowValidationExceptionWhenSettingOwnerRole() {
            setupOwnerAuthentication();
            testMemberRequest.setRole(HouseholdRole.OWNER);
            when(householdRepository.findById(householdId)).thenReturn(Optional.of(testHousehold));
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, userId))
                    .thenReturn(Optional.of(ownerMember));
            when(userRepository.findById(userId3)).thenReturn(Optional.of(testUser3));
            when(householdMemberRepository.existsByHouseholdIdAndUserId(householdId, userId3)).thenReturn(false);

            assertThatThrownBy(() -> householdMemberService.addMemberToHousehold(
                    householdId, testMemberRequest, validToken))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Cannot set role to OWNER");

            verify(householdMemberRepository, never()).save(any(HouseholdMember.class));
        }
    }

    @Nested
    @DisplayName("Get Household Members Tests")
    class GetHouseholdMembersTests {

        @Test
        @DisplayName("Should throw ValidationException when token is null")
        void shouldThrowValidationExceptionWhenTokenIsNull() {
            assertThatThrownBy(() -> householdMemberService.getHouseholdMembers(householdId, null))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("token cannot be null");

            verify(userService, never()).getCurrentUser(any(JwtAuthenticationToken.class));
            verify(householdMemberRepository, never()).findByHouseholdId(any(UUID.class));
        }

        @Test
        @DisplayName("Should throw ValidationException when householdId is null")
        void shouldThrowValidationExceptionWhenHouseholdIdIsNull() {

            assertThatThrownBy(() -> householdMemberService.getHouseholdMembers(null, validToken))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Household ID cannot be null");

            verify(householdRepository, never()).findById(any(UUID.class));
        }

        @Test
        @DisplayName("Should return all members when user has access")
        void shouldReturnAllMembersWhenUserHasAccess() {
            setupOwnerAuthentication();
            when(householdRepository.findById(householdId)).thenReturn(Optional.of(testHousehold));
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, userId))
                    .thenReturn(Optional.of(ownerMember));
            when(householdMemberRepository.findByHouseholdId(householdId))
                    .thenReturn(List.of(ownerMember, adminMember, regularMember));

            List<HouseholdMemberResponse> responses = householdMemberService.getHouseholdMembers(
                    householdId, validToken);

            assertThat(responses).hasSize(3);
            assertThat(responses).extracting(HouseholdMemberResponse::getUserId)
                    .containsExactlyInAnyOrder(userId, userId2, userId3);
            verify(householdMemberRepository).findByHouseholdId(householdId);
        }

        @Test
        @DisplayName("Should throw NotFoundException when household not found")
        void shouldThrowNotFoundExceptionWhenHouseholdNotFound() {
            setupOwnerAuthentication();
            when(householdRepository.findById(householdId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> householdMemberService.getHouseholdMembers(householdId, validToken))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("Household not found");

            verify(householdMemberRepository, never()).findByHouseholdId(any(UUID.class));
        }

        @Test
        @DisplayName("Should throw NotFoundException when user is not a member")
        void shouldThrowNotFoundExceptionWhenUserNotMember() {
            setupOwnerAuthentication();
            when(householdRepository.findById(householdId)).thenReturn(Optional.of(testHousehold));
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, userId))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> householdMemberService.getHouseholdMembers(householdId, validToken))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("not a member");

            verify(householdMemberRepository, never()).findByHouseholdId(any(UUID.class));
        }
    }

    @Nested
    @DisplayName("Get Member By ID Tests")
    class GetMemberByIdTests {

        @Test
        @DisplayName("Should throw ValidationException when token is null")
        void shouldThrowValidationExceptionWhenTokenIsNull() {
            assertThatThrownBy(() -> householdMemberService.getMemberById(householdId, memberId, null))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("token cannot be null");

            verify(userService, never()).getCurrentUser(any(JwtAuthenticationToken.class));
            verify(householdMemberRepository, never()).findById(any(UUID.class));
        }

        @Test
        @DisplayName("Should throw ValidationException when householdId is null")
        void shouldThrowValidationExceptionWhenHouseholdIdIsNull() {

            assertThatThrownBy(() -> householdMemberService.getMemberById(null, memberId, validToken))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Household ID cannot be null");

            verify(householdRepository, never()).findById(any(UUID.class));
        }

        @Test
        @DisplayName("Should throw ValidationException when memberId is null")
        void shouldThrowValidationExceptionWhenMemberIdIsNull() {

            assertThatThrownBy(() -> householdMemberService.getMemberById(householdId, null, validToken))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Member ID cannot be null");

            verify(userService, never()).getCurrentUser(any(JwtAuthenticationToken.class));
            verify(householdRepository, never()).findById(any(UUID.class));
            verify(householdMemberRepository, never()).findById(any(UUID.class));
        }

        @Test
        @DisplayName("Should return member successfully when user has access")
        void shouldReturnMemberSuccessfullyWhenUserHasAccess() {
            setupOwnerAuthentication();
            when(householdRepository.findById(householdId)).thenReturn(Optional.of(testHousehold));
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, userId))
                    .thenReturn(Optional.of(ownerMember));
            when(householdMemberRepository.findById(memberId3)).thenReturn(Optional.of(regularMember));

            HouseholdMemberResponse response = householdMemberService.getMemberById(
                    householdId, memberId3, validToken);

            assertThat(response).isNotNull();
            assertThat(response.getUserId()).isEqualTo(userId3);
            assertThat(response.getRole()).isEqualTo(HouseholdRole.MEMBER);
            verify(householdMemberRepository).findById(memberId3);
        }

        @Test
        @DisplayName("Should throw NotFoundException when household not found")
        void shouldThrowNotFoundExceptionWhenHouseholdNotFound() {
            setupOwnerAuthentication();
            when(householdRepository.findById(householdId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> householdMemberService.getMemberById(
                    householdId, memberId3, validToken))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("Household not found");

            verify(householdMemberRepository, never()).findById(any(UUID.class));
        }

        @Test
        @DisplayName("Should throw NotFoundException when current user is not a member")
        void shouldThrowNotFoundExceptionWhenCurrentUserNotMember() {
            setupOwnerAuthentication();
            when(householdRepository.findById(householdId)).thenReturn(Optional.of(testHousehold));
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, userId))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> householdMemberService.getMemberById(
                    householdId, memberId3, validToken))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("not a member");

            verify(householdMemberRepository, never()).findById(any(UUID.class));
        }

        @Test
        @DisplayName("Should throw NotFoundException when member not found")
        void shouldThrowNotFoundExceptionWhenMemberNotFound() {
            setupOwnerAuthentication();
            when(householdRepository.findById(householdId)).thenReturn(Optional.of(testHousehold));
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, userId))
                    .thenReturn(Optional.of(ownerMember));
            when(householdMemberRepository.findById(memberId3)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> householdMemberService.getMemberById(
                    householdId, memberId3, validToken))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("Member not found");
        }

        @Test
        @DisplayName("Should throw NotFoundException when member is from different household")
        void shouldThrowNotFoundExceptionWhenMemberFromDifferentHousehold() {
            setupOwnerAuthentication();
            UUID otherHouseholdId = UUID.randomUUID();
            Household otherHousehold = new Household();
            otherHousehold.setId(otherHouseholdId);

            HouseholdMember memberFromOtherHousehold = new HouseholdMember();
            memberFromOtherHousehold.setId(memberId3);
            memberFromOtherHousehold.setUser(testUser3);
            memberFromOtherHousehold.setHousehold(otherHousehold);
            memberFromOtherHousehold.setRole(HouseholdRole.MEMBER);

            when(householdRepository.findById(householdId)).thenReturn(Optional.of(testHousehold));
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, userId))
                    .thenReturn(Optional.of(ownerMember));
            when(householdMemberRepository.findById(memberId3)).thenReturn(Optional.of(memberFromOtherHousehold));

            assertThatThrownBy(() -> householdMemberService.getMemberById(
                    householdId, memberId3, validToken))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("not from the specified household");
        }
    }

    @Nested
    @DisplayName("Remove Member Tests")
    class RemoveMemberTests {

        @Test
        @DisplayName("Should throw ValidationException when token is null")
        void shouldThrowValidationExceptionWhenTokenIsNull() {
            assertThatThrownBy(() -> householdMemberService.removeMember(householdId, memberId, null))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("token cannot be null");

            verify(userService, never()).getCurrentUser(any(JwtAuthenticationToken.class));
            verify(householdMemberRepository, never()).delete(any(HouseholdMember.class));
        }

        @Test
        @DisplayName("Should throw ValidationException when householdId is null")
        void shouldThrowValidationExceptionWhenHouseholdIdIsNull() {
            assertThatThrownBy(() -> householdMemberService.removeMember(null, memberId, validToken))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Household ID cannot be null");

            verify(householdRepository, never()).findById(any(UUID.class));
        }

        @Test
        @DisplayName("Should throw ValidationException when memberId is null")
        void shouldThrowValidationExceptionWhenMemberIdIsNull() {

            assertThatThrownBy(() -> householdMemberService.removeMember(householdId, null, validToken))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Member ID cannot be null");

            verify(userService, never()).getCurrentUser(any(JwtAuthenticationToken.class));
            verify(householdRepository, never()).findById(any(UUID.class));
            verify(householdMemberRepository, never()).delete(any(HouseholdMember.class));
        }

        @Test
        @DisplayName("Should remove member successfully when user is owner")
        void shouldRemoveMemberSuccessfullyWhenUserIsOwner() {
            setupOwnerAuthentication();
            when(householdRepository.findById(householdId)).thenReturn(Optional.of(testHousehold));
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, userId))
                    .thenReturn(Optional.of(ownerMember));
            when(householdMemberRepository.findById(memberId3)).thenReturn(Optional.of(regularMember));

            householdMemberService.removeMember(householdId, memberId3, validToken);

            verify(householdMemberRepository).delete(regularMember);
        }

        @Test
        @DisplayName("Should remove member successfully when user is admin")
        void shouldRemoveMemberSuccessfullyWhenUserIsAdmin() {
            setupAdminAuthentication();
            when(householdRepository.findById(householdId)).thenReturn(Optional.of(testHousehold));
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, userId2))
                    .thenReturn(Optional.of(adminMember));
            when(householdMemberRepository.findById(memberId3)).thenReturn(Optional.of(regularMember));

            householdMemberService.removeMember(householdId, memberId3, validToken);

            verify(householdMemberRepository).delete(regularMember);
        }

        @Test
        @DisplayName("Should throw NotFoundException when household not found")
        void shouldThrowNotFoundExceptionWhenHouseholdNotFound() {
            setupOwnerAuthentication();
            when(householdRepository.findById(householdId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> householdMemberService.removeMember(householdId, memberId3, validToken))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("Household not found");

            verify(householdMemberRepository, never()).delete(any(HouseholdMember.class));
        }

        @Test
        @DisplayName("Should throw NotFoundException when current user is not a member")
        void shouldThrowNotFoundExceptionWhenCurrentUserNotMember() {
            setupOwnerAuthentication();
            when(householdRepository.findById(householdId)).thenReturn(Optional.of(testHousehold));
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, userId))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> householdMemberService.removeMember(householdId, memberId3, validToken))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("not a member");

            verify(householdMemberRepository, never()).delete(any(HouseholdMember.class));
        }

        @Test
        @DisplayName("Should throw InsufficientPermissionException when user is regular member")
        void shouldThrowInsufficientPermissionExceptionWhenUserIsRegularMember() {
            setupMemberAuthentication();
            when(householdRepository.findById(householdId)).thenReturn(Optional.of(testHousehold));
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, userId3))
                    .thenReturn(Optional.of(regularMember));

            assertThatThrownBy(() -> householdMemberService.removeMember(householdId, memberId, validToken))
                    .isInstanceOf(InsufficientPermissionException.class)
                    .hasMessageContaining("don't have permission to remove members");

            verify(householdMemberRepository, never()).delete(any(HouseholdMember.class));
        }

        @Test
        @DisplayName("Should throw NotFoundException when member to remove not found")
        void shouldThrowNotFoundExceptionWhenMemberToRemoveNotFound() {
            setupOwnerAuthentication();
            when(householdRepository.findById(householdId)).thenReturn(Optional.of(testHousehold));
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, userId))
                    .thenReturn(Optional.of(ownerMember));
            when(householdMemberRepository.findById(memberId3)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> householdMemberService.removeMember(householdId, memberId3, validToken))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("Member not found");

            verify(householdMemberRepository, never()).delete(any(HouseholdMember.class));
        }

        @Test
        @DisplayName("Should throw NotFoundException when member is from different household")
        void shouldThrowNotFoundExceptionWhenMemberFromDifferentHousehold() {
            setupOwnerAuthentication();
            UUID otherHouseholdId = UUID.randomUUID();
            Household otherHousehold = new Household();
            otherHousehold.setId(otherHouseholdId);

            HouseholdMember memberFromOtherHousehold = new HouseholdMember();
            memberFromOtherHousehold.setId(memberId3);
            memberFromOtherHousehold.setUser(testUser3);
            memberFromOtherHousehold.setHousehold(otherHousehold);
            memberFromOtherHousehold.setRole(HouseholdRole.MEMBER);

            when(householdRepository.findById(householdId)).thenReturn(Optional.of(testHousehold));
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, userId))
                    .thenReturn(Optional.of(ownerMember));
            when(householdMemberRepository.findById(memberId3)).thenReturn(Optional.of(memberFromOtherHousehold));

            assertThatThrownBy(() -> householdMemberService.removeMember(householdId, memberId3, validToken))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("not from the specified household");

            verify(householdMemberRepository, never()).delete(any(HouseholdMember.class));
        }

        @Test
        @DisplayName("Should throw ResourceStateException when attempting to remove owner")
        void shouldThrowResourceStateExceptionWhenRemovingOwner() {
            setupOwnerAuthentication();
            when(householdRepository.findById(householdId)).thenReturn(Optional.of(testHousehold));
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, userId))
                    .thenReturn(Optional.of(ownerMember));
            when(householdMemberRepository.findById(memberId)).thenReturn(Optional.of(ownerMember));

            assertThatThrownBy(() -> householdMemberService.removeMember(householdId, memberId, validToken))
                    .isInstanceOf(ResourceStateException.class)
                    .hasMessageContaining("Cannot remove the owner");

            verify(householdMemberRepository, never()).delete(any(HouseholdMember.class));
        }

        @Test
        @DisplayName("Should throw InsufficientPermissionException when admin tries to remove another admin")
        void shouldThrowInsufficientPermissionExceptionWhenAdminTriesToRemoveAdmin() {
            setupAdminAuthentication();
            HouseholdMember anotherAdmin = new HouseholdMember();
            anotherAdmin.setId(UUID.randomUUID());
            anotherAdmin.setUser(new User());
            anotherAdmin.setHousehold(testHousehold);
            anotherAdmin.setRole(HouseholdRole.ADMIN);

            when(householdRepository.findById(householdId)).thenReturn(Optional.of(testHousehold));
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, userId2))
                    .thenReturn(Optional.of(adminMember));
            when(householdMemberRepository.findById(anotherAdmin.getId())).thenReturn(Optional.of(anotherAdmin));

            assertThatThrownBy(() -> householdMemberService.removeMember(
                    householdId, anotherAdmin.getId(), validToken))
                    .isInstanceOf(InsufficientPermissionException.class)
                    .hasMessageContaining("Admin cannot remove another admin");

            verify(householdMemberRepository, never()).delete(any(HouseholdMember.class));
        }
    }

    @Nested
    @DisplayName("Update Member Role Tests")
    class UpdateMemberRoleTests {

        @Test
        @DisplayName("Should throw ValidationException when token is null")
        void shouldThrowValidationExceptionWhenTokenIsNull() {
            assertThatThrownBy(() -> householdMemberService.updateMemberRole(
                    householdId, memberId, HouseholdRole.ADMIN, null))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("token cannot be null");

            verify(userService, never()).getCurrentUser(any(JwtAuthenticationToken.class));
            verify(householdMemberRepository, never()).save(any(HouseholdMember.class));
        }

        @Test
        @DisplayName("Should throw ValidationException when householdId is null")
        void shouldThrowValidationExceptionWhenHouseholdIdIsNull() {

            assertThatThrownBy(() -> householdMemberService.updateMemberRole(
                    null, memberId, HouseholdRole.ADMIN, validToken))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Household ID cannot be null");

            verify(householdRepository, never()).findById(any(UUID.class));
        }

        @Test
        @DisplayName("Should throw ValidationException when memberId is null")
        void shouldThrowValidationExceptionWhenMemberIdIsNull() {

            assertThatThrownBy(() -> householdMemberService.updateMemberRole(
                    householdId, null, HouseholdRole.ADMIN, validToken))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Member ID cannot be null");

            verify(householdRepository, never()).findById(any(UUID.class));
        }

        @Test
        @DisplayName("Should throw ValidationException when role is null")
        void shouldThrowValidationExceptionWhenRoleIsNull() {
            assertThatThrownBy(() -> householdMemberService.updateMemberRole(
                    householdId, memberId, null, validToken))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Role cannot be null");

            verify(householdRepository, never()).findById(any(UUID.class));
        }

        @Test
        @DisplayName("Should throw ValidationException when attempting to set role to OWNER")
        void shouldThrowValidationExceptionWhenSettingOwnerRole() {

            assertThatThrownBy(() -> householdMemberService.updateMemberRole(
                    householdId, memberId3, HouseholdRole.OWNER, validToken))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Cannot set role to OWNER");

            verify(householdRepository, never()).findById(any(UUID.class));
            verify(householdMemberRepository, never()).save(any(HouseholdMember.class));
        }

        @Test
        @DisplayName("Should update member role successfully when user is owner")
        void shouldUpdateMemberRoleSuccessfullyWhenUserIsOwner() {
            setupOwnerAuthentication();
            when(householdRepository.findById(householdId)).thenReturn(Optional.of(testHousehold));
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, userId))
                    .thenReturn(Optional.of(ownerMember));
            when(householdMemberRepository.findById(memberId3)).thenReturn(Optional.of(regularMember));
            when(householdMemberRepository.save(any(HouseholdMember.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            HouseholdMemberResponse response = householdMemberService.updateMemberRole(
                    householdId, memberId3, HouseholdRole.ADMIN, validToken);

            assertThat(response).isNotNull();
            assertThat(response.getRole()).isEqualTo(HouseholdRole.ADMIN);
            verify(householdMemberRepository).save(any(HouseholdMember.class));
        }

        @Test
        @DisplayName("Should update member role successfully when user is admin")
        void shouldUpdateMemberRoleSuccessfullyWhenUserIsAdmin() {
            setupAdminAuthentication();
            when(householdRepository.findById(householdId)).thenReturn(Optional.of(testHousehold));
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, userId2))
                    .thenReturn(Optional.of(adminMember));
            when(householdMemberRepository.findById(memberId3)).thenReturn(Optional.of(regularMember));
            when(householdMemberRepository.save(any(HouseholdMember.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            HouseholdMemberResponse response = householdMemberService.updateMemberRole(
                    householdId, memberId3, HouseholdRole.ADMIN, validToken);

            assertThat(response).isNotNull();
            assertThat(response.getRole()).isEqualTo(HouseholdRole.ADMIN);
            verify(householdMemberRepository).save(any(HouseholdMember.class));
        }

        @Test
        @DisplayName("Should throw NotFoundException when household not found")
        void shouldThrowNotFoundExceptionWhenHouseholdNotFound() {
            setupOwnerAuthentication();
            when(householdRepository.findById(householdId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> householdMemberService.updateMemberRole(
                    householdId, memberId3, HouseholdRole.ADMIN, validToken))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("Household not found");

            verify(householdMemberRepository, never()).save(any(HouseholdMember.class));
        }

        @Test
        @DisplayName("Should throw NotFoundException when current user is not a member")
        void shouldThrowNotFoundExceptionWhenCurrentUserNotMember() {
            setupOwnerAuthentication();
            when(householdRepository.findById(householdId)).thenReturn(Optional.of(testHousehold));
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, userId))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> householdMemberService.updateMemberRole(
                    householdId, memberId3, HouseholdRole.ADMIN, validToken))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("not a member");

            verify(householdMemberRepository, never()).save(any(HouseholdMember.class));
        }

        @Test
        @DisplayName("Should throw InsufficientPermissionException when user is regular member")
        void shouldThrowInsufficientPermissionExceptionWhenUserIsRegularMember() {
            setupMemberAuthentication();
            when(householdRepository.findById(householdId)).thenReturn(Optional.of(testHousehold));
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, userId3))
                    .thenReturn(Optional.of(regularMember));

            assertThatThrownBy(() -> householdMemberService.updateMemberRole(
                    householdId, memberId, HouseholdRole.ADMIN, validToken))
                    .isInstanceOf(InsufficientPermissionException.class)
                    .hasMessageContaining("don't have permission to update member roles");

            verify(householdMemberRepository, never()).save(any(HouseholdMember.class));
        }

        @Test
        @DisplayName("Should throw NotFoundException when member to update not found")
        void shouldThrowNotFoundExceptionWhenMemberToUpdateNotFound() {
            setupOwnerAuthentication();
            when(householdRepository.findById(householdId)).thenReturn(Optional.of(testHousehold));
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, userId))
                    .thenReturn(Optional.of(ownerMember));
            when(householdMemberRepository.findById(memberId3)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> householdMemberService.updateMemberRole(
                    householdId, memberId3, HouseholdRole.ADMIN, validToken))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("Member not found");

            verify(householdMemberRepository, never()).save(any(HouseholdMember.class));
        }

        @Test
        @DisplayName("Should throw NotFoundException when member is from different household")
        void shouldThrowNotFoundExceptionWhenMemberFromDifferentHousehold() {
            setupOwnerAuthentication();
            UUID otherHouseholdId = UUID.randomUUID();
            Household otherHousehold = new Household();
            otherHousehold.setId(otherHouseholdId);

            HouseholdMember memberFromOtherHousehold = new HouseholdMember();
            memberFromOtherHousehold.setId(memberId3);
            memberFromOtherHousehold.setUser(testUser3);
            memberFromOtherHousehold.setHousehold(otherHousehold);
            memberFromOtherHousehold.setRole(HouseholdRole.MEMBER);

            when(householdRepository.findById(householdId)).thenReturn(Optional.of(testHousehold));
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, userId))
                    .thenReturn(Optional.of(ownerMember));
            when(householdMemberRepository.findById(memberId3)).thenReturn(Optional.of(memberFromOtherHousehold));

            assertThatThrownBy(() -> householdMemberService.updateMemberRole(
                    householdId, memberId3, HouseholdRole.ADMIN, validToken))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("not from the specified household");

            verify(householdMemberRepository, never()).save(any(HouseholdMember.class));
        }

        @Test
        @DisplayName("Should throw InsufficientPermissionException when admin tries to update another admin")
        void shouldThrowInsufficientPermissionExceptionWhenAdminTriesToUpdateAdmin() {
            setupAdminAuthentication();
            HouseholdMember anotherAdmin = new HouseholdMember();
            anotherAdmin.setId(UUID.randomUUID());
            anotherAdmin.setUser(new User());
            anotherAdmin.setHousehold(testHousehold);
            anotherAdmin.setRole(HouseholdRole.ADMIN);

            when(householdRepository.findById(householdId)).thenReturn(Optional.of(testHousehold));
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, userId2))
                    .thenReturn(Optional.of(adminMember));
            when(householdMemberRepository.findById(anotherAdmin.getId())).thenReturn(Optional.of(anotherAdmin));

            assertThatThrownBy(() -> householdMemberService.updateMemberRole(
                    householdId, anotherAdmin.getId(), HouseholdRole.MEMBER, validToken))
                    .isInstanceOf(InsufficientPermissionException.class)
                    .hasMessageContaining("Admin cannot update another admin's role");

            verify(householdMemberRepository, never()).save(any(HouseholdMember.class));
        }
    }
}