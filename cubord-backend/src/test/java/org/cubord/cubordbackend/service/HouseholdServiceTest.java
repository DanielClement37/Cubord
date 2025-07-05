package org.cubord.cubordbackend.service;

import org.cubord.cubordbackend.domain.*;
import org.cubord.cubordbackend.dto.HouseholdRequest;
import org.cubord.cubordbackend.dto.HouseholdResponse;
import org.cubord.cubordbackend.exception.NotFoundException;
import org.cubord.cubordbackend.repository.HouseholdMemberRepository;
import org.cubord.cubordbackend.repository.HouseholdRepository;
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
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class HouseholdServiceTest {

    @Mock
    private HouseholdRepository householdRepository;

    @Mock
    private HouseholdMemberRepository householdMemberRepository;

    @Mock
    private UserService userService;

    @Mock
    private JwtAuthenticationToken token;

    @InjectMocks
    private HouseholdService householdService;

    private User testUser;
    private Household testHousehold;
    private HouseholdMember testMember;
    private UUID userId;
    private UUID householdId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        householdId = UUID.randomUUID();

        testUser = new User();
        testUser.setId(userId);
        testUser.setUsername("testuser");

        testHousehold = new Household();
        testHousehold.setId(householdId);
        testHousehold.setName("Test Household");
        testHousehold.setMembers(new HashSet<>());
        testHousehold.setLocations(new HashSet<>());
        testHousehold.setCreatedAt(LocalDateTime.now());
        testHousehold.setUpdatedAt(LocalDateTime.now());

        testMember = new HouseholdMember();
        testMember.setId(UUID.randomUUID());
        testMember.setUser(testUser);
        testMember.setHousehold(testHousehold);
        testMember.setRole(HouseholdRole.OWNER);
        testMember.setCreatedAt(LocalDateTime.now());
        testMember.setUpdatedAt(LocalDateTime.now());

        testHousehold.getMembers().add(testMember);
    }

    @Nested
    @DisplayName("Create Household Tests")
    class CreateHouseholdTests {

        @Test
        @DisplayName("Should create a household successfully")
        void shouldCreateHouseholdSuccessfully() {
            // Given
            HouseholdRequest request = new HouseholdRequest();
            request.setName("New Household");

            when(userService.getCurrentUser(token)).thenReturn(testUser);
            when(householdRepository.existsByName(request.getName())).thenReturn(false);
            when(householdRepository.save(any(Household.class))).thenAnswer(invocation -> {
                Household savedHousehold = invocation.getArgument(0);
                savedHousehold.setId(UUID.randomUUID());
                return savedHousehold;
            });
            when(householdMemberRepository.save(any(HouseholdMember.class))).thenAnswer(invocation -> {
                HouseholdMember savedMember = invocation.getArgument(0);
                savedMember.setId(UUID.randomUUID());
                return savedMember;
            });

            // When
            HouseholdResponse response = householdService.createHousehold(request, token);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getName()).isEqualTo(request.getName());
            assertThat(response.getId()).isNotNull();
            verify(householdRepository).save(any(Household.class));
            verify(householdMemberRepository).save(any(HouseholdMember.class));
        }

        @Test
        @DisplayName("Should throw exception when household name already exists")
        void shouldThrowExceptionWhenHouseholdNameExists() {
            // Given
            HouseholdRequest request = new HouseholdRequest();
            request.setName("Existing Household");

            when(userService.getCurrentUser(token)).thenReturn(testUser);
            when(householdRepository.existsByName(request.getName())).thenReturn(true);

            // When/Then
            assertThatThrownBy(() -> householdService.createHousehold(request, token))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("already exists");

            verify(householdRepository, never()).save(any());
            verify(householdMemberRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Get Household Tests")
    class GetHouseholdTests {

        @Test
        @DisplayName("Should get household by ID when user is a member")
        void shouldGetHouseholdByIdWhenUserIsMember() {
            // Given
            when(userService.getCurrentUser(token)).thenReturn(testUser);
            when(householdRepository.findById(householdId)).thenReturn(Optional.of(testHousehold));
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, userId))
                    .thenReturn(Optional.of(testMember));

            // When
            HouseholdResponse response = householdService.getHouseholdById(householdId, token);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo(householdId);
            assertThat(response.getName()).isEqualTo(testHousehold.getName());
            verify(householdRepository).findById(householdId);
            verify(householdMemberRepository).findByHouseholdIdAndUserId(householdId, userId);
        }

        @Test
        @DisplayName("Should throw NotFoundException when household doesn't exist")
        void shouldThrowNotFoundExceptionWhenHouseholdDoesntExist() {
            // Given
            when(userService.getCurrentUser(token)).thenReturn(testUser);
            when(householdRepository.findById(householdId)).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> householdService.getHouseholdById(householdId, token))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("Household not found");

            verify(householdRepository).findById(householdId);
            verify(householdMemberRepository, never()).findByHouseholdIdAndUserId(any(), any());
        }

        @Test
        @DisplayName("Should throw ForbiddenException when user is not a member")
        void shouldThrowAccessDeniedExceptionWhenUserIsNotMember() {
            // Given
            when(userService.getCurrentUser(token)).thenReturn(testUser);
            when(householdRepository.findById(householdId)).thenReturn(Optional.of(testHousehold));
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, userId))
                    .thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> householdService.getHouseholdById(householdId, token))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("access to this household");

            verify(householdRepository).findById(householdId);
            verify(householdMemberRepository).findByHouseholdIdAndUserId(householdId, userId);
        }

        @Test
        @DisplayName("Should get all households for a user")
        void shouldGetAllHouseholdsForUser() {
            // Given
            when(userService.getCurrentUser(token)).thenReturn(testUser);
            when(householdRepository.findByMembersUserId(userId)).thenReturn(List.of(testHousehold));

            // When
            List<HouseholdResponse> responses = householdService.getUserHouseholds(token);

            // Then
            assertThat(responses).hasSize(1);
            assertThat(responses.getFirst().getId()).isEqualTo(householdId);
            assertThat(responses.getFirst().getName()).isEqualTo(testHousehold.getName());
            verify(householdRepository).findByMembersUserId(userId);
        }

        @Test
        @DisplayName("Should return empty list when user has no households")
        void shouldReturnEmptyListWhenUserHasNoHouseholds() {
            // Given
            when(userService.getCurrentUser(token)).thenReturn(testUser);
            when(householdRepository.findByMembersUserId(userId)).thenReturn(Collections.emptyList());

            // When
            List<HouseholdResponse> responses = householdService.getUserHouseholds(token);

            // Then
            assertThat(responses).isEmpty();
            verify(householdRepository).findByMembersUserId(userId);
        }
    }

    @Nested
    @DisplayName("Update Household Tests")
    class UpdateHouseholdTests {

        @Test
        @DisplayName("Should update household when user is owner")
        void shouldUpdateHouseholdWhenUserIsOwner() {
            // Given
            HouseholdRequest request = new HouseholdRequest();
            request.setName("Updated Household Name");

            when(userService.getCurrentUser(token)).thenReturn(testUser);
            when(householdRepository.findById(householdId)).thenReturn(Optional.of(testHousehold));
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, userId))
                    .thenReturn(Optional.of(testMember));
            when(householdRepository.save(any(Household.class))).thenReturn(testHousehold);

            // When
            HouseholdResponse response = householdService.updateHousehold(householdId, request, token);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo(householdId);
            assertThat(response.getName()).isEqualTo(request.getName());
            verify(householdRepository).findById(householdId);
            verify(householdMemberRepository).findByHouseholdIdAndUserId(householdId, userId);
            verify(householdRepository).save(testHousehold);
        }

        @Test
        @DisplayName("Should update household when user is admin")
        void shouldUpdateHouseholdWhenUserIsAdmin() {
            // Given
            testMember.setRole(HouseholdRole.ADMIN);
            HouseholdRequest request = new HouseholdRequest();
            request.setName("Updated Household Name");

            when(userService.getCurrentUser(token)).thenReturn(testUser);
            when(householdRepository.findById(householdId)).thenReturn(Optional.of(testHousehold));
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, userId))
                    .thenReturn(Optional.of(testMember));
            when(householdRepository.save(any(Household.class))).thenReturn(testHousehold);

            // When
            HouseholdResponse response = householdService.updateHousehold(householdId, request, token);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getName()).isEqualTo(request.getName());
            verify(householdRepository).save(testHousehold);
        }

        @Test
        @DisplayName("Should throw ForbiddenException when user is regular member")
        void shouldThrowAccessDeniedExceptionWhenUserIsRegularMember() {
            // Given
            testMember.setRole(HouseholdRole.MEMBER);
            HouseholdRequest request = new HouseholdRequest();
            request.setName("Updated Household Name");

            when(userService.getCurrentUser(token)).thenReturn(testUser);
            when(householdRepository.findById(householdId)).thenReturn(Optional.of(testHousehold));
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, userId))
                    .thenReturn(Optional.of(testMember));

            // When/Then
            assertThatThrownBy(() -> householdService.updateHousehold(householdId, request, token))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("permission to update");

            verify(householdRepository).findById(householdId);
            verify(householdMemberRepository).findByHouseholdIdAndUserId(householdId, userId);
            verify(householdRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw exception when updating to a name that already exists")
        void shouldThrowExceptionWhenUpdatingToExistingName() {
            // Given
            Household existingHousehold = new Household();
            existingHousehold.setId(UUID.randomUUID());
            existingHousehold.setName("Existing Name");

            HouseholdRequest request = new HouseholdRequest();
            request.setName("Existing Name");

            when(userService.getCurrentUser(token)).thenReturn(testUser);
            when(householdRepository.findById(householdId)).thenReturn(Optional.of(testHousehold));
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, userId))
                    .thenReturn(Optional.of(testMember));
            when(householdRepository.findByName(request.getName())).thenReturn(Optional.of(existingHousehold));

            // When/Then
            assertThatThrownBy(() -> householdService.updateHousehold(householdId, request, token))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("already exists");

            verify(householdRepository).findById(householdId);
            verify(householdMemberRepository).findByHouseholdIdAndUserId(householdId, userId);
            verify(householdRepository).findByName(request.getName());
            verify(householdRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Delete Household Tests")
    class DeleteHouseholdTests {

        @Test
        @DisplayName("Should delete household when user is owner")
        void shouldDeleteHouseholdWhenUserIsOwner() {
            // Given
            when(userService.getCurrentUser(token)).thenReturn(testUser);
            when(householdRepository.findById(householdId)).thenReturn(Optional.of(testHousehold));
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, userId))
                    .thenReturn(Optional.of(testMember));
            doNothing().when(householdRepository).delete(testHousehold);

            // When
            householdService.deleteHousehold(householdId, token);

            // Then
            verify(householdRepository).findById(householdId);
            verify(householdMemberRepository).findByHouseholdIdAndUserId(householdId, userId);
            verify(householdRepository).delete(testHousehold);
        }

        @Test
        @DisplayName("Should throw ForbiddenException when user is not owner")
        void shouldThrowAccessDeniedExceptionWhenUserIsNotOwner() {
            // Given
            testMember.setRole(HouseholdRole.ADMIN);

            when(userService.getCurrentUser(token)).thenReturn(testUser);
            when(householdRepository.findById(householdId)).thenReturn(Optional.of(testHousehold));
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, userId))
                    .thenReturn(Optional.of(testMember));

            // When/Then
            assertThatThrownBy(() -> householdService.deleteHousehold(householdId, token))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("Only the owner can delete");

            verify(householdRepository).findById(householdId);
            verify(householdMemberRepository).findByHouseholdIdAndUserId(householdId, userId);
            verify(householdRepository, never()).delete(any());
        }
    }

    @Nested
    @DisplayName("Leave Household Tests")
    class LeaveHouseholdTests {

        @Test
        @DisplayName("Should allow regular member to leave a household")
        void shouldAllowRegularMemberToLeaveHousehold() {
            // Given
            testMember.setRole(HouseholdRole.MEMBER);

            when(userService.getCurrentUser(token)).thenReturn(testUser);
            // Remove the findById setup since leaveHousehold doesn't call it
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, userId))
                    .thenReturn(Optional.of(testMember));
            doNothing().when(householdMemberRepository).delete(testMember);

            // When
            householdService.leaveHousehold(householdId, token);

            // Then
            // Don't verify findById call since it's not made
            verify(householdMemberRepository).findByHouseholdIdAndUserId(householdId, userId);
            verify(householdMemberRepository).delete(testMember);
        }

        @Test
        @DisplayName("Should throw exception when owner tries to leave a household")
        void shouldThrowExceptionWhenOwnerTriesToLeaveHousehold() {
            // Given
            testMember.setRole(HouseholdRole.OWNER);

            when(userService.getCurrentUser(token)).thenReturn(testUser);
            // Remove the findById setup since leaveHousehold doesn't call it
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, userId))
                    .thenReturn(Optional.of(testMember));

            // When/Then
            assertThatThrownBy(() -> householdService.leaveHousehold(householdId, token))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Owners cannot leave");

            // Don't verify findById call since it's not made
            verify(householdMemberRepository).findByHouseholdIdAndUserId(householdId, userId);
            verify(householdMemberRepository, never()).delete(any());
        }
    }

    @Nested
    @DisplayName("Transfer Ownership Tests")
    class TransferOwnershipTests {

        @Test
        @DisplayName("Should transfer ownership to another member")
        void shouldTransferOwnershipToAnotherMember() {
            // Given
            UUID newOwnerId = UUID.randomUUID();

            User newOwnerUser = new User();
            newOwnerUser.setId(newOwnerId);

            HouseholdMember newOwnerMember = new HouseholdMember();
            newOwnerMember.setId(UUID.randomUUID());
            newOwnerMember.setUser(newOwnerUser);
            newOwnerMember.setHousehold(testHousehold);
            newOwnerMember.setRole(HouseholdRole.ADMIN);

            when(userService.getCurrentUser(token)).thenReturn(testUser);
            // Remove findById stubbing since transferOwnership doesn't call it
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, userId))
                    .thenReturn(Optional.of(testMember));
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, newOwnerId))
                    .thenReturn(Optional.of(newOwnerMember));
            when(householdMemberRepository.save(any(HouseholdMember.class))).thenAnswer(i -> i.getArgument(0));

            // When
            householdService.transferOwnership(householdId, newOwnerId, token);

            // Then
            // Don't verify findById call since it's not made
            verify(householdMemberRepository).findByHouseholdIdAndUserId(householdId, userId);
            verify(householdMemberRepository).findByHouseholdIdAndUserId(householdId, newOwnerId);
            verify(householdMemberRepository, times(2)).save(any(HouseholdMember.class));
        }

        @Test
        @DisplayName("Should throw exception when non-owner tries to transfer ownership")
        void shouldThrowExceptionWhenNonOwnerTriesToTransferOwnership() {
            // Given
            UUID newOwnerId = UUID.randomUUID();
            testMember.setRole(HouseholdRole.ADMIN);

            when(userService.getCurrentUser(token)).thenReturn(testUser);
            // Remove findById stubbing since transferOwnership doesn't call it
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, userId))
                    .thenReturn(Optional.of(testMember));

            // When/Then
            assertThatThrownBy(() -> householdService.transferOwnership(householdId, newOwnerId, token))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("Only the owner can transfer ownership");

            // Don't verify findById call since it's not made
            verify(householdMemberRepository).findByHouseholdIdAndUserId(householdId, userId);
            verify(householdMemberRepository, never()).findByHouseholdIdAndUserId(householdId, newOwnerId);
            verify(householdMemberRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw exception when target user is not a member")
        void shouldThrowExceptionWhenTargetUserIsNotMember() {
            // Given
            UUID newOwnerId = UUID.randomUUID();

            when(userService.getCurrentUser(token)).thenReturn(testUser);
            // Remove findById stubbing since transferOwnership doesn't call it
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, userId))
                    .thenReturn(Optional.of(testMember));
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, newOwnerId))
                    .thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> householdService.transferOwnership(householdId, newOwnerId, token))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("New owner is not a member");

            // Don't verify findById call since it's not made
            verify(householdMemberRepository).findByHouseholdIdAndUserId(householdId, userId);
            verify(householdMemberRepository).findByHouseholdIdAndUserId(householdId, newOwnerId);
            verify(householdMemberRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Search Households Tests")
    class SearchHouseholdsTests {

        @Test
        @DisplayName("Should search households by name")
        void shouldSearchHouseholdsByName() {
            // Given
            String searchTerm = "Test";
            when(userService.getCurrentUser(token)).thenReturn(testUser);
            when(householdRepository.findByNameContainingIgnoreCase(searchTerm))
                    .thenReturn(List.of(testHousehold));
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, userId))
                    .thenReturn(Optional.of(testMember));

            // When
            List<HouseholdResponse> responses = householdService.searchHouseholds(searchTerm, token);

            // Then
            assertThat(responses).hasSize(1);
            assertThat(responses.getFirst().getId()).isEqualTo(householdId);
            assertThat(responses.getFirst().getName()).isEqualTo(testHousehold.getName());
            verify(householdRepository).findByNameContainingIgnoreCase(searchTerm);
        }

        @Test
        @DisplayName("Should return empty list when no matches found")
        void shouldReturnEmptyListWhenNoMatchesFound() {
            // Given
            String searchTerm = "Nonexistent";
            when(userService.getCurrentUser(token)).thenReturn(testUser);
            when(householdRepository.findByNameContainingIgnoreCase(searchTerm))
                    .thenReturn(Collections.emptyList());

            // When
            List<HouseholdResponse> responses = householdService.searchHouseholds(searchTerm, token);

            // Then
            assertThat(responses).isEmpty();
            verify(householdRepository).findByNameContainingIgnoreCase(searchTerm);
        }

        @Test
        @DisplayName("Should filter out households user is not a member of")
        void shouldFilterOutHouseholdsUserIsNotMemberOf() {
            // Given
            String searchTerm = "Test";
            when(userService.getCurrentUser(token)).thenReturn(testUser);
            when(householdRepository.findByNameContainingIgnoreCase(searchTerm))
                    .thenReturn(List.of(testHousehold));
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, userId))
                    .thenReturn(Optional.empty());

            // When
            List<HouseholdResponse> responses = householdService.searchHouseholds(searchTerm, token);

            // Then
            assertThat(responses).isEmpty();
            verify(householdRepository).findByNameContainingIgnoreCase(searchTerm);
            verify(householdMemberRepository).findByHouseholdIdAndUserId(householdId, userId);
        }
    }

    @Nested
    @DisplayName("Change Member Role Tests")
    class ChangeMemberRoleTests {

        @Test
        @DisplayName("Should allow owner to change member role")
        void shouldAllowOwnerToChangeMemberRole() {
            // Given
            UUID memberId = UUID.randomUUID();
            User memberUser = new User();
            memberUser.setId(memberId);

            HouseholdMember targetMember = new HouseholdMember();
            targetMember.setId(UUID.randomUUID());
            targetMember.setUser(memberUser);
            targetMember.setHousehold(testHousehold);
            targetMember.setRole(HouseholdRole.MEMBER);

            when(userService.getCurrentUser(token)).thenReturn(testUser);
            // Remove findById stubbing since changeMemberRole doesn't call it
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, userId))
                    .thenReturn(Optional.of(testMember));
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, memberId))
                    .thenReturn(Optional.of(targetMember));
            when(householdMemberRepository.save(any(HouseholdMember.class))).thenReturn(targetMember);

            // When
            householdService.changeMemberRole(householdId, memberId, HouseholdRole.ADMIN, token);

            // Then
            // Don't verify findById call since it's not made
            verify(householdMemberRepository).findByHouseholdIdAndUserId(householdId, userId);
            verify(householdMemberRepository).findByHouseholdIdAndUserId(householdId, memberId);
            verify(householdMemberRepository).save(any(HouseholdMember.class));
        }

        @Test
        @DisplayName("Should allow admin to change regular member role")
        void shouldAllowAdminToChangeRegularMemberRole() {
            // Given
            testMember.setRole(HouseholdRole.ADMIN);
            UUID memberId = UUID.randomUUID();
            User memberUser = new User();
            memberUser.setId(memberId);

            HouseholdMember targetMember = new HouseholdMember();
            targetMember.setId(UUID.randomUUID());
            targetMember.setUser(memberUser);
            targetMember.setHousehold(testHousehold);
            targetMember.setRole(HouseholdRole.MEMBER);

            when(userService.getCurrentUser(token)).thenReturn(testUser);
            // Use lenient for setup that might not be used in all scenarios
            lenient().when(householdRepository.findById(householdId)).thenReturn(Optional.of(testHousehold));
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, userId))
                    .thenReturn(Optional.of(testMember));
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, memberId))
                    .thenReturn(Optional.of(targetMember));
            when(householdMemberRepository.save(any(HouseholdMember.class))).thenReturn(targetMember);

            // When
            householdService.changeMemberRole(householdId, memberId, HouseholdRole.ADMIN, token);

            // Then
            verify(householdMemberRepository).findByHouseholdIdAndUserId(householdId, userId);
            verify(householdMemberRepository).findByHouseholdIdAndUserId(householdId, memberId);
            verify(householdMemberRepository).save(any(HouseholdMember.class));
        }

        @Test
        @DisplayName("Should not allow admin to change owner role")
        void shouldNotAllowAdminToChangeOwnerRole() {
            // Given
            testMember.setRole(HouseholdRole.ADMIN);
            UUID ownerId = UUID.randomUUID();
            User ownerUser = new User();
            ownerUser.setId(ownerId);

            HouseholdMember ownerMember = new HouseholdMember();
            ownerMember.setId(UUID.randomUUID());
            ownerMember.setUser(ownerUser);
            ownerMember.setHousehold(testHousehold);
            ownerMember.setRole(HouseholdRole.OWNER);

            when(userService.getCurrentUser(token)).thenReturn(testUser);
            // Use lenient for setup that might not be used in all scenarios
            lenient().when(householdRepository.findById(householdId)).thenReturn(Optional.of(testHousehold));
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, userId))
                    .thenReturn(Optional.of(testMember));
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, ownerId))
                    .thenReturn(Optional.of(ownerMember));

            // When/Then
            assertThatThrownBy(() -> householdService.changeMemberRole(householdId, ownerId, HouseholdRole.MEMBER, token))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("Only an owner can change the role of another owner or admin");

            verify(householdMemberRepository).findByHouseholdIdAndUserId(householdId, userId);
            verify(householdMemberRepository).findByHouseholdIdAndUserId(householdId, ownerId);
            verify(householdMemberRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw exception when regular member tries to change roles")
        void shouldThrowExceptionWhenRegularMemberTriesToChangeRoles() {
            // Given
            testMember.setRole(HouseholdRole.MEMBER);
            UUID memberId = UUID.randomUUID();

            when(userService.getCurrentUser(token)).thenReturn(testUser);
            // Use lenient for setup that might not be used in all scenarios
            lenient().when(householdRepository.findById(householdId)).thenReturn(Optional.of(testHousehold));
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, userId))
                    .thenReturn(Optional.of(testMember));

            // When/Then
            assertThatThrownBy(() -> householdService.changeMemberRole(householdId, memberId, HouseholdRole.ADMIN, token))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("You don't have permission to change member roles");

            verify(householdMemberRepository).findByHouseholdIdAndUserId(householdId, userId);
            verify(householdMemberRepository, never()).findByHouseholdIdAndUserId(householdId, memberId);
            verify(householdMemberRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should not allow changing role to OWNER through this method")
        void shouldNotAllowChangingRoleToOwner() {
            // Given
            UUID memberId = UUID.randomUUID();

            // Remove this stubbing since it's not used (the method throws an exception before this is called)
            // when(userService.getCurrentUser(token)).thenReturn(testUser);

            // When/Then
            assertThatThrownBy(() -> householdService.changeMemberRole(householdId, memberId, HouseholdRole.OWNER, token))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Cannot set role to OWNER");

            // The method throws an exception before any interactions with repository, so verify no interactions
            verify(householdMemberRepository, never()).findByHouseholdIdAndUserId(any(), any());
            verify(householdMemberRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Patch Household Tests")
    class PatchHouseholdTests {

        @Test
        @DisplayName("Should patch household name when user is owner")
        void shouldPatchHouseholdNameWhenUserIsOwner() {
            // Given
            Map<String, Object> patchFields = new HashMap<>();
            patchFields.put("name", "Updated Household Name");

            testMember.setRole(HouseholdRole.OWNER);

            when(userService.getCurrentUser(token)).thenReturn(testUser);
            when(householdRepository.findById(householdId)).thenReturn(Optional.of(testHousehold));
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, userId))
                    .thenReturn(Optional.of(testMember));
            when(householdRepository.findByName("Updated Household Name")).thenReturn(Optional.empty());
            when(householdRepository.save(testHousehold)).thenReturn(testHousehold);

            // When
            HouseholdResponse response = householdService.patchHousehold(householdId, patchFields, token);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo(householdId);
            assertThat(response.getName()).isEqualTo("Updated Household Name");

            verify(householdRepository).findById(householdId);
            verify(householdMemberRepository).findByHouseholdIdAndUserId(householdId, userId);
            verify(householdRepository).findByName("Updated Household Name");
            verify(householdRepository).save(testHousehold);
        }

        @Test
        @DisplayName("Should patch household when user is admin")
        void shouldPatchHouseholdWhenUserIsAdmin() {
            // Given
            Map<String, Object> patchFields = new HashMap<>();
            patchFields.put("name", "Admin Updated Name");

            testMember.setRole(HouseholdRole.ADMIN);

            when(userService.getCurrentUser(token)).thenReturn(testUser);
            when(householdRepository.findById(householdId)).thenReturn(Optional.of(testHousehold));
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, userId))
                    .thenReturn(Optional.of(testMember));
            when(householdRepository.findByName("Admin Updated Name")).thenReturn(Optional.empty());
            when(householdRepository.save(testHousehold)).thenReturn(testHousehold);

            // When
            HouseholdResponse response = householdService.patchHousehold(householdId, patchFields, token);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getName()).isEqualTo("Admin Updated Name");

            verify(householdRepository).findById(householdId);
            verify(householdMemberRepository).findByHouseholdIdAndUserId(householdId, userId);
            verify(householdRepository).findByName("Admin Updated Name");
            verify(householdRepository).save(testHousehold);
        }

        @Test
        @DisplayName("Should ignore unknown fields when patching")
        void shouldIgnoreUnknownFieldsWhenPatching() {
            // Given
            Map<String, Object> patchFields = new HashMap<>();
            patchFields.put("name", "Valid Name Update");
            patchFields.put("unknownField", "Some Value");

            testMember.setRole(HouseholdRole.OWNER);

            when(userService.getCurrentUser(token)).thenReturn(testUser);
            when(householdRepository.findById(householdId)).thenReturn(Optional.of(testHousehold));
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, userId))
                    .thenReturn(Optional.of(testMember));
            when(householdRepository.findByName("Valid Name Update")).thenReturn(Optional.empty());
            when(householdRepository.save(testHousehold)).thenReturn(testHousehold);

            // When
            HouseholdResponse response = householdService.patchHousehold(householdId, patchFields, token);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getName()).isEqualTo("Valid Name Update");

            verify(householdRepository).findById(householdId);
            verify(householdMemberRepository).findByHouseholdIdAndUserId(householdId, userId);
            verify(householdRepository).findByName("Valid Name Update");
            verify(householdRepository).save(testHousehold);
        }

        @Test
        @DisplayName("Should throw ForbiddenException when user is regular member")
        void shouldThrowForbiddenExceptionWhenUserIsRegularMember() {
            // Given
            Map<String, Object> patchFields = new HashMap<>();
            patchFields.put("name", "Attempt Update");

            testMember.setRole(HouseholdRole.MEMBER);

            when(userService.getCurrentUser(token)).thenReturn(testUser);
            when(householdRepository.findById(householdId)).thenReturn(Optional.of(testHousehold));
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, userId))
                    .thenReturn(Optional.of(testMember));

            // When/Then
            assertThatThrownBy(() -> householdService.patchHousehold(householdId, patchFields, token))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("don't have permission");

            verify(householdRepository).findById(householdId);
            verify(householdMemberRepository).findByHouseholdIdAndUserId(householdId, userId);
            verify(householdRepository, never()).findByName(anyString());
            verify(householdRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw exception when regular member tries to change roles")
        void shouldThrowExceptionWhenRegularMemberTriesToChangeRoles() {
            // Given
            testMember.setRole(HouseholdRole.MEMBER);
            UUID memberId = UUID.randomUUID();

            when(userService.getCurrentUser(token)).thenReturn(testUser);
            // Use lenient for setup that might not be used in all scenarios
            lenient().when(householdRepository.findById(householdId)).thenReturn(Optional.of(testHousehold));
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, userId))
                    .thenReturn(Optional.of(testMember));

            // When/Then
            assertThatThrownBy(() -> householdService.changeMemberRole(householdId, memberId, HouseholdRole.ADMIN, token))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("You don't have permission to change member roles");

            verify(householdMemberRepository).findByHouseholdIdAndUserId(householdId, userId);
            verify(householdMemberRepository, never()).findByHouseholdIdAndUserId(householdId, memberId);
            verify(householdMemberRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should not allow changing role to OWNER through this method")
        void shouldNotAllowChangingRoleToOwner() {
            // Given
            UUID memberId = UUID.randomUUID();

            // Remove this stubbing since it's not used
            // when(userService.getCurrentUser(token)).thenReturn(testUser);

            // When/Then
            assertThatThrownBy(() -> householdService.changeMemberRole(householdId, memberId, HouseholdRole.OWNER, token))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Cannot set role to OWNER");

            // The method throws an exception before any interactions with repository, so verify no interactions
            verify(householdMemberRepository, never()).findByHouseholdIdAndUserId(any(), any());
            verify(householdMemberRepository, never()).save(any());
        }
    }
}
