
package org.cubord.cubordbackend.service;

import org.cubord.cubordbackend.domain.*;
import org.cubord.cubordbackend.dto.householdInvitation.HouseholdInvitationRequest  ;
import org.cubord.cubordbackend.dto.householdInvitation.HouseholdInvitationResponse;
import org.cubord.cubordbackend.exception.*;
import org.cubord.cubordbackend.repository.HouseholdInvitationRepository;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Household Invitation Service Tests")
class HouseholdInvitationServiceTest {

    @Mock private HouseholdInvitationRepository householdInvitationRepository;
    @Mock private HouseholdRepository householdRepository;
    @Mock private HouseholdMemberRepository householdMemberRepository;
    @Mock private UserRepository userRepository;
    @Mock private UserService userService;
    @Mock private JwtAuthenticationToken validToken;

    @InjectMocks
    private HouseholdInvitationService householdInvitationService;

    // Test data
    private User currentUser;
    private User invitedUser;
    private Household testHousehold;
    private HouseholdMember ownerMember;
    private HouseholdMember adminMember;
    private HouseholdMember regularMember;
    private HouseholdInvitation testInvitation;
    private HouseholdInvitationRequest testInvitationRequest;
    private UUID currentUserId;
    private UUID invitedUserId;
    private UUID householdId;
    private UUID invitationId;

