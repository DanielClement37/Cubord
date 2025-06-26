package org.cubord.cubordbackend.service;

import org.cubord.cubordbackend.domain.Household;
import org.cubord.cubordbackend.domain.HouseholdMember;
import org.cubord.cubordbackend.domain.HouseholdRole;
import org.cubord.cubordbackend.domain.User;
import org.cubord.cubordbackend.dto.HouseholdMemberRequest;
import org.cubord.cubordbackend.dto.HouseholdMemberResponse;
import org.cubord.cubordbackend.exception.NotFoundException;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class HouseholdMemberServiceTest {

    @Mock
    private HouseholdRepository householdRepository;

    @Mock
    private HouseholdMemberRepository householdMemberRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserService userService;

    @Mock
    private JwtAuthenticationToken token;

    @InjectMocks
    private HouseholdMemberService householdMemberService;

    private User testUser;
    private User testUser2;
    private User testUser3;
    private Household testHousehold;
    private HouseholdMember ownerMember;
    private HouseholdMember adminMember;
    private HouseholdMember regularMember;
    private UUID userId;
    private UUID userId2;
    private UUID userId3;
    private UUID householdId;
    private UUID memberId;
    private UUID memberId2;
    private UUID memberId3;

    @BeforeEach
    void setUp() {
        // Set up test data
        userId = UUID.randomUUID();
        userId2 = UUID.randomUUID();
        userId3 = UUID.randomUUID();
        householdId = UUID.randomUUID();
        memberId = UUID.randomUUID();
        memberId2 = UUID.randomUUID();
        memberId3 = UUID.randomUUID();

        testUser = new User();
        testUser.setId(userId);
        testUser.setUsername("owner");

        testUser2 = new User();
        testUser2.setId(userId2);
        testUser2.setUsername("admin");

        testUser3 = new User();
        testUser3.setId(userId3);
        testUser3.setUsername("member");

        testHousehold = new Household();
        testHousehold.setId(householdId);
        testHousehold.setName("Test Household");

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
    }

    @Nested
    @DisplayName("Add Member Tests")
    class AddMemberTests {

        @Test
        @DisplayName("Should add member successfully when user is owner")
        void shouldAddMemberSuccessfullyWhenUserIsOwner() {
            // Given
            HouseholdMemberRequest request = new HouseholdMemberRequest();
            request.setUserId(userId3);
            request.setRole(HouseholdRole.MEMBER);

            when(userService.getCurrentUser(token)).thenReturn(testUser);
            when(householdRepository.findById(householdId)).thenReturn(Optional.of(testHousehold));
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, userId))
                    .thenReturn(Optional.of(ownerMember));
            when(userRepository.findById(userId3)).thenReturn(Optional.of(testUser3));
            when(householdMemberRepository.existsByHouseholdIdAndUserId(householdId, userId3)).thenReturn(false);
            when(householdMemberRepository.save(any(HouseholdMember.class))).thenAnswer(invocation -> {
                return invocation.<HouseholdMember>getArgument(0);
            });

            // When
            HouseholdMemberResponse response = householdMemberService.addMemberToHousehold(householdId, request, token);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getUserId()).isEqualTo(userId3);
            assertThat(response.getHouseholdId()).isEqualTo(householdId);
            assertThat(response.getRole()).isEqualTo(HouseholdRole.MEMBER);
            verify(householdMemberRepository).save(any(HouseholdMember.class));
        }

        @Test
        @DisplayName("Should add member successfully when user is admin")
        void shouldAddMemberSuccessfullyWhenUserIsAdmin() {
            // Given
            HouseholdMemberRequest request = new HouseholdMemberRequest();
            request.setUserId(userId3);
            request.setRole(HouseholdRole.MEMBER);

            when(userService.getCurrentUser(token)).thenReturn(testUser2);
            when(householdRepository.findById(householdId)).thenReturn(Optional.of(testHousehold));
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, userId2))
                    .thenReturn(Optional.of(adminMember));
            when(userRepository.findById(userId3)).thenReturn(Optional.of(testUser3));
            when(householdMemberRepository.existsByHouseholdIdAndUserId(householdId, userId3)).thenReturn(false);
            when(householdMemberRepository.save(any(HouseholdMember.class))).thenAnswer(invocation -> {
                return invocation.<HouseholdMember>getArgument(0);
            });

            // When
            HouseholdMemberResponse response = householdMemberService.addMemberToHousehold(householdId, request, token);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getUserId()).isEqualTo(userId3);
            assertThat(response.getHouseholdId()).isEqualTo(householdId);
            assertThat(response.getRole()).isEqualTo(HouseholdRole.MEMBER);
            verify(householdMemberRepository).save(any(HouseholdMember.class));
        }

        @Test
        @DisplayName("Should throw NotFoundException when household doesn't exist")
        void shouldThrowNotFoundExceptionWhenHouseholdDoesntExist() {
            // Given
            HouseholdMemberRequest request = new HouseholdMemberRequest();
            request.setUserId(userId3);
            request.setRole(HouseholdRole.MEMBER);

            when(userService.getCurrentUser(token)).thenReturn(testUser);
            when(householdRepository.findById(householdId)).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> householdMemberService.addMemberToHousehold(householdId, request, token))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("Household not found");

            verify(householdMemberRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw AccessDeniedException when user is not a member")
        void shouldThrowAccessDeniedExceptionWhenUserIsNotMember() {
            // Given
            HouseholdMemberRequest request = new HouseholdMemberRequest();
            request.setUserId(userId3);
            request.setRole(HouseholdRole.MEMBER);

            when(userService.getCurrentUser(token)).thenReturn(testUser);
            when(householdRepository.findById(householdId)).thenReturn(Optional.of(testHousehold));
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, userId))
                    .thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> householdMemberService.addMemberToHousehold(householdId, request, token))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("You don't have access to this household");

            verify(householdMemberRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw AccessDeniedException when user is regular member")
        void shouldThrowAccessDeniedExceptionWhenUserIsRegularMember() {
            // Given
            HouseholdMemberRequest request = new HouseholdMemberRequest();
            request.setUserId(userId2);
            request.setRole(HouseholdRole.MEMBER);

            when(userService.getCurrentUser(token)).thenReturn(testUser3);
            when(householdRepository.findById(householdId)).thenReturn(Optional.of(testHousehold));
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, userId3))
                    .thenReturn(Optional.of(regularMember));

            // When/Then
            assertThatThrownBy(() -> householdMemberService.addMemberToHousehold(householdId, request, token))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("You don't have permission to add members");

            verify(householdMemberRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw NotFoundException when user to add doesn't exist")
        void shouldThrowNotFoundExceptionWhenUserToAddDoesntExist() {
            // Given
            HouseholdMemberRequest request = new HouseholdMemberRequest();
            request.setUserId(UUID.randomUUID());
            request.setRole(HouseholdRole.MEMBER);

            when(userService.getCurrentUser(token)).thenReturn(testUser);
            when(householdRepository.findById(householdId)).thenReturn(Optional.of(testHousehold));
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, userId))
                    .thenReturn(Optional.of(ownerMember));
            when(userRepository.findById(request.getUserId())).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> householdMemberService.addMemberToHousehold(householdId, request, token))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("User not found");

            verify(householdMemberRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw IllegalStateException when user is already a member")
        void shouldThrowIllegalStateExceptionWhenUserIsAlreadyMember() {
            // Given
            HouseholdMemberRequest request = new HouseholdMemberRequest();
            request.setUserId(userId3);
            request.setRole(HouseholdRole.MEMBER);

            when(userService.getCurrentUser(token)).thenReturn(testUser);
            when(householdRepository.findById(householdId)).thenReturn(Optional.of(testHousehold));
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, userId))
                    .thenReturn(Optional.of(ownerMember));
            when(userRepository.findById(userId3)).thenReturn(Optional.of(testUser3));
            when(householdMemberRepository.existsByHouseholdIdAndUserId(householdId, userId3)).thenReturn(true);

            // When/Then
            assertThatThrownBy(() -> householdMemberService.addMemberToHousehold(householdId, request, token))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("User is already a member");

            verify(householdMemberRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Get Household Members Tests")
    class GetHouseholdMembersTests {

        @Test
        @DisplayName("Should get all members when user is a member")
        void shouldGetAllMembersWhenUserIsMember() {
            // Given
            when(userService.getCurrentUser(token)).thenReturn(testUser);
            when(householdRepository.findById(householdId)).thenReturn(Optional.of(testHousehold));
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, userId))
                    .thenReturn(Optional.of(ownerMember));
            when(householdMemberRepository.findByHouseholdId(householdId))
                    .thenReturn(List.of(ownerMember, adminMember, regularMember));

            // When
            List<HouseholdMemberResponse> responses = householdMemberService.getHouseholdMembers(householdId, token);

            // Then
            assertThat(responses).hasSize(3);
            assertThat(responses.get(0).getId()).isEqualTo(memberId);
            assertThat(responses.get(1).getId()).isEqualTo(memberId2);
            assertThat(responses.get(2).getId()).isEqualTo(memberId3);
        }

        @Test
        @DisplayName("Should throw NotFoundException when household doesn't exist")
        void shouldThrowNotFoundExceptionWhenHouseholdDoesntExist() {
            // Given
            when(userService.getCurrentUser(token)).thenReturn(testUser);
            when(householdRepository.findById(householdId)).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> householdMemberService.getHouseholdMembers(householdId, token))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("Household not found");
        }

        @Test
        @DisplayName("Should throw AccessDeniedException when user is not a member")
        void shouldThrowAccessDeniedExceptionWhenUserIsNotMember() {
            // Given
            when(userService.getCurrentUser(token)).thenReturn(testUser);
            when(householdRepository.findById(householdId)).thenReturn(Optional.of(testHousehold));
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, userId))
                    .thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> householdMemberService.getHouseholdMembers(householdId, token))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("You don't have access to this household");
        }

        @Test
        @DisplayName("Should return empty list when household has no members")
        void shouldReturnEmptyListWhenHouseholdHasNoMembers() {
            // Given
            when(userService.getCurrentUser(token)).thenReturn(testUser);
            when(householdRepository.findById(householdId)).thenReturn(Optional.of(testHousehold));
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, userId))
                    .thenReturn(Optional.of(ownerMember));
            when(householdMemberRepository.findByHouseholdId(householdId)).thenReturn(Collections.emptyList());

            // When
            List<HouseholdMemberResponse> responses = householdMemberService.getHouseholdMembers(householdId, token);

            // Then
            assertThat(responses).isEmpty();
        }
    }

    @Nested
    @DisplayName("Get Member By ID Tests")
    class GetMemberByIdTests {

        @Test
        @DisplayName("Should get member details when user is a household member")
        void shouldGetMemberDetailsWhenUserIsHouseholdMember() {
            // Given
            when(userService.getCurrentUser(token)).thenReturn(testUser);
            when(householdRepository.findById(householdId)).thenReturn(Optional.of(testHousehold));
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, userId))
                    .thenReturn(Optional.of(ownerMember));
            when(householdMemberRepository.findById(memberId2)).thenReturn(Optional.of(adminMember));

            // When
            HouseholdMemberResponse response = householdMemberService.getMemberById(householdId, memberId2, token);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo(memberId2);
            assertThat(response.getUserId()).isEqualTo(userId2);
            assertThat(response.getRole()).isEqualTo(HouseholdRole.ADMIN);
        }

        @Test
        @DisplayName("Should throw NotFoundException when household doesn't exist")
        void shouldThrowNotFoundExceptionWhenHouseholdDoesntExist() {
            // Given
            when(userService.getCurrentUser(token)).thenReturn(testUser);
            when(householdRepository.findById(householdId)).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> householdMemberService.getMemberById(householdId, memberId2, token))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("Household not found");
        }

        @Test
        @DisplayName("Should throw AccessDeniedException when user is not a household member")
        void shouldThrowAccessDeniedExceptionWhenUserIsNotHouseholdMember() {
            // Given
            when(userService.getCurrentUser(token)).thenReturn(testUser);
            when(householdRepository.findById(householdId)).thenReturn(Optional.of(testHousehold));
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, userId))
                    .thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> householdMemberService.getMemberById(householdId, memberId2, token))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("You don't have access to this household");
        }

        @Test
        @DisplayName("Should throw NotFoundException when member doesn't exist")
        void shouldThrowNotFoundExceptionWhenMemberDoesntExist() {
            // Given
            when(userService.getCurrentUser(token)).thenReturn(testUser);
            when(householdRepository.findById(householdId)).thenReturn(Optional.of(testHousehold));
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, userId))
                    .thenReturn(Optional.of(ownerMember));
            when(householdMemberRepository.findById(memberId2)).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> householdMemberService.getMemberById(householdId, memberId2, token))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("Member not found");
        }

        @Test
        @DisplayName("Should throw AccessDeniedException when member is not from the specified household")
        void shouldThrowAccessDeniedExceptionWhenMemberIsNotFromSpecifiedHousehold() {
            // Given
            when(userService.getCurrentUser(token)).thenReturn(testUser);
            when(householdRepository.findById(householdId)).thenReturn(Optional.of(testHousehold));
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, userId))
                    .thenReturn(Optional.of(ownerMember));

            Household otherHousehold = new Household();
            otherHousehold.setId(UUID.randomUUID());
            otherHousehold.setName("Other Household");

            HouseholdMember otherMember = new HouseholdMember();
            otherMember.setId(memberId2);
            otherMember.setUser(testUser2);
            otherMember.setHousehold(otherHousehold);
            otherMember.setRole(HouseholdRole.MEMBER);

            when(householdMemberRepository.findById(memberId2)).thenReturn(Optional.of(otherMember));

            // When/Then
            assertThatThrownBy(() -> householdMemberService.getMemberById(householdId, memberId2, token))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("Member is not from the specified household");
        }
    }

    @Nested
    @DisplayName("Remove Member Tests")
    class RemoveMemberTests {

        @Test
        @DisplayName("Should remove member when user is owner")
        void shouldRemoveMemberWhenUserIsOwner() {
            // Given
            when(userService.getCurrentUser(token)).thenReturn(testUser);
            when(householdRepository.findById(householdId)).thenReturn(Optional.of(testHousehold));
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, userId))
                    .thenReturn(Optional.of(ownerMember));
            when(householdMemberRepository.findById(memberId3)).thenReturn(Optional.of(regularMember));

            // When
            householdMemberService.removeMember(householdId, memberId3, token);

            // Then
            verify(householdMemberRepository).delete(regularMember);
        }

        @Test
        @DisplayName("Should remove member when user is admin")
        void shouldRemoveMemberWhenUserIsAdmin() {
            // Given
            when(userService.getCurrentUser(token)).thenReturn(testUser2);
            when(householdRepository.findById(householdId)).thenReturn(Optional.of(testHousehold));
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, userId2))
                    .thenReturn(Optional.of(adminMember));
            when(householdMemberRepository.findById(memberId3)).thenReturn(Optional.of(regularMember));

            // When
            householdMemberService.removeMember(householdId, memberId3, token);

            // Then
            verify(householdMemberRepository).delete(regularMember);
        }

        @Test
        @DisplayName("Should throw NotFoundException when household doesn't exist")
        void shouldThrowNotFoundExceptionWhenHouseholdDoesntExist() {
            // Given
            when(userService.getCurrentUser(token)).thenReturn(testUser);
            when(householdRepository.findById(householdId)).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> householdMemberService.removeMember(householdId, memberId3, token))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("Household not found");

            verify(householdMemberRepository, never()).delete(any());
        }

        @Test
        @DisplayName("Should throw AccessDeniedException when user is not a household member")
        void shouldThrowAccessDeniedExceptionWhenUserIsNotHouseholdMember() {
            // Given
            when(userService.getCurrentUser(token)).thenReturn(testUser);
            when(householdRepository.findById(householdId)).thenReturn(Optional.of(testHousehold));
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, userId))
                    .thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> householdMemberService.removeMember(householdId, memberId3, token))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("You don't have access to this household");

            verify(householdMemberRepository, never()).delete(any());
        }

        @Test
        @DisplayName("Should throw AccessDeniedException when user is regular member")
        void shouldThrowAccessDeniedExceptionWhenUserIsRegularMember() {
            // Given
            when(userService.getCurrentUser(token)).thenReturn(testUser3);
            when(householdRepository.findById(householdId)).thenReturn(Optional.of(testHousehold));
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, userId3))
                    .thenReturn(Optional.of(regularMember));

            // When/Then
            assertThatThrownBy(() -> householdMemberService.removeMember(householdId, memberId2, token))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("You don't have permission to remove members");

            verify(householdMemberRepository, never()).delete(any());
        }

        @Test
        @DisplayName("Should throw NotFoundException when member doesn't exist")
        void shouldThrowNotFoundExceptionWhenMemberDoesntExist() {
            // Given
            when(userService.getCurrentUser(token)).thenReturn(testUser);
            when(householdRepository.findById(householdId)).thenReturn(Optional.of(testHousehold));
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, userId))
                    .thenReturn(Optional.of(ownerMember));
            when(householdMemberRepository.findById(memberId3)).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> householdMemberService.removeMember(householdId, memberId3, token))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("Member not found");

            verify(householdMemberRepository, never()).delete(any());
        }

        @Test
        @DisplayName("Should throw AccessDeniedException when member is not from the specified household")
        void shouldThrowAccessDeniedExceptionWhenMemberIsNotFromSpecifiedHousehold() {
            // Given
            when(userService.getCurrentUser(token)).thenReturn(testUser);
            when(householdRepository.findById(householdId)).thenReturn(Optional.of(testHousehold));
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, userId))
                    .thenReturn(Optional.of(ownerMember));

            Household otherHousehold = new Household();
            otherHousehold.setId(UUID.randomUUID());
            otherHousehold.setName("Other Household");

            HouseholdMember otherMember = new HouseholdMember();
            otherMember.setId(memberId3);
            otherMember.setUser(testUser3);
            otherMember.setHousehold(otherHousehold);
            otherMember.setRole(HouseholdRole.MEMBER);

            when(householdMemberRepository.findById(memberId3)).thenReturn(Optional.of(otherMember));

            // When/Then
            assertThatThrownBy(() -> householdMemberService.removeMember(householdId, memberId3, token))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("Member is not from the specified household");

            verify(householdMemberRepository, never()).delete(any());
        }

        @Test
        @DisplayName("Should throw IllegalStateException when trying to remove the owner")
        void shouldThrowIllegalStateExceptionWhenTryingToRemoveOwner() {
            // Given
            when(userService.getCurrentUser(token)).thenReturn(testUser2);
            when(householdRepository.findById(householdId)).thenReturn(Optional.of(testHousehold));
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, userId2))
                    .thenReturn(Optional.of(adminMember));
            when(householdMemberRepository.findById(memberId)).thenReturn(Optional.of(ownerMember));

            // When/Then
            assertThatThrownBy(() -> householdMemberService.removeMember(householdId, memberId, token))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Cannot remove the owner");

            verify(householdMemberRepository, never()).delete(any());
        }

        @Test
        @DisplayName("Should throw AccessDeniedException when admin tries to remove another admin")
        void shouldThrowAccessDeniedExceptionWhenAdminTriesToRemoveAnotherAdmin() {
            // Given
            User otherAdmin = new User();
            otherAdmin.setId(UUID.randomUUID());
            otherAdmin.setUsername("otheradmin");

            HouseholdMember otherAdminMember = new HouseholdMember();
            otherAdminMember.setId(UUID.randomUUID());
            otherAdminMember.setUser(otherAdmin);
            otherAdminMember.setHousehold(testHousehold);
            otherAdminMember.setRole(HouseholdRole.ADMIN);

            when(userService.getCurrentUser(token)).thenReturn(testUser2);
            when(householdRepository.findById(householdId)).thenReturn(Optional.of(testHousehold));
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, userId2))
                    .thenReturn(Optional.of(adminMember));
            when(householdMemberRepository.findById(otherAdminMember.getId())).thenReturn(Optional.of(otherAdminMember));

            // When/Then
            assertThatThrownBy(() -> householdMemberService.removeMember(householdId, otherAdminMember.getId(), token))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("Admin cannot remove another admin");

            verify(householdMemberRepository, never()).delete(any());
        }
    }

    @Nested
    @DisplayName("Update Member Role Tests")
    class UpdateMemberRoleTests {

        @Test
        @DisplayName("Should update member role when user is owner")
        void shouldUpdateMemberRoleWhenUserIsOwner() {
            // Given
            when(userService.getCurrentUser(token)).thenReturn(testUser);
            when(householdRepository.findById(householdId)).thenReturn(Optional.of(testHousehold));
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, userId))
                    .thenReturn(Optional.of(ownerMember));
            when(householdMemberRepository.findById(memberId3)).thenReturn(Optional.of(regularMember));
            when(householdMemberRepository.save(any(HouseholdMember.class))).thenAnswer(invocation -> {
                return invocation.<HouseholdMember>getArgument(0);
            });

            // When
            HouseholdMemberResponse response = householdMemberService.updateMemberRole(householdId, memberId3, HouseholdRole.ADMIN, token);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getRole()).isEqualTo(HouseholdRole.ADMIN);
            verify(householdMemberRepository).save(regularMember);
        }

        @Test
        @DisplayName("Should update member role when user is admin")
        void shouldUpdateMemberRoleWhenUserIsAdmin() {
            // Given
            when(userService.getCurrentUser(token)).thenReturn(testUser2);
            when(householdRepository.findById(householdId)).thenReturn(Optional.of(testHousehold));
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, userId2))
                    .thenReturn(Optional.of(adminMember));
            when(householdMemberRepository.findById(memberId3)).thenReturn(Optional.of(regularMember));
            when(householdMemberRepository.save(any(HouseholdMember.class))).thenAnswer(invocation -> {
                return invocation.<HouseholdMember>getArgument(0);
            });

            // When
            HouseholdMemberResponse response = householdMemberService.updateMemberRole(householdId, memberId3, HouseholdRole.ADMIN, token);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getRole()).isEqualTo(HouseholdRole.ADMIN);
            verify(householdMemberRepository).save(regularMember);
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException when attempting to set role to OWNER")
        void shouldThrowIllegalArgumentExceptionWhenAttemptingToSetRoleToOwner() {
            // Given
            when(userService.getCurrentUser(token)).thenReturn(testUser);

            // When/Then
            assertThatThrownBy(() -> householdMemberService.updateMemberRole(householdId, memberId3, HouseholdRole.OWNER, token))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Cannot set role to OWNER");

            verify(householdMemberRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw AccessDeniedException when admin tries to update another admin's role")
        void shouldThrowAccessDeniedExceptionWhenAdminTriesToUpdateAnotherAdminRole() {
            // Given
            User otherAdmin = new User();
            otherAdmin.setId(UUID.randomUUID());
            otherAdmin.setUsername("otheradmin");

            HouseholdMember otherAdminMember = new HouseholdMember();
            otherAdminMember.setId(UUID.randomUUID());
            otherAdminMember.setUser(otherAdmin);
            otherAdminMember.setHousehold(testHousehold);
            otherAdminMember.setRole(HouseholdRole.ADMIN);

            when(userService.getCurrentUser(token)).thenReturn(testUser2);
            when(householdRepository.findById(householdId)).thenReturn(Optional.of(testHousehold));
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, userId2))
                    .thenReturn(Optional.of(adminMember));
            when(householdMemberRepository.findById(otherAdminMember.getId())).thenReturn(Optional.of(otherAdminMember));

            // When/Then
            assertThatThrownBy(() -> householdMemberService.updateMemberRole(householdId, otherAdminMember.getId(), HouseholdRole.MEMBER, token))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("Admin cannot update another admin's role");

            verify(householdMemberRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw AccessDeniedException when regular member tries to update roles")
        void shouldThrowAccessDeniedExceptionWhenRegularMemberTriesToUpdateRoles() {
            // Given
            when(userService.getCurrentUser(token)).thenReturn(testUser3);
            when(householdRepository.findById(householdId)).thenReturn(Optional.of(testHousehold));
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, userId3))
                    .thenReturn(Optional.of(regularMember));

            // When/Then
            assertThatThrownBy(() -> householdMemberService.updateMemberRole(householdId, memberId2, HouseholdRole.MEMBER, token))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("You don't have permission to update member roles");

            verify(householdMemberRepository, never()).save(any());
        }
    }
}