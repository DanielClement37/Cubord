package org.cubord.cubordbackend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cubord.cubordbackend.domain.Household;
import org.cubord.cubordbackend.domain.HouseholdMember;
import org.cubord.cubordbackend.domain.HouseholdRole;
import org.cubord.cubordbackend.domain.User;
import org.cubord.cubordbackend.dto.household.HouseholdRequest;
import org.cubord.cubordbackend.dto.household.HouseholdResponse;
import org.cubord.cubordbackend.exception.*;
import org.cubord.cubordbackend.repository.HouseholdMemberRepository;
import org.cubord.cubordbackend.repository.HouseholdRepository;
import org.cubord.cubordbackend.security.SecurityService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service class for managing households.
 *
 * <p>This service follows the modernized security architecture where:</p>
 * <ul>
 *   <li>Authentication is handled by Spring Security filters</li>
 *   <li>Authorization is declarative via @PreAuthorize annotations</li>
 *   <li>SecurityService provides business-level security context access</li>
 *   <li>No manual token validation or permission checks in business logic</li>
 * </ul>
 *
 * <h2>Authorization Rules</h2>
 * <ul>
 *   <li><strong>Create:</strong> All authenticated users can create households (becoming OWNER)</li>
 *   <li><strong>Read:</strong> Users can only view households where they are members</li>
 *   <li><strong>Update:</strong> Requires an OWNER or ADMIN role in the household</li>
 *   <li><strong>Delete:</strong> Requires an OWNER role in the household</li>
 *   <li><strong>Leave:</strong> Any member can leave (except OWNER)</li>
 *   <li><strong>Transfer Ownership:</strong> Only OWNER can transfer ownership</li>
 *   <li><strong>Manage Roles:</strong> Requires OWNER or ADMIN role</li>
 * </ul>
 *
 * <h2>Business Rules</h2>
 * <ul>
 *   <li>Household names must be unique per user</li>
 *   <li>Each household must have exactly one OWNER</li>
 *   <li>OWNER cannot leave without transferring ownership or deleting the household</li>
 *   <li>Ownership can only be transferred to existing members</li>
 * </ul>
 *
 * @see SecurityService
 * @see org.cubord.cubordbackend.domain.Household
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class HouseholdService {

    private final HouseholdRepository householdRepository;
    private final HouseholdMemberRepository householdMemberRepository;
    private final SecurityService securityService;

    // ==================== Create Operations ====================

    /**
     * Creates a new household with the current user as the owner.
     *
     * <p>Authorization: All authenticated users can create households.</p>
     *
     * <p>The creator automatically becomes the OWNER of the household with full permissions.
     * A corresponding HouseholdMember record is created to establish the membership.</p>
     *
     * @param request DTO containing household information
     * @return HouseholdResponse containing the created household's details
     * @throws ValidationException if the request is null or the household name is invalid
     * @throws ConflictException if a household with the same name already exists for this user
     * @throws DataIntegrityException if household creation fails
     */
    @Transactional
    @PreAuthorize("isAuthenticated()")
    public HouseholdResponse createHousehold(HouseholdRequest request) {
        if (request == null) {
            throw new ValidationException("Household request cannot be null");
        }

        validateHouseholdName(request.getName());

        User currentUser = securityService.getCurrentUser();
        UUID currentUserId = currentUser.getId();
        log.debug("User {} creating household with name: {}", currentUserId, request.getName());

        // Check if a household name already exists for this user
        if (householdRepository.existsByNameAndMembersUserId(request.getName(), currentUserId)) {
            throw new ConflictException("Household with name '" + request.getName() + "' already exists");
        }

        // Create household
        Household household = Household.builder()
                .name(request.getName())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        try {
            Household savedHousehold = householdRepository.save(household);

            // Create owner membership
            HouseholdMember ownerMember = HouseholdMember.builder()
                    .household(savedHousehold)
                    .user(currentUser)
                    .role(HouseholdRole.OWNER)
                    .build();

            householdMemberRepository.save(ownerMember);

            log.info("User {} successfully created household with ID: {}", currentUserId, savedHousehold.getId());
            return mapToHouseholdResponse(savedHousehold);
        } catch (Exception e) {
            log.error("Failed to create household for user: {}", currentUserId, e);
            throw new DataIntegrityException("Failed to create household: " + e.getMessage(), e);
        }
    }

    // ==================== Query Operations ====================

    /**
     * Retrieves a household by its ID.
     *
     * <p>Authorization: User must be a member of the household.</p>
     *
     * @param householdId UUID of the household to retrieve
     * @return HouseholdResponse containing the household's details
     * @throws ValidationException if householdId is null
     * @throws NotFoundException if the household doesn't exist
     * @throws InsufficientPermissionException if the user is not a member
     */
    @Transactional(readOnly = true)
    @PreAuthorize("@security.canAccessHousehold(#householdId)")
    public HouseholdResponse getHouseholdById(UUID householdId) {
        if (householdId == null) {
            throw new ValidationException("Household ID cannot be null");
        }

        UUID currentUserId = securityService.getCurrentUserId();
        log.debug("User {} retrieving household: {}", currentUserId, householdId);

        Household household = householdRepository.findById(householdId)
                .orElseThrow(() -> new NotFoundException("Household not found with ID: " + householdId));

        return mapToHouseholdResponse(household);
    }

    /**
     * Retrieves all households where the current user is a member.
     *
     * <p>Authorization: All authenticated users can list their own households.</p>
     *
     * @return List of HouseholdResponse objects containing household details
     */
    @Transactional(readOnly = true)
    @PreAuthorize("isAuthenticated()")
    public List<HouseholdResponse> getUserHouseholds() {
        UUID currentUserId = securityService.getCurrentUserId();
        log.debug("User {} retrieving their households", currentUserId);

        List<Household> households = householdRepository.findAllByMembersUserId(currentUserId);

        return households.stream()
                .map(this::mapToHouseholdResponse)
                .toList();
    }

    /**
     * Searches for households by name and returns only those the current user is a member of.
     *
     * <p>Authorization: All authenticated users can search their own households.</p>
     *
     * @param searchTerm Term to search for in household names
     * @return List of HouseholdResponse objects matching the search criteria
     * @throws ValidationException if searchTerm is null or empty
     */
    @Transactional(readOnly = true)
    @PreAuthorize("isAuthenticated()")
    public List<HouseholdResponse> searchHouseholds(String searchTerm) {
        if (searchTerm == null || searchTerm.isBlank()) {
            throw new ValidationException("Search term cannot be null or empty");
        }

        UUID currentUserId = securityService.getCurrentUserId();
        log.debug("User {} searching households with term: {}", currentUserId, searchTerm);

        List<Household> households = householdRepository
                .findAllByNameContainingIgnoreCaseAndMembersUserId(searchTerm, currentUserId);

        return households.stream()
                .map(this::mapToHouseholdResponse)
                .toList();
    }

    // ==================== Update Operations ====================

    /**
     * Updates a household's information.
     *
     * <p>Authorization: User must have an OWNER or ADMIN role in the household.</p>
     *
     * @param householdId UUID of the household to update
     * @param request DTO containing updated household information
     * @return HouseholdResponse containing the updated household's details
     * @throws ValidationException if inputs are null or the household name is invalid
     * @throws NotFoundException if the household doesn't exist
     * @throws ConflictException if the new name is already used by another household
     * @throws InsufficientPermissionException if a user lacks modify permission
     * @throws DataIntegrityException if update fails
     */
    @Transactional
    @PreAuthorize("@security.canModifyHousehold(#householdId)")
    public HouseholdResponse updateHousehold(UUID householdId, HouseholdRequest request) {
        if (householdId == null) {
            throw new ValidationException("Household ID cannot be null");
        }
        if (request == null) {
            throw new ValidationException("Update request cannot be null");
        }

        validateHouseholdName(request.getName());

        UUID currentUserId = securityService.getCurrentUserId();
        log.debug("User {} updating household: {}", currentUserId, householdId);

        Household household = householdRepository.findById(householdId)
                .orElseThrow(() -> new NotFoundException("Household not found with ID: " + householdId));

        // Check for name conflicts (excluding the current household)
        if (!household.getName().equals(request.getName()) &&
                householdRepository.existsByNameAndMembersUserId(request.getName(), currentUserId)) {
            throw new ConflictException("Household with name '" + request.getName() + "' already exists");
        }

        household.setName(request.getName());
        household.setUpdatedAt(LocalDateTime.now());

        try {
            Household savedHousehold = householdRepository.save(household);
            log.info("User {} successfully updated household: {}", currentUserId, householdId);
            return mapToHouseholdResponse(savedHousehold);
        } catch (Exception e) {
            log.error("Failed to update household: {}", householdId, e);
            throw new DataIntegrityException("Failed to update household: " + e.getMessage(), e);
        }
    }

    /**
     * Partially updates a household's information.
     *
     * <p>Authorization: User must have an OWNER or ADMIN role in the household.</p>
     *
     * <p>Supported fields: name</p>
     *
     * @param householdId UUID of the household to update
     * @param patchData Map of field names to updated values
     * @return HouseholdResponse containing the updated household's details
     * @throws ValidationException if inputs are null/empty or field values are invalid
     * @throws NotFoundException if the household doesn't exist
     * @throws ConflictException if the new name is already used by another household
     * @throws InsufficientPermissionException if a user lacks modify permission
     * @throws DataIntegrityException if the patch fails
     */
    @Transactional
    @PreAuthorize("@security.canModifyHousehold(#householdId)")
    public HouseholdResponse patchHousehold(UUID householdId, Map<String, Object> patchData) {
        if (householdId == null) {
            throw new ValidationException("Household ID cannot be null");
        }
        if (patchData == null || patchData.isEmpty()) {
            throw new ValidationException("Patch data cannot be null or empty");
        }

        UUID currentUserId = securityService.getCurrentUserId();
        log.debug("User {} patching household: {} with fields: {}",
                currentUserId, householdId, patchData.keySet());

        Household household = householdRepository.findById(householdId)
                .orElseThrow(() -> new NotFoundException("Household not found with ID: " + householdId));

        patchData.forEach((field, value) -> {
            if (field.equals("name")) {
                if (value != null && !value.toString().isBlank()) {
                    String newName = value.toString();
                    validateHouseholdName(newName);

                    // Check for name conflicts (excluding the current household)
                    if (!household.getName().equals(newName) &&
                            householdRepository.existsByNameAndMembersUserId(newName, currentUserId)) {
                        throw new ConflictException("Household with name '" + newName + "' already exists");
                    }

                    household.setName(newName);
                    log.debug("Patched name for household {}", householdId);
                }
            } else {
                log.warn("Attempted to patch unsupported field: {}", field);
                throw new ValidationException("Unsupported field for patching: " + field);
            }
        });

        household.setUpdatedAt(LocalDateTime.now());

        try {
            Household savedHousehold = householdRepository.save(household);
            log.info("User {} successfully patched household: {}", currentUserId, householdId);
            return mapToHouseholdResponse(savedHousehold);
        } catch (ConflictException | ValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to patch household: {}", householdId, e);
            throw new DataIntegrityException("Failed to patch household: " + e.getMessage(), e);
        }
    }

    // ==================== Delete Operations ====================

    /**
     * Deletes a household.
     *
     * <p>Authorization: Only the OWNER can delete the household.</p>
     *
     * <p>Deleting a household will cascade delete all associated members, locations, and pantry items.
     * This operation is irreversible.</p>
     *
     * @param householdId UUID of the household to delete
     * @throws ValidationException if householdId is null
     * @throws NotFoundException if the household doesn't exist
     * @throws InsufficientPermissionException if the user is not the owner
     * @throws DataIntegrityException if deletion fails
     */
    @Transactional
    @PreAuthorize("@security.isHouseholdOwner(#householdId)")
    public void deleteHousehold(UUID householdId) {
        if (householdId == null) {
            throw new ValidationException("Household ID cannot be null");
        }

        UUID currentUserId = securityService.getCurrentUserId();
        log.debug("User {} deleting household: {}", currentUserId, householdId);

        Household household = householdRepository.findById(householdId)
                .orElseThrow(() -> new NotFoundException("Household not found with ID: " + householdId));

        try {
            householdRepository.delete(household);
            log.info("User {} successfully deleted household: {}", currentUserId, householdId);
        } catch (Exception e) {
            log.error("Failed to delete household: {}", householdId, e);
            throw new DataIntegrityException("Failed to delete household: " + e.getMessage(), e);
        }
    }

    // ==================== Member Operations ====================

    /**
     * Allows a user to leave a household.
     *
     * <p>Authorization: User must be a member of the household.</p>
     *
     * <p>The OWNER cannot leave the household. They must either transfer ownership first
     * or delete the household entirely.</p>
     *
     * @param householdId UUID of the household to leave
     * @throws ValidationException if householdId is null
     * @throws NotFoundException if the household doesn't exist or the user is not a member
     * @throws ResourceStateException if the current user is the owner
     * @throws DataIntegrityException if the leave operation fails
     */
    @Transactional
    @PreAuthorize("@security.canAccessHousehold(#householdId)")
    public void leaveHousehold(UUID householdId) {
        if (householdId == null) {
            throw new ValidationException("Household ID cannot be null");
        }

        UUID currentUserId = securityService.getCurrentUserId();
        log.debug("User {} attempting to leave household: {}", currentUserId, householdId);

        HouseholdMember member = householdMemberRepository
                .findByHouseholdIdAndUserId(householdId, currentUserId)
                .orElseThrow(() -> new NotFoundException("User is not a member of this household"));

        if (member.getRole() == HouseholdRole.OWNER) {
            throw new ResourceStateException("Owner cannot leave the household. Transfer ownership or delete the household first.");
        }

        try {
            householdMemberRepository.delete(member);
            log.info("User {} successfully left household: {}", currentUserId, householdId);
        } catch (Exception e) {
            log.error("Failed to remove user {} from household: {}", currentUserId, householdId, e);
            throw new DataIntegrityException("Failed to leave household: " + e.getMessage(), e);
        }
    }

    /**
     * Transfers ownership of a household to another member.
     *
     * <p>Authorization: Only the current OWNER can transfer ownership.</p>
     *
     * <p>The new owner must be an existing member of the household. The current owner
     * will be demoted to an ADMIN role.</p>
     *
     * @param householdId UUID of the household
     * @param newOwnerId UUID of the member to become the new owner
     * @throws ValidationException if householdId or newOwnerId is null
     * @throws NotFoundException if the household or new owner doesn't exist or is not a member
     * @throws InsufficientPermissionException if the current user is not the owner
     * @throws DataIntegrityException if transfer fails
     */
    @Transactional
    @PreAuthorize("@security.isHouseholdOwner(#householdId)")
    public void transferOwnership(UUID householdId, UUID newOwnerId) {
        if (householdId == null) {
            throw new ValidationException("Household ID cannot be null");
        }
        if (newOwnerId == null) {
            throw new ValidationException("New owner ID cannot be null");
        }

        UUID currentUserId = securityService.getCurrentUserId();
        log.debug("User {} transferring ownership of household {} to user {}",
                currentUserId, householdId, newOwnerId);

        // Get current owner membership
        HouseholdMember currentOwner = householdMemberRepository
                .findByHouseholdIdAndUserId(householdId, currentUserId)
                .orElseThrow(() -> new NotFoundException("Current user is not a member of this household"));

        // Get new owner membership
        HouseholdMember newOwner = householdMemberRepository
                .findByHouseholdIdAndUserId(householdId, newOwnerId)
                .orElseThrow(() -> new NotFoundException("New owner is not a member of this household"));

        try {
            // Demote the current owner to admin
            currentOwner.setRole(HouseholdRole.ADMIN);
            householdMemberRepository.save(currentOwner);

            // Promote a new owner
            newOwner.setRole(HouseholdRole.OWNER);
            householdMemberRepository.save(newOwner);

            log.info("User {} successfully transferred ownership of household {} to user {}",
                    currentUserId, householdId, newOwnerId);
        } catch (Exception e) {
            log.error("Failed to transfer ownership of household: {}", householdId, e);
            throw new DataIntegrityException("Failed to transfer ownership: " + e.getMessage(), e);
        }
    }

    /**
     * Changes a member's role within a household.
     *
     * <p>Authorization: User must have an OWNER or ADMIN role in the household.</p>
     *
     * <p>Role change rules:</p>
     * <ul>
     *   <li>Cannot change a role to OWNER (use transferOwnership instead)</li>
     *   <li>ADMIN can change MEMBER roles but not other ADMIN roles</li>
     *   <li>OWNER can change any role except their own</li>
     * </ul>
     *
     * @param householdId UUID of the household
     * @param memberId UUID of the member whose role is to be changed
     * @param newRole New role to assign to the member
     * @throws ValidationException if inputs are null or attempting to set a role to OWNER
     * @throws NotFoundException if the household or member doesn't exist
     * @throws InsufficientPermissionException if a user lacks permission to change roles
     * @throws DataIntegrityException if role change fails
     */
    @Transactional
    @PreAuthorize("@security.canManageHouseholdMembers(#householdId)")
    public void changeMemberRole(UUID householdId, UUID memberId, HouseholdRole newRole) {
        if (householdId == null) {
            throw new ValidationException("Household ID cannot be null");
        }
        if (memberId == null) {
            throw new ValidationException("Member ID cannot be null");
        }
        if (newRole == null) {
            throw new ValidationException("New role cannot be null");
        }
        if (newRole == HouseholdRole.OWNER) {
            throw new ValidationException("Cannot set role to OWNER. Use transferOwnership instead.");
        }

        UUID currentUserId = securityService.getCurrentUserId();
        log.debug("User {} changing role for member {} in household {} to {}",
                currentUserId, memberId, householdId, newRole);

        // Get current user's membership
        HouseholdMember currentUserMember = householdMemberRepository
                .findByHouseholdIdAndUserId(householdId, currentUserId)
                .orElseThrow(() -> new NotFoundException("Current user is not a member of this household"));

        // Get a target member
        HouseholdMember targetMember = householdMemberRepository
                .findByHouseholdIdAndUserId(householdId, memberId)
                .orElseThrow(() -> new NotFoundException("Target user is not a member of this household"));

        // Additional authorization checks
        if (currentUserMember.getRole() == HouseholdRole.ADMIN) {
            // Admins cannot modify other admins or the owner
            if (targetMember.getRole() == HouseholdRole.ADMIN || targetMember.getRole() == HouseholdRole.OWNER) {
                throw new InsufficientPermissionException("Admin cannot modify other admin or owner roles");
            }
        }

        // Cannot change the owner's role through this method
        if (targetMember.getRole() == HouseholdRole.OWNER) {
            throw new ValidationException("Cannot change owner's role. Use transferOwnership instead.");
        }

        try {
            targetMember.setRole(newRole);
            householdMemberRepository.save(targetMember);
            log.info("User {} successfully changed role for member {} in household {} to {}",
                    currentUserId, memberId, householdId, newRole);
        } catch (Exception e) {
            log.error("Failed to change member role in household: {}", householdId, e);
            throw new DataIntegrityException("Failed to change member role: " + e.getMessage(), e);
        }
    }

    // ==================== Helper Methods ====================

    /**
     * Validates household name is not null or empty.
     *
     * @param name Household name to validate
     * @throws ValidationException if the name is null or empty
     */
    private void validateHouseholdName(String name) {
        if (name == null || name.isBlank()) {
            throw new ValidationException("Household name cannot be null or empty");
        }
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