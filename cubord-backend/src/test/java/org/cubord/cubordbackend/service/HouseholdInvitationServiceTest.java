
package org.cubord.cubordbackend.service;

import org.cubord.cubordbackend.domain.*;
import org.cubord.cubordbackend.dto.householdInvitation.HouseholdInvitationRequest;
import org.cubord.cubordbackend.dto.householdInvitation.HouseholdInvitationResponse;
import org.cubord.cubordbackend.dto.householdInvitation.HouseholdInvitationUpdateRequest;
import org.cubord.cubordbackend.dto.householdInvitation.ResendInvitationRequest;
import org.cubord.cubordbackend.exception.*;
import org.cubord.cubordbackend.repository.HouseholdInvitationRepository;
import org.cubord.cubordbackend.repository.HouseholdMemberRepository;
import org.cubord.cubordbackend.repository.HouseholdRepository;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive test suite for HouseholdInvitationService following the modernized security architecture.
 *
 * <p>Test Structure:</p>
 * <ul>
 *   <li>No token validation tests (handled by Spring Security filters)</li>
 *   <li>Authorization tested via SecurityService mocking</li>
 *   <li>Focus on business logic and data validation</li>
 *   <li>Clear separation of concerns with nested test classes</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("HouseholdInvitationService Tests")
class HouseholdInvitationServiceTest {

    @Mock
    private HouseholdInvitationRepository householdInvitationRepository;

    @Mock
    private HouseholdRepository householdRepository;

    @Mock
    private HouseholdMemberRepository householdMemberRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SecurityService securityService;

    @InjectMocks
    private HouseholdInvitationService householdInvitationService;

    // Test data
    private UUID currentUserId;
    private UUID invitedUserId;
    private UUID householdId;
    private UUID invitationId;
    private User currentUser;
    private User invitedUser;
    private Household testHousehold;
    private HouseholdMember ownerMember;
    private HouseholdMember adminMember;
    private HouseholdMember regularMember;
    private HouseholdInvitation testInvitation;
    private HouseholdInvitationRequest testInvitationRequest;

    @BeforeEach
    void setUp() {
        currentUserId = UUID.randomUUID();
        invitedUserId = UUID.randomUUID();
        householdId = UUID.randomUUID();
        invitationId = UUID.randomUUID();

        currentUser = User.builder()
                .id(currentUserId)
                .username("currentuser")
                .email("current@example.com")
                .displayName("Current User")
                .role(UserRole.USER)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        invitedUser = User.builder()
                .id(invitedUserId)
                .username("inviteduser")
                .email("invited@example.com")
                .displayName("Invited User")
                .role(UserRole.USER)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        testHousehold = Household.builder()
                .id(householdId)
                .name("Test Household")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        ownerMember = HouseholdMember.builder()
                .id(UUID.randomUUID())
                .user(currentUser)
                .household(testHousehold)
                .role(HouseholdRole.OWNER)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        adminMember = HouseholdMember.builder()
                .id(UUID.randomUUID())
                .user(currentUser)
                .household(testHousehold)
                .role(HouseholdRole.ADMIN)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        regularMember = HouseholdMember.builder()
                .id(UUID.randomUUID())
                .user(currentUser)
                .household(testHousehold)
                .role(HouseholdRole.MEMBER)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        testInvitation = HouseholdInvitation.builder()
                .id(invitationId)
                .household(testHousehold)
                .invitedUser(invitedUser)
                .invitedEmail(null)  // Explicitly set - user-linked invitation
                .invitedBy(currentUser)
                .proposedRole(HouseholdRole.MEMBER)
                .status(InvitationStatus.PENDING)
                .expiresAt(LocalDateTime.now().plusDays(7))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        testInvitationRequest = HouseholdInvitationRequest.builder()
                .invitedUserEmail("invited@example.com")
                .proposedRole(HouseholdRole.MEMBER)
                .build();
    }

    // ==================== Test Utilities ====================

    /**
     * Use this when the code under test calls securityService.getCurrentUserId().
     */
    private void stubCurrentUserId(UUID userId) {
        when(securityService.getCurrentUserId()).thenReturn(userId);
    }

    /**
     * Use this when the code under test calls securityService.getCurrentUser().
     */
    private void stubCurrentUser(User user) {
        when(securityService.getCurrentUser()).thenReturn(user);
    }

    // ==================== Create Operations Tests ====================

    @Nested
    @DisplayName("sendInvitation")
    class SendInvitationTests {

        @Test
        @DisplayName("should send invitation successfully when user is owner")
        void shouldSendInvitationSuccessfullyWhenUserIsOwner() {
            // Given
            stubCurrentUserId(currentUserId);
            stubCurrentUser(currentUser);
            when(householdRepository.findById(eq(householdId))).thenReturn(Optional.of(testHousehold));
            when(userRepository.findByEmail(eq("invited@example.com"))).thenReturn(Optional.of(invitedUser));
            when(householdMemberRepository.existsByHouseholdIdAndUserId(eq(householdId), eq(invitedUserId)))
                    .thenReturn(false);
            when(householdInvitationRepository.existsByHouseholdIdAndInvitedUserIdAndStatus(
                    eq(householdId), eq(invitedUserId), eq(InvitationStatus.PENDING)))
                    .thenReturn(false);
            when(householdInvitationRepository.save(any(HouseholdInvitation.class))).thenReturn(testInvitation);

            // When
            HouseholdInvitationResponse response = householdInvitationService.sendInvitation(
                    householdId, testInvitationRequest);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getInvitedUserEmail()).isEqualTo(invitedUser.getEmail());
            assertThat(response.getProposedRole()).isEqualTo(HouseholdRole.MEMBER);
            assertThat(response.getStatus()).isEqualTo(InvitationStatus.PENDING);

