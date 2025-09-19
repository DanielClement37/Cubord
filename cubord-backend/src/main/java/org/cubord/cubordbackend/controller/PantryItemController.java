package org.cubord.cubordbackend.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.cubord.cubordbackend.dto.*;
import org.cubord.cubordbackend.service.PantryItemService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.time.LocalDate;
import java.util.UUID;

/**
 * REST controller for pantry item management operations.
 * Handles HTTP requests related to creating, retrieving, updating, and deleting pantry items.
 * All authentication and authorization is handled at the service layer.
 * The controller validates input parameters and delegates business logic to the service layer.
 */
@RestController
@RequiredArgsConstructor
@Validated
public class PantryItemController {

    private final PantryItemService pantryItemService;

    // === Basic CRUD operations under /api/pantry-items ===

    /**
     * Creates a new pantry item.
     *
     * @param request DTO containing pantry item information
     * @param token JWT authentication token of the current user
     * @return ResponseEntity containing the created pantry item's details
     */
    @PostMapping("/api/pantry-items")
    public ResponseEntity<PantryItemResponse> createPantryItem(
            @Valid @RequestBody CreatePantryItemRequest request,
            JwtAuthenticationToken token) {
        PantryItemResponse response = pantryItemService.createPantryItem(request, token);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Gets a pantry item by ID.
     *
     * @param id The UUID of the pantry item
     * @param token JWT authentication token of the current user
     * @return ResponseEntity containing the pantry item's details
     */
    @GetMapping("/api/pantry-items/{id}")
    public ResponseEntity<PantryItemResponse> getPantryItem(
            @PathVariable @NotNull UUID id,
            JwtAuthenticationToken token) {
        PantryItemResponse response = pantryItemService.getPantryItemById(id, token);
        return ResponseEntity.ok(response);
    }

    /**
     * Updates a pantry item completely.
     *
     * @param id The UUID of the pantry item
     * @param request DTO containing updated pantry item information
     * @param token JWT authentication token of the current user
     * @return ResponseEntity containing the updated pantry item's details
     */
    @PutMapping("/api/pantry-items/{id}")
    public ResponseEntity<PantryItemResponse> updatePantryItem(
            @PathVariable @NotNull UUID id,
            @Valid @RequestBody UpdatePantryItemRequest request,
            JwtAuthenticationToken token) {
        PantryItemResponse response = pantryItemService.updatePantryItem(id, request, token);
        return ResponseEntity.ok(response);
    }

    /**
     * Partially updates a pantry item.
     *
     * @param id The UUID of the pantry item
     * @param patchData Map containing fields to update
     * @param token JWT authentication token of the current user
     * @return ResponseEntity containing the updated pantry item's details
     */
    @PatchMapping("/api/pantry-items/{id}")
    public ResponseEntity<PantryItemResponse> patchPantryItem(
            @PathVariable @NotNull UUID id,
            @RequestBody Map<String, Object> patchData,
            JwtAuthenticationToken token) {
        PantryItemResponse response = pantryItemService.patchPantryItem(id, patchData, token);
        return ResponseEntity.ok(response);
    }

    /**
     * Deletes a pantry item.
     *
     * @param id The UUID of the pantry item to delete
     * @param token JWT authentication token of the current user
     * @return ResponseEntity with no content
     */
    @DeleteMapping("/api/pantry-items/{id}")
    public ResponseEntity<Void> deletePantryItem(
            @PathVariable @NotNull UUID id,
            JwtAuthenticationToken token) {
        pantryItemService.deletePantryItem(id, token);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/pantry-items/batch")
    public ResponseEntity<List<PantryItemResponse>> createMultiplePantryItems(
            @Valid @RequestBody @NotEmpty List<CreatePantryItemRequest> requests,
            JwtAuthenticationToken token) {
        List<PantryItemResponse> response = pantryItemService.createMultiplePantryItems(requests, token);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Deletes multiple pantry items in a batch operation.
     *
     * @param itemIds List of pantry item UUIDs to delete
     * @param token JWT authentication token of the current user
     * @return ResponseEntity containing the number of deleted items
     */
    @DeleteMapping("/api/pantry-items/batch")
    public ResponseEntity<Map<String, Integer>> deleteMultiplePantryItems(
            @RequestBody @NotEmpty List<UUID> itemIds,
            JwtAuthenticationToken token) {
        int deletedCount = pantryItemService.deleteMultiplePantryItems(itemIds, token);
        return ResponseEntity.ok(Map.of("deletedCount", deletedCount));
    }

    // === Location-based endpoints ===

    /**
     * Gets all pantry items in a specific location.
     *
     * @param locationId The UUID of the location
     * @param token JWT authentication token of the current user
     * @return ResponseEntity containing list of pantry items in the location
     */
    @GetMapping("/api/locations/{locationId}/pantry-items")
    public ResponseEntity<List<PantryItemResponse>> getPantryItemsByLocation(
            @PathVariable @NotNull UUID locationId,
            JwtAuthenticationToken token) {
        List<PantryItemResponse> response = pantryItemService.getPantryItemsByLocation(locationId, token);
        return ResponseEntity.ok(response);
    }

    // === Household-based endpoints ===

    /**
     * Gets paginated pantry items for a household.
     *
     * @param householdId The UUID of the household
     * @param page Page number (default: 0)
     * @param size Page size (default: 20)
     * @param token JWT authentication token of the current user
     * @return ResponseEntity containing paginated pantry items
     */
    @GetMapping("/api/households/{householdId}/pantry-items")
    public ResponseEntity<Page<PantryItemResponse>> getPantryItemsByHousehold(
            @PathVariable @NotNull UUID householdId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            JwtAuthenticationToken token) {
        Pageable pageable = PageRequest.of(page, size);
        Page<PantryItemResponse> response = pantryItemService.getPantryItemsByHousehold(householdId, pageable, token);
        return ResponseEntity.ok(response);
    }

    /**
     * Gets low stock items for a household.
     *
     * @param householdId The UUID of the household
     * @param threshold Stock threshold (default: 5)
     * @param token JWT authentication token of the current user
     * @return ResponseEntity containing list of low stock items
     */
    @GetMapping("/api/households/{householdId}/pantry-items/low-stock")
    public ResponseEntity<List<PantryItemResponse>> getLowStockItems(
            @PathVariable @NotNull UUID householdId,
            @RequestParam(defaultValue = "5") int threshold,
            JwtAuthenticationToken token) {
        List<PantryItemResponse> response = pantryItemService.getLowStockItems(householdId, threshold, token);
        return ResponseEntity.ok(response);
    }

    /**
     * Gets expiring items for a household within a date range.
     *
     * @param householdId The UUID of the household
     * @param startDate Start date for expiration check (default: today)
     * @param endDate End date for expiration check (default: 7 days from today)
     * @param token JWT authentication token of the current user
     * @return ResponseEntity containing list of expiring items
     */
    @GetMapping("/api/households/{householdId}/pantry-items/expiring")
    public ResponseEntity<List<PantryItemResponse>> getExpiringItems(
            @PathVariable @NotNull UUID householdId,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            JwtAuthenticationToken token) {
        // Set defaults if not provided
        LocalDate actualStartDate = startDate != null ? startDate : LocalDate.now();
        LocalDate actualEndDate = endDate != null ? endDate : LocalDate.now().plusDays(7);
        
        List<PantryItemResponse> response = pantryItemService.getExpiringItems(householdId, actualStartDate, actualEndDate, token);
        return ResponseEntity.ok(response);
    }

    /**
     * Searches pantry items in a household by query string.
     *
     * @param householdId The UUID of the household
     * @param query Search query string
     * @param token JWT authentication token of the current user
     * @return ResponseEntity containing search results
     */
    @GetMapping("/api/households/{householdId}/pantry-items/search")
    public ResponseEntity<List<PantryItemResponse>> searchPantryItems(
            @PathVariable @NotNull UUID householdId,
            @RequestParam @NotNull String query,
            JwtAuthenticationToken token) {
        List<PantryItemResponse> response = pantryItemService.searchPantryItems(householdId, query, token);
        return ResponseEntity.ok(response);
    }

    /**
     * Gets pantry statistics for a household.
     *
     * @param householdId The UUID of the household
     * @param token JWT authentication token of the current user
     * @return ResponseEntity containing pantry statistics
     */
    @GetMapping("/api/households/{householdId}/pantry-items/statistics")
    public ResponseEntity<Map<String, Object>> getPantryStatistics(
            @PathVariable @NotNull UUID householdId,
            JwtAuthenticationToken token) {
        Map<String, Object> response = pantryItemService.getPantryStatistics(householdId, token);
        return ResponseEntity.ok(response);
    }
}