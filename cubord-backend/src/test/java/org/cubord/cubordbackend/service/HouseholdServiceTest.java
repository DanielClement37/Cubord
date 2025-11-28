package org.cubord.cubordbackend.service;

import org.cubord.cubordbackend.domain.*;
import org.cubord.cubordbackend.dto.household.HouseholdRequest;
import org.cubord.cubordbackend.dto.household.HouseholdResponse;
import org.cubord.cubordbackend.exception.ConflictException;
import org.cubord.cubordbackend.exception.InsufficientPermissionException;
import org.cubord.cubordbackend.exception.NotFoundException;
import org.cubord.cubordbackend.exception.ResourceStateException;
import org.cubord.cubordbackend.exception.ValidationException;
import org.cubord.cubordbackend.repository.HouseholdMemberRepository;
import org.cubord.cubordbackend.repository.HouseholdRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Household Service Tests")
class HouseholdServiceTest {

    @Mock private HouseholdRepository householdRepository;
    @Mock private HouseholdMemberRepository householdMemberRepository;
    @Mock private UserService userService;
    @Mock private JwtAuthenticationToken validToken;
    @Mock private JwtAuthenticationToken invalidToken;

    @InjectMocks
    private HouseholdService householdService;

    // Test data
    private User testUser;
    private User secondUser;
    private User adminUser;
    private Household testHousehold;
    private Household secondHousehold;
    private HouseholdMember ownerMember;
    private HouseholdMember adminMember;
    private HouseholdMember regularMember;
    private HouseholdRequest testHouseholdRequest;
    private UUID userId;
    private UUID secondUserId;
    private UUID adminUserId;
    private UUID householdId;
    private UUID secondHouseholdId;

    @BeforeEach
    void setUp() {
        reset(householdRepository, householdMemberRepository, userService, validToken, invalidToken);

        userId = UUID.randomUUID();
        secondUserId = UUID.randomUUID();
        adminUserId = UUID.randomUUID();
        householdId = UUID.randomUUID();
        secondHouseholdId = UUID.randomUUID();

        testUser = User.builder()
                .id(userId)
                .username("testuser")
                .email("test@example.com")
                .displayName("Test User")
                .role(UserRole.USER)
                .createdAt(LocalDateTime.now())
                .build();

        secondUser = User.builder()
                .id(secondUserId)
                .username("seconduser")
                .email("second@example.com")
                .displayName("Second User")
                .role(UserRole.USER)
                .createdAt(LocalDateTime.now())
                .build();

        adminUser = User.builder()
                .id(adminUserId)
                .username("adminuser")
                .email("admin@example.com")
                .displayName("Admin User")
                .role(UserRole.USER)
                .createdAt(LocalDateTime.now())
                .build();

        testHousehold = Household.builder()
                .id(householdId)
                .name("Test Household")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .members(new HashSet<>())
                .locations(new HashSet<>())
                .build();

        secondHousehold = Household.builder()
                .id(secondHouseholdId)
                .name("Second Household")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .members(new HashSet<>())
                .locations(new HashSet<>())
                .build();

        ownerMember = HouseholdMember.builder()
                .id(UUID.randomUUID())
                .user(testUser)
                .household(testHousehold)
                .role(HouseholdRole.OWNER)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        adminMember = HouseholdMember.builder()
                .id(UUID.randomUUID())
                .user(testUser)
                .household(testHousehold)
                .role(HouseholdRole.ADMIN)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        regularMember = HouseholdMember.builder()
                .id(UUID.randomUUID())
                .user(testUser)
                .household(testHousehold)
                .role(HouseholdRole.MEMBER)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        testHouseholdRequest = HouseholdRequest.builder()
                .name("New Household")
                .build();
    }

    // Helper methods
    private void setupValidAuthentication() {
        when(userService.getCurrentUser(eq(validToken))).thenReturn(testUser);
    }

    private void setupInvalidAuthentication() {
        when(userService.getCurrentUser(eq(invalidToken))).thenThrow(new NotFoundException("User not found"));
    }