            verify(securityService).getCurrentUserId();
            verify(securityService).getCurrentUser();
            verify(householdRepository).findById(eq(householdId));
            verify(userRepository).findByEmail(eq("invited@example.com"));
            verify(householdInvitationRepository).save(any(HouseholdInvitation.class));
        }

        @Test
        @DisplayName("should send invitation by user ID when provided")
        void shouldSendInvitationByUserIdWhenProvided() {
            // Given
            HouseholdInvitationRequest requestWithUserId = HouseholdInvitationRequest.builder()
                    .invitedUserId(invitedUserId)
                    .proposedRole(HouseholdRole.MEMBER)
                    .build();

            stubCurrentUserId(currentUserId);
            stubCurrentUser(currentUser);
            when(householdRepository.findById(eq(householdId))).thenReturn(Optional.of(testHousehold));
            when(userRepository.findById(eq(invitedUserId))).thenReturn(Optional.of(invitedUser));
            when(householdMemberRepository.existsByHouseholdIdAndUserId(eq(householdId), eq(invitedUserId)))
                    .thenReturn(false);
            when(householdInvitationRepository.existsByHouseholdIdAndInvitedUserIdAndStatus(
                    eq(householdId), eq(invitedUserId), eq(InvitationStatus.PENDING)))
                    .thenReturn(false);
            when(householdInvitationRepository.save(any(HouseholdInvitation.class))).thenReturn(testInvitation);

            // When
            HouseholdInvitationResponse response = householdInvitationService.sendInvitation(
                    householdId, requestWithUserId);

            // Then
            assertThat(response).isNotNull();
            verify(userRepository).findById(eq(invitedUserId));
            verify(userRepository, never()).findByEmail(anyString());
            verify(householdInvitationRepository).save(any(HouseholdInvitation.class));
        }

