package org.cubord.cubordbackend.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.cubord.cubordbackend.dto.household.HouseholdRequest;
import org.cubord.cubordbackend.dto.household.HouseholdResponse;
import org.cubord.cubordbackend.service.HouseholdService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for household management operations.
 * Handles HTTP requests related to household creation, retrieval, updating, and deletion.
 * All authentication and authorization is handled at the service layer.
 * The controller validates input parameters and delegates business logic to the service layer.
 */
@RestController
@RequestMapping("/api/households")
@RequiredArgsConstructor
public class HouseholdController {

    private final HouseholdService householdService;

    /**
     * Retrieves a household by its ID.
     * 
     * @param id The UUID of the household to retrieve
     * @param token JWT authentication token of the current user
     * @return ResponseEntity containing the household details
     */
    @GetMapping("/{id}")
    public ResponseEntity<HouseholdResponse> getHouseholdById(
            @PathVariable UUID id,
            JwtAuthenticationToken token) {
        
        HouseholdResponse response = householdService.getHouseholdById(id, token);
        return ResponseEntity.ok(response);
    }

    /**
     * Retrieves all households where the current user is a member.
     * 
     * @param token JWT authentication token of the current user
     * @return ResponseEntity containing a list of household details
     */
    @GetMapping
    public ResponseEntity<List<HouseholdResponse>> getUserHouseholds(JwtAuthenticationToken token) {
        List<HouseholdResponse> households = householdService.getUserHouseholds(token);
        return ResponseEntity.ok(households);
    }

    /**
     * Creates a new household with the current user as the owner.
     * 
     * @param request DTO containing household information
     * @param token JWT authentication token of the current user
     * @return ResponseEntity containing the created household's details
     */
    @PostMapping
    public ResponseEntity<HouseholdResponse> createHousehold(
            @Valid @RequestBody HouseholdRequest request,
            JwtAuthenticationToken token) {
        
        HouseholdResponse response = householdService.createHousehold(request, token);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Updates a household's information.
     * 
     * @param id The UUID of the household to update
     * @param request DTO containing updated household information
     * @param token JWT authentication token of the current user
     * @return ResponseEntity containing the updated household's details
     */
    @PutMapping("/{id}")
    public ResponseEntity<HouseholdResponse> updateHousehold(
            @PathVariable UUID id,
            @Valid @RequestBody HouseholdRequest request,
            JwtAuthenticationToken token) {
        
        HouseholdResponse response = householdService.updateHousehold(id, request, token);
        return ResponseEntity.ok(response);
    }

    /**
     * Deletes a household.
     * 
     * @param id The UUID of the household to delete
     * @param token JWT authentication token of the current user
     * @return ResponseEntity with no content
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteHousehold(
            @PathVariable UUID id,
            JwtAuthenticationToken token) {
        
        householdService.deleteHousehold(id, token);
        return ResponseEntity.noContent().build();
    }

    /**
     * Allows a user to leave a household.
     * 
     * @param id The UUID of the household to leave
     * @param token JWT authentication token of the current user
     * @return ResponseEntity with no content
     */
    @PostMapping("/{id}/leave")
    public ResponseEntity<Void> leaveHousehold(
            @PathVariable UUID id,
            JwtAuthenticationToken token) {
        
        householdService.leaveHousehold(id, token);
        return ResponseEntity.noContent().build();
    }
}