    private void setupOwnerAccess() {
        when(userService.getCurrentUser(eq(validToken))).thenReturn(testUser);
        when(householdRepository.findById(eq(householdId))).thenReturn(Optional.of(testHousehold));
        when(householdMemberRepository.findByHouseholdIdAndUserId(eq(householdId), eq(userId)))
                .thenReturn(Optional.of(ownerMember));
    }

    private void setupAdminAccess() {
        when(userService.getCurrentUser(eq(validToken))).thenReturn(testUser);
        when(householdRepository.findById(eq(householdId))).thenReturn(Optional.of(testHousehold));
        when(householdMemberRepository.findByHouseholdIdAndUserId(eq(householdId), eq(userId)))
                .thenReturn(Optional.of(adminMember));
    }

    private void setupRegularMemberAccess() {
        when(userService.getCurrentUser(eq(validToken))).thenReturn(testUser);
        when(householdRepository.findById(eq(householdId))).thenReturn(Optional.of(testHousehold));
        when(householdMemberRepository.findByHouseholdIdAndUserId(eq(householdId), eq(userId)))
                .thenReturn(Optional.of(regularMember));
    }

    @Nested
    @DisplayName("Authentication Tests")
    class AuthenticationTests {

        @Test
        @DisplayName("Should throw ValidationException when token is null for create")
        void shouldThrowValidationExceptionWhenTokenIsNullForCreate() {
            assertThatThrownBy(() -> householdService.createHousehold(testHouseholdRequest, null))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("token cannot be null");

            verify(userService, never()).getCurrentUser(any(JwtAuthenticationToken.class));
            verify(householdRepository, never()).save(any(Household.class));
        }

        @Test
        @DisplayName("Should throw ValidationException when request is null for create")
        void shouldThrowValidationExceptionWhenRequestIsNullForCreate() {
            assertThatThrownBy(() -> householdService.createHousehold(null, validToken))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("request cannot be null");

            verify(userService, never()).getCurrentUser(any(JwtAuthenticationToken.class));
            verify(householdRepository, never()).save(any(Household.class));
        }

        @Test
        @DisplayName("Should throw ValidationException when token is null for getById")
        void shouldThrowValidationExceptionWhenTokenIsNullForGetById() {
            assertThatThrownBy(() -> householdService.getHouseholdById(householdId, null))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("token cannot be null");

            verify(userService, never()).getCurrentUser(any(JwtAuthenticationToken.class));
            verify(householdRepository, never()).findById(any(UUID.class));
        }

        @Test
        @DisplayName("Should throw ValidationException when token is null for update")
        void shouldThrowValidationExceptionWhenTokenIsNullForUpdate() {
            assertThatThrownBy(() -> householdService.updateHousehold(householdId, testHouseholdRequest, null))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("token cannot be null");

            verify(userService, never()).getCurrentUser(any(JwtAuthenticationToken.class));
            verify(householdRepository, never()).save(any(Household.class));
        }

        @Test
        @DisplayName("Should throw ValidationException when token is null for delete")
        void shouldThrowValidationExceptionWhenTokenIsNullForDelete() {
            assertThatThrownBy(() -> householdService.deleteHousehold(householdId, null))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("token cannot be null");

            verify(userService, never()).getCurrentUser(any(JwtAuthenticationToken.class));
            verify(householdRepository, never()).delete(any(Household.class));
        }

        @Test
        @DisplayName("Should throw NotFoundException when user is not found")
        void shouldThrowNotFoundExceptionWhenUserNotFound() {
            setupInvalidAuthentication();

            assertThatThrownBy(() -> householdService.createHousehold(testHouseholdRequest, invalidToken))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("User not found");

            verify(userService).getCurrentUser(eq(invalidToken));
            verify(householdRepository, never()).save(any(Household.class));
        }
    }

    @Nested
    @DisplayName("Create Household Tests")
    class CreateHouseholdTests {

