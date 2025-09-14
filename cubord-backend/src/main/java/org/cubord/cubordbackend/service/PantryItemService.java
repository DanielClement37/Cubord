package org.cubord.cubordbackend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cubord.cubordbackend.domain.Location;
import org.cubord.cubordbackend.domain.PantryItem;
import org.cubord.cubordbackend.domain.Product;
import org.cubord.cubordbackend.domain.User;
import org.cubord.cubordbackend.dto.*;
import org.cubord.cubordbackend.exception.NotFoundException;
import org.cubord.cubordbackend.exception.ValidationException;
import org.cubord.cubordbackend.repository.HouseholdMemberRepository;
import org.cubord.cubordbackend.repository.LocationRepository;
import org.cubord.cubordbackend.repository.PantryItemRepository;
import org.cubord.cubordbackend.repository.ProductRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service class for managing pantry items.
 * Provides operations for creating, retrieving, updating, and deleting pantry items,
 * with proper authorization checks to ensure users can only access their household data.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PantryItemService {

    private final PantryItemRepository pantryItemRepository;
    private final ProductRepository productRepository;
    private final LocationRepository locationRepository;
    private final HouseholdMemberRepository householdMemberRepository;
    private final UserService userService;

    /**
     * Creates a new pantry item or consolidates with existing item if duplicate found.
     * Implements duplicate consolidation logic for same product in same location.
     *
     * @param request DTO containing pantry item information
     * @param token   JWT authentication token of the current user
     * @return PantryItemResponse containing the created or updated pantry item's details
     * @throws IllegalArgumentException if request or token is null
     * @throws NotFoundException        if location or product not found
     * @throws AccessDeniedException    if user is not a member of the household
     * @throws ValidationException      if request data is invalid
     */
    @Transactional
    public PantryItemResponse createPantryItem(CreatePantryItemRequest request, JwtAuthenticationToken token) {
        // Validation
        if (request == null) {
            throw new IllegalArgumentException("Create request cannot be null");
        }
        if (token == null) {
            throw new IllegalArgumentException("Authentication token cannot be null");
        }

        validateCreateRequest(request);

        User currentUser = userService.getCurrentUser(token);
        log.debug("User {} creating pantry item for product {} in location {}",
                currentUser.getId(), request.getProductId(), request.getLocationId());

        // Fetch and validate location
        Location location = locationRepository.findById(request.getLocationId())
                .orElseThrow(() -> new NotFoundException("Location not found"));

        // Fetch and validate product
        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new NotFoundException("Product not found"));

        // Check if user is member of household
        if (!householdMemberRepository.existsByHouseholdIdAndUserId(location.getHousehold().getId(), currentUser.getId())) {
            throw new AccessDeniedException("You are not a member of this household");
        }

        // Check for existing item (duplicate consolidation)
        Optional<PantryItem> existingItem = pantryItemRepository.findByLocationIdAndProductId(
                request.getLocationId(), request.getProductId());

        if (existingItem.isPresent()) {
            // Consolidate quantities
            PantryItem item = existingItem.get();
            int currentQuantity = item.getQuantity() != null ? item.getQuantity() : 0;
            int newQuantity = request.getQuantity() != null ? request.getQuantity() : 0;
            item.setQuantity(currentQuantity + newQuantity);

            // Update other fields if provided
            if (request.getExpirationDate() != null) {
                item.setExpirationDate(request.getExpirationDate());
            }
            if (request.getUnitOfMeasure() != null) {
                item.setUnitOfMeasure(request.getUnitOfMeasure());
            }
            if (request.getNotes() != null) {
                item.setNotes(request.getNotes());
            }

            item.setUpdatedAt(LocalDateTime.now());

            PantryItem savedItem = pantryItemRepository.save(item);
            log.debug("Consolidated pantry item {} with new quantity {}", savedItem.getId(), savedItem.getQuantity());
            return mapToResponse(savedItem);
        } else {
            // Create new item
            PantryItem newItem = new PantryItem();
            newItem.setId(UUID.randomUUID());
            newItem.setProduct(product);
            newItem.setLocation(location);
            newItem.setQuantity(request.getQuantity());
            newItem.setUnitOfMeasure(request.getUnitOfMeasure());
            newItem.setExpirationDate(request.getExpirationDate());
            newItem.setNotes(request.getNotes());
            newItem.setCreatedAt(LocalDateTime.now());
            newItem.setUpdatedAt(LocalDateTime.now());

            PantryItem savedItem = pantryItemRepository.save(newItem);
            log.debug("Created new pantry item {}", savedItem.getId());
            return mapToResponse(savedItem);
        }
    }

    /**
     * Retrieves a pantry item by its ID if the user has access to it.
     *
     * @param pantryItemId UUID of the pantry item to retrieve
     * @param token        JWT authentication token of the current user
     * @return PantryItemResponse containing the pantry item's details
     * @throws NotFoundException     if pantry item not found
     * @throws AccessDeniedException if user is not a member of the household
     */
    @Transactional(readOnly = true)
    public PantryItemResponse getPantryItemById(UUID pantryItemId, JwtAuthenticationToken token) {
        if (pantryItemId == null || token == null) {
            throw new IllegalArgumentException("Pantry item ID and token cannot be null");
        }

        User currentUser = userService.getCurrentUser(token);
        log.debug("User {} retrieving pantry item {}", currentUser.getId(), pantryItemId);

        PantryItem pantryItem = pantryItemRepository.findById(pantryItemId)
                .orElseThrow(() -> new NotFoundException("Pantry item not found"));

        // Check if user is member of household
        UUID householdId = pantryItem.getLocation().getHousehold().getId();
        if (!householdMemberRepository.existsByHouseholdIdAndUserId(householdId, currentUser.getId())) {
            throw new AccessDeniedException("You are not a member of this household");
        }

        return mapToResponse(pantryItem);
    }

    /**
     * Updates a pantry item's information.
     *
     * @param pantryItemId UUID of the pantry item to update
     * @param request      DTO containing updated pantry item information
     * @param token        JWT authentication token of the current user
     * @return PantryItemResponse containing the updated pantry item's details
     * @throws NotFoundException     if pantry item or location not found
     * @throws AccessDeniedException if user is not a member of the household
     * @throws ValidationException   if request data is invalid
     */
    @Transactional
    public PantryItemResponse updatePantryItem(UUID pantryItemId, UpdatePantryItemRequest request, JwtAuthenticationToken token) {
        if (pantryItemId == null || request == null || token == null) {
            throw new IllegalArgumentException("Pantry item ID, request, and token cannot be null");
        }

        validateUpdateRequest(request);

        User currentUser = userService.getCurrentUser(token);
        log.debug("User {} updating pantry item {}", currentUser.getId(), pantryItemId);

        PantryItem pantryItem = pantryItemRepository.findById(pantryItemId)
                .orElseThrow(() -> new NotFoundException("Pantry item not found"));

        // Check if user is member of household
        UUID householdId = pantryItem.getLocation().getHousehold().getId();
        if (!householdMemberRepository.existsByHouseholdIdAndUserId(householdId, currentUser.getId())) {
            throw new AccessDeniedException("You are not a member of this household");
        }

        // Update location if provided
        if (request.getLocationId() != null) {
            Location newLocation = locationRepository.findById(request.getLocationId())
                    .orElseThrow(() -> new NotFoundException("Location not found"));

            // Check if new location belongs to same household
            if (!newLocation.getHousehold().getId().equals(householdId)) {
                throw new AccessDeniedException("Cannot move item to location in different household");
            }

            pantryItem.setLocation(newLocation);
        }

        // Update other fields if provided
        if (request.getQuantity() != null) {
            pantryItem.setQuantity(request.getQuantity());
        }
        if (request.getUnitOfMeasure() != null) {
            pantryItem.setUnitOfMeasure(request.getUnitOfMeasure());
        }
        if (request.getExpirationDate() != null) {
            pantryItem.setExpirationDate(request.getExpirationDate());
        }
        if (request.getNotes() != null) {
            pantryItem.setNotes(request.getNotes());
        }

        pantryItem.setUpdatedAt(LocalDateTime.now());

        PantryItem savedItem = pantryItemRepository.save(pantryItem);
        return mapToResponse(savedItem);
    }

    /**
     * Deletes a pantry item.
     *
     * @param pantryItemId UUID of the pantry item to delete
     * @param token        JWT authentication token of the current user
     * @throws NotFoundException     if pantry item not found
     * @throws AccessDeniedException if user is not a member of the household
     */
    @Transactional
    public void deletePantryItem(UUID pantryItemId, JwtAuthenticationToken token) {
        if (pantryItemId == null || token == null) {
            throw new IllegalArgumentException("Pantry item ID and token cannot be null");
        }

        User currentUser = userService.getCurrentUser(token);
        log.debug("User {} deleting pantry item {}", currentUser.getId(), pantryItemId);

        PantryItem pantryItem = pantryItemRepository.findById(pantryItemId)
                .orElseThrow(() -> new NotFoundException("Pantry item not found"));

        // Check if user is member of household
        UUID householdId = pantryItem.getLocation().getHousehold().getId();
        if (!householdMemberRepository.existsByHouseholdIdAndUserId(householdId, currentUser.getId())) {
            throw new AccessDeniedException("You are not a member of this household");
        }

        pantryItemRepository.delete(pantryItem);
    }

    /**
     * Retrieves pantry items by location.
     *
     * @param locationId UUID of the location
     * @param token      JWT authentication token of the current user
     * @return List of PantryItemResponse objects
     * @throws NotFoundException     if location not found
     * @throws AccessDeniedException if user is not a member of the household
     */
    @Transactional(readOnly = true)
    public List<PantryItemResponse> getPantryItemsByLocation(UUID locationId, JwtAuthenticationToken token) {
        if (locationId == null || token == null) {
            throw new IllegalArgumentException("Location ID and token cannot be null");
        }

        User currentUser = userService.getCurrentUser(token);
        log.debug("User {} retrieving pantry items by location {}", currentUser.getId(), locationId);

        Location location = locationRepository.findById(locationId)
                .orElseThrow(() -> new NotFoundException("Location not found"));

        // Check if user is member of household
        if (!householdMemberRepository.existsByHouseholdIdAndUserId(location.getHousehold().getId(), currentUser.getId())) {
            throw new AccessDeniedException("You are not a member of this household");
        }

        List<PantryItem> items = pantryItemRepository.findByLocationId(locationId);
        return items.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Retrieves pantry items by household with pagination.
     *
     * @param householdId UUID of the household
     * @param pageable    Pagination information
     * @param token       JWT authentication token of the current user
     * @return Page of PantryItemResponse objects
     * @throws AccessDeniedException if user is not a member of the household
     */
    @Transactional(readOnly = true)
    public Page<PantryItemResponse> getPantryItemsByHousehold(UUID householdId, Pageable pageable, JwtAuthenticationToken token) {
        if (householdId == null || pageable == null || token == null) {
            throw new IllegalArgumentException("Household ID, pageable, and token cannot be null");
        }

        User currentUser = userService.getCurrentUser(token);
        log.debug("User {} retrieving pantry items by household {} with pagination", currentUser.getId(), householdId);

        // Check if user is member of household
        if (!householdMemberRepository.existsByHouseholdIdAndUserId(householdId, currentUser.getId())) {
            throw new AccessDeniedException("You are not a member of this household");
        }

        Page<PantryItem> items = pantryItemRepository.findByLocation_HouseholdId(householdId, pageable);
        return items.map(this::mapToResponse);
    }

    /**
     * Retrieves low stock items for a household.
     *
     * @param householdId UUID of the household
     * @param threshold   Maximum quantity to consider as low stock
     * @param token       JWT authentication token of the current user
     * @return List of PantryItemResponse objects with low stock
     * @throws AccessDeniedException if user is not a member of the household
     * @throws ValidationException   if threshold is invalid
     */
    @Transactional(readOnly = true)
    public List<PantryItemResponse> getLowStockItems(UUID householdId, Integer threshold, JwtAuthenticationToken token) {
        if (householdId == null || threshold == null || token == null) {
            throw new IllegalArgumentException("Household ID, threshold, and token cannot be null");
        }

        validateThreshold(threshold);

        User currentUser = userService.getCurrentUser(token);
        log.debug("User {} retrieving low stock items for household {} with threshold {}",
                currentUser.getId(), householdId, threshold);

        // Check if user is member of household
        if (!householdMemberRepository.existsByHouseholdIdAndUserId(householdId, currentUser.getId())) {
            throw new AccessDeniedException("You are not a member of this household");
        }

        List<PantryItem> items = pantryItemRepository.findLowStockItemsInHousehold(householdId, threshold);
        return items.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Retrieves expiring items for a household within a date range.
     *
     * @param householdId UUID of the household
     * @param startDate   Start date for expiration range
     * @param endDate     End date for expiration range
     * @param token       JWT authentication token of the current user
     * @return List of PantryItemResponse objects expiring in the range
     * @throws AccessDeniedException if user is not a member of the household
     */
    @Transactional(readOnly = true)
    public List<PantryItemResponse> getExpiringItems(UUID householdId, LocalDate startDate, LocalDate endDate, JwtAuthenticationToken token) {
        if (householdId == null || startDate == null || endDate == null || token == null) {
            throw new IllegalArgumentException("Household ID, dates, and token cannot be null");
        }

        User currentUser = userService.getCurrentUser(token);
        log.debug("User {} retrieving expiring items for household {} between {} and {}",
                currentUser.getId(), householdId, startDate, endDate);

        // Check if user is member of household
        if (!householdMemberRepository.existsByHouseholdIdAndUserId(householdId, currentUser.getId())) {
            throw new AccessDeniedException("You are not a member of this household");
        }

        List<PantryItem> items = pantryItemRepository.findExpiringItemsInHouseholdBetweenDates(householdId, startDate, endDate);
        return items.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Searches pantry items in a household by search term.
     *
     * @param householdId UUID of the household
     * @param searchTerm  Term to search for in product names, brands, and notes
     * @param token       JWT authentication token of the current user
     * @return List of PantryItemResponse objects matching the search
     * @throws IllegalArgumentException if search term is empty
     * @throws AccessDeniedException    if user is not a member of the household
     */
    @Transactional(readOnly = true)
    public List<PantryItemResponse> searchPantryItems(UUID householdId, String searchTerm, JwtAuthenticationToken token) {
        if (householdId == null || token == null) {
            throw new IllegalArgumentException("Household ID and token cannot be null");
        }
        if (searchTerm == null || searchTerm.trim().isEmpty()) {
            throw new IllegalArgumentException("Search term cannot be empty");
        }

        User currentUser = userService.getCurrentUser(token);
        log.debug("User {} searching pantry items in household {} for term '{}'",
                currentUser.getId(), householdId, searchTerm);

        // Check if user is member of household
        if (!householdMemberRepository.existsByHouseholdIdAndUserId(householdId, currentUser.getId())) {
            throw new AccessDeniedException("You are not a member of this household");
        }

        List<PantryItem> items = pantryItemRepository.searchItemsInHousehold(householdId, searchTerm.trim());
        return items.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Creates multiple pantry items in a batch operation.
     *
     * @param requests List of CreatePantryItemRequest objects
     * @param token    JWT authentication token of the current user
     * @return List of PantryItemResponse objects for created items
     * @throws IllegalArgumentException if requests list is null or empty
     */
    @Transactional
    public List<PantryItemResponse> createMultiplePantryItems(List<CreatePantryItemRequest> requests, JwtAuthenticationToken token) {
        if (requests == null || requests.isEmpty() || token == null) {
            throw new IllegalArgumentException("Requests list and token cannot be null or empty");
        }

        User currentUser = userService.getCurrentUser(token);
        log.debug("User {} creating {} pantry items in batch", currentUser.getId(), requests.size());

        List<PantryItemResponse> responses = new ArrayList<>();
        for (CreatePantryItemRequest request : requests) {
            try {
                PantryItemResponse response = createPantryItem(request, token);
                responses.add(response);
            } catch (Exception e) {
                log.warn("Failed to create pantry item in batch for product {} in location {}: {}",
                        request.getProductId(), request.getLocationId(), e.getMessage());
                // Continue with other items in the batch
            }
        }

        return responses;
    }

    /**
     * Deletes multiple pantry items in a batch operation.
     *
     * @param itemIds List of pantry item IDs to delete
     * @param token   JWT authentication token of the current user
     * @return Number of successfully deleted items
     * @throws IllegalArgumentException if item IDs list is null or empty
     */
    @Transactional
    public int deleteMultiplePantryItems(List<UUID> itemIds, JwtAuthenticationToken token) {
        if (itemIds == null || itemIds.isEmpty() || token == null) {
            throw new IllegalArgumentException("Item IDs list and token cannot be null or empty");
        }

        User currentUser = userService.getCurrentUser(token);
        log.debug("User {} deleting {} pantry items in batch", currentUser.getId(), itemIds.size());

        int deletedCount = 0;
        for (UUID itemId : itemIds) {
            try {
                deletePantryItem(itemId, token);
                deletedCount++;
            } catch (Exception e) {
                log.warn("Failed to delete pantry item {} in batch: {}", itemId, e.getMessage());
                // Continue with other items in the batch
            }
        }

        return deletedCount;
    }

    // Additional convenience methods for compatibility with existing repository methods

    @Transactional(readOnly = true)
    public List<PantryItemResponse> getItemsByLocation(UUID locationId, JwtAuthenticationToken token) {
        return getPantryItemsByLocation(locationId, token);
    }

    @Transactional(readOnly = true)
    public List<PantryItemResponse> getItemsByLocation(UUID locationId, Sort sort, JwtAuthenticationToken token) {
        if (locationId == null || sort == null || token == null) {
            throw new IllegalArgumentException("Location ID, sort, and token cannot be null");
        }

        User currentUser = userService.getCurrentUser(token);
        Location location = locationRepository.findById(locationId)
                .orElseThrow(() -> new NotFoundException("Location not found"));

        if (!householdMemberRepository.existsByHouseholdIdAndUserId(location.getHousehold().getId(), currentUser.getId())) {
            throw new AccessDeniedException("You are not a member of this household");
        }

        List<PantryItem> items = pantryItemRepository.findByLocationId(locationId, sort);
        return items.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Page<PantryItemResponse> getItemsByHousehold(UUID householdId, Pageable pageable, JwtAuthenticationToken token) {
        return getPantryItemsByHousehold(householdId, pageable, token);
    }

    @Transactional(readOnly = true)
    public List<PantryItemResponse> getExpiringItems(LocalDate beforeDate, JwtAuthenticationToken token) {
        if (beforeDate == null || token == null) {
            throw new IllegalArgumentException("Before date and token cannot be null");
        }

        User currentUser = userService.getCurrentUser(token);
        log.debug("User {} retrieving expiring items before {}", currentUser.getId(), beforeDate);

        List<PantryItem> items = pantryItemRepository.findByExpirationDateBefore(beforeDate);

        // Filter items to only include those from households where user is a member
        return items.stream()
                .filter(item -> householdMemberRepository.existsByHouseholdIdAndUserId(
                        item.getLocation().getHousehold().getId(), currentUser.getId()))
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<PantryItemResponse> getExpiringItemsInHousehold(UUID householdId, LocalDate beforeDate, JwtAuthenticationToken token) {
        if (householdId == null || beforeDate == null || token == null) {
            throw new IllegalArgumentException("Household ID, before date, and token cannot be null");
        }

        User currentUser = userService.getCurrentUser(token);

        if (!householdMemberRepository.existsByHouseholdIdAndUserId(householdId, currentUser.getId())) {
            throw new AccessDeniedException("You are not a member of this household");
        }

        List<PantryItem> items = pantryItemRepository.findByExpirationDateBeforeAndLocation_HouseholdId(beforeDate, householdId);
        return items.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<PantryItemResponse> getItemsExpiringBetween(LocalDate startDate, LocalDate endDate, JwtAuthenticationToken token) {
        if (startDate == null || endDate == null || token == null) {
            throw new IllegalArgumentException("Start date, end date, and token cannot be null");
        }

        User currentUser = userService.getCurrentUser(token);
        log.debug("User {} retrieving items expiring between {} and {}", currentUser.getId(), startDate, endDate);

        List<PantryItem> items = pantryItemRepository.findByExpirationDateBetween(startDate, endDate);

        // Filter items to only include those from households where user is a member
        return items.stream()
                .filter(item -> householdMemberRepository.existsByHouseholdIdAndUserId(
                        item.getLocation().getHousehold().getId(), currentUser.getId()))
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<PantryItemResponse> getLowStockItems(Integer threshold, JwtAuthenticationToken token) {
        if (threshold == null || token == null) {
            throw new IllegalArgumentException("Threshold and token cannot be null");
        }

        validateThreshold(threshold);

        User currentUser = userService.getCurrentUser(token);
        log.debug("User {} retrieving low stock items with threshold {}", currentUser.getId(), threshold);

        List<PantryItem> items = pantryItemRepository.findByQuantityLessThanEqual(threshold);

        // Filter items to only include those from households where user is a member
        return items.stream()
                .filter(item -> householdMemberRepository.existsByHouseholdIdAndUserId(
                        item.getLocation().getHousehold().getId(), currentUser.getId()))
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<PantryItemResponse> getLowStockItemsInHousehold(UUID householdId, Integer threshold, JwtAuthenticationToken token) {
        return getLowStockItems(householdId, threshold, token);
    }

    @Transactional(readOnly = true)
    public List<PantryItemResponse> searchItemsByProductName(String searchTerm, JwtAuthenticationToken token) {
        if (searchTerm == null || searchTerm.trim().isEmpty() || token == null) {
            throw new IllegalArgumentException("Search term and token cannot be empty");
        }

        User currentUser = userService.getCurrentUser(token);
        log.debug("User {} searching items by product name '{}'", currentUser.getId(), searchTerm);

        List<PantryItem> items = pantryItemRepository.findByProduct_NameContainingIgnoreCase(searchTerm.trim());

        // Filter items to only include those from households where user is a member
        return items.stream()
                .filter(item -> householdMemberRepository.existsByHouseholdIdAndUserId(
                        item.getLocation().getHousehold().getId(), currentUser.getId()))
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<PantryItemResponse> searchItemsInLocation(UUID locationId, String searchTerm, JwtAuthenticationToken token) {
        if (locationId == null || searchTerm == null || searchTerm.trim().isEmpty() || token == null) {
            throw new IllegalArgumentException("Location ID, search term, and token cannot be null or empty");
        }

        User currentUser = userService.getCurrentUser(token);
        Location location = locationRepository.findById(locationId)
                .orElseThrow(() -> new NotFoundException("Location not found"));

        if (!householdMemberRepository.existsByHouseholdIdAndUserId(location.getHousehold().getId(), currentUser.getId())) {
            throw new AccessDeniedException("You are not a member of this household");
        }

        List<PantryItem> items = pantryItemRepository.searchItemsInLocation(locationId, searchTerm.trim());
        return items.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<PantryItemResponse> searchItemsInHousehold(UUID householdId, String searchTerm, JwtAuthenticationToken token) {
        return searchPantryItems(householdId, searchTerm, token);
    }

    @Transactional
    public List<PantryItemResponse> updateQuantities(Map<UUID, Integer> quantityUpdates, JwtAuthenticationToken token) {
        if (quantityUpdates == null || quantityUpdates.isEmpty() || token == null) {
            throw new IllegalArgumentException("Quantity updates map and token cannot be null or empty");
        }

        User currentUser = userService.getCurrentUser(token);
        log.debug("User {} updating quantities for {} items", currentUser.getId(), quantityUpdates.size());

        List<PantryItemResponse> updatedItems = new ArrayList<>();

        for (Map.Entry<UUID, Integer> entry : quantityUpdates.entrySet()) {
            UUID itemId = entry.getKey();
            Integer newQuantity = entry.getValue();

            if (newQuantity == null || newQuantity < 0) {
                log.warn("Invalid quantity {} for item {}, skipping", newQuantity, itemId);
                continue;
            }

            try {
                UpdatePantryItemRequest request = UpdatePantryItemRequest.builder()
                        .quantity(newQuantity)
                        .build();

                PantryItemResponse updated = updatePantryItem(itemId, request, token);
                updatedItems.add(updated);
            } catch (Exception e) {
                log.warn("Failed to update quantity for item {}: {}", itemId, e.getMessage());
                // Continue with other items
            }
        }

        return updatedItems;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getPantryStatistics(UUID householdId, JwtAuthenticationToken token) {
        if (householdId == null || token == null) {
            throw new IllegalArgumentException("Household ID and token cannot be null");
        }

        User currentUser = userService.getCurrentUser(token);

        if (!householdMemberRepository.existsByHouseholdIdAndUserId(householdId, currentUser.getId())) {
            throw new AccessDeniedException("You are not a member of this household");
        }

        long totalItems = pantryItemRepository.countByLocation_HouseholdId(householdId);
        List<PantryItem> allItems = pantryItemRepository.findByLocation_HouseholdId(householdId);

        long expiringItems = allItems.stream()
                .filter(item -> item.getExpirationDate() != null &&
                        item.getExpirationDate().isBefore(LocalDate.now().plusDays(7)))
                .count();

        long lowStockItems = allItems.stream()
                .filter(item -> item.getQuantity() != null && item.getQuantity() <= 5)
                .count();

        Map<String, Object> statistics = new HashMap<>();
        statistics.put("totalItems", totalItems);
        statistics.put("expiringItems", expiringItems);
        statistics.put("lowStockItems", lowStockItems);
        statistics.put("generatedAt", LocalDateTime.now());

        return statistics;
    }

    // Private helper methods

    /**
     * Maps a PantryItem entity to a PantryItemResponse DTO.
     */
    private PantryItemResponse mapToResponse(PantryItem pantryItem) {
        if (pantryItem == null) {
            return null;
        }

        return PantryItemResponse.builder()
                .id(pantryItem.getId())
                .product(mapProductToResponse(pantryItem.getProduct()))
                .location(mapLocationToResponse(pantryItem.getLocation()))
                .expirationDate(pantryItem.getExpirationDate())
                .quantity(pantryItem.getQuantity())
                .unitOfMeasure(pantryItem.getUnitOfMeasure())
                .notes(pantryItem.getNotes())
                .createdAt(pantryItem.getCreatedAt())
                .updatedAt(pantryItem.getUpdatedAt())
                .build();
    }

    /**
     * Maps a Product entity to a ProductResponse DTO.
     */
    private ProductResponse mapProductToResponse(Product product) {
        if (product == null) {
            return null;
        }

        return ProductResponse.builder()
                .id(product.getId())
                .upc(product.getUpc())
                .name(product.getName())
                .brand(product.getBrand())
                .category(product.getCategory())
                .defaultExpirationDays(product.getDefaultExpirationDays())
                .dataSource(product.getDataSource())
                .requiresApiRetry(product.getRequiresApiRetry())
                .retryAttempts(product.getRetryAttempts())
                .lastRetryAttempt(product.getLastRetryAttempt())
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .build();
    }

    /**
     * Maps a Location entity to a LocationResponse DTO.
     */
    private LocationResponse mapLocationToResponse(Location location) {
        if (location == null) {
            return null;
        }

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

    /**
     * Validates a create pantry item request.
     */
    private void validateCreateRequest(CreatePantryItemRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Create request cannot be null");
        }
        if (request.getProductId() == null) {
            throw new ValidationException("Product ID is required");
        }
        if (request.getLocationId() == null) {
            throw new ValidationException("Location ID is required");
        }
        if (request.getQuantity() != null && request.getQuantity() <= 0) {
            throw new ValidationException("Quantity must be greater than 0");
        }
    }

    /**
     * Validates an update pantry item request.
     */
    private void validateUpdateRequest(UpdatePantryItemRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Update request cannot be null");
        }
        if (request.getQuantity() != null && request.getQuantity() <= 0) {
            throw new ValidationException("Quantity must be greater than 0");
        }
    }

    /**
     * Validates a search term.
     */
    private void validateSearchTerm(String searchTerm) {
        if (searchTerm == null || searchTerm.trim().isEmpty()) {
            throw new ValidationException("Search term cannot be empty");
        }
    }

    /**
     * Validates a threshold value.
     */
    private void validateThreshold(Integer threshold) {
        if (threshold == null || threshold <= 0) {
            throw new ValidationException("Threshold must be greater than 0");
        }
    }
}