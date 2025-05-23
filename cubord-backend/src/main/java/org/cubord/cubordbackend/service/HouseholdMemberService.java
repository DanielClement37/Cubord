package org.cubord.cubordbackend.service;

import lombok.RequiredArgsConstructor;
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

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
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
        Household household = householdRepository.findById(householdId)
                .orElseThrow(() -> new NotFoundException("Household not found"));

        HouseholdMember currentMember = householdMemberRepository.findByHouseholdIdAndUserId(householdId, currentUser.getId())
                .orElseThrow(() -> new AccessDeniedException("You don't have access to this household"));

        if (currentMember.getRole() != HouseholdRole.OWNER && currentMember.getRole() != HouseholdRole.ADMIN) {
            throw new AccessDeniedException("You don't have permission to add members to this household");
        }

        User userToAdd = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new NotFoundException("User not found"));

        if (householdMemberRepository.findByHouseholdIdAndUserId(householdId, userToAdd.getId()).isPresent()) {
            throw new IllegalStateException("User is already a member of this household");
        }

        HouseholdMember newMember = HouseholdMember.builder()
                .household(household)
                .user(userToAdd)
                .role(request.getRole())
                .build();

        newMember = householdMemberRepository.save(newMember);

        return mapToResponse(newMember);
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
        Household household = householdRepository.findById(householdId)
                .orElseThrow(() -> new NotFoundException("Household not found"));

        householdMemberRepository.findByHouseholdIdAndUserId(householdId, currentUser.getId())
                .orElseThrow(() -> new AccessDeniedException("You don't have access to this household"));

        List<HouseholdMember> members = householdMemberRepository.findByHouseholdId(householdId);

        return members.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
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