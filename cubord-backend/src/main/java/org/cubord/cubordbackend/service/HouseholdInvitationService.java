package org.cubord.cubordbackend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cubord.cubordbackend.domain.*;
import org.cubord.cubordbackend.dto.householdInvitation.HouseholdInvitationRequest;
import org.cubord.cubordbackend.dto.householdInvitation.HouseholdInvitationResponse;
import org.cubord.cubordbackend.dto.householdInvitation.HouseholdInvitationUpdateRequest;
import org.cubord.cubordbackend.dto.householdInvitation.ResendInvitationRequest;
import org.cubord.cubordbackend.exception.*;
import org.cubord.cubordbackend.repository.HouseholdInvitationRepository;
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
 * Service for managing household invitations.
 * Handles the business logic for sending, accepting, declining, and managing invitations.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class HouseholdInvitationService {

    private final HouseholdInvitationRepository householdInvitationRepository;
    private final HouseholdRepository householdRepository;
    private final HouseholdMemberRepository householdMemberRepository;
    private final UserRepository userRepository;
    private final UserService userService;

    private static final int DEFAULT_INVITATION_EXPIRY_DAYS = 7;

    /**
     * Sends an invitation to join a household.
     *
     * @param householdId UUID of the household to send invitation for
     * @param request DTO containing invitation details
     * @param token JWT authentication token of the current user
     * @return HouseholdInvitationResponse containing the created invitation's details
     * @throws ValidationException if request validation fails
     * @throws NotFoundException if household or user isn't found
     * @throws InsufficientPermissionException if a user lacks permission to send invitations
     * @throws BusinessRuleViolationException if business rules are violated
     * @throws ConflictException if user already has pending invitation or is already a member
     */
    @Transactional
    public HouseholdInvitationResponse sendInvitation(UUID householdId, HouseholdInvitationRequest request, JwtAuthenticationToken token) {
        User currentUser = userService.getCurrentUser(token);

        log.debug("User {} sending invitation to household {}", currentUser.getId(), householdId);

        // Validate household exists
        Household household = householdRepository.findById(householdId)
                .orElseThrow(() -> new NotFoundException("Household not found"));

        // Validate user has permission (must be owner or admin)
        HouseholdMember member = householdMemberRepository.findByHouseholdIdAndUserId(householdId, currentUser.getId())
                .orElseThrow(() -> new InsufficientPermissionException("You don't have access to this household"));

        if (member.getRole() == HouseholdRole.MEMBER) {
            throw new InsufficientPermissionException("You don't have permission to send invitations");
        }

        // Find an invited user - either userId or email must be provided
        User invitedUser;
        if (request.getInvitedUserId() != null) {
            invitedUser = userRepository.findById(request.getInvitedUserId())
                    .orElseThrow(() -> new NotFoundException("User not found"));
        } else if (request.getInvitedUserEmail() != null) {
            invitedUser = userRepository.findByEmail(request.getInvitedUserEmail())
                    .orElseThrow(() -> new NotFoundException("User not found"));
        } else {
            throw new ValidationException("Either invitedUserId or invitedUserEmail must be provided");
        }

        // Validate invited user is not the current user
        if (invitedUser.getId().equals(currentUser.getId())) {
            throw new BusinessRuleViolationException("Cannot invite yourself");
        }

        // Validate role
        if (request.getProposedRole() == HouseholdRole.OWNER) {
            throw new ValidationException("Cannot invite user as OWNER");
        }

        // Set default expiry if not provided
        LocalDateTime expiryDate = request.getExpiresAt() != null
                ? request.getExpiresAt()
                : LocalDateTime.now().plusDays(DEFAULT_INVITATION_EXPIRY_DAYS);

        // Validate expiry date
        if (expiryDate.isBefore(LocalDateTime.now())) {
            throw new ValidationException("Expiry date cannot be in the past");
        }

        // Check if a user is already a member
        if (householdMemberRepository.findByHouseholdIdAndUserId(householdId, invitedUser.getId()).isPresent()) {
            throw new ConflictException("User is already a member of this household");
        }

        // Check if user already has pending invitation
        if (householdInvitationRepository.existsByHouseholdIdAndInvitedUserIdAndStatus(householdId, invitedUser.getId(), InvitationStatus.PENDING)) {
            throw new ConflictException("User already has a pending invitation");
        }

        // Create invitation with audit fields
        HouseholdInvitation invitation = HouseholdInvitation.builder()
                .id(UUID.randomUUID())
                .household(household)
                .invitedUser(invitedUser)
                .invitedBy(currentUser)
                .proposedRole(request.getProposedRole())
                .status(InvitationStatus.PENDING)
                .expiresAt(expiryDate)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        invitation = householdInvitationRepository.save(invitation);

        log.info("Invitation sent: {} invited {} to household {}",
                currentUser.getEmail(), invitedUser.getEmail(), household.getName());

        return mapToResponse(invitation);
    }

    /**
     * Retrieves all invitations for a household.
     *
     * @param householdId UUID of the household
     * @param token JWT authentication token of the current user
     * @return List of HouseholdInvitationResponse objects
     * @throws NotFoundException if household not found
     * @throws InsufficientPermissionException if a user doesn't have access to a household
     */
    @Transactional(readOnly = true)
    public List<HouseholdInvitationResponse> getHouseholdInvitations(UUID householdId, JwtAuthenticationToken token) {
        User currentUser = userService.getCurrentUser(token);

        log.debug("User {} retrieving invitations for household {}", currentUser.getId(), householdId);

        // Validate household exists and the user has access
        validateHouseholdAccess(householdId, currentUser.getId());

        List<HouseholdInvitation> invitations = householdInvitationRepository.findByHouseholdId(householdId);

        return invitations.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Retrieves household invitations filtered by status.
     *
     * @param householdId UUID of the household
     * @param status Status to filter by
     * @param token JWT authentication token of the current user
     * @return List of HouseholdInvitationResponse objects matching the status
     * @throws NotFoundException if household not found
     * @throws InsufficientPermissionException if a user doesn't have access to a household
     */
    @Transactional(readOnly = true)
    public List<HouseholdInvitationResponse> getHouseholdInvitationsByStatus(UUID householdId, InvitationStatus status, JwtAuthenticationToken token) {
        User currentUser = userService.getCurrentUser(token);

        log.debug("User {} retrieving {} invitations for household {}", currentUser.getId(), status, householdId);

        // Validate household exists and the user has access
        validateHouseholdAccess(householdId, currentUser.getId());

        List<HouseholdInvitation> invitations = householdInvitationRepository.findByHouseholdIdAndStatus(householdId, status);

        return invitations.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Retrieves a specific invitation by ID.
     *
     * @param householdId UUID of the household
     * @param invitationId UUID of the invitation
     * @param token JWT authentication token of the current user
     * @return HouseholdInvitationResponse containing the invitation's details
     * @throws NotFoundException if household or invitation not found
     * @throws InsufficientPermissionException if a user doesn't have access to a household
     */
    @Transactional(readOnly = true)
    public HouseholdInvitationResponse getInvitationById(UUID householdId, UUID invitationId, JwtAuthenticationToken token) {
        User currentUser = userService.getCurrentUser(token);

        log.debug("User {} retrieving invitation {} for household {}", currentUser.getId(), invitationId, householdId);

        // Validate household exists and the user has access
        validateHouseholdAccess(householdId, currentUser.getId());

        HouseholdInvitation invitation = householdInvitationRepository.findById(invitationId)
                .orElseThrow(() -> new NotFoundException("Invitation not found"));

        // Validate invitation belongs to a household
        if (!invitation.getHousehold().getId().equals(householdId)) {
            throw new NotFoundException("Invitation not found");
        }

        return mapToResponse(invitation);
    }

    /**
     * Retrieves current user's pending invitations.
     *
     * @param token JWT authentication token of the current user
     * @return List of HouseholdInvitationResponse objects for pending invitations
     */
    @Transactional(readOnly = true)
    public List<HouseholdInvitationResponse> getMyInvitations(JwtAuthenticationToken token) {
        User currentUser = userService.getCurrentUser(token);

        log.debug("User {} retrieving their invitations", currentUser.getId());

        List<HouseholdInvitation> invitations = householdInvitationRepository
                .findByInvitedUserIdAndStatus(currentUser.getId(), InvitationStatus.PENDING);

        return invitations.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Retrieves current user's invitations filtered by status.
     *
     * @param status Status to filter by
     * @param token JWT authentication token of the current user
     * @return List of HouseholdInvitationResponse objects matching the status
     */
    @Transactional(readOnly = true)
    public List<HouseholdInvitationResponse> getMyInvitationsByStatus(InvitationStatus status, JwtAuthenticationToken token) {
        User currentUser = userService.getCurrentUser(token);

        log.debug("User {} retrieving their {} invitations", currentUser.getId(), status);

        List<HouseholdInvitation> invitations = householdInvitationRepository
                .findByInvitedUserIdAndStatus(currentUser.getId(), status);

        return invitations.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Accepts a household invitation.
     *
     * @param invitationId UUID of the invitation to accept
     * @param token JWT authentication token of the current user
     * @return HouseholdInvitationResponse containing the updated invitation's details
     * @throws NotFoundException if invitation not found
     * @throws InsufficientPermissionException if the user is not the invited user
     * @throws ResourceStateException if the invitation is not in valid state
     * @throws ConflictException if a user is already a member
     */
    @Transactional
    public HouseholdInvitationResponse acceptInvitation(UUID invitationId, JwtAuthenticationToken token) {
        User currentUser = userService.getCurrentUser(token);

        log.debug("User {} accepting invitation {}", currentUser.getId(), invitationId);

        HouseholdInvitation invitation = validateInvitationForInvitedUser(invitationId, currentUser);

        // Check if the invitation has expired
        if (invitation.getExpiresAt() != null && invitation.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new ResourceStateException("Invitation has expired");
        }

        // Check if a user is already a member
        if (householdMemberRepository.findByHouseholdIdAndUserId(invitation.getHousehold().getId(), currentUser.getId()).isPresent()) {
            throw new ConflictException("You are already a member of this household");
        }

        // Update invitation status
        invitation.setStatus(InvitationStatus.ACCEPTED);
        invitation.setUpdatedAt(LocalDateTime.now());
        invitation = householdInvitationRepository.save(invitation);

        // Create a household member
        HouseholdMember member = HouseholdMember.builder()
                .id(UUID.randomUUID())
                .household(invitation.getHousehold())
                .user(currentUser)
                .role(invitation.getProposedRole())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        householdMemberRepository.save(member);

        log.info("Invitation accepted: {} joined household {}",
                currentUser.getEmail(), invitation.getHousehold().getName());

        return mapToResponse(invitation);
    }

    /**
     * Declines a household invitation.
     *
     * @param invitationId UUID of the invitation to decline
     * @param token JWT authentication token of the current user
     * @return HouseholdInvitationResponse containing the updated invitation's details
     * @throws NotFoundException if invitation not found
     * @throws InsufficientPermissionException if the user is not the invited user
     * @throws ResourceStateException if the invitation is not in valid state
     */
    @Transactional
    public HouseholdInvitationResponse declineInvitation(UUID invitationId, JwtAuthenticationToken token) {
        User currentUser = userService.getCurrentUser(token);

        log.debug("User {} declining invitation {}", currentUser.getId(), invitationId);

        HouseholdInvitation invitation = validateInvitationForInvitedUser(invitationId, currentUser);

        // Update invitation status
        invitation.setStatus(InvitationStatus.DECLINED);
        invitation.setUpdatedAt(LocalDateTime.now());
        invitation = householdInvitationRepository.save(invitation);

        log.info("Invitation declined: {} declined invitation to household {}",
                currentUser.getEmail(), invitation.getHousehold().getName());

        return mapToResponse(invitation);
    }

    /**
     * Cancels a household invitation (for household admins/owners).
     *
     * @param householdId UUID of the household
     * @param invitationId UUID of the invitation to cancel
     * @param token JWT authentication token of the current user
     * @throws NotFoundException if household or invitation not found
     * @throws InsufficientPermissionException if the user doesn't have admin/owner permission
     * @throws ResourceStateException if the invitation is not in valid state
     */
    @Transactional
    public void cancelInvitation(UUID householdId, UUID invitationId, JwtAuthenticationToken token) {
        User currentUser = userService.getCurrentUser(token);

        log.debug("User {} canceling invitation {} for household {}", currentUser.getId(), invitationId, householdId);

        // Validate household exists and the user has admin/owner permission
        validateHouseholdAdminAccess(householdId, currentUser.getId());

        HouseholdInvitation invitation = householdInvitationRepository.findById(invitationId)
                .orElseThrow(() -> new NotFoundException("Invitation not found"));

        // Validate invitation belongs to a household
        if (!invitation.getHousehold().getId().equals(householdId)) {
            throw new NotFoundException("Invitation not found");
        }

        // Validate invitation status
        if (invitation.getStatus() != InvitationStatus.PENDING) {
            throw new ResourceStateException("Cannot cancel processed invitation");
        }

        // Update invitation status
        invitation.setStatus(InvitationStatus.CANCELLED);
        invitation.setUpdatedAt(LocalDateTime.now());
        householdInvitationRepository.save(invitation);

        log.info("Invitation cancelled: {} cancelled invitation to {} for household {}",
                currentUser.getEmail(), invitation.getInvitedUser().getEmail(), invitation.getHousehold().getName());
    }

    /**
     * Updates an existing invitation.
     *
     * @param householdId UUID of the household
     * @param invitationId UUID of the invitation to update
     * @param request DTO containing the updated invitation information
     * @param token JWT authentication token of the current user
     * @return HouseholdInvitationResponse containing the updated invitation's details
     * @throws ValidationException if request validation fails
     * @throws NotFoundException if household or invitation not found
     * @throws InsufficientPermissionException if the user doesn't have admin/owner permission
     * @throws ResourceStateException if the invitation is not in valid state
     */
    @Transactional
    public HouseholdInvitationResponse updateInvitation(UUID householdId, UUID invitationId, HouseholdInvitationUpdateRequest request, JwtAuthenticationToken token) {
        User currentUser = userService.getCurrentUser(token);

        log.debug("User {} updating invitation {} for household {}", currentUser.getId(), invitationId, householdId);

        // Validate household exists and the user has admin/owner permission
        validateHouseholdAdminAccess(householdId, currentUser.getId());

        HouseholdInvitation invitation = householdInvitationRepository.findById(invitationId)
                .orElseThrow(() -> new NotFoundException("Invitation not found"));

        // Validate invitation belongs to a household
        if (!invitation.getHousehold().getId().equals(householdId)) {
            throw new NotFoundException("Invitation not found");
        }

        // Validate invitation status
        if (invitation.getStatus() != InvitationStatus.PENDING) {
            throw new ResourceStateException("Can only update pending invitations");
        }

        // Validate role
        if (request.getProposedRole() != null && request.getProposedRole() == HouseholdRole.OWNER) {
            throw new ValidationException("Cannot set proposed role to OWNER");
        }

        // Validate expiry date
        if (request.getExpiresAt() != null && request.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new ValidationException("Expiry date cannot be in the past");
        }

        // Update invitation
        if (request.getProposedRole() != null) {
            invitation.setProposedRole(request.getProposedRole());
        }
        if (request.getExpiresAt() != null) {
            invitation.setExpiresAt(request.getExpiresAt());
        }

        invitation.setUpdatedAt(LocalDateTime.now());
        invitation = householdInvitationRepository.save(invitation);

        log.info("Invitation updated: {} updated invitation to {} for household {}",
                currentUser.getEmail(), invitation.getInvitedUser().getEmail(), invitation.getHousehold().getName());

        return mapToResponse(invitation);
    }

    /**
     * Resends an invitation with updated expiry.
     *
     * @param householdId UUID of the household
     * @param invitationId UUID of the invitation to resend
     * @param request DTO containing the resend request information
     * @param token JWT authentication token of the current user
     * @return HouseholdInvitationResponse containing the updated invitation's details
     * @throws NotFoundException if household or invitation not found
     * @throws InsufficientPermissionException if the user doesn't have admin/owner permission
     * @throws ResourceStateException if the invitation is not in valid state
     */
    @Transactional
    public HouseholdInvitationResponse resendInvitation(UUID householdId, UUID invitationId, ResendInvitationRequest request, JwtAuthenticationToken token) {
        User currentUser = userService.getCurrentUser(token);

        log.debug("User {} resending invitation {} for household {}", currentUser.getId(), invitationId, householdId);

        // Validate household exists and the user has admin/owner permission
        validateHouseholdAdminAccess(householdId, currentUser.getId());

        HouseholdInvitation invitation = householdInvitationRepository.findById(invitationId)
                .orElseThrow(() -> new NotFoundException("Invitation not found"));

        // Validate invitation belongs to a household
        if (!invitation.getHousehold().getId().equals(householdId)) {
            throw new NotFoundException("Invitation not found");
        }

        // Validate invitation status
        if (invitation.getStatus() != InvitationStatus.PENDING) {
            throw new ResourceStateException("Can only resend pending invitations");
        }

        // Update the expiry date with consistent default logic
        LocalDateTime newExpiryDate = request.getExpiresAt() != null
                ? request.getExpiresAt()
                : LocalDateTime.now().plusDays(DEFAULT_INVITATION_EXPIRY_DAYS);

        invitation.setExpiresAt(newExpiryDate);
        invitation.setUpdatedAt(LocalDateTime.now());
        invitation = householdInvitationRepository.save(invitation);

        log.info("Invitation resent: {} resent invitation to {} for household {}",
                currentUser.getEmail(), invitation.getInvitedUser().getEmail(), invitation.getHousehold().getName());

        return mapToResponse(invitation);
    }

    /**
     * Marks expired invitations as EXPIRED (scheduled task).
     */
    @Transactional
    public void markExpiredInvitations() {
        LocalDateTime currentTime = LocalDateTime.now();

        log.debug("Marking expired invitations as of {}", currentTime);

        // Find all pending invitations that have expired
        List<HouseholdInvitation> expiredInvitations = householdInvitationRepository
                .findByStatusAndExpiresAtBefore(InvitationStatus.PENDING, currentTime);

        // Update status for each expired invitation
        expiredInvitations.forEach(invitation -> {
            invitation.setStatus(InvitationStatus.EXPIRED);
            invitation.setUpdatedAt(currentTime);
            householdInvitationRepository.save(invitation);
        });

        if (!expiredInvitations.isEmpty()) {
            log.info("Marked {} invitations as expired", expiredInvitations.size());
        }
    }

    /**
     * Sends invitation reminder email.
     *
     * @param invitationId UUID of the invitation
     * @throws NotFoundException if invitation not found
     * @throws ResourceStateException if the invitation is not in valid state
     */
    @Transactional
    public void sendInvitationReminder(UUID invitationId) {
        log.debug("Sending reminder for invitation {}", invitationId);

        HouseholdInvitation invitation = householdInvitationRepository.findById(invitationId)
                .orElseThrow(() -> new NotFoundException("Invitation not found"));

        if (invitation.getStatus() != InvitationStatus.PENDING) {
            throw new ResourceStateException("Can only send reminders for pending invitations");
        }

        // Check if the invitation has expired before sending a reminder
        if (invitation.getExpiresAt() != null && invitation.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new ResourceStateException("Cannot send reminder for expired invitation");
        }

        // TODO: Implement email sending logic
        log.info("Invitation reminder sent to {} for household {}",
                invitation.getInvitedUser().getEmail(), invitation.getHousehold().getName());
    }

    /**
     * Helper method to validate household access for regular members.
     *
     * @param householdId UUID of the household
     * @param userId UUID of the user
     * @throws NotFoundException if household not found
     * @throws InsufficientPermissionException if a user doesn't have access to a household
     */
    private void validateHouseholdAccess(UUID householdId, UUID userId) {
        // Validate household exists
        householdRepository.findById(householdId)
                .orElseThrow(() -> new NotFoundException("Household not found"));

        // Validate user is a member
        householdMemberRepository.findByHouseholdIdAndUserId(householdId, userId)
                .orElseThrow(() -> new InsufficientPermissionException("You don't have access to this household"));
    }

    /**
     * Helper method to validate household admin/owner access.
     *
     * @param householdId UUID of the household
     * @param userId UUID of the user
     * @throws NotFoundException if household not found
     * @throws InsufficientPermissionException if a user doesn't have admin/owner access
     */
    private void validateHouseholdAdminAccess(UUID householdId, UUID userId) {
        // Validate household exists
        householdRepository.findById(householdId)
                .orElseThrow(() -> new NotFoundException("Household not found"));

        // Validate user has permission (must be owner or admin)
        HouseholdMember member = householdMemberRepository.findByHouseholdIdAndUserId(householdId, userId)
                .orElseThrow(() -> new InsufficientPermissionException("You don't have access to this household"));

        if (member.getRole() == HouseholdRole.MEMBER) {
            throw new InsufficientPermissionException("You don't have permission to perform this action");
        }
    }

    /**
     * Validates that an invitation exists, is pending, and belongs to the specified user.
     * This is a common validation pattern for operations that invited users perform on their own invitations.
     *
     * @param invitationId UUID of the invitation to validate
     * @param invitedUser User who should be the recipient of the invitation
     * @return HouseholdInvitation entity if all validations pass
     * @throws NotFoundException if invitation not found
     * @throws InsufficientPermissionException if the user is not the invited user
     * @throws ResourceStateException if the invitation is not in PENDING state
     */
    private HouseholdInvitation validateInvitationForInvitedUser(UUID invitationId, User invitedUser) {
        HouseholdInvitation invitation = householdInvitationRepository.findById(invitationId)
                .orElseThrow(() -> new NotFoundException("Invitation not found"));

        // Validate user is the invited user
        if (!invitation.getInvitedUser().getId().equals(invitedUser.getId())) {
            throw new InsufficientPermissionException("You are not the invited user");
        }

        // Validate invitation status
        if (invitation.getStatus() != InvitationStatus.PENDING) {
            throw new ResourceStateException("Invitation has already been processed");
        }

        return invitation;
    }

    /**
     * Maps HouseholdInvitation entity to response DTO.
     *
     * @param invitation HouseholdInvitation entity to map
     * @return HouseholdInvitationResponse containing the invitation's details
     */
    private HouseholdInvitationResponse mapToResponse(HouseholdInvitation invitation) {
        return HouseholdInvitationResponse.builder()
                .id(invitation.getId())
                .invitedUserId(invitation.getInvitedUser().getId())
                .invitedUserEmail(invitation.getInvitedUser().getEmail())
                .invitedUserName(invitation.getInvitedUser().getUsername())
                .householdId(invitation.getHousehold().getId())
                .householdName(invitation.getHousehold().getName())
                .invitedByUserId(invitation.getInvitedBy().getId())
                .invitedByUserName(invitation.getInvitedBy().getUsername())
                .proposedRole(invitation.getProposedRole())
                .status(invitation.getStatus())
                .createdAt(invitation.getCreatedAt())
                .expiresAt(invitation.getExpiresAt())
                .build();
    }
}