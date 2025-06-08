package org.cubord.cubordbackend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cubord.cubordbackend.domain.Household;
import org.cubord.cubordbackend.domain.HouseholdMember;
import org.cubord.cubordbackend.domain.HouseholdRole;
import org.cubord.cubordbackend.domain.User;
import org.cubord.cubordbackend.dto.HouseholdRequest;
import org.cubord.cubordbackend.dto.HouseholdResponse;
import org.cubord.cubordbackend.exception.ForbiddenException;
import org.cubord.cubordbackend.exception.NotFoundException;
import org.cubord.cubordbackend.repository.HouseholdMemberRepository;
import org.cubord.cubordbackend.repository.HouseholdRepository;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class HouseholdService {

    private final HouseholdRepository householdRepository;
    private final HouseholdMemberRepository householdMemberRepository;
    private final UserService userService;

    /**
     * Creates a new household with the current user as the owner.
     *
     * @param request DTO containing household information
     * @param token JWT authentication token of the current user
     * @return HouseholdResponse containing the created household's details
     * @throws IllegalStateException if a household with the same name already exists
     */
    @Transactional
    public HouseholdResponse createHousehold(HouseholdRequest request, JwtAuthenticationToken token) {
        User currentUser = userService.getCurrentUser(token);

        // Check if a household with this name already exists
        if (householdRepository.existsByName(request.getName())) {
            throw new IllegalStateException("Household with name '" + request.getName() + "' already exists");
        }

        // Create new household
        Household household = new Household();
        household.setName(request.getName());
        household.setCreatedAt(LocalDateTime.now());
        household.setUpdatedAt(LocalDateTime.now());

        // Save household to get ID
        household = householdRepository.save(household);

        // Create household member with OWNER role for current user
        HouseholdMember member = new HouseholdMember();
        member.setHousehold(household);
        member.setUser(currentUser);
        member.setRole(HouseholdRole.OWNER);
        member.setCreatedAt(LocalDateTime.now());
        member.setUpdatedAt(LocalDateTime.now());

        householdMemberRepository.save(member);

        // Convert to response DTO
        return mapToHouseholdResponse(household);
    }

    /**
     * Retrieves a household by its ID if the current user is a member.
     *
     * @param householdId UUID of the household to retrieve
     * @param token JWT authentication token of the current user
     * @return HouseholdResponse containing the household's details
     * @throws NotFoundException if the household doesn't exist
     * @throws ForbiddenException if the current user is not a member of the household
     */
    @Transactional(readOnly = true)
    public HouseholdResponse getHouseholdById(UUID householdId, JwtAuthenticationToken token) {
        User currentUser = userService.getCurrentUser(token);

        // Find household
        Household household = householdRepository.findById(householdId)
                .orElseThrow(() -> new NotFoundException("Household not found"));

        // Check if user is a member
        householdMemberRepository.findByHouseholdIdAndUserId(householdId, currentUser.getId())
                .orElseThrow(() -> new ForbiddenException("You don't have access to this household"));

        // Convert to response DTO
        return mapToHouseholdResponse(household);
    }

    /**
     * Retrieves all households where the current user is a member.
     *
     * @param token JWT authentication token of the current user
     * @return List of HouseholdResponse objects containing household details
     */
    @Transactional(readOnly = true)
    public List<HouseholdResponse> getUserHouseholds(JwtAuthenticationToken token) {
        User currentUser = userService.getCurrentUser(token);

        // Find all households where user is a member
        List<Household> households = householdRepository.findByMembersUserId(currentUser.getId());

        // Convert to response DTOs
        return households.stream()
                .map(this::mapToHouseholdResponse)
                .collect(Collectors.toList());
    }

    /**
     * Updates a household's information if the current user has appropriate permissions.
     *
     * @param householdId UUID of the household to update
     * @param request DTO containing updated household information
     * @param token JWT authentication token of the current user
     * @return HouseholdResponse containing the updated household's details
     * @throws NotFoundException if the household doesn't exist
     * @throws ForbiddenException if the current user lacks permission to update the household
     * @throws IllegalStateException if the new name is already used by another household
     */
    @Transactional
    public HouseholdResponse updateHousehold(UUID householdId, HouseholdRequest request, JwtAuthenticationToken token) {
        User currentUser = userService.getCurrentUser(token);

        // Find household
        Household household = householdRepository.findById(householdId)
                .orElseThrow(() -> new NotFoundException("Household not found"));

        // Check if user is a member with appropriate permissions
        HouseholdMember member = householdMemberRepository.findByHouseholdIdAndUserId(householdId, currentUser.getId())
                .orElseThrow(() -> new ForbiddenException("You don't have access to this household"));

        // Check if user has permissions to update
        if (member.getRole() != HouseholdRole.OWNER && member.getRole() != HouseholdRole.ADMIN) {
            throw new ForbiddenException("You don't have permission to update this household");
        }

        // Check if name is already used by a different household
        Optional<Household> existingHousehold = householdRepository.findByName(request.getName());
        if (existingHousehold.isPresent() && !existingHousehold.get().getId().equals(householdId)) {
            throw new IllegalStateException("Household with name '" + request.getName() + "' already exists");
        }

        // Update household
        household.setName(request.getName());
        household.setUpdatedAt(LocalDateTime.now());

        household = householdRepository.save(household);

        // Convert to response DTO
        return mapToHouseholdResponse(household);
    }

    /**
     * Deletes a household if the current user is the owner.
     *
     * @param householdId UUID of the household to delete
     * @param token JWT authentication token of the current user
     * @throws NotFoundException if the household doesn't exist
     * @throws ForbiddenException if the current user is not the owner of the household
     */
    @Transactional
    public void deleteHousehold(UUID householdId, JwtAuthenticationToken token) {
        User currentUser = userService.getCurrentUser(token);

        // Find household
        Household household = householdRepository.findById(householdId)
                .orElseThrow(() -> new NotFoundException("Household not found"));

        // Check if user is the owner
        HouseholdMember member = householdMemberRepository.findByHouseholdIdAndUserId(householdId, currentUser.getId())
                .orElseThrow(() -> new ForbiddenException("You don't have access to this household"));

        if (member.getRole() != HouseholdRole.OWNER) {
            throw new ForbiddenException("Only the owner can delete a household");
        }

        // Delete household
        householdRepository.delete(household);
    }

    /**
     * Allows a user to leave a household unless they are the owner.
     *
     * @param householdId UUID of the household to leave
     * @param token JWT authentication token of the current user
     * @throws NotFoundException if the household doesn't exist
     * @throws ForbiddenException if the current user is not a member of the household
     * @throws IllegalStateException if the current user is the owner of the household
     */
    @Transactional
    public void leaveHousehold(UUID householdId, JwtAuthenticationToken token) {
        User currentUser = userService.getCurrentUser(token);

        // Find household
        Household household = householdRepository.findById(householdId)
                .orElseThrow(() -> new NotFoundException("Household not found"));

        // Check if user is a member
        HouseholdMember member = householdMemberRepository.findByHouseholdIdAndUserId(householdId, currentUser.getId())
                .orElseThrow(() -> new ForbiddenException("You are not a member of this household"));

        // Owners cannot leave, they must transfer ownership first
        if (member.getRole() == HouseholdRole.OWNER) {
            throw new IllegalStateException("Owners cannot leave a household. Transfer ownership first.");
        }

        // Remove the member
        householdMemberRepository.delete(member);
    }

    /**
     * Transfers ownership of a household to another member.
     *
     * @param householdId UUID of the household
     * @param newOwnerId UUID of the member to become the new owner
     * @param token JWT authentication token of the current user
     * @throws NotFoundException if the household or new owner doesn't exist
     * @throws ForbiddenException if the current user is not the owner of the household
     */
    @Transactional
    public void transferOwnership(UUID householdId, UUID newOwnerId, JwtAuthenticationToken token) {
        User currentUser = userService.getCurrentUser(token);

        // Find household
        Household household = householdRepository.findById(householdId)
                .orElseThrow(() -> new NotFoundException("Household not found"));

        // Check if current user is the owner
        HouseholdMember currentMember = householdMemberRepository.findByHouseholdIdAndUserId(householdId, currentUser.getId())
                .orElseThrow(() -> new ForbiddenException("You don't have access to this household"));

        if (currentMember.getRole() != HouseholdRole.OWNER) {
            throw new ForbiddenException("Only the owner can transfer ownership");
        }

        // Check if new owner is a member
        HouseholdMember newOwnerMember = householdMemberRepository.findByHouseholdIdAndUserId(householdId, newOwnerId)
                .orElseThrow(() -> new NotFoundException("New owner is not a member of this household"));

        // Update roles
        currentMember.setRole(HouseholdRole.ADMIN);
        newOwnerMember.setRole(HouseholdRole.OWNER);

        // Save changes
        householdMemberRepository.save(currentMember);
        householdMemberRepository.save(newOwnerMember);
    }

    /**
     * Searches for households by name and returns only those the current user is a member of.
     *
     * @param searchTerm Term to search for in household names
     * @param token JWT authentication token of the current user
     * @return List of HouseholdResponse objects matching the search criteria
     */
    @Transactional(readOnly = true)
    public List<HouseholdResponse> searchHouseholds(String searchTerm, JwtAuthenticationToken token) {
        User currentUser = userService.getCurrentUser(token);

        // Find households matching search term
        List<Household> households = householdRepository.findByNameContainingIgnoreCase(searchTerm);

        // Filter to only include households where user is a member
        return households.stream()
                .filter(household -> householdMemberRepository.findByHouseholdIdAndUserId(
                        household.getId(), currentUser.getId()).isPresent())
                .map(this::mapToHouseholdResponse)
                .collect(Collectors.toList());
    }

    /**
     * Changes a member's role within a household if the current user has appropriate permissions.
     *
     * @param householdId UUID of the household
     * @param memberId UUID of the member whose role is to be changed
     * @param role New role to assign to the member
     * @param token JWT authentication token of the current user
     * @throws NotFoundException if the household or member doesn't exist
     * @throws ForbiddenException if the current user lacks permission to change roles
     * @throws IllegalArgumentException if attempting to set role to OWNER through this method
     */
    @Transactional
    public void changeMemberRole(UUID householdId, UUID memberId, HouseholdRole role, JwtAuthenticationToken token) {
        if (role == HouseholdRole.OWNER) {
            throw new IllegalArgumentException("Cannot set role to OWNER. Use transferOwnership method instead.");
        }

        User currentUser = userService.getCurrentUser(token);

        // Find household
        Household household = householdRepository.findById(householdId)
                .orElseThrow(() -> new NotFoundException("Household not found"));

        // Check if current user is a member with appropriate permissions
        HouseholdMember currentMember = householdMemberRepository.findByHouseholdIdAndUserId(householdId, currentUser.getId())
                .orElseThrow(() -> new ForbiddenException("You don't have access to this household"));

        if (currentMember.getRole() != HouseholdRole.OWNER && currentMember.getRole() != HouseholdRole.ADMIN) {
            throw new ForbiddenException("You don't have permission to change member roles");
        }

        // Find target member
        HouseholdMember targetMember = householdMemberRepository.findByHouseholdIdAndUserId(householdId, memberId)
                .orElseThrow(() -> new NotFoundException("Member not found in this household"));

        // Admins can't change owner or other admin roles
        if (currentMember.getRole() == HouseholdRole.ADMIN &&
                (targetMember.getRole() == HouseholdRole.OWNER || targetMember.getRole() == HouseholdRole.ADMIN)) {
            throw new ForbiddenException("Only an owner can change the role of another owner or admin");
        }

        // Update role
        targetMember.setRole(role);
        targetMember.setUpdatedAt(LocalDateTime.now());

        householdMemberRepository.save(targetMember);
    }

    /**
     * Maps a Household entity to a HouseholdResponse DTO.
     *
     * @param household Household entity to map
     * @return HouseholdResponse containing the household's details
     */
    private HouseholdResponse mapToHouseholdResponse(Household household) {
        return HouseholdResponse.builder()
                .id(household.getId())
                .name(household.getName())
                .createdAt(household.getCreatedAt())
                .updatedAt(household.getUpdatedAt())
                .build();
    }
}