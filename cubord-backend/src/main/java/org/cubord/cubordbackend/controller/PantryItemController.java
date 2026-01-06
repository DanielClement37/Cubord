
package org.cubord.cubordbackend.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cubord.cubordbackend.dto.pantryItem.CreatePantryItemRequest;
import org.cubord.cubordbackend.dto.pantryItem.PantryItemResponse;
import org.cubord.cubordbackend.dto.pantryItem.UpdatePantryItemRequest;
import org.cubord.cubordbackend.service.PantryItemService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * REST controller for pantry item management operations.
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
 *   <li><strong>POST /api/pantry-items:</strong> Access to the location's household</li>
 *   <li><strong>GET /api/pantry-items/{id}:</strong> Access to the pantry item's household</li>
 *   <li><strong>PUT /api/pantry-items/{id}:</strong> Access to the pantry item's household</li>
 *   <li><strong>PATCH /api/pantry-items/{id}:</strong> Access to the pantry item's household</li>
 *   <li><strong>DELETE /api/pantry-items/{id}:</strong> Access to the pantry item's household</li>
 *   <li><strong>Location endpoints:</strong> Access to the location's household</li>
 *   <li><strong>Household endpoints:</strong> Access to the household</li>
 * </ul>
 *
 * <h2>Exception Handling</h2>
 * <p>All exceptions are handled by {@link org.cubord.cubordbackend.exception.RestExceptionHandler}
 * which provides consistent error responses with correlation IDs.</p>
 *
 * @see PantryItemService
 * @see org.cubord.cubordbackend.security.SecurityService
 */
@RestController
@RequiredArgsConstructor
@Validated
@Slf4j
public class PantryItemController {

    private final PantryItemService pantryItemService;

    // === Basic CRUD operations under /api/pantry-items ===

    /**
     * Creates a new pantry item.
     *
     * <p>Authorization: User must have access to the household containing the location.</p>
     *
     * @param request DTO containing pantry item information
     * @return ResponseEntity containing the created pantry item's details
     */
    @PostMapping("/api/pantry-items")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<PantryItemResponse> createPantryItem(
            @Valid @RequestBody CreatePantryItemRequest request) {
        log.debug("Creating pantry item for location: {}", request.getLocationId());

        PantryItemResponse response = pantryItemService.createPantryItem(request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .contentType(MediaType.APPLICATION_JSON)
                .body(response);
    }

    /**
     * Gets a pantry item by ID.
     *
     * <p>Authorization: User must have access to the household containing the pantry item.</p>
     *
     * @param id The UUID of the pantry item
     * @return ResponseEntity containing the pantry item's details
     */
    @GetMapping("/api/pantry-items/{id}")
    @PreAuthorize("@security.canAccessPantryItem(#id)")
    public ResponseEntity<PantryItemResponse> getPantryItem(@PathVariable @NotNull UUID id) {
        log.debug("Retrieving pantry item by ID: {}", id);

        PantryItemResponse response = pantryItemService.getPantryItemById(id);

        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(1, TimeUnit.MINUTES))
                .contentType(MediaType.APPLICATION_JSON)
                .body(response);
    }

    /**
     * Updates a pantry item completely.
     *
     * <p>Authorization: User must have access to the household containing the pantry item.</p>
     *
     * @param id      The UUID of the pantry item
     * @param request DTO containing updated pantry item information
     * @return ResponseEntity containing the updated pantry item's details
     */
    @PutMapping("/api/pantry-items/{id}")
    @PreAuthorize("@security.canAccessPantryItem(#id)")
    public ResponseEntity<PantryItemResponse> updatePantryItem(
            @PathVariable @NotNull UUID id,
            @Valid @RequestBody UpdatePantryItemRequest request) {
        log.debug("Updating pantry item: {}", id);

        PantryItemResponse response = pantryItemService.updatePantryItem(id, request);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(response);
    }

