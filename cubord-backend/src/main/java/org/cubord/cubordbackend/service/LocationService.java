package org.cubord.cubordbackend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cubord.cubordbackend.domain.*;
import org.cubord.cubordbackend.dto.location.LocationRequest;
import org.cubord.cubordbackend.dto.location.LocationResponse;
import org.cubord.cubordbackend.dto.location.LocationUpdateRequest;
import org.cubord.cubordbackend.exception.ConflictException;
import org.cubord.cubordbackend.exception.NotFoundException;
import org.cubord.cubordbackend.repository.HouseholdMemberRepository;
import org.cubord.cubordbackend.repository.HouseholdRepository;
import org.cubord.cubordbackend.repository.LocationRepository;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class LocationService {

    private final LocationRepository locationRepository;
    private final HouseholdRepository householdRepository;
    private final HouseholdMemberRepository householdMemberRepository;
    private final UserService userService;

    /**
     * Creates a new location in a household.
     */
    @Transactional
    public LocationResponse createLocation(LocationRequest request, JwtAuthenticationToken token) {
        if (request == null || token == null) {
            throw new IllegalArgumentException("Request and token cannot be null");
        }

        log.debug("Creating location: {} in household: {}", request.getName(), request.getHouseholdId());

        // Check if location name already exists in household
        if (locationRepository.existsByHouseholdIdAndName(request.getHouseholdId(), request.getName())) {
            throw new ConflictException("Location with name '" + request.getName() + "' already exists in this household");
        }

        Household household = householdRepository.findById(request.getHouseholdId())
                .orElseThrow(() -> new NotFoundException("Household not found"));

        Location location = Location.builder()
                .id(UUID.randomUUID())
                .name(request.getName())
                .description(request.getDescription())
                .household(household)
                .build();

        Location savedLocation = locationRepository.save(location);
        log.debug("Successfully created location with ID: {}", savedLocation.getId());

        return mapToResponse(savedLocation);
    }

    /**
     * Retrieves a location by its ID if the current user has access.
     */
    @Transactional(readOnly = true)
    public LocationResponse getLocationById(UUID locationId, JwtAuthenticationToken token) {
        if (locationId == null || token == null) {
            throw new IllegalArgumentException("Location ID and token cannot be null");
        }

        log.debug("Getting location by ID: {}", locationId);

        User currentUser = userService.getCurrentUser(token);
        Location location = locationRepository.findById(locationId)
                .orElseThrow(() -> new NotFoundException("Location not found"));

        validateHouseholdAccess(location.getHousehold().getId(), currentUser);

        return mapToResponse(location);
    }

    /**
     * Retrieves all locations in a household.
     */
    @Transactional(readOnly = true)
    public List<LocationResponse> getLocationsByHousehold(UUID householdId, JwtAuthenticationToken token) {
        if (householdId == null || token == null) {
            throw new IllegalArgumentException("Household ID and token cannot be null");
        }

        log.debug("Getting locations for household: {}", householdId);

        User currentUser = userService.getCurrentUser(token);
        validateHouseholdAccess(householdId, currentUser);

        List<Location> locations = locationRepository.findByHouseholdId(householdId, Sort.by("name"));

        return locations.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Updates a location's information.
     */
    @Transactional
    public LocationResponse updateLocation(UUID locationId, LocationUpdateRequest request, JwtAuthenticationToken token) {
        if (locationId == null || request == null || token == null) {
            throw new IllegalArgumentException("Location ID, request, and token cannot be null");
        }

        log.debug("Updating location: {}", locationId);

        Location location = locationRepository.findById(locationId)
                .orElseThrow(() -> new NotFoundException("Location not found"));

        // Check for name conflicts if name is being changed
        if (!location.getName().equals(request.getName()) &&
            locationRepository.existsByHouseholdIdAndName(location.getHousehold().getId(), request.getName())) {
            throw new ConflictException("Location with name '" + request.getName() + "' already exists in this household");
        }

        location.setName(request.getName());
        location.setDescription(request.getDescription());

        Location savedLocation = locationRepository.save(location);
        log.debug("Successfully updated location: {}", locationId);

        return mapToResponse(savedLocation);
    }

    /**
     * Partially updates a location with the provided field values.
     */
    @Transactional
    public LocationResponse patchLocation(UUID locationId, Map<String, Object> patchData, JwtAuthenticationToken token) {
        if (locationId == null || patchData == null || token == null) {
            throw new IllegalArgumentException("Location ID, patch data, and token cannot be null");
        }

        if (patchData.isEmpty()) {
            throw new IllegalArgumentException("Patch data cannot be empty");
        }

        log.debug("Patching location: {} with data: {}", locationId, patchData);

        Location location = locationRepository.findById(locationId)
                .orElseThrow(() -> new NotFoundException("Location not found"));

        // Apply patches
        for (Map.Entry<String, Object> entry : patchData.entrySet()) {
            String field = entry.getKey();
            Object value = entry.getValue();

            switch (field) {
                case "name":
                    String newName = (String) value;
                    if (!location.getName().equals(newName) &&
                        locationRepository.existsByHouseholdIdAndName(location.getHousehold().getId(), newName)) {
                        throw new ConflictException("Location with name '" + newName + "' already exists in this household");
                    }
                    location.setName(newName);
                    break;
                case "description":
                    location.setDescription((String) value);
                    break;
                default:
                    throw new IllegalArgumentException("Invalid field: " + field);
            }
        }

        Location savedLocation = locationRepository.save(location);
        log.debug("Successfully patched location: {}", locationId);

        return mapToResponse(savedLocation);
    }

    /**
     * Deletes a location.
     */
    @Transactional
    public void deleteLocation(UUID locationId, JwtAuthenticationToken token) {
        if (locationId == null || token == null) {
            throw new IllegalArgumentException("Location ID and token cannot be null");
        }

        log.debug("Deleting location: {}", locationId);

        Location location = locationRepository.findById(locationId)
                .orElseThrow(() -> new NotFoundException("Location not found"));

        locationRepository.delete(location);
        log.debug("Successfully deleted location: {}", locationId);
    }

    /**
     * Simple search for locations by name or description.
     */
    @Transactional(readOnly = true)
    public List<LocationResponse> searchLocations(UUID householdId, String searchTerm, JwtAuthenticationToken token) {
        if (householdId == null || searchTerm == null || token == null) {
            throw new IllegalArgumentException("Household ID, search term, and token cannot be null");
        }

        log.debug("Searching locations in household: {} with term: {}", householdId, searchTerm);

        User currentUser = userService.getCurrentUser(token);
        validateHouseholdAccess(householdId, currentUser);

        List<Location> locations = locationRepository.searchByNameOrDescription(householdId, searchTerm);

        return locations.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Checks if a location name is available in a household.
     */
    @Transactional(readOnly = true)
    public boolean isLocationNameAvailable(UUID householdId, String name, JwtAuthenticationToken token) {
        if (householdId == null || name == null || token == null) {
            throw new IllegalArgumentException("Household ID, name, and token cannot be null");
        }

        User currentUser = userService.getCurrentUser(token);
        validateHouseholdAccess(householdId, currentUser);

        return !locationRepository.existsByHouseholdIdAndName(householdId, name);
    }

    /**
     * Validates that the current user has access to a household.
     */
    private void validateHouseholdAccess(UUID householdId, User user) {
        if (!householdRepository.existsById(householdId)) {
            throw new NotFoundException("Household not found");
        }

        householdMemberRepository.findByHouseholdIdAndUserId(householdId, user.getId())
                .orElseThrow(() -> new AccessDeniedException("You do not have access to this household"));
    }
    /**
     * Maps a Location entity to a LocationResponse DTO.
     */
    private LocationResponse mapToResponse(Location location) {
        return LocationResponse.builder()
                .id(location.getId())
                .name(location.getName())
                .description(location.getDescription())
                .householdId(location.getHousehold().getId())
                .householdName(location.getHousehold().getName())
                .createdAt(location.getCreatedAt())
                .updatedAt(location.getUpdatedAt())
                .build();
    }
}