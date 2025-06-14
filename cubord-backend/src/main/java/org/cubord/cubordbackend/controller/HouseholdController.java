package org.cubord.cubordbackend.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.cubord.cubordbackend.dto.HouseholdRequest;
import org.cubord.cubordbackend.dto.HouseholdResponse;
import org.cubord.cubordbackend.service.HouseholdService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/households")
@RequiredArgsConstructor
@Validated
public class HouseholdController {
    private static final Logger logger = LoggerFactory.getLogger(HouseholdController.class);

    private final HouseholdService householdService;

    @GetMapping("/{id}")
    @PreAuthorize("@householdPermissionEvaluator.hasViewPermission(authentication, #id.toString())")
    public ResponseEntity<HouseholdResponse> getHousehold(@PathVariable UUID id, JwtAuthenticationToken auth) {
        logger.debug("Getting household with ID: {}", id);
        return ResponseEntity.ok(householdService.getHouseholdById(id, auth));
    }

    @GetMapping
    public ResponseEntity<List<HouseholdResponse>> getUserHouseholds(JwtAuthenticationToken auth) {
        logger.debug("Getting households for user: {}", auth.getName());
        return ResponseEntity.ok(householdService.getUserHouseholds(auth));
    }

    @PostMapping
    public ResponseEntity<HouseholdResponse> createHousehold(
            @Valid @RequestBody HouseholdRequest request,
            JwtAuthenticationToken auth) {
        logger.debug("Creating new household: {}", request.getName());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(householdService.createHousehold(request, auth));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@householdPermissionEvaluator.hasEditPermission(authentication, #id.toString())")
    public ResponseEntity<HouseholdResponse> updateHousehold(
            @PathVariable UUID id,
            @Valid @RequestBody HouseholdRequest request,
            JwtAuthenticationToken auth) {
        logger.debug("Updating household with ID: {}", id);
        return ResponseEntity.ok(householdService.updateHousehold(id, request, auth));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("@householdPermissionEvaluator.hasEditPermission(authentication, #id.toString())")
    public ResponseEntity<HouseholdResponse> patchHousehold(
            @PathVariable UUID id,
            @RequestBody Map<String, Object> fields,
            JwtAuthenticationToken auth) {
        logger.debug("Patching household with ID: {}", id);
        return ResponseEntity.ok(householdService.patchHousehold(id, fields, auth));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@householdPermissionEvaluator.hasEditPermission(authentication, #id.toString())")
    public ResponseEntity<Void> deleteHousehold(
            @PathVariable UUID id,
            JwtAuthenticationToken auth) {
        logger.debug("Deleting household with ID: {}", id);
        householdService.deleteHousehold(id, auth);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/leave")
    @PreAuthorize("@householdPermissionEvaluator.hasViewPermission(authentication, #id.toString())")
    public ResponseEntity<Void> leaveHousehold(
            @PathVariable UUID id,
            JwtAuthenticationToken auth) {
        logger.debug("User {} leaving household with ID: {}", auth.getName(), id);
        householdService.leaveHousehold(id, auth);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/transfer-ownership")
    @PreAuthorize("@householdPermissionEvaluator.hasEditPermission(authentication, #id.toString())")
    public ResponseEntity<Void> transferOwnership(
            @PathVariable UUID id,
            @RequestBody Map<String, UUID> request,
            JwtAuthenticationToken auth) {

        UUID newOwnerId = request.get("newOwnerId");
        if (newOwnerId == null) {
            return ResponseEntity.badRequest().build();
        }

        logger.debug("Transferring ownership of household {} to user {}", id, newOwnerId);
        householdService.transferOwnership(id, newOwnerId, auth);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/search")
    public ResponseEntity<List<HouseholdResponse>> searchHouseholds(
            @RequestParam @NotBlank String term,
            JwtAuthenticationToken auth) {
        logger.debug("Searching households with term: {}", term);
        return ResponseEntity.ok(householdService.searchHouseholds(term, auth));
    }
}