    /**
     * Partially updates a pantry item.
     *
     * <p>Authorization: User must have access to the household containing the pantry item.</p>
     *
     * <p>Supported fields: quantity, expirationDate, notes, locationId</p>
     *
     * @param id        The UUID of the pantry item
     * @param patchData Map containing fields to update
     * @return ResponseEntity containing the updated pantry item's details
     */
    @PatchMapping("/api/pantry-items/{id}")
    @PreAuthorize("@security.canAccessPantryItem(#id)")
    public ResponseEntity<PantryItemResponse> patchPantryItem(
            @PathVariable @NotNull UUID id,
            @RequestBody Map<String, Object> patchData) {
        log.debug("Patching pantry item {} with fields: {}", id, patchData.keySet());

        PantryItemResponse response = pantryItemService.patchPantryItem(id, patchData);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(response);
    }

    /**
     * Deletes a pantry item.
     *
     * <p>Authorization: User must have access to the household containing the pantry item.</p>
     *
     * @param id The UUID of the pantry item to delete
     * @return ResponseEntity with no content
     */
    @DeleteMapping("/api/pantry-items/{id}")
    @PreAuthorize("@security.canAccessPantryItem(#id)")
    public ResponseEntity<Void> deletePantryItem(@PathVariable @NotNull UUID id) {
        log.debug("Deleting pantry item: {}", id);

        pantryItemService.deletePantryItem(id);

        return ResponseEntity.noContent().build();
    }

    /**
     * Creates multiple pantry items in a batch operation.
     *
     * <p>Authorization: Each item's location access is checked individually during creation.</p>
     *
     * @param requests List of pantry item creation requests
     * @return ResponseEntity containing the created pantry items
     */
    @PostMapping("/api/pantry-items/batch")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<PantryItemResponse>> createMultiplePantryItems(
            @Valid @RequestBody @NotEmpty List<CreatePantryItemRequest> requests) {
        log.debug("Creating {} pantry items in batch", requests.size());

        List<PantryItemResponse> response = pantryItemService.createMultiplePantryItems(requests);

        return ResponseEntity.status(HttpStatus.CREATED)
                .contentType(MediaType.APPLICATION_JSON)
                .body(response);
    }

