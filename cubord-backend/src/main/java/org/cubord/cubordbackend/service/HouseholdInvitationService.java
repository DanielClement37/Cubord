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
import org.cubord.cubordbackend.security.SecurityService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Service class for managing household invitations.
 *
 * <h2>Authorization Rules</h2>
 * <ul>
 *   <li><strong>Send Invitation:</strong> Requires an OWNER or ADMIN role in the household</li>
 *   <li><strong>View Invitations:</strong> Any household member can view household invitations</li>
 *   <li><strong>Accept/Decline:</strong> Only the invited user can accept or decline</li>
 *   <li><strong>Cancel/Update:</strong> Requires an OWNER or ADMIN role in the household</li>
 *   <li><strong>My Invitations:</strong> All authenticated users can view their own invitations</li>
 * </ul>
 *
 * <h2>Business Rules</h2>
 * <ul>
 *   <li>Users cannot invite themselves</li>
 *   <li>Cannot invite to an OWNER role (use ownership transfer instead)</li>
 *   <li>Users cannot have multiple pending invitations to the same household</li>
 *   <li>Invitations expire after a configurable period (default: 7 days)</li>
 *   <li>Only PENDING invitations can be accepted, declined, updated, or canceled</li>
 *   <li><strong>New:</strong> Invitations can be sent to email addresses without existing accounts</li>
 *   <li><strong>New:</strong> Email-based invitations are automatically linked when users sign up</li>
 * </ul>
 *
 * @see SecurityService
 * @see org.cubord.cubordbackend.domain.HouseholdInvitation
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class HouseholdInvitationService {

    private final HouseholdInvitationRepository householdInvitationRepository;
    private final HouseholdRepository householdRepository;
    private final HouseholdMemberRepository householdMemberRepository;
    private final UserRepository userRepository;
    private final SecurityService securityService;

    private static final int DEFAULT_INVITATION_EXPIRY_DAYS = 7;

    // ==================== Create Operations ====================

    /**
     * Sends an invitation to join a household.
     *
     * <p>Authorization: User must have an OWNER or ADMIN role in the household.</p>
     *
     * <p>The invitation creates a pending request for a user to join the household
     * with the specified role. Either invitedUserId or invitedUserEmail must be provided.</p>
     *
     * <p><strong>New behavior:</strong> If inviting by email and no user account exists,
     * the invitation is created with just the email address. When a user signs up with
     * that email, the invitation will be automatically linked to their account.</p>
     *
     * @param householdId UUID of the household to send invitation for
     * @param request DTO containing invitation details (user identifier and proposed role)
     * @return HouseholdInvitationResponse containing the created invitation's details
     * @throws ValidationException if request validation fails (null request, invalid role, etc.)
     * @throws NotFoundException if a household isn't found, or if inviting by userId and user isn't found
     * @throws InsufficientPermissionException if a user lacks permission (via @PreAuthorize)
     * @throws BusinessRuleViolationException if business rules are violated (e.g., inviting self)
     * @throws ConflictException if user already has pending invitation or is already a member
     */
    @Transactional
    @PreAuthorize("@security.canSendHouseholdInvitations(#householdId)")
    public HouseholdInvitationResponse sendInvitation(UUID householdId, HouseholdInvitationRequest request) {
        if (householdId == null) {
            throw new ValidationException("Household ID cannot be null");
        }
        if (request == null) {
            throw new ValidationException("Invitation request cannot be null");
        }

        UUID currentUserId = securityService.getCurrentUserId();
        log.debug("User {} sending invitation to household {}", currentUserId, householdId);

        // Validate household exists
        Household household = householdRepository.findById(householdId)
                .orElseThrow(() -> new NotFoundException("Household not found with ID: " + householdId));

        // Resolve invited user - may return null for email-only invitations
        InvitationTarget target = resolveInvitationTarget(request, currentUserId);

        // Validate role
        if (request.getProposedRole() == null) {
            throw new ValidationException("Proposed role cannot be null");
        }
        if (request.getProposedRole() == HouseholdRole.OWNER) {
            throw new ValidationException("Cannot invite user as OWNER. Use ownership transfer instead.");
        }

        // Set default expiry if not provided
        LocalDateTime expiryDate = request.getExpiresAt() != null
                ? request.getExpiresAt()
                : LocalDateTime.now().plusDays(DEFAULT_INVITATION_EXPIRY_DAYS);

        // Validate expiry date
        if (expiryDate.isBefore(LocalDateTime.now())) {
            throw new ValidationException("Expiry date cannot be in the past");
        }

        // Check for conflicts based on whether we have a user or just an email
        checkForInvitationConflicts(householdId, target);

        // Create invitation
        User currentUser = securityService.getCurrentUser();
        HouseholdInvitation invitation = HouseholdInvitation.builder()
                .id(UUID.randomUUID())
                .household(household)
                .invitedUser(target.user())
                .invitedEmail(target.email())
                .invitedBy(currentUser)
                .proposedRole(request.getProposedRole())
                .status(InvitationStatus.PENDING)
                .expiresAt(expiryDate)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        try {
            invitation = householdInvitationRepository.save(invitation);
            String inviteeIdentifier = target.user() != null ? target.user().getEmail() : target.email();
            log.info("User {} sent invitation to {} for household {} (email-only: {})",
                    currentUserId, inviteeIdentifier, household.getName(), target.user() == null);
            return mapToResponse(invitation);
        } catch (Exception e) {
            log.error("Failed to create invitation for household: {}", householdId, e);
            throw new DataIntegrityException("Failed to create invitation: " + e.getMessage(), e);
        }
    }

    // ==================== Query Operations ====================

    /**
     * Retrieves all invitations for a household.
     *
     * <p>Authorization: User must be a member of the household.</p>
     *
     * @param householdId UUID of the household
     * @return List of HouseholdInvitationResponse objects
     * @throws ValidationException if householdId is null
     * @throws InsufficientPermissionException if the user doesn't have access (via @PreAuthorize)
     */
    @Transactional(readOnly = true)
    @PreAuthorize("@security.canAccessHousehold(#householdId)")
    public List<HouseholdInvitationResponse> getHouseholdInvitations(UUID householdId) {
        if (householdId == null) {
            throw new ValidationException("Household ID cannot be null");
        }

        UUID currentUserId = securityService.getCurrentUserId();
        log.debug("User {} retrieving invitations for household {}", currentUserId, householdId);

        List<HouseholdInvitation> invitations = householdInvitationRepository.findByHouseholdId(householdId);

        return invitations.stream()
                .map(this::mapToResponse)
                .toList();
    }

    /**
     * Retrieves household invitations filtered by status.
     *
     * <p>Authorization: User must be a member of the household.</p>
     *
     * @param householdId UUID of the household
     * @param status Status to filter by
     * @return List of HouseholdInvitationResponse objects matching the status
     * @throws ValidationException if householdId or status is null
     * @throws InsufficientPermissionException if a user doesn't have access (via @PreAuthorize)
     */
    @Transactional(readOnly = true)
    @PreAuthorize("@security.canAccessHousehold(#householdId)")
    public List<HouseholdInvitationResponse> getHouseholdInvitationsByStatus(UUID householdId, InvitationStatus status) {
        if (householdId == null) {
            throw new ValidationException("Household ID cannot be null");
        }
        if (status == null) {
            throw new ValidationException("Status cannot be null");
        }

        UUID currentUserId = securityService.getCurrentUserId();
        log.debug("User {} retrieving {} invitations for household {}", currentUserId, status, householdId);

        List<HouseholdInvitation> invitations = householdInvitationRepository
                .findByHouseholdIdAndStatus(householdId, status);

        return invitations.stream()
                .map(this::mapToResponse)
                .toList();
    }

    /**
     * Retrieves a specific invitation by ID.
     *
     * <p>Authorization: User must be a member of the household.</p>
     *
     * @param householdId UUID of the household
     * @param invitationId UUID of the invitation
     * @return HouseholdInvitationResponse containing the invitation's details
     * @throws ValidationException if householdId or invitationId is null
     * @throws NotFoundException if household or invitation not found
     * @throws InsufficientPermissionException if a user doesn't have access (via @PreAuthorize)
     */
    @Transactional(readOnly = true)
    @PreAuthorize("@security.canAccessHousehold(#householdId)")
    public HouseholdInvitationResponse getInvitationById(UUID householdId, UUID invitationId) {
        if (householdId == null) {
            throw new ValidationException("Household ID cannot be null");
        }
        if (invitationId == null) {
            throw new ValidationException("Invitation ID cannot be null");
        }

        UUID currentUserId = securityService.getCurrentUserId();
        log.debug("User {} retrieving invitation {} for household {}", currentUserId, invitationId, householdId);

        HouseholdInvitation invitation = householdInvitationRepository.findById(invitationId)
                .orElseThrow(() -> new NotFoundException("Invitation not found with ID: " + invitationId));

        // Validate invitation belongs to a household
        if (!invitation.getHousehold().getId().equals(householdId)) {
            throw new NotFoundException("Invitation not found in the specified household");
        }

        return mapToResponse(invitation);
    }

    /**
     * Retrieves current user's pending invitations.
     *
     * <p>This method also links any email-based invitations to the current user's account.
     * This handles the case where invitations were sent before the user created their account.</p>
     *
     * <p>Authorization: All authenticated users can view their own invitations.</p>
     *
     * @return List of HouseholdInvitationResponse objects for pending invitations
     */
    @Transactional
    @PreAuthorize("isAuthenticated()")
    public List<HouseholdInvitationResponse> getMyInvitations() {
        User currentUser = securityService.getCurrentUser();
        UUID currentUserId = currentUser.getId();
        String currentUserEmail = currentUser.getEmail();

        log.debug("User {} (email: {}) retrieving their invitations", currentUserId, currentUserEmail);

        // Link any email-based invitations to this user
        linkEmailInvitationsToCurrentUser(currentUser);

        // Now fetch all invitations for this user (including newly linked ones)
        List<HouseholdInvitation> invitations = householdInvitationRepository
                .findByInvitedUserIdAndStatus(currentUserId, InvitationStatus.PENDING);

        return invitations.stream()
                .map(this::mapToResponse)
                .toList();
    }

    /**
     * Retrieves current user's invitations filtered by status.
     *
     * <p>This method also links any email-based invitations to the current user's account
     * when checking for PENDING invitations.</p>
     *
     * <p>Authorization: All authenticated users can view their own invitations.</p>
     *
     * @param status Status to filter by
     * @return List of HouseholdInvitationResponse objects matching the status
     * @throws ValidationException if status is null
     */
    @Transactional
    @PreAuthorize("isAuthenticated()")
    public List<HouseholdInvitationResponse> getMyInvitationsByStatus(InvitationStatus status) {
        if (status == null) {
            throw new ValidationException("Status cannot be null");
        }

        User currentUser = securityService.getCurrentUser();
        UUID currentUserId = currentUser.getId();
        log.debug("User {} retrieving their {} invitations", currentUserId, status);

        // Link email-based invitations if checking for pending
        if (status == InvitationStatus.PENDING) {
            linkEmailInvitationsToCurrentUser(currentUser);
        }

        List<HouseholdInvitation> invitations = householdInvitationRepository
                .findByInvitedUserIdAndStatus(currentUserId, status);

        return invitations.stream()
                .map(this::mapToResponse)
                .toList();
    }

    // ==================== Update Operations ====================

    /**
     * Accepts a household invitation.
     *
     * <p>Authorization: Only the invited user can accept their invitation.
     * This is enforced by validating that the current user matches the invitation's invited user.</p>
     *
     * <p>Upon acceptance, a HouseholdMember record is created with the proposed role,
     * and the invitation status is updated to ACCEPTED.</p>
     *
     * @param invitationId UUID of the invitation to accept
     * @return HouseholdInvitationResponse containing the updated invitation's details
     * @throws ValidationException if invitationId is null
     * @throws NotFoundException if invitation not found
     * @throws InsufficientPermissionException if the user is not the invited user
     * @throws ResourceStateException if the invitation is not in valid state (expired or already processed)
     * @throws ConflictException if a user is already a member
     */
    @Transactional
    @PreAuthorize("isAuthenticated()")
    public HouseholdInvitationResponse acceptInvitation(UUID invitationId) {
        if (invitationId == null) {
            throw new ValidationException("Invitation ID cannot be null");
        }

        User currentUser = securityService.getCurrentUser();
        log.debug("User {} accepting invitation {}", currentUser.getId(), invitationId);

        HouseholdInvitation invitation = validateInvitationForInvitedUser(invitationId, currentUser);

        // Check if the invitation has expired
        if (invitation.getExpiresAt() != null && invitation.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new ResourceStateException("Invitation has expired");
        }

        // Check if a user is already a member
        if (householdMemberRepository.existsByHouseholdIdAndUserId(
                invitation.getHousehold().getId(), currentUser.getId())) {
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

        log.info("User {} accepted invitation and joined household {}",
                currentUser.getEmail(), invitation.getHousehold().getName());

        return mapToResponse(invitation);
    }

    /**
     * Declines a household invitation.
     *
     * <p>Authorization: Only the invited user can decline their invitation.
     * This is enforced by validating that the current user matches the invitation's invited user.</p>
     *
     * @param invitationId UUID of the invitation to decline
     * @return HouseholdInvitationResponse containing the updated invitation's details
     * @throws ValidationException if invitationId is null
     * @throws NotFoundException if invitation not found
     * @throws InsufficientPermissionException if the user is not the invited user
     * @throws ResourceStateException if the invitation is not in valid state
     */
    @Transactional
    @PreAuthorize("isAuthenticated()")
    public HouseholdInvitationResponse declineInvitation(UUID invitationId) {
        if (invitationId == null) {
            throw new ValidationException("Invitation ID cannot be null");
        }

        User currentUser = securityService.getCurrentUser();
        log.debug("User {} declining invitation {}", currentUser.getId(), invitationId);

        HouseholdInvitation invitation = validateInvitationForInvitedUser(invitationId, currentUser);

        // Update invitation status
        invitation.setStatus(InvitationStatus.DECLINED);
        invitation.setUpdatedAt(LocalDateTime.now());
        invitation = householdInvitationRepository.save(invitation);

        log.info("User {} declined invitation to household {}",
                currentUser.getEmail(), invitation.getHousehold().getName());

        return mapToResponse(invitation);
    }

    /**
     * Cancels a household invitation.
     *
     * <p>Authorization: User must have an OWNER or ADMIN role in the household.</p>
     *
     * <p>This allows household administrators to revoke pending invitations.</p>
     *
     * @param householdId UUID of the household
     * @param invitationId UUID of the invitation to cancel
     * @throws ValidationException if householdId or invitationId is null
     * @throws NotFoundException if household or invitation not found
     * @throws InsufficientPermissionException if a user doesn't have admin/owner permission (via @PreAuthorize)
     * @throws ResourceStateException if the invitation is not in valid state
     */
    @Transactional
    @PreAuthorize("@security.canModifyHousehold(#householdId)")
    public void cancelInvitation(UUID householdId, UUID invitationId) {
        if (householdId == null) {
            throw new ValidationException("Household ID cannot be null");
        }
        if (invitationId == null) {
            throw new ValidationException("Invitation ID cannot be null");
        }

        UUID currentUserId = securityService.getCurrentUserId();
        log.debug("User {} canceling invitation {} for household {}", currentUserId, invitationId, householdId);

        HouseholdInvitation invitation = householdInvitationRepository.findById(invitationId)
                .orElseThrow(() -> new NotFoundException("Invitation not found with ID: " + invitationId));

        // Validate invitation belongs to a household
        if (!invitation.getHousehold().getId().equals(householdId)) {
            throw new NotFoundException("Invitation not found in the specified household");
        }

        // Validate invitation status
        if (invitation.getStatus() != InvitationStatus.PENDING) {
            throw new ResourceStateException("Cannot cancel processed invitation. Current status: " + invitation.getStatus());
        }

        // Update invitation status
        invitation.setStatus(InvitationStatus.CANCELLED);
        invitation.setUpdatedAt(LocalDateTime.now());
        householdInvitationRepository.save(invitation);

        String inviteeIdentifier = invitation.getEffectiveEmail();
        log.info("User {} cancelled invitation to {} for household {}",
                currentUserId, inviteeIdentifier, invitation.getHousehold().getName());
    }

    /**
     * Updates an existing invitation.
     *
     * <p>Authorization: User must have an OWNER or ADMIN role in the household.</p>
     *
     * <p>Allows updating the proposed role and/or expiry date of a pending invitation.</p>
     *
     * @param householdId UUID of the household
     * @param invitationId UUID of the invitation to update
     * @param request DTO containing the updated invitation information
     * @return HouseholdInvitationResponse containing the updated invitation's details
     * @throws ValidationException if request validation fails (null values, invalid role, past expiry)
     * @throws NotFoundException if household or invitation not found
     * @throws InsufficientPermissionException if a user doesn't have admin/owner permission (via @PreAuthorize)
     * @throws ResourceStateException if the invitation is not in valid state
     */
    @Transactional
    @PreAuthorize("@security.canModifyHousehold(#householdId)")
    public HouseholdInvitationResponse updateInvitation(UUID householdId, UUID invitationId,
                                                        HouseholdInvitationUpdateRequest request) {
        if (householdId == null) {
            throw new ValidationException("Household ID cannot be null");
        }
        if (invitationId == null) {
            throw new ValidationException("Invitation ID cannot be null");
        }
        if (request == null) {
            throw new ValidationException("Update request cannot be null");
        }

        UUID currentUserId = securityService.getCurrentUserId();
        log.debug("User {} updating invitation {} for household {}", currentUserId, invitationId, householdId);

        HouseholdInvitation invitation = householdInvitationRepository.findById(invitationId)
                .orElseThrow(() -> new NotFoundException("Invitation not found with ID: " + invitationId));

        // Validate invitation belongs to a household
        if (!invitation.getHousehold().getId().equals(householdId)) {
            throw new NotFoundException("Invitation not found in the specified household");
        }

        // Validate invitation status
        if (invitation.getStatus() != InvitationStatus.PENDING) {
            throw new ResourceStateException("Can only update pending invitations. Current status: " + invitation.getStatus());
        }

        // Validate and update role
        if (request.getProposedRole() != null) {
            if (request.getProposedRole() == HouseholdRole.OWNER) {
                throw new ValidationException("Cannot set proposed role to OWNER. Use ownership transfer instead.");
            }
            invitation.setProposedRole(request.getProposedRole());
        }

        // Validate and update expiry date
        if (request.getExpiresAt() != null) {
            if (request.getExpiresAt().isBefore(LocalDateTime.now())) {
                throw new ValidationException("Expiry date cannot be in the past");
            }
            invitation.setExpiresAt(request.getExpiresAt());
        }

        invitation.setUpdatedAt(LocalDateTime.now());
        invitation = householdInvitationRepository.save(invitation);

        String inviteeIdentifier = invitation.getEffectiveEmail();
        log.info("User {} updated invitation to {} for household {}",
                currentUserId, inviteeIdentifier, invitation.getHousehold().getName());

        return mapToResponse(invitation);
    }

    /**
     * Resends an invitation with updated expiry.
     *
     * <p>Authorization: User must have an OWNER or ADMIN role in the household.</p>
     *
     * <p>This extends the expiry date of a pending invitation, allowing more time for acceptance.</p>
     *
     * @param householdId UUID of the household
     * @param invitationId UUID of the invitation to resend
     * @param request DTO containing the resend request information (optional new expiry date)
     * @return HouseholdInvitationResponse containing the updated invitation's details
     * @throws ValidationException if householdId or invitationId is null
     * @throws NotFoundException if household or invitation not found
     * @throws InsufficientPermissionException if a user doesn't have admin/owner permission (via @PreAuthorize)
     * @throws ResourceStateException if the invitation is not in valid state
     */
    @Transactional
    @PreAuthorize("@security.canModifyHousehold(#householdId)")
    public HouseholdInvitationResponse resendInvitation(UUID householdId, UUID invitationId,
                                                        ResendInvitationRequest request) {
        if (householdId == null) {
            throw new ValidationException("Household ID cannot be null");
        }
        if (invitationId == null) {
            throw new ValidationException("Invitation ID cannot be null");
        }

        UUID currentUserId = securityService.getCurrentUserId();
        log.debug("User {} resending invitation {} for household {}", currentUserId, invitationId, householdId);

        HouseholdInvitation invitation = householdInvitationRepository.findById(invitationId)
                .orElseThrow(() -> new NotFoundException("Invitation not found with ID: " + invitationId));

        // Validate invitation belongs to a household
        if (!invitation.getHousehold().getId().equals(householdId)) {
            throw new NotFoundException("Invitation not found in the specified household");
        }

        // Validate invitation status
        if (invitation.getStatus() != InvitationStatus.PENDING) {
            throw new ResourceStateException("Can only resend pending invitations. Current status: " + invitation.getStatus());
        }

        // Update the expiry date with consistent default logic
        LocalDateTime newExpiryDate = (request != null && request.getExpiresAt() != null)
                ? request.getExpiresAt()
                : LocalDateTime.now().plusDays(DEFAULT_INVITATION_EXPIRY_DAYS);

        invitation.setExpiresAt(newExpiryDate);
        invitation.setUpdatedAt(LocalDateTime.now());
        invitation = householdInvitationRepository.save(invitation);

        String inviteeIdentifier = invitation.getEffectiveEmail();
        log.info("User {} resent invitation to {} for household {}",
                currentUserId, inviteeIdentifier, invitation.getHousehold().getName());

        return mapToResponse(invitation);
    }

    // ==================== Scheduled Operations ====================

    /**
     * Marks expired invitations as EXPIRED.
     *
     * <p>This method is intended to be called by a scheduled task to clean up expired invitations.
     * It finds all PENDING invitations with expiry dates in the past and updates their status.</p>
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
     * <p>This method validates that an invitation exists and is in a valid state for reminders,
     * then triggers the reminder email sending process (implementation pending).</p>
     *
     * @param invitationId UUID of the invitation
     * @throws ValidationException if invitationId is null
     * @throws NotFoundException if invitation not found
     * @throws ResourceStateException if the invitation is not in valid state
     */
    @Transactional
    @PreAuthorize("isAuthenticated()")
    public void sendInvitationReminder(UUID invitationId) {
        if (invitationId == null) {
            throw new ValidationException("Invitation ID cannot be null");
        }

        log.debug("Sending reminder for invitation {}", invitationId);

        HouseholdInvitation invitation = householdInvitationRepository.findById(invitationId)
                .orElseThrow(() -> new NotFoundException("Invitation not found with ID: " + invitationId));

        if (invitation.getStatus() != InvitationStatus.PENDING) {
            throw new ResourceStateException("Can only send reminders for pending invitations. Current status: " + invitation.getStatus());
        }

        // Check if the invitation has expired before sending a reminder
        if (invitation.getExpiresAt() != null && invitation.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new ResourceStateException("Cannot send reminder for expired invitation");
        }

        // TODO: Implement email sending logic
        String inviteeIdentifier = invitation.getEffectiveEmail();
        log.info("Invitation reminder sent to {} for household {}",
                inviteeIdentifier, invitation.getHousehold().getName());
    }

    // ==================== User Registration/Login Hook ====================

    /**
     * Links any pending email-based invitations to a newly registered or logged-in user.
     *
     * <p>This method should be called during user registration or first login to ensure
     * invitations sent before the user had an account are properly linked.</p>
     *
     * @param user The user to link invitations to
     * @return Number of invitations that were linked
     */
    @Transactional
    public int linkEmailInvitationsToUser(User user) {
        if (user == null || user.getEmail() == null) {
            return 0;
        }

        int linkedCount = householdInvitationRepository.linkEmailInvitationsToUser(
                user,
                user.getEmail(),
                InvitationStatus.PENDING,
                LocalDateTime.now()
        );

        if (linkedCount > 0) {
            log.info("Linked {} email-based invitation(s) to user {} ({})",
                    linkedCount, user.getId(), user.getEmail());
        }

        return linkedCount;
    }

    // ==================== Helper Methods ====================

    /**
     * Record to hold the target of an invitation - either a User or just an email address.
     */
    private record InvitationTarget(User user, String email) {
        /**
         * Returns the effective email for this target.
         */
        String effectiveEmail() {
            return user != null ? user.getEmail() : email;
        }
    }

    /**
     * Resolves the invitation target from the request.
     *
     * <p>If inviting by userId, the user must exist. If inviting by email, the user may or may not exist.
     * If the user exists, they are returned. If not, just the email is returned.</p>
     *
     * @param request The invitation request containing user identifier
     * @param currentUserId The ID of the current user (to check for self-invitation)
     * @return InvitationTarget containing either a User or just an email
     * @throws ValidationException if neither userId nor email is provided, or if email is blank
     * @throws NotFoundException if inviting by userId and user not found
     * @throws BusinessRuleViolationException if trying to invite self
     */
    private InvitationTarget resolveInvitationTarget(HouseholdInvitationRequest request, UUID currentUserId) {
        if (request.getInvitedUserId() != null) {
            // Inviting by user ID - user must exist
            User invitedUser = userRepository.findById(request.getInvitedUserId())
                    .orElseThrow(() -> new NotFoundException("User not found with ID: " + request.getInvitedUserId()));

            // Validate not inviting self
            if (invitedUser.getId().equals(currentUserId)) {
                throw new BusinessRuleViolationException("Cannot invite yourself");
            }

            return new InvitationTarget(invitedUser, null);

        } else if (request.getInvitedUserEmail() != null && !request.getInvitedUserEmail().isBlank()) {
            String email = request.getInvitedUserEmail().trim().toLowerCase();

            // Try to find existing user with this email
            User existingUser = userRepository.findByEmail(email).orElse(null);

            if (existingUser != null) {
                // Validate not inviting self
                if (existingUser.getId().equals(currentUserId)) {
                    throw new BusinessRuleViolationException("Cannot invite yourself");
                }
                return new InvitationTarget(existingUser, null);
            }

            // No existing user - this is an email-only invitation
            // Check if current user's email matches (self-invitation check)
            User currentUser = securityService.getCurrentUser();
            if (currentUser.getEmail() != null && currentUser.getEmail().equalsIgnoreCase(email)) {
                throw new BusinessRuleViolationException("Cannot invite yourself");
            }

            return new InvitationTarget(null, email);

        } else {
            throw new ValidationException("Either invitedUserId or invitedUserEmail must be provided");
        }
    }

    /**
     * Checks for invitation conflicts (existing membership or pending invitation).
     *
     * @param householdId The household ID
     * @param target The invitation target
     * @throws ConflictException if user is already a member or has a pending invitation
     */
    private void checkForInvitationConflicts(UUID householdId, InvitationTarget target) {
        if (target.user() != null) {
            // Check if user is already a member
            if (householdMemberRepository.existsByHouseholdIdAndUserId(householdId, target.user().getId())) {
                throw new ConflictException("User is already a member of this household");
            }

            // Check if user already has pending invitation
            if (householdInvitationRepository.existsByHouseholdIdAndInvitedUserIdAndStatus(
                    householdId, target.user().getId(), InvitationStatus.PENDING)) {
                throw new ConflictException("User already has a pending invitation to this household");
            }
        } else {
            // Email-only invitation - check if email already has pending invitation
            if (householdInvitationRepository.existsByHouseholdIdAndInvitedEmailIgnoreCaseAndStatus(
                    householdId, target.email(), InvitationStatus.PENDING)) {
                throw new ConflictException("This email already has a pending invitation to this household");
            }
        }
    }

    /**
     * Links any email-based invitations to the current user.
     * Called internally when fetching invitations.
     */
    private void linkEmailInvitationsToCurrentUser(User currentUser) {
        if (currentUser.getEmail() != null) {
            int linkedCount = householdInvitationRepository.linkEmailInvitationsToUser(
                    currentUser,
                    currentUser.getEmail(),
                    InvitationStatus.PENDING,
                    LocalDateTime.now()
            );
            if (linkedCount > 0) {
                log.info("Linked {} email-based invitation(s) to user {} during invitation fetch",
                        linkedCount, currentUser.getId());
            }
        }
    }

    /**
     * Validates that an invitation exists, is pending, and belongs to the specified user.
     * This is a common validation pattern for operations that invited users perform on their own invitations.
     *
     * <p>This method also handles email-based invitations by checking if the current user's email
     * matches the invitation's email, and links them if so.</p>
     *
     * @param invitationId UUID of the invitation to validate
     * @param currentUser User who should be the recipient of the invitation
     * @return HouseholdInvitation entity if all validations pass
     * @throws NotFoundException if invitation not found
     * @throws InsufficientPermissionException if the user is not the invited user
     * @throws ResourceStateException if the invitation is not in the PENDING state
     */
    private HouseholdInvitation validateInvitationForInvitedUser(UUID invitationId, User currentUser) {
        HouseholdInvitation invitation = householdInvitationRepository.findById(invitationId)
                .orElseThrow(() -> new NotFoundException("Invitation not found with ID: " + invitationId));

        // Check if this is an email-only invitation that matches current user's email
        if (invitation.isEmailOnlyInvitation() &&
                currentUser.getEmail() != null &&
                currentUser.getEmail().equalsIgnoreCase(invitation.getInvitedEmail())) {
            // Link the invitation to this user
            invitation.setInvitedUser(currentUser);
            invitation.setInvitedEmail(null);
            invitation.setUpdatedAt(LocalDateTime.now());
            invitation = householdInvitationRepository.save(invitation);
            log.info("Linked email-based invitation {} to user {} during acceptance/decline",
                    invitationId, currentUser.getId());
        }

        // Validate user is the invited user
        if (invitation.getInvitedUser() == null || !invitation.getInvitedUser().getId().equals(currentUser.getId())) {
            log.warn("User {} attempted to access invitation {} intended for {}",
                    currentUser.getId(), invitationId,
                    invitation.getInvitedUser() != null ? invitation.getInvitedUser().getId() : invitation.getInvitedEmail());
            throw new InsufficientPermissionException("You are not the invited user for this invitation");
        }

        // Validate invitation status
        if (invitation.getStatus() != InvitationStatus.PENDING) {
            throw new ResourceStateException("Invitation has already been processed. Current status: " + invitation.getStatus());
        }

        return invitation;
    }

    /**
     * Maps HouseholdInvitation entity to response DTO.
     *
     * <p>Handles both user-linked and email-only invitations.</p>
     *
     * @param invitation HouseholdInvitation entity to map
     * @return HouseholdInvitationResponse containing the invitation's details
     */
    private HouseholdInvitationResponse mapToResponse(HouseholdInvitation invitation) {
        HouseholdInvitationResponse.HouseholdInvitationResponseBuilder builder = HouseholdInvitationResponse.builder()
                .id(invitation.getId())
                .householdId(invitation.getHousehold().getId())
                .householdName(invitation.getHousehold().getName())
                .invitedByUserId(invitation.getInvitedBy().getId())
                .invitedByUserName(invitation.getInvitedBy().getUsername())
                .proposedRole(invitation.getProposedRole())
                .status(invitation.getStatus())
                .createdAt(invitation.getCreatedAt())
                .expiresAt(invitation.getExpiresAt());

        // Handle invited user info - may be from a user object or just email
        if (invitation.getInvitedUser() != null) {
            builder.invitedUserId(invitation.getInvitedUser().getId())
                    .invitedUserEmail(invitation.getInvitedUser().getEmail())
                    .invitedUserName(invitation.getInvitedUser().getUsername());
        } else {
            // Email-only invitation
            builder.invitedUserId(null)
                    .invitedUserEmail(invitation.getInvitedEmail())
                    .invitedUserName(null);
        }

        return builder.build();
    }
}