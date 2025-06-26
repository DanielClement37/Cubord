package org.cubord.cubordbackend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import org.springframework.security.access.AccessDeniedException;
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
     * @throws NotFoundException if the household or user is not found
     * @throws AccessDeniedException if the current user lacks permission
     * @throws IllegalStateException if the user is already a member
     */
    @Transactional
    public HouseholdMemberResponse addMemberToHousehold(UUID householdId, HouseholdMemberRequest request,
                                                       JwtAuthenticationToken token) {
        User currentUser = userService.getCurrentUser(token);

        // Check if household exists
        Household household = householdRepository.findById(householdId)
                .orElseThrow(() -> new NotFoundException("Household not found"));

        // Check if current user is a member with appropriate permissions
        HouseholdMember currentMember = householdMemberRepository.findByHouseholdIdAndUserId(householdId, currentUser.getId())
                .orElseThrow(() -> new AccessDeniedException("You don't have access to this household"));

        if (currentMember.getRole() != HouseholdRole.OWNER && currentMember.getRole() != HouseholdRole.ADMIN) {
            throw new AccessDeniedException("You don't have permission to add members to this household");
        }

        // Check if user to add exists
        User userToAdd = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new NotFoundException("User not found"));

        // Check if user is already a member
        if (householdMemberRepository.existsByHouseholdIdAndUserId(householdId, userToAdd.getId())) {
            throw new IllegalStateException("User is already a member of this household");
        }

        // Cannot set role to OWNER through this method
        if (request.getRole() == HouseholdRole.OWNER) {
            throw new IllegalArgumentException("Cannot set role to OWNER");
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

        return mapToResponse(member);
    }

    /**
     * Retrieves all members of a household.
     * 
     * @param householdId ID of the household to get members for
     * @param token JWT token of the user performing the action
     * @return List of HouseholdMemberResponse objects representing household members
     * @throws NotFoundException if the household is not found
     * @throws AccessDeniedException if the current user is not a member
     */
    @Transactional(readOnly = true)
    public List<HouseholdMemberResponse> getHouseholdMembers(UUID householdId, JwtAuthenticationToken token) {
        User currentUser = userService.getCurrentUser(token);

        // Check if household exists
        Household household = householdRepository.findById(householdId)
                .orElseThrow(() -> new NotFoundException("Household not found"));

        // Check if current user is a member
        householdMemberRepository.findByHouseholdIdAndUserId(householdId, currentUser.getId())
                .orElseThrow(() -> new AccessDeniedException("You don't have access to this household"));

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
     * @throws NotFoundException if the household or member is not found
     * @throws AccessDeniedException if the current user is not a member of the household
     * or if the member is not from the specified household
     */
    @Transactional(readOnly = true)
    public HouseholdMemberResponse getMemberById(UUID householdId, UUID memberId, JwtAuthenticationToken token) {
        User currentUser = userService.getCurrentUser(token);

        // Check if household exists
        Household household = householdRepository.findById(householdId)
                .orElseThrow(() -> new NotFoundException("Household not found"));

        // Check if current user is a member
        householdMemberRepository.findByHouseholdIdAndUserId(householdId, currentUser.getId())
                .orElseThrow(() -> new AccessDeniedException("You don't have access to this household"));

        // Get the requested member
        HouseholdMember member = householdMemberRepository.findById(memberId)
                .orElseThrow(() -> new NotFoundException("Member not found"));

        // Check if member is from the specified household
        if (!member.getHousehold().getId().equals(householdId)) {
            throw new AccessDeniedException("Member is not from the specified household");
        }

        return mapToResponse(member);
    }

    /**
     * Removes a member from a household.
     * 
     * @param householdId ID of the household
     * @param memberId ID of the member to remove
     * @param token JWT token of the user performing the action
     * @throws NotFoundException if the household or member is not found
     * @throws AccessDeniedException if the current user lacks permission
     * @throws IllegalStateException if attempting to remove the owner
     */
    @Transactional
    public void removeMember(UUID householdId, UUID memberId, JwtAuthenticationToken token) {
        User currentUser = userService.getCurrentUser(token);

        // Check if household exists
        Household household = householdRepository.findById(householdId)
                .orElseThrow(() -> new NotFoundException("Household not found"));

        // Check if current user is a member with appropriate permissions
        HouseholdMember currentMember = householdMemberRepository.findByHouseholdIdAndUserId(householdId, currentUser.getId())
                .orElseThrow(() -> new AccessDeniedException("You don't have access to this household"));

        // Check permissions first - before looking up the member to remove
        if (currentMember.getRole() != HouseholdRole.OWNER && currentMember.getRole() != HouseholdRole.ADMIN) {
            throw new AccessDeniedException("You don't have permission to remove members from this household");
        }

        // Get the member to remove
        HouseholdMember memberToRemove = householdMemberRepository.findById(memberId)
                .orElseThrow(() -> new NotFoundException("Member not found"));

        // Check if member is from the specified household
        if (!memberToRemove.getHousehold().getId().equals(householdId)) {
            throw new AccessDeniedException("Member is not from the specified household");
        }

        // Cannot remove the owner
        if (memberToRemove.getRole() == HouseholdRole.OWNER) {
            throw new IllegalStateException("Cannot remove the owner from the household");
        }

        // Admin cannot remove another admin
        if (currentMember.getRole() == HouseholdRole.ADMIN && memberToRemove.getRole() == HouseholdRole.ADMIN) {
            throw new AccessDeniedException("Admin cannot remove another admin");
        }

        // Remove the member
        householdMemberRepository.delete(memberToRemove);
    }

    /**
     * Updates a member's role within a household.
     * 
     * @param householdId ID of the household
     * @param memberId ID of the member whose role is to be updated
     * @param role New role to assign to the member
     * @param token JWT token of the user performing the action
     * @return HouseholdMemberResponse containing updated member details
     * @throws NotFoundException if the household or member is not found
     * @throws AccessDeniedException if the current user lacks permission
     * @throws IllegalArgumentException if attempting to set role to OWNER
     */
    @Transactional
    public HouseholdMemberResponse updateMemberRole(UUID householdId, UUID memberId, HouseholdRole role, JwtAuthenticationToken token) {
        User currentUser = userService.getCurrentUser(token);

        // Cannot set role to OWNER
        if (role == HouseholdRole.OWNER) {
            throw new IllegalArgumentException("Cannot set role to OWNER");
        }

        // Check if household exists
        Household household = householdRepository.findById(householdId)
                .orElseThrow(() -> new NotFoundException("Household not found"));

        // Check if current user is a member with appropriate permissions
        HouseholdMember currentMember = householdMemberRepository.findByHouseholdIdAndUserId(householdId, currentUser.getId())
                .orElseThrow(() -> new AccessDeniedException("You don't have access to this household"));

        // Check permissions first - before looking up the member to update
        if (currentMember.getRole() != HouseholdRole.OWNER && currentMember.getRole() != HouseholdRole.ADMIN) {
            throw new AccessDeniedException("You don't have permission to update member roles in this household");
        }

        // Get the member to update
        HouseholdMember memberToUpdate = householdMemberRepository.findById(memberId)
                .orElseThrow(() -> new NotFoundException("Member not found"));

        // Check if member is from the specified household
        if (!memberToUpdate.getHousehold().getId().equals(householdId)) {
            throw new AccessDeniedException("Member is not from the specified household");
        }

        // Admin cannot update another admin's role
        if (currentMember.getRole() == HouseholdRole.ADMIN && memberToUpdate.getRole() == HouseholdRole.ADMIN) {
            throw new AccessDeniedException("Admin cannot update another admin's role");
        }

        // Update the role
        memberToUpdate.setRole(role);
        memberToUpdate.setUpdatedAt(LocalDateTime.now());

        memberToUpdate = householdMemberRepository.save(memberToUpdate);

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