        @Test
        @DisplayName("Should create household successfully")
        void shouldCreateHouseholdSuccessfully() {
            setupValidAuthentication();
            when(householdRepository.existsByName(eq(testHouseholdRequest.getName()))).thenReturn(false);
            when(householdRepository.save(any(Household.class))).thenAnswer(invocation -> {
                Household saved = invocation.getArgument(0);
                saved.setId(householdId);
                return saved;
            });
            when(householdMemberRepository.save(any(HouseholdMember.class))).thenAnswer(invocation ->
                    invocation.getArgument(0));

            HouseholdResponse response = householdService.createHousehold(testHouseholdRequest, validToken);

            assertThat(response).isNotNull();
            assertThat(response.getName()).isEqualTo(testHouseholdRequest.getName());
            assertThat(response.getId()).isEqualTo(householdId);
            verify(householdRepository).save(any(Household.class));
            verify(householdMemberRepository).save(any(HouseholdMember.class));
        }

        @Test
        @DisplayName("Should throw ConflictException when household name already exists")
        void shouldThrowConflictExceptionWhenHouseholdNameExists() {
            setupValidAuthentication();
            when(householdRepository.existsByName(eq(testHouseholdRequest.getName()))).thenReturn(true);

            assertThatThrownBy(() -> householdService.createHousehold(testHouseholdRequest, validToken))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("already exists");

            verify(householdRepository, never()).save(any(Household.class));
            verify(householdMemberRepository, never()).save(any(HouseholdMember.class));
        }

        @Test
        @DisplayName("Should throw ValidationException when household name is empty")
        void shouldThrowValidationExceptionWhenHouseholdNameIsEmpty() {
            HouseholdRequest emptyNameRequest = HouseholdRequest.builder().name("").build();

            assertThatThrownBy(() -> householdService.createHousehold(emptyNameRequest, validToken))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("name cannot be empty");

            verify(householdRepository, never()).save(any(Household.class));
        }

        @Test
        @DisplayName("Should throw ValidationException when household name is null")
        void shouldThrowValidationExceptionWhenHouseholdNameIsNull() {
            HouseholdRequest nullNameRequest = HouseholdRequest.builder().build();

            assertThatThrownBy(() -> householdService.createHousehold(nullNameRequest, validToken))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("name cannot be null");

            verify(householdRepository, never()).save(any(Household.class));
        }
    }

    @Nested
    @DisplayName("Get Household Tests")
    class GetHouseholdTests {

        @Test
        @DisplayName("Should get household by ID when user is a member")
        void shouldGetHouseholdByIdWhenUserIsMember() {
            setupValidAuthentication();
            when(householdRepository.findById(eq(householdId))).thenReturn(Optional.of(testHousehold));
            when(householdMemberRepository.findByHouseholdIdAndUserId(eq(householdId), eq(userId)))
                    .thenReturn(Optional.of(ownerMember));

            HouseholdResponse response = householdService.getHouseholdById(householdId, validToken);

            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo(householdId);
            assertThat(response.getName()).isEqualTo(testHousehold.getName());
            verify(householdRepository).findById(eq(householdId));
            verify(householdMemberRepository).findByHouseholdIdAndUserId(eq(householdId), eq(userId));
        }

        @Test
        @DisplayName("Should throw NotFoundException when household doesn't exist")
        void shouldThrowNotFoundExceptionWhenHouseholdDoesntExist() {
            setupValidAuthentication();
            when(householdRepository.findById(eq(householdId))).thenReturn(Optional.empty());

            assertThatThrownBy(() -> householdService.getHouseholdById(householdId, validToken))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("Household not found");

            verify(householdRepository).findById(eq(householdId));
            verify(householdMemberRepository, never()).findByHouseholdIdAndUserId(any(UUID.class), any(UUID.class));
        }

        @Test
        @DisplayName("Should throw InsufficientPermissionException when user is not a member")
        void shouldThrowInsufficientPermissionExceptionWhenUserIsNotMember() {
            setupValidAuthentication();
            when(householdRepository.findById(eq(householdId))).thenReturn(Optional.of(testHousehold));
            when(householdMemberRepository.findByHouseholdIdAndUserId(eq(householdId), eq(userId)))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> householdService.getHouseholdById(householdId, validToken))
                    .isInstanceOf(InsufficientPermissionException.class)
                    .hasMessageContaining("don't have access");

