package org.cubord.cubordbackend.service;

import org.cubord.cubordbackend.domain.*;
import org.cubord.cubordbackend.dto.pantryItem.*;
import org.cubord.cubordbackend.exception.*;
import org.cubord.cubordbackend.repository.*;
import org.cubord.cubordbackend.security.SecurityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive test suite for PantryItemService using the modernized security architecture.
 *
 * <p>These tests verify:</p>
 * <ul>
 *   <li>SecurityService integration for authentication and authorization</li>
 *   <li>Authorization via @PreAuthorize (integration tests verify actual enforcement)</li>
 *   <li>Business logic correctness (quantity consolidation, expiration tracking)</li>
 *   <li>Error handling and validation</li>
 * </ul>
 *
 * <p>Note: @PreAuthorize enforcement is not tested in unit tests as it requires
 * Spring Security context. Integration tests should cover authorization scenarios.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PantryItemService Tests")
class PantryItemServiceTest {

    @Mock
    private PantryItemRepository pantryItemRepository;

    @Mock
    private LocationRepository locationRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private SecurityService securityService;

    @InjectMocks
    private PantryItemService pantryItemService;

    // Test data
    private UUID userId;
    private UUID householdId;
    private UUID locationId;
    private UUID productId;
    private UUID pantryItemId;

    private User testUser;
    private Household testHousehold;
    private Location testLocation;
    private Product testProduct;
    private PantryItem testPantryItem;
    
    private CreatePantryItemRequest createRequest;
    private UpdatePantryItemRequest updateRequest;

    private LocalDateTime fixedTime;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        householdId = UUID.randomUUID();
        locationId = UUID.randomUUID();
        productId = UUID.randomUUID();
        pantryItemId = UUID.randomUUID();
        fixedTime = LocalDateTime.of(2024, 1, 1, 12, 0);

        testUser = User.builder()
                .id(userId)
                .email("test@example.com")
                .displayName("Test User")
                .username("testuser")
                .role(UserRole.USER)
                .createdAt(fixedTime)
                .updatedAt(fixedTime)
                .build();

        testHousehold = Household.builder()
                .id(householdId)
                .name("Test Household")
                .createdAt(fixedTime)
                .updatedAt(fixedTime)
                .build();

        testLocation = Location.builder()
                .id(locationId)
                .name("Pantry")
                .household(testHousehold)
                .createdAt(fixedTime)
                .updatedAt(fixedTime)
                .build();

        testProduct = Product.builder()
                .id(productId)
                .upc("123456789012")
                .name("Test Product")
                .brand("Test Brand")
                .category("Test Category")
                .defaultExpirationDays(30)
                .dataSource(ProductDataSource.MANUAL)
                .createdAt(fixedTime)
                .updatedAt(fixedTime)
                .build();

        testPantryItem = PantryItem.builder()
                .id(pantryItemId)
                .product(testProduct)
                .location(testLocation)
                .quantity(5)
                .expirationDate(LocalDate.now().plusDays(30))
                .notes("Test notes")
                .createdAt(fixedTime)
                .updatedAt(fixedTime)
                .build();

        createRequest = CreatePantryItemRequest.builder()
                .productId(productId)
                .locationId(locationId)
                .quantity(5)
                .expirationDate(LocalDate.now().plusDays(30))
                .notes("Test notes")
                .build();