    @BeforeEach
    void setUp() {
        reset(householdInvitationRepository, householdRepository, householdMemberRepository,
                userRepository, userService, validToken);

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
                .build();

        invitedUser = User.builder()
                .id(invitedUserId)
                .username("inviteduser")
                .email("invited@example.com")
                .displayName("Invited User")
                .role(UserRole.USER)
                .createdAt(LocalDateTime.now())
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

    // Helper methods
    private void setupValidAuthentication() {
        when(userService.getCurrentUser(eq(validToken))).thenReturn(currentUser);
    }

    private void setupOwnerAccess() {
        setupValidAuthentication();
        when(householdRepository.findById(eq(householdId))).thenReturn(Optional.of(testHousehold));
        when(householdMemberRepository.findByHouseholdIdAndUserId(eq(householdId), eq(currentUserId)))
                .thenReturn(Optional.of(ownerMember));
    }

    private void setupAdminAccess() {
        setupValidAuthentication();
        when(householdRepository.findById(eq(householdId))).thenReturn(Optional.of(testHousehold));
        when(householdMemberRepository.findByHouseholdIdAndUserId(eq(householdId), eq(currentUserId)))
                .thenReturn(Optional.of(adminMember));
    }

    private void setupRegularMemberAccess() {
        setupValidAuthentication();
        when(householdRepository.findById(eq(householdId))).thenReturn(Optional.of(testHousehold));
        when(householdMemberRepository.findByHouseholdIdAndUserId(eq(householdId), eq(currentUserId)))
                .thenReturn(Optional.of(regularMember));
    }

    private void setupSuccessfulInvitation() {
        setupOwnerAccess();
        when(userRepository.findByEmail(eq("invited@example.com"))).thenReturn(Optional.of(invitedUser));
        when(householdMemberRepository.findByHouseholdIdAndUserId(eq(householdId), eq(invitedUserId)))
                .thenReturn(Optional.empty());
        when(householdInvitationRepository.existsByHouseholdIdAndInvitedUserIdAndStatus(
                eq(householdId), eq(invitedUserId), eq(InvitationStatus.PENDING)))
                .thenReturn(false);
        when(householdInvitationRepository.save(any(HouseholdInvitation.class))).thenReturn(testInvitation);
    }

    @Nested
    @DisplayName("Send Invitation Tests")
    class SendInvitationTests {

        @Test
        @DisplayName("Should send invitation successfully when user is owner")
        void shouldSendInvitationSuccessfullyWhenUserIsOwner() {
            setupSuccessfulInvitation();

            HouseholdInvitationResponse response = householdInvitationService.sendInvitation(
                    householdId, testInvitationRequest, validToken);

            assertThat(response).isNotNull();
            assertThat(response.getInvitedUserEmail()).isEqualTo(invitedUser.getEmail());
            assertThat(response.getProposedRole()).isEqualTo(HouseholdRole.MEMBER);
            assertThat(response.getStatus()).isEqualTo(InvitationStatus.PENDING);

            verify(householdInvitationRepository).save(any(HouseholdInvitation.class));
        }

        @Test
        @DisplayName("Should send invitation successfully when user is admin")
        void shouldSendInvitationSuccessfullyWhenUserIsAdmin() {
            setupAdminAccess();
            when(userRepository.findByEmail(eq("invited@example.com"))).thenReturn(Optional.of(invitedUser));
            when(householdMemberRepository.findByHouseholdIdAndUserId(eq(householdId), eq(invitedUserId)))
                    .thenReturn(Optional.empty());
            when(householdInvitationRepository.existsByHouseholdIdAndInvitedUserIdAndStatus(
                    eq(householdId), eq(invitedUserId), eq(InvitationStatus.PENDING)))
                    .thenReturn(false);
            when(householdInvitationRepository.save(any(HouseholdInvitation.class))).thenReturn(testInvitation);

            HouseholdInvitationResponse response = householdInvitationService.sendInvitation(
                    householdId, testInvitationRequest, validToken);

            assertThat(response).isNotNull();
            verify(householdInvitationRepository).save(any(HouseholdInvitation.class));
        }

        @Test
        @DisplayName("Should send invitation by user ID when provided")
        void shouldSendInvitationByUserIdWhenProvided() {
            setupOwnerAccess();
            HouseholdInvitationRequest requestWithUserId = HouseholdInvitationRequest.builder()
                    .invitedUserId(invitedUserId)
                    .proposedRole(HouseholdRole.MEMBER)
                    .build();

            when(userRepository.findById(eq(invitedUserId))).thenReturn(Optional.of(invitedUser));
            when(householdMemberRepository.findByHouseholdIdAndUserId(eq(householdId), eq(invitedUserId)))
                    .thenReturn(Optional.empty());
            when(householdInvitationRepository.existsByHouseholdIdAndInvitedUserIdAndStatus(
                    eq(householdId), eq(invitedUserId), eq(InvitationStatus.PENDING)))
                    .thenReturn(false);
            when(householdInvitationRepository.save(any(HouseholdInvitation.class))).thenReturn(testInvitation);

            HouseholdInvitationResponse response = householdInvitationService.sendInvitation(
                    householdId, requestWithUserId, validToken);

            assertThat(response).isNotNull();
            verify(userRepository).findById(eq(invitedUserId));
            verify(userRepository, never()).findByEmail(anyString());
            verify(householdInvitationRepository).save(any(HouseholdInvitation.class));
        }

        @Test
        @DisplayName("Should throw NotFoundException when household not found")
        void shouldThrowNotFoundExceptionWhenHouseholdNotFound() {
            setupValidAuthentication();
            when(householdRepository.findById(eq(householdId))).thenReturn(Optional.empty());

            assertThatThrownBy(() -> householdInvitationService.sendInvitation(
                    householdId, testInvitationRequest, validToken))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("Household not found");

            verify(householdInvitationRepository, never()).save(any(HouseholdInvitation.class));
        }

        @Test
        @DisplayName("Should throw InsufficientPermissionException when user is not a member")
        void shouldThrowInsufficientPermissionExceptionWhenUserIsNotMember() {
            setupValidAuthentication();
            when(householdRepository.findById(eq(householdId))).thenReturn(Optional.of(testHousehold));
            when(householdMemberRepository.findByHouseholdIdAndUserId(eq(householdId), eq(currentUserId)))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> householdInvitationService.sendInvitation(
                    householdId, testInvitationRequest, validToken))
                    .isInstanceOf(InsufficientPermissionException.class)
                    .hasMessageContaining("don't have access to this household");

            verify(householdInvitationRepository, never()).save(any(HouseholdInvitation.class));
        }

        @Test
        @DisplayName("Should throw InsufficientPermissionException when user is regular member")
        void shouldThrowInsufficientPermissionExceptionWhenUserIsRegularMember() {
            setupRegularMemberAccess();

            assertThatThrownBy(() -> householdInvitationService.sendInvitation(
                    householdId, testInvitationRequest, validToken))
                    .isInstanceOf(InsufficientPermissionException.class)
                    .hasMessageContaining("don't have permission to send invitations");

            verify(householdInvitationRepository, never()).save(any(HouseholdInvitation.class));
        }

        @Test
        @DisplayName("Should throw NotFoundException when invited user by email not found")
        void shouldThrowNotFoundExceptionWhenInvitedUserByEmailNotFound() {
            setupOwnerAccess();
            when(userRepository.findByEmail(eq("invited@example.com"))).thenReturn(Optional.empty());

            assertThatThrownBy(() -> householdInvitationService.sendInvitation(
                    householdId, testInvitationRequest, validToken))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("User not found");

            verify(householdInvitationRepository, never()).save(any(HouseholdInvitation.class));
        }

        @Test
        @DisplayName("Should throw NotFoundException when invited user by ID not found")
        void shouldThrowNotFoundExceptionWhenInvitedUserByIdNotFound() {
            setupOwnerAccess();
            HouseholdInvitationRequest requestWithUserId = HouseholdInvitationRequest.builder()
                    .invitedUserId(invitedUserId)
                    .proposedRole(HouseholdRole.MEMBER)
                    .build();

            when(userRepository.findById(eq(invitedUserId))).thenReturn(Optional.empty());

            assertThatThrownBy(() -> householdInvitationService.sendInvitation(
                    householdId, requestWithUserId, validToken))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("User not found");

            verify(householdInvitationRepository, never()).save(any(HouseholdInvitation.class));
        }

        @Test
        @DisplayName("Should throw ValidationException when neither userId nor email provided")
        void shouldThrowValidationExceptionWhenNeitherUserIdNorEmailProvided() {
            setupOwnerAccess();
            HouseholdInvitationRequest invalidRequest = HouseholdInvitationRequest.builder()
                    .proposedRole(HouseholdRole.MEMBER)
                    .build();

            assertThatThrownBy(() -> householdInvitationService.sendInvitation(
                    householdId, invalidRequest, validToken))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Either invitedUserId or invitedUserEmail must be provided");

            verify(householdInvitationRepository, never()).save(any(HouseholdInvitation.class));
        }

        @Test
        @DisplayName("Should throw BusinessRuleViolationException when trying to invite self")
        void shouldThrowBusinessRuleViolationExceptionWhenTryingToInviteSelf() {
            setupOwnerAccess();
            when(userRepository.findByEmail(eq("invited@example.com"))).thenReturn(Optional.of(currentUser));

            assertThatThrownBy(() -> householdInvitationService.sendInvitation(
                    householdId, testInvitationRequest, validToken))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("Cannot invite yourself");

            verify(householdInvitationRepository, never()).save(any(HouseholdInvitation.class));
        }

        @Test
        @DisplayName("Should throw ValidationException when trying to invite as OWNER")
        void shouldThrowValidationExceptionWhenTryingToInviteAsOwner() {
            setupOwnerAccess();
            HouseholdInvitationRequest ownerRequest = HouseholdInvitationRequest.builder()
                    .invitedUserEmail("invited@example.com")
                    .proposedRole(HouseholdRole.OWNER)
                    .build();

            when(userRepository.findByEmail(eq("invited@example.com"))).thenReturn(Optional.of(invitedUser));

            assertThatThrownBy(() -> householdInvitationService.sendInvitation(
                    householdId, ownerRequest, validToken))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Cannot invite user as OWNER");

            verify(householdInvitationRepository, never()).save(any(HouseholdInvitation.class));
        }

        @Test
        @DisplayName("Should throw ValidationException when expiry date is in the past")
        void shouldThrowValidationExceptionWhenExpiryDateIsInThePast() {
            setupOwnerAccess();
            HouseholdInvitationRequest requestWithPastExpiry = HouseholdInvitationRequest.builder()
                    .invitedUserEmail("invited@example.com")
                    .proposedRole(HouseholdRole.MEMBER)
                    .expiresAt(LocalDateTime.now().minusDays(1))
                    .build();

            when(userRepository.findByEmail(eq("invited@example.com"))).thenReturn(Optional.of(invitedUser));

            assertThatThrownBy(() -> householdInvitationService.sendInvitation(
                    householdId, requestWithPastExpiry, validToken))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Expiry date cannot be in the past");

            verify(householdInvitationRepository, never()).save(any(HouseholdInvitation.class));
        }

        @Test
        @DisplayName("Should throw ConflictException when user is already a member")
        void shouldThrowConflictExceptionWhenUserIsAlreadyMember() {
            setupOwnerAccess();
            when(userRepository.findByEmail(eq("invited@example.com"))).thenReturn(Optional.of(invitedUser));
            when(householdMemberRepository.findByHouseholdIdAndUserId(eq(householdId), eq(invitedUserId)))
                    .thenReturn(Optional.of(regularMember));

            assertThatThrownBy(() -> householdInvitationService.sendInvitation(
                    householdId, testInvitationRequest, validToken))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("User is already a member of this household");

            verify(householdInvitationRepository, never()).save(any(HouseholdInvitation.class));
        }

        @Test
        @DisplayName("Should throw ConflictException when user already has pending invitation")
        void shouldThrowConflictExceptionWhenUserAlreadyHasPendingInvitation() {
            setupOwnerAccess();
            when(userRepository.findByEmail(eq("invited@example.com"))).thenReturn(Optional.of(invitedUser));
            when(householdMemberRepository.findByHouseholdIdAndUserId(eq(householdId), eq(invitedUserId)))
                    .thenReturn(Optional.empty());
            when(householdInvitationRepository.existsByHouseholdIdAndInvitedUserIdAndStatus(
                    eq(householdId), eq(invitedUserId), eq(InvitationStatus.PENDING)))
                    .thenReturn(true);

            assertThatThrownBy(() -> householdInvitationService.sendInvitation(
                    householdId, testInvitationRequest, validToken))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("User already has a pending invitation");

            verify(householdInvitationRepository, never()).save(any(HouseholdInvitation.class));
        }
    }