            verify(householdRepository).findById(eq(householdId));
            verify(householdMemberRepository).findByHouseholdIdAndUserId(eq(householdId), eq(userId));
        }

        @Test
        @DisplayName("Should get all households for a user")
        void shouldGetAllHouseholdsForUser() {
            setupValidAuthentication();
            List<Household> households = Arrays.asList(testHousehold, secondHousehold);
            when(householdRepository.findByMembersUserId(eq(userId))).thenReturn(households);

            List<HouseholdResponse> responses = householdService.getUserHouseholds(validToken);

            assertThat(responses).isNotNull();
            assertThat(responses).hasSize(2);
            assertThat(responses.get(0).getId()).isEqualTo(householdId);
            assertThat(responses.get(1).getId()).isEqualTo(secondHouseholdId);
            verify(householdRepository).findByMembersUserId(eq(userId));
        }

        @Test
        @DisplayName("Should return empty list when user has no households")
        void shouldReturnEmptyListWhenUserHasNoHouseholds() {
            setupValidAuthentication();
            when(householdRepository.findByMembersUserId(eq(userId))).thenReturn(Collections.emptyList());

            List<HouseholdResponse> responses = householdService.getUserHouseholds(validToken);

            assertThat(responses).isNotNull();
            assertThat(responses).isEmpty();
            verify(householdRepository).findByMembersUserId(eq(userId));
        }
    }

    @Nested
    @DisplayName("Update Household Tests")
    class UpdateHouseholdTests {

        @Test
        @DisplayName("Should update household when user is owner")
        void shouldUpdateHouseholdWhenUserIsOwner() {
            setupOwnerAccess();
            HouseholdRequest updateRequest = HouseholdRequest.builder().name("Updated Household").build();
            when(householdRepository.findByName(eq(updateRequest.getName()))).thenReturn(Optional.empty());
            when(householdRepository.save(any(Household.class))).thenAnswer(invocation -> invocation.getArgument(0));

            HouseholdResponse response = householdService.updateHousehold(householdId, updateRequest, validToken);

            assertThat(response).isNotNull();
            assertThat(response.getName()).isEqualTo(updateRequest.getName());
            verify(householdRepository).save(any(Household.class));
        }

        @Test
        @DisplayName("Should update household when user is admin")
        void shouldUpdateHouseholdWhenUserIsAdmin() {
            setupAdminAccess();
            HouseholdRequest updateRequest = HouseholdRequest.builder().name("Updated Household").build();
            when(householdRepository.findByName(eq(updateRequest.getName()))).thenReturn(Optional.empty());
            when(householdRepository.save(any(Household.class))).thenAnswer(invocation -> invocation.getArgument(0));

            HouseholdResponse response = householdService.updateHousehold(householdId, updateRequest, validToken);

            assertThat(response).isNotNull();
            assertThat(response.getName()).isEqualTo(updateRequest.getName());
            verify(householdRepository).save(any(Household.class));
        }

        @Test
        @DisplayName("Should throw InsufficientPermissionException when user is regular member")
        void shouldThrowInsufficientPermissionExceptionWhenUserIsRegularMember() {
            setupRegularMemberAccess();
            HouseholdRequest updateRequest = HouseholdRequest.builder().name("Updated Household").build();

            assertThatThrownBy(() -> householdService.updateHousehold(householdId, updateRequest, validToken))
                    .isInstanceOf(InsufficientPermissionException.class)
                    .hasMessageContaining("don't have permission");

            verify(householdRepository, never()).save(any(Household.class));
        }

        @Test
        @DisplayName("Should throw ConflictException when updating to a name that already exists")
        void shouldThrowConflictExceptionWhenUpdatingToExistingName() {
            setupOwnerAccess();
            HouseholdRequest updateRequest = HouseholdRequest.builder().name("Existing Household").build();
            when(householdRepository.findByName(eq(updateRequest.getName())))
                    .thenReturn(Optional.of(secondHousehold));

            assertThatThrownBy(() -> householdService.updateHousehold(householdId, updateRequest, validToken))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("already exists");

            verify(householdRepository, never()).save(any(Household.class));
        }

        @Test
        @DisplayName("Should allow updating to same name")
        void shouldAllowUpdatingToSameName() {
            setupOwnerAccess();
            HouseholdRequest updateRequest = HouseholdRequest.builder().name(testHousehold.getName()).build();
            when(householdRepository.findByName(eq(updateRequest.getName())))
                    .thenReturn(Optional.of(testHousehold));
            when(householdRepository.save(any(Household.class))).thenAnswer(invocation -> invocation.getArgument(0));

            HouseholdResponse response = householdService.updateHousehold(householdId, updateRequest, validToken);

            assertThat(response).isNotNull();
            verify(householdRepository).save(any(Household.class));
        }
    }

    @Nested
    @DisplayName("Delete Household Tests")
    class DeleteHouseholdTests {

        @Test
        @DisplayName("Should delete household when user is owner")
        void shouldDeleteHouseholdWhenUserIsOwner() {
            setupOwnerAccess();
            doNothing().when(householdRepository).delete(any(Household.class));

            assertThatNoException().isThrownBy(() ->
                    householdService.deleteHousehold(householdId, validToken));

            verify(householdRepository).delete(eq(testHousehold));
        }

        @Test
        @DisplayName("Should throw InsufficientPermissionException when user is not owner")
        void shouldThrowInsufficientPermissionExceptionWhenUserIsNotOwner() {
            setupAdminAccess();

            assertThatThrownBy(() -> householdService.deleteHousehold(householdId, validToken))
                    .isInstanceOf(InsufficientPermissionException.class)
                    .hasMessageContaining("Only the household owner");

            verify(householdRepository, never()).delete(any(Household.class));
        }

        @Test
        @DisplayName("Should throw NotFoundException when household doesn't exist")
        void shouldThrowNotFoundExceptionWhenHouseholdDoesntExist() {
            setupValidAuthentication();
            when(householdRepository.findById(eq(householdId))).thenReturn(Optional.empty());

            assertThatThrownBy(() -> householdService.deleteHousehold(householdId, validToken))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("Household not found");

            verify(householdRepository, never()).delete(any(Household.class));
        }
    }

    @Nested
    @DisplayName("Leave Household Tests")
    class LeaveHouseholdTests {

        @Test
        @DisplayName("Should allow regular member to leave household")
        void shouldAllowRegularMemberToLeaveHousehold() {
            setupRegularMemberAccess();
            doNothing().when(householdMemberRepository).delete(any(HouseholdMember.class));

            assertThatNoException().isThrownBy(() ->
                    householdService.leaveHousehold(householdId, validToken));

            verify(householdMemberRepository).delete(eq(regularMember));
        }

        @Test
        @DisplayName("Should allow admin to leave household")
        void shouldAllowAdminToLeaveHousehold() {
            setupAdminAccess();
            doNothing().when(householdMemberRepository).delete(any(HouseholdMember.class));

            assertThatNoException().isThrownBy(() ->
                    householdService.leaveHousehold(householdId, validToken));

            verify(householdMemberRepository).delete(eq(adminMember));
        }

        @Test
        @DisplayName("Should throw ResourceStateException when owner tries to leave household")
        void shouldThrowResourceStateExceptionWhenOwnerTriesToLeaveHousehold() {
            setupOwnerAccess();

            assertThatThrownBy(() -> householdService.leaveHousehold(householdId, validToken))
                    .isInstanceOf(ResourceStateException.class)
                    .hasMessageContaining("Owner cannot leave");

            verify(householdMemberRepository, never()).delete(any(HouseholdMember.class));
        }

        @Test
        @DisplayName("Should throw NotFoundException when household doesn't exist")
        void shouldThrowNotFoundExceptionWhenHouseholdDoesntExist() {
            setupValidAuthentication();
            when(householdRepository.findById(eq(householdId))).thenReturn(Optional.empty());

            assertThatThrownBy(() -> householdService.leaveHousehold(householdId, validToken))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("Household not found");

            verify(householdMemberRepository, never()).delete(any(HouseholdMember.class));
        }
    }

    @Nested
    @DisplayName("Transfer Ownership Tests")
    class TransferOwnershipTests {

        @Test
        @DisplayName("Should transfer ownership to another member")
        void shouldTransferOwnershipToAnotherMember() {
            setupOwnerAccess();
            HouseholdMember targetMember = HouseholdMember.builder()
                    .id(UUID.randomUUID())
                    .user(secondUser)
                    .household(testHousehold)
                    .role(HouseholdRole.MEMBER)
                    .build();

            when(householdMemberRepository.findByHouseholdIdAndUserId(eq(householdId), eq(secondUserId)))
                    .thenReturn(Optional.of(targetMember));
            when(householdMemberRepository.save(any(HouseholdMember.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            assertThatNoException().isThrownBy(() ->
                    householdService.transferOwnership(householdId, secondUserId, validToken));

            verify(householdMemberRepository, times(2)).save(any(HouseholdMember.class));
        }

        @Test
        @DisplayName("Should throw InsufficientPermissionException when non-owner tries to transfer ownership")
        void shouldThrowInsufficientPermissionExceptionWhenNonOwnerTriesToTransferOwnership() {
            setupAdminAccess();

            assertThatThrownBy(() -> householdService.transferOwnership(householdId, secondUserId, validToken))
                    .isInstanceOf(InsufficientPermissionException.class)
                    .hasMessageContaining("Only the household owner");

            verify(householdMemberRepository, never()).save(any(HouseholdMember.class));
        }

        @Test
        @DisplayName("Should throw NotFoundException when target user is not a member")
        void shouldThrowNotFoundExceptionWhenTargetUserIsNotMember() {
            setupOwnerAccess();
            when(householdMemberRepository.findByHouseholdIdAndUserId(eq(householdId), eq(secondUserId)))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> householdService.transferOwnership(householdId, secondUserId, validToken))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("not a member");

            verify(householdMemberRepository, never()).save(any(HouseholdMember.class));
        }

        @Test
        @DisplayName("Should throw ValidationException when target user ID is null")
        void shouldThrowValidationExceptionWhenTargetUserIdIsNull() {
            assertThatThrownBy(() -> householdService.transferOwnership(householdId, null, validToken))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("cannot be null");

            verify(householdMemberRepository, never()).save(any(HouseholdMember.class));
        }
    }

    @Nested
    @DisplayName("Search Households Tests")
    class SearchHouseholdsTests {

        @Test
        @DisplayName("Should search households by name")
        void shouldSearchHouseholdsByName() {
            setupValidAuthentication();
            List<Household> searchResults = Collections.singletonList(testHousehold);
            when(householdRepository.findByNameContainingIgnoreCase(eq("Test")))
                    .thenReturn(searchResults);
            when(householdMemberRepository.findByHouseholdIdAndUserId(eq(householdId), eq(userId)))
                    .thenReturn(Optional.of(ownerMember));

            List<HouseholdResponse> responses = householdService.searchHouseholds("Test", validToken);

            assertThat(responses).isNotNull();
            assertThat(responses).hasSize(1);
            assertThat(responses.getFirst().getName()).contains("Test");
            verify(householdRepository).findByNameContainingIgnoreCase(eq("Test"));
        }

        @Test
        @DisplayName("Should return empty list when no matches found")
        void shouldReturnEmptyListWhenNoMatchesFound() {
            setupValidAuthentication();
            when(householdRepository.findByNameContainingIgnoreCase(eq("NonExistent")))
                    .thenReturn(Collections.emptyList());

            List<HouseholdResponse> responses = householdService.searchHouseholds("NonExistent", validToken);

            assertThat(responses).isNotNull();
            assertThat(responses).isEmpty();
            verify(householdRepository).findByNameContainingIgnoreCase(eq("NonExistent"));
        }

        @Test
        @DisplayName("Should filter out households user is not a member of")
        void shouldFilterOutHouseholdsUserIsNotMemberOf() {
            setupValidAuthentication();
            List<Household> searchResults = Arrays.asList(testHousehold, secondHousehold);
            when(householdRepository.findByNameContainingIgnoreCase(eq("Household")))
                    .thenReturn(searchResults);
            when(householdMemberRepository.findByHouseholdIdAndUserId(eq(householdId), eq(userId)))
                    .thenReturn(Optional.of(ownerMember));
            when(householdMemberRepository.findByHouseholdIdAndUserId(eq(secondHouseholdId), eq(userId)))
                    .thenReturn(Optional.empty());

            List<HouseholdResponse> responses = householdService.searchHouseholds("Household", validToken);

            assertThat(responses).isNotNull();
            assertThat(responses).hasSize(1);
            assertThat(responses.getFirst().getId()).isEqualTo(householdId);
        }

        @Test
        @DisplayName("Should throw ValidationException when search query is null")
        void shouldThrowValidationExceptionWhenSearchQueryIsNull() {

            assertThatThrownBy(() -> householdService.searchHouseholds(null, validToken))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("cannot be null");

            verify(householdRepository, never()).findByNameContainingIgnoreCase(any(String.class));
        }
    }

    @Nested
    @DisplayName("Change Member Role Tests")
    class ChangeMemberRoleTests {

        @Test
        @DisplayName("Should allow owner to change member role")
        void shouldAllowOwnerToChangeMemberRole() {
            setupOwnerAccess();
            HouseholdMember targetMember = HouseholdMember.builder()
                    .id(UUID.randomUUID())
                    .user(secondUser)
                    .household(testHousehold)
                    .role(HouseholdRole.MEMBER)
                    .build();

            when(householdMemberRepository.findById(eq(targetMember.getId())))
                    .thenReturn(Optional.of(targetMember));
            when(householdMemberRepository.save(any(HouseholdMember.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            assertThatNoException().isThrownBy(() ->
                    householdService.changeMemberRole(householdId, targetMember.getId(),
                            HouseholdRole.ADMIN, validToken));

            verify(householdMemberRepository).save(any(HouseholdMember.class));
        }

        @Test
        @DisplayName("Should allow admin to change regular member role")
        void shouldAllowAdminToChangeRegularMemberRole() {
            setupAdminAccess();
            HouseholdMember targetMember = HouseholdMember.builder()
                    .id(UUID.randomUUID())
                    .user(secondUser)
                    .household(testHousehold)
                    .role(HouseholdRole.MEMBER)
                    .build();

            when(householdMemberRepository.findById(eq(targetMember.getId())))
                    .thenReturn(Optional.of(targetMember));
            when(householdMemberRepository.save(any(HouseholdMember.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            assertThatNoException().isThrownBy(() ->
                    householdService.changeMemberRole(householdId, targetMember.getId(),
                            HouseholdRole.ADMIN, validToken));

            verify(householdMemberRepository).save(any(HouseholdMember.class));
        }

        @Test
        @DisplayName("Should throw InsufficientPermissionException when admin tries to change owner role")
        void shouldThrowInsufficientPermissionExceptionWhenAdminTriesToChangeOwnerRole() {
            setupAdminAccess();
            HouseholdMember ownerTargetMember = HouseholdMember.builder()
                    .id(UUID.randomUUID())
                    .user(secondUser)
                    .household(testHousehold)
                    .role(HouseholdRole.OWNER)
                    .build();

            when(householdMemberRepository.findById(eq(ownerTargetMember.getId())))
                    .thenReturn(Optional.of(ownerTargetMember));

            assertThatThrownBy(() -> householdService.changeMemberRole(householdId,
                    ownerTargetMember.getId(), HouseholdRole.MEMBER, validToken))
                    .isInstanceOf(InsufficientPermissionException.class)
                    .hasMessageContaining("cannot change owner");

            verify(householdMemberRepository, never()).save(any(HouseholdMember.class));
        }

        @Test
        @DisplayName("Should throw InsufficientPermissionException when regular member tries to change roles")
        void shouldThrowInsufficientPermissionExceptionWhenRegularMemberTriesToChangeRoles() {
            setupRegularMemberAccess();

            assertThatThrownBy(() -> householdService.changeMemberRole(householdId,
                    UUID.randomUUID(), HouseholdRole.ADMIN, validToken))
                    .isInstanceOf(InsufficientPermissionException.class)
                    .hasMessageContaining("don't have permission");

            verify(householdMemberRepository, never()).save(any(HouseholdMember.class));
        }

        @Test
        @DisplayName("Should throw ValidationException when changing role to OWNER")
        void shouldThrowValidationExceptionWhenChangingRoleToOwner() {
            assertThatThrownBy(() -> householdService.changeMemberRole(householdId,
                    UUID.randomUUID(), HouseholdRole.OWNER, validToken))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Cannot set role to OWNER");

            verify(householdMemberRepository, never()).save(any(HouseholdMember.class));
        }
    }

    @Nested
    @DisplayName("Patch Household Tests")
    class PatchHouseholdTests {

        @Test
        @DisplayName("Should patch household name when user is owner")
        void shouldPatchHouseholdNameWhenUserIsOwner() {
            setupOwnerAccess();
            Map<String, Object> updates = new HashMap<>();
            updates.put("name", "Patched Household");
            when(householdRepository.findByName(eq("Patched Household"))).thenReturn(Optional.empty());
            when(householdRepository.save(any(Household.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            HouseholdResponse response = householdService.patchHousehold(householdId, updates, validToken);

            assertThat(response).isNotNull();
            assertThat(response.getName()).isEqualTo("Patched Household");
            verify(householdRepository).save(any(Household.class));
        }

        @Test
        @DisplayName("Should patch household when user is admin")
        void shouldPatchHouseholdWhenUserIsAdmin() {
            setupAdminAccess();
            Map<String, Object> updates = new HashMap<>();
            updates.put("name", "Patched by Admin");
            when(householdRepository.findByName(eq("Patched by Admin"))).thenReturn(Optional.empty());
            when(householdRepository.save(any(Household.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            HouseholdResponse response = householdService.patchHousehold(householdId, updates, validToken);

            assertThat(response).isNotNull();
            verify(householdRepository).save(any(Household.class));
        }

        @Test
        @DisplayName("Should ignore unknown fields when patching")
        void shouldIgnoreUnknownFieldsWhenPatching() {
            setupOwnerAccess();
            Map<String, Object> updates = new HashMap<>();
            updates.put("name", "Valid Name");
            updates.put("unknownField", "Should be ignored");
            when(householdRepository.findByName(eq("Valid Name"))).thenReturn(Optional.empty());
            when(householdRepository.save(any(Household.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            assertThatNoException().isThrownBy(() ->
                    householdService.patchHousehold(householdId, updates, validToken));

            verify(householdRepository).save(any(Household.class));
        }

        @Test
        @DisplayName("Should throw InsufficientPermissionException when user is regular member")
        void shouldThrowInsufficientPermissionExceptionWhenUserIsRegularMember() {
            setupRegularMemberAccess();
            Map<String, Object> updates = new HashMap<>();
            updates.put("name", "New Name");

            assertThatThrownBy(() -> householdService.patchHousehold(householdId, updates, validToken))
                    .isInstanceOf(InsufficientPermissionException.class)
                    .hasMessageContaining("don't have permission");

            verify(householdRepository, never()).save(any(Household.class));
        }

        @Test
        @DisplayName("Should throw ValidationException when updates map is null")
        void shouldThrowValidationExceptionWhenUpdatesMapIsNull() {
            assertThatThrownBy(() -> householdService.patchHousehold(householdId, null, validToken))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("cannot be null");

            verify(householdRepository, never()).save(any(Household.class));
        }

        @Test
        @DisplayName("Should throw ValidationException when updates map is empty")
        void shouldThrowValidationExceptionWhenUpdatesMapIsEmpty() {
            assertThatThrownBy(() -> householdService.patchHousehold(householdId,
                    Collections.emptyMap(), validToken))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("cannot be empty");

            verify(householdRepository, never()).save(any(Household.class));
        }
    }
}