        updateRequest = UpdatePantryItemRequest.builder()
                .locationId(locationId)
                .quantity(10)
                .expirationDate(LocalDate.now().plusDays(60))
                .notes("Updated notes")
                .build();
    }

    // ==================== Helper Methods ====================

    private void mockAuthenticatedUser() {
        when(securityService.getCurrentUserId()).thenReturn(userId);
        when(securityService.getCurrentUser()).thenReturn(testUser);
    }

    // ==================== Create Pantry Item Tests ====================

    @Nested
    @DisplayName("createPantryItem")
    class CreatePantryItemTests {

        @Test
        @DisplayName("should create new pantry item successfully")
        void whenValidRequest_createsNewPantryItem() {
            // Given
            when(locationRepository.findById(eq(locationId))).thenReturn(Optional.of(testLocation));
            when(productRepository.findById(eq(productId))).thenReturn(Optional.of(testProduct));
            // Use correct method name with correct parameter order
            when(pantryItemRepository.findByLocationIdAndProductIdAndExpirationDate(
                    eq(locationId), eq(productId), any(LocalDate.class))).thenReturn(Optional.empty());
            when(pantryItemRepository.save(any(PantryItem.class))).thenReturn(testPantryItem);

            // When
            PantryItemResponse response = pantryItemService.createPantryItem(createRequest);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo(pantryItemId);
            // Access nested product and location objects
            assertThat(response.getProduct().getId()).isEqualTo(productId);
            assertThat(response.getLocation().getId()).isEqualTo(locationId);
            assertThat(response.getQuantity()).isEqualTo(5);

            verify(securityService).getCurrentUserId();
            verify(locationRepository).findById(eq(locationId));
            verify(productRepository).findById(eq(productId));
            verify(pantryItemRepository).save(any(PantryItem.class));
        }

        @Test
        @DisplayName("should consolidate quantity when item with same expiration exists")
        void whenDuplicateItem_consolidatesQuantity() {
            // Given
            PantryItem existingItem = PantryItem.builder()
                    .id(pantryItemId)
                    .product(testProduct)
                    .location(testLocation)
                    .quantity(5)
                    .expirationDate(createRequest.getExpirationDate())
                    .createdAt(fixedTime)
                    .updatedAt(fixedTime)
                    .build();

            when(locationRepository.findById(eq(locationId))).thenReturn(Optional.of(testLocation));
            when(productRepository.findById(eq(productId))).thenReturn(Optional.of(testProduct));
            // Use correct method name with correct parameter order
            when(pantryItemRepository.findByLocationIdAndProductIdAndExpirationDate(
                    eq(locationId), eq(productId), eq(createRequest.getExpirationDate())))
                    .thenReturn(Optional.of(existingItem));

            PantryItem consolidatedItem = PantryItem.builder()
                    .id(pantryItemId)
                    .product(testProduct)
                    .location(testLocation)
                    .quantity(10) // 5 existing + 5 new
                    .expirationDate(createRequest.getExpirationDate())
                    .createdAt(fixedTime)
                    .updatedAt(fixedTime)
                    .build();
            when(pantryItemRepository.save(any(PantryItem.class))).thenReturn(consolidatedItem);

            // When
            PantryItemResponse response = pantryItemService.createPantryItem(createRequest);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getQuantity()).isEqualTo(10);

            verify(pantryItemRepository).save(argThat(item -> item.getQuantity() == 10));
        }

        @Test
        @DisplayName("should throw ValidationException when request is null")
        void whenRequestIsNull_throwsValidationException() {
            // When/Then
            assertThatThrownBy(() -> pantryItemService.createPantryItem(null))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Pantry item request cannot be null");

            verifyNoInteractions(securityService, locationRepository, productRepository, pantryItemRepository);
        }

        @Test
        @DisplayName("should throw ValidationException when product ID is null")
        void whenProductIdIsNull_throwsValidationException() {
            // Given
            createRequest.setProductId(null);

            // When/Then
            assertThatThrownBy(() -> pantryItemService.createPantryItem(createRequest))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Product ID cannot be null");

            verify(securityService).getCurrentUserId();
            verifyNoInteractions(locationRepository, productRepository, pantryItemRepository);
        }

        @Test
        @DisplayName("should throw ValidationException when location ID is null")
        void whenLocationIdIsNull_throwsValidationException() {
            // Given
            createRequest.setLocationId(null);

            // When/Then
            assertThatThrownBy(() -> pantryItemService.createPantryItem(createRequest))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Location ID cannot be null");

            verify(securityService).getCurrentUserId();
            verifyNoInteractions(locationRepository, productRepository, pantryItemRepository);
        }

        @Test
        @DisplayName("should throw ValidationException when quantity is negative")
        void whenQuantityIsNegative_throwsValidationException() {
            // Given
            createRequest.setQuantity(-1);

            // When/Then
            assertThatThrownBy(() -> pantryItemService.createPantryItem(createRequest))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Quantity must be a non-negative value");

            verify(securityService).getCurrentUserId();
            verifyNoInteractions(locationRepository, productRepository, pantryItemRepository);
        }

        @Test
        @DisplayName("should throw NotFoundException when location not found")
        void whenLocationNotFound_throwsNotFoundException() {
            // Given
            when(locationRepository.findById(eq(locationId))).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> pantryItemService.createPantryItem(createRequest))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("Location not found");

            verify(locationRepository).findById(eq(locationId));
            verifyNoInteractions(productRepository, pantryItemRepository);
        }

        @Test
        @DisplayName("should throw NotFoundException when product not found")
        void whenProductNotFound_throwsNotFoundException() {
            // Given
            when(locationRepository.findById(eq(locationId))).thenReturn(Optional.of(testLocation));
            when(productRepository.findById(eq(productId))).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> pantryItemService.createPantryItem(createRequest))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("Product not found");

            verify(productRepository).findById(eq(productId));
            verifyNoInteractions(pantryItemRepository);
        }

        @Test
        @DisplayName("should throw DataIntegrityException when save fails")
        void whenSaveFails_throwsDataIntegrityException() {
            // Given
            when(locationRepository.findById(eq(locationId))).thenReturn(Optional.of(testLocation));
            when(productRepository.findById(eq(productId))).thenReturn(Optional.of(testProduct));
            when(pantryItemRepository.findByLocationIdAndProductIdAndExpirationDate(
                    any(), any(), any())).thenReturn(Optional.empty());
            when(pantryItemRepository.save(any(PantryItem.class)))
                    .thenThrow(new RuntimeException("Database error"));

            // When/Then
            assertThatThrownBy(() -> pantryItemService.createPantryItem(createRequest))
                    .isInstanceOf(DataIntegrityException.class)
                    .hasMessageContaining("Failed to create pantry item");
        }
    }

    // ==================== Get Pantry Item Tests ====================

    @Nested
    @DisplayName("getPantryItemById")
    class GetPantryItemByIdTests {

        @Test
        @DisplayName("should retrieve pantry item successfully")
        void whenValidId_returnsPantryItem() {
            // Given
            when(pantryItemRepository.findById(eq(pantryItemId))).thenReturn(Optional.of(testPantryItem));

            // When
            PantryItemResponse response = pantryItemService.getPantryItemById(pantryItemId);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo(pantryItemId);
            // Access nested objects
            assertThat(response.getProduct().getName()).isEqualTo("Test Product");
            assertThat(response.getLocation().getName()).isEqualTo("Pantry");

            verify(securityService).getCurrentUserId();
            verify(pantryItemRepository).findById(eq(pantryItemId));
        }

        @Test
        @DisplayName("should throw ValidationException when ID is null")
        void whenIdIsNull_throwsValidationException() {
            // When/Then
            assertThatThrownBy(() -> pantryItemService.getPantryItemById(null))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Pantry item ID cannot be null");

            verifyNoInteractions(securityService, pantryItemRepository);
        }

        @Test
        @DisplayName("should throw NotFoundException when pantry item not found")
        void whenPantryItemNotFound_throwsNotFoundException() {
            // Given
            when(pantryItemRepository.findById(eq(pantryItemId))).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> pantryItemService.getPantryItemById(pantryItemId))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("Pantry item not found");

            verify(pantryItemRepository).findById(eq(pantryItemId));
        }
    }

    // ==================== Get Pantry Items by Location Tests ====================

    @Nested
    @DisplayName("getPantryItemsByLocation")
    class GetPantryItemsByLocationTests {

        @Test
        @DisplayName("should retrieve pantry items for location successfully")
        void whenValidLocationId_returnsPantryItems() {
            // Given
            List<PantryItem> items = Collections.singletonList(testPantryItem);
            when(pantryItemRepository.findByLocationId(eq(locationId))).thenReturn(items);

            // When
            List<PantryItemResponse> responses = pantryItemService.getPantryItemsByLocation(locationId);

            // Then
            assertThat(responses).hasSize(1);
            assertThat(responses.getFirst().getId()).isEqualTo(pantryItemId);

            verify(securityService).getCurrentUserId();
            verify(pantryItemRepository).findByLocationId(eq(locationId));
        }

        @Test
        @DisplayName("should throw ValidationException when location ID is null")
        void whenLocationIdIsNull_throwsValidationException() {
            // When/Then
            assertThatThrownBy(() -> pantryItemService.getPantryItemsByLocation(null))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Location ID cannot be null");

            verifyNoInteractions(securityService, pantryItemRepository);
        }
    }

    // ==================== Get Pantry Items by Household Tests ====================

    @Nested
    @DisplayName("getPantryItemsByHousehold")
    class GetPantryItemsByHouseholdTests {

        @Test
        @DisplayName("should retrieve paginated pantry items for household successfully")
        void whenValidHouseholdId_returnsPaginatedPantryItems() {
            // Given
            Pageable pageable = PageRequest.of(0, 20);
            Page<PantryItem> page = new PageImpl<>(Collections.singletonList(testPantryItem));
            // Use correct method name
            when(pantryItemRepository.findByLocation_HouseholdId(eq(householdId), eq(pageable)))
                    .thenReturn(page);

            // When
            Page<PantryItemResponse> responsePage = pantryItemService.getPantryItemsByHousehold(householdId, pageable);

            // Then
            assertThat(responsePage).isNotNull();
            assertThat(responsePage.getContent()).hasSize(1);
            assertThat(responsePage.getContent().getFirst().getId()).isEqualTo(pantryItemId);

            verify(securityService).getCurrentUserId();
            verify(pantryItemRepository).findByLocation_HouseholdId(eq(householdId), eq(pageable));
        }

        @Test
        @DisplayName("should throw ValidationException when household ID is null")
        void whenHouseholdIdIsNull_throwsValidationException() {
            // When/Then
            assertThatThrownBy(() -> pantryItemService.getPantryItemsByHousehold(null, PageRequest.of(0, 20)))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Household ID cannot be null");

            verifyNoInteractions(securityService, pantryItemRepository);
        }
    }

    // ==================== Get Low Stock Items Tests ====================

    @Nested
    @DisplayName("getLowStockItems")
    class GetLowStockItemsTests {

        @Test
        @DisplayName("should retrieve low stock items successfully")
        void whenValidParameters_returnsLowStockItems() {
            // Given
            int threshold = 5;
            List<PantryItem> lowStockItems = Collections.singletonList(testPantryItem);
            // Use correct method name
            when(pantryItemRepository.findLowStockItemsInHousehold(eq(householdId), eq(threshold)))
                    .thenReturn(lowStockItems);

            // When
            List<PantryItemResponse> responses = pantryItemService.getLowStockItems(householdId, threshold);

            // Then
            assertThat(responses).hasSize(1);
            assertThat(responses.getFirst().getQuantity()).isLessThanOrEqualTo(threshold);

            verify(securityService).getCurrentUserId();
            verify(pantryItemRepository).findLowStockItemsInHousehold(eq(householdId), eq(threshold));
        }

        @Test
        @DisplayName("should throw ValidationException when household ID is null")
        void whenHouseholdIdIsNull_throwsValidationException() {
            // When/Then
            assertThatThrownBy(() -> pantryItemService.getLowStockItems(null, 5))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Household ID cannot be null");

            verifyNoInteractions(securityService, pantryItemRepository);
        }

        @Test
        @DisplayName("should throw ValidationException when threshold is negative")
        void whenThresholdIsNegative_throwsValidationException() {
            // When/Then
            assertThatThrownBy(() -> pantryItemService.getLowStockItems(householdId, -1))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Threshold cannot be negative");

            verifyNoInteractions(securityService, pantryItemRepository);
        }
    }

    // ==================== Get Expiring Items Tests ====================

    @Nested
    @DisplayName("getExpiringItems")
    class GetExpiringItemsTests {

        @Test
        @DisplayName("should retrieve expiring items successfully")
        void whenValidParameters_returnsExpiringItems() {
            // Given
            int daysUntilExpiration = 7;
            LocalDate startDate = LocalDate.now();
            LocalDate endDate = LocalDate.now().plusDays(daysUntilExpiration);
            List<PantryItem> expiringItems = Collections.singletonList(testPantryItem);
            // Use correct method name
            when(pantryItemRepository.findExpiringItemsInHouseholdBetweenDates(
                    eq(householdId), eq(startDate), eq(endDate)))
                    .thenReturn(expiringItems);

            // When
            List<PantryItemResponse> responses = pantryItemService.getExpiringItems(householdId, daysUntilExpiration);

            // Then
            assertThat(responses).hasSize(1);

            verify(securityService).getCurrentUserId();
            verify(pantryItemRepository).findExpiringItemsInHouseholdBetweenDates(
                    eq(householdId), any(LocalDate.class), any(LocalDate.class));
        }

        @Test
        @DisplayName("should throw ValidationException when household ID is null")
        void whenHouseholdIdIsNull_throwsValidationException() {
            // When/Then
            assertThatThrownBy(() -> pantryItemService.getExpiringItems(null, 7))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Household ID cannot be null");

            verifyNoInteractions(securityService, pantryItemRepository);
        }

        @Test
        @DisplayName("should throw ValidationException when days is negative")
        void whenDaysIsNegative_throwsValidationException() {
            // When/Then
            assertThatThrownBy(() -> pantryItemService.getExpiringItems(householdId, -1))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Days until expiration cannot be negative");

            verifyNoInteractions(securityService, pantryItemRepository);
        }
    }

    // ==================== Update Pantry Item Tests ====================

    @Nested
    @DisplayName("updatePantryItem")
    class UpdatePantryItemTests {

        @Test
        @DisplayName("should update pantry item successfully")
        void whenValidUpdate_updatesPantryItem() {
            // Given
            when(pantryItemRepository.findById(eq(pantryItemId))).thenReturn(Optional.of(testPantryItem));
            
            PantryItem updatedItem = PantryItem.builder()
                    .id(pantryItemId)
                    .product(testProduct)
                    .location(testLocation)
                    .quantity(10)
                    .expirationDate(updateRequest.getExpirationDate())
                    .notes("Updated notes")
                    .createdAt(fixedTime)
                    .updatedAt(LocalDateTime.now())
                    .build();
            when(pantryItemRepository.save(any(PantryItem.class))).thenReturn(updatedItem);

            // When
            PantryItemResponse response = pantryItemService.updatePantryItem(pantryItemId, updateRequest);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getQuantity()).isEqualTo(10);
            assertThat(response.getNotes()).isEqualTo("Updated notes");

            verify(securityService).getCurrentUserId();
            verify(pantryItemRepository).findById(eq(pantryItemId));
            verify(pantryItemRepository).save(any(PantryItem.class));
        }

        @Test
        @DisplayName("should throw ValidationException when ID is null")
        void whenIdIsNull_throwsValidationException() {
            // When/Then
            assertThatThrownBy(() -> pantryItemService.updatePantryItem(null, updateRequest))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Pantry item ID cannot be null");

            verifyNoInteractions(securityService, pantryItemRepository);
        }

        @Test
        @DisplayName("should throw ValidationException when request is null")
        void whenRequestIsNull_throwsValidationException() {
            // When/Then
            assertThatThrownBy(() -> pantryItemService.updatePantryItem(pantryItemId, null))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Update request cannot be null");

            verifyNoInteractions(securityService, pantryItemRepository);
        }

        @Test
        @DisplayName("should throw ValidationException when quantity is negative")
        void whenQuantityIsNegative_throwsValidationException() {
            // Given
            updateRequest.setQuantity(-1);

            // When/Then
            assertThatThrownBy(() -> pantryItemService.updatePantryItem(pantryItemId, updateRequest))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Quantity must be a non-negative value");

            verify(securityService).getCurrentUserId();
            verifyNoInteractions(pantryItemRepository);
        }

        @Test
        @DisplayName("should throw NotFoundException when pantry item not found")
        void whenPantryItemNotFound_throwsNotFoundException() {
            // Given
            when(pantryItemRepository.findById(eq(pantryItemId))).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> pantryItemService.updatePantryItem(pantryItemId, updateRequest))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("Pantry item not found");

            verify(pantryItemRepository).findById(eq(pantryItemId));
        }

        @Test
        @DisplayName("should update location when new location provided and accessible")
        void whenNewLocationProvided_updatesLocation() {
            // Given
            UUID newLocationId = UUID.randomUUID();
            Location newLocation = Location.builder()
                    .id(newLocationId)
                    .name("Fridge")
                    .household(testHousehold)
                    .build();
            updateRequest.setLocationId(newLocationId);

            when(pantryItemRepository.findById(eq(pantryItemId))).thenReturn(Optional.of(testPantryItem));
            when(securityService.canAccessLocationForPantryItem(eq(newLocationId))).thenReturn(true);
            when(locationRepository.findById(eq(newLocationId))).thenReturn(Optional.of(newLocation));
            when(pantryItemRepository.save(any(PantryItem.class))).thenReturn(testPantryItem);

            // When
            PantryItemResponse response = pantryItemService.updatePantryItem(pantryItemId, updateRequest);

            // Then
            assertThat(response).isNotNull();
            verify(locationRepository).findById(eq(newLocationId));
            verify(securityService).canAccessLocationForPantryItem(eq(newLocationId));
        }

        @Test
        @DisplayName("should throw InsufficientPermissionException when no access to new location")
        void whenNoAccessToNewLocation_throwsInsufficientPermissionException() {
            // Given
            UUID newLocationId = UUID.randomUUID();
            updateRequest.setLocationId(newLocationId);

            when(pantryItemRepository.findById(eq(pantryItemId))).thenReturn(Optional.of(testPantryItem));
            when(securityService.canAccessLocationForPantryItem(eq(newLocationId))).thenReturn(false);

            // When/Then
            assertThatThrownBy(() -> pantryItemService.updatePantryItem(pantryItemId, updateRequest))
                    .isInstanceOf(InsufficientPermissionException.class)
                    .hasMessageContaining("You do not have access to the target location");

            verify(securityService).canAccessLocationForPantryItem(eq(newLocationId));
            verify(locationRepository, never()).findById(any());
        }
    }

    // ==================== Patch Pantry Item Tests ====================

    @Nested
    @DisplayName("patchPantryItem")
    class PatchPantryItemTests {

        @Test
        @DisplayName("should patch pantry item quantity successfully")
        void whenPatchingQuantity_updatesQuantity() {
            // Given
            Map<String, Object> patchData = new HashMap<>();
            patchData.put("quantity", 15);

            when(pantryItemRepository.findById(eq(pantryItemId))).thenReturn(Optional.of(testPantryItem));
            when(pantryItemRepository.save(any(PantryItem.class))).thenReturn(testPantryItem);

            // When
            PantryItemResponse response = pantryItemService.patchPantryItem(pantryItemId, patchData);

            // Then
            assertThat(response).isNotNull();
            verify(pantryItemRepository).save(argThat(item -> item.getQuantity() == 15));
        }

        @Test
        @DisplayName("should patch pantry item notes successfully")
        void whenPatchingNotes_updatesNotes() {
            // Given
            Map<String, Object> patchData = new HashMap<>();
            patchData.put("notes", "New notes");

            when(pantryItemRepository.findById(eq(pantryItemId))).thenReturn(Optional.of(testPantryItem));
            when(pantryItemRepository.save(any(PantryItem.class))).thenReturn(testPantryItem);

            // When
            PantryItemResponse response = pantryItemService.patchPantryItem(pantryItemId, patchData);

            // Then
            assertThat(response).isNotNull();
            verify(pantryItemRepository).save(argThat(item -> "New notes".equals(item.getNotes())));
        }

        @Test
        @DisplayName("should throw ValidationException when ID is null")
        void whenIdIsNull_throwsValidationException() {
            // When/Then
            assertThatThrownBy(() -> pantryItemService.patchPantryItem(null, Collections.emptyMap()))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Pantry item ID cannot be null");

            verifyNoInteractions(securityService, pantryItemRepository);
        }

        @Test
        @DisplayName("should throw ValidationException when patch data is null")
        void whenPatchDataIsNull_throwsValidationException() {
            // When/Then
            assertThatThrownBy(() -> pantryItemService.patchPantryItem(pantryItemId, null))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Patch data cannot be null or empty");

            verifyNoInteractions(securityService, pantryItemRepository);
        }

        @Test
        @DisplayName("should throw ValidationException when patch data is empty")
        void whenPatchDataIsEmpty_throwsValidationException() {
            // When/Then
            assertThatThrownBy(() -> pantryItemService.patchPantryItem(pantryItemId, Collections.emptyMap()))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Patch data cannot be null or empty");

            verifyNoInteractions(securityService, pantryItemRepository);
        }

        @Test
        @DisplayName("should throw ValidationException when patching negative quantity")
        void whenPatchingNegativeQuantity_throwsValidationException() {
            // Given
            Map<String, Object> patchData = new HashMap<>();
            patchData.put("quantity", -5);

            when(pantryItemRepository.findById(eq(pantryItemId))).thenReturn(Optional.of(testPantryItem));

            // When/Then
            assertThatThrownBy(() -> pantryItemService.patchPantryItem(pantryItemId, patchData))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Quantity must be non-negative");
        }
    }

    // ==================== Delete Pantry Item Tests ====================

    @Nested
    @DisplayName("deletePantryItem")
    class DeletePantryItemTests {

        @Test
        @DisplayName("should delete pantry item successfully")
        void whenValidId_deletesPantryItem() {
            // Given
            when(pantryItemRepository.findById(eq(pantryItemId))).thenReturn(Optional.of(testPantryItem));
            doNothing().when(pantryItemRepository).delete(any(PantryItem.class));

            // When
            pantryItemService.deletePantryItem(pantryItemId);

            // Then
            verify(securityService).getCurrentUserId();
            verify(pantryItemRepository).findById(eq(pantryItemId));
            verify(pantryItemRepository).delete(eq(testPantryItem));
        }

        @Test
        @DisplayName("should throw ValidationException when ID is null")
        void whenIdIsNull_throwsValidationException() {
            // When/Then
            assertThatThrownBy(() -> pantryItemService.deletePantryItem(null))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Pantry item ID cannot be null");

            verifyNoInteractions(securityService, pantryItemRepository);
        }

        @Test
        @DisplayName("should throw NotFoundException when pantry item not found")
        void whenPantryItemNotFound_throwsNotFoundException() {
            // Given
            when(pantryItemRepository.findById(eq(pantryItemId))).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> pantryItemService.deletePantryItem(pantryItemId))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("Pantry item not found");

            verify(pantryItemRepository).findById(eq(pantryItemId));
            verify(pantryItemRepository, never()).delete(any());
        }
    }

    // ==================== Batch Operations Tests ====================

    @Nested
    @DisplayName("createMultiplePantryItems")
    class CreateMultiplePantryItemsTests {

        @Test
        @DisplayName("should create multiple pantry items successfully")
        void whenValidRequests_createsMultiplePantryItems() {
            // Given
            List<CreatePantryItemRequest> requests = Arrays.asList(createRequest, createRequest);
            
            when(locationRepository.findById(eq(locationId))).thenReturn(Optional.of(testLocation));
            when(productRepository.findById(eq(productId))).thenReturn(Optional.of(testProduct));
            when(pantryItemRepository.findByLocationIdAndProductIdAndExpirationDate(any(), any(), any()))
                    .thenReturn(Optional.empty());
            when(pantryItemRepository.save(any(PantryItem.class))).thenReturn(testPantryItem);

            // When
            List<PantryItemResponse> responses = pantryItemService.createMultiplePantryItems(requests);

            // Then
            assertThat(responses).hasSize(2);
            verify(pantryItemRepository, times(2)).save(any(PantryItem.class));
        }

        @Test
        @DisplayName("should throw ValidationException when requests list is null")
        void whenRequestsIsNull_throwsValidationException() {
            // When/Then
            assertThatThrownBy(() -> pantryItemService.createMultiplePantryItems(null))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Pantry item requests list cannot be null or empty");

            verifyNoInteractions(securityService, pantryItemRepository);
        }

        @Test
        @DisplayName("should throw ValidationException when requests list is empty")
        void whenRequestsIsEmpty_throwsValidationException() {
            // When/Then
            assertThatThrownBy(() -> pantryItemService.createMultiplePantryItems(Collections.emptyList()))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Pantry item requests list cannot be null or empty");

            verifyNoInteractions(securityService, pantryItemRepository);
        }
    }

    @Nested
    @DisplayName("deleteMultiplePantryItems")
    class DeleteMultiplePantryItemsTests {

        @Test
        @DisplayName("should delete multiple pantry items successfully")
        void whenValidIds_deletesMultiplePantryItems() {
            // Given
            UUID id1 = UUID.randomUUID();
            UUID id2 = UUID.randomUUID();
            List<UUID> itemIds = Arrays.asList(id1, id2);

            when(securityService.canAccessPantryItem(eq(id1))).thenReturn(true);
            when(securityService.canAccessPantryItem(eq(id2))).thenReturn(true);
            when(pantryItemRepository.findById(eq(id1))).thenReturn(Optional.of(testPantryItem));
            when(pantryItemRepository.findById(eq(id2))).thenReturn(Optional.of(testPantryItem));
            doNothing().when(pantryItemRepository).delete(any(PantryItem.class));

            // When
            int deletedCount = pantryItemService.deleteMultiplePantryItems(itemIds);

            // Then
            assertThat(deletedCount).isEqualTo(2);
            verify(pantryItemRepository, times(2)).delete(any(PantryItem.class));
        }

        @Test
        @DisplayName("should skip items without access")
        void whenNoAccessToSomeItems_skipsThoseItems() {
            // Given
            UUID id1 = UUID.randomUUID();
            UUID id2 = UUID.randomUUID();
            List<UUID> itemIds = Arrays.asList(id1, id2);

            when(securityService.canAccessPantryItem(eq(id1))).thenReturn(true);
            when(securityService.canAccessPantryItem(eq(id2))).thenReturn(false);
            when(pantryItemRepository.findById(eq(id1))).thenReturn(Optional.of(testPantryItem));
            doNothing().when(pantryItemRepository).delete(any(PantryItem.class));

            // When
            int deletedCount = pantryItemService.deleteMultiplePantryItems(itemIds);

            // Then
            assertThat(deletedCount).isEqualTo(1);
            verify(pantryItemRepository, times(1)).delete(any(PantryItem.class));
        }

        @Test
        @DisplayName("should throw ValidationException when item IDs list is null")
        void whenItemIdsIsNull_throwsValidationException() {
            // When/Then
            assertThatThrownBy(() -> pantryItemService.deleteMultiplePantryItems(null))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Item IDs list cannot be null or empty");

            verifyNoInteractions(securityService, pantryItemRepository);
        }

        @Test
        @DisplayName("should throw ValidationException when item IDs list is empty")
        void whenItemIdsIsEmpty_throwsValidationException() {
            // When/Then
            assertThatThrownBy(() -> pantryItemService.deleteMultiplePantryItems(Collections.emptyList()))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Item IDs list cannot be null or empty");

            verifyNoInteractions(securityService, pantryItemRepository);
        }
    }
}