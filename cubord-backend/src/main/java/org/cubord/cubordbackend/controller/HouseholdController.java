
package org.cubord.cubordbackend.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cubord.cubordbackend.dto.household.HouseholdRequest;
import org.cubord.cubordbackend.dto.household.HouseholdResponse;
import org.cubord.cubordbackend.service.HouseholdService;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * REST controller for household management operations.
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
 *   <li><strong>POST /:</strong> Any authenticated user can create households</li>
 *   <li><strong>GET /:</strong> Any authenticated user can list their households</li>
 *   <li><strong>GET /{id}:</strong> Must be a member of the household</li>
 *   <li><strong>GET /search:</strong> Any authenticated user can search their households</li>
 *   <li><strong>PUT /{id}:</strong> Requires an OWNER or ADMIN role</li>
 *   <li><strong>DELETE /{id}:</strong> Requires OWNER role</li>
 *   <li><strong>POST /{id}/leave:</strong> Any member except OWNER can leave</li>
 * </ul>
 *
 * <h2>Exception Handling</h2>
 * <p>All exceptions are handled by {@link org.cubord.cubordbackend.exception.RestExceptionHandler}
 * which provides consistent error responses with correlation IDs.</p>
 *
 * @see HouseholdService
 * @see org.cubord.cubordbackend.security.SecurityService
 */
@RestController
@RequestMapping("/api/households")
@RequiredArgsConstructor
@Validated
@Slf4j
public class HouseholdController {

    private final HouseholdService householdService;

    /**
     * Creates a new household with the current user as the owner.
     *
     * <p>Authorization: All authenticated users can create households.</p>
     *
     * @param request DTO containing household information
     * @return ResponseEntity containing the created household's details with 201 CREATED statuses
     */
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<HouseholdResponse> createHousehold(
            @RequestBody @Valid HouseholdRequest request) {

        log.debug("Creating household with name: {}", request.getName());

        HouseholdResponse response = householdService.createHousehold(request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .contentType(MediaType.APPLICATION_JSON)
                .body(response);
    }

    /**
     * Retrieves all households where the current user is a member.
     *
     * <p>Authorization: All authenticated users can list their own households.</p>
     *
     * @return ResponseEntity containing a list of household details
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<HouseholdResponse>> getUserHouseholds() {
        log.debug("Retrieving households for current user");

        List<HouseholdResponse> households = householdService.getUserHouseholds();

        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(2, TimeUnit.MINUTES))
                .contentType(MediaType.APPLICATION_JSON)
                .body(households);
    }

    /**
     * Retrieves a household by its ID.
     *
     * <p>Authorization: User must be a member of the household.</p>
     *
     * @param id UUID of the household to retrieve
     * @return ResponseEntity containing the household details
     */
    @GetMapping("/{id}")
    @PreAuthorize("@security.canAccessHousehold(#id)")
    public ResponseEntity<HouseholdResponse> getHouseholdById(@PathVariable @NotNull UUID id) {
        log.debug("Retrieving household by ID: {}", id);

        HouseholdResponse response = householdService.getHouseholdById(id);

        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(5, TimeUnit.MINUTES))
                .contentType(MediaType.APPLICATION_JSON)
                .body(response);
    }

    /**
     * Searches for households by name that the current user is a member of.
     *
     * <p>Authorization: All authenticated users can search their own households.</p>
     *
     * @param query Search term to find in household names
     * @return ResponseEntity containing a list of matching households
     */
    @GetMapping("/search")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<HouseholdResponse>> searchHouseholds(
            @RequestParam @NotBlank String query) {

        log.debug("Searching households with query: {}", query);

        List<HouseholdResponse> households = householdService.searchHouseholds(query);

        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(1, TimeUnit.MINUTES))
                .contentType(MediaType.APPLICATION_JSON)
                .body(households);
    }

    /**
     * Updates a household's information.
     *
     * <p>Authorization: User must have an OWNER or ADMIN role in the household.</p>
     *
     * @param id UUID of the household to update
     * @param request DTO containing updated household information
     * @return ResponseEntity containing the updated household's details
     */
    @PutMapping("/{id}")
    @PreAuthorize("@security.canModifyHousehold(#id)")
    public ResponseEntity<HouseholdResponse> updateHousehold(
            @PathVariable @NotNull UUID id,
            @RequestBody @Valid HouseholdRequest request) {

        log.debug("Updating household: {}", id);

        HouseholdResponse response = householdService.updateHousehold(id, request);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(response);
    }

    /**
     * Deletes a household.
     *
     * <p>Authorization: Only the household OWNER can delete it.</p>
     *
     * <p>This operation is irreversible and will cascade delete all associated
     * members, locations, and pantry items.</p>
     *
     * @param id UUID of the household to delete
     * @return ResponseEntity with no content (204)
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("@security.isHouseholdOwner(#id)")
    public ResponseEntity<Void> deleteHousehold(@PathVariable @NotNull UUID id) {
        log.debug("Deleting household: {}", id);

        householdService.deleteHousehold(id);

        return ResponseEntity.noContent().build();
    }

    /**
     * Allows a user to leave a household.
     *
     * <p>Authorization: User must be a member of the household.</p>
     *
     * <p>The OWNER cannot leave their household - they must transfer ownership
     * first or delete the household entirely.</p>
     *
     * @param id UUID of the household to leave
     * @return ResponseEntity with no content (204)
     */
    @PostMapping("/{id}/leave")
    @PreAuthorize("@security.canAccessHousehold(#id)")
    public ResponseEntity<Void> leaveHousehold(@PathVariable @NotNull UUID id) {
        log.debug("User leaving household: {}", id);

        householdService.leaveHousehold(id);

        return ResponseEntity.noContent().build();
    }
}