    @Nested
    @DisplayName("Accept Invitation Tests")
    class AcceptInvitationTests {

        @Test
        @DisplayName("Should accept invitation successfully")
        void shouldAcceptInvitationSuccessfully() {
            when(householdInvitationRepository.findById(eq(invitationId))).thenReturn(Optional.of(testInvitation));
            when(householdMemberRepository.findByHouseholdIdAndUserId(eq(householdId), eq(invitedUserId)))
                    .thenReturn(Optional.empty());
            when(householdInvitationRepository.save(any(HouseholdInvitation.class))).thenReturn(testInvitation);
            when(householdMemberRepository.save(any(HouseholdMember.class)))
                    .thenReturn(regularMember);

            // Set the current user to invited user for this test
            when(userService.getCurrentUser(eq(validToken))).thenReturn(invitedUser);

            HouseholdInvitationResponse response = householdInvitationService.acceptInvitation(
                    invitationId, validToken);

            assertThat(response).isNotNull();
            verify(householdMemberRepository).save(any(HouseholdMember.class));
            verify(householdInvitationRepository).save(any(HouseholdInvitation.class));
        }

        @Test
        @DisplayName("Should throw NotFoundException when invitation not found")
        void shouldThrowNotFoundExceptionWhenInvitationNotFound() {
            setupValidAuthentication();
            when(householdInvitationRepository.findById(eq(invitationId))).thenReturn(Optional.empty());

            assertThatThrownBy(() -> householdInvitationService.acceptInvitation(invitationId, validToken))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("Invitation not found");

            verify(householdMemberRepository, never()).save(any(HouseholdMember.class));
        }

