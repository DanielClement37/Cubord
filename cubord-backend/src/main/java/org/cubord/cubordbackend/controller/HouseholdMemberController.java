package org.cubord.cubordbackend.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.cubord.cubordbackend.dto.HouseholdMemberRequest;
import org.cubord.cubordbackend.dto.HouseholdMemberResponse;
import org.cubord.cubordbackend.service.HouseholdMemberService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/households/{householdId}/members")
@RequiredArgsConstructor
public class HouseholdMemberController {

    private static final Logger logger = LoggerFactory.getLogger(HouseholdMemberController.class);
    private final HouseholdMemberService householdMemberService;
    
    /**
     * Adds a new member to a household.
     * 
     * @param householdId ID of the household to add member to
     * @param request Details of the member to add
     * @param token JWT token of the authenticated user
     * @return ResponseEntity with created status and the added member details
     */
    @PostMapping
    public ResponseEntity<HouseholdMemberResponse> addMember(
            @PathVariable UUID householdId,
            @Valid @RequestBody HouseholdMemberRequest request,
            JwtAuthenticationToken token) {
        
        logger.debug("Adding member to household: {}, with request: {}", householdId, request);
        HouseholdMemberResponse response = householdMemberService.addMemberToHousehold(
                householdId, request, token);
        logger.debug("Member added, response: {}", response);
        
        return ResponseEntity.status(HttpStatus.CREATED)
                .contentType(MediaType.APPLICATION_JSON)
                .body(response);
    }
    
    /**
     * Retrieves all members of a household.
     * 
     * @param householdId ID of the household to get members for
     * @param token JWT token of the authenticated user
     * @return ResponseEntity with OK status and list of household members
     */
    @GetMapping
    public ResponseEntity<List<HouseholdMemberResponse>> getHouseholdMembers(
            @PathVariable UUID householdId,
            JwtAuthenticationToken token) {
        
        logger.debug("Getting members for household: {}", householdId);
        List<HouseholdMemberResponse> members = householdMemberService.getHouseholdMembers(
                householdId, token);
        logger.debug("Found {} members", members.size());
        
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(members);
    }
}