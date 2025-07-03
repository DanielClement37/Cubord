package org.cubord.cubordbackend.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.cubord.cubordbackend.dto.HouseholdMemberRequest;
import org.cubord.cubordbackend.dto.HouseholdMemberResponse;
import org.cubord.cubordbackend.dto.HouseholdMemberRoleUpdateRequest;
import org.cubord.cubordbackend.service.HouseholdMemberService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for household member management operations.
 * Handles HTTP requests related to adding, retrieving, updating, and removing household members.
 * All authentication and authorization is handled at the service layer.
 * The controller validates input parameters and delegates business logic to the service layer.
 */
@RestController
@RequestMapping("/api/households/{householdId}/members")
@RequiredArgsConstructor
public class HouseholdMemberController {

    private final HouseholdMemberService householdMemberService;

    /**
     * Adds a new member to a household.
     * 
     * @param householdId The UUID of the household
     * @param request DTO containing member information
     * @param token JWT authentication token of the current user
     * @return ResponseEntity containing the created member's details
     */
    @PostMapping
    public ResponseEntity<HouseholdMemberResponse> addMember(
            @PathVariable UUID householdId,
            @Valid @RequestBody HouseholdMemberRequest request,
            JwtAuthenticationToken token) {
        
        HouseholdMemberResponse response = householdMemberService.addMemberToHousehold(householdId, request, token);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Retrieves all members of a household.
     * 
     * @param householdId The UUID of the household
     * @param token JWT authentication token of the current user
     * @return ResponseEntity containing a list of household members
     */
    @GetMapping
    public ResponseEntity<List<HouseholdMemberResponse>> getHouseholdMembers(
            @PathVariable UUID householdId,
            JwtAuthenticationToken token) {
        
        List<HouseholdMemberResponse> members = householdMemberService.getHouseholdMembers(householdId, token);
        return ResponseEntity.ok(members);
    }

    /**
     * Retrieves a specific member by ID.
     * 
     * @param householdId The UUID of the household
     * @param memberId The UUID of the member
     * @param token JWT authentication token of the current user
     * @return ResponseEntity containing the member's details
     */
    @GetMapping("/{memberId}")
    public ResponseEntity<HouseholdMemberResponse> getMemberById(
            @PathVariable UUID householdId,
            @PathVariable UUID memberId,
            JwtAuthenticationToken token) {
        
        HouseholdMemberResponse response = householdMemberService.getMemberById(householdId, memberId, token);
        return ResponseEntity.ok(response);
    }

    /**
     * Removes a member from a household.
     * 
     * @param householdId The UUID of the household
     * @param memberId The UUID of the member to remove
     * @param token JWT authentication token of the current user
     * @return ResponseEntity with no content
     */
    @DeleteMapping("/{memberId}")
    public ResponseEntity<Void> removeMember(
            @PathVariable UUID householdId,
            @PathVariable UUID memberId,
            JwtAuthenticationToken token) {
        
        householdMemberService.removeMember(householdId, memberId, token);
        return ResponseEntity.noContent().build();
    }

    /**
     * Updates a member's role in a household.
     * 
     * @param householdId The UUID of the household
     * @param memberId The UUID of the member
     * @param request DTO containing the new role information
     * @param token JWT authentication token of the current user
     * @return ResponseEntity containing the updated member's details
     */
    @PutMapping("/{memberId}/role")
    public ResponseEntity<HouseholdMemberResponse> updateMemberRole(
            @PathVariable UUID householdId,
            @PathVariable UUID memberId,
            @Valid @RequestBody HouseholdMemberRoleUpdateRequest request,
            JwtAuthenticationToken token) {
        
        HouseholdMemberResponse response = householdMemberService.updateMemberRole(householdId, memberId,request.getRole(), token);
        return ResponseEntity.ok(response);
    }
}