        @Test
        @DisplayName("Should throw InsufficientPermissionException when user is not the invited user")
        void shouldThrowInsufficientPermissionExceptionWhenUserIsNotInvitedUser() {
            setupValidAuthentication();
            when(householdInvitationRepository.findById(eq(invitationId))).thenReturn(Optional.of(testInvitation));

            assertThatThrownBy(() -> householdInvitationService.acceptInvitation(invitationId, validToken))
                    .isInstanceOf(InsufficientPermissionException.class)
                    .hasMessageContaining("You are not the invited user");

            verify(householdMemberRepository, never()).save(any(HouseholdMember.class));
        }

        @Test
        @DisplayName("Should throw ResourceStateException when invitation is already processed")
        void shouldThrowResourceStateExceptionWhenInvitationIsAlreadyProcessed() {
            testInvitation.setStatus(InvitationStatus.ACCEPTED);
            when(householdInvitationRepository.findById(eq(invitationId))).thenReturn(Optional.of(testInvitation));
            when(userService.getCurrentUser(eq(validToken))).thenReturn(invitedUser);

            assertThatThrownBy(() -> householdInvitationService.acceptInvitation(invitationId, validToken))
                    .isInstanceOf(ResourceStateException.class)
                    .hasMessageContaining("Invitation has already been processed");

            verify(householdMemberRepository, never()).save(any(HouseholdMember.class));
        }

