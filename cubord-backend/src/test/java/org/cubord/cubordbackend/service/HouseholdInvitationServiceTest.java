package org.cubord.cubordbackend.service;

import org.cubord.cubordbackend.domain.HouseholdRole;
import org.cubord.cubordbackend.domain.InvitationStatus;
import org.cubord.cubordbackend.dto.HouseholdInvitationRequest;
import org.cubord.cubordbackend.dto.HouseholdInvitationResponse;
import org.cubord.cubordbackend.domain.*;
import org.cubord.cubordbackend.exception.ConflictException;
import org.cubord.cubordbackend.exception.ForbiddenException;
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

        // Set up users
        currentUser = new User();
        currentUser.setId(currentUserId);
        currentUser.setEmail("current@example.com");
        currentUser.setUsername("currentuser");

        invitedUser = new User();
        invitedUser.setId(invitedUserId);
        invitedUser.setEmail("invited@example.com");
        invitedUser.setUsername("inviteduser");

        // Set up household
        testHousehold = new Household();
        testHousehold.setId(householdId);
        testHousehold.setName("Test Household");

        // Set up household members
        ownerMember = new HouseholdMember();
        ownerMember.setId(UUID.randomUUID());
        ownerMember.setUser(currentUser);
        ownerMember.setHousehold(testHousehold);
        ownerMember.setRole(HouseholdRole.OWNER);

        adminMember = new HouseholdMember();
        adminMember.setId(UUID.randomUUID());
        adminMember.setUser(currentUser);
        adminMember.setHousehold(testHousehold);
        adminMember.setRole(HouseholdRole.ADMIN);

        regularMember = new HouseholdMember();
        regularMember.setId(UUID.randomUUID());
        regularMember.setUser(currentUser);
        regularMember.setHousehold(testHousehold);
        regularMember.setRole(HouseholdRole.MEMBER);

        // Set up invitation
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

    @Nested
    @DisplayName("Send Invitation Tests")
    class SendInvitationTests {

        @Test
        @DisplayName("Should send invitation successfully when user is owner")
        void shouldSendInvitationSuccessfullyWhenUserIsOwner() {
            // Given
            HouseholdInvitationRequest request = HouseholdInvitationRequest.builder()
                    .invitedUserEmail("invited@example.com")
                    .proposedRole(HouseholdRole.MEMBER)
                    .build();

            when(userService.getCurrentUser(token)).thenReturn(currentUser);
            when(householdRepository.findById(householdId)).thenReturn(Optional.of(testHousehold));
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, currentUserId))
                    .thenReturn(Optional.of(ownerMember));
            when(userRepository.findByEmail("invited@example.com")).thenReturn(Optional.of(invitedUser));
            when(householdMemberRepository.existsByHouseholdIdAndUserId(householdId, invitedUserId))
                    .thenReturn(false);
            when(householdInvitationRepository.existsByHouseholdIdAndInvitedUserIdAndStatus(
                    householdId, invitedUserId, InvitationStatus.PENDING)).thenReturn(false);
            when(householdInvitationRepository.save(any(HouseholdInvitation.class))).thenReturn(testInvitation);

            // When
            HouseholdInvitationResponse response = householdInvitationService.sendInvitation(householdId, request, token);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getInvitedUserEmail()).isEqualTo("invited@example.com");
            assertThat(response.getProposedRole()).isEqualTo(HouseholdRole.MEMBER);
            assertThat(response.getStatus()).isEqualTo(InvitationStatus.PENDING);
            verify(householdInvitationRepository).save(any(HouseholdInvitation.class));
        }

        @Test
        @DisplayName("Should send invitation successfully when user is admin")
        void shouldSendInvitationSuccessfullyWhenUserIsAdmin() {
            // Given
            HouseholdInvitationRequest request = HouseholdInvitationRequest.builder()
                    .invitedUserEmail("invited@example.com")
                    .proposedRole(HouseholdRole.MEMBER)
                    .build();

            when(userService.getCurrentUser(token)).thenReturn(currentUser);
            when(householdRepository.findById(householdId)).thenReturn(Optional.of(testHousehold));
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, currentUserId))
                    .thenReturn(Optional.of(adminMember));
            when(userRepository.findByEmail("invited@example.com")).thenReturn(Optional.of(invitedUser));
            when(householdMemberRepository.existsByHouseholdIdAndUserId(householdId, invitedUserId))
                    .thenReturn(false);
            when(householdInvitationRepository.existsByHouseholdIdAndInvitedUserIdAndStatus(
                    householdId, invitedUserId, InvitationStatus.PENDING)).thenReturn(false);
            when(householdInvitationRepository.save(any(HouseholdInvitation.class))).thenReturn(testInvitation);

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
            HouseholdInvitationRequest request = HouseholdInvitationRequest.builder()
                    .invitedUserId(invitedUserId)
                    .proposedRole(HouseholdRole.MEMBER)
                    .build();

            when(userService.getCurrentUser(token)).thenReturn(currentUser);
            when(householdRepository.findById(householdId)).thenReturn(Optional.of(testHousehold));
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, currentUserId))
                    .thenReturn(Optional.of(ownerMember));
            when(userRepository.findById(invitedUserId)).thenReturn(Optional.of(invitedUser));
            when(householdMemberRepository.existsByHouseholdIdAndUserId(householdId, invitedUserId))
                    .thenReturn(false);
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
            HouseholdInvitationRequest request = HouseholdInvitationRequest.builder()
                    .invitedUserEmail("invited@example.com")
                    .proposedRole(HouseholdRole.MEMBER)
                    .build();

            when(userService.getCurrentUser(token)).thenReturn(currentUser);
            when(householdRepository.findById(householdId)).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> householdInvitationService.sendInvitation(householdId, request, token))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("Household not found");
        }

        @Test
        @DisplayName("Should throw ForbiddenException when user is not a member")
        void shouldThrowForbiddenExceptionWhenUserIsNotMember() {
            // Given
            HouseholdInvitationRequest request = HouseholdInvitationRequest.builder()
                    .invitedUserEmail("invited@example.com")
                    .proposedRole(HouseholdRole.MEMBER)
                    .build();

            when(userService.getCurrentUser(token)).thenReturn(currentUser);
            when(householdRepository.findById(householdId)).thenReturn(Optional.of(testHousehold));
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, currentUserId))
                    .thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> householdInvitationService.sendInvitation(householdId, request, token))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessage("You don't have access to this household");
        }

        @Test
        @DisplayName("Should throw ForbiddenException when user is regular member")
        void shouldThrowForbiddenExceptionWhenUserIsRegularMember() {
            // Given
            HouseholdInvitationRequest request = HouseholdInvitationRequest.builder()
                    .invitedUserEmail("invited@example.com")
                    .proposedRole(HouseholdRole.MEMBER)
                    .build();

            when(userService.getCurrentUser(token)).thenReturn(currentUser);
            when(householdRepository.findById(householdId)).thenReturn(Optional.of(testHousehold));
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, currentUserId))
                    .thenReturn(Optional.of(regularMember));

            // When & Then
            assertThatThrownBy(() -> householdInvitationService.sendInvitation(householdId, request, token))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessage("You don't have permission to send invitations");
        }

        @Test
        @DisplayName("Should throw NotFoundException when invited user doesn't exist")
        void shouldThrowNotFoundExceptionWhenInvitedUserDoesntExist() {
            // Given
            HouseholdInvitationRequest request = HouseholdInvitationRequest.builder()
                    .invitedUserEmail("nonexistent@example.com")
                    .proposedRole(HouseholdRole.MEMBER)
                    .build();

            when(userService.getCurrentUser(token)).thenReturn(currentUser);
            when(householdRepository.findById(householdId)).thenReturn(Optional.of(testHousehold));
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
            HouseholdInvitationRequest request = HouseholdInvitationRequest.builder()
                    .invitedUserEmail("invited@example.com")
                    .proposedRole(HouseholdRole.MEMBER)
                    .build();

            when(userService.getCurrentUser(token)).thenReturn(currentUser);
            when(householdRepository.findById(householdId)).thenReturn(Optional.of(testHousehold));
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, currentUserId))
                    .thenReturn(Optional.of(ownerMember));
            when(userRepository.findByEmail("invited@example.com")).thenReturn(Optional.of(invitedUser));
            when(householdMemberRepository.existsByHouseholdIdAndUserId(householdId, invitedUserId))
                    .thenReturn(false);
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
            HouseholdInvitationRequest request = HouseholdInvitationRequest.builder()
                    .invitedUserEmail("invited@example.com")
                    .proposedRole(HouseholdRole.MEMBER)
                    .build();

            when(userService.getCurrentUser(token)).thenReturn(currentUser);
            when(householdRepository.findById(householdId)).thenReturn(Optional.of(testHousehold));
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, currentUserId))
                    .thenReturn(Optional.of(ownerMember));
            when(userRepository.findByEmail("invited@example.com")).thenReturn(Optional.of(invitedUser));
            when(householdMemberRepository.existsByHouseholdIdAndUserId(householdId, invitedUserId))
                    .thenReturn(true);

            // When & Then
            assertThatThrownBy(() -> householdInvitationService.sendInvitation(householdId, request, token))
                    .isInstanceOf(ConflictException.class)
                    .hasMessage("User is already a member of this household");
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException when trying to invite as OWNER")
        void shouldThrowIllegalArgumentExceptionWhenTryingToInviteAsOwner() {
            // Given
            HouseholdInvitationRequest request = HouseholdInvitationRequest.builder()
                    .invitedUserEmail("invited@example.com")
                    .proposedRole(HouseholdRole.OWNER)
                    .build();

            when(userService.getCurrentUser(token)).thenReturn(currentUser);
            when(householdRepository.findById(householdId)).thenReturn(Optional.of(testHousehold));
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, currentUserId))
                    .thenReturn(Optional.of(ownerMember));

            // When & Then
            assertThatThrownBy(() -> householdInvitationService.sendInvitation(householdId, request, token))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Cannot invite user as OWNER");
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException when trying to invite self")
        void shouldThrowIllegalArgumentExceptionWhenTryingToInviteSelf() {
            // Given
            HouseholdInvitationRequest request = HouseholdInvitationRequest.builder()
                    .invitedUserEmail("current@example.com") // Same as current user
                    .proposedRole(HouseholdRole.MEMBER)
                    .build();

            when(userService.getCurrentUser(token)).thenReturn(currentUser);
            when(householdRepository.findById(householdId)).thenReturn(Optional.of(testHousehold));
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

            when(userService.getCurrentUser(token)).thenReturn(currentUser);
            when(householdRepository.findById(householdId)).thenReturn(Optional.of(testHousehold));
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, currentUserId))
                    .thenReturn(Optional.of(ownerMember));

            // When & Then
            assertThatThrownBy(() -> householdInvitationService.sendInvitation(householdId, request, token))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Expiry date cannot be in the past");
        }
    }

    @Nested
    @DisplayName("Get Household Invitations Tests")
    class GetHouseholdInvitationsTests {

        @Test
        @DisplayName("Should get all invitations when user is member")
        void shouldGetAllInvitationsWhenUserIsMember() {
            // Given
            List<HouseholdInvitation> invitations = List.of(testInvitation);
            
            when(userService.getCurrentUser(token)).thenReturn(currentUser);
            when(householdRepository.findById(householdId)).thenReturn(Optional.of(testHousehold));
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, currentUserId))
                    .thenReturn(Optional.of(ownerMember));
            when(householdInvitationRepository.findByHouseholdId(householdId)).thenReturn(invitations);

            // When
            List<HouseholdInvitationResponse> responses = householdInvitationService.getHouseholdInvitations(householdId, token);

            // Then
            assertThat(responses).hasSize(1);
            assertThat(responses.get(0).getId()).isEqualTo(invitationId);
        }

        @Test
        @DisplayName("Should return empty list when no invitations exist")
        void shouldReturnEmptyListWhenNoInvitationsExist() {
            // Given
            when(userService.getCurrentUser(token)).thenReturn(currentUser);
            when(householdRepository.findById(householdId)).thenReturn(Optional.of(testHousehold));
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, currentUserId))
                    .thenReturn(Optional.of(ownerMember));
            when(householdInvitationRepository.findByHouseholdId(householdId)).thenReturn(Collections.emptyList());

            // When
            List<HouseholdInvitationResponse> responses = householdInvitationService.getHouseholdInvitations(householdId, token);

            // Then
            assertThat(responses).isEmpty();
        }

        @Test
        @DisplayName("Should throw NotFoundException when household doesn't exist")
        void shouldThrowNotFoundExceptionWhenHouseholdDoesntExist() {
            // Given
            when(userService.getCurrentUser(token)).thenReturn(currentUser);
            when(householdRepository.findById(householdId)).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> householdInvitationService.getHouseholdInvitations(householdId, token))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("Household not found");
        }

        @Test
        @DisplayName("Should throw ForbiddenException when user is not a member")
        void shouldThrowForbiddenExceptionWhenUserIsNotMember() {
            // Given
            when(userService.getCurrentUser(token)).thenReturn(currentUser);
            when(householdRepository.findById(householdId)).thenReturn(Optional.of(testHousehold));
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, currentUserId))
                    .thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> householdInvitationService.getHouseholdInvitations(householdId, token))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessage("You don't have access to this household");
        }
    }

    @Nested
    @DisplayName("Get Household Invitations By Status Tests")
    class GetHouseholdInvitationsByStatusTests {

        @Test
        @DisplayName("Should get invitations filtered by status")
        void shouldGetInvitationsFilteredByStatus() {
            // Given
            List<HouseholdInvitation> invitations = List.of(testInvitation);
            
            when(userService.getCurrentUser(token)).thenReturn(currentUser);
            when(householdRepository.findById(householdId)).thenReturn(Optional.of(testHousehold));
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, currentUserId))
                    .thenReturn(Optional.of(ownerMember));
            when(householdInvitationRepository.findByHouseholdIdAndStatus(householdId, InvitationStatus.PENDING))
                    .thenReturn(invitations);

            // When
            List<HouseholdInvitationResponse> responses = householdInvitationService
                    .getHouseholdInvitationsByStatus(householdId, InvitationStatus.PENDING, token);

            // Then
            assertThat(responses).hasSize(1);
            assertThat(responses.get(0).getStatus()).isEqualTo(InvitationStatus.PENDING);
        }
    }

    @Nested
    @DisplayName("Get My Invitations Tests")
    class GetMyInvitationsTests {

        @Test
        @DisplayName("Should get current user's invitations")
        void shouldGetCurrentUsersInvitations() {
            // Given
            List<HouseholdInvitation> invitations = List.of(testInvitation);
            
            when(userService.getCurrentUser(token)).thenReturn(invitedUser);
            when(householdInvitationRepository.findByInvitedUserIdAndStatus(invitedUserId, InvitationStatus.PENDING))
                    .thenReturn(invitations);

            // When
            List<HouseholdInvitationResponse> responses = householdInvitationService.getMyInvitations(token);

            // Then
            assertThat(responses).hasSize(1);
            assertThat(responses.get(0).getInvitedUserId()).isEqualTo(invitedUserId);
        }

        @Test
        @DisplayName("Should return empty list when user has no invitations")
        void shouldReturnEmptyListWhenUserHasNoInvitations() {
            // Given
            when(userService.getCurrentUser(token)).thenReturn(invitedUser);
            when(householdInvitationRepository.findByInvitedUserIdAndStatus(invitedUserId, InvitationStatus.PENDING))
                    .thenReturn(Collections.emptyList());

            // When
            List<HouseholdInvitationResponse> responses = householdInvitationService.getMyInvitations(token);

            // Then
            assertThat(responses).isEmpty();
        }
    }

    @Nested
    @DisplayName("Accept Invitation Tests")
    class AcceptInvitationTests {

        @Test
        @DisplayName("Should accept invitation successfully")
        void shouldAcceptInvitationSuccessfully() {
            // Given
            HouseholdInvitation acceptedInvitation = new HouseholdInvitation();
            acceptedInvitation.setId(invitationId);
            acceptedInvitation.setStatus(InvitationStatus.ACCEPTED);
            acceptedInvitation.setInvitedUser(invitedUser);
            acceptedInvitation.setHousehold(testHousehold);
            acceptedInvitation.setProposedRole(HouseholdRole.MEMBER);

            when(userService.getCurrentUser(token)).thenReturn(invitedUser);
            when(householdInvitationRepository.findById(invitationId)).thenReturn(Optional.of(testInvitation));
            when(householdMemberRepository.existsByHouseholdIdAndUserId(householdId, invitedUserId)).thenReturn(false);
            when(householdInvitationRepository.save(any(HouseholdInvitation.class))).thenReturn(acceptedInvitation);
            when(householdMemberRepository.save(any(HouseholdMember.class))).thenReturn(new HouseholdMember());

            // When
            HouseholdInvitationResponse response = householdInvitationService.acceptInvitation(invitationId, token);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getStatus()).isEqualTo(InvitationStatus.ACCEPTED);
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
        @DisplayName("Should throw ForbiddenException when not invited user")
        void shouldThrowForbiddenExceptionWhenNotInvitedUser() {
            // Given
            when(userService.getCurrentUser(token)).thenReturn(currentUser);
            when(householdInvitationRepository.findById(invitationId)).thenReturn(Optional.of(testInvitation));

            // When & Then
            assertThatThrownBy(() -> householdInvitationService.acceptInvitation(invitationId, token))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessage("You are not the invited user");
        }

        @Test
        @DisplayName("Should throw IllegalStateException when invitation already processed")
        void shouldThrowIllegalStateExceptionWhenInvitationAlreadyProcessed() {
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
        @DisplayName("Should throw IllegalStateException when invitation expired")
        void shouldThrowIllegalStateExceptionWhenInvitationExpired() {
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
        @DisplayName("Should throw ConflictException when user already member")
        void shouldThrowConflictExceptionWhenUserAlreadyMember() {
            // Given
            when(userService.getCurrentUser(token)).thenReturn(invitedUser);
            when(householdInvitationRepository.findById(invitationId)).thenReturn(Optional.of(testInvitation));
            when(householdMemberRepository.existsByHouseholdIdAndUserId(householdId, invitedUserId)).thenReturn(true);

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
            HouseholdInvitation declinedInvitation = new HouseholdInvitation();
            declinedInvitation.setId(invitationId);
            declinedInvitation.setStatus(InvitationStatus.DECLINED);

            when(userService.getCurrentUser(token)).thenReturn(invitedUser);
            when(householdInvitationRepository.findById(invitationId)).thenReturn(Optional.of(testInvitation));
            when(householdInvitationRepository.save(any(HouseholdInvitation.class))).thenReturn(declinedInvitation);

            // When
            HouseholdInvitationResponse response = householdInvitationService.declineInvitation(invitationId, token);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getStatus()).isEqualTo(InvitationStatus.DECLINED);
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
        @DisplayName("Should throw ForbiddenException when not invited user")
        void shouldThrowForbiddenExceptionWhenNotInvitedUser() {
            // Given
            when(userService.getCurrentUser(token)).thenReturn(currentUser);
            when(householdInvitationRepository.findById(invitationId)).thenReturn(Optional.of(testInvitation));

            // When & Then
            assertThatThrownBy(() -> householdInvitationService.declineInvitation(invitationId, token))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessage("You are not the invited user");
        }

        @Test
        @DisplayName("Should throw IllegalStateException when invitation already processed")
        void shouldThrowIllegalStateExceptionWhenInvitationAlreadyProcessed() {
            // Given
            testInvitation.setStatus(InvitationStatus.DECLINED);
            
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
        @DisplayName("Should cancel invitation when user is owner")
        void shouldCancelInvitationWhenUserIsOwner() {
            // Given
            when(userService.getCurrentUser(token)).thenReturn(currentUser);
            when(householdRepository.findById(householdId)).thenReturn(Optional.of(testHousehold));
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, currentUserId))
                    .thenReturn(Optional.of(ownerMember));
            when(householdInvitationRepository.findById(invitationId)).thenReturn(Optional.of(testInvitation));

            // When
            householdInvitationService.cancelInvitation(householdId, invitationId, token);

            // Then
            verify(householdInvitationRepository).delete(testInvitation);
        }

        @Test
        @DisplayName("Should cancel invitation when user is admin")
        void shouldCancelInvitationWhenUserIsAdmin() {
            // Given
            when(userService.getCurrentUser(token)).thenReturn(currentUser);
            when(householdRepository.findById(householdId)).thenReturn(Optional.of(testHousehold));
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, currentUserId))
                    .thenReturn(Optional.of(adminMember));
            when(householdInvitationRepository.findById(invitationId)).thenReturn(Optional.of(testInvitation));

            // When
            householdInvitationService.cancelInvitation(householdId, invitationId, token);

            // Then
            verify(householdInvitationRepository).delete(testInvitation);
        }

        @Test
        @DisplayName("Should cancel invitation when user is the inviter")
        void shouldCancelInvitationWhenUserIsInviter() {
            // Given
            when(userService.getCurrentUser(token)).thenReturn(currentUser);
            when(householdRepository.findById(householdId)).thenReturn(Optional.of(testHousehold));
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, currentUserId))
                    .thenReturn(Optional.of(regularMember));
            when(householdInvitationRepository.findById(invitationId)).thenReturn(Optional.of(testInvitation));

            // When
            householdInvitationService.cancelInvitation(householdId, invitationId, token);

            // Then
            verify(householdInvitationRepository).delete(testInvitation);
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
        @DisplayName("Should throw ForbiddenException when user lacks permission")
        void shouldThrowForbiddenExceptionWhenUserLacksPermission() {
            // Given
            User anotherUser = new User();
            anotherUser.setId(UUID.randomUUID());
            testInvitation.setInvitedBy(anotherUser);

            when(userService.getCurrentUser(token)).thenReturn(currentUser);
            when(householdRepository.findById(householdId)).thenReturn(Optional.of(testHousehold));
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, currentUserId))
                    .thenReturn(Optional.of(regularMember));
            when(householdInvitationRepository.findById(invitationId)).thenReturn(Optional.of(testInvitation));

            // When & Then
            assertThatThrownBy(() -> householdInvitationService.cancelInvitation(householdId, invitationId, token))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessage("You don't have permission to cancel this invitation");
        }

        @Test
        @DisplayName("Should throw IllegalStateException when invitation already processed")
        void shouldThrowIllegalStateExceptionWhenInvitationAlreadyProcessed() {
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
}
