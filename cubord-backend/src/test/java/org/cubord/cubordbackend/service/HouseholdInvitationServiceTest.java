package org.cubord.cubordbackend.service;

import org.cubord.cubordbackend.domain.HouseholdRole;
import org.cubord.cubordbackend.domain.InvitationStatus;
import org.cubord.cubordbackend.dto.HouseholdInvitationRequest;
import org.cubord.cubordbackend.dto.HouseholdInvitationResponse;
import org.cubord.cubordbackend.domain.*;
import org.cubord.cubordbackend.exception.ConflictException;
import org.cubord.cubordbackend.exception.NotFoundException;
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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class HouseholdInvitationServiceTest {
    @Mock
    private HouseholdRepository householdRepository;
    @Mock
    private HouseholdMemberRepository householdMemberRepository;
    @Mock
    private HouseholdInvitationRepository householdInvitationRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private UserService userService;
    @Mock
    private JwtAuthenticationToken token;
    @InjectMocks
    private HouseholdInvitationService householdInvitationService;

    private User currentUser;
    private User invitedUser;
    private Household testHousehold;
    private HouseholdMember ownerMember;
    private HouseholdMember adminMember;
    private HouseholdMember regularMember;
    private HouseholdInvitation testInvitation;
    private UUID currentUserId;
    private UUID invitedUserId;
    private UUID householdId;
    private UUID invitationId;

    @BeforeEach
    void setUp() {
        currentUserId = UUID.randomUUID();
        invitedUserId = UUID.randomUUID();
        householdId = UUID.randomUUID();
        invitationId = UUID.randomUUID();

        setupUsers();
        setupHousehold();
        setupHouseholdMembers();
        setupInvitation();
    }

    private void setupUsers() {
        currentUser = new User();
        currentUser.setId(currentUserId);
        currentUser.setEmail("current@example.com");
        currentUser.setUsername("currentuser");

        invitedUser = new User();
        invitedUser.setId(invitedUserId);
        invitedUser.setEmail("invited@example.com");
        invitedUser.setUsername("inviteduser");
    }

    private void setupHousehold() {
        testHousehold = new Household();
        testHousehold.setId(householdId);
        testHousehold.setName("Test Household");
    }

    private void setupHouseholdMembers() {
        ownerMember = createHouseholdMember(HouseholdRole.OWNER);
        adminMember = createHouseholdMember(HouseholdRole.ADMIN);
        regularMember = createHouseholdMember(HouseholdRole.MEMBER);
    }

    private HouseholdMember createHouseholdMember(HouseholdRole role) {
        HouseholdMember member = new HouseholdMember();
        member.setId(UUID.randomUUID());
        member.setUser(currentUser);
        member.setHousehold(testHousehold);
        member.setRole(role);
        return member;
    }

    private void setupInvitation() {
        testInvitation = new HouseholdInvitation();
        testInvitation.setId(invitationId);
        testInvitation.setInvitedUser(invitedUser);
        testInvitation.setHousehold(testHousehold);
        testInvitation.setInvitedBy(currentUser);
        testInvitation.setProposedRole(HouseholdRole.MEMBER);
        testInvitation.setStatus(InvitationStatus.PENDING);
        testInvitation.setCreatedAt(LocalDateTime.now());
        testInvitation.setExpiresAt(LocalDateTime.now().plusDays(7));
    }

    private void setupSuccessfulInvitationMocks(HouseholdMember member) {
        when(userService.getCurrentUser(token)).thenReturn(currentUser);
        when(householdRepository.findById(householdId)).thenReturn(Optional.of(testHousehold));
        when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, currentUserId))
                .thenReturn(Optional.of(member));
        when(userRepository.findByEmail("invited@example.com")).thenReturn(Optional.of(invitedUser));
        when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, invitedUserId))
                .thenReturn(Optional.empty());
        when(householdInvitationRepository.existsByHouseholdIdAndInvitedUserIdAndStatus(
                householdId, invitedUserId, InvitationStatus.PENDING)).thenReturn(false);
        when(householdInvitationRepository.save(any(HouseholdInvitation.class))).thenReturn(testInvitation);
    }

    private void setupBasicInvitationMocks() {
        when(userService.getCurrentUser(token)).thenReturn(currentUser);
        when(householdRepository.findById(householdId)).thenReturn(Optional.of(testHousehold));
    }

    private HouseholdInvitationRequest createInvitationRequest(String email, HouseholdRole role) {
        return HouseholdInvitationRequest.builder()
                .invitedUserEmail(email)
                .proposedRole(role)
                .build();
    }

    private HouseholdInvitationRequest createInvitationRequestWithUserId(UUID userId, HouseholdRole role) {
        return HouseholdInvitationRequest.builder()
                .invitedUserId(userId)
                .proposedRole(role)
                .build();
    }

    private void assertSuccessfulInvitationResponse(HouseholdInvitationResponse response) {
        assertThat(response).isNotNull();
        assertThat(response.getInvitedUserEmail()).isEqualTo("invited@example.com");
        assertThat(response.getProposedRole()).isEqualTo(HouseholdRole.MEMBER);
        assertThat(response.getStatus()).isEqualTo(InvitationStatus.PENDING);
    }

    @Nested
    @DisplayName("Send Invitation Tests")
    class SendInvitationTests {

        @Test
        @DisplayName("Should send invitation successfully when user is owner")
        void shouldSendInvitationSuccessfullyWhenUserIsOwner() {
            // Given
            HouseholdInvitationRequest request = createInvitationRequest("invited@example.com", HouseholdRole.MEMBER);
            setupSuccessfulInvitationMocks(ownerMember);

            // When
            HouseholdInvitationResponse response = householdInvitationService.sendInvitation(householdId, request, token);

            // Then
            assertSuccessfulInvitationResponse(response);
            verify(householdInvitationRepository).save(any(HouseholdInvitation.class));
        }

        @Test
        @DisplayName("Should send invitation successfully when user is admin")
        void shouldSendInvitationSuccessfullyWhenUserIsAdmin() {
            // Given
            HouseholdInvitationRequest request = createInvitationRequest("invited@example.com", HouseholdRole.MEMBER);
            setupSuccessfulInvitationMocks(adminMember);

            // When
            HouseholdInvitationResponse response = householdInvitationService.sendInvitation(householdId, request, token);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getProposedRole()).isEqualTo(HouseholdRole.MEMBER);
        }

        @Test
        @DisplayName("Should send invitation by user ID when provided")
        void shouldSendInvitationByUserIdWhenProvided() {
            // Given
            HouseholdInvitationRequest request = createInvitationRequestWithUserId(invitedUserId, HouseholdRole.MEMBER);
            when(userService.getCurrentUser(token)).thenReturn(currentUser);
            when(householdRepository.findById(householdId)).thenReturn(Optional.of(testHousehold));
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, currentUserId))
                    .thenReturn(Optional.of(ownerMember));
            when(userRepository.findById(invitedUserId)).thenReturn(Optional.of(invitedUser));
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, invitedUserId))
                    .thenReturn(Optional.empty());
            when(householdInvitationRepository.existsByHouseholdIdAndInvitedUserIdAndStatus(
                    householdId, invitedUserId, InvitationStatus.PENDING)).thenReturn(false);
            when(householdInvitationRepository.save(any(HouseholdInvitation.class))).thenReturn(testInvitation);

            // When
            HouseholdInvitationResponse response = householdInvitationService.sendInvitation(householdId, request, token);

            // Then
            assertThat(response).isNotNull();
            verify(userRepository).findById(invitedUserId);
            verify(userRepository, never()).findByEmail(anyString());
        }

        @Test
        @DisplayName("Should throw NotFoundException when household doesn't exist")
        void shouldThrowNotFoundExceptionWhenHouseholdDoesntExist() {
            // Given
            HouseholdInvitationRequest request = createInvitationRequest("invited@example.com", HouseholdRole.MEMBER);
            when(userService.getCurrentUser(token)).thenReturn(currentUser);
            when(householdRepository.findById(householdId)).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> householdInvitationService.sendInvitation(householdId, request, token))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("Household not found");
        }

        @Test
        @DisplayName("Should throw AccessDeniedException when user is not a member")
        void shouldThrowAccessDeniedExceptionWhenUserIsNotMember() {
            // Given
            HouseholdInvitationRequest request = createInvitationRequest("invited@example.com", HouseholdRole.MEMBER);
            setupBasicInvitationMocks();
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, currentUserId))
                    .thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> householdInvitationService.sendInvitation(householdId, request, token))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessage("You don't have access to this household");
        }

        @Test
        @DisplayName("Should throw AccessDeniedException when user is regular member")
        void shouldThrowAccessDeniedExceptionWhenUserIsRegularMember() {
            // Given
            HouseholdInvitationRequest request = createInvitationRequest("invited@example.com", HouseholdRole.MEMBER);
            setupBasicInvitationMocks();
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, currentUserId))
                    .thenReturn(Optional.of(regularMember));

            // When & Then
            assertThatThrownBy(() -> householdInvitationService.sendInvitation(householdId, request, token))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessage("You don't have permission to send invitations");
        }

        @Test
        @DisplayName("Should throw NotFoundException when invited user doesn't exist")
        void shouldThrowNotFoundExceptionWhenInvitedUserDoesntExist() {
            // Given
            HouseholdInvitationRequest request = createInvitationRequest("nonexistent@example.com", HouseholdRole.MEMBER);
            setupBasicInvitationMocks();
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, currentUserId))
                    .thenReturn(Optional.of(ownerMember));
            when(userRepository.findByEmail("nonexistent@example.com")).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> householdInvitationService.sendInvitation(householdId, request, token))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("User not found");
        }

        @Test
        @DisplayName("Should throw ConflictException when user already has pending invitation")
        void shouldThrowConflictExceptionWhenUserAlreadyHasPendingInvitation() {
            // Given
            HouseholdInvitationRequest request = createInvitationRequest("invited@example.com", HouseholdRole.MEMBER);
            setupBasicInvitationMocks();
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, currentUserId))
                    .thenReturn(Optional.of(ownerMember));
            when(userRepository.findByEmail("invited@example.com")).thenReturn(Optional.of(invitedUser));
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, invitedUserId))
                    .thenReturn(Optional.empty());
            when(householdInvitationRepository.existsByHouseholdIdAndInvitedUserIdAndStatus(
                    householdId, invitedUserId, InvitationStatus.PENDING)).thenReturn(true);

            // When & Then
            assertThatThrownBy(() -> householdInvitationService.sendInvitation(householdId, request, token))
                    .isInstanceOf(ConflictException.class)
                    .hasMessage("User already has a pending invitation");
        }

        @Test
        @DisplayName("Should throw ConflictException when user is already a member")
        void shouldThrowConflictExceptionWhenUserIsAlreadyMember() {
            // Given
            HouseholdInvitationRequest request = createInvitationRequest("invited@example.com", HouseholdRole.MEMBER);
            setupBasicInvitationMocks();
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, currentUserId))
                    .thenReturn(Optional.of(ownerMember));
            when(userRepository.findByEmail("invited@example.com")).thenReturn(Optional.of(invitedUser));
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, invitedUserId))
                    .thenReturn(Optional.of(regularMember));

            // When & Then
            assertThatThrownBy(() -> householdInvitationService.sendInvitation(householdId, request, token))
                    .isInstanceOf(ConflictException.class)
                    .hasMessage("User is already a member of this household");
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException when trying to invite as OWNER")
        void shouldThrowIllegalArgumentExceptionWhenTryingToInviteAsOwner() {
            // Given
            HouseholdInvitationRequest request = createInvitationRequest("invited@example.com", HouseholdRole.OWNER);
            setupBasicInvitationMocks();
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, currentUserId))
                    .thenReturn(Optional.of(ownerMember));
            when(userRepository.findByEmail("invited@example.com")).thenReturn(Optional.of(invitedUser));

            // When & Then
            assertThatThrownBy(() -> householdInvitationService.sendInvitation(householdId, request, token))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Cannot invite user as OWNER");
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException when trying to invite self")
        void shouldThrowIllegalArgumentExceptionWhenTryingToInviteSelf() {
            // Given
            HouseholdInvitationRequest request = createInvitationRequest("current@example.com", HouseholdRole.MEMBER);
            setupBasicInvitationMocks();
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, currentUserId))
                    .thenReturn(Optional.of(ownerMember));
            when(userRepository.findByEmail("current@example.com")).thenReturn(Optional.of(currentUser));

            // When & Then
            assertThatThrownBy(() -> householdInvitationService.sendInvitation(householdId, request, token))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Cannot invite yourself");
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException when expiry date is in the past")
        void shouldThrowIllegalArgumentExceptionWhenExpiryDateIsInThePast() {
            // Given
            HouseholdInvitationRequest request = HouseholdInvitationRequest.builder()
                    .invitedUserEmail("invited@example.com")
                    .proposedRole(HouseholdRole.MEMBER)
                    .expiresAt(LocalDateTime.now().minusDays(1))
                    .build();
            setupBasicInvitationMocks();
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, currentUserId))
                    .thenReturn(Optional.of(ownerMember));
            when(userRepository.findByEmail("invited@example.com")).thenReturn(Optional.of(invitedUser));

            // When & Then
            assertThatThrownBy(() -> householdInvitationService.sendInvitation(householdId, request, token))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Expiry date cannot be in the past");
        }
    }

    @Nested
    @DisplayName("Accept Invitation Tests")
    class AcceptInvitationTests {

        @Test
        @DisplayName("Should accept invitation successfully")
        void shouldAcceptInvitationSuccessfully() {
            // Given
            when(userService.getCurrentUser(token)).thenReturn(invitedUser);
            when(householdInvitationRepository.findById(invitationId)).thenReturn(Optional.of(testInvitation));
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, invitedUserId))
                    .thenReturn(Optional.empty());
            when(householdInvitationRepository.save(any(HouseholdInvitation.class))).thenReturn(testInvitation);
            when(householdMemberRepository.save(any(HouseholdMember.class))).thenReturn(new HouseholdMember());

            // When
            HouseholdInvitationResponse response = householdInvitationService.acceptInvitation(invitationId, token);

            // Then
            assertThat(response).isNotNull();
            verify(householdInvitationRepository).save(any(HouseholdInvitation.class));
            verify(householdMemberRepository).save(any(HouseholdMember.class));
        }

        @Test
        @DisplayName("Should throw NotFoundException when invitation doesn't exist")
        void shouldThrowNotFoundExceptionWhenInvitationDoesntExist() {
            // Given
            when(userService.getCurrentUser(token)).thenReturn(invitedUser);
            when(householdInvitationRepository.findById(invitationId)).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> householdInvitationService.acceptInvitation(invitationId, token))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("Invitation not found");
        }

        @Test
        @DisplayName("Should throw AccessDeniedException when user is not the invited user")
        void shouldThrowAccessDeniedExceptionWhenUserIsNotInvitedUser() {
            // Given
            when(userService.getCurrentUser(token)).thenReturn(currentUser);
            when(householdInvitationRepository.findById(invitationId)).thenReturn(Optional.of(testInvitation));

            // When & Then
            assertThatThrownBy(() -> householdInvitationService.acceptInvitation(invitationId, token))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessage("You can only accept your own invitations");
        }

        @Test
        @DisplayName("Should throw IllegalStateException when invitation is already processed")
        void shouldThrowIllegalStateExceptionWhenInvitationIsAlreadyProcessed() {
            // Given
            testInvitation.setStatus(InvitationStatus.ACCEPTED);
            when(userService.getCurrentUser(token)).thenReturn(invitedUser);
            when(householdInvitationRepository.findById(invitationId)).thenReturn(Optional.of(testInvitation));

            // When & Then
            assertThatThrownBy(() -> householdInvitationService.acceptInvitation(invitationId, token))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Invitation has already been processed");
        }

        @Test
        @DisplayName("Should throw IllegalStateException when invitation is expired")
        void shouldThrowIllegalStateExceptionWhenInvitationIsExpired() {
            // Given
            testInvitation.setExpiresAt(LocalDateTime.now().minusDays(1));
            when(userService.getCurrentUser(token)).thenReturn(invitedUser);
            when(householdInvitationRepository.findById(invitationId)).thenReturn(Optional.of(testInvitation));

            // When & Then
            assertThatThrownBy(() -> householdInvitationService.acceptInvitation(invitationId, token))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Invitation has expired");
        }

        @Test
        @DisplayName("Should throw ConflictException when user is already a member")
        void shouldThrowConflictExceptionWhenUserIsAlreadyMember() {
            // Given
            when(userService.getCurrentUser(token)).thenReturn(invitedUser);
            when(householdInvitationRepository.findById(invitationId)).thenReturn(Optional.of(testInvitation));
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, invitedUserId))
                    .thenReturn(Optional.of(regularMember));

            // When & Then
            assertThatThrownBy(() -> householdInvitationService.acceptInvitation(invitationId, token))
                    .isInstanceOf(ConflictException.class)
                    .hasMessage("User is already a member of this household");
        }
    }

    @Nested
    @DisplayName("Decline Invitation Tests")
    class DeclineInvitationTests {

        @Test
        @DisplayName("Should decline invitation successfully")
        void shouldDeclineInvitationSuccessfully() {
            // Given
            when(userService.getCurrentUser(token)).thenReturn(invitedUser);
            when(householdInvitationRepository.findById(invitationId)).thenReturn(Optional.of(testInvitation));
            when(householdInvitationRepository.save(any(HouseholdInvitation.class))).thenReturn(testInvitation);

            // When
            HouseholdInvitationResponse response = householdInvitationService.declineInvitation(invitationId, token);

            // Then
            assertThat(response).isNotNull();
            verify(householdInvitationRepository).save(any(HouseholdInvitation.class));
        }

        @Test
        @DisplayName("Should throw NotFoundException when invitation doesn't exist")
        void shouldThrowNotFoundExceptionWhenInvitationDoesntExist() {
            // Given
            when(userService.getCurrentUser(token)).thenReturn(invitedUser);
            when(householdInvitationRepository.findById(invitationId)).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> householdInvitationService.declineInvitation(invitationId, token))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("Invitation not found");
        }

        @Test
        @DisplayName("Should throw AccessDeniedException when user is not the invited user")
        void shouldThrowAccessDeniedExceptionWhenUserIsNotInvitedUser() {
            // Given
            when(userService.getCurrentUser(token)).thenReturn(currentUser);
            when(householdInvitationRepository.findById(invitationId)).thenReturn(Optional.of(testInvitation));

            // When & Then
            assertThatThrownBy(() -> householdInvitationService.declineInvitation(invitationId, token))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessage("You can only decline your own invitations");
        }

        @Test
        @DisplayName("Should throw IllegalStateException when invitation is already processed")
        void shouldThrowIllegalStateExceptionWhenInvitationIsAlreadyProcessed() {
            // Given
            testInvitation.setStatus(InvitationStatus.ACCEPTED);
            when(userService.getCurrentUser(token)).thenReturn(invitedUser);
            when(householdInvitationRepository.findById(invitationId)).thenReturn(Optional.of(testInvitation));

            // When & Then
            assertThatThrownBy(() -> householdInvitationService.declineInvitation(invitationId, token))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Invitation has already been processed");
        }
    }

    @Nested
    @DisplayName("Cancel Invitation Tests")
    class CancelInvitationTests {

        @Test
        @DisplayName("Should cancel invitation successfully when user is owner")
        void shouldCancelInvitationSuccessfullyWhenUserIsOwner() {
            // Given
            when(userService.getCurrentUser(token)).thenReturn(currentUser);
            when(householdRepository.findById(householdId)).thenReturn(Optional.of(testHousehold));
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, currentUserId))
                    .thenReturn(Optional.of(ownerMember));
            when(householdInvitationRepository.findById(invitationId)).thenReturn(Optional.of(testInvitation));
            when(householdInvitationRepository.save(any(HouseholdInvitation.class))).thenReturn(testInvitation);

            // When
            householdInvitationService.cancelInvitation(householdId, invitationId, token);

            // Then
            verify(householdInvitationRepository).save(any(HouseholdInvitation.class));
        }

        @Test
        @DisplayName("Should throw NotFoundException when household doesn't exist")
        void shouldThrowNotFoundExceptionWhenHouseholdDoesntExist() {
            // Given
            when(userService.getCurrentUser(token)).thenReturn(currentUser);
            when(householdRepository.findById(householdId)).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> householdInvitationService.cancelInvitation(householdId, invitationId, token))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("Household not found");
        }

        @Test
        @DisplayName("Should throw AccessDeniedException when user is not a member")
        void shouldThrowAccessDeniedExceptionWhenUserIsNotMember() {
            // Given
            when(userService.getCurrentUser(token)).thenReturn(currentUser);
            when(householdRepository.findById(householdId)).thenReturn(Optional.of(testHousehold));
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, currentUserId))
                    .thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> householdInvitationService.cancelInvitation(householdId, invitationId, token))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessage("You don't have access to this household");
        }

        @Test
        @DisplayName("Should throw AccessDeniedException when user is regular member")
        void shouldThrowAccessDeniedExceptionWhenUserIsRegularMember() {
            // Given
            when(userService.getCurrentUser(token)).thenReturn(currentUser);
            when(householdRepository.findById(householdId)).thenReturn(Optional.of(testHousehold));
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, currentUserId))
                    .thenReturn(Optional.of(regularMember));

            // When & Then
            assertThatThrownBy(() -> householdInvitationService.cancelInvitation(householdId, invitationId, token))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessage("You don't have permission to perform this action");
        }

        @Test
        @DisplayName("Should throw NotFoundException when invitation doesn't exist")
        void shouldThrowNotFoundExceptionWhenInvitationDoesntExist() {
            // Given
            when(userService.getCurrentUser(token)).thenReturn(currentUser);
            when(householdRepository.findById(householdId)).thenReturn(Optional.of(testHousehold));
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, currentUserId))
                    .thenReturn(Optional.of(ownerMember));
            when(householdInvitationRepository.findById(invitationId)).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> householdInvitationService.cancelInvitation(householdId, invitationId, token))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("Invitation not found");
        }

        @Test
        @DisplayName("Should throw IllegalStateException when invitation is already processed")
        void shouldThrowIllegalStateExceptionWhenInvitationIsAlreadyProcessed() {
            // Given
            testInvitation.setStatus(InvitationStatus.ACCEPTED);
            when(userService.getCurrentUser(token)).thenReturn(currentUser);
            when(householdRepository.findById(householdId)).thenReturn(Optional.of(testHousehold));
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, currentUserId))
                    .thenReturn(Optional.of(ownerMember));
            when(householdInvitationRepository.findById(invitationId)).thenReturn(Optional.of(testInvitation));

            // When & Then
            assertThatThrownBy(() -> householdInvitationService.cancelInvitation(householdId, invitationId, token))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Cannot cancel processed invitation");
        }
    }

    @Nested
    @DisplayName("Get Invitations Tests")
    class GetInvitationsTests {

        @Test
        @DisplayName("Should get household invitations successfully")
        void shouldGetHouseholdInvitationsSuccessfully() {
            // Given
            when(userService.getCurrentUser(token)).thenReturn(currentUser);
            when(householdRepository.findById(householdId)).thenReturn(Optional.of(testHousehold));
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, currentUserId))
                    .thenReturn(Optional.of(ownerMember));
            when(householdInvitationRepository.findByHouseholdId(householdId))
                    .thenReturn(List.of(testInvitation));

            // When
            List<HouseholdInvitationResponse> responses = householdInvitationService.getHouseholdInvitations(householdId, token);

            // Then
            assertThat(responses).hasSize(1);
            assertThat(responses.get(0).getInvitedUserEmail()).isEqualTo("invited@example.com");
        }

        @Test
        @DisplayName("Should get household invitations by status successfully")
        void shouldGetHouseholdInvitationsByStatusSuccessfully() {
            // Given
            when(userService.getCurrentUser(token)).thenReturn(currentUser);
            when(householdRepository.findById(householdId)).thenReturn(Optional.of(testHousehold));
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, currentUserId))
                    .thenReturn(Optional.of(ownerMember));
            when(householdInvitationRepository.findByHouseholdIdAndStatus(householdId, InvitationStatus.PENDING))
                    .thenReturn(List.of(testInvitation));

            // When
            List<HouseholdInvitationResponse> responses = householdInvitationService.getHouseholdInvitationsByStatus(householdId, InvitationStatus.PENDING, token);

            // Then
            assertThat(responses).hasSize(1);
            assertThat(responses.get(0).getStatus()).isEqualTo(InvitationStatus.PENDING);
        }

        @Test
        @DisplayName("Should get my invitations successfully")
        void shouldGetMyInvitationsSuccessfully() {
            // Given
            when(userService.getCurrentUser(token)).thenReturn(invitedUser);
            when(householdInvitationRepository.findByInvitedUserIdAndStatus(invitedUserId, InvitationStatus.PENDING))
                    .thenReturn(List.of(testInvitation));

            // When
            List<HouseholdInvitationResponse> responses = householdInvitationService.getMyInvitations(token);

            // Then
            assertThat(responses).hasSize(1);
            assertThat(responses.get(0).getInvitedUserEmail()).isEqualTo("invited@example.com");
        }

        @Test
        @DisplayName("Should get my invitations by status successfully")
        void shouldGetMyInvitationsByStatusSuccessfully() {
            // Given
            when(userService.getCurrentUser(token)).thenReturn(invitedUser);
            when(householdInvitationRepository.findByInvitedUserIdAndStatus(invitedUserId, InvitationStatus.PENDING))
                    .thenReturn(List.of(testInvitation));

            // When
            List<HouseholdInvitationResponse> responses = householdInvitationService.getMyInvitationsByStatus(InvitationStatus.PENDING, token);

            // Then
            assertThat(responses).hasSize(1);
            assertThat(responses.get(0).getStatus()).isEqualTo(InvitationStatus.PENDING);
        }

        @Test
        @DisplayName("Should get invitation by ID successfully")
        void shouldGetInvitationByIdSuccessfully() {
            // Given
            when(userService.getCurrentUser(token)).thenReturn(currentUser);
            when(householdRepository.findById(householdId)).thenReturn(Optional.of(testHousehold));
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, currentUserId))
                    .thenReturn(Optional.of(ownerMember));
            when(householdInvitationRepository.findById(invitationId)).thenReturn(Optional.of(testInvitation));

            // When
            HouseholdInvitationResponse response = householdInvitationService.getInvitationById(householdId, invitationId, token);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getInvitedUserEmail()).isEqualTo("invited@example.com");
        }
    }

    @Nested
    @DisplayName("Mark Expired Invitations Tests")
    class MarkExpiredInvitationsTests {

        @Test
        @DisplayName("Should mark expired invitations successfully")
        void shouldMarkExpiredInvitationsSuccessfully() {
            // Given
            LocalDateTime pastDate = LocalDateTime.now().minusDays(1);
            HouseholdInvitation expiredInvitation = new HouseholdInvitation();
            expiredInvitation.setId(UUID.randomUUID());
            expiredInvitation.setStatus(InvitationStatus.PENDING);
            expiredInvitation.setExpiresAt(pastDate);

            when(householdInvitationRepository.findByStatusAndExpiresAtBefore(eq(InvitationStatus.PENDING), any(LocalDateTime.class)))
                    .thenReturn(List.of(expiredInvitation));
            when(householdInvitationRepository.save(any(HouseholdInvitation.class))).thenReturn(expiredInvitation);

            // When
            householdInvitationService.markExpiredInvitations();

            // Then
            verify(householdInvitationRepository).save(any(HouseholdInvitation.class));
        }

        @Test
        @DisplayName("Should handle no expired invitations")
        void shouldHandleNoExpiredInvitations() {
            // Given
            when(householdInvitationRepository.findByStatusAndExpiresAtBefore(eq(InvitationStatus.PENDING), any(LocalDateTime.class)))
                    .thenReturn(Collections.emptyList());

            // When
            householdInvitationService.markExpiredInvitations();

            // Then
            verify(householdInvitationRepository, never()).save(any(HouseholdInvitation.class));
        }
    }

    @Nested
    @DisplayName("Send Invitation Reminder Tests")
    class SendInvitationReminderTests {

        @Test
        @DisplayName("Should send invitation reminder successfully")
        void shouldSendInvitationReminderSuccessfully() {
            // Given
            when(householdInvitationRepository.findById(invitationId)).thenReturn(Optional.of(testInvitation));

            // When
            householdInvitationService.sendInvitationReminder(invitationId);

            // Then - No exception should be thrown
            verify(householdInvitationRepository).findById(invitationId);
        }

        @Test
        @DisplayName("Should throw NotFoundException when invitation doesn't exist")
        void shouldThrowNotFoundExceptionWhenInvitationDoesntExist() {
            // Given
            when(householdInvitationRepository.findById(invitationId)).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> householdInvitationService.sendInvitationReminder(invitationId))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("Invitation not found");
        }

        @Test
        @DisplayName("Should throw IllegalStateException when invitation is not pending")
        void shouldThrowIllegalStateExceptionWhenInvitationIsNotPending() {
            // Given
            testInvitation.setStatus(InvitationStatus.ACCEPTED);
            when(householdInvitationRepository.findById(invitationId)).thenReturn(Optional.of(testInvitation));

            // When & Then
            assertThatThrownBy(() -> householdInvitationService.sendInvitationReminder(invitationId))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Can only send reminders for pending invitations");
        }

        @Test
        @DisplayName("Should throw IllegalStateException when invitation is expired")
        void shouldThrowIllegalStateExceptionWhenInvitationIsExpired() {
            // Given
            testInvitation.setExpiresAt(LocalDateTime.now().minusDays(1));
            when(householdInvitationRepository.findById(invitationId)).thenReturn(Optional.of(testInvitation));

            // When & Then
            assertThatThrownBy(() -> householdInvitationService.sendInvitationReminder(invitationId))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Cannot send reminder for expired invitation");
        }
    }
}