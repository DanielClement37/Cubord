package org.cubord.cubordbackend.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.cubord.cubordbackend.dto.HouseholdRequest;
import org.cubord.cubordbackend.dto.HouseholdResponse;
import org.cubord.cubordbackend.exception.ConflictException;
import org.cubord.cubordbackend.exception.ForbiddenException;
import org.cubord.cubordbackend.exception.NotFoundException;
import org.cubord.cubordbackend.exception.TokenExpiredException;
import org.cubord.cubordbackend.security.HouseholdPermissionEvaluator;
import org.cubord.cubordbackend.service.HouseholdService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/households")
@RequiredArgsConstructor
@Validated
public class HouseholdController {
    private static final Logger logger = LoggerFactory.getLogger(HouseholdController.class);

    private final HouseholdService householdService;
    private final HouseholdPermissionEvaluator householdPermissionEvaluator;

    @GetMapping("/{id}")
    @PreAuthorize("@householdPermissionEvaluator.hasViewPermission(authentication, #id.toString())")
    public ResponseEntity<HouseholdResponse> getHousehold(@PathVariable UUID id, JwtAuthenticationToken auth) {
        logger.debug("Getting household with ID: {}", id);

        // The PreAuthorize annotation should handle this, but double check explicitly
        // for tests that mock the security context but not method security
        if (!householdPermissionEvaluator.hasViewPermission(auth, id.toString())) {
            logger.warn("User {} doesn't have permission to view household {}",
                    auth.getName(), id);
            throw new ForbiddenException("You don't have permission to view this household");
        }

        try {
            HouseholdResponse response = householdService.getHouseholdById(id, auth);
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.maxAge(5, TimeUnit.MINUTES))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(response);
        } catch (Exception e) {
            logger.error("Error retrieving household {}", id, e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error retrieving household", e);
        }
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<HouseholdResponse>> getUserHouseholds(JwtAuthenticationToken auth) {
        // Check token expiration manually for the test that needs this specific check
        checkTokenExpiration(auth);

        logger.debug("Getting households for user: {}", auth.getName());
        try {
            List<HouseholdResponse> households = householdService.getUserHouseholds(auth);
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.maxAge(5, TimeUnit.MINUTES))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(households);
        } catch (Exception e) {
            logger.error("Error retrieving user households", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error retrieving user households", e);
        }
    }

    @PostMapping
    public ResponseEntity<HouseholdResponse> createHousehold(
            @Valid @RequestBody HouseholdRequest request,
            JwtAuthenticationToken auth) {
    
        logger.debug("Creating household with request: {}", request);
    
        try {
            HouseholdResponse response = householdService.createHousehold(request, auth);
            logger.debug("Household created successfully: {}", response);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(response);
        } catch (IllegalStateException e) {
            // Handle specific case where household name already exists
            logger.warn("Conflict creating household: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage(), e);
        } catch (Exception e) {
            logger.error("Error creating household", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error creating household", e);
        }
    }

    @PutMapping("/{id}")
    @PreAuthorize("@householdPermissionEvaluator.hasEditPermission(authentication, #id.toString())")
    public ResponseEntity<HouseholdResponse> updateHousehold(
            @PathVariable UUID id,
            @Valid @RequestBody HouseholdRequest request,
            JwtAuthenticationToken auth) {
    
        logger.debug("Updating household with ID: {}, request: {}", id, request);
    
        // The PreAuthorize annotation should handle this, but double check explicitly
        if (!householdPermissionEvaluator.hasEditPermission(auth, id.toString())) {
            logger.warn("User {} doesn't have permission to edit household {}", 
                auth.getName(), id);
            throw new ForbiddenException("You don't have permission to edit this household");
        }
    
        try {
            HouseholdResponse response = householdService.updateHousehold(id, request, auth);
            logger.debug("Household updated successfully: {}", response);
            return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(response);
        } catch (IllegalStateException e) {
            // Handle the specific case where household name already exists
            logger.warn("Conflict updating household: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage(), e);
        } catch (Exception e) {
            logger.error("Error updating household {}", id, e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error updating household", e);
        }
    }

    /**
     * Partially updates a household with the provided fields.
     *
     * @param id     UUID of the household to patch
     * @param fields Map of field names to updated values
     * @param auth   JWT authentication token of the current user
     * @return ResponseEntity with the updated household details
     * @throws ForbiddenException if the user doesn't have edit permission
     * @throws NotFoundException  if the household doesn't exist
     */
    @PatchMapping("/{id}")
    @PreAuthorize("@householdPermissionEvaluator.hasEditPermission(authentication, #id.toString())")
    public ResponseEntity<HouseholdResponse> patchHousehold(
            @PathVariable UUID id,
            @RequestBody Map<String, Object> fields,
            JwtAuthenticationToken auth) {
        logger.debug("Patching household: {} with fields: {}", id, fields);

        // The PreAuthorize annotation should handle this, but double check explicitly
        if (!householdPermissionEvaluator.hasEditPermission(auth, id.toString())) {
            logger.warn("User {} doesn't have permission to edit household {}",
                    auth.getName(), id);
            throw new ForbiddenException("You don't have permission to edit this household");
        }

        try {
            HouseholdResponse response = householdService.patchHousehold(id, fields, auth);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(response);
        } catch (NotFoundException e) {
            logger.info("Household not found for patch: {}", id);
            throw e;
        } catch (IllegalStateException e) {
            // Handle the specific case where household name already exists
            logger.warn("Conflict patching household: {}", e.getMessage());
            throw new ConflictException(e.getMessage());
        } catch (Exception e) {
            logger.error("Error patching household {}", id, e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error patching household", e);
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@householdPermissionEvaluator.hasEditPermission(authentication, #id.toString())")
    public ResponseEntity<Void> deleteHousehold(
            @PathVariable UUID id,
            JwtAuthenticationToken auth) {
        logger.debug("Deleting household: {}", id);

        // The PreAuthorize annotation should handle this, but double check explicitly
        if (!householdPermissionEvaluator.hasEditPermission(auth, id.toString())) {
            logger.warn("User {} doesn't have permission to delete household {}",
                    auth.getName(), id);
            throw new ForbiddenException("You don't have permission to delete this household");
        }

        try {
            householdService.deleteHousehold(id, auth);
            return ResponseEntity.noContent().build();
        } catch (NotFoundException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Error deleting household {}", id, e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error deleting household", e);
        }
    }

    @PostMapping("/{id}/leave")
    public ResponseEntity<Void> leaveHousehold(
            @PathVariable UUID id,
            JwtAuthenticationToken auth) {
        // Explicitly check token expiration
        checkTokenExpiration(auth);

        // Check if user has view permission
        if (!householdPermissionEvaluator.hasViewPermission(auth, id.toString())) {
            logger.warn("User {} doesn't have permission to leave household {}",
                    auth.getName(), id);
            throw new ForbiddenException("You don't have permission to leave this household");
        }

        logger.debug("User {} leaving household: {}", auth.getName(), id);
        try {
            householdService.leaveHousehold(id, auth);
            return ResponseEntity.noContent().build();
        } catch (NotFoundException e) {
            throw e;
        } catch (IllegalStateException e) {
            // Handle the specific case where household owner cannot leave
            logger.warn("Bad request leaving household: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        } catch (IllegalArgumentException e) {
            logger.info("Invalid leave household request: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        } catch (Exception e) {
            logger.error("Error leaving household", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error leaving household", e);
        }
    }

    @PostMapping("/{id}/transfer-ownership")
    @PreAuthorize("@householdPermissionEvaluator.hasEditPermission(authentication, #id.toString())")
    public ResponseEntity<Void> transferOwnership(
            @PathVariable UUID id,
            @RequestBody Map<String, UUID> request,
            JwtAuthenticationToken auth) {
    
        logger.debug("Transferring ownership of household: {}", id);
    
        // Validate the request has the required fields
        UUID newOwnerId = request.get("newOwnerId");
        if (newOwnerId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "newOwnerId is required");
        }
    
        // For consistency with other methods, explicitly check permissions too
        if (!householdPermissionEvaluator.hasEditPermission(auth, id.toString())) {
            logger.warn("User {} doesn't have permission to edit household {}", 
                    auth.getName(), id);
            throw new ForbiddenException("You don't have permission to edit this household");
        }
    
        householdService.transferOwnership(id, newOwnerId, auth);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/search")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<HouseholdResponse>> searchHouseholds(
            @RequestParam @NotBlank String term,
            JwtAuthenticationToken auth) {
        // For expired token test
        checkTokenExpiration(auth);

        logger.debug("Searching households with term: {}", term);
        try {
            List<HouseholdResponse> results = householdService.searchHouseholds(term, auth);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(results);
        } catch (Exception e) {
            logger.error("Error searching households", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error searching households", e);
        }
    }

    /**
     * Helper method to check if a token has expired
     */
    private void checkTokenExpiration(JwtAuthenticationToken auth) {
        Instant expiration = auth.getToken().getExpiresAt();
        if (expiration == null || expiration.isBefore(Instant.now())) {
            logger.warn("JWT token has expired. Expiration: {}, Now: {}", expiration, Instant.now());
            throw new TokenExpiredException();
        }
    }
}