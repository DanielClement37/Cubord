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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Comprehensive test suite for HouseholdMemberService following the modernized security architecture.
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
@DisplayName("HouseholdMemberService Tests")
class HouseholdMemberServiceTest {

    @Mock
    private HouseholdRepository householdRepository;

    @Mock
    private HouseholdMemberRepository householdMemberRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SecurityService securityService;

    @InjectMocks
    private HouseholdMemberService householdMemberService;

    // Test data
    private UUID testUserId;
    private UUID otherUserId;
    private UUID thirdUserId;
    private UUID householdId;
    private UUID memberId;
    private UUID adminMemberId;
    private UUID regularMemberId;
    private User testUser;
    private User otherUser;
    private User thirdUser;
    private Household testHousehold;
    private HouseholdMember ownerMember;
    private HouseholdMember adminMember;
    private HouseholdMember regularMember;
    private HouseholdMemberRequest testMemberRequest;

    @BeforeEach
    void setUp() {
        testUserId = UUID.randomUUID();
        otherUserId = UUID.randomUUID();
        thirdUserId = UUID.randomUUID();
        householdId = UUID.randomUUID();
        memberId = UUID.randomUUID();
        adminMemberId = UUID.randomUUID();
        regularMemberId = UUID.randomUUID();

        testUser = User.builder()
                .id(testUserId)
                .username("owner")
                .email("owner@example.com")
                .displayName("Owner User")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        otherUser = User.builder()
                .id(otherUserId)
                .username("admin")
                .email("admin@example.com")
                .displayName("Admin User")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        thirdUser = User.builder()
                .id(thirdUserId)
                .username("member")
                .email("member@example.com")
                .displayName("Member User")
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
                .id(memberId)
                .user(testUser)
                .household(testHousehold)
                .role(HouseholdRole.OWNER)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        adminMember = HouseholdMember.builder()
                .id(adminMemberId)
                .user(otherUser)
                .household(testHousehold)
                .role(HouseholdRole.ADMIN)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        regularMember = HouseholdMember.builder()
                .id(regularMemberId)
                .user(thirdUser)
                .household(testHousehold)
                .role(HouseholdRole.MEMBER)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        testMemberRequest = HouseholdMemberRequest.builder()
                .userId(thirdUserId)
                .role(HouseholdRole.MEMBER)
                .build();
    }

    // ==================== Test Utilities ====================

    /**
     * Use this when the code under test calls securityService.getCurrentUserId().
     */
    private void stubCurrentUserId(UUID userId) {
        when(securityService.getCurrentUserId()).thenReturn(userId);
    }

    // ==================== Create Operations Tests ====================

    @Nested
    @DisplayName("addMemberToHousehold")
    class AddMemberTests {

