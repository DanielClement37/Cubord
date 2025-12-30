
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
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
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
     * @param householdId UUID of the household to send invitation for
     * @param request DTO containing invitation details (user identifier and proposed role)
     * @return HouseholdInvitationResponse containing the created invitation's details
     * @throws ValidationException if request validation fails (null request, invalid role, etc.)
     * @throws NotFoundException if household or user isn't found
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

        // Find an invited user - either userId or email must be provided
        User invitedUser = resolveInvitedUser(request);

        // Validate invited user is not the current user
        if (invitedUser.getId().equals(currentUserId)) {
            throw new BusinessRuleViolationException("Cannot invite yourself");
        }

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

        // Check if a user is already a member
        if (householdMemberRepository.existsByHouseholdIdAndUserId(householdId, invitedUser.getId())) {
            throw new ConflictException("User is already a member of this household");
        }

        // Check if user already has pending invitation
        if (householdInvitationRepository.existsByHouseholdIdAndInvitedUserIdAndStatus(
                householdId, invitedUser.getId(), InvitationStatus.PENDING)) {
            throw new ConflictException("User already has a pending invitation to this household");
        }

        // Create invitation
        User currentUser = securityService.getCurrentUser();
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

        try {
            invitation = householdInvitationRepository.save(invitation);
            log.info("User {} sent invitation to {} for household {}",
                    currentUserId, invitedUser.getEmail(), household.getName());
            return mapToResponse(invitation);
        } catch (Exception e) {
            log.error("Failed to create invitation for household: {}", householdId, e);
            throw new DataIntegrityException("Failed to create invitation: " + e.getMessage(), e);
        }
    }

    /**
     * Sends an invitation to join a household.
     *
     * @deprecated Use {@link #sendInvitation(UUID, HouseholdInvitationRequest)} instead.
     *             This method is maintained for backward compatibility with controllers
     *             that haven't been migrated to the new security architecture.
     *             Token-based authentication is now handled by Spring Security filters.
     *
     * @param householdId UUID of the household to send invitation for
     * @param request DTO containing invitation details
     * @param token JWT authentication token (ignored in favor of SecurityContext)
     * @return HouseholdInvitationResponse containing the created invitation's details
     * @throws ValidationException if request validation fails
     * @throws NotFoundException if household or user isn't found
     * @throws InsufficientPermissionException if a user lacks permission
     * @throws BusinessRuleViolationException if business rules are violated
     * @throws ConflictException if user already has pending invitation or is already a member
     */
    @Deprecated(since = "2.0", forRemoval = true)
    @Transactional
    public HouseholdInvitationResponse sendInvitation(UUID householdId, HouseholdInvitationRequest request,
                                                      JwtAuthenticationToken token) {
        log.warn("DEPRECATED: sendInvitation(householdId, request, token) called. " +
                "Migrate to sendInvitation(householdId, request) for improved security architecture. " +
                "The token parameter is ignored - using SecurityContext instead.");

        return sendInvitation(householdId, request);
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
     * Retrieves all invitations for a household.
     *
     * @deprecated Use {@link #getHouseholdInvitations(UUID)} instead.
     *             This method is maintained for backward compatibility with controllers
     *             that haven't been migrated to the new security architecture.
     *             Token-based authentication is now handled by Spring Security filters.
     *
     * @param householdId UUID of the household
     * @param token JWT authentication token (ignored in favor of SecurityContext)
     * @return List of HouseholdInvitationResponse objects
     * @throws ValidationException if householdId is null
     * @throws InsufficientPermissionException if a user doesn't have access
     */
    @Deprecated(since = "2.0", forRemoval = true)
    @Transactional(readOnly = true)
    public List<HouseholdInvitationResponse> getHouseholdInvitations(UUID householdId, JwtAuthenticationToken token) {
        log.warn("DEPRECATED: getHouseholdInvitations(householdId, token) called. " +
                "Migrate to getHouseholdInvitations(householdId) for improved security architecture. " +
                "The token parameter is ignored - using SecurityContext instead.");

        return getHouseholdInvitations(householdId);
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
     * Retrieves household invitations filtered by status.
     *
     * @deprecated Use {@link #getHouseholdInvitationsByStatus(UUID, InvitationStatus)} instead.
     *             This method is maintained for backward compatibility with controllers
     *             that haven't been migrated to the new security architecture.
     *             Token-based authentication is now handled by Spring Security filters.
     *
     * @param householdId UUID of the household
     * @param status Status to filter by
     * @param token JWT authentication token (ignored in favor of SecurityContext)
     * @return List of HouseholdInvitationResponse objects matching the status
     * @throws ValidationException if householdId or status is null
     * @throws InsufficientPermissionException if a user doesn't have access
     */
    @Deprecated(since = "2.0", forRemoval = true)
    @Transactional(readOnly = true)
    public List<HouseholdInvitationResponse> getHouseholdInvitationsByStatus(UUID householdId,
                                                                             InvitationStatus status,
                                                                             JwtAuthenticationToken token) {
        log.warn("DEPRECATED: getHouseholdInvitationsByStatus(householdId, status, token) called. " +
                "Migrate to getHouseholdInvitationsByStatus(householdId, status) for improved security architecture. " +
                "The token parameter is ignored - using SecurityContext instead.");

        return getHouseholdInvitationsByStatus(householdId, status);
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
     * Retrieves a specific invitation by ID.
     *
     * @deprecated Use {@link #getInvitationById(UUID, UUID)} instead.
     *             This method is maintained for backward compatibility with controllers
     *             that haven't been migrated to the new security architecture.
     *             Token-based authentication is now handled by Spring Security filters.
     *
     * @param householdId UUID of the household
     * @param invitationId UUID of the invitation
     * @param token JWT authentication token (ignored in favor of SecurityContext)
     * @return HouseholdInvitationResponse containing the invitation's details
     * @throws ValidationException if householdId or invitationId is null
     * @throws NotFoundException if household or invitation not found
     * @throws InsufficientPermissionException if a user doesn't have access
     */
    @Deprecated(since = "2.0", forRemoval = true)
    @Transactional(readOnly = true)
    public HouseholdInvitationResponse getInvitationById(UUID householdId, UUID invitationId,
                                                         JwtAuthenticationToken token) {
        log.warn("DEPRECATED: getInvitationById(householdId, invitationId, token) called. " +
                "Migrate to getInvitationById(householdId, invitationId) for improved security architecture. " +
                "The token parameter is ignored - using SecurityContext instead.");

        return getInvitationById(householdId, invitationId);
    }

    /**
     * Retrieves current user's pending invitations.
     *
     * <p>Authorization: All authenticated users can view their own invitations.</p>
     *
     * @return List of HouseholdInvitationResponse objects for pending invitations
     */
    @Transactional(readOnly = true)
    @PreAuthorize("isAuthenticated()")
    public List<HouseholdInvitationResponse> getMyInvitations() {
        UUID currentUserId = securityService.getCurrentUserId();
        log.debug("User {} retrieving their invitations", currentUserId);

        List<HouseholdInvitation> invitations = householdInvitationRepository
                .findByInvitedUserIdAndStatus(currentUserId, InvitationStatus.PENDING);

        return invitations.stream()
                .map(this::mapToResponse)
                .toList();
    }

    /**
     * Retrieves current user's pending invitations.
     *
     * @deprecated Use {@link #getMyInvitations()} instead.
     *             This method is maintained for backward compatibility with controllers
     *             that haven't been migrated to the new security architecture.
     *             Token-based authentication is now handled by Spring Security filters.
     *
     * @param token JWT authentication token (ignored in favor of SecurityContext)
     * @return List of HouseholdInvitationResponse objects for pending invitations
     */
    @Deprecated(since = "2.0", forRemoval = true)
    @Transactional(readOnly = true)
    public List<HouseholdInvitationResponse> getMyInvitations(JwtAuthenticationToken token) {
        log.warn("DEPRECATED: getMyInvitations(token) called. " +
                "Migrate to getMyInvitations() for improved security architecture. " +
                "The token parameter is ignored - using SecurityContext instead.");

        return getMyInvitations();
    }

    /**
     * Retrieves current user's invitations filtered by status.
     *
     * <p>Authorization: All authenticated users can view their own invitations.</p>
     *
     * @param status Status to filter by
     * @return List of HouseholdInvitationResponse objects matching the status
     * @throws ValidationException if status is null
     */
    @Transactional(readOnly = true)
    @PreAuthorize("isAuthenticated()")
    public List<HouseholdInvitationResponse> getMyInvitationsByStatus(InvitationStatus status) {
        if (status == null) {
            throw new ValidationException("Status cannot be null");
        }

        UUID currentUserId = securityService.getCurrentUserId();
        log.debug("User {} retrieving their {} invitations", currentUserId, status);

        List<HouseholdInvitation> invitations = householdInvitationRepository
                .findByInvitedUserIdAndStatus(currentUserId, status);

        return invitations.stream()
                .map(this::mapToResponse)
                .toList();
    }

    /**
     * Retrieves current user's invitations filtered by status.
     *
     * @deprecated Use {@link #getMyInvitationsByStatus(InvitationStatus)} instead.
     *             This method is maintained for backward compatibility with controllers
     *             that haven't been migrated to the new security architecture.
     *             Token-based authentication is now handled by Spring Security filters.
     *
     * @param status Status to filter by
     * @param token JWT authentication token (ignored in favor of SecurityContext)
     * @return List of HouseholdInvitationResponse objects matching the status
     * @throws ValidationException if status is null
     */
    @Deprecated(since = "2.0", forRemoval = true)
    @Transactional(readOnly = true)
    public List<HouseholdInvitationResponse> getMyInvitationsByStatus(InvitationStatus status,
                                                                      JwtAuthenticationToken token) {
        log.warn("DEPRECATED: getMyInvitationsByStatus(status, token) called. " +
                "Migrate to getMyInvitationsByStatus(status) for improved security architecture. " +
                "The token parameter is ignored - using SecurityContext instead.");

        return getMyInvitationsByStatus(status);
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
     * Accepts a household invitation.
     *
     * @deprecated Use {@link #acceptInvitation(UUID)} instead.
     *             This method is maintained for backward compatibility with controllers
     *             that haven't been migrated to the new security architecture.
     *             Token-based authentication is now handled by Spring Security filters.
     *
     * @param invitationId UUID of the invitation to accept
     * @param token JWT authentication token (ignored in favor of SecurityContext)
     * @return HouseholdInvitationResponse containing the updated invitation's details
     * @throws ValidationException if invitationId is null
     * @throws NotFoundException if invitation not found
     * @throws InsufficientPermissionException if the user is not the invited user
     * @throws ResourceStateException if the invitation is not in valid state
     * @throws ConflictException if a user is already a member
     */
    @Deprecated(since = "2.0", forRemoval = true)
    @Transactional
    public HouseholdInvitationResponse acceptInvitation(UUID invitationId, JwtAuthenticationToken token) {
        log.warn("DEPRECATED: acceptInvitation(invitationId, token) called. " +
                "Migrate to acceptInvitation(invitationId) for improved security architecture. " +
                "The token parameter is ignored - using SecurityContext instead.");

        return acceptInvitation(invitationId);
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
     * Declines a household invitation.
     *
     * @deprecated Use {@link #declineInvitation(UUID)} instead.
     *             This method is maintained for backward compatibility with controllers
     *             that haven't been migrated to the new security architecture.
     *             Token-based authentication is now handled by Spring Security filters.
     *
     * @param invitationId UUID of the invitation to decline
     * @param token JWT authentication token (ignored in favor of SecurityContext)
     * @return HouseholdInvitationResponse containing the updated invitation's details
     * @throws ValidationException if invitationId is null
     * @throws NotFoundException if invitation not found
     * @throws InsufficientPermissionException if the user is not the invited user
     * @throws ResourceStateException if the invitation is not in valid state
     */
    @Deprecated(since = "2.0", forRemoval = true)
    @Transactional
    public HouseholdInvitationResponse declineInvitation(UUID invitationId, JwtAuthenticationToken token) {
        log.warn("DEPRECATED: declineInvitation(invitationId, token) called. " +
                "Migrate to declineInvitation(invitationId) for improved security architecture. " +
                "The token parameter is ignored - using SecurityContext instead.");

        return declineInvitation(invitationId);
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

        log.info("User {} cancelled invitation to {} for household {}",
                currentUserId, invitation.getInvitedUser().getEmail(), invitation.getHousehold().getName());
    }

    /**
     * Cancels a household invitation.
     *
     * @deprecated Use {@link #cancelInvitation(UUID, UUID)} instead.
     *             This method is maintained for backward compatibility with controllers
     *             that haven't been migrated to the new security architecture.
     *             Token-based authentication is now handled by Spring Security filters.
     *
     * @param householdId UUID of the household
     * @param invitationId UUID of the invitation to cancel
     * @param token JWT authentication token (ignored in favor of SecurityContext)
     * @throws ValidationException if householdId or invitationId is null
     * @throws NotFoundException if household or invitation not found
     * @throws InsufficientPermissionException if a user doesn't have admin/owner permission
     * @throws ResourceStateException if the invitation is not in valid state
     */
    @Deprecated(since = "2.0", forRemoval = true)
    @Transactional
    public void cancelInvitation(UUID householdId, UUID invitationId, JwtAuthenticationToken token) {
        log.warn("DEPRECATED: cancelInvitation(householdId, invitationId, token) called. " +
                "Migrate to cancelInvitation(householdId, invitationId) for improved security architecture. " +
                "The token parameter is ignored - using SecurityContext instead.");

        cancelInvitation(householdId, invitationId);
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

        log.info("User {} updated invitation to {} for household {}",
                currentUserId, invitation.getInvitedUser().getEmail(), invitation.getHousehold().getName());

        return mapToResponse(invitation);
    }

    /**
     * Updates an existing invitation.
     *
     * @deprecated Use {@link #updateInvitation(UUID, UUID, HouseholdInvitationUpdateRequest)} instead.
     *             This method is maintained for backward compatibility with controllers
     *             that haven't been migrated to the new security architecture.
     *             Token-based authentication is now handled by Spring Security filters.
     *
     * @param householdId UUID of the household
     * @param invitationId UUID of the invitation to update
     * @param request DTO containing the updated invitation information
     * @param token JWT authentication token (ignored in favor of SecurityContext)
     * @return HouseholdInvitationResponse containing the updated invitation's details
     * @throws ValidationException if request validation fails
     * @throws NotFoundException if household or invitation not found
     * @throws InsufficientPermissionException if a user doesn't have admin/owner permission
     * @throws ResourceStateException if the invitation is not in valid state
     */
    @Deprecated(since = "2.0", forRemoval = true)
    @Transactional
    public HouseholdInvitationResponse updateInvitation(UUID householdId, UUID invitationId,
                                                        HouseholdInvitationUpdateRequest request,
                                                        JwtAuthenticationToken token) {
        log.warn("DEPRECATED: updateInvitation(householdId, invitationId, request, token) called. " +
                "Migrate to updateInvitation(householdId, invitationId, request) for improved security architecture. " +
                "The token parameter is ignored - using SecurityContext instead.");

        return updateInvitation(householdId, invitationId, request);
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

        log.info("User {} resent invitation to {} for household {}",
                currentUserId, invitation.getInvitedUser().getEmail(), invitation.getHousehold().getName());

        return mapToResponse(invitation);
    }

    /**
     * Resends an invitation with updated expiry.
     *
     * @deprecated Use {@link #resendInvitation(UUID, UUID, ResendInvitationRequest)} instead.
     *             This method is maintained for backward compatibility with controllers
     *             that haven't been migrated to the new security architecture.
     *             Token-based authentication is now handled by Spring Security filters.
     *
     * @param householdId UUID of the household
     * @param invitationId UUID of the invitation to resend
     * @param request DTO containing the resend request information
     * @param token JWT authentication token (ignored in favor of SecurityContext)
     * @return HouseholdInvitationResponse containing the updated invitation's details
     * @throws ValidationException if householdId or invitationId is null
     * @throws NotFoundException if household or invitation not found
     * @throws InsufficientPermissionException if a user doesn't have admin/owner permission
     * @throws ResourceStateException if the invitation is not in valid state
     */
    @Deprecated(since = "2.0", forRemoval = true)
    @Transactional
    public HouseholdInvitationResponse resendInvitation(UUID householdId, UUID invitationId,
                                                        ResendInvitationRequest request,
                                                        JwtAuthenticationToken token) {
        log.warn("DEPRECATED: resendInvitation(householdId, invitationId, request, token) called. " +
                "Migrate to resendInvitation(householdId, invitationId, request) for improved security architecture. " +
                "The token parameter is ignored - using SecurityContext instead.");

        return resendInvitation(householdId, invitationId, request);
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
        log.info("Invitation reminder sent to {} for household {}",
                invitation.getInvitedUser().getEmail(), invitation.getHousehold().getName());
    }

    // ==================== Helper Methods ====================

    /**
     * Resolves the invited user from the request.
     * Either invitedUserId or invitedUserEmail must be provided.
     *
     * @param request The invitation request containing user identifier
     * @return User entity for the invited user
     * @throws ValidationException if neither userId nor email is provided
     * @throws NotFoundException if the user is not found
     */
    private User resolveInvitedUser(HouseholdInvitationRequest request) {
        if (request.getInvitedUserId() != null) {
            return userRepository.findById(request.getInvitedUserId())
                    .orElseThrow(() -> new NotFoundException("User not found with ID: " + request.getInvitedUserId()));
        } else if (request.getInvitedUserEmail() != null && !request.getInvitedUserEmail().isBlank()) {
            return userRepository.findByEmail(request.getInvitedUserEmail())
                    .orElseThrow(() -> new NotFoundException("User not found with email: " + request.getInvitedUserEmail()));
        } else {
            throw new ValidationException("Either invitedUserId or invitedUserEmail must be provided");
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
     * @throws ResourceStateException if the invitation is not in the PENDING state
     */
    private HouseholdInvitation validateInvitationForInvitedUser(UUID invitationId, User invitedUser) {
        HouseholdInvitation invitation = householdInvitationRepository.findById(invitationId)
                .orElseThrow(() -> new NotFoundException("Invitation not found with ID: " + invitationId));

        // Validate user is the invited user
        if (!invitation.getInvitedUser().getId().equals(invitedUser.getId())) {
            log.warn("User {} attempted to access invitation {} intended for user {}",
                    invitedUser.getId(), invitationId, invitation.getInvitedUser().getId());
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