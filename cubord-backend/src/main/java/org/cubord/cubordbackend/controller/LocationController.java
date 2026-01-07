
package org.cubord.cubordbackend.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cubord.cubordbackend.dto.location.LocationRequest;
import org.cubord.cubordbackend.dto.location.LocationResponse;
import org.cubord.cubordbackend.dto.location.LocationUpdateRequest;
import org.cubord.cubordbackend.service.LocationService;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * REST controller for location management operations.
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
 *   <li><strong>POST /:</strong> Requires an OWNER or ADMIN role in the household</li>
 *   <li><strong>GET /{locationId}:</strong> Any household member can view</li>
 *   <li><strong>GET /:</strong> Any household member can list locations</li>
 *   <li><strong>GET /search:</strong> Any household member can search</li>
 *   <li><strong>GET /check-availability:</strong> Any household member can check</li>
 *   <li><strong>PUT /{locationId}:</strong> Requires an OWNER or ADMIN role</li>
 *   <li><strong>PATCH /{locationId}:</strong> Requires an OWNER or ADMIN role</li>
 *   <li><strong>DELETE /{locationId}:</strong> Requires an OWNER or ADMIN role</li>
 * </ul>
 *
 * <h2>Exception Handling</h2>
 * <p>All exceptions are handled by {@link org.cubord.cubordbackend.exception.RestExceptionHandler}
 * which provides consistent error responses with correlation IDs.</p>
 *
 * @see LocationService
 * @see org.cubord.cubordbackend.security.SecurityService
 */
@RestController
@RequestMapping("/api/households/{householdId}/locations")
@RequiredArgsConstructor
@Validated
@Slf4j
public class LocationController {

    private final LocationService locationService;

    /**
     * Creates a new location in a household.
     *
     * <p>Authorization: User must have an OWNER or ADMIN role in the household.</p>
     *
     * @param request DTO containing location information (includes householdId)
     * @return ResponseEntity containing the created location's details with 201 CREATED statuses
     */
    @PostMapping
    @PreAuthorize("@security.canModifyHousehold(#request.householdId)")
    public ResponseEntity<LocationResponse> createLocation(
            @RequestBody @Valid LocationRequest request) {

        log.debug("Creating location '{}' in household: {}", request.getName(), request.getHouseholdId());

        LocationResponse response = locationService.createLocation(request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .contentType(MediaType.APPLICATION_JSON)
                .body(response);
    }

    /**
     * Retrieves a specific location by ID.
     *
     * <p>Authorization: User must be a member of the household that owns the location.</p>
     *
     * @param householdId UUID of the household (from a path, for context)
     * @param locationId UUID of the location to retrieve
     * @return ResponseEntity containing the location's details
     */
    @GetMapping("/{locationId}")
    @PreAuthorize("@security.canAccessHousehold(#householdId)")
    public ResponseEntity<LocationResponse> getLocationById(
            @PathVariable @NotNull UUID householdId,
            @PathVariable @NotNull UUID locationId) {

        log.debug("Retrieving location: {} in household: {}", locationId, householdId);

        LocationResponse response = locationService.getLocationById(locationId);

        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(5, TimeUnit.MINUTES))
                .contentType(MediaType.APPLICATION_JSON)
                .body(response);
    }