    /**
     * Deletes multiple pantry items in a batch operation.
     *
     * <p>Authorization: Each item's access is checked individually during deletion.
     * Items without access are skipped.</p>
     *
     * @param itemIds List of pantry item UUIDs to delete
     * @return ResponseEntity containing the number of deleted items
     */
    @DeleteMapping("/api/pantry-items/batch")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Integer>> deleteMultiplePantryItems(
            @RequestBody @NotEmpty List<UUID> itemIds) {
        log.debug("Deleting {} pantry items in batch", itemIds.size());

        int deletedCount = pantryItemService.deleteMultiplePantryItems(itemIds);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("deletedCount", deletedCount));
    }

    // === Location-based endpoints ===

    /**
     * Gets all pantry items in a specific location.
     *
     * <p>Authorization: User must have access to the household containing the location.</p>
     *
     * @param locationId The UUID of the location
     * @return ResponseEntity containing a list of pantry items in the location
     */
    @GetMapping("/api/locations/{locationId}/pantry-items")
    @PreAuthorize("@security.canAccessLocationForPantryItem(#locationId)")
    public ResponseEntity<List<PantryItemResponse>> getPantryItemsByLocation(
            @PathVariable @NotNull UUID locationId) {
        log.debug("Retrieving pantry items for location: {}", locationId);

        List<PantryItemResponse> response = pantryItemService.getPantryItemsByLocation(locationId);

        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(1, TimeUnit.MINUTES))
                .contentType(MediaType.APPLICATION_JSON)
                .body(response);
    }

    // === Household-based endpoints ===

    /**
     * Gets paginated pantry items for a household.
     *
     * <p>Authorization: User must have access to the household.</p>
     *
     * @param householdId The UUID of the household
     * @param page        Page number (default: 0)
     * @param size        Page size (default: 20)
     * @return ResponseEntity containing paginated pantry items
     */
    @GetMapping("/api/households/{householdId}/pantry-items")
    @PreAuthorize("@security.canAccessHousehold(#householdId)")
    public ResponseEntity<Page<PantryItemResponse>> getPantryItemsByHousehold(
            @PathVariable @NotNull UUID householdId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        log.debug("Retrieving pantry items for household: {} (page={}, size={})", householdId, page, size);

        Pageable pageable = PageRequest.of(page, size);
        Page<PantryItemResponse> response = pantryItemService.getPantryItemsByHousehold(householdId, pageable);

        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(30, TimeUnit.SECONDS))
                .contentType(MediaType.APPLICATION_JSON)
                .body(response);
    }

    /**
     * Gets low stock items for a household.
     *
     * <p>Authorization: User must have access to the household.</p>
     *
     * @param householdId The UUID of the household
     * @param threshold   Stock threshold (default: 5)
     * @return ResponseEntity containing a list of low stock items
     */
    @GetMapping("/api/households/{householdId}/pantry-items/low-stock")
    @PreAuthorize("@security.canAccessHousehold(#householdId)")
    public ResponseEntity<List<PantryItemResponse>> getLowStockItems(
            @PathVariable @NotNull UUID householdId,
            @RequestParam(defaultValue = "5") int threshold) {
        log.debug("Retrieving low stock items (threshold: {}) for household: {}", threshold, householdId);

        List<PantryItemResponse> response = pantryItemService.getLowStockItems(householdId, threshold);

        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(1, TimeUnit.MINUTES))
                .contentType(MediaType.APPLICATION_JSON)
                .body(response);
    }

    /**
     * Gets expiring items for a household within a date range.
     *
     * <p>Authorization: User must have access to the household.</p>
     *
     * @param householdId The UUID of the household
     * @param startDate   Start date for expiration check (default: today)
     * @param endDate     End date for expiration check (default: 7 days from today)
     * @return ResponseEntity containing a list of expiring items
     */
    @GetMapping("/api/households/{householdId}/pantry-items/expiring")
    @PreAuthorize("@security.canAccessHousehold(#householdId)")
    public ResponseEntity<List<PantryItemResponse>> getExpiringItems(
            @PathVariable @NotNull UUID householdId,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate) {
        // Set defaults if not provided
        LocalDate actualStartDate = startDate != null ? startDate : LocalDate.now();
        LocalDate actualEndDate = endDate != null ? endDate : LocalDate.now().plusDays(7);

        log.debug("Retrieving expiring items for household: {} (from {} to {})",
                householdId, actualStartDate, actualEndDate);

        List<PantryItemResponse> response = pantryItemService.getExpiringItems(
                householdId, actualStartDate, actualEndDate);

        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(5, TimeUnit.MINUTES))
                .contentType(MediaType.APPLICATION_JSON)
                .body(response);
    }

    /**
     * Searches pantry items in a household by query string.
     *
     * <p>Authorization: User must have access to the household.</p>
     *
     * @param householdId The UUID of the household
     * @param query       Search query string
     * @return ResponseEntity containing search results
     */
    @GetMapping("/api/households/{householdId}/pantry-items/search")
    @PreAuthorize("@security.canAccessHousehold(#householdId)")
    public ResponseEntity<List<PantryItemResponse>> searchPantryItems(
            @PathVariable @NotNull UUID householdId,
            @RequestParam @NotNull String query) {
        log.debug("Searching pantry items in household: {} with query: {}", householdId, query);

        List<PantryItemResponse> response = pantryItemService.searchPantryItems(householdId, query);

        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(1, TimeUnit.MINUTES))
                .contentType(MediaType.APPLICATION_JSON)
                .body(response);
    }

    /**
     * Gets pantry statistics for a household.
     *
     * <p>Authorization: User must have access to the household.</p>
     *
     * @param householdId The UUID of the household
     * @return ResponseEntity containing pantry statistics
     */
    @GetMapping("/api/households/{householdId}/pantry-items/statistics")
    @PreAuthorize("@security.canAccessHousehold(#householdId)")
    public ResponseEntity<Map<String, Object>> getPantryStatistics(
            @PathVariable @NotNull UUID householdId) {
        log.debug("Retrieving pantry statistics for household: {}", householdId);

        Map<String, Object> response = pantryItemService.getPantryStatistics(householdId);

        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(30, TimeUnit.SECONDS))
                .contentType(MediaType.APPLICATION_JSON)
                .body(response);
    }
}