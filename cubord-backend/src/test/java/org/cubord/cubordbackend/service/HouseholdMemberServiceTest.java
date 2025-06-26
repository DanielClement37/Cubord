package org.cubord.cubordbackend.service;

import org.cubord.cubordbackend.domain.Household;
import org.cubord.cubordbackend.domain.HouseholdMember;
import org.cubord.cubordbackend.domain.HouseholdRole;
import org.cubord.cubordbackend.domain.User;
import org.cubord.cubordbackend.dto.HouseholdMemberRequest;
import org.cubord.cubordbackend.dto.HouseholdMemberResponse;
import org.cubord.cubordbackend.exception.ForbiddenException;
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
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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

        testUser = User.builder()
                .id(userId)
                .username("testuser")
                .email("test@example.com")
                .displayName("Test User")
                .build();

        testUser2 = User.builder()
                .id(userId2)
                .username("testuser2")
                .email("test2@example.com")
                .displayName("Test User 2")
                .build();

        testUser3 = User.builder()
                .id(userId3)
                .username("testuser3")
                .email("test3@example.com")
                .displayName("Test User 3")
                .build();

        testHousehold = Household.builder()
                .id(householdId)
                .name("Test Household")
                .members(new HashSet<>())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        ownerMember = HouseholdMember.builder()
                .id(memberId)
                .household(testHousehold)
                .user(testUser)
                .role(HouseholdRole.OWNER)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        adminMember = HouseholdMember.builder()
                .id(memberId2)
                .household(testHousehold)
                .user(testUser2)
                .role(HouseholdRole.ADMIN)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        regularMember = HouseholdMember.builder()
                .id(memberId3)
                .household(testHousehold)
                .user(testUser3)
                .role(HouseholdRole.MEMBER)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        // Set up common mock behaviors
        when(userService.getCurrentUser(token)).thenReturn(testUser);
    }

    @Nested
    @DisplayName("Add Member Tests")
    class AddMemberTests {

        @Test
        @DisplayName("Should add member successfully when user is owner")
        void shouldAddMemberSuccessfullyWhenUserIsOwner() {
            // Given
            UUID newUserId = UUID.randomUUID();
            User newUser = User.builder()
                    .id(newUserId)
                    .username("newuser")
                    .email("new@example.com")
                    .displayName("New User")
                    .build();

            HouseholdMemberRequest request = new HouseholdMemberRequest();
            request.setUserId(newUserId);
            request.setRole(HouseholdRole.MEMBER);

            when(householdRepository.findById(householdId)).thenReturn(Optional.of(testHousehold));
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, userId))
                    .thenReturn(Optional.of(ownerMember));
            when(userRepository.findById(newUserId)).thenReturn(Optional.of(newUser));
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, newUserId))
                    .thenReturn(Optional.empty());
            when(householdMemberRepository.save(any(HouseholdMember.class)))
                    .thenAnswer(invocation -> {
                        HouseholdMember savedMember = invocation.getArgument(0);
                        savedMember.setId(UUID.randomUUID());
                        return savedMember;
                    });

            // When
            HouseholdMemberResponse response = householdMemberService.addMemberToHousehold(householdId, request, token);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getUserId()).isEqualTo(newUserId);
            assertThat(response.getUsername()).isEqualTo(newUser.getUsername());
            assertThat(response.getHouseholdId()).isEqualTo(householdId);
            assertThat(response.getRole()).isEqualTo(HouseholdRole.MEMBER);

            verify(householdRepository).findById(householdId);
            verify(householdMemberRepository).findByHouseholdIdAndUserId(householdId, userId);
            verify(userRepository).findById(newUserId);
            verify(householdMemberRepository).findByHouseholdIdAndUserId(householdId, newUserId);
            verify(householdMemberRepository).save(any(HouseholdMember.class));
        }

        @Test
        @DisplayName("Should add member successfully when user is admin")
        void shouldAddMemberSuccessfullyWhenUserIsAdmin() {
            // Given
            UUID newUserId = UUID.randomUUID();
            User newUser = User.builder()
                    .id(newUserId)
                    .username("newuser")
                    .email("new@example.com")
                    .displayName("New User")
                    .build();

            HouseholdMemberRequest request = new HouseholdMemberRequest();
            request.setUserId(newUserId);
            request.setRole(HouseholdRole.MEMBER);

            when(userService.getCurrentUser(token)).thenReturn(testUser2);
            when(householdRepository.findById(householdId)).thenReturn(Optional.of(testHousehold));
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, userId2))
                    .thenReturn(Optional.of(adminMember));
            when(userRepository.findById(newUserId)).thenReturn(Optional.of(newUser));
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, newUserId))
                    .thenReturn(Optional.empty());
            when(householdMemberRepository.save(any(HouseholdMember.class)))
                    .thenAnswer(invocation -> {
                        HouseholdMember savedMember = invocation.getArgument(0);
                        savedMember.setId(UUID.randomUUID());
                        return savedMember;
                    });

            // When
            HouseholdMemberResponse response = householdMemberService.addMemberToHousehold(householdId, request, token);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getUserId()).isEqualTo(newUserId);
            assertThat(response.getRole()).isEqualTo(HouseholdRole.MEMBER);

            verify(householdMemberRepository).save(any(HouseholdMember.class));
        }

        @Test
        @DisplayName("Should throw NotFoundException when household doesn't exist")
        void shouldThrowNotFoundExceptionWhenHouseholdDoesntExist() {
            // Given
            UUID nonExistentHouseholdId = UUID.randomUUID();
            HouseholdMemberRequest request = new HouseholdMemberRequest();
            request.setUserId(UUID.randomUUID());
            request.setRole(HouseholdRole.MEMBER);

            when(householdRepository.findById(nonExistentHouseholdId)).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> householdMemberService.addMemberToHousehold(nonExistentHouseholdId, request, token))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("Household not found");

            verify(householdRepository).findById(nonExistentHouseholdId);
            verify(householdMemberRepository, never()).save(any(HouseholdMember.class));
        }

        @Test
        @DisplayName("Should throw AccessDeniedException when user is not a member")
        void shouldThrowAccessDeniedExceptionWhenUserIsNotMember() {
            // Given
            HouseholdMemberRequest request = new HouseholdMemberRequest();
            request.setUserId(UUID.randomUUID());
            request.setRole(HouseholdRole.MEMBER);

            when(householdRepository.findById(householdId)).thenReturn(Optional.of(testHousehold));
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, userId))
                    .thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> householdMemberService.addMemberToHousehold(householdId, request, token))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessage("You don't have access to this household");

            verify(householdRepository).findById(householdId);
            verify(householdMemberRepository).findByHouseholdIdAndUserId(householdId, userId);
            verify(householdMemberRepository, never()).save(any(HouseholdMember.class));
        }

        @Test
        @DisplayName("Should throw AccessDeniedException when user is regular member")
        void shouldThrowAccessDeniedExceptionWhenUserIsRegularMember() {
            // Given
            HouseholdMemberRequest request = new HouseholdMemberRequest();
            request.setUserId(UUID.randomUUID());
            request.setRole(HouseholdRole.MEMBER);

            when(userService.getCurrentUser(token)).thenReturn(testUser3);
            when(householdRepository.findById(householdId)).thenReturn(Optional.of(testHousehold));
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, userId3))
                    .thenReturn(Optional.of(regularMember));

            // When & Then
            assertThatThrownBy(() -> householdMemberService.addMemberToHousehold(householdId, request, token))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessage("You don't have permission to add members to this household");

            verify(householdRepository).findById(householdId);
            verify(householdMemberRepository).findByHouseholdIdAndUserId(householdId, userId3);
            verify(householdMemberRepository, never()).save(any(HouseholdMember.class));
        }

        @Test
        @DisplayName("Should throw NotFoundException when user to add doesn't exist")
        void shouldThrowNotFoundExceptionWhenUserToAddDoesntExist() {
            // Given
            UUID nonExistentUserId = UUID.randomUUID();
            HouseholdMemberRequest request = new HouseholdMemberRequest();
            request.setUserId(nonExistentUserId);
            request.setRole(HouseholdRole.MEMBER);

            when(householdRepository.findById(householdId)).thenReturn(Optional.of(testHousehold));
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, userId))
                    .thenReturn(Optional.of(ownerMember));
            when(userRepository.findById(nonExistentUserId)).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> householdMemberService.addMemberToHousehold(householdId, request, token))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("User not found");

            verify(householdRepository).findById(householdId);
            verify(householdMemberRepository).findByHouseholdIdAndUserId(householdId, userId);
            verify(userRepository).findById(nonExistentUserId);
            verify(householdMemberRepository, never()).save(any(HouseholdMember.class));
        }

        @Test
        @DisplayName("Should throw IllegalStateException when user is already a member")
        void shouldThrowIllegalStateExceptionWhenUserIsAlreadyMember() {
            // Given
            HouseholdMemberRequest request = new HouseholdMemberRequest();
            request.setUserId(userId2);
            request.setRole(HouseholdRole.MEMBER);

            when(householdRepository.findById(householdId)).thenReturn(Optional.of(testHousehold));
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, userId))
                    .thenReturn(Optional.of(ownerMember));
            when(userRepository.findById(userId2)).thenReturn(Optional.of(testUser2));
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, userId2))
                    .thenReturn(Optional.of(adminMember));

            // When & Then
            assertThatThrownBy(() -> householdMemberService.addMemberToHousehold(householdId, request, token))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("User is already a member of this household");

            verify(householdRepository).findById(householdId);
            verify(householdMemberRepository).findByHouseholdIdAndUserId(householdId, userId);
            verify(userRepository).findById(userId2);
            verify(householdMemberRepository).findByHouseholdIdAndUserId(householdId, userId2);
            verify(householdMemberRepository, never()).save(any(HouseholdMember.class));
        }
    }

    @Nested
    @DisplayName("Get Household Members Tests")
    class GetHouseholdMembersTests {

        @Test
        @DisplayName("Should get all members when user is a member")
        void shouldGetAllMembersWhenUserIsMember() {
            // Given
            List<HouseholdMember> members = Arrays.asList(ownerMember, adminMember, regularMember);

            when(householdRepository.findById(householdId)).thenReturn(Optional.of(testHousehold));
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, userId))
                    .thenReturn(Optional.of(ownerMember));
            when(householdMemberRepository.findByHouseholdId(householdId)).thenReturn(members);

            // When
            List<HouseholdMemberResponse> responses = householdMemberService.getHouseholdMembers(householdId, token);

            // Then
            assertThat(responses).hasSize(3);
            assertThat(responses).extracting("role")
                    .containsExactlyInAnyOrder(HouseholdRole.OWNER, HouseholdRole.ADMIN, HouseholdRole.MEMBER);

            verify(householdRepository).findById(householdId);
            verify(householdMemberRepository).findByHouseholdIdAndUserId(householdId, userId);
            verify(householdMemberRepository).findByHouseholdId(householdId);
        }

        @Test
        @DisplayName("Should throw NotFoundException when household doesn't exist")
        void shouldThrowNotFoundExceptionWhenHouseholdDoesntExist() {
            // Given
            UUID nonExistentHouseholdId = UUID.randomUUID();
            when(householdRepository.findById(nonExistentHouseholdId)).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> householdMemberService.getHouseholdMembers(nonExistentHouseholdId, token))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("Household not found");

            verify(householdRepository).findById(nonExistentHouseholdId);
            verify(householdMemberRepository, never()).findByHouseholdId(any());
        }

        @Test
        @DisplayName("Should throw AccessDeniedException when user is not a member")
        void shouldThrowAccessDeniedExceptionWhenUserIsNotMember() {
            // Given
            when(householdRepository.findById(householdId)).thenReturn(Optional.of(testHousehold));
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, userId))
                    .thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> householdMemberService.getHouseholdMembers(householdId, token))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessage("You don't have access to this household");

            verify(householdRepository).findById(householdId);
            verify(householdMemberRepository).findByHouseholdIdAndUserId(householdId, userId);
            verify(householdMemberRepository, never()).findByHouseholdId(any());
        }

        @Test
        @DisplayName("Should return empty list when household has no members")
        void shouldReturnEmptyListWhenHouseholdHasNoMembers() {
            // Given
            when(householdRepository.findById(householdId)).thenReturn(Optional.of(testHousehold));
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, userId))
                    .thenReturn(Optional.of(ownerMember));
            when(householdMemberRepository.findByHouseholdId(householdId)).thenReturn(Collections.emptyList());

            // When
            List<HouseholdMemberResponse> responses = householdMemberService.getHouseholdMembers(householdId, token);

            // Then
            assertThat(responses).isEmpty();

            verify(householdRepository).findById(householdId);
            verify(householdMemberRepository).findByHouseholdIdAndUserId(householdId, userId);
            verify(householdMemberRepository).findByHouseholdId(householdId);
        }
    }

    @Nested
    @DisplayName("Get Member By ID Tests")
    class GetMemberByIdTests {

        @Test
        @DisplayName("Should get member details when user is a household member")
        void shouldGetMemberDetailsWhenUserIsHouseholdMember() {
            // Given
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

            verify(householdRepository).findById(householdId);
            verify(householdMemberRepository).findByHouseholdIdAndUserId(householdId, userId);
            verify(householdMemberRepository).findById(memberId2);
        }

        @Test
        @DisplayName("Should throw NotFoundException when household doesn't exist")
        void shouldThrowNotFoundExceptionWhenHouseholdDoesntExist() {
            // Given
            UUID nonExistentHouseholdId = UUID.randomUUID();
            when(householdRepository.findById(nonExistentHouseholdId)).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> householdMemberService.getMemberById(nonExistentHouseholdId, memberId2, token))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("Household not found");

            verify(householdRepository).findById(nonExistentHouseholdId);
            verify(householdMemberRepository, never()).findById(any());
        }

        @Test
        @DisplayName("Should throw AccessDeniedException when user is not a household member")
        void shouldThrowAccessDeniedExceptionWhenUserIsNotHouseholdMember() {
            // Given
            when(householdRepository.findById(householdId)).thenReturn(Optional.of(testHousehold));
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, userId))
                    .thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> householdMemberService.getMemberById(householdId, memberId2, token))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessage("You don't have access to this household");

            verify(householdRepository).findById(householdId);
            verify(householdMemberRepository).findByHouseholdIdAndUserId(householdId, userId);
            verify(householdMemberRepository, never()).findById(any());
        }

        @Test
        @DisplayName("Should throw NotFoundException when member doesn't exist")
        void shouldThrowNotFoundExceptionWhenMemberDoesntExist() {
            // Given
            UUID nonExistentMemberId = UUID.randomUUID();
            when(householdRepository.findById(householdId)).thenReturn(Optional.of(testHousehold));
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, userId))
                    .thenReturn(Optional.of(ownerMember));
            when(householdMemberRepository.findById(nonExistentMemberId)).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> householdMemberService.getMemberById(householdId, nonExistentMemberId, token))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("Household member not found");

            verify(householdRepository).findById(householdId);
            verify(householdMemberRepository).findByHouseholdIdAndUserId(householdId, userId);
            verify(householdMemberRepository).findById(nonExistentMemberId);
        }

        @Test
        @DisplayName("Should throw ForbiddenException when member is not from the specified household")
        void shouldThrowForbiddenExceptionWhenMemberIsNotFromSpecifiedHousehold() {
            // Given
            Household otherHousehold = Household.builder()
                    .id(UUID.randomUUID())
                    .name("Other Household")
                    .build();

            HouseholdMember otherHouseholdMember = HouseholdMember.builder()
                    .id(UUID.randomUUID())
                    .household(otherHousehold)
                    .user(testUser2)
                    .role(HouseholdRole.MEMBER)
                    .build();

            when(householdRepository.findById(householdId)).thenReturn(Optional.of(testHousehold));
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, userId))
                    .thenReturn(Optional.of(ownerMember));
            when(householdMemberRepository.findById(otherHouseholdMember.getId())).thenReturn(Optional.of(otherHouseholdMember));

            // When & Then
            assertThatThrownBy(() -> householdMemberService.getMemberById(householdId, otherHouseholdMember.getId(), token))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessage("Member does not belong to the specified household");

            verify(householdRepository).findById(householdId);
            verify(householdMemberRepository).findByHouseholdIdAndUserId(householdId, userId);
            verify(householdMemberRepository).findById(otherHouseholdMember.getId());
        }
    }

    @Nested
    @DisplayName("Remove Member Tests")
    class RemoveMemberTests {

        @Test
        @DisplayName("Should remove member when user is owner")
        void shouldRemoveMemberWhenUserIsOwner() {
            // Given
            when(householdRepository.findById(householdId)).thenReturn(Optional.of(testHousehold));
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, userId))
                    .thenReturn(Optional.of(ownerMember));
            when(householdMemberRepository.findById(memberId3)).thenReturn(Optional.of(regularMember));

            // When
            householdMemberService.removeMember(householdId, memberId3, token);

            // Then
            verify(householdRepository).findById(householdId);
            verify(householdMemberRepository).findByHouseholdIdAndUserId(householdId, userId);
            verify(householdMemberRepository).findById(memberId3);
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
            verify(householdRepository).findById(householdId);
            verify(householdMemberRepository).findByHouseholdIdAndUserId(householdId, userId2);
            verify(householdMemberRepository).findById(memberId3);
            verify(householdMemberRepository).delete(regularMember);
        }

        @Test
        @DisplayName("Should throw NotFoundException when household doesn't exist")
        void shouldThrowNotFoundExceptionWhenHouseholdDoesntExist() {
            // Given
            UUID nonExistentHouseholdId = UUID.randomUUID();
            when(householdRepository.findById(nonExistentHouseholdId)).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> householdMemberService.removeMember(nonExistentHouseholdId, memberId3, token))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("Household not found");

            verify(householdRepository).findById(nonExistentHouseholdId);
            verify(householdMemberRepository, never()).delete(any());
        }

        @Test
        @DisplayName("Should throw AccessDeniedException when user is not a household member")
        void shouldThrowAccessDeniedExceptionWhenUserIsNotHouseholdMember() {
            // Given
            when(householdRepository.findById(householdId)).thenReturn(Optional.of(testHousehold));
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, userId))
                    .thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> householdMemberService.removeMember(householdId, memberId3, token))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessage("You don't have access to this household");

            verify(householdRepository).findById(householdId);
            verify(householdMemberRepository).findByHouseholdIdAndUserId(householdId, userId);
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

            // When & Then
            assertThatThrownBy(() -> householdMemberService.removeMember(householdId, memberId2, token))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessage("You don't have permission to remove members from this household");

            verify(householdRepository).findById(householdId);
            verify(householdMemberRepository).findByHouseholdIdAndUserId(householdId, userId3);
            verify(householdMemberRepository, never()).delete(any());
        }

        @Test
        @DisplayName("Should throw NotFoundException when member doesn't exist")
        void shouldThrowNotFoundExceptionWhenMemberDoesntExist() {
            // Given
            UUID nonExistentMemberId = UUID.randomUUID();
            when(householdRepository.findById(householdId)).thenReturn(Optional.of(testHousehold));
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, userId))
                    .thenReturn(Optional.of(ownerMember));
            when(householdMemberRepository.findById(nonExistentMemberId)).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> householdMemberService.removeMember(householdId, nonExistentMemberId, token))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("Household member not found");

            verify(householdRepository).findById(householdId);
            verify(householdMemberRepository).findByHouseholdIdAndUserId(householdId, userId);
            verify(householdMemberRepository).findById(nonExistentMemberId);
            verify(householdMemberRepository, never()).delete(any());
        }

        @Test
        @DisplayName("Should throw ForbiddenException when member is not from the specified household")
        void shouldThrowForbiddenExceptionWhenMemberIsNotFromSpecifiedHousehold() {
            // Given
            Household otherHousehold = Household.builder()
                    .id(UUID.randomUUID())
                    .name("Other Household")
                    .build();

            HouseholdMember otherHouseholdMember = HouseholdMember.builder()
                    .id(UUID.randomUUID())
                    .household(otherHousehold)
                    .user(testUser2)
                    .role(HouseholdRole.MEMBER)
                    .build();

            when(householdRepository.findById(householdId)).thenReturn(Optional.of(testHousehold));
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, userId))
                    .thenReturn(Optional.of(ownerMember));
            when(householdMemberRepository.findById(otherHouseholdMember.getId())).thenReturn(Optional.of(otherHouseholdMember));

            // When & Then
            assertThatThrownBy(() -> householdMemberService.removeMember(householdId, otherHouseholdMember.getId(), token))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessage("Member does not belong to the specified household");

            verify(householdRepository).findById(householdId);
            verify(householdMemberRepository).findByHouseholdIdAndUserId(householdId, userId);
            verify(householdMemberRepository).findById(otherHouseholdMember.getId());
            verify(householdMemberRepository, never()).delete(any());
        }

        @Test
        @DisplayName("Should throw IllegalStateException when trying to remove the owner")
        void shouldThrowIllegalStateExceptionWhenTryingToRemoveOwner() {
            // Given
            when(householdRepository.findById(householdId)).thenReturn(Optional.of(testHousehold));
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, userId))
                    .thenReturn(Optional.of(ownerMember));
            when(householdMemberRepository.findById(memberId)).thenReturn(Optional.of(ownerMember));

            // When & Then
            assertThatThrownBy(() -> householdMemberService.removeMember(householdId, memberId, token))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Cannot remove the household owner. Transfer ownership first.");

            verify(householdRepository).findById(householdId);
            verify(householdMemberRepository).findByHouseholdIdAndUserId(householdId, userId);
            verify(householdMemberRepository).findById(memberId);
            verify(householdMemberRepository, never()).delete(any());
        }

        @Test
        @DisplayName("Should throw ForbiddenException when admin tries to remove another admin")
        void shouldThrowForbiddenExceptionWhenAdminTriesToRemoveAnotherAdmin() {
            // Given
            User anotherAdminUser = User.builder()
                    .id(UUID.randomUUID())
                    .username("adminuser2")
                    .build();

            HouseholdMember anotherAdminMember = HouseholdMember.builder()
                    .id(UUID.randomUUID())
                    .household(testHousehold)
                    .user(anotherAdminUser)
                    .role(HouseholdRole.ADMIN)
                    .build();

            when(userService.getCurrentUser(token)).thenReturn(testUser2); // Admin user
            when(householdRepository.findById(householdId)).thenReturn(Optional.of(testHousehold));
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, userId2))
                    .thenReturn(Optional.of(adminMember));
            when(householdMemberRepository.findById(anotherAdminMember.getId())).thenReturn(Optional.of(anotherAdminMember));

            // When & Then
            assertThatThrownBy(() -> householdMemberService.removeMember(householdId, anotherAdminMember.getId(), token))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessage("Admins cannot remove other admins. Only the owner can do that.");

            verify(householdRepository).findById(householdId);
            verify(householdMemberRepository).findByHouseholdIdAndUserId(householdId, userId2);
            verify(householdMemberRepository).findById(anotherAdminMember.getId());
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
            HouseholdRole newRole = HouseholdRole.ADMIN;

            when(householdRepository.findById(householdId)).thenReturn(Optional.of(testHousehold));
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, userId))
                    .thenReturn(Optional.of(ownerMember));
            when(householdMemberRepository.findById(memberId3)).thenReturn(Optional.of(regularMember));
            when(householdMemberRepository.save(any(HouseholdMember.class))).thenReturn(regularMember);

            // When
            HouseholdMemberResponse response = householdMemberService.updateMemberRole(householdId, memberId3, newRole, token);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo(memberId3);
            assertThat(response.getRole()).isEqualTo(newRole);

            verify(householdRepository).findById(householdId);
            verify(householdMemberRepository).findByHouseholdIdAndUserId(householdId, userId);
            verify(householdMemberRepository).findById(memberId3);
            verify(householdMemberRepository).save(any(HouseholdMember.class));
        }

        @Test
        @DisplayName("Should update member role when user is admin")
        void shouldUpdateMemberRoleWhenUserIsAdmin() {
            // Given
            HouseholdRole newRole = HouseholdRole.MEMBER;

            when(userService.getCurrentUser(token)).thenReturn(testUser2);
            when(householdRepository.findById(householdId)).thenReturn(Optional.of(testHousehold));
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, userId2))
                    .thenReturn(Optional.of(adminMember));
            when(householdMemberRepository.findById(memberId3)).thenReturn(Optional.of(regularMember));
            when(householdMemberRepository.save(any(HouseholdMember.class))).thenReturn(regularMember);

            // When
            HouseholdMemberResponse response = householdMemberService.updateMemberRole(householdId, memberId3, newRole, token);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getRole()).isEqualTo(newRole);

            verify(householdRepository).findById(householdId);
            verify(householdMemberRepository).findByHouseholdIdAndUserId(householdId, userId2);
            verify(householdMemberRepository).findById(memberId3);
            verify(householdMemberRepository).save(any(HouseholdMember.class));
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException when attempting to set role to OWNER")
        void shouldThrowIllegalArgumentExceptionWhenAttemptingToSetRoleToOwner() {
            // Given
            when(householdRepository.findById(householdId)).thenReturn(Optional.of(testHousehold));
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, userId))
                    .thenReturn(Optional.of(ownerMember));
            when(householdMemberRepository.findById(memberId3)).thenReturn(Optional.of(regularMember));

            // When & Then
            assertThatThrownBy(() -> householdMemberService.updateMemberRole(householdId, memberId3, HouseholdRole.OWNER, token))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Cannot set role to OWNER. Use transferOwnership method instead.");

            verify(householdRepository).findById(householdId);
            verify(householdMemberRepository).findByHouseholdIdAndUserId(householdId, userId);
            verify(householdMemberRepository).findById(memberId3);
            verify(householdMemberRepository, never()).save(any(HouseholdMember.class));
        }

        @Test
        @DisplayName("Should throw ForbiddenException when admin tries to update another admin's role")
        void shouldThrowForbiddenExceptionWhenAdminTriesToUpdateAnotherAdminRole() {
            // Given
            when(userService.getCurrentUser(token)).thenReturn(testUser2); // Admin user
            when(householdRepository.findById(householdId)).thenReturn(Optional.of(testHousehold));
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, userId2))
                    .thenReturn(Optional.of(adminMember));

            User anotherAdminUser = User.builder()
                    .id(UUID.randomUUID())
                    .username("adminuser2")
                    .build();

            HouseholdMember anotherAdminMember = HouseholdMember.builder()
                    .id(UUID.randomUUID())
                    .household(testHousehold)
                    .user(anotherAdminUser)
                    .role(HouseholdRole.ADMIN)
                    .build();

            when(householdMemberRepository.findById(anotherAdminMember.getId())).thenReturn(Optional.of(anotherAdminMember));

            // When & Then
            assertThatThrownBy(() -> householdMemberService.updateMemberRole(
                    householdId, anotherAdminMember.getId(), HouseholdRole.MEMBER, token))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessage("Only the owner can change the role of another admin");

            verify(householdRepository).findById(householdId);
            verify(householdMemberRepository).findByHouseholdIdAndUserId(householdId, userId2);
            verify(householdMemberRepository).findById(anotherAdminMember.getId());
            verify(householdMemberRepository, never()).save(any(HouseholdMember.class));
        }

        @Test
        @DisplayName("Should throw ForbiddenException when regular member tries to update roles")
        void shouldThrowForbiddenExceptionWhenRegularMemberTriesToUpdateRoles() {
            // Given
            when(userService.getCurrentUser(token)).thenReturn(testUser3); // Regular member
            when(householdRepository.findById(householdId)).thenReturn(Optional.of(testHousehold));
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, userId3))
                    .thenReturn(Optional.of(regularMember));

            // When & Then
            assertThatThrownBy(() -> householdMemberService.updateMemberRole(
                    householdId, memberId2, HouseholdRole.MEMBER, token))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessage("You don't have permission to change member roles");

            verify(householdRepository).findById(householdId);
            verify(householdMemberRepository).findByHouseholdIdAndUserId(householdId, userId3);
            verify(householdMemberRepository, never()).findById(any());
            verify(householdMemberRepository, never()).save(any(HouseholdMember.class));
        }
    }

    @Nested
    @DisplayName("Process Invitation Tests")
    class ProcessInvitationTests {

        @Test
        @DisplayName("Should accept invitation successfully")
        void shouldAcceptInvitationSuccessfully() {
            // Given
            // Assuming we have an invitation status enum and field added to HouseholdMember
            UUID invitationId = UUID.randomUUID();
            boolean accept = true;

            HouseholdMember invitation = HouseholdMember.builder()
                    .id(invitationId)
                    .household(testHousehold)
                    .user(testUser)
                    .role(HouseholdRole.MEMBER)
                    // .invitationStatus(InvitationStatus.PENDING)
                    .createdAt(LocalDateTime.now().minusDays(1))
                    .updatedAt(LocalDateTime.now().minusDays(1))
                    .build();

            when(householdMemberRepository.findById(invitationId)).thenReturn(Optional.of(invitation));
            when(userService.getCurrentUser(token)).thenReturn(testUser);
            when(householdMemberRepository.save(any(HouseholdMember.class))).thenReturn(invitation);

            // When
            HouseholdMemberResponse response = householdMemberService.processInvitation(invitationId, accept, token);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo(invitationId);
            // assertThat(response.getInvitationStatus()).isEqualTo(InvitationStatus.ACCEPTED);

            verify(householdMemberRepository).findById(invitationId);
            verify(householdMemberRepository).save(invitation);
        }

        @Test
        @DisplayName("Should decline invitation successfully")
        void shouldDeclineInvitationSuccessfully() {
            // Given
            UUID invitationId = UUID.randomUUID();
            boolean accept = false;

            HouseholdMember invitation = HouseholdMember.builder()
                    .id(invitationId)
                    .household(testHousehold)
                    .user(testUser)
                    .role(HouseholdRole.MEMBER)
                    // .invitationStatus(InvitationStatus.PENDING)
                    .createdAt(LocalDateTime.now().minusDays(1))
                    .updatedAt(LocalDateTime.now().minusDays(1))
                    .build();

            when(householdMemberRepository.findById(invitationId)).thenReturn(Optional.of(invitation));
            when(userService.getCurrentUser(token)).thenReturn(testUser);

            // When
            HouseholdMemberResponse response = householdMemberService.processInvitation(invitationId, accept, token);

            // Then
            assertThat(response).isNotNull();
            // assertThat(response.getInvitationStatus()).isEqualTo(InvitationStatus.DECLINED);

            verify(householdMemberRepository).findById(invitationId);
            verify(householdMemberRepository).delete(invitation);
        }

        @Test
        @DisplayName("Should throw NotFoundException when invitation doesn't exist")
        void shouldThrowNotFoundExceptionWhenInvitationDoesntExist() {
            // Given
            UUID nonExistentInvitationId = UUID.randomUUID();
            when(householdMemberRepository.findById(nonExistentInvitationId)).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> householdMemberService.processInvitation(nonExistentInvitationId, true, token))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("Invitation not found");

            verify(householdMemberRepository).findById(nonExistentInvitationId);
            verify(householdMemberRepository, never()).save(any(HouseholdMember.class));
            verify(householdMemberRepository, never()).delete(any(HouseholdMember.class));
        }

        @Test
        @DisplayName("Should throw ForbiddenException when user tries to process someone else's invitation")
        void shouldThrowForbiddenExceptionWhenUserTriesToProcessSomeoneElsesInvitation() {
            // Given
            UUID invitationId = UUID.randomUUID();
            
            HouseholdMember invitation = HouseholdMember.builder()
                    .id(invitationId)
                    .household(testHousehold)
                    .user(testUser2) // Invitation for another user
                    .role(HouseholdRole.MEMBER)
                    // .invitationStatus(InvitationStatus.PENDING)
                    .build();

            when(householdMemberRepository.findById(invitationId)).thenReturn(Optional.of(invitation));
            when(userService.getCurrentUser(token)).thenReturn(testUser); // Current user

            // When & Then
            assertThatThrownBy(() -> householdMemberService.processInvitation(invitationId, true, token))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessage("You can only process your own invitations");

            verify(householdMemberRepository).findById(invitationId);
            verify(householdMemberRepository, never()).save(any(HouseholdMember.class));
            verify(householdMemberRepository, never()).delete(any(HouseholdMember.class));
        }

        @Test
        @DisplayName("Should throw IllegalStateException when invitation is not pending")
        void shouldThrowIllegalStateExceptionWhenInvitationIsNotPending() {
            // Given
            UUID invitationId = UUID.randomUUID();
            
            HouseholdMember invitation = HouseholdMember.builder()
                    .id(invitationId)
                    .household(testHousehold)
                    .user(testUser)
                    .role(HouseholdRole.MEMBER)
                    // .invitationStatus(InvitationStatus.ACCEPTED) // Already accepted
                    .build();

            when(householdMemberRepository.findById(invitationId)).thenReturn(Optional.of(invitation));
            when(userService.getCurrentUser(token)).thenReturn(testUser);

            // When & Then
            assertThatThrownBy(() -> householdMemberService.processInvitation(invitationId, true, token))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("This invitation has already been processed");

            verify(householdMemberRepository).findById(invitationId);
            verify(householdMemberRepository, never()).save(any(HouseholdMember.class));
            verify(householdMemberRepository, never()).delete(any(HouseholdMember.class));
        }
    }

    @Nested
    @DisplayName("Get Invitations Tests")
    class GetInvitationsTests {

        @Test
        @DisplayName("Should get user's pending invitations")
        void shouldGetUsersPendingInvitations() {
            // Given
            HouseholdMember invitation1 = HouseholdMember.builder()
                    .id(UUID.randomUUID())
                    .household(testHousehold)
                    .user(testUser)
                    .role(HouseholdRole.MEMBER)
                    // .invitationStatus(InvitationStatus.PENDING)
                    .createdAt(LocalDateTime.now().minusDays(1))
                    .build();

            Household anotherHousehold = Household.builder()
                    .id(UUID.randomUUID())
                    .name("Another Household")
                    .build();

            HouseholdMember invitation2 = HouseholdMember.builder()
                    .id(UUID.randomUUID())
                    .household(anotherHousehold)
                    .user(testUser)
                    .role(HouseholdRole.MEMBER)
                    // .invitationStatus(InvitationStatus.PENDING)
                    .createdAt(LocalDateTime.now().minusDays(2))
                    .build();

            List<HouseholdMember> pendingInvitations = Arrays.asList(invitation1, invitation2);

            when(userService.getCurrentUser(token)).thenReturn(testUser);
            // when(householdMemberRepository.findByUserIdAndInvitationStatus(userId, InvitationStatus.PENDING))
            //     .thenReturn(pendingInvitations);
            
            // Mocking alternative method until invitation status is implemented
            when(householdMemberRepository.findByUserId(userId)).thenReturn(pendingInvitations);

            // When
            List<HouseholdMemberResponse> responses = householdMemberService.getUserInvitations(token);

            // Then
            assertThat(responses).hasSize(2);
            assertThat(responses).extracting("householdName")
                    .containsExactlyInAnyOrder("Test Household", "Another Household");

            verify(userService).getCurrentUser(token);
            // verify(householdMemberRepository).findByUserIdAndInvitationStatus(userId, InvitationStatus.PENDING);
            verify(householdMemberRepository).findByUserId(userId);
        }

        @Test
        @DisplayName("Should return empty list when user has no invitations")
        void shouldReturnEmptyListWhenUserHasNoInvitations() {
            // Given
            when(userService.getCurrentUser(token)).thenReturn(testUser);
            // when(householdMemberRepository.findByUserIdAndInvitationStatus(userId, InvitationStatus.PENDING))
            //     .thenReturn(Collections.emptyList());
            
            // Mocking alternative method until invitation status is implemented
            when(householdMemberRepository.findByUserId(userId)).thenReturn(Collections.emptyList());

            // When
            List<HouseholdMemberResponse> responses = householdMemberService.getUserInvitations(token);

            // Then
            assertThat(responses).isEmpty();

            verify(userService).getCurrentUser(token);
            // verify(householdMemberRepository).findByUserIdAndInvitationStatus(userId, InvitationStatus.PENDING);
            verify(householdMemberRepository).findByUserId(userId);
        }
    }
}