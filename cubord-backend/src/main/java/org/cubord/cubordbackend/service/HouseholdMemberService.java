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
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for managing household members.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class HouseholdMemberService {

    private final HouseholdRepository householdRepository;
    private final UserRepository userRepository;
    private final HouseholdMemberRepository householdMemberRepository;
    private final UserService userService;

    /**
     * Adds a new member to a household.
     *
     * @param householdId ID of the household to add member to
     * @param request Details of the member to add
     * @param token JWT token of the user performing the action
     * @return HouseholdMemberResponse containing details of the added member
     * @throws ValidationException if request, token, or householdId is null
     * @throws NotFoundException if the household or user is not found
     * @throws InsufficientPermissionException if the current user lacks permission
     * @throws ConflictException if the user is already a member
     */
    @Transactional
    public HouseholdMemberResponse addMemberToHousehold(UUID householdId, HouseholdMemberRequest request,
                                                        JwtAuthenticationToken token) {
        // Validate inputs
        if (request == null) {
            throw new ValidationException("Household member request cannot be null");
        }
        if (token == null) {
            throw new ValidationException("Authentication token cannot be null");
        }
        if (householdId == null) {
            throw new ValidationException("Household ID cannot be null");
        }

        User currentUser = userService.getCurrentUser(token);
        log.debug("User {} attempting to add member to household {}", currentUser.getId(), householdId);

        // Check if household exists
        Household household = householdRepository.findById(householdId)
                .orElseThrow(() -> new NotFoundException("Household not found"));

        // Check if current user is a member with appropriate permissions
        HouseholdMember currentMember = householdMemberRepository.findByHouseholdIdAndUserId(householdId, currentUser.getId())
                .orElseThrow(() -> new NotFoundException("You are not a member of this household"));

        if (currentMember.getRole() != HouseholdRole.OWNER && currentMember.getRole() != HouseholdRole.ADMIN) {
            log.warn("User {} attempted to add member without permission to household {}",
                    currentUser.getId(), householdId);
            throw new InsufficientPermissionException("You don't have permission to add members to this household");
        }

        // Check if user to add exists
        User userToAdd = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new NotFoundException("User not found"));

        // Check if user is already a member
        if (householdMemberRepository.existsByHouseholdIdAndUserId(householdId, userToAdd.getId())) {
            throw new ConflictException("User is already a member of this household");
        }

        // Cannot set role to OWNER through this method
        if (request.getRole() == HouseholdRole.OWNER) {
            throw new ValidationException("Cannot set role to OWNER");
        }

        // Create new member
        HouseholdMember member = new HouseholdMember();
        member.setId(UUID.randomUUID());
        member.setHousehold(household);
        member.setUser(userToAdd);
        member.setRole(request.getRole());
        member.setCreatedAt(LocalDateTime.now());
        member.setUpdatedAt(LocalDateTime.now());

        member = householdMemberRepository.save(member);
        log.info("User {} successfully added user {} as {} to household {}",
                currentUser.getId(), userToAdd.getId(), request.getRole(), householdId);

        return mapToResponse(member);
    }

    /**
     * Retrieves all members of a household.
     *
     * @param householdId ID of the household to get members for
     * @param token JWT token of the user performing the action
     * @return List of HouseholdMemberResponse objects representing household members
     * @throws ValidationException if token or householdId is null
     * @throws NotFoundException if the household is not found or user is not a member
     */
    @Transactional(readOnly = true)
    public List<HouseholdMemberResponse> getHouseholdMembers(UUID householdId, JwtAuthenticationToken token) {
        // Validate inputs
        if (token == null) {
            throw new ValidationException("Authentication token cannot be null");
        }
        if (householdId == null) {
            throw new ValidationException("Household ID cannot be null");
        }

        User currentUser = userService.getCurrentUser(token);
        log.debug("User {} retrieving members for household {}", currentUser.getId(), householdId);

        // Check if household exists
        Household household = householdRepository.findById(householdId)
                .orElseThrow(() -> new NotFoundException("Household not found"));

        // Check if current user is a member
        householdMemberRepository.findByHouseholdIdAndUserId(householdId, currentUser.getId())
                .orElseThrow(() -> new NotFoundException("You are not a member of this household"));

        // Get all members
        List<HouseholdMember> members = householdMemberRepository.findByHouseholdId(householdId);

        // Map to response DTOs
        return members.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Retrieves a specific member of a household by ID.
     *
     * @param householdId ID of the household
     * @param memberId ID of the member to retrieve
     * @param token JWT token of the user performing the action
     * @return HouseholdMemberResponse containing member details
     * @throws ValidationException if token, householdId, or memberId is null
     * @throws NotFoundException if the household, member, or current user is not found
     */
    @Transactional(readOnly = true)
    public HouseholdMemberResponse getMemberById(UUID householdId, UUID memberId, JwtAuthenticationToken token) {
        // Validate inputs
        if (token == null) {
            throw new ValidationException("Authentication token cannot be null");
        }
        if (householdId == null) {
            throw new ValidationException("Household ID cannot be null");
        }
        if (memberId == null) {
            throw new ValidationException("Member ID cannot be null");
        }

        User currentUser = userService.getCurrentUser(token);
        log.debug("User {} retrieving member {} from household {}", currentUser.getId(), memberId, householdId);

        // Check if household exists
        Household household = householdRepository.findById(householdId)
                .orElseThrow(() -> new NotFoundException("Household not found"));

        // Check if current user is a member
        householdMemberRepository.findByHouseholdIdAndUserId(householdId, currentUser.getId())
                .orElseThrow(() -> new NotFoundException("You are not a member of this household"));

        // Get the requested member
        HouseholdMember member = householdMemberRepository.findById(memberId)
                .orElseThrow(() -> new NotFoundException("Member not found"));

        // Check if member is from the specified household
        if (!member.getHousehold().getId().equals(householdId)) {
            throw new NotFoundException("Member is not from the specified household");
        }

        return mapToResponse(member);
    }

    /**
     * Removes a member from a household.
     *
     * @param householdId ID of the household
     * @param memberId ID of the member to remove
     * @param token JWT token of the user performing the action
     * @throws ValidationException if token, householdId, or memberId is null
     * @throws NotFoundException if the household or member is not found
     * @throws InsufficientPermissionException if the current user lacks permission
     * @throws ResourceStateException if attempting to remove the owner
     */
    @Transactional
    public void removeMember(UUID householdId, UUID memberId, JwtAuthenticationToken token) {
        // Validate inputs
        if (token == null) {
            throw new ValidationException("Authentication token cannot be null");
        }
        if (householdId == null) {
            throw new ValidationException("Household ID cannot be null");
        }
        if (memberId == null) {
            throw new ValidationException("Member ID cannot be null");
        }

        User currentUser = userService.getCurrentUser(token);
        log.debug("User {} attempting to remove member {} from household {}",
                currentUser.getId(), memberId, householdId);

        // Check if household exists
        Household household = householdRepository.findById(householdId)
                .orElseThrow(() -> new NotFoundException("Household not found"));

        // Check if current user is a member with appropriate permissions
        HouseholdMember currentMember = householdMemberRepository.findByHouseholdIdAndUserId(householdId, currentUser.getId())
                .orElseThrow(() -> new NotFoundException("You are not a member of this household"));

        // Check permissions first - before looking up the member to remove
        if (currentMember.getRole() != HouseholdRole.OWNER && currentMember.getRole() != HouseholdRole.ADMIN) {
            log.warn("User {} attempted to remove member without permission from household {}",
                    currentUser.getId(), householdId);
            throw new InsufficientPermissionException("You don't have permission to remove members from this household");
        }

        // Get the member to remove
        HouseholdMember memberToRemove = householdMemberRepository.findById(memberId)
                .orElseThrow(() -> new NotFoundException("Member not found"));

        // Check if member is from the specified household
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
                    currentUser.getId(), householdId);
            throw new InsufficientPermissionException("Admin cannot remove another admin");
        }

        // Remove the member
        householdMemberRepository.delete(memberToRemove);
        log.info("User {} successfully removed member {} from household {}",
                currentUser.getId(), memberId, householdId);
    }

    /**
     * Updates a member's role within a household.
     *
     * @param householdId ID of the household
     * @param memberId ID of the member whose role is to be updated
     * @param role New role to assign to the member
     * @param token JWT token of the user performing the action
     * @return HouseholdMemberResponse containing updated member details
     * @throws ValidationException if token, householdId, memberId, or role is null, or attempting to set OWNER role
     * @throws NotFoundException if the household or member is not found
     * @throws InsufficientPermissionException if the current user lacks permission
     */
    @Transactional
    public HouseholdMemberResponse updateMemberRole(UUID householdId, UUID memberId, HouseholdRole role,
                                                    JwtAuthenticationToken token) {
        // Validate inputs
        if (token == null) {
            throw new ValidationException("Authentication token cannot be null");
        }
        if (householdId == null) {
            throw new ValidationException("Household ID cannot be null");
        }
        if (memberId == null) {
            throw new ValidationException("Member ID cannot be null");
        }
        if (role == null) {
            throw new ValidationException("Role cannot be null");
        }

        // Cannot set role to OWNER
        if (role == HouseholdRole.OWNER) {
            throw new ValidationException("Cannot set role to OWNER");
        }

        User currentUser = userService.getCurrentUser(token);
        log.debug("User {} attempting to update role for member {} in household {}",
                currentUser.getId(), memberId, householdId);

        // Check if household exists
        Household household = householdRepository.findById(householdId)
                .orElseThrow(() -> new NotFoundException("Household not found"));

        // Check if current user is a member with appropriate permissions
        HouseholdMember currentMember = householdMemberRepository.findByHouseholdIdAndUserId(householdId, currentUser.getId())
                .orElseThrow(() -> new NotFoundException("You are not a member of this household"));

        // Check permissions first - before looking up the member to update
        if (currentMember.getRole() != HouseholdRole.OWNER && currentMember.getRole() != HouseholdRole.ADMIN) {
            log.warn("User {} attempted to update member role without permission in household {}",
                    currentUser.getId(), householdId);
            throw new InsufficientPermissionException("You don't have permission to update member roles in this household");
        }

        // Get the member to update
        HouseholdMember memberToUpdate = householdMemberRepository.findById(memberId)
                .orElseThrow(() -> new NotFoundException("Member not found"));

        // Check if member is from the specified household
        if (!memberToUpdate.getHousehold().getId().equals(householdId)) {
            throw new NotFoundException("Member is not from the specified household");
        }

        // Admin cannot update another admin's role
        if (currentMember.getRole() == HouseholdRole.ADMIN && memberToUpdate.getRole() == HouseholdRole.ADMIN) {
            log.warn("Admin {} attempted to update another admin's role in household {}",
                    currentUser.getId(), householdId);
            throw new InsufficientPermissionException("Admin cannot update another admin's role");
        }

        // Update the role
        memberToUpdate.setRole(role);
        memberToUpdate.setUpdatedAt(LocalDateTime.now());

        memberToUpdate = householdMemberRepository.save(memberToUpdate);
        log.info("User {} successfully updated role for member {} to {} in household {}",
                currentUser.getId(), memberId, role, householdId);

        return mapToResponse(memberToUpdate);
    }

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