        @Test
        @DisplayName("should add member successfully when user is owner")
        void shouldAddMemberSuccessfullyWhenUserIsOwner() {
            // Given
            stubCurrentUserId(testUserId);
            when(householdRepository.findById(eq(householdId))).thenReturn(Optional.of(testHousehold));
            when(userRepository.findById(eq(thirdUserId))).thenReturn(Optional.of(thirdUser));
            when(householdMemberRepository.existsByHouseholdIdAndUserId(eq(householdId), eq(thirdUserId))).thenReturn(false);
            when(householdMemberRepository.save(any(HouseholdMember.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // When
            HouseholdMemberResponse response = householdMemberService.addMemberToHousehold(householdId, testMemberRequest);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getUserId()).isEqualTo(thirdUserId);
            assertThat(response.getHouseholdId()).isEqualTo(householdId);
            assertThat(response.getRole()).isEqualTo(HouseholdRole.MEMBER);

            verify(securityService).getCurrentUserId();
            verify(householdRepository).findById(eq(householdId));
            verify(userRepository).findById(eq(thirdUserId));
            verify(householdMemberRepository).existsByHouseholdIdAndUserId(eq(householdId), eq(thirdUserId));
            verify(householdMemberRepository).save(any(HouseholdMember.class));
        }

        @Test
        @DisplayName("should add member successfully when user is admin")
        void shouldAddMemberSuccessfullyWhenUserIsAdmin() {
            // Given
            stubCurrentUserId(otherUserId);
            when(householdRepository.findById(eq(householdId))).thenReturn(Optional.of(testHousehold));
            when(userRepository.findById(eq(thirdUserId))).thenReturn(Optional.of(thirdUser));
            when(householdMemberRepository.existsByHouseholdIdAndUserId(eq(householdId), eq(thirdUserId))).thenReturn(false);
            when(householdMemberRepository.save(any(HouseholdMember.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // When
            HouseholdMemberResponse response = householdMemberService.addMemberToHousehold(householdId, testMemberRequest);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getUserId()).isEqualTo(thirdUserId);
            assertThat(response.getRole()).isEqualTo(HouseholdRole.MEMBER);

            verify(householdMemberRepository).save(any(HouseholdMember.class));
        }

        @Test
        @DisplayName("should throw ValidationException when household ID is null")
        void shouldThrowValidationExceptionWhenHouseholdIdIsNull() {
            // When/Then
            assertThatThrownBy(() -> householdMemberService.addMemberToHousehold(null, testMemberRequest))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Household ID cannot be null");

            verify(securityService, never()).getCurrentUserId();
            verify(householdMemberRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw ValidationException when request is null")
        void shouldThrowValidationExceptionWhenRequestIsNull() {
            // When/Then
            assertThatThrownBy(() -> householdMemberService.addMemberToHousehold(householdId, null))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("request cannot be null");

            verify(securityService, never()).getCurrentUserId();
            verify(householdMemberRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw ValidationException when user ID is null")
        void shouldThrowValidationExceptionWhenUserIdIsNull() {
            // Given
            HouseholdMemberRequest invalidRequest = HouseholdMemberRequest.builder()
                    .userId(null)
                    .role(HouseholdRole.MEMBER)
                    .build();

            // When/Then
            assertThatThrownBy(() -> householdMemberService.addMemberToHousehold(householdId, invalidRequest))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("User ID cannot be null");

            verify(securityService, never()).getCurrentUserId();
            verify(householdMemberRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw ValidationException when role is null")
        void shouldThrowValidationExceptionWhenRoleIsNull() {
            // Given
            HouseholdMemberRequest invalidRequest = HouseholdMemberRequest.builder()
                    .userId(thirdUserId)
                    .role(null)
                    .build();

            // When/Then
            assertThatThrownBy(() -> householdMemberService.addMemberToHousehold(householdId, invalidRequest))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Role cannot be null");

            verify(securityService, never()).getCurrentUserId();
            verify(householdMemberRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw ValidationException when attempting to set role to OWNER")
        void shouldThrowValidationExceptionWhenSettingOwnerRole() {
            // Given
            HouseholdMemberRequest invalidRequest = HouseholdMemberRequest.builder()
                    .userId(thirdUserId)
                    .role(HouseholdRole.OWNER)
                    .build();

            // When/Then
            assertThatThrownBy(() -> householdMemberService.addMemberToHousehold(householdId, invalidRequest))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Cannot set role to OWNER");

            verify(securityService, never()).getCurrentUserId();
            verify(householdMemberRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw NotFoundException when household not found")
        void shouldThrowNotFoundExceptionWhenHouseholdNotFound() {
            // Given
            stubCurrentUserId(testUserId);
            when(householdRepository.findById(eq(householdId))).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> householdMemberService.addMemberToHousehold(householdId, testMemberRequest))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("Household not found");

            verify(householdMemberRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw NotFoundException when user to add not found")
        void shouldThrowNotFoundExceptionWhenUserToAddNotFound() {
            // Given
            stubCurrentUserId(testUserId);
            when(householdRepository.findById(eq(householdId))).thenReturn(Optional.of(testHousehold));
            when(userRepository.findById(eq(thirdUserId))).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> householdMemberService.addMemberToHousehold(householdId, testMemberRequest))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("User not found");

            verify(householdMemberRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw ConflictException when user is already a member")
        void shouldThrowConflictExceptionWhenUserAlreadyMember() {
            // Given
            stubCurrentUserId(testUserId);
            when(householdRepository.findById(eq(householdId))).thenReturn(Optional.of(testHousehold));
            when(userRepository.findById(eq(thirdUserId))).thenReturn(Optional.of(thirdUser));
            when(householdMemberRepository.existsByHouseholdIdAndUserId(eq(householdId), eq(thirdUserId))).thenReturn(true);

            // When/Then
            assertThatThrownBy(() -> householdMemberService.addMemberToHousehold(householdId, testMemberRequest))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("already a member");

            verify(householdMemberRepository, never()).save(any());
        }
    }

    // ==================== Query Operations Tests ====================

    @Nested
    @DisplayName("getHouseholdMembers")
    class GetHouseholdMembersTests {

        @Test
        @DisplayName("should retrieve all members when user has access")
        void shouldGetHouseholdMembersSuccessfully() {
            // Given
            stubCurrentUserId(testUserId);
            when(householdMemberRepository.findByHouseholdId(eq(householdId)))
                    .thenReturn(List.of(ownerMember, adminMember, regularMember));

            // When
            List<HouseholdMemberResponse> responses = householdMemberService.getHouseholdMembers(householdId);

            // Then
            assertThat(responses).hasSize(3);
            assertThat(responses).extracting(HouseholdMemberResponse::getUserId)
                    .containsExactlyInAnyOrder(testUserId, otherUserId, thirdUserId);

            verify(securityService).getCurrentUserId();
            verify(householdMemberRepository).findByHouseholdId(eq(householdId));
        }

        @Test
        @DisplayName("should return empty list when household has no members")
        void shouldReturnEmptyListWhenNoMembers() {
            // Given
            stubCurrentUserId(testUserId);
            when(householdMemberRepository.findByHouseholdId(eq(householdId))).thenReturn(List.of());

            // When
            List<HouseholdMemberResponse> responses = householdMemberService.getHouseholdMembers(householdId);

            // Then
            assertThat(responses).isEmpty();

            verify(householdMemberRepository).findByHouseholdId(eq(householdId));
        }

        @Test
        @DisplayName("should throw ValidationException when household ID is null")
        void shouldThrowValidationExceptionWhenHouseholdIdIsNull() {
            // When/Then
            assertThatThrownBy(() -> householdMemberService.getHouseholdMembers(null))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Household ID cannot be null");

            verify(securityService, never()).getCurrentUserId();
            verify(householdMemberRepository, never()).findByHouseholdId(any());
        }
    }

    @Nested
    @DisplayName("getMemberById")
    class GetMemberByIdTests {

        @Test
        @DisplayName("should retrieve member successfully when user has access")
        void shouldGetMemberByIdSuccessfully() {
            // Given
            stubCurrentUserId(testUserId);
            when(householdMemberRepository.findById(eq(regularMemberId))).thenReturn(Optional.of(regularMember));

            // When
            HouseholdMemberResponse response = householdMemberService.getMemberById(householdId, regularMemberId);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo(regularMemberId);
            assertThat(response.getUserId()).isEqualTo(thirdUserId);
            assertThat(response.getRole()).isEqualTo(HouseholdRole.MEMBER);

            verify(securityService).getCurrentUserId();
            verify(householdMemberRepository).findById(eq(regularMemberId));
        }

        @Test
        @DisplayName("should throw ValidationException when household ID is null")
        void shouldThrowValidationExceptionWhenHouseholdIdIsNull() {
            // When/Then
            assertThatThrownBy(() -> householdMemberService.getMemberById(null, regularMemberId))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Household ID cannot be null");

            verify(securityService, never()).getCurrentUserId();
            verify(householdMemberRepository, never()).findById(any());
        }

        @Test
        @DisplayName("should throw ValidationException when member ID is null")
        void shouldThrowValidationExceptionWhenMemberIdIsNull() {
            // When/Then
            assertThatThrownBy(() -> householdMemberService.getMemberById(householdId, null))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Member ID cannot be null");

            verify(securityService, never()).getCurrentUserId();
            verify(householdMemberRepository, never()).findById(any());
        }

        @Test
        @DisplayName("should throw NotFoundException when member not found")
        void shouldThrowNotFoundExceptionWhenMemberNotFound() {
            // Given
            stubCurrentUserId(testUserId);
            when(householdMemberRepository.findById(eq(regularMemberId))).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> householdMemberService.getMemberById(householdId, regularMemberId))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("Member not found");
        }

        @Test
        @DisplayName("should throw NotFoundException when member is from different household")
        void shouldThrowNotFoundExceptionWhenMemberFromDifferentHousehold() {
            // Given
            UUID otherHouseholdId = UUID.randomUUID();
            Household otherHousehold = Household.builder()
                    .id(otherHouseholdId)
                    .name("Other Household")
                    .build();

            HouseholdMember memberFromOtherHousehold = HouseholdMember.builder()
                    .id(regularMemberId)
                    .user(thirdUser)
                    .household(otherHousehold)
                    .role(HouseholdRole.MEMBER)
                    .build();

            stubCurrentUserId(testUserId);
            when(householdMemberRepository.findById(eq(regularMemberId))).thenReturn(Optional.of(memberFromOtherHousehold));

            // When/Then
            assertThatThrownBy(() -> householdMemberService.getMemberById(householdId, regularMemberId))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("not from the specified household");
        }
    }

    // ==================== Delete Operations Tests ====================

    @Nested
    @DisplayName("removeMember")
    class RemoveMemberTests {

        @Test
        @DisplayName("should remove member successfully when user is owner")
        void shouldRemoveMemberSuccessfullyWhenUserIsOwner() {
            // Given
            stubCurrentUserId(testUserId);
            when(householdMemberRepository.findByHouseholdIdAndUserId(eq(householdId), eq(testUserId)))
                    .thenReturn(Optional.of(ownerMember));
            when(householdMemberRepository.findById(eq(regularMemberId))).thenReturn(Optional.of(regularMember));

            // When
            householdMemberService.removeMember(householdId, regularMemberId);

            // Then
            verify(householdMemberRepository).delete(eq(regularMember));
        }

        @Test
        @DisplayName("should remove member successfully when user is admin")
        void shouldRemoveMemberSuccessfullyWhenUserIsAdmin() {
            // Given
            stubCurrentUserId(otherUserId);
            when(householdMemberRepository.findByHouseholdIdAndUserId(eq(householdId), eq(otherUserId)))
                    .thenReturn(Optional.of(adminMember));
            when(householdMemberRepository.findById(eq(regularMemberId))).thenReturn(Optional.of(regularMember));

            // When
            householdMemberService.removeMember(householdId, regularMemberId);

            // Then
            verify(householdMemberRepository).delete(eq(regularMember));
        }

        @Test
        @DisplayName("should throw ValidationException when household ID is null")
        void shouldThrowValidationExceptionWhenHouseholdIdIsNull() {
            // When/Then
            assertThatThrownBy(() -> householdMemberService.removeMember(null, regularMemberId))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Household ID cannot be null");

            verify(securityService, never()).getCurrentUserId();
            verify(householdMemberRepository, never()).delete(any());
        }

        @Test
        @DisplayName("should throw ValidationException when member ID is null")
        void shouldThrowValidationExceptionWhenMemberIdIsNull() {
            // When/Then
            assertThatThrownBy(() -> householdMemberService.removeMember(householdId, null))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Member ID cannot be null");

            verify(securityService, never()).getCurrentUserId();
            verify(householdMemberRepository, never()).delete(any());
        }

        @Test
        @DisplayName("should throw NotFoundException when current user is not a member")
        void shouldThrowNotFoundExceptionWhenCurrentUserNotMember() {
            // Given
            stubCurrentUserId(testUserId);
            when(householdMemberRepository.findByHouseholdIdAndUserId(eq(householdId), eq(testUserId)))
                    .thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> householdMemberService.removeMember(householdId, regularMemberId))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("not a member");

            verify(householdMemberRepository, never()).delete(any());
        }

        @Test
        @DisplayName("should throw NotFoundException when member to remove not found")
        void shouldThrowNotFoundExceptionWhenMemberToRemoveNotFound() {
            // Given
            stubCurrentUserId(testUserId);
            when(householdMemberRepository.findByHouseholdIdAndUserId(eq(householdId), eq(testUserId)))
                    .thenReturn(Optional.of(ownerMember));
            when(householdMemberRepository.findById(eq(regularMemberId))).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> householdMemberService.removeMember(householdId, regularMemberId))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("Member not found");

            verify(householdMemberRepository, never()).delete(any());
        }

        @Test
        @DisplayName("should throw NotFoundException when member is from different household")
        void shouldThrowNotFoundExceptionWhenMemberFromDifferentHousehold() {
            // Given
            UUID otherHouseholdId = UUID.randomUUID();
            Household otherHousehold = Household.builder()
                    .id(otherHouseholdId)
                    .name("Other Household")
                    .build();

            HouseholdMember memberFromOtherHousehold = HouseholdMember.builder()
                    .id(regularMemberId)
                    .user(thirdUser)
                    .household(otherHousehold)
                    .role(HouseholdRole.MEMBER)
                    .build();

            stubCurrentUserId(testUserId);
            when(householdMemberRepository.findByHouseholdIdAndUserId(eq(householdId), eq(testUserId)))
                    .thenReturn(Optional.of(ownerMember));
            when(householdMemberRepository.findById(eq(regularMemberId))).thenReturn(Optional.of(memberFromOtherHousehold));

            // When/Then
            assertThatThrownBy(() -> householdMemberService.removeMember(householdId, regularMemberId))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("not from the specified household");

            verify(householdMemberRepository, never()).delete(any());
        }

        @Test
        @DisplayName("should throw ResourceStateException when attempting to remove owner")
        void shouldThrowResourceStateExceptionWhenRemovingOwner() {
            // Given
            stubCurrentUserId(testUserId);
            when(householdMemberRepository.findByHouseholdIdAndUserId(eq(householdId), eq(testUserId)))
                    .thenReturn(Optional.of(ownerMember));
            when(householdMemberRepository.findById(eq(memberId))).thenReturn(Optional.of(ownerMember));

            // When/Then
            assertThatThrownBy(() -> householdMemberService.removeMember(householdId, memberId))
                    .isInstanceOf(ResourceStateException.class)
                    .hasMessageContaining("Cannot remove the owner");

            verify(householdMemberRepository, never()).delete(any());
        }

        @Test
        @DisplayName("should throw InsufficientPermissionException when admin tries to remove another admin")
        void shouldThrowInsufficientPermissionExceptionWhenAdminTriesToRemoveAdmin() {
            // Given
            UUID anotherAdminId = UUID.randomUUID();
            HouseholdMember anotherAdmin = HouseholdMember.builder()
                    .id(anotherAdminId)
                    .user(User.builder().id(UUID.randomUUID()).build())
                    .household(testHousehold)
                    .role(HouseholdRole.ADMIN)
                    .build();

            stubCurrentUserId(otherUserId);
            when(householdMemberRepository.findByHouseholdIdAndUserId(eq(householdId), eq(otherUserId)))
                    .thenReturn(Optional.of(adminMember));
            when(householdMemberRepository.findById(eq(anotherAdminId))).thenReturn(Optional.of(anotherAdmin));

            // When/Then
            assertThatThrownBy(() -> householdMemberService.removeMember(householdId, anotherAdminId))
                    .isInstanceOf(InsufficientPermissionException.class)
                    .hasMessageContaining("Admin cannot remove another admin");

            verify(householdMemberRepository, never()).delete(any());
        }
    }

    // ==================== Update Operations Tests ====================

    @Nested
    @DisplayName("updateMemberRole")
    class UpdateMemberRoleTests {

        @Test
        @DisplayName("should update member role successfully when user is owner")
        void shouldUpdateMemberRoleSuccessfullyWhenUserIsOwner() {
            // Given
            stubCurrentUserId(testUserId);
            when(householdMemberRepository.findByHouseholdIdAndUserId(eq(householdId), eq(testUserId)))
                    .thenReturn(Optional.of(ownerMember));
            when(householdMemberRepository.findById(eq(regularMemberId))).thenReturn(Optional.of(regularMember));
            when(householdMemberRepository.save(any(HouseholdMember.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // When
            HouseholdMemberResponse response = householdMemberService.updateMemberRole(
                    householdId, regularMemberId, HouseholdRole.ADMIN);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getRole()).isEqualTo(HouseholdRole.ADMIN);

            verify(householdMemberRepository).save(any(HouseholdMember.class));
        }

        @Test
        @DisplayName("should update member role successfully when user is admin")
        void shouldUpdateMemberRoleSuccessfullyWhenUserIsAdmin() {
            // Given
            stubCurrentUserId(otherUserId);
            when(householdMemberRepository.findByHouseholdIdAndUserId(eq(householdId), eq(otherUserId)))
                    .thenReturn(Optional.of(adminMember));
            when(householdMemberRepository.findById(eq(regularMemberId))).thenReturn(Optional.of(regularMember));
            when(householdMemberRepository.save(any(HouseholdMember.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // When
            HouseholdMemberResponse response = householdMemberService.updateMemberRole(
                    householdId, regularMemberId, HouseholdRole.ADMIN);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getRole()).isEqualTo(HouseholdRole.ADMIN);

            verify(householdMemberRepository).save(any(HouseholdMember.class));
        }

        @Test
        @DisplayName("should throw ValidationException when household ID is null")
        void shouldThrowValidationExceptionWhenHouseholdIdIsNull() {
            // When/Then
            assertThatThrownBy(() -> householdMemberService.updateMemberRole(
                    null, regularMemberId, HouseholdRole.ADMIN))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Household ID cannot be null");

            verify(securityService, never()).getCurrentUserId();
            verify(householdMemberRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw ValidationException when member ID is null")
        void shouldThrowValidationExceptionWhenMemberIdIsNull() {
            // When/Then
            assertThatThrownBy(() -> householdMemberService.updateMemberRole(
                    householdId, null, HouseholdRole.ADMIN))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Member ID cannot be null");

            verify(securityService, never()).getCurrentUserId();
            verify(householdMemberRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw ValidationException when role is null")
        void shouldThrowValidationExceptionWhenRoleIsNull() {
            // When/Then
            assertThatThrownBy(() -> householdMemberService.updateMemberRole(
                    householdId, regularMemberId, null))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Role cannot be null");

            verify(securityService, never()).getCurrentUserId();
            verify(householdMemberRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw ValidationException when attempting to set role to OWNER")
        void shouldThrowValidationExceptionWhenSettingOwnerRole() {
            // When/Then
            assertThatThrownBy(() -> householdMemberService.updateMemberRole(
                    householdId, regularMemberId, HouseholdRole.OWNER))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Cannot set role to OWNER");

            verify(securityService, never()).getCurrentUserId();
            verify(householdMemberRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw NotFoundException when current user is not a member")
        void shouldThrowNotFoundExceptionWhenCurrentUserNotMember() {
            // Given
            stubCurrentUserId(testUserId);
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, testUserId))
                    .thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> householdMemberService.updateMemberRole(
                    householdId, regularMemberId, HouseholdRole.ADMIN))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("not a member");

            verify(householdMemberRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw NotFoundException when member to update not found")
        void shouldThrowNotFoundExceptionWhenMemberToUpdateNotFound() {
            // Given
            stubCurrentUserId(testUserId);
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, testUserId))
                    .thenReturn(Optional.of(ownerMember));
            when(householdMemberRepository.findById(regularMemberId)).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> householdMemberService.updateMemberRole(
                    householdId, regularMemberId, HouseholdRole.ADMIN))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("Member not found");

            verify(householdMemberRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw NotFoundException when member is from different household")
        void shouldThrowNotFoundExceptionWhenMemberFromDifferentHousehold() {
            // Given
            UUID otherHouseholdId = UUID.randomUUID();
            Household otherHousehold = Household.builder()
                    .id(otherHouseholdId)
                    .name("Other Household")
                    .build();

            HouseholdMember memberFromOtherHousehold = HouseholdMember.builder()
                    .id(regularMemberId)
                    .user(thirdUser)
                    .household(otherHousehold)
                    .role(HouseholdRole.MEMBER)
                    .build();

            stubCurrentUserId(testUserId);
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, testUserId))
                    .thenReturn(Optional.of(ownerMember));
            when(householdMemberRepository.findById(regularMemberId)).thenReturn(Optional.of(memberFromOtherHousehold));

            // When/Then
            assertThatThrownBy(() -> householdMemberService.updateMemberRole(
                    householdId, regularMemberId, HouseholdRole.ADMIN))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("not from the specified household");

            verify(householdMemberRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw InsufficientPermissionException when admin tries to update another admin's role")
        void shouldThrowInsufficientPermissionExceptionWhenAdminTriesToUpdateAdminRole() {
            // Given
            UUID anotherAdminId = UUID.randomUUID();
            HouseholdMember anotherAdmin = HouseholdMember.builder()
                    .id(anotherAdminId)
                    .user(User.builder().id(UUID.randomUUID()).build())
                    .household(testHousehold)
                    .role(HouseholdRole.ADMIN)
                    .build();

            stubCurrentUserId(otherUserId);
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, otherUserId))
                    .thenReturn(Optional.of(adminMember));
            when(householdMemberRepository.findById(anotherAdminId)).thenReturn(Optional.of(anotherAdmin));

            // When/Then
            assertThatThrownBy(() -> householdMemberService.updateMemberRole(
                    householdId, anotherAdminId, HouseholdRole.MEMBER))
                    .isInstanceOf(InsufficientPermissionException.class)
                    .hasMessageContaining("Admin cannot update another admin's role");

            verify(householdMemberRepository, never()).save(any());
        }
    }
}