package org.cubord.cubordbackend.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.cubord.cubordbackend.domain.InvitationStatus;
import org.cubord.cubordbackend.dto.HouseholdInvitationRequest;
import org.cubord.cubordbackend.dto.HouseholdInvitationResponse;
import org.cubord.cubordbackend.dto.HouseholdInvitationUpdateRequest;
import org.cubord.cubordbackend.dto.ResendInvitationRequest;
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
        
        // TODO: Implement invitation sending logic
        throw new RuntimeException("Method not implemented yet");
    }

    /**
     * Retrieves all invitations for a household.
     */
    @GetMapping("/api/households/{householdId}/invitations")
    public ResponseEntity<List<HouseholdInvitationResponse>> getHouseholdInvitations(
            @PathVariable UUID householdId,
            @RequestParam(required = false) InvitationStatus status,
            JwtAuthenticationToken token) {
        
        // TODO: Implement get household invitations logic
        throw new RuntimeException("Method not implemented yet");
    }

    /**
     * Retrieves a specific invitation by ID.
     */
    @GetMapping("/api/households/{householdId}/invitations/{invitationId}")
    public ResponseEntity<HouseholdInvitationResponse> getInvitationById(
            @PathVariable UUID householdId,
            @PathVariable UUID invitationId,
            JwtAuthenticationToken token) {
        
        // TODO: Implement get invitation by ID logic
        throw new RuntimeException("Method not implemented yet");
    }

    /**
     * Retrieves current user's pending invitations.
     */
    @GetMapping("/api/invitations/my")
    public ResponseEntity<List<HouseholdInvitationResponse>> getMyInvitations(
            @RequestParam(required = false) InvitationStatus status,
            JwtAuthenticationToken token) {
        
        // TODO: Implement get my invitations logic
        throw new RuntimeException("Method not implemented yet");
    }

    /**
     * Accepts a household invitation.
     */
    @PostMapping("/api/invitations/{invitationId}/accept")
    public ResponseEntity<HouseholdInvitationResponse> acceptInvitation(
            @PathVariable UUID invitationId,
            JwtAuthenticationToken token) {
        
        // TODO: Implement accept invitation logic
        throw new RuntimeException("Method not implemented yet");
    }

    /**
     * Declines a household invitation.
     */
    @PostMapping("/api/invitations/{invitationId}/decline")
    public ResponseEntity<HouseholdInvitationResponse> declineInvitation(
            @PathVariable UUID invitationId,
            JwtAuthenticationToken token) {
        
        // TODO: Implement decline invitation logic
        throw new RuntimeException("Method not implemented yet");
    }

    /**
     * Cancels a household invitation (for household admins/owners).
     */
    @DeleteMapping("/api/households/{householdId}/invitations/{invitationId}")
    public ResponseEntity<Void> cancelInvitation(
            @PathVariable UUID householdId,
            @PathVariable UUID invitationId,
            JwtAuthenticationToken token) {
        
        // TODO: Implement cancel invitation logic
        throw new RuntimeException("Method not implemented yet");
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
        
        // TODO: Implement update invitation logic
        throw new RuntimeException("Method not implemented yet");
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
        
        // TODO: Implement resend invitation logic
        throw new RuntimeException("Method not implemented yet");
    }
}