        @Test
        @DisplayName("Should throw ResourceStateException when invitation is expired")
        void shouldThrowResourceStateExceptionWhenInvitationIsExpired() {
            testInvitation.setExpiresAt(LocalDateTime.now().minusDays(1));
            when(householdInvitationRepository.findById(eq(invitationId))).thenReturn(Optional.of(testInvitation));
            when(userService.getCurrentUser(eq(validToken))).thenReturn(invitedUser);

            assertThatThrownBy(() -> householdInvitationService.acceptInvitation(invitationId, validToken))
                    .isInstanceOf(ResourceStateException.class)
                    .hasMessageContaining("Invitation has expired");

            verify(householdMemberRepository, never()).save(any(HouseholdMember.class));
        }

        @Test
        @DisplayName("Should throw ConflictException when user is already a member")
        void shouldThrowConflictExceptionWhenUserIsAlreadyMember() {
            when(householdInvitationRepository.findById(eq(invitationId))).thenReturn(Optional.of(testInvitation));
            when(userService.getCurrentUser(eq(validToken))).thenReturn(invitedUser);
            when(householdMemberRepository.findByHouseholdIdAndUserId(eq(householdId), eq(invitedUserId)))
                    .thenReturn(Optional.of(regularMember));

            assertThatThrownBy(() -> householdInvitationService.acceptInvitation(invitationId, validToken))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("You are already a member of this household");

            verify(householdMemberRepository, never()).save(any(HouseholdMember.class));
        }
    }

    @Nested
    @DisplayName("Decline Invitation Tests")
    class DeclineInvitationTests {

        @Test
        @DisplayName("Should decline invitation successfully")
        void shouldDeclineInvitationSuccessfully() {
            when(householdInvitationRepository.findById(eq(invitationId))).thenReturn(Optional.of(testInvitation));
            when(userService.getCurrentUser(eq(validToken))).thenReturn(invitedUser);
            when(householdInvitationRepository.save(any(HouseholdInvitation.class))).thenReturn(testInvitation);

            HouseholdInvitationResponse response = householdInvitationService.declineInvitation(
                    invitationId, validToken);

            assertThat(response).isNotNull();
            verify(householdInvitationRepository).save(any(HouseholdInvitation.class));
        }

