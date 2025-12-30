package org.cubord.cubordbackend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service class for managing household members.
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
 *   <li><strong>Add Member:</strong> Requires an OWNER or ADMIN role in the household</li>
 *   <li><strong>View Members:</strong> Any household member can view all members</li>
 *   <li><strong>Remove Member:</strong> Requires an OWNER or ADMIN role (with restrictions)</li>
 *   <li><strong>Update Role:</strong> Requires an OWNER or ADMIN role (with restrictions)</li>
 * </ul>
 *
 * <h2>Business Rules</h2>
 * <ul>
 *   <li>OWNER role cannot be set through member management (use ownership transfer)</li>
 *   <li>Owner cannot be removed from the household</li>
 *   <li>Admin cannot remove or modify another admin's role</li>
 *   <li>Users cannot be added if already a member</li>
 * </ul>
 *
 * @see SecurityService
 * @see org.cubord.cubordbackend.domain.HouseholdMember
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class HouseholdMemberService {

    private final HouseholdRepository householdRepository;
    private final UserRepository userRepository;
    private final HouseholdMemberRepository householdMemberRepository;
    private final SecurityService securityService;

    // ==================== Create Operations ====================

    /**
     * Adds a new member to a household.
     *
     * <p>Authorization: User must have an OWNER or ADMIN role in the household.</p>
     *
     * @param householdId ID of the household to add member to
     * @param request Details of the member to add
     * @return HouseholdMemberResponse containing details of the added member
     * @throws ValidationException if request or householdId is null, or a role is OWNER
     * @throws NotFoundException if the household or user is not found
     * @throws ConflictException if the user is already a member
     * @throws InsufficientPermissionException if the current user lacks permission (via @PreAuthorize)
     */
    @Transactional
    @PreAuthorize("@security.canManageHouseholdMembers(#householdId)")
    public HouseholdMemberResponse addMemberToHousehold(UUID householdId, HouseholdMemberRequest request) {
        if (householdId == null) {
            throw new ValidationException("Household ID cannot be null");
        }
        if (request == null) {
            throw new ValidationException("Household member request cannot be null");
        }
        if (request.getUserId() == null) {
            throw new ValidationException("User ID cannot be null");
        }
        if (request.getRole() == null) {
            throw new ValidationException("Role cannot be null");
        }
        if (request.getRole() == HouseholdRole.OWNER) {
            throw new ValidationException("Cannot set role to OWNER");
        }

        UUID currentUserId = securityService.getCurrentUserId();
        log.debug("User {} attempting to add member to household {}", currentUserId, householdId);

        // Verify household exists
        Household household = householdRepository.findById(householdId)
                .orElseThrow(() -> new NotFoundException("Household not found"));

        // Verify user to add exists
        User userToAdd = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new NotFoundException("User not found"));

        // Check if a user is already a member
        if (householdMemberRepository.existsByHouseholdIdAndUserId(householdId, userToAdd.getId())) {
            throw new ConflictException("User is already a member of this household");
        }

        // Create a new member
        HouseholdMember member = HouseholdMember.builder()
                .household(household)
                .user(userToAdd)
                .role(request.getRole())
                .build();

        member = householdMemberRepository.save(member);
        log.info("User {} successfully added user {} as {} to household {}",
                currentUserId, userToAdd.getId(), request.getRole(), householdId);

        return mapToResponse(member);
    }

    /**
     * Adds a new member to a household.
     *
     * @deprecated Use {@link #addMemberToHousehold(UUID, HouseholdMemberRequest)} instead.
     *             This method is maintained for backward compatibility with controllers
     *             that haven't been migrated to the new security architecture.
     *             Token-based authentication is now handled by Spring Security filters.
     *
     * @param householdId ID of the household to add member to
     * @param request Details of the member to add
     * @param token JWT token of the user performing the action (ignored in favor of SecurityContext)
     * @return HouseholdMemberResponse containing details of the added member
     * @throws ValidationException if request or householdId is null, or a role is OWNER
     * @throws NotFoundException if the household or user is not found
     * @throws ConflictException if the user is already a member
     * @throws InsufficientPermissionException if the current user lacks permission
     */
    @Deprecated(since = "2.0", forRemoval = true)
    @Transactional
    public HouseholdMemberResponse addMemberToHousehold(UUID householdId, HouseholdMemberRequest request,
                                                        JwtAuthenticationToken token) {
        log.warn("DEPRECATED: addMemberToHousehold(UUID, HouseholdMemberRequest, JwtAuthenticationToken) called. " +
                "Migrate to addMemberToHousehold(UUID, HouseholdMemberRequest) for improved security architecture. " +
                "The token parameter is ignored - using SecurityContext instead.");

        // Delegate to the modern method
        return addMemberToHousehold(householdId, request);
    }

    // ==================== Query Operations ====================

    /**
     * Retrieves all members of a household.
     *
     * <p>Authorization: User must be a member of the household.</p>
     *
     * @param householdId ID of the household to get members for
     * @return List of HouseholdMemberResponse objects representing household members
     * @throws ValidationException if householdId is null
     * @throws InsufficientPermissionException if the user is not a member (via @PreAuthorize)
     */
    @Transactional(readOnly = true)
    @PreAuthorize("@security.canAccessHousehold(#householdId)")
    public List<HouseholdMemberResponse> getHouseholdMembers(UUID householdId) {
        if (householdId == null) {
            throw new ValidationException("Household ID cannot be null");
        }

        UUID currentUserId = securityService.getCurrentUserId();
        log.debug("User {} retrieving members for household {}", currentUserId, householdId);

        List<HouseholdMember> members = householdMemberRepository.findByHouseholdId(householdId);

        return members.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Retrieves all members of a household.
     *
     * @deprecated Use {@link #getHouseholdMembers(UUID)} instead.
     *             This method is maintained for backward compatibility with controllers
     *             that haven't been migrated to the new security architecture.
     *             Token-based authentication is now handled by Spring Security filters.
     *
     * @param householdId ID of the household to get members for
     * @param token JWT token of the user performing the action (ignored in favor of SecurityContext)
     * @return List of HouseholdMemberResponse objects representing household members
     * @throws ValidationException if householdId is null
     * @throws InsufficientPermissionException if the user is not a member
     */
    @Deprecated(since = "2.0", forRemoval = true)
    @Transactional(readOnly = true)
    public List<HouseholdMemberResponse> getHouseholdMembers(UUID householdId, JwtAuthenticationToken token) {
        log.warn("DEPRECATED: getHouseholdMembers(UUID, JwtAuthenticationToken) called. " +
                "Migrate to getHouseholdMembers(UUID) for improved security architecture. " +
                "The token parameter is ignored - using SecurityContext instead.");

        // Delegate to the modern method
        return getHouseholdMembers(householdId);
    }

    /**
     * Retrieves a specific member of a household by ID.
     *
     * <p>Authorization: User must be a member of the household.</p>
     *
     * @param householdId ID of the household
     * @param memberId ID of the member to retrieve
     * @return HouseholdMemberResponse containing member details
     * @throws ValidationException if householdId or memberId is null
     * @throws NotFoundException if the member is not found or not from the specified household
     * @throws InsufficientPermissionException if the user is not a member (via @PreAuthorize)
     */
    @Transactional(readOnly = true)
    @PreAuthorize("@security.canAccessHousehold(#householdId)")
    public HouseholdMemberResponse getMemberById(UUID householdId, UUID memberId) {
        if (householdId == null) {
            throw new ValidationException("Household ID cannot be null");
        }
        if (memberId == null) {
            throw new ValidationException("Member ID cannot be null");
        }

        UUID currentUserId = securityService.getCurrentUserId();
        log.debug("User {} retrieving member {} from household {}", currentUserId, memberId, householdId);

        HouseholdMember member = householdMemberRepository.findById(memberId)
                .orElseThrow(() -> new NotFoundException("Member not found"));

        // Verify a member belongs to the specified household
        if (!member.getHousehold().getId().equals(householdId)) {
            throw new NotFoundException("Member is not from the specified household");
        }

        return mapToResponse(member);
    }

    /**
     * Retrieves a specific member of a household by ID.
     *
     * @deprecated Use {@link #getMemberById(UUID, UUID)} instead.
     *             This method is maintained for backward compatibility with controllers
     *             that haven't been migrated to the new security architecture.
     *             Token-based authentication is now handled by Spring Security filters.
     *
     * @param householdId ID of the household
     * @param memberId ID of the member to retrieve
     * @param token JWT token of the user performing the action (ignored in favor of SecurityContext)
     * @return HouseholdMemberResponse containing member details
     * @throws ValidationException if householdId or memberId is null
     * @throws NotFoundException if the member is not found or not from the specified household
     * @throws InsufficientPermissionException if the user is not a member
     */
    @Deprecated(since = "2.0", forRemoval = true)
    @Transactional(readOnly = true)
    public HouseholdMemberResponse getMemberById(UUID householdId, UUID memberId, JwtAuthenticationToken token) {
        log.warn("DEPRECATED: getMemberById(UUID, UUID, JwtAuthenticationToken) called. " +
                "Migrate to getMemberById(UUID, UUID) for improved security architecture. " +
                "The token parameter is ignored - using SecurityContext instead.");

        // Delegate to the modern method
        return getMemberById(householdId, memberId);
    }

    // ==================== Delete Operations ====================

    /**
     * Removes a member from a household.
     *
     * <p>Authorization: User must have an OWNER or ADMIN role in the household.</p>
     *
     * <p>Business rules:</p>
     * <ul>
     *   <li>Cannot remove the OWNER</li>
     *   <li>ADMIN cannot remove another ADMIN</li>
     * </ul>
     *
     * @param householdId ID of the household
     * @param memberId ID of the member to remove
     * @throws ValidationException if householdId or memberId is null
     * @throws NotFoundException if the member is not found or not from the specified household
     * @throws InsufficientPermissionException if the current user lacks permission (via @PreAuthorize or business rules)
     * @throws ResourceStateException if attempting to remove the owner
     */
    @Transactional
    @PreAuthorize("@security.canManageHouseholdMembers(#householdId)")
    public void removeMember(UUID householdId, UUID memberId) {
        if (householdId == null) {
            throw new ValidationException("Household ID cannot be null");
        }
        if (memberId == null) {
            throw new ValidationException("Member ID cannot be null");
        }

        UUID currentUserId = securityService.getCurrentUserId();
        log.debug("User {} attempting to remove member {} from household {}",
                currentUserId, memberId, householdId);

        // Get the current user's membership to check detailed permissions
        HouseholdMember currentMember = householdMemberRepository.findByHouseholdIdAndUserId(householdId, currentUserId)
                .orElseThrow(() -> new NotFoundException("You are not a member of this household"));

        // Get the member to remove
        HouseholdMember memberToRemove = householdMemberRepository.findById(memberId)
                .orElseThrow(() -> new NotFoundException("Member not found"));

        // Verify a member belongs to the specified household
        if (!memberToRemove.getHousehold().getId().equals(householdId)) {
            throw new NotFoundException("Member is not from the specified household");
        }

        // Cannot remove the owner
        if (memberToRemove.getRole() == HouseholdRole.OWNER) {
            throw new ResourceStateException("Cannot remove the owner from the household");
        }

        // Admin cannot remove another admin
        if (currentMember.getRole() == HouseholdRole.ADMIN && memberToRemove.getRole() == HouseholdRole.ADMIN) {
            log.warn("Admin {} attempted to remove another admin from household {}",
                    currentUserId, householdId);
            throw new InsufficientPermissionException("Admin cannot remove another admin");
        }

        householdMemberRepository.delete(memberToRemove);
        log.info("User {} successfully removed member {} from household {}",
                currentUserId, memberId, householdId);
    }

    /**
     * Removes a member from a household.
     *
     * @deprecated Use {@link #removeMember(UUID, UUID)} instead.
     *             This method is maintained for backward compatibility with controllers
     *             that haven't been migrated to the new security architecture.
     *             Token-based authentication is now handled by Spring Security filters.
     *
     * @param householdId ID of the household
     * @param memberId ID of the member to remove
     * @param token JWT token of the user performing the action (ignored in favor of SecurityContext)
     * @throws ValidationException if householdId or memberId is null
     * @throws NotFoundException if the member is not found or not from the specified household
     * @throws InsufficientPermissionException if the current user lacks permission
     * @throws ResourceStateException if attempting to remove the owner
     */
    @Deprecated(since = "2.0", forRemoval = true)
    @Transactional
    public void removeMember(UUID householdId, UUID memberId, JwtAuthenticationToken token) {
        log.warn("DEPRECATED: removeMember(UUID, UUID, JwtAuthenticationToken) called. " +
                "Migrate to removeMember(UUID, UUID) for improved security architecture. " +
                "The token parameter is ignored - using SecurityContext instead.");

        // Delegate to the modern method
        removeMember(householdId, memberId);
    }

    // ==================== Update Operations ====================

    /**
     * Updates a member's role within a household.
     *
     * <p>Authorization: User must have an OWNER or ADMIN role in the household.</p>
     *
     * <p>Business rules:</p>
     * <ul>
     *   <li>Cannot set a role to OWNER (use ownership transfer instead)</li>
     *   <li>ADMIN cannot update another ADMIN's role</li>
     * </ul>
     *
     * @param householdId ID of the household
     * @param memberId ID of the member whose role is to be updated
     * @param role New role to assign to the member
     * @return HouseholdMemberResponse containing updated member details
     * @throws ValidationException if householdId, memberId, or role is null, or a role is OWNER
     * @throws NotFoundException if the member is not found or not from the specified household
     * @throws InsufficientPermissionException if the current user lacks permission (via @PreAuthorize or business rules)
     */
    @Transactional
    @PreAuthorize("@security.canManageHouseholdMembers(#householdId)")
    public HouseholdMemberResponse updateMemberRole(UUID householdId, UUID memberId, HouseholdRole role) {
        if (householdId == null) {
            throw new ValidationException("Household ID cannot be null");
        }
        if (memberId == null) {
            throw new ValidationException("Member ID cannot be null");
        }
        if (role == null) {
            throw new ValidationException("Role cannot be null");
        }
        if (role == HouseholdRole.OWNER) {
            throw new ValidationException("Cannot set role to OWNER");
        }

        UUID currentUserId = securityService.getCurrentUserId();
        log.debug("User {} attempting to update role for member {} in household {}",
                currentUserId, memberId, householdId);

        // Get the current user's membership to check detailed permissions
        HouseholdMember currentMember = householdMemberRepository.findByHouseholdIdAndUserId(householdId, currentUserId)
                .orElseThrow(() -> new NotFoundException("You are not a member of this household"));

        // Get the member to update
        HouseholdMember memberToUpdate = householdMemberRepository.findById(memberId)
                .orElseThrow(() -> new NotFoundException("Member not found"));

        // Verify a member belongs to the specified household
        if (!memberToUpdate.getHousehold().getId().equals(householdId)) {
            throw new NotFoundException("Member is not from the specified household");
        }

        // Admin cannot update another admin's role
        if (currentMember.getRole() == HouseholdRole.ADMIN && memberToUpdate.getRole() == HouseholdRole.ADMIN) {
            log.warn("Admin {} attempted to update another admin's role in household {}",
                    currentUserId, householdId);
            throw new InsufficientPermissionException("Admin cannot update another admin's role");
        }

        memberToUpdate.setRole(role);
        memberToUpdate.setUpdatedAt(LocalDateTime.now());

        memberToUpdate = householdMemberRepository.save(memberToUpdate);
        log.info("User {} successfully updated role for member {} to {} in household {}",
                currentUserId, memberId, role, householdId);

        return mapToResponse(memberToUpdate);
    }

    /**
     * Updates a member's role within a household.
     *
     * @deprecated Use {@link #updateMemberRole(UUID, UUID, HouseholdRole)} instead.
     *             This method is maintained for backward compatibility with controllers
     *             that haven't been migrated to the new security architecture.
     *             Token-based authentication is now handled by Spring Security filters.
     *
     * @param householdId ID of the household
     * @param memberId ID of the member whose role is to be updated
     * @param role New role to assign to the member
     * @param token JWT token of the user performing the action (ignored in favor of SecurityContext)
     * @return HouseholdMemberResponse containing updated member details
     * @throws ValidationException if householdId, memberId, or role is null, or a role is OWNER
     * @throws NotFoundException if the member is not found or not from the specified household
     * @throws InsufficientPermissionException if the current user lacks permission
     */
    @Deprecated(since = "2.0", forRemoval = true)
    @Transactional
    public HouseholdMemberResponse updateMemberRole(UUID householdId, UUID memberId, HouseholdRole role,
                                                    JwtAuthenticationToken token) {
        log.warn("DEPRECATED: updateMemberRole(UUID, UUID, HouseholdRole, JwtAuthenticationToken) called. " +
                "Migrate to updateMemberRole(UUID, UUID, HouseholdRole) for improved security architecture. " +
                "The token parameter is ignored - using SecurityContext instead.");

        // Delegate to the modern method
        return updateMemberRole(householdId, memberId, role);
    }

    // ==================== Mapping Methods ====================

    /**
     * Maps a HouseholdMember entity to a HouseholdMemberResponse DTO.
     *
     * @param member HouseholdMember entity to map
     * @return HouseholdMemberResponse containing member details
     */
    private HouseholdMemberResponse mapToResponse(HouseholdMember member) {
        return HouseholdMemberResponse.builder()
                .id(member.getId())
                .userId(member.getUser().getId())
                .username(member.getUser().getUsername())
                .householdId(member.getHousehold().getId())
                .householdName(member.getHousehold().getName())
                .role(member.getRole())
                .createdAt(member.getCreatedAt())
                .build();
    }
}