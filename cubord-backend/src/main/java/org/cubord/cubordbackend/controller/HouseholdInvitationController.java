
package org.cubord.cubordbackend.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cubord.cubordbackend.domain.InvitationStatus;
import org.cubord.cubordbackend.dto.householdInvitation.HouseholdInvitationRequest;
import org.cubord.cubordbackend.dto.householdInvitation.HouseholdInvitationResponse;
import org.cubord.cubordbackend.dto.householdInvitation.HouseholdInvitationUpdateRequest;
import org.cubord.cubordbackend.dto.householdInvitation.ResendInvitationRequest;
import org.cubord.cubordbackend.service.HouseholdInvitationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for household invitation management operations.
 *
 * <p>This controller follows the modernized security architecture where:</p>
 * <ul>
 *   <li>Authentication is handled by Spring Security filters (JWT validation)</li>
 *   <li>Authorization is declarative via {@code @PreAuthorize} annotations</li>
 *   <li>No manual token validation or security checks in controller methods</li>
 *   <li>Business logic is delegated entirely to the service layer</li>
 * </ul>
 *
 * <h2>Authorization Rules</h2>
 * <ul>
 *   <li><strong>POST /households/{id}/invitations:</strong> Requires an OWNER or ADMIN role</li>
 *   <li><strong>GET /households/{id}/invitations:</strong> Any household member can view</li>
 *   <li><strong>GET /households/{id}/invitations/{invitationId}:</strong> Household member or invited user</li>
 *   <li><strong>GET /invitations/my:</strong> All authenticated users</li>
 *   <li><strong>POST /invitations/{id}/accept:</strong> Only the invited user</li>
 *   <li><strong>POST /invitations/{id}/decline:</strong> Only the invited user</li>
 *   <li><strong>DELETE /households/{id}/invitations/{invitationId}:</strong> Requires an OWNER or ADMIN role</li>
 *   <li><strong>PUT /households/{id}/invitations/{invitationId}:</strong> Requires an OWNER or ADMIN role</li>
 *   <li><strong>POST /households/{id}/invitations/{invitationId}/resend:</strong> Requires an OWNER or ADMIN role</li>
 * </ul>
 *
 * <h2>Exception Handling</h2>
 * <p>All exceptions are handled by {@link org.cubord.cubordbackend.exception.RestExceptionHandler}
 * which provides consistent error responses with correlation IDs.</p>
 *
 * @see HouseholdInvitationService
 * @see org.cubord.cubordbackend.security.SecurityService
 */
@RestController
@RequiredArgsConstructor
@Validated
@Slf4j
public class HouseholdInvitationController {

    private final HouseholdInvitationService householdInvitationService;

