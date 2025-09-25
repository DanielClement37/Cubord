package org.cubord.cubordbackend.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.cubord.cubordbackend.dto.householdInvitation.HouseholdInvitationRequest;
import org.cubord.cubordbackend.dto.householdInvitation.HouseholdInvitationResponse;
import org.cubord.cubordbackend.dto.householdInvitation.HouseholdInvitationUpdateRequest;
import org.cubord.cubordbackend.dto.householdInvitation.ResendInvitationRequest;
import org.cubord.cubordbackend.domain.InvitationStatus;
import org.cubord.cubordbackend.service.HouseholdInvitationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for household invitation management operations.
 * Handles HTTP requests related to sending, accepting, declining, and managing household invitations.
 * All authentication and authorization is handled at the service layer.
 */
@RestController
@RequiredArgsConstructor
public class HouseholdInvitationController {

    private final HouseholdInvitationService householdInvitationService;

    /**
     * Sends an invitation to join a household.
     */
    @PostMapping("/api/households/{householdId}/invitations")
    public ResponseEntity<HouseholdInvitationResponse> sendInvitation(
            @PathVariable UUID householdId,
            @Valid @RequestBody HouseholdInvitationRequest request,
            JwtAuthenticationToken token) {
        
        HouseholdInvitationResponse response = householdInvitationService.sendInvitation(householdId, request, token);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Retrieves all invitations for a household.
     */
    @GetMapping("/api/households/{householdId}/invitations")
    public ResponseEntity<List<HouseholdInvitationResponse>> getHouseholdInvitations(
            @PathVariable UUID householdId,
            @RequestParam(required = false) InvitationStatus status,
            JwtAuthenticationToken token) {
        
        List<HouseholdInvitationResponse> invitations;
        if (status != null) {
            invitations = householdInvitationService.getHouseholdInvitationsByStatus(householdId, status, token);
        } else {
            invitations = householdInvitationService.getHouseholdInvitations(householdId, token);
        }
        
        return ResponseEntity.ok(invitations);
    }

    /**
     * Retrieves a specific invitation by ID.
     */
    @GetMapping("/api/households/{householdId}/invitations/{invitationId}")
    public ResponseEntity<HouseholdInvitationResponse> getInvitationById(
            @PathVariable UUID householdId,
            @PathVariable UUID invitationId,
            JwtAuthenticationToken token) {
        
        HouseholdInvitationResponse response = householdInvitationService.getInvitationById(householdId, invitationId, token);
        return ResponseEntity.ok(response);
    }

    /**
     * Retrieves current user's pending invitations.
     */
    @GetMapping("/api/invitations/my")
    public ResponseEntity<List<HouseholdInvitationResponse>> getMyInvitations(
            @RequestParam(required = false) InvitationStatus status,
            JwtAuthenticationToken token) {
        
        List<HouseholdInvitationResponse> invitations;
        if (status != null) {
            invitations = householdInvitationService.getMyInvitationsByStatus(status, token);
        } else {
            invitations = householdInvitationService.getMyInvitations(token);
        }
        
        return ResponseEntity.ok(invitations);
    }

    /**
     * Accepts a household invitation.
     */
    @PostMapping("/api/invitations/{invitationId}/accept")
    public ResponseEntity<HouseholdInvitationResponse> acceptInvitation(
            @PathVariable UUID invitationId,
            JwtAuthenticationToken token) {
        
        HouseholdInvitationResponse response = householdInvitationService.acceptInvitation(invitationId, token);
        return ResponseEntity.ok(response);
    }

    /**
     * Declines a household invitation.
     */
    @PostMapping("/api/invitations/{invitationId}/decline")
    public ResponseEntity<HouseholdInvitationResponse> declineInvitation(
            @PathVariable UUID invitationId,
            JwtAuthenticationToken token) {
        
        HouseholdInvitationResponse response = householdInvitationService.declineInvitation(invitationId, token);
        return ResponseEntity.ok(response);
    }

    /**
     * Cancels a household invitation (for household admins/owners).
     */
    @DeleteMapping("/api/households/{householdId}/invitations/{invitationId}")
    public ResponseEntity<Void> cancelInvitation(
            @PathVariable UUID householdId,
            @PathVariable UUID invitationId,
            JwtAuthenticationToken token) {
        
        householdInvitationService.cancelInvitation(householdId, invitationId, token);
        return ResponseEntity.noContent().build();
    }

    /**
     * Updates an existing invitation.
     */
    @PutMapping("/api/households/{householdId}/invitations/{invitationId}")
    public ResponseEntity<HouseholdInvitationResponse> updateInvitation(
            @PathVariable UUID householdId,
            @PathVariable UUID invitationId,
            @Valid @RequestBody HouseholdInvitationUpdateRequest request,
            JwtAuthenticationToken token) {
        
        HouseholdInvitationResponse response = householdInvitationService.updateInvitation(householdId, invitationId, request, token);
        return ResponseEntity.ok(response);
    }

    /**
     * Resends an invitation with updated expiry.
     */
    @PostMapping("/api/households/{householdId}/invitations/{invitationId}/resend")
    public ResponseEntity<HouseholdInvitationResponse> resendInvitation(
            @PathVariable UUID householdId,
            @PathVariable UUID invitationId,
            @Valid @RequestBody ResendInvitationRequest request,
            JwtAuthenticationToken token) {
        
        HouseholdInvitationResponse response = householdInvitationService.resendInvitation(householdId, invitationId, request, token);
        return ResponseEntity.ok(response);
    }
}