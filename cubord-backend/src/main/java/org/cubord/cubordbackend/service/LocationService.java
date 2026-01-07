
package org.cubord.cubordbackend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cubord.cubordbackend.domain.*;
import org.cubord.cubordbackend.dto.location.LocationRequest;
import org.cubord.cubordbackend.dto.location.LocationResponse;
import org.cubord.cubordbackend.dto.location.LocationUpdateRequest;
import org.cubord.cubordbackend.exception.ConflictException;
import org.cubord.cubordbackend.exception.DataIntegrityException;
import org.cubord.cubordbackend.exception.NotFoundException;
import org.cubord.cubordbackend.exception.ValidationException;
import org.cubord.cubordbackend.repository.HouseholdRepository;
import org.cubord.cubordbackend.repository.LocationRepository;
import org.cubord.cubordbackend.security.SecurityService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service class for managing locations within households.
 *
 * <p>This service follows the modernized security architecture where:</p>
 * <ul>
 *   <li>Authentication is handled by Spring Security filters</li>
 *   <li>Authorization is declarative via @PreAuthorize annotations</li>
 *   <li>SecurityService provides business-level security context access</li>
 *   <li>No manual token validation or permission checks in business logic</li>
 * </ul>
 *
 * <h2>Authorization Rules</h2>
 * <ul>
 *   <li><strong>Create:</strong> OWNER or ADMIN role in the household</li>
 *   <li><strong>Read:</strong> Any household member can view locations</li>
 *   <li><strong>Update:</strong> OWNER or ADMIN role in the household</li>
 *   <li><strong>Delete:</strong> OWNER or ADMIN role in the household</li>
 * </ul>
 *
 * <h2>Business Rules</h2>
 * <ul>
 *   <li>Location names must be unique within a household</li>
 *   <li>Locations are scoped to households - members can only access locations in their households</li>
 *   <li>Deleting a location with pantry items may be restricted (based on cascade rules)</li>
 * </ul>
 *
 * @see SecurityService
 * @see org.cubord.cubordbackend.domain.Location
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LocationService {

    private final LocationRepository locationRepository;
    private final HouseholdRepository householdRepository;
    private final SecurityService securityService;

    // ==================== Create Operations ====================

    /**
     * Creates a new location in a household.
     *
     * <p>Authorization: User must have OWNER or ADMIN role in the household.</p>
     *
     * <p>The location name must be unique within the household. If a location with the
     * same name already exists in the household, a ConflictException is thrown.</p>
     *
     * @param request DTO containing the location information
     * @return LocationResponse containing the created location's details
     * @throws ValidationException if the request is null or contains invalid data
     * @throws NotFoundException if the household doesn't exist
     * @throws ConflictException if a location with the same name already exists in the household
     * @throws DataIntegrityException if location creation fails
     */
    @Transactional
    @PreAuthorize("@security.canModifyHousehold(#request.householdId)")
    public LocationResponse createLocation(LocationRequest request) {
        if (request == null) {
            throw new ValidationException("Location request cannot be null");
        }
        if (request.getHouseholdId() == null) {
            throw new ValidationException("Household ID cannot be null");
        }
        if (request.getName() == null || request.getName().trim().isEmpty()) {
            throw new ValidationException("Location name cannot be null or empty");
        }

        UUID currentUserId = securityService.getCurrentUserId();
        log.debug("User {} creating location in household: {}", currentUserId, request.getHouseholdId());

        // Validate household exists
        Household household = householdRepository.findById(request.getHouseholdId())
                .orElseThrow(() -> new NotFoundException("Household not found with ID: " + request.getHouseholdId()));

        // Check for duplicate location name in household
        if (locationRepository.existsByHouseholdIdAndName(request.getHouseholdId(), request.getName().trim())) {
            throw new ConflictException("Location with name '" + request.getName() + "' already exists in this household");
        }

        Location location = Location.builder()
                .household(household)
                .name(request.getName().trim())
                .description(request.getDescription() != null ? request.getDescription().trim() : null)
                .build();

        try {
            Location savedLocation = locationRepository.save(location);
            log.info("User {} successfully created location with ID: {} in household: {}",
                    currentUserId, savedLocation.getId(), request.getHouseholdId());
            return mapToResponse(savedLocation);
        } catch (Exception e) {
            log.error("Failed to create location in household: {}", request.getHouseholdId(), e);
            throw new DataIntegrityException("Failed to create location: " + e.getMessage(), e);
        }
    }

    // ==================== Query Operations ====================

    /**
     * Retrieves a location by its ID.
     *
     * <p>Authorization: User must be a member of the household that owns the location.</p>
     *
     * @param locationId UUID of the location to retrieve
     * @return LocationResponse containing the location's details
     * @throws ValidationException if locationId is null
     * @throws NotFoundException if the location doesn't exist
     */
    @Transactional(readOnly = true)
    @PreAuthorize("@security.canAccessLocation(#locationId, @locationRepository.findById(#locationId).orElse(null)?.household?.id)")
    public LocationResponse getLocationById(UUID locationId) {
        if (locationId == null) {
            throw new ValidationException("Location ID cannot be null");
        }

        UUID currentUserId = securityService.getCurrentUserId();
        log.debug("User {} retrieving location: {}", currentUserId, locationId);

        Location location = locationRepository.findById(locationId)
                .orElseThrow(() -> new NotFoundException("Location not found with ID: " + locationId));

        return mapToResponse(location);
    }

    /**
     * Retrieves all locations in a household.
     *
     * <p>Authorization: User must be a member of the household.</p>
     *
     * @param householdId UUID of the household
     * @return List of LocationResponse objects
     * @throws ValidationException if householdId is null
     */
    @Transactional(readOnly = true)
    @PreAuthorize("@security.canAccessHousehold(#householdId)")
    public List<LocationResponse> getLocationsByHousehold(UUID householdId) {
        if (householdId == null) {
            throw new ValidationException("Household ID cannot be null");
        }

        UUID currentUserId = securityService.getCurrentUserId();
        log.debug("User {} retrieving locations for household: {}", currentUserId, householdId);

        List<Location> locations = locationRepository.findByHouseholdId(householdId);

        return locations.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Searches for locations by name or description within a household.
     *
     * <p>Authorization: User must be a member of the household.</p>
     *
     * @param householdId UUID of the household to search within
     * @param searchTerm Search term to match against location name or description
     * @return List of LocationResponse objects matching the search criteria
     * @throws ValidationException if householdId or searchTerm is null/empty
     */
    @Transactional(readOnly = true)
    @PreAuthorize("@security.canAccessHousehold(#householdId)")
    public List<LocationResponse> searchLocations(UUID householdId, String searchTerm) {
        if (householdId == null) {
            throw new ValidationException("Household ID cannot be null");
        }
        if (searchTerm == null || searchTerm.trim().isEmpty()) {
            throw new ValidationException("Search term cannot be null or empty");
        }

        UUID currentUserId = securityService.getCurrentUserId();
        log.debug("User {} searching locations in household: {} with term: {}",
                currentUserId, householdId, searchTerm);

        List<Location> locations = locationRepository.searchByNameOrDescription(householdId, searchTerm.trim());

        return locations.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Checks if a location name is available in a household.
     *
     * <p>Authorization: User must be a member of the household.</p>
     *
     * @param householdId UUID of the household
     * @param name Location name to check
     * @return true if the name is available, false otherwise
     * @throws ValidationException if inputs are null or empty
     */
    @Transactional(readOnly = true)
    @PreAuthorize("@security.canAccessHousehold(#householdId)")
    public boolean isLocationNameAvailable(UUID householdId, String name) {
        if (householdId == null) {
            throw new ValidationException("Household ID cannot be null");
        }
        if (name == null || name.trim().isEmpty()) {
            throw new ValidationException("Location name cannot be null or empty");
        }

        UUID currentUserId = securityService.getCurrentUserId();
        log.debug("User {} checking location name availability in household: {}", currentUserId, householdId);

        return !locationRepository.existsByHouseholdIdAndName(householdId, name.trim());
    }

    // ==================== Update Operations ====================

    /**
     * Updates a location's information.
     *
     * <p>Authorization: User must have OWNER or ADMIN role in the household.</p>
     *
     * @param locationId UUID of the location to update
     * @param request DTO containing the updated location information
     * @return LocationResponse containing the updated location's details
     * @throws ValidationException if inputs are null or invalid
     * @throws NotFoundException if the location doesn't exist
     * @throws ConflictException if the new name conflicts with an existing location
     * @throws DataIntegrityException if update fails
     */
    @Transactional
    @PreAuthorize("@security.canModifyLocation(#locationId, @locationRepository.findById(#locationId).orElse(null)?.household?.id)")
    public LocationResponse updateLocation(UUID locationId, LocationUpdateRequest request) {
        if (locationId == null) {
            throw new ValidationException("Location ID cannot be null");
        }
        if (request == null) {
            throw new ValidationException("Update request cannot be null");
        }

        UUID currentUserId = securityService.getCurrentUserId();
        log.debug("User {} updating location: {}", currentUserId, locationId);

        Location location = locationRepository.findById(locationId)
                .orElseThrow(() -> new NotFoundException("Location not found with ID: " + locationId));

        // Update name if provided and changed
        if (request.getName() != null && !request.getName().trim().isEmpty()) {
            String newName = request.getName().trim();
            if (!newName.equals(location.getName())) {
                // Check for duplicate name in the same household
                if (locationRepository.existsByHouseholdIdAndName(location.getHousehold().getId(), newName)) {
                    throw new ConflictException("Location with name '" + newName + "' already exists in this household");
                }
                location.setName(newName);
                log.debug("Updated name for location: {}", locationId);
            }
        }

        // Update description if provided
        if (request.getDescription() != null) {
            location.setDescription(request.getDescription().trim());
            log.debug("Updated description for location: {}", locationId);
        }

        try {
            Location savedLocation = locationRepository.save(location);
            log.info("User {} successfully updated location: {}", currentUserId, locationId);
            return mapToResponse(savedLocation);
        } catch (Exception e) {
            log.error("Failed to update location: {}", locationId, e);
            throw new DataIntegrityException("Failed to update location: " + e.getMessage(), e);
        }
    }

    /**
     * Partially updates a location with the provided field values.
     *
     * <p>Authorization: User must have OWNER or ADMIN role in the household.</p>
     *
     * <p>Supported fields: name, description</p>
     *
     * @param locationId UUID of the location to patch
     * @param patchData Map containing field names and their new values
     * @return LocationResponse containing the updated location's details
     * @throws ValidationException if inputs are null or invalid
     * @throws NotFoundException if the location doesn't exist
     * @throws ConflictException if the new name conflicts with an existing location
     * @throws DataIntegrityException if patch fails
     */
    @Transactional
    @PreAuthorize("@security.canModifyLocation(#locationId, @locationRepository.findById(#locationId).orElse(null)?.household?.id)")
    public LocationResponse patchLocation(UUID locationId, Map<String, Object> patchData) {
        if (locationId == null) {
            throw new ValidationException("Location ID cannot be null");
        }
        if (patchData == null || patchData.isEmpty()) {
            throw new ValidationException("Patch data cannot be null or empty");
        }

        UUID currentUserId = securityService.getCurrentUserId();
        log.debug("User {} patching location: {} with fields: {}",
                currentUserId, locationId, patchData.keySet());

        Location location = locationRepository.findById(locationId)
                .orElseThrow(() -> new NotFoundException("Location not found with ID: " + locationId));

        // Apply patches
        patchData.forEach((field, value) -> {
            switch (field) {
                case "name":
                    if (value != null && !value.toString().trim().isEmpty()) {
                        String newName = value.toString().trim();
                        if (!newName.equals(location.getName())) {
                            if (locationRepository.existsByHouseholdIdAndName(location.getHousehold().getId(), newName)) {
                                throw new ConflictException("Location with name '" + newName + "' already exists in this household");
                            }
                            location.setName(newName);
                            log.debug("Patched name for location: {}", locationId);
                        }
                    }
                    break;

                case "description":
                    location.setDescription(value != null ? value.toString().trim() : null);
                    log.debug("Patched description for location: {}", locationId);
                    break;

                default:
                    log.warn("Attempted to patch unsupported field: {}", field);
                    throw new ValidationException("Unsupported field for patching: " + field);
            }
        });

        try {
            Location savedLocation = locationRepository.save(location);
            log.info("User {} successfully patched location: {}", currentUserId, locationId);
            return mapToResponse(savedLocation);
        } catch (Exception e) {
            log.error("Failed to patch location: {}", locationId, e);
            throw new DataIntegrityException("Failed to patch location: " + e.getMessage(), e);
        }
    }

    // ==================== Delete Operations ====================

    /**
     * Deletes a location.
     *
     * <p>Authorization: User must have OWNER or ADMIN role in the household.</p>
     *
     * <p>Note: Deletion may fail if the location has associated pantry items,
     * depending on cascade configuration.</p>
     *
     * @param locationId UUID of the location to delete
     * @throws ValidationException if locationId is null
     * @throws NotFoundException if the location doesn't exist
     * @throws DataIntegrityException if deletion fails due to data constraints
     */
    @Transactional
    @PreAuthorize("@security.canModifyLocation(#locationId, @locationRepository.findById(#locationId).orElse(null)?.household?.id)")
    public void deleteLocation(UUID locationId) {
        if (locationId == null) {
            throw new ValidationException("Location ID cannot be null");
        }

        UUID currentUserId = securityService.getCurrentUserId();
        log.debug("User {} deleting location: {}", currentUserId, locationId);

        Location location = locationRepository.findById(locationId)
                .orElseThrow(() -> new NotFoundException("Location not found with ID: " + locationId));

        try {
            locationRepository.delete(location);
            log.info("User {} successfully deleted location: {}", currentUserId, locationId);
        } catch (Exception e) {
            log.error("Failed to delete location: {}", locationId, e);
            throw new DataIntegrityException(
                    "Failed to delete location. Location may have associated pantry items that must be removed first.", e);
        }
    }

    // ==================== Helper Methods ====================

    /**
     * Maps a Location entity to a LocationResponse DTO.
     *
     * @param location Location entity to map
     * @return LocationResponse containing the location's details
     */
    private LocationResponse mapToResponse(Location location) {
        return LocationResponse.builder()
                .id(location.getId())
                .householdId(location.getHousehold().getId())
                .name(location.getName())
                .description(location.getDescription())
                .createdAt(location.getCreatedAt())
                .updatedAt(location.getUpdatedAt())
                .build();
    }
}