        @Test
        @DisplayName("Should throw NotFoundException when invitation not found")
        void shouldThrowNotFoundExceptionWhenInvitationNotFound() {
            setupValidAuthentication();
            when(householdInvitationRepository.findById(eq(invitationId))).thenReturn(Optional.empty());

            assertThatThrownBy(() -> householdInvitationService.declineInvitation(invitationId, validToken))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("Invitation not found");

            verify(householdInvitationRepository, never()).save(any(HouseholdInvitation.class));
        }

        @Test
        @DisplayName("Should throw InsufficientPermissionException when user is not the invited user")
        void shouldThrowInsufficientPermissionExceptionWhenUserIsNotInvitedUser() {
            setupValidAuthentication();
            when(householdInvitationRepository.findById(eq(invitationId))).thenReturn(Optional.of(testInvitation));

            assertThatThrownBy(() -> householdInvitationService.declineInvitation(invitationId, validToken))
                    .isInstanceOf(InsufficientPermissionException.class)
                    .hasMessageContaining("You are not the invited user");

            verify(householdInvitationRepository, never()).save(any(HouseholdInvitation.class));
        }

        @Test
        @DisplayName("Should throw ResourceStateException when invitation is already processed")
        void shouldThrowResourceStateExceptionWhenInvitationIsAlreadyProcessed() {
            testInvitation.setStatus(InvitationStatus.DECLINED);
            when(householdInvitationRepository.findById(eq(invitationId))).thenReturn(Optional.of(testInvitation));
            when(userService.getCurrentUser(eq(validToken))).thenReturn(invitedUser);

            assertThatThrownBy(() -> householdInvitationService.declineInvitation(invitationId, validToken))
                    .isInstanceOf(ResourceStateException.class)
                    .hasMessageContaining("Invitation has already been processed");

            verify(householdInvitationRepository, never()).save(any(HouseholdInvitation.class));
        }
    }

    @Nested
    @DisplayName("Cancel Invitation Tests")
    class CancelInvitationTests {

        @Test
        @DisplayName("Should cancel invitation successfully when user is owner")
        void shouldCancelInvitationSuccessfullyWhenUserIsOwner() {
            setupOwnerAccess();
            when(householdInvitationRepository.findById(eq(invitationId))).thenReturn(Optional.of(testInvitation));
            when(householdInvitationRepository.save(any(HouseholdInvitation.class))).thenReturn(testInvitation);

            householdInvitationService.cancelInvitation(householdId, invitationId, validToken);

            verify(householdInvitationRepository).save(any(HouseholdInvitation.class));
        }

        @Test
        @DisplayName("Should cancel invitation successfully when user is admin")
        void shouldCancelInvitationSuccessfullyWhenUserIsAdmin() {
            setupAdminAccess();
            when(householdInvitationRepository.findById(eq(invitationId))).thenReturn(Optional.of(testInvitation));

            householdInvitationService.cancelInvitation(householdId, invitationId, validToken);

            verify(householdInvitationRepository).save(any(HouseholdInvitation.class));
        }

        @Test
        @DisplayName("Should throw NotFoundException when household not found")
        void shouldThrowNotFoundExceptionWhenHouseholdNotFound() {
            setupValidAuthentication();
            when(householdRepository.findById(eq(householdId))).thenReturn(Optional.empty());

            assertThatThrownBy(() -> householdInvitationService.cancelInvitation(
                    householdId, invitationId, validToken))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("Household not found");

            verify(householdInvitationRepository, never()).save(any(HouseholdInvitation.class));
        }

        @Test
        @DisplayName("Should throw InsufficientPermissionException when user is not a member")
        void shouldThrowInsufficientPermissionExceptionWhenUserIsNotMember() {
            setupValidAuthentication();
            when(householdRepository.findById(eq(householdId))).thenReturn(Optional.of(testHousehold));
            when(householdMemberRepository.findByHouseholdIdAndUserId(eq(householdId), eq(currentUserId)))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> householdInvitationService.cancelInvitation(
                    householdId, invitationId, validToken))
                    .isInstanceOf(InsufficientPermissionException.class)
                    .hasMessageContaining("don't have access to this household");

            verify(householdInvitationRepository, never()).save(any(HouseholdInvitation.class));
        }