        @Test
        @DisplayName("should throw ValidationException when household ID is null")
        void shouldThrowValidationExceptionWhenHouseholdIdIsNull() {
            // When/Then
            assertThatThrownBy(() -> householdInvitationService.sendInvitation(
                    null, testInvitationRequest))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Household ID cannot be null");

            verify(householdInvitationRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw ValidationException when request is null")
        void shouldThrowValidationExceptionWhenRequestIsNull() {
            // When/Then
            assertThatThrownBy(() -> householdInvitationService.sendInvitation(
                    householdId, null))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Invitation request cannot be null");

            verify(householdInvitationRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw NotFoundException when household not found")
        void shouldThrowNotFoundExceptionWhenHouseholdNotFound() {
            // Given
            stubCurrentUserId(currentUserId);
            when(householdRepository.findById(eq(householdId))).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> householdInvitationService.sendInvitation(
                    householdId, testInvitationRequest))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("Household not found");

            verify(householdInvitationRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw NotFoundException when invited user by ID not found")
        void shouldThrowNotFoundExceptionWhenInvitedUserByIdNotFound() {
            // Given
            HouseholdInvitationRequest requestWithUserId = HouseholdInvitationRequest.builder()
                    .invitedUserId(invitedUserId)
                    .proposedRole(HouseholdRole.MEMBER)
                    .build();

            stubCurrentUserId(currentUserId);
            when(householdRepository.findById(eq(householdId))).thenReturn(Optional.of(testHousehold));
            when(userRepository.findById(eq(invitedUserId))).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> householdInvitationService.sendInvitation(
                    householdId, requestWithUserId))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("User not found");

            verify(householdInvitationRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw ValidationException when neither userId nor email provided")
        void shouldThrowValidationExceptionWhenNeitherUserIdNorEmailProvided() {
            // Given
            HouseholdInvitationRequest invalidRequest = HouseholdInvitationRequest.builder()
                    .proposedRole(HouseholdRole.MEMBER)
                    .build();

            stubCurrentUserId(currentUserId);
            when(householdRepository.findById(eq(householdId))).thenReturn(Optional.of(testHousehold));

            // When/Then
            assertThatThrownBy(() -> householdInvitationService.sendInvitation(
                    householdId, invalidRequest))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Either invitedUserId or invitedUserEmail must be provided");

            verify(householdInvitationRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw BusinessRuleViolationException when trying to invite self")
        void shouldThrowBusinessRuleViolationExceptionWhenTryingToInviteSelf() {
            // Given
            stubCurrentUserId(currentUserId);
            when(householdRepository.findById(eq(householdId))).thenReturn(Optional.of(testHousehold));
            when(userRepository.findByEmail(eq("invited@example.com"))).thenReturn(Optional.of(currentUser));

            // When/Then
            assertThatThrownBy(() -> householdInvitationService.sendInvitation(
                    householdId, testInvitationRequest))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("Cannot invite yourself");

            verify(householdInvitationRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw ValidationException when proposed role is null")
        void shouldThrowValidationExceptionWhenProposedRoleIsNull() {
            // Given
            HouseholdInvitationRequest requestWithNullRole = HouseholdInvitationRequest.builder()
                    .invitedUserEmail("invited@example.com")
                    .proposedRole(null)
                    .build();

            stubCurrentUserId(currentUserId);
            when(householdRepository.findById(eq(householdId))).thenReturn(Optional.of(testHousehold));
            when(userRepository.findByEmail(eq("invited@example.com"))).thenReturn(Optional.of(invitedUser));

            // When/Then
            assertThatThrownBy(() -> householdInvitationService.sendInvitation(
                    householdId, requestWithNullRole))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Proposed role cannot be null");

            verify(householdInvitationRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw ValidationException when trying to invite as OWNER")
        void shouldThrowValidationExceptionWhenTryingToInviteAsOwner() {
            // Given
            HouseholdInvitationRequest ownerRequest = HouseholdInvitationRequest.builder()
                    .invitedUserEmail("invited@example.com")
                    .proposedRole(HouseholdRole.OWNER)
                    .build();

            stubCurrentUserId(currentUserId);
            when(householdRepository.findById(eq(householdId))).thenReturn(Optional.of(testHousehold));
            when(userRepository.findByEmail(eq("invited@example.com"))).thenReturn(Optional.of(invitedUser));

            // When/Then
            assertThatThrownBy(() -> householdInvitationService.sendInvitation(
                    householdId, ownerRequest))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Cannot invite user as OWNER");

            verify(householdInvitationRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw ValidationException when expiry date is in the past")
        void shouldThrowValidationExceptionWhenExpiryDateIsInThePast() {
            // Given
            HouseholdInvitationRequest requestWithPastExpiry = HouseholdInvitationRequest.builder()
                    .invitedUserEmail("invited@example.com")
                    .proposedRole(HouseholdRole.MEMBER)
                    .expiresAt(LocalDateTime.now().minusDays(1))
                    .build();

            stubCurrentUserId(currentUserId);
            when(householdRepository.findById(eq(householdId))).thenReturn(Optional.of(testHousehold));
            when(userRepository.findByEmail(eq("invited@example.com"))).thenReturn(Optional.of(invitedUser));

            // When/Then
            assertThatThrownBy(() -> householdInvitationService.sendInvitation(
                    householdId, requestWithPastExpiry))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Expiry date cannot be in the past");

            verify(householdInvitationRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw ConflictException when user is already a member")
        void shouldThrowConflictExceptionWhenUserIsAlreadyMember() {
            // Given
            stubCurrentUserId(currentUserId);
            when(householdRepository.findById(eq(householdId))).thenReturn(Optional.of(testHousehold));
            when(userRepository.findByEmail(eq("invited@example.com"))).thenReturn(Optional.of(invitedUser));
            when(householdMemberRepository.existsByHouseholdIdAndUserId(eq(householdId), eq(invitedUserId)))
                    .thenReturn(true);

            // When/Then
            assertThatThrownBy(() -> householdInvitationService.sendInvitation(
                    householdId, testInvitationRequest))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("User is already a member of this household");

            verify(householdInvitationRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw ConflictException when user already has pending invitation")
        void shouldThrowConflictExceptionWhenUserAlreadyHasPendingInvitation() {
            // Given
            stubCurrentUserId(currentUserId);
            when(householdRepository.findById(eq(householdId))).thenReturn(Optional.of(testHousehold));
            when(userRepository.findByEmail(eq("invited@example.com"))).thenReturn(Optional.of(invitedUser));
            when(householdMemberRepository.existsByHouseholdIdAndUserId(eq(householdId), eq(invitedUserId)))
                    .thenReturn(false);
            when(householdInvitationRepository.existsByHouseholdIdAndInvitedUserIdAndStatus(
                    eq(householdId), eq(invitedUserId), eq(InvitationStatus.PENDING)))
                    .thenReturn(true);

            // When/Then
            assertThatThrownBy(() -> householdInvitationService.sendInvitation(
                    householdId, testInvitationRequest))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("User already has a pending invitation");

            verify(householdInvitationRepository, never()).save(any());
        }
    }

    // ==================== Query Operations Tests ====================

    @Nested
    @DisplayName("getHouseholdInvitations")
    class GetHouseholdInvitationsTests {

        @Test
        @DisplayName("should retrieve all invitations successfully")
        void shouldGetHouseholdInvitationsSuccessfully() {
            // Given
            stubCurrentUserId(currentUserId);
            when(householdInvitationRepository.findByHouseholdId(eq(householdId)))
                    .thenReturn(List.of(testInvitation));

            // When
            List<HouseholdInvitationResponse> responses = householdInvitationService
                    .getHouseholdInvitations(householdId);

            // Then
            assertThat(responses).hasSize(1);
            assertThat(responses.get(0).getId()).isEqualTo(invitationId);

            verify(securityService).getCurrentUserId();
            verify(householdInvitationRepository).findByHouseholdId(eq(householdId));
        }

        @Test
        @DisplayName("should return empty list when no invitations exist")
        void shouldReturnEmptyListWhenNoInvitations() {
            // Given
            stubCurrentUserId(currentUserId);
            when(householdInvitationRepository.findByHouseholdId(eq(householdId)))
                    .thenReturn(List.of());

            // When
            List<HouseholdInvitationResponse> responses = householdInvitationService
                    .getHouseholdInvitations(householdId);

            // Then
            assertThat(responses).isEmpty();
        }

        @Test
        @DisplayName("should throw ValidationException when household ID is null")
        void shouldThrowValidationExceptionWhenHouseholdIdIsNull() {
            // When/Then
            assertThatThrownBy(() -> householdInvitationService.getHouseholdInvitations(null))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Household ID cannot be null");

            verify(householdInvitationRepository, never()).findByHouseholdId(any());
        }
    }

    @Nested
    @DisplayName("getHouseholdInvitationsByStatus")
    class GetHouseholdInvitationsByStatusTests {

        @Test
        @DisplayName("should retrieve invitations by status successfully")
        void shouldGetHouseholdInvitationsByStatusSuccessfully() {
            // Given
            stubCurrentUserId(currentUserId);
            when(householdInvitationRepository.findByHouseholdIdAndStatus(
                    eq(householdId), eq(InvitationStatus.PENDING)))
                    .thenReturn(List.of(testInvitation));

            // When
            List<HouseholdInvitationResponse> responses = householdInvitationService
                    .getHouseholdInvitationsByStatus(householdId, InvitationStatus.PENDING);

            // Then
            assertThat(responses).hasSize(1);
            assertThat(responses.get(0).getStatus()).isEqualTo(InvitationStatus.PENDING);

            verify(householdInvitationRepository).findByHouseholdIdAndStatus(
                    eq(householdId), eq(InvitationStatus.PENDING));
        }

        @Test
        @DisplayName("should throw ValidationException when household ID is null")
        void shouldThrowValidationExceptionWhenHouseholdIdIsNull() {
            // When/Then
            assertThatThrownBy(() -> householdInvitationService
                    .getHouseholdInvitationsByStatus(null, InvitationStatus.PENDING))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Household ID cannot be null");
        }

        @Test
        @DisplayName("should throw ValidationException when status is null")
        void shouldThrowValidationExceptionWhenStatusIsNull() {
            // When/Then
            assertThatThrownBy(() -> householdInvitationService
                    .getHouseholdInvitationsByStatus(householdId, null))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Status cannot be null");
        }
    }

    @Nested
    @DisplayName("getInvitationById")
    class GetInvitationByIdTests {

        @Test
        @DisplayName("should retrieve invitation by ID successfully")
        void shouldGetInvitationByIdSuccessfully() {
            // Given
            stubCurrentUserId(currentUserId);
            when(householdInvitationRepository.findById(eq(invitationId)))
                    .thenReturn(Optional.of(testInvitation));

            // When
            HouseholdInvitationResponse response = householdInvitationService
                    .getInvitationById(householdId, invitationId);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo(invitationId);

            verify(householdInvitationRepository).findById(eq(invitationId));
        }

        @Test
        @DisplayName("should throw ValidationException when household ID is null")
        void shouldThrowValidationExceptionWhenHouseholdIdIsNull() {
            // When/Then
            assertThatThrownBy(() -> householdInvitationService
                    .getInvitationById(null, invitationId))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Household ID cannot be null");
        }

        @Test
        @DisplayName("should throw ValidationException when invitation ID is null")
        void shouldThrowValidationExceptionWhenInvitationIdIsNull() {
            // When/Then
            assertThatThrownBy(() -> householdInvitationService
                    .getInvitationById(householdId, null))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Invitation ID cannot be null");
        }

        @Test
        @DisplayName("should throw NotFoundException when invitation not found")
        void shouldThrowNotFoundExceptionWhenInvitationNotFound() {
            // Given
            stubCurrentUserId(currentUserId);
            when(householdInvitationRepository.findById(eq(invitationId)))
                    .thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> householdInvitationService
                    .getInvitationById(householdId, invitationId))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("Invitation not found");
        }

        @Test
        @DisplayName("should throw NotFoundException when invitation belongs to different household")
        void shouldThrowNotFoundExceptionWhenInvitationBelongsToDifferentHousehold() {
            // Given
            UUID differentHouseholdId = UUID.randomUUID();
            stubCurrentUserId(currentUserId);
            when(householdInvitationRepository.findById(eq(invitationId)))
                    .thenReturn(Optional.of(testInvitation));

            // When/Then
            assertThatThrownBy(() -> householdInvitationService
                    .getInvitationById(differentHouseholdId, invitationId))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("Invitation not found in the specified household");
        }
    }

    @Nested
    @DisplayName("getMyInvitations")
    class GetMyInvitationsTests {

        @Test
        @DisplayName("should retrieve current user's invitations successfully")
        void shouldGetMyInvitationsSuccessfully() {
            // Given
            stubCurrentUser(currentUser);
            when(householdInvitationRepository.linkEmailInvitationsToUser(
                    eq(currentUser), eq(currentUser.getEmail()), eq(InvitationStatus.PENDING), any(LocalDateTime.class)))
                    .thenReturn(0);
            when(householdInvitationRepository.findByInvitedUserIdAndStatus(
                    eq(currentUserId), eq(InvitationStatus.PENDING)))
                    .thenReturn(List.of(testInvitation));

            // When
            List<HouseholdInvitationResponse> responses = householdInvitationService.getMyInvitations();

            // Then
            assertThat(responses).hasSize(1);

            verify(securityService).getCurrentUser();
            verify(householdInvitationRepository).linkEmailInvitationsToUser(
                    eq(currentUser), eq(currentUser.getEmail()), eq(InvitationStatus.PENDING), any(LocalDateTime.class));
            verify(householdInvitationRepository).findByInvitedUserIdAndStatus(
                    eq(currentUserId), eq(InvitationStatus.PENDING));
        }

        @Test
        @DisplayName("should return empty list when user has no invitations")
        void shouldReturnEmptyListWhenNoInvitations() {
            // Given
            stubCurrentUser(currentUser);
            when(householdInvitationRepository.linkEmailInvitationsToUser(
                    eq(currentUser), eq(currentUser.getEmail()), eq(InvitationStatus.PENDING), any(LocalDateTime.class)))
                    .thenReturn(0);
            when(householdInvitationRepository.findByInvitedUserIdAndStatus(
                    eq(currentUserId), eq(InvitationStatus.PENDING)))
                    .thenReturn(List.of());

            // When
            List<HouseholdInvitationResponse> responses = householdInvitationService.getMyInvitations();

            // Then
            assertThat(responses).isEmpty();
        }
    }

    @Nested
    @DisplayName("getMyInvitationsByStatus")
    class GetMyInvitationsByStatusTests {

        @Test
        @DisplayName("should retrieve current user's invitations by status successfully")
        void shouldGetMyInvitationsByStatusSuccessfully() {
            // Given - Using ACCEPTED status to avoid email linking logic
            stubCurrentUser(currentUser);
            when(householdInvitationRepository.findByInvitedUserIdAndStatus(
                    eq(currentUserId), eq(InvitationStatus.ACCEPTED)))
                    .thenReturn(List.of(testInvitation));

            // When
            List<HouseholdInvitationResponse> responses = householdInvitationService
                    .getMyInvitationsByStatus(InvitationStatus.ACCEPTED);

            // Then
            assertThat(responses).hasSize(1);
            
            verify(securityService).getCurrentUser();
            // Should NOT call linkEmailInvitationsToUser for non-PENDING status
            verify(householdInvitationRepository, never()).linkEmailInvitationsToUser(any(), any(), any(), any());
        }

        @Test
        @DisplayName("should link email invitations when status is PENDING")
        void shouldLinkEmailInvitationsWhenStatusIsPending() {
            // Given
            stubCurrentUser(currentUser);
            when(householdInvitationRepository.linkEmailInvitationsToUser(
                    eq(currentUser), eq(currentUser.getEmail()), eq(InvitationStatus.PENDING), any(LocalDateTime.class)))
                    .thenReturn(0);
            when(householdInvitationRepository.findByInvitedUserIdAndStatus(
                    eq(currentUserId), eq(InvitationStatus.PENDING)))
                    .thenReturn(List.of(testInvitation));

            // When
            List<HouseholdInvitationResponse> responses = householdInvitationService
                    .getMyInvitationsByStatus(InvitationStatus.PENDING);

            // Then
            assertThat(responses).hasSize(1);
            
            verify(householdInvitationRepository).linkEmailInvitationsToUser(
                    eq(currentUser), eq(currentUser.getEmail()), eq(InvitationStatus.PENDING), any(LocalDateTime.class));
        }

        @Test
        @DisplayName("should throw ValidationException when status is null")
        void shouldThrowValidationExceptionWhenStatusIsNull() {
            // When/Then
            assertThatThrownBy(() -> householdInvitationService.getMyInvitationsByStatus(null))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Status cannot be null");
        }
    }

    // ==================== Update Operations Tests ====================

    @Nested
    @DisplayName("acceptInvitation")
    class AcceptInvitationTests {

        @Test
        @DisplayName("should accept invitation successfully")
        void shouldAcceptInvitationSuccessfully() {
            // Given
            stubCurrentUser(invitedUser);
            when(householdInvitationRepository.findById(eq(invitationId)))
                    .thenReturn(Optional.of(testInvitation));
            when(householdMemberRepository.existsByHouseholdIdAndUserId(eq(householdId), eq(invitedUserId)))
                    .thenReturn(false);
            when(householdInvitationRepository.save(any(HouseholdInvitation.class)))
                    .thenReturn(testInvitation);
            when(householdMemberRepository.save(any(HouseholdMember.class)))
                    .thenReturn(regularMember);

            // When
            HouseholdInvitationResponse response = householdInvitationService
                    .acceptInvitation(invitationId);

            // Then
            assertThat(response).isNotNull();

            verify(securityService).getCurrentUser();
            verify(householdMemberRepository).save(any(HouseholdMember.class));
            verify(householdInvitationRepository).save(any(HouseholdInvitation.class));
        }

        @Test
        @DisplayName("should throw ValidationException when invitation ID is null")
        void shouldThrowValidationExceptionWhenInvitationIdIsNull() {
            // When/Then
            assertThatThrownBy(() -> householdInvitationService.acceptInvitation(null))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Invitation ID cannot be null");

            verify(householdMemberRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw NotFoundException when invitation not found")
        void shouldThrowNotFoundExceptionWhenInvitationNotFound() {
            // Given
            stubCurrentUser(currentUser);
            when(householdInvitationRepository.findById(eq(invitationId)))
                    .thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> householdInvitationService.acceptInvitation(invitationId))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("Invitation not found");

            verify(householdMemberRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw InsufficientPermissionException when user is not the invited user")
        void shouldThrowInsufficientPermissionExceptionWhenUserIsNotInvitedUser() {
            // Given
            stubCurrentUser(currentUser);
            when(householdInvitationRepository.findById(eq(invitationId)))
                    .thenReturn(Optional.of(testInvitation));

            // When/Then
            assertThatThrownBy(() -> householdInvitationService.acceptInvitation(invitationId))
                    .isInstanceOf(InsufficientPermissionException.class)
                    .hasMessageContaining("You are not the invited user");

            verify(householdMemberRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw ResourceStateException when invitation is already processed")
        void shouldThrowResourceStateExceptionWhenInvitationIsAlreadyProcessed() {
            // Given
            testInvitation.setStatus(InvitationStatus.ACCEPTED);
            stubCurrentUser(invitedUser);
            when(householdInvitationRepository.findById(eq(invitationId)))
                    .thenReturn(Optional.of(testInvitation));

            // When/Then
            assertThatThrownBy(() -> householdInvitationService.acceptInvitation(invitationId))
                    .isInstanceOf(ResourceStateException.class)
                    .hasMessageContaining("Invitation has already been processed");

            verify(householdMemberRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw ResourceStateException when invitation is expired")
        void shouldThrowResourceStateExceptionWhenInvitationIsExpired() {
            // Given
            testInvitation.setExpiresAt(LocalDateTime.now().minusDays(1));
            stubCurrentUser(invitedUser);
            when(householdInvitationRepository.findById(eq(invitationId)))
                    .thenReturn(Optional.of(testInvitation));

            // When/Then
            assertThatThrownBy(() -> householdInvitationService.acceptInvitation(invitationId))
                    .isInstanceOf(ResourceStateException.class)
                    .hasMessageContaining("Invitation has expired");

            verify(householdMemberRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw ConflictException when user is already a member")
        void shouldThrowConflictExceptionWhenUserIsAlreadyMember() {
            // Given
            stubCurrentUser(invitedUser);
            when(householdInvitationRepository.findById(eq(invitationId)))
                    .thenReturn(Optional.of(testInvitation));
            when(householdMemberRepository.existsByHouseholdIdAndUserId(eq(householdId), eq(invitedUserId)))
                    .thenReturn(true);

            // When/Then
            assertThatThrownBy(() -> householdInvitationService.acceptInvitation(invitationId))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("You are already a member of this household");

            verify(householdMemberRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("declineInvitation")
    class DeclineInvitationTests {

        @Test
        @DisplayName("should decline invitation successfully")
        void shouldDeclineInvitationSuccessfully() {
            // Given
            stubCurrentUser(invitedUser);
            when(householdInvitationRepository.findById(eq(invitationId)))
                    .thenReturn(Optional.of(testInvitation));
            when(householdInvitationRepository.save(any(HouseholdInvitation.class)))
                    .thenReturn(testInvitation);

            // When
            HouseholdInvitationResponse response = householdInvitationService
                    .declineInvitation(invitationId);

            // Then
            assertThat(response).isNotNull();

            verify(securityService).getCurrentUser();
            verify(householdInvitationRepository).save(any(HouseholdInvitation.class));
        }

        @Test
        @DisplayName("should throw ValidationException when invitation ID is null")
        void shouldThrowValidationExceptionWhenInvitationIdIsNull() {
            // When/Then
            assertThatThrownBy(() -> householdInvitationService.declineInvitation(null))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Invitation ID cannot be null");

            verify(householdInvitationRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw NotFoundException when invitation not found")
        void shouldThrowNotFoundExceptionWhenInvitationNotFound() {
            // Given
            stubCurrentUser(currentUser);
            when(householdInvitationRepository.findById(eq(invitationId)))
                    .thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> householdInvitationService.declineInvitation(invitationId))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("Invitation not found");

            verify(householdInvitationRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw InsufficientPermissionException when user is not the invited user")
        void shouldThrowInsufficientPermissionExceptionWhenUserIsNotInvitedUser() {
            // Given
            stubCurrentUser(currentUser);
            when(householdInvitationRepository.findById(eq(invitationId)))
                    .thenReturn(Optional.of(testInvitation));

            // When/Then
            assertThatThrownBy(() -> householdInvitationService.declineInvitation(invitationId))
                    .isInstanceOf(InsufficientPermissionException.class)
                    .hasMessageContaining("You are not the invited user");

            verify(householdInvitationRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw ResourceStateException when invitation is already processed")
        void shouldThrowResourceStateExceptionWhenInvitationIsAlreadyProcessed() {
            // Given
            testInvitation.setStatus(InvitationStatus.DECLINED);
            stubCurrentUser(invitedUser);
            when(householdInvitationRepository.findById(eq(invitationId)))
                    .thenReturn(Optional.of(testInvitation));

            // When/Then
            assertThatThrownBy(() -> householdInvitationService.declineInvitation(invitationId))
                    .isInstanceOf(ResourceStateException.class)
                    .hasMessageContaining("Invitation has already been processed");

            verify(householdInvitationRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("cancelInvitation")
    class CancelInvitationTests {

        @Test
        @DisplayName("should cancel invitation successfully when user is owner")
        void shouldCancelInvitationSuccessfullyWhenUserIsOwner() {
            // Given
            stubCurrentUserId(currentUserId);
            when(householdInvitationRepository.findById(eq(invitationId)))
                    .thenReturn(Optional.of(testInvitation));
            when(householdInvitationRepository.save(any(HouseholdInvitation.class)))
                    .thenReturn(testInvitation);

            // When
            householdInvitationService.cancelInvitation(householdId, invitationId);

            // Then
            verify(securityService).getCurrentUserId();
            verify(householdInvitationRepository).save(any(HouseholdInvitation.class));
        }

        @Test
        @DisplayName("should throw ValidationException when household ID is null")
        void shouldThrowValidationExceptionWhenHouseholdIdIsNull() {
            // When/Then
            assertThatThrownBy(() -> householdInvitationService
                    .cancelInvitation(null, invitationId))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Household ID cannot be null");

            verify(householdInvitationRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw ValidationException when invitation ID is null")
        void shouldThrowValidationExceptionWhenInvitationIdIsNull() {
            // When/Then
            assertThatThrownBy(() -> householdInvitationService
                    .cancelInvitation(householdId, null))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Invitation ID cannot be null");

            verify(householdInvitationRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw NotFoundException when invitation not found")
        void shouldThrowNotFoundExceptionWhenInvitationNotFound() {
            // Given
            stubCurrentUserId(currentUserId);
            when(householdInvitationRepository.findById(eq(invitationId)))
                    .thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> householdInvitationService
                    .cancelInvitation(householdId, invitationId))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("Invitation not found");

            verify(householdInvitationRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw NotFoundException when invitation belongs to different household")
        void shouldThrowNotFoundExceptionWhenInvitationBelongsToDifferentHousehold() {
            // Given
            UUID differentHouseholdId = UUID.randomUUID();
            stubCurrentUserId(currentUserId);
            when(householdInvitationRepository.findById(eq(invitationId)))
                    .thenReturn(Optional.of(testInvitation));

            // When/Then
            assertThatThrownBy(() -> householdInvitationService
                    .cancelInvitation(differentHouseholdId, invitationId))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("Invitation not found in the specified household");

            verify(householdInvitationRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw ResourceStateException when invitation is already processed")
        void shouldThrowResourceStateExceptionWhenInvitationIsAlreadyProcessed() {
            // Given
            testInvitation.setStatus(InvitationStatus.ACCEPTED);
            stubCurrentUserId(currentUserId);
            when(householdInvitationRepository.findById(eq(invitationId)))
                    .thenReturn(Optional.of(testInvitation));

            // When/Then
            assertThatThrownBy(() -> householdInvitationService
                    .cancelInvitation(householdId, invitationId))
                    .isInstanceOf(ResourceStateException.class)
                    .hasMessageContaining("Cannot cancel processed invitation");

            verify(householdInvitationRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("updateInvitation")
    class UpdateInvitationTests {

        @Test
        @DisplayName("should update invitation successfully")
        void shouldUpdateInvitationSuccessfully() {
            // Given
            HouseholdInvitationUpdateRequest updateRequest = HouseholdInvitationUpdateRequest.builder()
                    .proposedRole(HouseholdRole.ADMIN)
                    .expiresAt(LocalDateTime.now().plusDays(14))
                    .build();

            stubCurrentUserId(currentUserId);
            when(householdInvitationRepository.findById(eq(invitationId)))
                    .thenReturn(Optional.of(testInvitation));
            when(householdInvitationRepository.save(any(HouseholdInvitation.class)))
                    .thenReturn(testInvitation);

            // When
            HouseholdInvitationResponse response = householdInvitationService
                    .updateInvitation(householdId, invitationId, updateRequest);

            // Then
            assertThat(response).isNotNull();

            verify(securityService).getCurrentUserId();
            verify(householdInvitationRepository).save(any(HouseholdInvitation.class));
        }

        @Test
        @DisplayName("should throw ValidationException when household ID is null")
        void shouldThrowValidationExceptionWhenHouseholdIdIsNull() {
            // Given
            HouseholdInvitationUpdateRequest updateRequest = HouseholdInvitationUpdateRequest.builder()
                    .proposedRole(HouseholdRole.ADMIN)
                    .build();

            // When/Then
            assertThatThrownBy(() -> householdInvitationService
                    .updateInvitation(null, invitationId, updateRequest))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Household ID cannot be null");

            verify(householdInvitationRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw ValidationException when invitation ID is null")
        void shouldThrowValidationExceptionWhenInvitationIdIsNull() {
            // Given
            HouseholdInvitationUpdateRequest updateRequest = HouseholdInvitationUpdateRequest.builder()
                    .proposedRole(HouseholdRole.ADMIN)
                    .build();

            // When/Then
            assertThatThrownBy(() -> householdInvitationService
                    .updateInvitation(householdId, null, updateRequest))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Invitation ID cannot be null");

            verify(householdInvitationRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw ValidationException when update request is null")
        void shouldThrowValidationExceptionWhenUpdateRequestIsNull() {
            // When/Then
            assertThatThrownBy(() -> householdInvitationService
                    .updateInvitation(householdId, invitationId, null))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Update request cannot be null");

            verify(householdInvitationRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw ValidationException when proposed role is OWNER")
        void shouldThrowValidationExceptionWhenProposedRoleIsOwner() {
            // Given
            HouseholdInvitationUpdateRequest updateRequest = HouseholdInvitationUpdateRequest.builder()
                    .proposedRole(HouseholdRole.OWNER)
                    .build();

            stubCurrentUserId(currentUserId);
            when(householdInvitationRepository.findById(eq(invitationId)))
                    .thenReturn(Optional.of(testInvitation));

            // When/Then
            assertThatThrownBy(() -> householdInvitationService
                    .updateInvitation(householdId, invitationId, updateRequest))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Cannot set proposed role to OWNER");

            verify(householdInvitationRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw ValidationException when expiry date is in the past")
        void shouldThrowValidationExceptionWhenExpiryDateIsInThePast() {
            // Given
            HouseholdInvitationUpdateRequest updateRequest = HouseholdInvitationUpdateRequest.builder()
                    .expiresAt(LocalDateTime.now().minusDays(1))
                    .build();

            stubCurrentUserId(currentUserId);
            when(householdInvitationRepository.findById(eq(invitationId)))
                    .thenReturn(Optional.of(testInvitation));

            // When/Then
            assertThatThrownBy(() -> householdInvitationService
                    .updateInvitation(householdId, invitationId, updateRequest))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Expiry date cannot be in the past");

            verify(householdInvitationRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw ResourceStateException when invitation is already processed")
        void shouldThrowResourceStateExceptionWhenInvitationIsAlreadyProcessed() {
            // Given
            HouseholdInvitationUpdateRequest updateRequest = HouseholdInvitationUpdateRequest.builder()
                    .proposedRole(HouseholdRole.ADMIN)
                    .build();

            testInvitation.setStatus(InvitationStatus.ACCEPTED);
            stubCurrentUserId(currentUserId);
            when(householdInvitationRepository.findById(eq(invitationId)))
                    .thenReturn(Optional.of(testInvitation));

            // When/Then
            assertThatThrownBy(() -> householdInvitationService
                    .updateInvitation(householdId, invitationId, updateRequest))
                    .isInstanceOf(ResourceStateException.class)
                    .hasMessageContaining("Can only update pending invitations");

            verify(householdInvitationRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("resendInvitation")
    class ResendInvitationTests {

        @Test
        @DisplayName("should resend invitation successfully")
        void shouldResendInvitationSuccessfully() {
            // Given
            ResendInvitationRequest resendRequest = ResendInvitationRequest.builder()
                    .expiresAt(LocalDateTime.now().plusDays(14))
                    .build();

            stubCurrentUserId(currentUserId);
            when(householdInvitationRepository.findById(eq(invitationId)))
                    .thenReturn(Optional.of(testInvitation));
            when(householdInvitationRepository.save(any(HouseholdInvitation.class)))
                    .thenReturn(testInvitation);

            // When
            HouseholdInvitationResponse response = householdInvitationService
                    .resendInvitation(householdId, invitationId, resendRequest);

            // Then
            assertThat(response).isNotNull();

            verify(securityService).getCurrentUserId();
            verify(householdInvitationRepository).save(any(HouseholdInvitation.class));
        }

        @Test
        @DisplayName("should resend invitation with default expiry when request is null")
        void shouldResendInvitationWithDefaultExpiryWhenRequestIsNull() {
            // Given
            stubCurrentUserId(currentUserId);
            when(householdInvitationRepository.findById(eq(invitationId)))
                    .thenReturn(Optional.of(testInvitation));
            when(householdInvitationRepository.save(any(HouseholdInvitation.class)))
                    .thenReturn(testInvitation);

            // When
            HouseholdInvitationResponse response = householdInvitationService
                    .resendInvitation(householdId, invitationId, null);

            // Then
            assertThat(response).isNotNull();
            verify(householdInvitationRepository).save(any(HouseholdInvitation.class));
        }

        @Test
        @DisplayName("should throw ValidationException when household ID is null")
        void shouldThrowValidationExceptionWhenHouseholdIdIsNull() {
            // When/Then
            assertThatThrownBy(() -> householdInvitationService
                    .resendInvitation(null, invitationId, null))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Household ID cannot be null");

            verify(householdInvitationRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw ValidationException when invitation ID is null")
        void shouldThrowValidationExceptionWhenInvitationIdIsNull() {
            // When/Then
            assertThatThrownBy(() -> householdInvitationService
                    .resendInvitation(householdId, null, null))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Invitation ID cannot be null");

            verify(householdInvitationRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw ResourceStateException when invitation is already processed")
        void shouldThrowResourceStateExceptionWhenInvitationIsAlreadyProcessed() {
            // Given
            testInvitation.setStatus(InvitationStatus.DECLINED);
            stubCurrentUserId(currentUserId);
            when(householdInvitationRepository.findById(eq(invitationId)))
                    .thenReturn(Optional.of(testInvitation));

            // When/Then
            assertThatThrownBy(() -> householdInvitationService
                    .resendInvitation(householdId, invitationId, null))
                    .isInstanceOf(ResourceStateException.class)
                    .hasMessageContaining("Can only resend pending invitations");

            verify(householdInvitationRepository, never()).save(any());
        }
    }

    // ==================== Scheduled Operations Tests ====================

    @Nested
    @DisplayName("markExpiredInvitations")
    class MarkExpiredInvitationsTests {

        @Test
        @DisplayName("should mark expired invitations successfully")
        void shouldMarkExpiredInvitationsSuccessfully() {
            // Given
            when(householdInvitationRepository.findByStatusAndExpiresAtBefore(
                    eq(InvitationStatus.PENDING), any(LocalDateTime.class)))
                    .thenReturn(List.of(testInvitation));
            when(householdInvitationRepository.save(any(HouseholdInvitation.class)))
                    .thenReturn(testInvitation);

            // When
            householdInvitationService.markExpiredInvitations();

            // Then
            verify(householdInvitationRepository).findByStatusAndExpiresAtBefore(
                    eq(InvitationStatus.PENDING), any(LocalDateTime.class));
            verify(householdInvitationRepository).save(any(HouseholdInvitation.class));
        }

        @Test
        @DisplayName("should handle no expired invitations gracefully")
        void shouldHandleNoExpiredInvitationsGracefully() {
            // Given
            when(householdInvitationRepository.findByStatusAndExpiresAtBefore(
                    eq(InvitationStatus.PENDING), any(LocalDateTime.class)))
                    .thenReturn(List.of());

            // When
            householdInvitationService.markExpiredInvitations();

            // Then
            verify(householdInvitationRepository).findByStatusAndExpiresAtBefore(
                    eq(InvitationStatus.PENDING), any(LocalDateTime.class));
            verify(householdInvitationRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("sendInvitationReminder")
    class SendInvitationReminderTests {

        @Test
        @DisplayName("should send invitation reminder successfully")
        void shouldSendInvitationReminderSuccessfully() {
            // Given
            when(householdInvitationRepository.findById(eq(invitationId)))
                    .thenReturn(Optional.of(testInvitation));

            // When
            householdInvitationService.sendInvitationReminder(invitationId);

            // Then
            verify(householdInvitationRepository).findById(eq(invitationId));
        }

        @Test
        @DisplayName("should throw ValidationException when invitation ID is null")
        void shouldThrowValidationExceptionWhenInvitationIdIsNull() {
            // When/Then
            assertThatThrownBy(() -> householdInvitationService.sendInvitationReminder(null))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Invitation ID cannot be null");
        }

        @Test
        @DisplayName("should throw NotFoundException when invitation not found")
        void shouldThrowNotFoundExceptionWhenInvitationNotFound() {
            // Given
            when(householdInvitationRepository.findById(eq(invitationId)))
                    .thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> householdInvitationService.sendInvitationReminder(invitationId))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("Invitation not found");
        }

        @Test
        @DisplayName("should throw ResourceStateException when invitation is not pending")
        void shouldThrowResourceStateExceptionWhenInvitationIsNotPending() {
            // Given
            testInvitation.setStatus(InvitationStatus.ACCEPTED);
            when(householdInvitationRepository.findById(eq(invitationId)))
                    .thenReturn(Optional.of(testInvitation));

            // When/Then
            assertThatThrownBy(() -> householdInvitationService.sendInvitationReminder(invitationId))
                    .isInstanceOf(ResourceStateException.class)
                    .hasMessageContaining("Can only send reminders for pending invitations");
        }

        @Test
        @DisplayName("should throw ResourceStateException when invitation is expired")
        void shouldThrowResourceStateExceptionWhenInvitationIsExpired() {
            // Given
            testInvitation.setExpiresAt(LocalDateTime.now().minusDays(1));
            when(householdInvitationRepository.findById(eq(invitationId)))
                    .thenReturn(Optional.of(testInvitation));

            // When/Then
            assertThatThrownBy(() -> householdInvitationService.sendInvitationReminder(invitationId))
                    .isInstanceOf(ResourceStateException.class)
                    .hasMessageContaining("Cannot send reminder for expired invitation");
        }
    }
}