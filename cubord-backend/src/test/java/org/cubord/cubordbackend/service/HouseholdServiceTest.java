package org.cubord.cubordbackend.service;

import org.cubord.cubordbackend.domain.*;
import org.cubord.cubordbackend.dto.household.HouseholdRequest;
import org.cubord.cubordbackend.dto.household.HouseholdResponse;
import org.cubord.cubordbackend.exception.*;
import org.cubord.cubordbackend.repository.HouseholdMemberRepository;
import org.cubord.cubordbackend.repository.HouseholdRepository;
import org.cubord.cubordbackend.security.SecurityService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive test suite for HouseholdService following the modernized security architecture.
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
@DisplayName("HouseholdService Tests")
class HouseholdServiceTest {

    @Mock
    private HouseholdRepository householdRepository;

    @Mock
    private HouseholdMemberRepository householdMemberRepository;

    @Mock
    private SecurityService securityService;

    @InjectMocks
    private HouseholdService householdService;

    // Test data
    private UUID testUserId;
    private UUID otherUserId;
    private UUID householdId;
    private User testUser;
    private User otherUser;
    private Household testHousehold;
    private HouseholdMember ownerMember;
    private HouseholdMember adminMember;
    private HouseholdMember regularMember;
    private HouseholdRequest testHouseholdRequest;

    @BeforeEach
    void setUp() {
        testUserId = UUID.randomUUID();
        otherUserId = UUID.randomUUID();
        householdId = UUID.randomUUID();

        testUser = User.builder()
                .id(testUserId)
                .username("testuser")
                .email("test@example.com")
                .displayName("Test User")
                .role(UserRole.USER)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        otherUser = User.builder()
                .id(otherUserId)
                .username("otheruser")
                .email("other@example.com")
                .displayName("Other User")
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
                .household(testHousehold)
                .user(testUser)
                .role(HouseholdRole.OWNER)
                .build();

        adminMember = HouseholdMember.builder()
                .id(UUID.randomUUID())
                .household(testHousehold)
                .user(testUser)
                .role(HouseholdRole.ADMIN)
                .build();

        regularMember = HouseholdMember.builder()
                .id(UUID.randomUUID())
                .household(testHousehold)
                .user(testUser)
                .role(HouseholdRole.MEMBER)
                .build();

        testHouseholdRequest = HouseholdRequest.builder()
                .name("Test Household")
                .build();
    }

    // ==================== Test Utilities ====================

    /**
     * Use this when the code under test calls securityService.getCurrentUserId().
     * (Most "member/admin/owner" authorization flows.)
     */
    private void stubCurrentUserId(UUID userId) {
        when(securityService.getCurrentUserId()).thenReturn(userId);
    }

    /**
     * Use this when the code under test calls securityService.getCurrentUser().
     * (Common for create flows where user entity is needed.)
     */
    private void stubCurrentUser(User user) {
        when(securityService.getCurrentUser()).thenReturn(user);
    }

    // ==================== Create Operations Tests ====================

    @Nested
    @DisplayName("createHousehold")
    class CreateHouseholdTests {

        @Test
        @DisplayName("should create household successfully when authenticated")
        void shouldCreateHouseholdSuccessfully() {
            // Given
            stubCurrentUser(testUser);

            when(householdRepository.existsByNameAndMembersUserId(eq(testHouseholdRequest.getName()), eq(testUserId)))
                    .thenReturn(false);
            when(householdRepository.save(any(Household.class))).thenReturn(testHousehold);
            when(householdMemberRepository.save(any(HouseholdMember.class))).thenReturn(ownerMember);

            // When
            HouseholdResponse response = householdService.createHousehold(testHouseholdRequest);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo(householdId);
            assertThat(response.getName()).isEqualTo(testHousehold.getName());

            verify(securityService).getCurrentUser();
            verify(householdRepository).existsByNameAndMembersUserId(eq(testHouseholdRequest.getName()), eq(testUserId));
            verify(householdRepository).save(any(Household.class));
            verify(householdMemberRepository).save(argThat(member ->
                    member.getRole() == HouseholdRole.OWNER &&
                            member.getUser().getId().equals(testUserId)
            ));
        }

