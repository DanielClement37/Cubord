
package org.cubord.cubordbackend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cubord.cubordbackend.domain.*;
import org.cubord.cubordbackend.dto.location.LocationResponse;
import org.cubord.cubordbackend.dto.pantryItem.CreatePantryItemRequest;
import org.cubord.cubordbackend.dto.pantryItem.PantryItemResponse;
import org.cubord.cubordbackend.dto.pantryItem.UpdatePantryItemRequest;
import org.cubord.cubordbackend.dto.product.ProductResponse;
import org.cubord.cubordbackend.exception.*;
import org.cubord.cubordbackend.repository.HouseholdMemberRepository;
import org.cubord.cubordbackend.repository.LocationRepository;
import org.cubord.cubordbackend.repository.PantryItemRepository;
import org.cubord.cubordbackend.repository.ProductRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
 * with proper authorization checks and exception handling to ensure users can only access their household data.
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
     * Validates that the user is a member of the specified household.
     *
     * @param householdId UUID of the household to validate
     * @param currentUser User to validate
     * @throws InsufficientPermissionException if user is not a member of the household
     */
    private void validateHouseholdAccess(UUID householdId, User currentUser) {
        if (!householdMemberRepository.existsByHouseholdIdAndUserId(householdId, currentUser.getId())) {
            log.warn("User {} attempted to access household {} without membership",
                    currentUser.getId(), householdId);
            throw new InsufficientPermissionException("You are not a member of this household");
        }
        log.debug("User {} authorized for household {} access", currentUser.getId(), householdId);
    }

    /**
     * Validates a create pantry item request.
     *
     * @param request Request to validate
     * @throws ValidationException if request data is invalid
     */
    private void validateCreateRequest(CreatePantryItemRequest request) {
        if (request.getProductId() == null) {
            throw new ValidationException("Product ID cannot be null");
        }
        if (request.getLocationId() == null) {
            throw new ValidationException("Location ID cannot be null");
        }
        if (request.getQuantity() != null && request.getQuantity() < 0) {
            throw new ValidationException("Quantity cannot be negative");
        }
        log.debug("Create request validation passed for product {} in location {}",
                request.getProductId(), request.getLocationId());
    }

    /**
     * Validates an update pantry item request.
     *
     * @param request Request to validate
     * @throws ValidationException if request data is invalid
     */
    private void validateUpdateRequest(UpdatePantryItemRequest request) {
        if (request.getQuantity() != null && request.getQuantity() < 0) {
            throw new ValidationException("Quantity cannot be negative");
        }
        log.debug("Update request validation passed");
    }

    /**
     * Validates a search term.
     *
     * @param searchTerm Search term to validate
     * @throws ValidationException if search term is invalid
     */
    private void validateSearchTerm(String searchTerm) {
        if (searchTerm == null) {
            throw new ValidationException("Search term cannot be null");
        }
        if (searchTerm.trim().isEmpty()) {
            throw new ValidationException("Search term cannot be empty");
        }
        log.debug("Search term validation passed: {}", searchTerm);
    }

    /**
     * Validates a threshold value.
     *
     * @param threshold Threshold to validate
     * @throws ValidationException if threshold is invalid
     */
    private void validateThreshold(Integer threshold) {
        if (threshold == null) {
            throw new ValidationException("Threshold cannot be null");
        }
        if (threshold < 0) {
            throw new ValidationException("Threshold must be non-negative");
        }
        log.debug("Threshold validation passed: {}", threshold);
    }

    /**
     * Creates a new pantry item or consolidates with existing item if duplicate found.
     * Only consolidates items with matching expiration dates (including null matches).
     * Items with different expiration dates are kept separate.
     *
     * @param request DTO containing pantry item information
     * @param token   JWT authentication token of the current user
     * @return PantryItemResponse containing the created or updated pantry item's details
     * @throws ValidationException              if request or token is null, or request data is invalid
     * @throws NotFoundException                if location or product not found
     * @throws InsufficientPermissionException  if user is not a member of the household
     * @throws DataIntegrityException           if save operation fails
     */
    @Transactional
    public PantryItemResponse createPantryItem(CreatePantryItemRequest request, JwtAuthenticationToken token) {
        // Validate inputs
        if (request == null) {
            throw new ValidationException("Create request cannot be null");
        }
        if (token == null) {
            throw new ValidationException("Authentication token cannot be null");
        }

        validateCreateRequest(request);

        User currentUser = userService.getCurrentUser(token);
        log.debug("User {} creating pantry item for product {} in location {} with expiration date {}",
                currentUser.getId(), request.getProductId(), request.getLocationId(), request.getExpirationDate());

        // Fetch and validate location
        Location location = locationRepository.findById(request.getLocationId())
                .orElseThrow(() -> new NotFoundException("Location not found"));

        // Fetch and validate product
        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new NotFoundException("Product not found"));

        // Check if user is member of household
        validateHouseholdAccess(location.getHousehold().getId(), currentUser);

        // Check for existing item with matching expiration date (expiration-date-aware duplicate detection)
        Optional<PantryItem> existingItem;
        if (request.getExpirationDate() != null) {
            existingItem = pantryItemRepository.findByLocationIdAndProductIdAndExpirationDate(
                    request.getLocationId(), request.getProductId(), request.getExpirationDate());
        } else {
            existingItem = pantryItemRepository.findByLocationIdAndProductIdAndExpirationDateIsNull(
                    request.getLocationId(), request.getProductId());
        }

        if (existingItem.isPresent()) {
            // Consolidate quantities for items with matching expiration dates
            PantryItem item = existingItem.get();
            int currentQuantity = item.getQuantity() != null ? item.getQuantity() : 0;
            int newQuantity = request.getQuantity() != null ? request.getQuantity() : 0;
            item.setQuantity(currentQuantity + newQuantity);

            // Update other fields if provided
            if (request.getUnitOfMeasure() != null) {
                item.setUnitOfMeasure(request.getUnitOfMeasure());
            }
            if (request.getNotes() != null) {
                item.setNotes(request.getNotes());
            }

            item.setUpdatedAt(LocalDateTime.now());

            try {
                PantryItem savedItem = pantryItemRepository.save(item);
                log.info("User {} consolidated pantry item {} with matching expiration date, new quantity {}",
                        currentUser.getId(), savedItem.getId(), savedItem.getQuantity());
                return mapToResponse(savedItem);
            } catch (Exception e) {
                log.error("Failed to save consolidated pantry item", e);
                throw new DataIntegrityException("Failed to save pantry item: " + e.getMessage(), e);
            }
        } else {
            // Create new item (no existing item with matching expiration date)
            PantryItem newItem = PantryItem.builder()
                    .id(UUID.randomUUID())
                    .product(product)
                    .location(location)
                    .quantity(request.getQuantity())
                    .unitOfMeasure(request.getUnitOfMeasure())
                    .expirationDate(request.getExpirationDate())
                    .notes(request.getNotes())
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();

            try {
                PantryItem savedItem = pantryItemRepository.save(newItem);
                log.info("User {} created new pantry item {} with expiration date {}",
                        currentUser.getId(), savedItem.getId(), savedItem.getExpirationDate());
                return mapToResponse(savedItem);
            } catch (Exception e) {
                log.error("Failed to save new pantry item", e);
                throw new DataIntegrityException("Failed to save pantry item: " + e.getMessage(), e);
            }
        }
    }

    /**
     * Retrieves a pantry item by its ID if the user has access to it.
     *
     * @param pantryItemId UUID of the pantry item to retrieve
     * @param token        JWT authentication token of the current user
     * @return PantryItemResponse containing the pantry item's details
     * @throws ValidationException              if pantryItemId or token is null
     * @throws NotFoundException                if pantry item not found
     * @throws InsufficientPermissionException  if user is not a member of the household
     */
    @Transactional(readOnly = true)
    public PantryItemResponse getPantryItemById(UUID pantryItemId, JwtAuthenticationToken token) {
        // Validate inputs
        if (pantryItemId == null) {
            throw new ValidationException("Pantry item ID cannot be null");
        }
        if (token == null) {
            throw new ValidationException("Authentication token cannot be null");
        }

        User currentUser = userService.getCurrentUser(token);
        log.debug("User {} retrieving pantry item {}", currentUser.getId(), pantryItemId);

        PantryItem pantryItem = pantryItemRepository.findById(pantryItemId)
                .orElseThrow(() -> new NotFoundException("Pantry item not found"));

        // Check if user is member of household
        validateHouseholdAccess(pantryItem.getLocation().getHousehold().getId(), currentUser);

        return mapToResponse(pantryItem);
    }

    /**
     * Updates a pantry item's information.
     *
     * @param pantryItemId UUID of the pantry item to update
     * @param request      DTO containing updated pantry item information
     * @param token        JWT authentication token of the current user
     * @return PantryItemResponse containing the updated pantry item's details
     * @throws ValidationException              if inputs are null or request data is invalid
     * @throws NotFoundException                if pantry item or location not found
     * @throws InsufficientPermissionException  if user is not a member of the household
     * @throws DataIntegrityException           if save operation fails
     */
    @Transactional
    public PantryItemResponse updatePantryItem(UUID pantryItemId, UpdatePantryItemRequest request, JwtAuthenticationToken token) {
        // Validate inputs
        if (pantryItemId == null) {
            throw new ValidationException("Pantry item ID cannot be null");
        }
        if (request == null) {
            throw new ValidationException("Update request cannot be null");
        }
        if (token == null) {
            throw new ValidationException("Authentication token cannot be null");
        }

        validateUpdateRequest(request);

        User currentUser = userService.getCurrentUser(token);
        log.debug("User {} updating pantry item {}", currentUser.getId(), pantryItemId);

        PantryItem pantryItem = pantryItemRepository.findById(pantryItemId)
                .orElseThrow(() -> new NotFoundException("Pantry item not found"));

        // Check if user is member of household
        validateHouseholdAccess(pantryItem.getLocation().getHousehold().getId(), currentUser);

        // Update fields
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

        // Update location if provided
        if (request.getLocationId() != null) {
            Location newLocation = locationRepository.findById(request.getLocationId())
                    .orElseThrow(() -> new NotFoundException("Location not found"));

            // Verify user has access to new location's household
            validateHouseholdAccess(newLocation.getHousehold().getId(), currentUser);

            pantryItem.setLocation(newLocation);
        }

        pantryItem.setUpdatedAt(LocalDateTime.now());

        try {
            PantryItem savedItem = pantryItemRepository.save(pantryItem);
            log.info("User {} successfully updated pantry item {}", currentUser.getId(), pantryItemId);
            return mapToResponse(savedItem);
        } catch (Exception e) {
            log.error("Failed to update pantry item {}", pantryItemId, e);
            throw new DataIntegrityException("Failed to update pantry item: " + e.getMessage(), e);
        }
    }

    /**
     * Deletes a pantry item.
     *
     * @param pantryItemId UUID of the pantry item to delete
     * @param token        JWT authentication token of the current user
     * @throws ValidationException              if pantryItemId or token is null
     * @throws NotFoundException                if pantry item not found
     * @throws InsufficientPermissionException  if user is not a member of the household
     */
    @Transactional
    public void deletePantryItem(UUID pantryItemId, JwtAuthenticationToken token) {
        // Validate inputs
        if (pantryItemId == null) {
            throw new ValidationException("Pantry item ID cannot be null");
        }
        if (token == null) {
            throw new ValidationException("Authentication token cannot be null");
        }

        User currentUser = userService.getCurrentUser(token);
        log.debug("User {} deleting pantry item {}", currentUser.getId(), pantryItemId);

        PantryItem pantryItem = pantryItemRepository.findById(pantryItemId)
                .orElseThrow(() -> new NotFoundException("Pantry item not found"));

        // Check if user is member of household
        validateHouseholdAccess(pantryItem.getLocation().getHousehold().getId(), currentUser);

        pantryItemRepository.delete(pantryItem);
        log.info("User {} successfully deleted pantry item {}", currentUser.getId(), pantryItemId);
    }

    /**
     * Retrieves pantry items by location.
     *
     * @param locationId UUID of the location
     * @param token      JWT authentication token of the current user
     * @return List of PantryItemResponse objects
     * @throws ValidationException              if locationId or token is null
     * @throws NotFoundException                if location not found
     * @throws InsufficientPermissionException  if user is not a member of the household
     */
    @Transactional(readOnly = true)
    public List<PantryItemResponse> getPantryItemsByLocation(UUID locationId, JwtAuthenticationToken token) {
        // Validate inputs
        if (locationId == null) {
            throw new ValidationException("Location ID cannot be null");
        }
        if (token == null) {
            throw new ValidationException("Authentication token cannot be null");
        }

        User currentUser = userService.getCurrentUser(token);
        log.debug("User {} retrieving pantry items for location {}", currentUser.getId(), locationId);

        Location location = locationRepository.findById(locationId)
                .orElseThrow(() -> new NotFoundException("Location not found"));

        // Check if user is member of household
        validateHouseholdAccess(location.getHousehold().getId(), currentUser);

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
     * @throws ValidationException              if householdId or token is null
     * @throws InsufficientPermissionException  if user is not a member of the household
     */
    @Transactional(readOnly = true)
    public Page<PantryItemResponse> getPantryItemsByHousehold(UUID householdId, Pageable pageable, JwtAuthenticationToken token) {
        // Validate inputs
        if (householdId == null) {
            throw new ValidationException("Household ID cannot be null");
        }
        if (token == null) {
            throw new ValidationException("Authentication token cannot be null");
        }

        User currentUser = userService.getCurrentUser(token);
        log.debug("User {} retrieving pantry items for household {}", currentUser.getId(), householdId);

        // Check if user is member of household
        validateHouseholdAccess(householdId, currentUser);

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
     * @throws ValidationException              if inputs are null or threshold is invalid
     * @throws InsufficientPermissionException  if user is not a member of the household
     */
    @Transactional(readOnly = true)
    public List<PantryItemResponse> getLowStockItems(UUID householdId, Integer threshold, JwtAuthenticationToken token) {
        // Validate inputs
        if (householdId == null) {
            throw new ValidationException("Household ID cannot be null");
        }
        if (token == null) {
            throw new ValidationException("Authentication token cannot be null");
        }

        validateThreshold(threshold);

        User currentUser = userService.getCurrentUser(token);
        log.debug("User {} retrieving low stock items for household {} with threshold {}",
                currentUser.getId(), householdId, threshold);

        // Check if user is member of household
        validateHouseholdAccess(householdId, currentUser);

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
     * @throws ValidationException              if dates are invalid (start after end)
     * @throws InsufficientPermissionException  if user is not a member of the household
     */
    @Transactional(readOnly = true)
    public List<PantryItemResponse> getExpiringItems(UUID householdId, LocalDate startDate, LocalDate endDate, JwtAuthenticationToken token) {
        // Validate inputs
        if (householdId == null) {
            throw new ValidationException("Household ID cannot be null");
        }
        if (token == null) {
            throw new ValidationException("Authentication token cannot be null");
        }
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new ValidationException("Start date must be before or equal to end date");
        }

        User currentUser = userService.getCurrentUser(token);
        log.debug("User {} retrieving expiring items for household {} between {} and {}",
                currentUser.getId(), householdId, startDate, endDate);

        // Check if user is member of household
        validateHouseholdAccess(householdId, currentUser);

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
     * @param searchTerm  Term to search for in product names, brands, and notes
     * @param token       JWT authentication token of the current user
     * @return List of PantryItemResponse objects matching the search
     * @throws ValidationException              if search term is null or empty
     * @throws InsufficientPermissionException  if user is not a member of the household
     */
    @Transactional(readOnly = true)
    public List<PantryItemResponse> searchPantryItems(UUID householdId, String searchTerm, JwtAuthenticationToken token) {
        // Validate inputs
        if (householdId == null) {
            throw new ValidationException("Household ID cannot be null");
        }
        if (token == null) {
            throw new ValidationException("Authentication token cannot be null");
        }

        validateSearchTerm(searchTerm);

        User currentUser = userService.getCurrentUser(token);
        log.debug("User {} searching pantry items in household {} with term: {}",
                currentUser.getId(), householdId, searchTerm);

        // Check if user is member of household
        validateHouseholdAccess(householdId, currentUser);

        List<PantryItem> items = pantryItemRepository.searchItemsInHousehold(householdId, searchTerm);
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
     * @throws ValidationException if requests list is null or empty
     */
    @Transactional
    public List<PantryItemResponse> createMultiplePantryItems(List<CreatePantryItemRequest> requests, JwtAuthenticationToken token) {
        // Validate inputs
        if (requests == null) {
            throw new ValidationException("Requests list cannot be null");
        }
        if (requests.isEmpty()) {
            throw new ValidationException("Requests list cannot be empty");
        }
        if (token == null) {
            throw new ValidationException("Authentication token cannot be null");
        }

        User currentUser = userService.getCurrentUser(token);
        log.debug("User {} creating {} pantry items in batch", currentUser.getId(), requests.size());

        List<PantryItemResponse> responses = new ArrayList<>();
        for (CreatePantryItemRequest request : requests) {
            responses.add(createPantryItem(request, token));
        }

        log.info("User {} successfully created {} pantry items in batch", currentUser.getId(), responses.size());
        return responses;
    }

    /**
     * Deletes multiple pantry items in a batch operation.
     *
     * @param itemIds List of pantry item IDs to delete
     * @param token   JWT authentication token of the current user
     * @return Number of successfully deleted items
     * @throws ValidationException if item IDs list is null or empty
     */
    @Transactional
    public int deleteMultiplePantryItems(List<UUID> itemIds, JwtAuthenticationToken token) {
        // Validate inputs
        if (itemIds == null) {
            throw new ValidationException("Item IDs list cannot be null");
        }
        if (itemIds.isEmpty()) {
            throw new ValidationException("Item IDs list cannot be empty");
        }
        if (token == null) {
            throw new ValidationException("Authentication token cannot be null");
        }

        User currentUser = userService.getCurrentUser(token);
        log.debug("User {} deleting {} pantry items in batch", currentUser.getId(), itemIds.size());

        int deletedCount = 0;
        for (UUID itemId : itemIds) {
            try {
                deletePantryItem(itemId, token);
                deletedCount++;
            } catch (NotFoundException e) {
                log.warn("Pantry item {} not found during batch delete, skipping", itemId);
            }
        }

        log.info("User {} successfully deleted {} out of {} pantry items in batch",
                currentUser.getId(), deletedCount, itemIds.size());
        return deletedCount;
    }

    /**
     * Partially updates a pantry item's information using PATCH semantics.
     * Only the fields provided in the patch map will be updated.
     *
     * @param pantryItemId UUID of the pantry item to patch
     * @param patchFields  Map containing field names and values to update
     * @param token        JWT authentication token of the current user
     * @return PantryItemResponse containing the updated pantry item's details
     * @throws ValidationException              if inputs are null, empty, or contain invalid data
     * @throws NotFoundException                if pantry item or location not found
     * @throws InsufficientPermissionException  if user is not a member of the household
     * @throws DataIntegrityException           if save operation fails
     */
    @Transactional
    public PantryItemResponse patchPantryItem(UUID pantryItemId, Map<String, Object> patchFields, JwtAuthenticationToken token) {
        // Validate inputs
        if (pantryItemId == null) {
            throw new ValidationException("Pantry item ID cannot be null");
        }
        if (patchFields == null) {
            throw new ValidationException("Patch fields cannot be null");
        }
        if (patchFields.isEmpty()) {
            throw new ValidationException("Patch fields cannot be empty");
        }
        if (token == null) {
            throw new ValidationException("Authentication token cannot be null");
        }

        User currentUser = userService.getCurrentUser(token);
        log.debug("User {} patching pantry item {} with {} fields",
                currentUser.getId(), pantryItemId, patchFields.size());

        PantryItem pantryItem = pantryItemRepository.findById(pantryItemId)
                .orElseThrow(() -> new NotFoundException("Pantry item not found"));

        // Check if user is member of household
        validateHouseholdAccess(pantryItem.getLocation().getHousehold().getId(), currentUser);

        // Apply patches
        for (Map.Entry<String, Object> entry : patchFields.entrySet()) {
            String field = entry.getKey();
            Object value = entry.getValue();

            switch (field) {
                case "quantity":
                    Integer quantity = value != null ? ((Number) value).intValue() : null;
                    if (quantity != null && quantity < 0) {
                        throw new ValidationException("Quantity cannot be negative");
                    }
                    pantryItem.setQuantity(quantity);
                    break;

                case "unitOfMeasure":
                    pantryItem.setUnitOfMeasure((String) value);
                    break;

                case "expirationDate":
                    if (value == null) {
                        pantryItem.setExpirationDate(null);
                    } else if (value instanceof String) {
                        pantryItem.setExpirationDate(LocalDate.parse((String) value));
                    } else if (value instanceof LocalDate) {
                        pantryItem.setExpirationDate((LocalDate) value);
                    }
                    break;

                case "notes":
                    pantryItem.setNotes((String) value);
                    break;

                case "locationId":
                    if (value != null) {
                        UUID locationId = UUID.fromString(value.toString());
                        Location newLocation = locationRepository.findById(locationId)
                                .orElseThrow(() -> new NotFoundException("Location not found"));

                        // Verify user has access to new location's household
                        validateHouseholdAccess(newLocation.getHousehold().getId(), currentUser);

                        pantryItem.setLocation(newLocation);
                    }
                    break;

                default:
                    log.debug("Ignoring unknown field in patch: {}", field);
                    break;
            }
        }

        pantryItem.setUpdatedAt(LocalDateTime.now());

        try {
            PantryItem savedItem = pantryItemRepository.save(pantryItem);
            log.info("User {} successfully patched pantry item {}", currentUser.getId(), pantryItemId);
            return mapToResponse(savedItem);
        } catch (Exception e) {
            log.error("Failed to patch pantry item {}", pantryItemId, e);
            throw new DataIntegrityException("Failed to patch pantry item: " + e.getMessage(), e);
        }
    }

    /**
     * Gets pantry statistics for a household including item count, low stock count, and expiring items count.
     *
     * @param householdId UUID of the household
     * @param token       JWT authentication token of the current user
     * @return Map containing various pantry statistics
     * @throws ValidationException              if householdId or token is null
     * @throws InsufficientPermissionException  if user is not a member of the household
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getPantryStatistics(UUID householdId, JwtAuthenticationToken token) {
        // Validate inputs
        if (householdId == null) {
            throw new ValidationException("Household ID cannot be null");
        }
        if (token == null) {
            throw new ValidationException("Authentication token cannot be null");
        }

        User currentUser = userService.getCurrentUser(token);
        log.debug("User {} retrieving pantry statistics for household {}", currentUser.getId(), householdId);

        // Check if user is member of household
        validateHouseholdAccess(householdId, currentUser);

        // Calculate statistics
        long totalItems = pantryItemRepository.countByLocation_HouseholdId(householdId);
        long uniqueProducts = pantryItemRepository.countDistinctProductsByHouseholdId(householdId);
        long expiringInWeek = pantryItemRepository.countExpiringItemsByHouseholdIdAndDate(
                householdId, LocalDate.now().plusDays(7));

        List<PantryItem> lowStockItems = pantryItemRepository.findLowStockItemsInHousehold(householdId, 5);
        long lowStockCount = lowStockItems.size();

        Map<String, Object> statistics = new HashMap<>();
        statistics.put("totalItems", totalItems);
        statistics.put("uniqueProducts", uniqueProducts);
        statistics.put("expiringInWeek", expiringInWeek);
        statistics.put("lowStockCount", lowStockCount);

        log.debug("Pantry statistics for household {}: {} items, {} unique products, {} expiring, {} low stock",
                householdId, totalItems, uniqueProducts, expiringInWeek, lowStockCount);

        return statistics;
    }

    /**
     * Gets all variants of a product in a specific location, ordered by expiration date.
     *
     * @param locationId UUID of the location
     * @param productId  UUID of the product
     * @param token      JWT authentication token of the current user
     * @return List of PantryItemResponse objects for all variants of the product
     * @throws ValidationException              if locationId, productId, or token is null
     * @throws NotFoundException                if location or product not found
     * @throws InsufficientPermissionException  if user is not a member of the household
     */
    @Transactional(readOnly = true)
    public List<PantryItemResponse> getProductVariantsByLocation(UUID locationId, UUID productId, JwtAuthenticationToken token) {
        // Validate inputs
        if (locationId == null) {
            throw new ValidationException("Location ID cannot be null");
        }
        if (productId == null) {
            throw new ValidationException("Product ID cannot be null");
        }
        if (token == null) {
            throw new ValidationException("Authentication token cannot be null");
        }

        User currentUser = userService.getCurrentUser(token);
        log.debug("User {} retrieving product variants for product {} in location {}",
                currentUser.getId(), productId, locationId);

        Location location = locationRepository.findById(locationId)
                .orElseThrow(() -> new NotFoundException("Location not found"));

        // Check if user is member of household
        validateHouseholdAccess(location.getHousehold().getId(), currentUser);

        // Verify product exists
        if (!productRepository.existsById(productId)) {
            throw new NotFoundException("Product not found");
        }

        List<PantryItem> variants = pantryItemRepository
                .findByLocationIdAndProductIdOrderByExpirationDateNullsLast(locationId, productId);
        
        return variants.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Checks if a specific product variant exists in a location.
     *
     * @param locationId     UUID of the location
     * @param productId      UUID of the product
     * @param expirationDate Expiration date to check (null for items without expiration)
     * @param token          JWT authentication token of the current user
     * @return true if the variant exists, false otherwise
     * @throws ValidationException              if locationId, productId, or token is null
     * @throws NotFoundException                if location not found
     * @throws InsufficientPermissionException  if user is not a member of the household
     */
    @Transactional(readOnly = true)
    public boolean productVariantExists(UUID locationId, UUID productId, LocalDate expirationDate, JwtAuthenticationToken token) {
        // Validate inputs
        if (locationId == null) {
            throw new ValidationException("Location ID cannot be null");
        }
        if (productId == null) {
            throw new ValidationException("Product ID cannot be null");
        }
        if (token == null) {
            throw new ValidationException("Authentication token cannot be null");
        }

        User currentUser = userService.getCurrentUser(token);
        log.debug("User {} checking if product variant exists: product {} in location {} with expiration {}",
                currentUser.getId(), productId, locationId, expirationDate);

        Location location = locationRepository.findById(locationId)
                .orElseThrow(() -> new NotFoundException("Location not found"));

        // Check if user is member of household
        validateHouseholdAccess(location.getHousehold().getId(), currentUser);

        if (expirationDate != null) {
            return pantryItemRepository.existsByLocationIdAndProductIdAndExpirationDate(
                    locationId, productId, expirationDate);
        } else {
            return pantryItemRepository.existsByLocationIdAndProductIdAndExpirationDateIsNull(
                    locationId, productId);
        }
    }

    /**
     * Deletes a specific product variant from a location.
     *
     * @param locationId     UUID of the location
     * @param productId      UUID of the product
     * @param expirationDate Expiration date of the variant to delete (null for items without expiration)
     * @param token          JWT authentication token of the current user
     * @return number of items deleted
     * @throws ValidationException              if locationId, productId, or token is null
     * @throws NotFoundException                if location not found
     * @throws InsufficientPermissionException  if user is not a member of the household
     */
    @Transactional
    public int deleteProductVariant(UUID locationId, UUID productId, LocalDate expirationDate, JwtAuthenticationToken token) {
        // Validate inputs
        if (locationId == null) {
            throw new ValidationException("Location ID cannot be null");
        }
        if (productId == null) {
            throw new ValidationException("Product ID cannot be null");
        }
        if (token == null) {
            throw new ValidationException("Authentication token cannot be null");
        }

        User currentUser = userService.getCurrentUser(token);
        log.debug("User {} deleting product variant: product {} in location {} with expiration {}",
                currentUser.getId(), productId, locationId, expirationDate);

        Location location = locationRepository.findById(locationId)
                .orElseThrow(() -> new NotFoundException("Location not found"));

        // Check if user is member of household
        validateHouseholdAccess(location.getHousehold().getId(), currentUser);

        int deletedCount;
        if (expirationDate != null) {
            deletedCount = pantryItemRepository.deleteByLocationIdAndProductIdAndExpirationDate(
                    locationId, productId, expirationDate);
        } else {
            deletedCount = pantryItemRepository.deleteByLocationIdAndProductIdAndExpirationDateIsNull(
                    locationId, productId);
        }

        log.info("User {} deleted {} product variant(s)", currentUser.getId(), deletedCount);
        return deletedCount;
    }

    /**
     * Updates quantities for multiple pantry items in a batch operation.
     *
     * @param quantityUpdates Map of pantry item IDs to new quantities
     * @param token           JWT authentication token of the current user
     * @return List of updated PantryItemResponse objects
     * @throws ValidationException if quantityUpdates or token is null or empty
     */
    @Transactional
    public List<PantryItemResponse> updateQuantities(Map<UUID, Integer> quantityUpdates, JwtAuthenticationToken token) {
        // Validate inputs
        if (quantityUpdates == null) {
            throw new ValidationException("Quantity updates map cannot be null");
        }
        if (quantityUpdates.isEmpty()) {
            throw new ValidationException("Quantity updates map cannot be empty");
        }
        if (token == null) {
            throw new ValidationException("Authentication token cannot be null");
        }

        User currentUser = userService.getCurrentUser(token);
        log.debug("User {} updating quantities for {} pantry items", currentUser.getId(), quantityUpdates.size());

        List<PantryItemResponse> updatedItems = new ArrayList<>();

        for (Map.Entry<UUID, Integer> entry : quantityUpdates.entrySet()) {
            UUID itemId = entry.getKey();
            Integer newQuantity = entry.getValue();

            if (newQuantity != null && newQuantity < 0) {
                throw new ValidationException("Quantity cannot be negative for item " + itemId);
            }

            try {
                PantryItem item = pantryItemRepository.findById(itemId)
                        .orElseThrow(() -> new NotFoundException("Pantry item not found"));

                // Check if user is member of household
                validateHouseholdAccess(item.getLocation().getHousehold().getId(), currentUser);

                item.setQuantity(newQuantity);
                item.setUpdatedAt(LocalDateTime.now());

                PantryItem savedItem = pantryItemRepository.save(item);
                updatedItems.add(mapToResponse(savedItem));
            } catch (NotFoundException | InsufficientPermissionException e) {
                log.warn("Failed to update quantity for item {}: {}", itemId, e.getMessage());
                // Continue with other items
            }
        }

        log.info("User {} successfully updated quantities for {} out of {} pantry items",
                currentUser.getId(), updatedItems.size(), quantityUpdates.size());

        return updatedItems;
    }

    /**
     * Maps a PantryItem entity to a PantryItemResponse DTO.
     *
     * @param pantryItem PantryItem entity to map
     * @return PantryItemResponse DTO
     */
    private PantryItemResponse mapToResponse(PantryItem pantryItem) {
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
     *
     * @param product Product entity to map
     * @return ProductResponse DTO
     */
    private ProductResponse mapProductToResponse(Product product) {
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
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .build();
    }

    /**
     * Maps a Location entity to a LocationResponse DTO.
     *
     * @param location Location entity to map
     * @return LocationResponse DTO
     */
    private LocationResponse mapLocationToResponse(Location location) {
        return LocationResponse.builder()
                .id(location.getId())
                .name(location.getName())
                .description(location.getDescription())
                .householdId(location.getHousehold().getId())
                .createdAt(location.getCreatedAt())
                .updatedAt(location.getUpdatedAt())
                .build();
    }
}