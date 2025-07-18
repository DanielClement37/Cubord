package org.cubord.cubordbackend.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.cubord.cubordbackend.dto.LocationRequest;
import org.cubord.cubordbackend.dto.LocationResponse;
import org.cubord.cubordbackend.dto.LocationUpdateRequest;
import org.cubord.cubordbackend.service.LocationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST controller for location management operations.
 * Handles HTTP requests related to creating, retrieving, updating, and deleting locations within households.
 * All authentication and authorization is handled at the service layer.
 * The controller validates input parameters and delegates business logic to the service layer.
 */
@RestController
@RequestMapping("/api/households/{householdId}/locations")
@RequiredArgsConstructor
public class LocationController {

    private final LocationService locationService;

    /**
     * Creates a new location in a household.
     *
     * @param request DTO containing location information
     * @param token JWT authentication token of the current user
     * @return ResponseEntity containing the created location's details
     */
    @PostMapping
    public ResponseEntity<LocationResponse> createLocation(
            @Valid @RequestBody LocationRequest request,
            JwtAuthenticationToken token) {
        
        LocationResponse response = locationService.createLocation(request, token);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Retrieves a specific location by ID.
     *
     * @param locationId The UUID of the location
     * @param token JWT authentication token of the current user
     * @return ResponseEntity containing the location's details
     */
    @GetMapping("/{locationId}")
    public ResponseEntity<LocationResponse> getLocationById(
            @PathVariable UUID locationId,
            JwtAuthenticationToken token) {
        
        LocationResponse response = locationService.getLocationById(locationId, token);
        return ResponseEntity.ok(response);
    }

    /**
     * Retrieves all locations for a household.
     * 
     * @param householdId The UUID of the household
     * @param token JWT authentication token of the current user
     * @return ResponseEntity containing a list of locations
     */
    @GetMapping
    public ResponseEntity<List<LocationResponse>> getLocationsByHousehold(
            @PathVariable UUID householdId,
            JwtAuthenticationToken token) {
        
        List<LocationResponse> locations = locationService.getLocationsByHousehold(householdId, token);
        return ResponseEntity.ok(locations);
    }

    /**
     * Updates a location completely.
     *
     * @param locationId The UUID of the location
     * @param request DTO containing updated location information
     * @param token JWT authentication token of the current user
     * @return ResponseEntity containing the updated location's details
     */
    @PutMapping("/{locationId}")
    public ResponseEntity<LocationResponse> updateLocation(
            @PathVariable UUID locationId,
            @Valid @RequestBody LocationUpdateRequest request,
            JwtAuthenticationToken token) {
        
        LocationResponse response = locationService.updateLocation(locationId, request, token);
        return ResponseEntity.ok(response);
    }

    /**
     * Partially updates a location.
     *
     * @param locationId The UUID of the location
     * @param updates Map containing fields to update
     * @param token JWT authentication token of the current user
     * @return ResponseEntity containing the updated location's details
     */
    @PatchMapping("/{locationId}")
    public ResponseEntity<LocationResponse> patchLocation(
            @PathVariable UUID locationId,
            @RequestBody Map<String, Object> updates,
            JwtAuthenticationToken token) {
        
        LocationResponse response = locationService.patchLocation(locationId, updates, token);
        return ResponseEntity.ok(response);
    }

    /**
     * Deletes a location from a household.
     *
     * @param locationId The UUID of the location to delete
     * @param token JWT authentication token of the current user
     * @return ResponseEntity with no content
     */
    @DeleteMapping("/{locationId}")
    public ResponseEntity<Void> deleteLocation(
            @PathVariable UUID locationId,
            JwtAuthenticationToken token) {
        
        locationService.deleteLocation(locationId, token);
        return ResponseEntity.noContent().build();
    }

    /**
     * Searches for locations within a household based on a query string.
     * 
     * @param householdId The UUID of the household
     * @param query The search query string
     * @param token JWT authentication token of the current user
     * @return ResponseEntity containing a list of matching locations
     */
    @GetMapping("/search")
    public ResponseEntity<List<LocationResponse>> searchLocations(
            @PathVariable UUID householdId,
            @RequestParam String query,
            JwtAuthenticationToken token) {
        
        List<LocationResponse> locations = locationService.searchLocations(householdId, query, token);
        return ResponseEntity.ok(locations);
    }

    /**
     * Checks if a location name is available within a household.
     * 
     * @param householdId The UUID of the household
     * @param name The location name to check
     * @param token JWT authentication token of the current user
     * @return ResponseEntity containing true if available, false otherwise
     */
    @GetMapping("/check-availability")
    public ResponseEntity<Boolean> isLocationNameAvailable(
            @PathVariable UUID householdId,
            @RequestParam String name,
            JwtAuthenticationToken token) {
        
        Boolean isAvailable = locationService.isLocationNameAvailable(householdId, name, token);
        return ResponseEntity.ok(isAvailable);
    }
}