        @Test
        @DisplayName("should throw ValidationException when request is null")
        void shouldThrowValidationExceptionWhenRequestIsNull() {
            // When/Then
            assertThatThrownBy(() -> householdService.createHousehold(null))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Household request cannot be null");

            verify(securityService, never()).getCurrentUser();
            verify(householdRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw ValidationException when household name is null")
        void shouldThrowValidationExceptionWhenNameIsNull() {
            // Given
            HouseholdRequest invalidRequest = HouseholdRequest.builder()
                    .name(null)
                    .build();

            // When/Then
            assertThatThrownBy(() -> householdService.createHousehold(invalidRequest))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Household name cannot be null or empty");

            verify(securityService, never()).getCurrentUser();
            verify(householdRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw ValidationException when household name is blank")
        void shouldThrowValidationExceptionWhenNameIsBlank() {
            // Given
            HouseholdRequest invalidRequest = HouseholdRequest.builder()
                    .name("   ")
                    .build();

            // When/Then
            assertThatThrownBy(() -> householdService.createHousehold(invalidRequest))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Household name cannot be null or empty");

            verify(securityService, never()).getCurrentUser();
            verify(householdRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw ConflictException when household name already exists for user")
        void shouldThrowConflictExceptionWhenNameExists() {
            // Given
            stubCurrentUser(testUser);

            when(householdRepository.existsByNameAndMembersUserId(eq(testHouseholdRequest.getName()), eq(testUserId)))
                    .thenReturn(true);

            // When/Then
            assertThatThrownBy(() -> householdService.createHousehold(testHouseholdRequest))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("Household with name")
                    .hasMessageContaining("already exists");

            verify(securityService).getCurrentUser();
            verify(householdRepository).existsByNameAndMembersUserId(eq(testHouseholdRequest.getName()), eq(testUserId));
            verify(householdRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw DataIntegrityException when household save fails")
        void shouldThrowDataIntegrityExceptionWhenSaveFails() {
            // Given
            stubCurrentUser(testUser);

            when(householdRepository.existsByNameAndMembersUserId(eq(testHouseholdRequest.getName()), eq(testUserId)))
                    .thenReturn(false);
            when(householdRepository.save(any(Household.class)))
                    .thenThrow(new RuntimeException("Database error"));

            // When/Then
            assertThatThrownBy(() -> householdService.createHousehold(testHouseholdRequest))
                    .isInstanceOf(DataIntegrityException.class)
                    .hasMessageContaining("Failed to create household");

            verify(securityService).getCurrentUser();
            verify(householdRepository).save(any(Household.class));
        }
    }

    // ==================== Query Operations Tests ====================

    @Nested
    @DisplayName("getHouseholdById")
    class GetHouseholdByIdTests {

        @Test
        @DisplayName("should retrieve household successfully when user is member")
        void shouldGetHouseholdByIdSuccessfully() {
            // Given
            stubCurrentUserId(testUserId);

            when(householdRepository.findById(eq(householdId))).thenReturn(Optional.of(testHousehold));

            // When
            HouseholdResponse response = householdService.getHouseholdById(householdId);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo(householdId);
            assertThat(response.getName()).isEqualTo(testHousehold.getName());

            verify(securityService).getCurrentUserId();
            verify(householdRepository).findById(eq(householdId));
        }

        @Test
        @DisplayName("should throw ValidationException when household ID is null")
        void shouldThrowValidationExceptionWhenIdIsNull() {
            // When/Then
            assertThatThrownBy(() -> householdService.getHouseholdById(null))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Household ID cannot be null");

            verify(securityService, never()).getCurrentUserId();
            verify(householdRepository, never()).findById(any());
        }

        @Test
        @DisplayName("should throw NotFoundException when household doesn't exist")
        void shouldThrowNotFoundExceptionWhenHouseholdDoesntExist() {
            // Given
            stubCurrentUserId(testUserId);

            when(householdRepository.findById(eq(householdId))).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> householdService.getHouseholdById(householdId))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("Household not found");

            verify(securityService).getCurrentUserId();
            verify(householdRepository).findById(eq(householdId));
        }
    }

    @Nested
    @DisplayName("getUserHouseholds")
    class GetUserHouseholdsTests {

        @Test
        @DisplayName("should retrieve all user households")
        void shouldGetUserHouseholdsSuccessfully() {
            // Given
            stubCurrentUserId(testUserId);

            List<Household> households = List.of(testHousehold);
            when(householdRepository.findAllByMembersUserId(eq(testUserId))).thenReturn(households);

            // When
            List<HouseholdResponse> responses = householdService.getUserHouseholds();

            // Then
            assertThat(responses).isNotNull();
            assertThat(responses).hasSize(1);
            assertThat(responses.getFirst().getId()).isEqualTo(householdId);

            verify(securityService).getCurrentUserId();
            verify(householdRepository).findAllByMembersUserId(eq(testUserId));
        }

        @Test
        @DisplayName("should return empty list when user has no households")
        void shouldReturnEmptyListWhenNoHouseholds() {
            // Given
            stubCurrentUserId(testUserId);

            when(householdRepository.findAllByMembersUserId(eq(testUserId))).thenReturn(Collections.emptyList());

            // When
            List<HouseholdResponse> responses = householdService.getUserHouseholds();

            // Then
            assertThat(responses).isNotNull();
            assertThat(responses).isEmpty();

            verify(securityService).getCurrentUserId();
            verify(householdRepository).findAllByMembersUserId(eq(testUserId));
        }
    }

    @Nested
    @DisplayName("searchHouseholds")
    class SearchHouseholdsTests {

        @Test
        @DisplayName("should search households successfully")
        void shouldSearchHouseholdsSuccessfully() {
            // Given
            String searchTerm = "test";
            stubCurrentUserId(testUserId);

            List<Household> households = List.of(testHousehold);
            when(householdRepository.findAllByNameContainingIgnoreCaseAndMembersUserId(eq(searchTerm), eq(testUserId)))
                    .thenReturn(households);

            // When
            List<HouseholdResponse> responses = householdService.searchHouseholds(searchTerm);

            // Then
            assertThat(responses).isNotNull();
            assertThat(responses).hasSize(1);
            assertThat(responses.getFirst().getName()).contains("Test");

            verify(securityService).getCurrentUserId();
            verify(householdRepository).findAllByNameContainingIgnoreCaseAndMembersUserId(eq(searchTerm), eq(testUserId));
        }

        @Test
        @DisplayName("should throw ValidationException when search term is null")
        void shouldThrowValidationExceptionWhenSearchTermIsNull() {
            // When/Then
            assertThatThrownBy(() -> householdService.searchHouseholds(null))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Search term cannot be null or empty");

            verify(securityService, never()).getCurrentUserId();
            verify(householdRepository, never()).findAllByNameContainingIgnoreCaseAndMembersUserId(any(), any());
        }

        @Test
        @DisplayName("should throw ValidationException when search term is blank")
        void shouldThrowValidationExceptionWhenSearchTermIsBlank() {
            // When/Then
            assertThatThrownBy(() -> householdService.searchHouseholds("   "))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Search term cannot be null or empty");

            verify(securityService, never()).getCurrentUserId();
            verify(householdRepository, never()).findAllByNameContainingIgnoreCaseAndMembersUserId(any(), any());
        }
    }

    // ==================== Update Operations Tests ====================

    @Nested
    @DisplayName("updateHousehold")
    class UpdateHouseholdTests {

        @Test
        @DisplayName("should update household successfully when user is admin")
        void shouldUpdateHouseholdSuccessfully() {
            // Given
            HouseholdRequest updateRequest = HouseholdRequest.builder()
                    .name("Updated Household")
                    .build();

            stubCurrentUserId(testUserId);

            when(householdRepository.findById(eq(householdId))).thenReturn(Optional.of(testHousehold));
            when(householdRepository.existsByNameAndMembersUserId(eq(updateRequest.getName()), eq(testUserId)))
                    .thenReturn(false);
            when(householdRepository.save(any(Household.class))).thenReturn(testHousehold);

            // When
            HouseholdResponse response = householdService.updateHousehold(householdId, updateRequest);

            // Then
            assertThat(response).isNotNull();
            verify(securityService).getCurrentUserId();
            verify(householdRepository).findById(eq(householdId));
            verify(householdRepository).save(any(Household.class));
        }

        @Test
        @DisplayName("should throw ValidationException when household ID is null")
        void shouldThrowValidationExceptionWhenIdIsNull() {
            // Given
            HouseholdRequest updateRequest = HouseholdRequest.builder().name("Updated").build();

            // When/Then
            assertThatThrownBy(() -> householdService.updateHousehold(null, updateRequest))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Household ID cannot be null");

            verify(householdRepository, never()).findById(any());
        }

        @Test
        @DisplayName("should throw ValidationException when request is null")
        void shouldThrowValidationExceptionWhenRequestIsNull() {
            // When/Then
            assertThatThrownBy(() -> householdService.updateHousehold(householdId, null))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Update request cannot be null");

            verify(householdRepository, never()).findById(any());
        }

        @Test
        @DisplayName("should throw NotFoundException when household doesn't exist")
        void shouldThrowNotFoundExceptionWhenHouseholdDoesntExist() {
            // Given
            HouseholdRequest updateRequest = HouseholdRequest.builder().name("Updated").build();
            stubCurrentUserId(testUserId);

            when(householdRepository.findById(eq(householdId))).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> householdService.updateHousehold(householdId, updateRequest))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("Household not found");

            verify(householdRepository).findById(eq(householdId));
        }

        @Test
        @DisplayName("should throw ConflictException when new name already exists")
        void shouldThrowConflictExceptionWhenNameExists() {
            // Given
            HouseholdRequest updateRequest = HouseholdRequest.builder()
                    .name("Existing Household")
                    .build();

            stubCurrentUserId(testUserId);

            when(householdRepository.findById(eq(householdId))).thenReturn(Optional.of(testHousehold));
            when(householdRepository.existsByNameAndMembersUserId(eq(updateRequest.getName()), eq(testUserId)))
                    .thenReturn(true);

            // When/Then
            assertThatThrownBy(() -> householdService.updateHousehold(householdId, updateRequest))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("already exists");

            verify(householdRepository).findById(eq(householdId));
            verify(householdRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("patchHousehold")
    class PatchHouseholdTests {

        @Test
        @DisplayName("should patch household name successfully")
        void shouldPatchHouseholdSuccessfully() {
            // Given
            stubCurrentUserId(testUserId);

            Map<String, Object> patchData = Map.of("name", "Patched Household");
            when(householdRepository.findById(eq(householdId))).thenReturn(Optional.of(testHousehold));
            when(householdRepository.existsByNameAndMembersUserId(eq("Patched Household"), eq(testUserId)))
                    .thenReturn(false);

            ArgumentCaptor<Household> householdCaptor = ArgumentCaptor.forClass(Household.class);
            when(householdRepository.save(householdCaptor.capture()))
                    .thenAnswer(invocation -> invocation.getArgument(0, Household.class));

            // When
            HouseholdResponse response = householdService.patchHousehold(householdId, patchData);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo(householdId);
            assertThat(response.getName()).isEqualTo("Patched Household");

            Household saved = householdCaptor.getValue();
            assertThat(saved.getName()).isEqualTo("Patched Household");

            verify(securityService).getCurrentUserId();
            verify(householdRepository).findById(eq(householdId));
            verify(householdRepository).existsByNameAndMembersUserId("Patched Household", testUserId);
            verify(householdRepository).save(any(Household.class));
        }

        @Test
        @DisplayName("should throw ValidationException when patch data is null")
        void shouldThrowValidationExceptionWhenPatchDataIsNull() {
            // When/Then
            assertThatThrownBy(() -> householdService.patchHousehold(householdId, null))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Patch data cannot be null or empty");

            verify(householdRepository, never()).findById(any());
        }

        @Test
        @DisplayName("should throw ValidationException when patch data is empty")
        void shouldThrowValidationExceptionWhenPatchDataIsEmpty() {
            // When/Then
            assertThatThrownBy(() -> householdService.patchHousehold(householdId, Collections.emptyMap()))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Patch data cannot be null or empty");

            verify(householdRepository, never()).findById(any());
        }

        @Test
        @DisplayName("should throw ValidationException when patching unsupported field")
        void shouldThrowValidationExceptionWhenUnsupportedField() {
            // Given
            stubCurrentUserId(testUserId);

            Map<String, Object> patchData = Map.of("unsupportedField", "value");
            when(householdRepository.findById(eq(householdId))).thenReturn(Optional.of(testHousehold));

            // When/Then
            assertThatThrownBy(() -> householdService.patchHousehold(householdId, patchData))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Unsupported field for patching");

            verify(householdRepository).findById(eq(householdId));
            verify(householdRepository, never()).save(any());
        }
    }

    // ==================== Delete Operations Tests ====================

    @Nested
    @DisplayName("deleteHousehold")
    class DeleteHouseholdTests {

        @Test
        @DisplayName("should delete household successfully when user is owner")
        void shouldDeleteHouseholdSuccessfully() {
            // Given
            stubCurrentUserId(testUserId);

            when(householdRepository.findById(eq(householdId))).thenReturn(Optional.of(testHousehold));
            doNothing().when(householdRepository).delete(any(Household.class));

            // When
            householdService.deleteHousehold(householdId);

            // Then
            verify(securityService).getCurrentUserId();
            verify(householdRepository).findById(eq(householdId));
            verify(householdRepository).delete(eq(testHousehold));
        }

        @Test
        @DisplayName("should throw ValidationException when household ID is null")
        void shouldThrowValidationExceptionWhenIdIsNull() {
            // When/Then
            assertThatThrownBy(() -> householdService.deleteHousehold(null))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Household ID cannot be null");

            verify(householdRepository, never()).findById(any());
            verify(householdRepository, never()).delete(any());
        }

        @Test
        @DisplayName("should throw NotFoundException when household doesn't exist")
        void shouldThrowNotFoundExceptionWhenHouseholdDoesntExist() {
            // Given
            stubCurrentUserId(testUserId);

            when(householdRepository.findById(eq(householdId))).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> householdService.deleteHousehold(householdId))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("Household not found");

            verify(householdRepository).findById(eq(householdId));
            verify(householdRepository, never()).delete(any());
        }
    }

    // ==================== Member Operations Tests ====================

    @Nested
    @DisplayName("leaveHousehold")
    class LeaveHouseholdTests {

        @Test
        @DisplayName("should allow regular member to leave household")
        void shouldLeaveHouseholdSuccessfully() {
            // Given
            stubCurrentUserId(testUserId);

            when(householdMemberRepository.findByHouseholdIdAndUserId(eq(householdId), eq(testUserId)))
                    .thenReturn(Optional.of(regularMember));
            doNothing().when(householdMemberRepository).delete(any(HouseholdMember.class));

            // When
            householdService.leaveHousehold(householdId);

            // Then
            verify(securityService).getCurrentUserId();
            verify(householdMemberRepository).findByHouseholdIdAndUserId(eq(householdId), eq(testUserId));
            verify(householdMemberRepository).delete(eq(regularMember));
        }

        @Test
        @DisplayName("should throw ResourceStateException when owner tries to leave")
        void shouldThrowResourceStateExceptionWhenOwnerLeaves() {
            // Given
            stubCurrentUserId(testUserId);

            when(householdMemberRepository.findByHouseholdIdAndUserId(eq(householdId), eq(testUserId)))
                    .thenReturn(Optional.of(ownerMember));

            // When/Then
            assertThatThrownBy(() -> householdService.leaveHousehold(householdId))
                    .isInstanceOf(ResourceStateException.class)
                    .hasMessageContaining("Owner cannot leave the household");

            verify(householdMemberRepository).findByHouseholdIdAndUserId(eq(householdId), eq(testUserId));
            verify(householdMemberRepository, never()).delete(any());
        }

        @Test
        @DisplayName("should throw NotFoundException when user is not a member")
        void shouldThrowNotFoundExceptionWhenNotMember() {
            // Given
            stubCurrentUserId(testUserId);

            when(householdMemberRepository.findByHouseholdIdAndUserId(eq(householdId), eq(testUserId)))
                    .thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> householdService.leaveHousehold(householdId))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("not a member");

            verify(householdMemberRepository).findByHouseholdIdAndUserId(eq(householdId), eq(testUserId));
            verify(householdMemberRepository, never()).delete(any());
        }
    }

    @Nested
    @DisplayName("transferOwnership")
    class TransferOwnershipTests {

        @Test
        @DisplayName("should transfer ownership successfully")
        void shouldTransferOwnershipSuccessfully() {
            // Given
            stubCurrentUserId(testUserId);

            HouseholdMember newOwnerMember = HouseholdMember.builder()
                    .id(UUID.randomUUID())
                    .household(testHousehold)
                    .user(otherUser)
                    .role(HouseholdRole.MEMBER)
                    .build();

            when(householdMemberRepository.findByHouseholdIdAndUserId(eq(householdId), eq(testUserId)))
                    .thenReturn(Optional.of(ownerMember));
            when(householdMemberRepository.findByHouseholdIdAndUserId(eq(householdId), eq(otherUserId)))
                    .thenReturn(Optional.of(newOwnerMember));
            when(householdMemberRepository.save(any(HouseholdMember.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // When
            householdService.transferOwnership(householdId, otherUserId);

            // Then
            verify(securityService).getCurrentUserId();
            verify(householdMemberRepository).findByHouseholdIdAndUserId(eq(householdId), eq(testUserId));
            verify(householdMemberRepository).findByHouseholdIdAndUserId(eq(householdId), eq(otherUserId));
            verify(householdMemberRepository, times(2)).save(any(HouseholdMember.class));
        }

        @Test
        @DisplayName("should throw ValidationException when new owner ID is null")
        void shouldThrowValidationExceptionWhenNewOwnerIdIsNull() {
            // When/Then
            assertThatThrownBy(() -> householdService.transferOwnership(householdId, null))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("New owner ID cannot be null");

            verify(householdMemberRepository, never()).findByHouseholdIdAndUserId(any(), any());
        }

        @Test
        @DisplayName("should throw NotFoundException when new owner is not a member")
        void shouldThrowNotFoundExceptionWhenNewOwnerNotMember() {
            // Given
            stubCurrentUserId(testUserId);

            when(householdMemberRepository.findByHouseholdIdAndUserId(eq(householdId), eq(testUserId)))
                    .thenReturn(Optional.of(ownerMember));
            when(householdMemberRepository.findByHouseholdIdAndUserId(eq(householdId), eq(otherUserId)))
                    .thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> householdService.transferOwnership(householdId, otherUserId))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("not a member");

            verify(householdMemberRepository).findByHouseholdIdAndUserId(eq(householdId), eq(otherUserId));
            verify(householdMemberRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("changeMemberRole")
    class ChangeMemberRoleTests {

        @Test
        @DisplayName("should change member role successfully when user is owner")
        void shouldChangeMemberRoleSuccessfully() {
            // Given
            stubCurrentUserId(testUserId);

            HouseholdMember targetMember = HouseholdMember.builder()
                    .id(UUID.randomUUID())
                    .household(testHousehold)
                    .user(otherUser)
                    .role(HouseholdRole.MEMBER)
                    .build();

            when(householdMemberRepository.findByHouseholdIdAndUserId(eq(householdId), eq(testUserId)))
                    .thenReturn(Optional.of(ownerMember));
            when(householdMemberRepository.findByHouseholdIdAndUserId(eq(householdId), eq(otherUserId)))
                    .thenReturn(Optional.of(targetMember));
            when(householdMemberRepository.save(any(HouseholdMember.class))).thenReturn(targetMember);

            // When
            householdService.changeMemberRole(householdId, otherUserId, HouseholdRole.ADMIN);

            // Then
            verify(securityService).getCurrentUserId();
            verify(householdMemberRepository).findByHouseholdIdAndUserId(eq(householdId), eq(testUserId));
            verify(householdMemberRepository).findByHouseholdIdAndUserId(eq(householdId), eq(otherUserId));
            verify(householdMemberRepository).save(argThat(member ->
                    member.getRole() == HouseholdRole.ADMIN
            ));
        }

        @Test
        @DisplayName("should throw ValidationException when trying to set role to OWNER")
        void shouldThrowValidationExceptionWhenSettingOwnerRole() {
            // When/Then
            assertThatThrownBy(() -> householdService.changeMemberRole(householdId, otherUserId, HouseholdRole.OWNER))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Cannot set role to OWNER");

            verify(householdMemberRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw ValidationException when new role is null")
        void shouldThrowValidationExceptionWhenRoleIsNull() {
            // When/Then
            assertThatThrownBy(() -> householdService.changeMemberRole(householdId, otherUserId, null))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("New role cannot be null");

            verify(householdMemberRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw InsufficientPermissionException when admin tries to modify admin")
        void shouldThrowInsufficientPermissionExceptionWhenAdminModifiesAdmin() {
            // Given
            stubCurrentUserId(testUserId);

            HouseholdMember targetAdminMember = HouseholdMember.builder()
                    .id(UUID.randomUUID())
                    .household(testHousehold)
                    .user(otherUser)
                    .role(HouseholdRole.ADMIN)
                    .build();

            when(householdMemberRepository.findByHouseholdIdAndUserId(eq(householdId), eq(testUserId)))
                    .thenReturn(Optional.of(adminMember));
            when(householdMemberRepository.findByHouseholdIdAndUserId(eq(householdId), eq(otherUserId)))
                    .thenReturn(Optional.of(targetAdminMember));

            // When/Then
            assertThatThrownBy(() -> householdService.changeMemberRole(householdId, otherUserId, HouseholdRole.MEMBER))
                    .isInstanceOf(InsufficientPermissionException.class)
                    .hasMessageContaining("Admin cannot modify other admin");

            verify(householdMemberRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw ValidationException when trying to change owner's role")
        void shouldThrowValidationExceptionWhenChangingOwnerRole() {
            // Arrange
            stubCurrentUserId(testUserId);

            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, testUserId))
                    .thenReturn(Optional.of(ownerMember));
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, otherUserId))
                    .thenReturn(Optional.of(ownerMember));

            // Act & Assert
            assertThatThrownBy(() -> householdService.changeMemberRole(householdId, otherUserId, HouseholdRole.ADMIN))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Cannot change owner's role");
        }
    }
}