    /**
     * Sends an invitation to join a household.
     *
     * <p>Authorization: User must have an OWNER or ADMIN role in the household.</p>
     *
     * @param householdId UUID of the household
     * @param request DTO containing invitation details (user identifier and proposed role)
     * @return ResponseEntity containing the created invitation's details with HTTP 201 status
     */
    @PostMapping("/api/households/{householdId}/invitations")
    @PreAuthorize("@security.canSendHouseholdInvitations(#householdId)")
    public ResponseEntity<HouseholdInvitationResponse> sendInvitation(
            @PathVariable @NotNull UUID householdId,
            @Valid @RequestBody HouseholdInvitationRequest request) {

        log.debug("Sending invitation for household: {}", householdId);

        HouseholdInvitationResponse response = householdInvitationService.sendInvitation(householdId, request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .contentType(MediaType.APPLICATION_JSON)
                .body(response);
    }

    /**
     * Retrieves all invitations for a household.
     *
     * <p>Authorization: User must be a member of the household.</p>
     *
     * <p>Optionally filter by invitation status using the {@code status} query parameter.</p>
     *
     * @param householdId UUID of the household
     * @param status Optional status filter (PENDING, ACCEPTED, DECLINED, CANCELLED, EXPIRED)
     * @return ResponseEntity containing a list of household invitations
     */
    @GetMapping("/api/households/{householdId}/invitations")
    @PreAuthorize("@security.canAccessHousehold(#householdId)")
    public ResponseEntity<List<HouseholdInvitationResponse>> getHouseholdInvitations(
            @PathVariable @NotNull UUID householdId,
            @RequestParam(required = false) InvitationStatus status) {

        log.debug("Retrieving invitations for household: {} with status filter: {}", householdId, status);

        List<HouseholdInvitationResponse> invitations;
        if (status != null) {
            invitations = householdInvitationService.getHouseholdInvitationsByStatus(householdId, status);
        } else {
            invitations = householdInvitationService.getHouseholdInvitations(householdId);
        }

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(invitations);
    }

    /**
     * Retrieves a specific invitation by ID.
     *
     * <p>Authorization: User must be a household member or the invited user.</p>
     *
     * @param householdId UUID of the household
     * @param invitationId UUID of the invitation
     * @return ResponseEntity containing the invitation's details
     */
    @GetMapping("/api/households/{householdId}/invitations/{invitationId}")
    @PreAuthorize("@security.canAccessHousehold(#householdId)")
    public ResponseEntity<HouseholdInvitationResponse> getInvitationById(
            @PathVariable @NotNull UUID householdId,
            @PathVariable @NotNull UUID invitationId) {

        log.debug("Retrieving invitation {} for household: {}", invitationId, householdId);

        HouseholdInvitationResponse response = householdInvitationService.getInvitationById(householdId, invitationId);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(response);
    }

    /**
     * Retrieves current user's pending invitations.
     *
     * <p>Authorization: Any authenticated user can view their own invitations.</p>
     *
     * <p>Optionally filter by invitation status using the {@code status} query parameter.</p>
     *
     * @param status Optional status filter (PENDING, ACCEPTED, DECLINED, CANCELLED, EXPIRED)
     * @return ResponseEntity containing a list of user's invitations
     */
    @GetMapping("/api/invitations/my")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<HouseholdInvitationResponse>> getMyInvitations(
            @RequestParam(required = false) InvitationStatus status) {

        log.debug("Retrieving invitations for current user with status filter: {}", status);

        List<HouseholdInvitationResponse> invitations;
        if (status != null) {
            invitations = householdInvitationService.getMyInvitationsByStatus(status);
        } else {
            invitations = householdInvitationService.getMyInvitations();
        }

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(invitations);
    }

    /**
     * Accepts a household invitation.
     *
     * <p>Authorization: Only the invited user can accept their invitation.
     * This is enforced in the service layer by validating the current user matches the invitation's invited user.</p>
     *
     * @param invitationId UUID of the invitation to accept
     * @return ResponseEntity containing the updated invitation's details
     */
    @PostMapping("/api/invitations/{invitationId}/accept")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<HouseholdInvitationResponse> acceptInvitation(
            @PathVariable @NotNull UUID invitationId) {

        log.debug("Accepting invitation: {}", invitationId);

        HouseholdInvitationResponse response = householdInvitationService.acceptInvitation(invitationId);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(response);
    }

    /**
     * Declines a household invitation.
     *
     * <p>Authorization: Only the invited user can decline their invitation.
     * This is enforced in the service layer by validating the current user matches the invitation's invited user.</p>
     *
     * @param invitationId UUID of the invitation to decline
     * @return ResponseEntity containing the updated invitation's details
     */
    @PostMapping("/api/invitations/{invitationId}/decline")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<HouseholdInvitationResponse> declineInvitation(
            @PathVariable @NotNull UUID invitationId) {

        log.debug("Declining invitation: {}", invitationId);

        HouseholdInvitationResponse response = householdInvitationService.declineInvitation(invitationId);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(response);
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
     * @return ResponseEntity with no content (HTTP 204)
     */
    @DeleteMapping("/api/households/{householdId}/invitations/{invitationId}")
    @PreAuthorize("@security.canModifyHousehold(#householdId)")
    public ResponseEntity<Void> cancelInvitation(
            @PathVariable @NotNull UUID householdId,
            @PathVariable @NotNull UUID invitationId) {

        log.debug("Canceling invitation {} for household: {}", invitationId, householdId);

        householdInvitationService.cancelInvitation(householdId, invitationId);

        return ResponseEntity.noContent().build();
    }

    /**
     * Updates an existing invitation.
     *
     * <p>Authorization: User must have an OWNER or ADMIN role in the household.</p>
     *
     * @param householdId UUID of the household
     * @param invitationId UUID of the invitation to update
     * @param request DTO containing the updated invitation information
     * @return ResponseEntity containing the updated invitation's details
     */
    @PutMapping("/api/households/{householdId}/invitations/{invitationId}")
    @PreAuthorize("@security.canModifyHousehold(#householdId)")
    public ResponseEntity<HouseholdInvitationResponse> updateInvitation(
            @PathVariable @NotNull UUID householdId,
            @PathVariable @NotNull UUID invitationId,
            @Valid @RequestBody HouseholdInvitationUpdateRequest request) {

        log.debug("Updating invitation {} for household: {}", invitationId, householdId);

        HouseholdInvitationResponse response = householdInvitationService.updateInvitation(
                householdId,
                invitationId,
                request
        );

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(response);
    }

    /**
     * Resends an invitation with an updated expiry.
     *
     * <p>Authorization: User must have an OWNER or ADMIN role in the household.</p>
     *
     * @param householdId UUID of the household
     * @param invitationId UUID of the invitation to resend
     * @param request DTO containing the resend request information (optional new expiry date)
     * @return ResponseEntity containing the updated invitation's details
     */
    @PostMapping("/api/households/{householdId}/invitations/{invitationId}/resend")
    @PreAuthorize("@security.canModifyHousehold(#householdId)")
    public ResponseEntity<HouseholdInvitationResponse> resendInvitation(
            @PathVariable @NotNull UUID householdId,
            @PathVariable @NotNull UUID invitationId,
            @Valid @RequestBody ResendInvitationRequest request) {

        log.debug("Resending invitation {} for household: {}", invitationId, householdId);

        HouseholdInvitationResponse response = householdInvitationService.resendInvitation(
                householdId,
                invitationId,
                request
        );

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(response);
    }
}