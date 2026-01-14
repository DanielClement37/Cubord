package org.cubord.cubordbackend.service;

import org.cubord.cubordbackend.domain.*;
import org.cubord.cubordbackend.dto.householdInvitation.HouseholdInvitationRequest;
import org.cubord.cubordbackend.dto.householdInvitation.HouseholdInvitationResponse;
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
 * Test suite for email-based invitation functionality.
 * Tests the new feature that allows inviting users by email even if they don't have an account yet.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("HouseholdInvitationService Email-Based Invitation Tests")
class HouseholdInvitationServiceEmailTest {

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
    private UUID householdId;
    private UUID invitationId;
    private User currentUser;
    private Household testHousehold;
    private final String nonExistentEmail = "newuser@example.com";

    @BeforeEach
    void setUp() {
        currentUserId = UUID.randomUUID();
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

        testHousehold = Household.builder()
                .id(householdId)
                .name("Test Household")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    @Nested
    @DisplayName("sendInvitation - Email-only invitations")
    class SendEmailOnlyInvitationTests {

        @Test
        @DisplayName("should create email-only invitation when user doesn't exist")
        void shouldCreateEmailOnlyInvitationWhenUserDoesntExist() {
            // Given
            HouseholdInvitationRequest request = HouseholdInvitationRequest.builder()
                    .invitedUserEmail(nonExistentEmail)
                    .proposedRole(HouseholdRole.MEMBER)
                    .build();

            when(securityService.getCurrentUserId()).thenReturn(currentUserId);
            when(securityService.getCurrentUser()).thenReturn(currentUser);
            when(householdRepository.findById(householdId)).thenReturn(Optional.of(testHousehold));
            when(userRepository.findByEmail(nonExistentEmail.toLowerCase())).thenReturn(Optional.empty());
            when(householdInvitationRepository.existsByHouseholdIdAndInvitedEmailIgnoreCaseAndStatus(
                    eq(householdId), eq(nonExistentEmail.toLowerCase()), eq(InvitationStatus.PENDING)))
                    .thenReturn(false);
            when(householdInvitationRepository.save(any(HouseholdInvitation.class)))
                    .thenAnswer(inv -> {
                        HouseholdInvitation saved = inv.getArgument(0);
                        saved.setId(invitationId);
                        return saved;
                    });

            // When
            HouseholdInvitationResponse response = householdInvitationService.sendInvitation(householdId, request);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getInvitedUserEmail()).isEqualTo(nonExistentEmail.toLowerCase());
            assertThat(response.getInvitedUserId()).isNull();
            assertThat(response.getInvitedUserName()).isNull();
            assertThat(response.getProposedRole()).isEqualTo(HouseholdRole.MEMBER);
            assertThat(response.getStatus()).isEqualTo(InvitationStatus.PENDING);

            verify(householdInvitationRepository).save(argThat(invitation ->
                    invitation.getInvitedUser() == null &&
                    invitation.getInvitedEmail().equals(nonExistentEmail.toLowerCase()) &&
                    invitation.isEmailOnlyInvitation()
            ));
        }

        @Test
        @DisplayName("should throw ConflictException when email already has pending invitation")
        void shouldThrowConflictExceptionWhenEmailAlreadyHasPendingInvitation() {
            // Given
            HouseholdInvitationRequest request = HouseholdInvitationRequest.builder()
                    .invitedUserEmail(nonExistentEmail)
                    .proposedRole(HouseholdRole.MEMBER)
                    .build();

            when(securityService.getCurrentUserId()).thenReturn(currentUserId);
            when(securityService.getCurrentUser()).thenReturn(currentUser);
            when(householdRepository.findById(householdId)).thenReturn(Optional.of(testHousehold));
            when(userRepository.findByEmail(nonExistentEmail.toLowerCase())).thenReturn(Optional.empty());
            when(householdInvitationRepository.existsByHouseholdIdAndInvitedEmailIgnoreCaseAndStatus(
                    eq(householdId), eq(nonExistentEmail.toLowerCase()), eq(InvitationStatus.PENDING)))
                    .thenReturn(true);

            // When/Then
            assertThatThrownBy(() -> householdInvitationService.sendInvitation(householdId, request))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("already has a pending invitation");

            verify(householdInvitationRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw BusinessRuleViolationException when inviting own email")
        void shouldThrowBusinessRuleViolationExceptionWhenInvitingOwnEmail() {
            // Given
            HouseholdInvitationRequest request = HouseholdInvitationRequest.builder()
                    .invitedUserEmail(currentUser.getEmail())
                    .proposedRole(HouseholdRole.MEMBER)
                    .build();

            when(securityService.getCurrentUserId()).thenReturn(currentUserId);
            when(householdRepository.findById(householdId)).thenReturn(Optional.of(testHousehold));
            when(userRepository.findByEmail(currentUser.getEmail().toLowerCase())).thenReturn(Optional.of(currentUser));

            // When/Then
            assertThatThrownBy(() -> householdInvitationService.sendInvitation(householdId, request))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("Cannot invite yourself");

            verify(householdInvitationRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("getMyInvitations - Linking email invitations")
    class GetMyInvitationsWithLinkingTests {

        @Test
        @DisplayName("should link email-based invitations when fetching my invitations")
        void shouldLinkEmailBasedInvitationsWhenFetchingMyInvitations() {
            // Given
            User newUser = User.builder()
                    .id(UUID.randomUUID())
                    .username("newuser")
                    .email(nonExistentEmail)
                    .displayName("New User")
                    .build();

            HouseholdInvitation linkedInvitation = HouseholdInvitation.builder()
                    .id(invitationId)
                    .household(testHousehold)
                    .invitedUser(newUser)
                    .invitedEmail(null)
                    .invitedBy(currentUser)
                    .proposedRole(HouseholdRole.MEMBER)
                    .status(InvitationStatus.PENDING)
                    .expiresAt(LocalDateTime.now().plusDays(7))
                    .build();

            when(securityService.getCurrentUser()).thenReturn(newUser);
            when(householdInvitationRepository.linkEmailInvitationsToUser(
                    eq(newUser), eq(nonExistentEmail), eq(InvitationStatus.PENDING), any(LocalDateTime.class)))
                    .thenReturn(1);
            when(householdInvitationRepository.findByInvitedUserIdAndStatus(
                    eq(newUser.getId()), eq(InvitationStatus.PENDING)))
                    .thenReturn(List.of(linkedInvitation));

            // When
            List<HouseholdInvitationResponse> responses = householdInvitationService.getMyInvitations();

            // Then
            assertThat(responses).hasSize(1);
            assertThat(responses.getFirst().getInvitedUserId()).isEqualTo(newUser.getId());

            verify(householdInvitationRepository).linkEmailInvitationsToUser(
                    eq(newUser), eq(nonExistentEmail), eq(InvitationStatus.PENDING), any(LocalDateTime.class));
        }
    }

    @Nested
    @DisplayName("acceptInvitation - Email-only invitations")
    class AcceptEmailOnlyInvitationTests {

        @Test
        @DisplayName("should link and accept email-only invitation")
        void shouldLinkAndAcceptEmailOnlyInvitation() {
            // Given
            User newUser = User.builder()
                    .id(UUID.randomUUID())
                    .username("newuser")
                    .email(nonExistentEmail)
                    .displayName("New User")
                    .build();

            HouseholdInvitation emailOnlyInvitation = HouseholdInvitation.builder()
                    .id(invitationId)
                    .household(testHousehold)
                    .invitedUser(null)
                    .invitedEmail(nonExistentEmail)
                    .invitedBy(currentUser)
                    .proposedRole(HouseholdRole.MEMBER)
                    .status(InvitationStatus.PENDING)
                    .expiresAt(LocalDateTime.now().plusDays(7))
                    .build();

            when(securityService.getCurrentUser()).thenReturn(newUser);
            when(householdInvitationRepository.findById(invitationId))
                    .thenReturn(Optional.of(emailOnlyInvitation));
            when(householdInvitationRepository.save(any(HouseholdInvitation.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(householdMemberRepository.existsByHouseholdIdAndUserId(householdId, newUser.getId()))
                    .thenReturn(false);
            when(householdMemberRepository.save(any(HouseholdMember.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            // When
            HouseholdInvitationResponse response = householdInvitationService.acceptInvitation(invitationId);

            // Then
            assertThat(response).isNotNull();

            // Verify invitation was linked to user
            verify(householdInvitationRepository, times(2)).save(argThat(invitation ->
                    invitation.getInvitedUser() != null &&
                    invitation.getInvitedUser().getId().equals(newUser.getId()) &&
                    invitation.getInvitedEmail() == null
            ));

            // Verify member was created
            verify(householdMemberRepository).save(argThat(member ->
                    member.getUser().getId().equals(newUser.getId()) &&
                    member.getHousehold().getId().equals(householdId) &&
                    member.getRole() == HouseholdRole.MEMBER
            ));
        }

        @Test
        @DisplayName("should reject when email doesn't match")
        void shouldRejectWhenEmailDoesntMatch() {
            // Given
            User wrongUser = User.builder()
                    .id(UUID.randomUUID())
                    .username("wronguser")
                    .email("wrong@example.com")
                    .displayName("Wrong User")
                    .build();

            HouseholdInvitation emailOnlyInvitation = HouseholdInvitation.builder()
                    .id(invitationId)
                    .household(testHousehold)
                    .invitedUser(null)
                    .invitedEmail(nonExistentEmail)
                    .invitedBy(currentUser)
                    .proposedRole(HouseholdRole.MEMBER)
                    .status(InvitationStatus.PENDING)
                    .expiresAt(LocalDateTime.now().plusDays(7))
                    .build();

            when(securityService.getCurrentUser()).thenReturn(wrongUser);
            when(householdInvitationRepository.findById(invitationId))
                    .thenReturn(Optional.of(emailOnlyInvitation));

            // When/Then
            assertThatThrownBy(() -> householdInvitationService.acceptInvitation(invitationId))
                    .isInstanceOf(InsufficientPermissionException.class)
                    .hasMessageContaining("not the invited user");

            verify(householdMemberRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("linkEmailInvitationsToUser")
    class LinkEmailInvitationsToUserTests {

        @Test
        @DisplayName("should link invitations and return count")
        void shouldLinkInvitationsAndReturnCount() {
            // Given
            User newUser = User.builder()
                    .id(UUID.randomUUID())
                    .username("newuser")
                    .email(nonExistentEmail)
                    .build();

            when(householdInvitationRepository.linkEmailInvitationsToUser(
                    eq(newUser), eq(nonExistentEmail), eq(InvitationStatus.PENDING), any(LocalDateTime.class)))
                    .thenReturn(3);

            // When
            int linkedCount = householdInvitationService.linkEmailInvitationsToUser(newUser);

            // Then
            assertThat(linkedCount).isEqualTo(3);

            verify(householdInvitationRepository).linkEmailInvitationsToUser(
                    eq(newUser), eq(nonExistentEmail), eq(InvitationStatus.PENDING), any(LocalDateTime.class));
        }

        @Test
        @DisplayName("should return 0 when user is null")
        void shouldReturnZeroWhenUserIsNull() {
            // When
            int linkedCount = householdInvitationService.linkEmailInvitationsToUser(null);

            // Then
            assertThat(linkedCount).isZero();
            verify(householdInvitationRepository, never()).linkEmailInvitationsToUser(any(), any(), any(), any());
        }

        @Test
        @DisplayName("should return 0 when user email is null")
        void shouldReturnZeroWhenUserEmailIsNull() {
            // Given
            User userWithoutEmail = User.builder()
                    .id(UUID.randomUUID())
                    .username("nomail")
                    .email(null)
                    .build();

            // When
            int linkedCount = householdInvitationService.linkEmailInvitationsToUser(userWithoutEmail);

            // Then
            assertThat(linkedCount).isZero();
            verify(householdInvitationRepository, never()).linkEmailInvitationsToUser(any(), any(), any(), any());
        }
    }

    @Nested
    @DisplayName("HouseholdInvitation entity helper methods")
    class EntityHelperMethodTests {

        @Test
        @DisplayName("getEffectiveEmail should return user email when user exists")
        void getEffectiveEmailShouldReturnUserEmailWhenUserExists() {
            // Given
            User user = User.builder().email("user@example.com").build();
            HouseholdInvitation invitation = HouseholdInvitation.builder()
                    .invitedUser(user)
                    .invitedEmail(null)
                    .build();

            // When/Then
            assertThat(invitation.getEffectiveEmail()).isEqualTo("user@example.com");
        }

        @Test
        @DisplayName("getEffectiveEmail should return invitedEmail when user is null")
        void getEffectiveEmailShouldReturnInvitedEmailWhenUserIsNull() {
            // Given
            HouseholdInvitation invitation = HouseholdInvitation.builder()
                    .invitedUser(null)
                    .invitedEmail("email@example.com")
                    .build();

            // When/Then
            assertThat(invitation.getEffectiveEmail()).isEqualTo("email@example.com");
        }

        @Test
        @DisplayName("isEmailOnlyInvitation should return true when user is null and email is set")
        void isEmailOnlyInvitationShouldReturnTrueWhenUserIsNullAndEmailIsSet() {
            // Given
            HouseholdInvitation invitation = HouseholdInvitation.builder()
                    .invitedUser(null)
                    .invitedEmail("email@example.com")
                    .build();

            // When/Then
            assertThat(invitation.isEmailOnlyInvitation()).isTrue();
        }

        @Test
        @DisplayName("isEmailOnlyInvitation should return false when user exists")
        void isEmailOnlyInvitationShouldReturnFalseWhenUserExists() {
            // Given
            User user = User.builder().email("user@example.com").build();
            HouseholdInvitation invitation = HouseholdInvitation.builder()
                    .invitedUser(user)
                    .invitedEmail(null)
                    .build();

            // When/Then
            assertThat(invitation.isEmailOnlyInvitation()).isFalse();
        }
    }
}