    /**
     * Retrieves all locations for a household.
     *
     * <p>Authorization: User must be a member of the household.</p>
     *
     * @param householdId UUID of the household
     * @return ResponseEntity containing a list of locations
     */
    @GetMapping
    @PreAuthorize("@security.canAccessHousehold(#householdId)")
    public ResponseEntity<List<LocationResponse>> getLocationsByHousehold(
            @PathVariable @NotNull UUID householdId) {

        log.debug("Retrieving all locations for household: {}", householdId);

        List<LocationResponse> locations = locationService.getLocationsByHousehold(householdId);

        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(2, TimeUnit.MINUTES))
                .contentType(MediaType.APPLICATION_JSON)
                .body(locations);
    }

    /**
     * Searches for locations within a household based on a query string.
     *
     * <p>Authorization: User must be a member of the household.</p>
     *
     * @param householdId UUID of the household
     * @param query Search query string to match against a location name or description
     * @return ResponseEntity containing a list of matching locations
     */
    @GetMapping("/search")
    @PreAuthorize("@security.canAccessHousehold(#householdId)")
    public ResponseEntity<List<LocationResponse>> searchLocations(
            @PathVariable @NotNull UUID householdId,
            @RequestParam @NotBlank String query) {

        log.debug("Searching locations in household: {} with query: {}", householdId, query);

        List<LocationResponse> locations = locationService.searchLocations(householdId, query);

        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(1, TimeUnit.MINUTES))
                .contentType(MediaType.APPLICATION_JSON)
                .body(locations);
    }

    /**
     * Checks if a location name is available within a household.
     *
     * <p>Authorization: User must be a member of the household.</p>
     *
     * @param householdId UUID of the household
     * @param name Location name to check for availability
     * @return ResponseEntity containing true if available, false otherwise
     */
    @GetMapping("/check-availability")
    @PreAuthorize("@security.canAccessHousehold(#householdId)")
    public ResponseEntity<Boolean> isLocationNameAvailable(
            @PathVariable @NotNull UUID householdId,
            @RequestParam @NotBlank String name) {

        log.debug("Checking location name availability in household: {} for name: {}", householdId, name);

        Boolean isAvailable = locationService.isLocationNameAvailable(householdId, name);

        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(30, TimeUnit.SECONDS))
                .contentType(MediaType.APPLICATION_JSON)
                .body(isAvailable);
    }

    /**
     * Updates a location completely.
     *
     * <p>Authorization: User must have an OWNER or ADMIN role in the household.</p>
     *
     * @param householdId UUID of the household (from a path, for context)
     * @param locationId UUID of the location to update
     * @param request DTO containing updated location information
     * @return ResponseEntity containing the updated location's details
     */
    @PutMapping("/{locationId}")
    @PreAuthorize("@security.canModifyHousehold(#householdId)")
    public ResponseEntity<LocationResponse> updateLocation(
            @PathVariable @NotNull UUID householdId,
            @PathVariable @NotNull UUID locationId,
            @RequestBody @Valid LocationUpdateRequest request) {

        log.debug("Updating location: {} in household: {}", locationId, householdId);

        LocationResponse response = locationService.updateLocation(locationId, request);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(response);
    }

    /**
     * Partially updates a location.
     *
     * <p>Authorization: User must have an OWNER or ADMIN role in the household.</p>
     *
     * <p>Supported fields: name, description</p>
     *
     * @param householdId UUID of the household (from a path, for context)
     * @param locationId UUID of the location to patch
     * @param updates Map containing field names and their new values
     * @return ResponseEntity containing the updated location's details
     */
    @PatchMapping("/{locationId}")
    @PreAuthorize("@security.canModifyHousehold(#householdId)")
    public ResponseEntity<LocationResponse> patchLocation(
            @PathVariable @NotNull UUID householdId,
            @PathVariable @NotNull UUID locationId,
            @RequestBody Map<String, Object> updates) {

        log.debug("Patching location: {} in household: {} with fields: {}",
                locationId, householdId, updates.keySet());

        LocationResponse response = locationService.patchLocation(locationId, updates);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(response);
    }

    /**
     * Deletes a location from a household.
     *
     * <p>Authorization: User must have an OWNER or ADMIN role in the household.</p>
     *
     * <p>Note: Deletion may fail if the location has associated pantry items.</p>
     *
     * @param householdId UUID of the household (from a path, for context)
     * @param locationId UUID of the location to delete
     * @return ResponseEntity with no content (204)
     */
    @DeleteMapping("/{locationId}")
    @PreAuthorize("@security.canModifyHousehold(#householdId)")
    public ResponseEntity<Void> deleteLocation(
            @PathVariable @NotNull UUID householdId,
            @PathVariable @NotNull UUID locationId) {

        log.debug("Deleting location: {} from household: {}", locationId, householdId);

        locationService.deleteLocation(locationId);

        return ResponseEntity.noContent().build();
    }
}