        @Test
        @DisplayName("Should throw InsufficientPermissionException when user is regular member")
        void shouldThrowInsufficientPermissionExceptionWhenUserIsRegularMember() {
            setupRegularMemberAccess();

            assertThatThrownBy(() -> householdInvitationService.cancelInvitation(
                    householdId, invitationId, validToken))
                    .isInstanceOf(InsufficientPermissionException.class)
                    .hasMessageContaining("don't have permission");

            verify(householdInvitationRepository, never()).save(any(HouseholdInvitation.class));
        }

        @Test
        @DisplayName("Should throw NotFoundException when invitation not found")
        void shouldThrowNotFoundExceptionWhenInvitationNotFound() {
            setupOwnerAccess();
            when(householdInvitationRepository.findById(eq(invitationId))).thenReturn(Optional.empty());

            assertThatThrownBy(() -> householdInvitationService.cancelInvitation(
                    householdId, invitationId, validToken))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("Invitation not found");

            verify(householdInvitationRepository, never()).save(any(HouseholdInvitation.class));
        }

        @Test
        @DisplayName("Should throw ResourceStateException when invitation is already processed")
        void shouldThrowResourceStateExceptionWhenInvitationIsAlreadyProcessed() {
            setupOwnerAccess();
            testInvitation.setStatus(InvitationStatus.ACCEPTED);
            when(householdInvitationRepository.findById(eq(invitationId))).thenReturn(Optional.of(testInvitation));

            assertThatThrownBy(() -> householdInvitationService.cancelInvitation(
                    householdId, invitationId, validToken))
                    .isInstanceOf(ResourceStateException.class)
                    .hasMessageContaining("Cannot cancel processed invitation");

            verify(householdInvitationRepository, never()).save(any(HouseholdInvitation.class));
        }
    }

    @Nested
    @DisplayName("Get Invitations Tests")
    class GetInvitationsTests {

        @Test
        @DisplayName("Should get household invitations successfully")
        void shouldGetHouseholdInvitationsSuccessfully() {
            setupOwnerAccess();
            when(householdInvitationRepository.findByHouseholdId(eq(householdId)))
                    .thenReturn(java.util.Collections.singletonList(testInvitation));

            List<HouseholdInvitationResponse> responses = householdInvitationService.getHouseholdInvitations(
                    householdId, validToken);

            assertThat(responses).hasSize(1);
            assertThat(responses.getFirst().getId()).isEqualTo(invitationId);
        }

        @Test
        @DisplayName("Should get household invitations by status successfully")
        void shouldGetHouseholdInvitationsByStatusSuccessfully() {
            setupOwnerAccess();
            when(householdInvitationRepository.findByHouseholdIdAndStatus(eq(householdId), eq(InvitationStatus.PENDING)))
                    .thenReturn(java.util.Collections.singletonList(testInvitation));

            List<HouseholdInvitationResponse> responses = householdInvitationService.getHouseholdInvitationsByStatus(
                    householdId, InvitationStatus.PENDING, validToken);

            assertThat(responses).hasSize(1);
            assertThat(responses.getFirst().getStatus()).isEqualTo(InvitationStatus.PENDING);
        }

        @Test
        @DisplayName("Should get my invitations successfully")
        void shouldGetMyInvitationsSuccessfully() {
            setupValidAuthentication();
            when(householdInvitationRepository.findByInvitedUserIdAndStatus(eq(currentUserId), eq(InvitationStatus.PENDING)))
                    .thenReturn(java.util.Collections.singletonList(testInvitation));

            List<HouseholdInvitationResponse> responses = householdInvitationService.getMyInvitations(validToken);

            assertThat(responses).hasSize(1);
        }

        @Test
        @DisplayName("Should get my invitations by status successfully")
        void shouldGetMyInvitationsByStatusSuccessfully() {
            setupValidAuthentication();
            when(householdInvitationRepository.findByInvitedUserIdAndStatus(
                    eq(currentUserId), eq(InvitationStatus.PENDING)))
                    .thenReturn(java.util.Collections.singletonList(testInvitation));

            List<HouseholdInvitationResponse> responses = householdInvitationService.getMyInvitationsByStatus(
                    InvitationStatus.PENDING, validToken);

            assertThat(responses).hasSize(1);
            assertThat(responses.getFirst().getStatus()).isEqualTo(InvitationStatus.PENDING);
        }

        @Test
        @DisplayName("Should get invitation by ID successfully")
        void shouldGetInvitationByIdSuccessfully() {
            setupOwnerAccess();
            when(householdInvitationRepository.findById(eq(invitationId))).thenReturn(Optional.of(testInvitation));

            HouseholdInvitationResponse response = householdInvitationService.getInvitationById(
                    householdId, invitationId, validToken);

            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo(invitationId);
        }

        @Test
        @DisplayName("Should throw NotFoundException when invitation by ID not found")
        void shouldThrowNotFoundExceptionWhenInvitationByIdNotFound() {
            setupOwnerAccess();
            when(householdInvitationRepository.findById(eq(invitationId))).thenReturn(Optional.empty());

            assertThatThrownBy(() -> householdInvitationService.getInvitationById(
                    householdId, invitationId, validToken))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("Invitation not found");
        }

        @Test
        @DisplayName("Should throw NotFoundException when household not found for getHouseholdInvitations")
        void shouldThrowNotFoundExceptionWhenHouseholdNotFoundForGetHouseholdInvitations() {
            setupValidAuthentication();
            when(householdRepository.findById(eq(householdId))).thenReturn(Optional.empty());

            assertThatThrownBy(() -> householdInvitationService.getHouseholdInvitations(householdId, validToken))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("Household not found");
        }

        @Test
        @DisplayName("Should throw InsufficientPermissionException when user not member for getHouseholdInvitations")
        void shouldThrowInsufficientPermissionExceptionWhenUserNotMemberForGetHouseholdInvitations() {
            setupValidAuthentication();
            when(householdRepository.findById(eq(householdId))).thenReturn(Optional.of(testHousehold));
            when(householdMemberRepository.findByHouseholdIdAndUserId(eq(householdId), eq(currentUserId)))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> householdInvitationService.getHouseholdInvitations(householdId, validToken))
                    .isInstanceOf(InsufficientPermissionException.class)
                    .hasMessageContaining("don't have access to this household");
        }
    }

    @Nested
    @DisplayName("Mark Expired Invitations Tests")
    class MarkExpiredInvitationsTests {

        @Test
        @DisplayName("Should mark expired invitations successfully")
        void shouldMarkExpiredInvitationsSuccessfully() {
            when(householdInvitationRepository.findByStatusAndExpiresAtBefore(
                    eq(InvitationStatus.PENDING), any(LocalDateTime.class)))
                    .thenReturn(java.util.Collections.singletonList(testInvitation));
            when(householdInvitationRepository.save(any(HouseholdInvitation.class)))
                    .thenReturn(testInvitation);

            householdInvitationService.markExpiredInvitations();

            verify(householdInvitationRepository).findByStatusAndExpiresAtBefore(
                    eq(InvitationStatus.PENDING), any(LocalDateTime.class));
            verify(householdInvitationRepository).save(any(HouseholdInvitation.class));
        }

        @Test
        @DisplayName("Should handle no expired invitations")
        void shouldHandleNoExpiredInvitations() {
            when(householdInvitationRepository.findByStatusAndExpiresAtBefore(
                    eq(InvitationStatus.PENDING), any(LocalDateTime.class)))
                    .thenReturn(java.util.Collections.emptyList());

            householdInvitationService.markExpiredInvitations();

            verify(householdInvitationRepository).findByStatusAndExpiresAtBefore(
                    eq(InvitationStatus.PENDING), any(LocalDateTime.class));
            verify(householdInvitationRepository, never()).save(any(HouseholdInvitation.class));
        }
    }
}