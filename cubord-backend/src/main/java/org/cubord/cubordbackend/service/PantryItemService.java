
package org.cubord.cubordbackend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import org.cubord.cubordbackend.domain.*;
import org.cubord.cubordbackend.dto.location.LocationResponse;
import org.cubord.cubordbackend.dto.pantryItem.CreatePantryItemRequest;
import org.cubord.cubordbackend.dto.pantryItem.PantryItemResponse;
import org.cubord.cubordbackend.dto.pantryItem.UpdatePantryItemRequest;
import org.cubord.cubordbackend.dto.product.ProductResponse;
import org.cubord.cubordbackend.exception.*;
import org.cubord.cubordbackend.repository.*;
import org.cubord.cubordbackend.security.SecurityService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service class for managing pantry items.
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
 *   <li><strong>Create:</strong> User must have access to the household containing the location</li>
 *   <li><strong>Read:</strong> User must have access to the household containing the pantry item</li>
 *   <li><strong>Update:</strong> User must have access to the household containing the pantry item</li>
 *   <li><strong>Delete:</strong> User must have access to the household containing the pantry item</li>
 * </ul>
 * 
 * <h2>Business Logic</h2>
 * <p>Pantry items with identical product, location, and expiration date are automatically consolidated.
 * This prevents duplicate entries and simplifies inventory management.</p>
 *
 * @see SecurityService
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PantryItemService {

    private final PantryItemRepository pantryItemRepository;
    private final LocationRepository locationRepository;
    private final ProductRepository productRepository;
    private final SecurityService securityService;

    // ==================== Create Operations ====================

    /**
     * Creates a new pantry item or consolidates with an existing one.
     *
     * <p>Authorization: User must have access to the household containing the location.
     * This is enforced by checking if the user is a member of the household that owns the location.</p>
     *
     * <p>If an item with the same product, location, and expiration date exists,
     * the quantities are consolidated instead of creating a duplicate.</p>
     *
     * @param request DTO containing pantry item information
     * @return PantryItemResponse containing the created or updated pantry item's details
     * @throws ValidationException             if request is invalid
     * @throws NotFoundException               if location or product not found
     * @throws InsufficientPermissionException if user cannot access the household
     * @throws DataIntegrityException if save operation fails
     */
    @Transactional
    @PreAuthorize("@security.canAccessLocationForPantryItem(#request.locationId)")
    public PantryItemResponse createPantryItem(CreatePantryItemRequest request) {
        if (request == null) {
            throw new ValidationException("Pantry item request cannot be null");
        }

        UUID currentUserId = securityService.getCurrentUserId();
        log.debug("User {} creating pantry item in location: {}", currentUserId, request.getLocationId());

        // Validate required fields
        validateCreateRequest(request);

        // Load and validate location
        Location location = locationRepository.findById(request.getLocationId())
                .orElseThrow(() -> new NotFoundException("Location not found with ID: " + request.getLocationId()));

        // Load and validate product
        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new NotFoundException("Product not found with ID: " + request.getProductId()));

        // Check for existing item to consolidate (using correct method name from repository)
        Optional<PantryItem> existingItem = pantryItemRepository.findByLocationIdAndProductIdAndExpirationDate(
                request.getLocationId(),
                request.getProductId(),
                request.getExpirationDate()
        );

        if (existingItem.isPresent()) {
            // Consolidate quantities
            PantryItem item = existingItem.get();
            int newQuantity = item.getQuantity() + request.getQuantity();
            item.setQuantity(newQuantity);

            log.debug("Consolidating pantry item {} with {} units (new total: {})",
                    item.getId(), request.getQuantity(), newQuantity);

            PantryItem saved = pantryItemRepository.save(item);
            return mapToResponse(saved);
        }

        // Create new pantry item
        PantryItem pantryItem = PantryItem.builder()
                .product(product)
                .location(location)
                .quantity(request.getQuantity())
                .expirationDate(request.getExpirationDate())
                .notes(request.getNotes())
                .build();

        try {
            PantryItem saved = pantryItemRepository.save(pantryItem);
            log.debug("Created new pantry item with ID: {}", saved.getId());
            return mapToResponse(saved);
        } catch (Exception e) {
            log.error("Failed to save pantry item for user {}", currentUserId, e);
            throw new DataIntegrityException("Failed to create pantry item: " + e.getMessage());
        }
    }

    /**
     * Creates multiple pantry items in a single batch operation.
     *
     * <p>Authorization: User must have access to the household for each location.
     * Each item is validated individually.</p>
     *
     * @param requests List of create requests
     * @return List of created/updated pantry item responses
     * @throws ValidationException if any request is invalid
     */
    @Transactional
    public List<PantryItemResponse> createMultiplePantryItems(List<CreatePantryItemRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            throw new ValidationException("Pantry item requests list cannot be null or empty");
        }

        UUID currentUserId = securityService.getCurrentUserId();
        log.debug("User {} creating {} pantry items in batch", currentUserId, requests.size());

        return requests.stream()
                .map(this::createPantryItem)
                .collect(Collectors.toList());
    }

    // ==================== Read Operations ====================

    /**
     * Retrieves a pantry item by its ID.
     *
     * <p>Authorization: User must have access to the household containing the pantry item.</p>
     *
     * @param id UUID of the pantry item to retrieve
     * @return PantryItemResponse containing the pantry item's details
     * @throws ValidationException             if id is null
     * @throws NotFoundException               if pantry item not found
     * @throws InsufficientPermissionException if user cannot access the household
     */
    @Transactional(readOnly = true)
    @PreAuthorize("@security.canAccessPantryItem(#id)")
    public PantryItemResponse getPantryItemById(UUID id) {
        if (id == null) {
            throw new ValidationException("Pantry item ID cannot be null");
        }

        UUID currentUserId = securityService.getCurrentUserId();
        log.debug("User {} retrieving pantry item by ID: {}", currentUserId, id);

        PantryItem pantryItem = pantryItemRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Pantry item not found with ID: " + id));

        return mapToResponse(pantryItem);
    }

    /**
     * Retrieves all pantry items in a specific location.
     * 
     * <p>Authorization: User must have access to the household containing the location.</p>
     *
     * @param locationId UUID of the location
     * @return List of pantry items in the location
     * @throws ValidationException if locationId is null
     * @throws InsufficientPermissionException if user cannot access the household
     */
    @Transactional(readOnly = true)
    @PreAuthorize("@security.canAccessLocationForPantryItem(#locationId)")
    public List<PantryItemResponse> getPantryItemsByLocation(UUID locationId) {
        if (locationId == null) {
            throw new ValidationException("Location ID cannot be null");
        }

        UUID currentUserId = securityService.getCurrentUserId();
        log.debug("User {} retrieving pantry items for location: {}", currentUserId, locationId);

        List<PantryItem> items = pantryItemRepository.findByLocationId(locationId);
        return items.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Retrieves paginated pantry items for a household.
     * 
     * <p>Authorization: User must have access to the household.</p>
     *
     * @param householdId UUID of the household
     * @param pageable Pagination information
     * @return Page of pantry items in the household
     * @throws ValidationException if householdId is null
     * @throws InsufficientPermissionException if user cannot access the household
     */
    @Transactional(readOnly = true)
    @PreAuthorize("@security.canAccessHousehold(#householdId)")
    public Page<PantryItemResponse> getPantryItemsByHousehold(UUID householdId, Pageable pageable) {
        if (householdId == null) {
            throw new ValidationException("Household ID cannot be null");
        }

        UUID currentUserId = securityService.getCurrentUserId();
        log.debug("User {} retrieving pantry items for household: {}", currentUserId, householdId);

        // Use correct method name from repository
        Page<PantryItem> items = pantryItemRepository.findByLocation_HouseholdId(householdId, pageable);
        return items.map(this::mapToResponse);
    }

    /**
     * Retrieves low stock items for a household.
     * 
     * <p>Authorization: User must have access to the household.</p>
     *
     * @param householdId UUID of the household
     * @param threshold Stock threshold (items with quantity <= threshold)
     * @return List of low stock pantry items
     * @throws ValidationException if householdId is null or threshold is negative
     * @throws InsufficientPermissionException if user cannot access the household
     */
    @Transactional(readOnly = true)
    @PreAuthorize("@security.canAccessHousehold(#householdId)")
    public List<PantryItemResponse> getLowStockItems(UUID householdId, int threshold) {
        if (householdId == null) {
            throw new ValidationException("Household ID cannot be null");
        }
        if (threshold < 0) {
            throw new ValidationException("Threshold cannot be negative");
        }

        UUID currentUserId = securityService.getCurrentUserId();
        log.debug("User {} retrieving low stock items (threshold: {}) for household: {}",
                currentUserId, threshold, householdId);

        // Use correct method name from repository
        List<PantryItem> items = pantryItemRepository.findLowStockItemsInHousehold(householdId, threshold);
        return items.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Retrieves expiring items for a household within the specified number of days.
     * 
     * <p>Authorization: User must have access to the household.</p>
     *
     * @param householdId UUID of the household
     * @param daysUntilExpiration Number of days to look ahead
     * @return List of expiring pantry items
     * @throws ValidationException if householdId is null or daysUntilExpiration is negative
     * @throws InsufficientPermissionException if user cannot access the household
     */
    @Transactional(readOnly = true)
    @PreAuthorize("@security.canAccessHousehold(#householdId)")
    public List<PantryItemResponse> getExpiringItems(UUID householdId, int daysUntilExpiration) {
        if (householdId == null) {
            throw new ValidationException("Household ID cannot be null");
        }
        if (daysUntilExpiration < 0) {
            throw new ValidationException("Days until expiration cannot be negative");
        }

        UUID currentUserId = securityService.getCurrentUserId();
        log.debug("User {} retrieving expiring items (days: {}) for household: {}",
                currentUserId, daysUntilExpiration, householdId);

        LocalDate startDate = LocalDate.now();
        LocalDate endDate = LocalDate.now().plusDays(daysUntilExpiration);
        
        // Use correct method name from repository
        List<PantryItem> items = pantryItemRepository.findExpiringItemsInHouseholdBetweenDates(
                householdId, startDate, endDate);
        return items.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Retrieves expiring items for a household within a date range.
     * 
     * <p>Authorization: User must have access to the household.</p>
     *
     * @param householdId UUID of the household
     * @param startDate Start date for expiration check
     * @param endDate End date for expiration check
     * @return List of expiring pantry items
     * @throws ValidationException if householdId, startDate, or endDate is null
     * @throws InsufficientPermissionException if user cannot access the household
     */
    @Transactional(readOnly = true)
    @PreAuthorize("@security.canAccessHousehold(#householdId)")
    public List<PantryItemResponse> getExpiringItems(UUID householdId, LocalDate startDate, LocalDate endDate) {
        if (householdId == null) {
            throw new ValidationException("Household ID cannot be null");
        }
        if (startDate == null || endDate == null) {
            throw new ValidationException("Start date and end date cannot be null");
        }

        UUID currentUserId = securityService.getCurrentUserId();
        log.debug("User {} retrieving expiring items for household {} between {} and {}",
                currentUserId, householdId, startDate, endDate);

        List<PantryItem> items = pantryItemRepository.findExpiringItemsInHouseholdBetweenDates(
                householdId, startDate, endDate);
        return items.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Searches pantry items in a household by search term.
     *
     * @param householdId UUID of the household
     * @param searchTerm Term to search for in product names, brands, and notes
     * @return List of PantryItemResponse objects matching the search
     * @throws ValidationException if search term is null or empty
     * @throws InsufficientPermissionException if user is not a member of the household
     */
    @Transactional(readOnly = true)
    @PreAuthorize("@security.canAccessHousehold(#householdId)")
    public List<PantryItemResponse> searchPantryItems(UUID householdId, String searchTerm) {
        if (householdId == null) {
            throw new ValidationException("Household ID cannot be null");
        }
        if (searchTerm == null || searchTerm.trim().isEmpty()) {
            throw new ValidationException("Search term cannot be null or empty");
        }

        UUID currentUserId = securityService.getCurrentUserId();
        log.debug("User {} searching pantry items in household {} with term: {}",
                currentUserId, householdId, searchTerm);

        List<PantryItem> items = pantryItemRepository.searchItemsInHousehold(householdId, searchTerm);
        return items.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Gets pantry statistics for a household.
     * 
     * <p>Authorization: User must have access to the household.</p>
     *
     * @param householdId UUID of the household
     * @return Map containing pantry statistics (totalItems, distinctProducts, lowStockCount, expiringCount, noExpirationDateCount)
     * @throws ValidationException if householdId is null
     * @throws InsufficientPermissionException if user cannot access the household
     */
    @Transactional(readOnly = true)
    @PreAuthorize("@security.canAccessHousehold(#householdId)")
    public Map<String, Object> getPantryStatistics(UUID householdId) {
        if (householdId == null) {
            throw new ValidationException("Household ID cannot be null");
        }

        UUID currentUserId = securityService.getCurrentUserId();
        log.debug("User {} retrieving pantry statistics for household: {}", currentUserId, householdId);

        Map<String, Object> statistics = new HashMap<>();
        
        // Total items count
        long totalItems = pantryItemRepository.countByLocation_HouseholdId(householdId);
        statistics.put("totalItems", totalItems);
        
        // Distinct products count
        long distinctProducts = pantryItemRepository.countDistinctProductsByHouseholdId(householdId);
        statistics.put("distinctProducts", distinctProducts);
        
        // Low stock items (threshold of 5)
        List<PantryItem> lowStockItems = pantryItemRepository.findLowStockItemsInHousehold(householdId, 5);
        statistics.put("lowStockCount", lowStockItems.size());
        
        // Expiring items (within 7 days)
        LocalDate sevenDaysFromNow = LocalDate.now().plusDays(7);
        long expiringCount = pantryItemRepository.countExpiringItemsByHouseholdIdAndDate(householdId, sevenDaysFromNow);
        statistics.put("expiringCount", expiringCount);
        
        // Items without expiration date
        List<PantryItem> noExpirationItems = pantryItemRepository.findByExpirationDateIsNullAndLocation_HouseholdId(householdId);
        statistics.put("noExpirationDateCount", noExpirationItems.size());
        
        return statistics;
    }

    // ==================== Update Operations ====================

    /**
     * Updates a pantry item completely.
     *
     * <p>Authorization: User must have access to the household containing the pantry item.</p>
     *
     * @param id      UUID of the pantry item to update
     * @param request DTO containing updated pantry item information
     * @return PantryItemResponse containing the updated pantry item's details
     * @throws ValidationException             if id or request is null
     * @throws NotFoundException               if pantry item or location not found
     * @throws InsufficientPermissionException if user cannot access the household
     */
    @Transactional
    @PreAuthorize("@security.canAccessPantryItem(#id)")
    public PantryItemResponse updatePantryItem(UUID id, UpdatePantryItemRequest request) {
        if (id == null) {
            throw new ValidationException("Pantry item ID cannot be null");
        }
        if (request == null) {
            throw new ValidationException("Update request cannot be null");
        }

        UUID currentUserId = securityService.getCurrentUserId();
        log.debug("User {} updating pantry item: {}", currentUserId, id);

        // Validate update request
        validateUpdateRequest(request);

        PantryItem pantryItem = pantryItemRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Pantry item not found with ID: " + id));

        // Update location if provided
        if (request.getLocationId() != null && !request.getLocationId().equals(pantryItem.getLocation().getId())) {
            // Verify new location access
            if (!securityService.canAccessLocationForPantryItem(request.getLocationId())) {
                throw new InsufficientPermissionException("You do not have access to the target location");
            }

            Location newLocation = locationRepository.findById(request.getLocationId())
                    .orElseThrow(() -> new NotFoundException("Location not found with ID: " + request.getLocationId()));
            pantryItem.setLocation(newLocation);
        }

        // Update other fields
        pantryItem.setQuantity(request.getQuantity());
        pantryItem.setExpirationDate(request.getExpirationDate());
        pantryItem.setNotes(request.getNotes());

        PantryItem updated = pantryItemRepository.save(pantryItem);
        log.debug("Updated pantry item: {}", id);
        return mapToResponse(updated);
    }

    /**
     * Partially updates a pantry item.
     *
     * <p>Authorization: User must have access to the household containing the pantry item.</p>
     *
     * @param id        UUID of the pantry item to patch
     * @param patchData Map containing fields to update
     * @return PantryItemResponse containing the updated pantry item's details
     * @throws ValidationException             if id or patchData is null
     * @throws NotFoundException               if pantry item not found
     * @throws InsufficientPermissionException if user cannot access the household
     */
    @Transactional
    @PreAuthorize("@security.canAccessPantryItem(#id)")
    public PantryItemResponse patchPantryItem(UUID id, Map<String, Object> patchData) {
        if (id == null) {
            throw new ValidationException("Pantry item ID cannot be null");
        }
        if (patchData == null || patchData.isEmpty()) {
            throw new ValidationException("Patch data cannot be null or empty");
        }

        UUID currentUserId = securityService.getCurrentUserId();
        log.debug("User {} patching pantry item: {}", currentUserId, id);

        PantryItem pantryItem = pantryItemRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Pantry item not found with ID: " + id));

        // Apply patch operations
        applyPatchOperations(pantryItem, patchData);

        PantryItem updated = pantryItemRepository.save(pantryItem);
        log.debug("Patched pantry item: {}", id);
        return mapToResponse(updated);
    }

    // ==================== Delete Operations ====================

    /**
     * Deletes a pantry item.
     *
     * <p>Authorization: User must have access to the household containing the pantry item.</p>
     *
     * @param id UUID of the pantry item to delete
     * @throws ValidationException             if id is null
     * @throws NotFoundException               if pantry item not found
     * @throws InsufficientPermissionException if user cannot access the household
     */
    @Transactional
    @PreAuthorize("@security.canAccessPantryItem(#id)")
    public void deletePantryItem(UUID id) {
        if (id == null) {
            throw new ValidationException("Pantry item ID cannot be null");
        }

        UUID currentUserId = securityService.getCurrentUserId();
        log.debug("User {} deleting pantry item: {}", currentUserId, id);

        PantryItem pantryItem = pantryItemRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Pantry item not found with ID: " + id));

        pantryItemRepository.delete(pantryItem);
        log.debug("Deleted pantry item: {}", id);
    }

    /**
     * Deletes multiple pantry items in a batch operation.
     *
     * <p>Authorization: User must have access to the household for each pantry item.
     * Items the user doesn't have access to are skipped with a warning.</p>
     *
     * @param itemIds List of pantry item UUIDs to delete
     * @return Number of items successfully deleted
     * @throws ValidationException if itemIds is null or empty
     */
    @Transactional
    public int deleteMultiplePantryItems(List<UUID> itemIds) {
        if (itemIds == null || itemIds.isEmpty()) {
            throw new ValidationException("Item IDs list cannot be null or empty");
        }

        UUID currentUserId = securityService.getCurrentUserId();
        log.debug("User {} deleting {} pantry items in batch", currentUserId, itemIds.size());

        int deletedCount = 0;
        for (UUID itemId : itemIds) {
            try {
                if (securityService.canAccessPantryItem(itemId)) {
                    deletePantryItem(itemId);
                    deletedCount++;
                } else {
                    log.warn("User {} attempted to delete pantry item {} without access", currentUserId, itemId);
                }
            } catch (NotFoundException e) {
                log.warn("Pantry item {} not found during batch delete", itemId);
            }
        }

        log.debug("Deleted {} out of {} pantry items", deletedCount, itemIds.size());
        return deletedCount;
    }

    // ==================== Helper Methods ====================

    /**
     * Validates a create pantry item request.
     *
     * @param request Request to validate
     * @throws ValidationException if validation fails
     */
    private void validateCreateRequest(CreatePantryItemRequest request) {
        if (request.getProductId() == null) {
            throw new ValidationException("Product ID cannot be null");
        }
        if (request.getLocationId() == null) {
            throw new ValidationException("Location ID cannot be null");
        }
        if (request.getQuantity() == null || request.getQuantity() < 0) {
            throw new ValidationException("Quantity must be a non-negative value");
        }
    }

    /**
     * Validates an update pantry item request.
     *
     * @param request Request to validate
     * @throws ValidationException if validation fails
     */
    private void validateUpdateRequest(UpdatePantryItemRequest request) {
        if (request.getQuantity() != null && request.getQuantity() < 0) {
            throw new ValidationException("Quantity must be a non-negative value");
        }
    }

    /**
     * Applies patch operations to a pantry item.
     *
     * @param pantryItem Pantry item to patch
     * @param patchData  Patch data
     * @throws ValidationException if patch data is invalid
     */
    private void applyPatchOperations(PantryItem pantryItem, Map<String, Object> patchData) {
        patchData.forEach((key, value) -> {
            switch (key) {
                case "quantity":
                    if (value instanceof Number) {
                        int quantity = ((Number) value).intValue();
                        if (quantity < 0) {
                            throw new ValidationException("Quantity must be non-negative");
                        }
                        pantryItem.setQuantity(quantity);
                    }
                    break;
                case "expirationDate":
                    if (value == null) {
                        pantryItem.setExpirationDate(null);
                    } else if (value instanceof String) {
                        pantryItem.setExpirationDate(LocalDate.parse((String) value));
                    }
                    break;
                case "notes":
                    pantryItem.setNotes(value != null ? value.toString() : null);
                    break;
                case "locationId":
                    if (value instanceof String) {
                        UUID locationId = UUID.fromString((String) value);
                        if (!securityService.canAccessLocationForPantryItem(locationId)) {
                            throw new InsufficientPermissionException("You do not have access to the target location");
                        }
                        Location location = locationRepository.findById(locationId)
                                .orElseThrow(() -> new NotFoundException("Location not found"));
                        pantryItem.setLocation(location);
                    }
                    break;
                default:
                    log.warn("Unknown patch field: {}", key);
            }
        });
    }

    /**
     * Maps a PantryItem entity to a PantryItemResponse DTO.
     *
     * @param pantryItem Entity to map
     * @return PantryItemResponse DTO
     */
    private PantryItemResponse mapToResponse(PantryItem pantryItem) {
        return PantryItemResponse.builder()
                .id(pantryItem.getId())
                .product(ProductResponse.builder()
                        .id(pantryItem.getProduct().getId())
                        .name(pantryItem.getProduct().getName())
                        .brand(pantryItem.getProduct().getBrand())
                        .category(pantryItem.getProduct().getCategory())
                        .upc(pantryItem.getProduct().getUpc())
                        .build())
                .location(LocationResponse.builder()
                        .id(pantryItem.getLocation().getId())
                        .name(pantryItem.getLocation().getName())
                        .description(pantryItem.getLocation().getDescription())
                        .householdId(pantryItem.getLocation().getHousehold().getId())
                        .build())
                .quantity(pantryItem.getQuantity())
                .expirationDate(pantryItem.getExpirationDate())
                .notes(pantryItem.getNotes())
                .createdAt(pantryItem.getCreatedAt())
                .updatedAt(pantryItem.getUpdatedAt())
                .build();
    }

    /**
     * Retrieves a pantry item using JWT token for authentication.
     *
     * @deprecated Use {@link #getPantryItemById(UUID)} instead.
     */
    @Deprecated(since = "2.0", forRemoval = true)
    @Transactional
    public PantryItemResponse getPantryItemById(UUID id,
                                                org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken token) {
        log.warn("DEPRECATED: getPantryItemById(UUID, JwtAuthenticationToken) called. " +
                "Migrate to getPantryItemById(UUID) for improved security architecture. " +
                "The token parameter is ignored - using SecurityContext instead.");
        return getPantryItemById(id);
    }

    /**
     * Updates a pantry item using JWT token for authentication.
     *
     * @deprecated Use {@link #updatePantryItem(UUID, UpdatePantryItemRequest)} instead.
     */
    @Deprecated(since = "2.0", forRemoval = true)
    @Transactional
    public PantryItemResponse updatePantryItem(UUID id, UpdatePantryItemRequest request,
                                               org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken token) {
        log.warn("DEPRECATED: updatePantryItem(UUID, UpdatePantryItemRequest, JwtAuthenticationToken) called. " +
                "Migrate to updatePantryItem(UUID, UpdatePantryItemRequest) for improved security architecture. " +
                "The token parameter is ignored - using SecurityContext instead.");
        return updatePantryItem(id, request);
    }

    /**
     * Patches a pantry item using JWT token for authentication.
     *
     * @deprecated Use {@link #patchPantryItem(UUID, Map)} instead.
     */
    @Deprecated(since = "2.0", forRemoval = true)
    @Transactional
    public PantryItemResponse patchPantryItem(UUID id, Map<String, Object> patchData,
                                              org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken token) {
        log.warn("DEPRECATED: patchPantryItem(UUID, Map, JwtAuthenticationToken) called. " +
                "Migrate to patchPantryItem(UUID, Map) for improved security architecture. " +
                "The token parameter is ignored - using SecurityContext instead.");
        return patchPantryItem(id, patchData);
    }

    /**
     * Deletes a pantry item using JWT token for authentication.
     *
     * @deprecated Use {@link #deletePantryItem(UUID)} instead.
     */
    @Deprecated(since = "2.0", forRemoval = true)
    @Transactional
    public void deletePantryItem(UUID id,
                                 org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken token) {
        log.warn("DEPRECATED: deletePantryItem(UUID, JwtAuthenticationToken) called. " +
                "Migrate to deletePantryItem(UUID) for improved security architecture. " +
                "The token parameter is ignored - using SecurityContext instead.");
        deletePantryItem(id);
    }

    /**
     * Creates multiple pantry items using JWT token for authentication.
     *
     * @deprecated Use {@link #createMultiplePantryItems(List)} instead.
     */
    @Deprecated(since = "2.0", forRemoval = true)
    @Transactional
    public List<PantryItemResponse> createMultiplePantryItems(List<CreatePantryItemRequest> requests,
                                                              org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken token) {
        log.warn("DEPRECATED: createMultiplePantryItems(List, JwtAuthenticationToken) called. " +
                "Migrate to createMultiplePantryItems(List) for improved security architecture. " +
                "The token parameter is ignored - using SecurityContext instead.");
        return createMultiplePantryItems(requests);
    }

    /**
     * Deletes multiple pantry items using JWT token for authentication.
     *
     * @deprecated Use {@link #deleteMultiplePantryItems(List)} instead.
     */
    @Deprecated(since = "2.0", forRemoval = true)
    @Transactional
    public int deleteMultiplePantryItems(List<UUID> itemIds,
                                         org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken token) {
        log.warn("DEPRECATED: deleteMultiplePantryItems(List, JwtAuthenticationToken) called. " +
                "Migrate to deleteMultiplePantryItems(List) for improved security architecture. " +
                "The token parameter is ignored - using SecurityContext instead.");
        return deleteMultiplePantryItems(itemIds);
    }

    /**
     * Retrieves pantry items by location using JWT token for authentication.
     *
     * @deprecated Use {@link #getPantryItemsByLocation(UUID)} instead.
     */
    @Deprecated(since = "2.0", forRemoval = true)
    @Transactional
    public List<PantryItemResponse> getPantryItemsByLocation(UUID locationId,
                                                             org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken token) {
        log.warn("DEPRECATED: getPantryItemsByLocation(UUID, JwtAuthenticationToken) called. " +
                "Migrate to getPantryItemsByLocation(UUID) for improved security architecture. " +
                "The token parameter is ignored - using SecurityContext instead.");
        return getPantryItemsByLocation(locationId);
    }

    /**
     * Retrieves pantry items by household using JWT token for authentication.
     *
     * @deprecated Use {@link #getPantryItemsByHousehold(UUID, Pageable)} instead.
     */
    @Deprecated(since = "2.0", forRemoval = true)
    @Transactional
    public Page<PantryItemResponse> getPantryItemsByHousehold(UUID householdId, Pageable pageable,
                                                              org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken token) {
        log.warn("DEPRECATED: getPantryItemsByHousehold(UUID, Pageable, JwtAuthenticationToken) called. " +
                "Migrate to getPantryItemsByHousehold(UUID, Pageable) for improved security architecture. " +
                "The token parameter is ignored - using SecurityContext instead.");
        return getPantryItemsByHousehold(householdId, pageable);
    }

    /**
     * Retrieves low stock items using JWT token for authentication.
     *
     * @deprecated Use {@link #getLowStockItems(UUID, int)} instead.
     */
    @Deprecated(since = "2.0", forRemoval = true)
    @Transactional
    public List<PantryItemResponse> getLowStockItems(UUID householdId, int threshold,
                                                     org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken token) {
        log.warn("DEPRECATED: getLowStockItems(UUID, int, JwtAuthenticationToken) called. " +
                "Migrate to getLowStockItems(UUID, int) for improved security architecture. " +
                "The token parameter is ignored - using SecurityContext instead.");
        return getLowStockItems(householdId, threshold);
    }

    /**
     * Retrieves expiring items using JWT token for authentication.
     *
     * @deprecated Use {@link #getExpiringItems(UUID, int)} instead.
     */
    @Deprecated(since = "2.0", forRemoval = true)
    @Transactional
    public List<PantryItemResponse> getExpiringItems(UUID householdId, int daysUntilExpiration,
                                                     org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken token) {
        log.warn("DEPRECATED: getExpiringItems(UUID, int, JwtAuthenticationToken) called. " +
                "Migrate to getExpiringItems(UUID, int) for improved security architecture. " +
                "The token parameter is ignored - using SecurityContext instead.");
        return getExpiringItems(householdId, daysUntilExpiration);
    }

    /**
     * Searches pantry items using JWT token for authentication.
     *
     * @deprecated Use {@link #searchPantryItems(UUID, String)} instead.
     */
    @Deprecated(since = "2.0", forRemoval = true)
    @Transactional
    public List<PantryItemResponse> searchPantryItems(UUID householdId, String searchTerm,
                                                          org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken token) {
        log.warn("DEPRECATED: searchPantryItems(UUID, String, JwtAuthenticationToken) called. " +
                "Migrate to searchPantryItems(UUID, String) for improved security architecture. " +
                "The token parameter is ignored - using SecurityContext instead.");
        return searchPantryItems(householdId, searchTerm);
    }

        /**
         * Creates a pantry item using JWT token for authentication.
         * 
         * @deprecated Use {@link #createPantryItem(CreatePantryItemRequest)} instead.
         *             This method is maintained for backward compatibility with controllers
         *             that haven't been migrated to the new security architecture.
         *             Token-based authentication is now handled by Spring Security filters.
         * 
         * @param request DTO containing pantry item information
         * @param token JWT authentication token (ignored in favor of SecurityContext)
         * @return PantryItemResponse containing the created pantry item's details
         */
        @Deprecated(since = "2.0", forRemoval = true)
        @Transactional
        public PantryItemResponse createPantryItem(CreatePantryItemRequest request,
                                                   org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken token) {
            log.warn("DEPRECATED: createPantryItem(CreatePantryItemRequest, JwtAuthenticationToken) called. " +
                    "Migrate to createPantryItem(CreatePantryItemRequest) for improved security architecture. " +
                    "The token parameter is ignored - using SecurityContext instead.");
            return createPantryItem(request);
        }

        /**
         * Retrieves expiring items within a date range using JWT token for authentication.
         *
         * @param householdId UUID of the household
         * @param startDate Start date for expiration check
         * @param endDate End date for expiration check
         * @param token JWT authentication token (ignored in favor of SecurityContext)
         * @return List of expiring pantry items
         * @deprecated Use {@link #getExpiringItems(UUID, int)} instead, or call the repository method directly
         *             if you need specific date range support.
         */
        @Deprecated(since = "2.0", forRemoval = true)
        @Transactional(readOnly = true)
        public List<PantryItemResponse> getExpiringItems(UUID householdId, LocalDate startDate, LocalDate endDate,
                                                         org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken token) {
            log.warn("DEPRECATED: getExpiringItems(UUID, LocalDate, LocalDate, JwtAuthenticationToken) called. " +
                    "Migrate to getExpiringItems(UUID, int) for improved security architecture. " +
                    "The token parameter is ignored - using SecurityContext instead.");
            
            if (householdId == null) {
                throw new ValidationException("Household ID cannot be null");
            }
            if (startDate == null || endDate == null) {
                throw new ValidationException("Start date and end date cannot be null");
            }

            UUID currentUserId = securityService.getCurrentUserId();
            
            // Authorization check
            if (!securityService.canAccessHousehold(householdId)) {
                throw new InsufficientPermissionException("You do not have access to this household");
            }
            
            log.debug("User {} retrieving expiring items for household {} between {} and {}",
                    currentUserId, householdId, startDate, endDate);

            List<PantryItem> items = pantryItemRepository.findExpiringItemsInHouseholdBetweenDates(
                    householdId, startDate, endDate);
            return items.stream()
                    .map(this::mapToResponse)
                    .collect(Collectors.toList());
        }

        /**
         * Gets pantry statistics for a household using JWT token for authentication.
         *
         * @param householdId UUID of the household
         * @param token JWT authentication token (ignored in favor of SecurityContext)
         * @return Map containing pantry statistics
         * @deprecated This method will be replaced with a more comprehensive statistics API.
         *             Token-based authentication is now handled by Spring Security filters.
         */
        @Deprecated(since = "2.0", forRemoval = true)
        @Transactional(readOnly = true)
        public Map<String, Object> getPantryStatistics(UUID householdId,
                                                       org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken token) {
            log.warn("DEPRECATED: getPantryStatistics(UUID, JwtAuthenticationToken) called. " +
                    "This method will be replaced with a more comprehensive statistics API. " +
                    "The token parameter is ignored - using SecurityContext instead.");
            
            if (householdId == null) {
                throw new ValidationException("Household ID cannot be null");
            }

            UUID currentUserId = securityService.getCurrentUserId();
            
            // Authorization check
            if (!securityService.canAccessHousehold(householdId)) {
                throw new InsufficientPermissionException("You do not have access to this household");
            }
            
            log.debug("User {} retrieving pantry statistics for household {}", currentUserId, householdId);

            Map<String, Object> statistics = new HashMap<>();
            
            // Total items count
            long totalItems = pantryItemRepository.countByLocation_HouseholdId(householdId);
            statistics.put("totalItems", totalItems);
            
            // Distinct products count
            long distinctProducts = pantryItemRepository.countDistinctProductsByHouseholdId(householdId);
            statistics.put("distinctProducts", distinctProducts);
            
            // Low stock items (threshold of 5)
            List<PantryItem> lowStockItems = pantryItemRepository.findLowStockItemsInHousehold(householdId, 5);
            statistics.put("lowStockCount", lowStockItems.size());
            
            // Expiring items (within 7 days)
            LocalDate sevenDaysFromNow = LocalDate.now().plusDays(7);
            long expiringCount = pantryItemRepository.countExpiringItemsByHouseholdIdAndDate(householdId, sevenDaysFromNow);
            statistics.put("expiringCount", expiringCount);
            
            // Items without expiration date
            List<PantryItem> noExpirationItems = pantryItemRepository.findByExpirationDateIsNullAndLocation_HouseholdId(householdId);
            statistics.put("noExpirationDateCount", noExpirationItems.size());
            
            return statistics;
        }
    }