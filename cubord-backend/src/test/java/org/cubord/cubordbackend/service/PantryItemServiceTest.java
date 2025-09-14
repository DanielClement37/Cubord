package org.cubord.cubordbackend.service;

import org.cubord.cubordbackend.domain.*;
import org.cubord.cubordbackend.dto.CreatePantryItemRequest;
import org.cubord.cubordbackend.dto.PantryItemResponse;
import org.cubord.cubordbackend.dto.UpdatePantryItemRequest;
import org.cubord.cubordbackend.exception.NotFoundException;
import org.cubord.cubordbackend.repository.HouseholdMemberRepository;
import org.cubord.cubordbackend.repository.LocationRepository;
import org.cubord.cubordbackend.repository.PantryItemRepository;
import org.cubord.cubordbackend.repository.ProductRepository;
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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PantryItemServiceTest {

    @Mock
    private PantryItemRepository pantryItemRepository;

    @Mock
    private LocationRepository locationRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private HouseholdMemberRepository householdMemberRepository;

    @Mock
    private UserService userService;

    @Mock
    private JwtAuthenticationToken token;

    @InjectMocks
    private PantryItemService pantryItemService;

    private User testUser;
    private User otherUser;
    private Household testHousehold;
    private Household otherHousehold;
    private HouseholdMember testHouseholdMember;
    private Location testLocation;
    private Location otherLocation;
    private Product testProduct;
    private PantryItem testPantryItem;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(UUID.randomUUID())
                .email("test@example.com")
                .username("testuser")
                .role(UserRole.USER)
                .build();

        otherUser = User.builder()
                .id(UUID.randomUUID())
                .email("other@example.com")
                .username("otheruser")
                .role(UserRole.USER)
                .build();

        testHousehold = Household.builder()
                .id(UUID.randomUUID())
                .name("Test Household")
                .build();

        otherHousehold = Household.builder()
                .id(UUID.randomUUID())
                .name("Other Household")
                .build();

        testHouseholdMember = HouseholdMember.builder()
                .id(UUID.randomUUID())
                .user(testUser)
                .household(testHousehold)
                .role(HouseholdRole.MEMBER)
                .build();

        testLocation = Location.builder()
                .id(UUID.randomUUID())
                .name("Kitchen Pantry")
                .household(testHousehold)
                .build();

        otherLocation = Location.builder()
                .id(UUID.randomUUID())
                .name("Other Kitchen")
                .household(otherHousehold)
                .build();

        testProduct = Product.builder()
                .id(UUID.randomUUID())
                .upc("123456789012")
                .name("Milk")
                .brand("Fresh Dairy")
                .category("Dairy")
                .defaultExpirationDays(7)
                .build();

        testPantryItem = PantryItem.builder()
                .id(UUID.randomUUID())
                .product(testProduct)
                .location(testLocation)
                .quantity(2)
                .unitOfMeasure("liters")
                .expirationDate(LocalDate.now().plusDays(5))
                .notes("Test notes")
                .createdAt(LocalDateTime.now().minusDays(1))
                .updatedAt(LocalDateTime.now().minusDays(1))
                .build();
    }

    @Nested
    @DisplayName("Create Pantry Item Tests")
    class CreatePantryItemTests {

        @Test
        @DisplayName("Should create new pantry item successfully")
        void shouldCreateNewPantryItemSuccessfully() {
            // Given
            CreatePantryItemRequest request = CreatePantryItemRequest.builder()
                    .productId(testProduct.getId())
                    .locationId(testLocation.getId())
                    .quantity(2)
                    .unitOfMeasure("liters")
                    .expirationDate(LocalDate.now().plusDays(7))
                    .notes("Fresh milk")
                    .build();

            when(userService.getCurrentUser(token)).thenReturn(testUser);
            when(locationRepository.findById(testLocation.getId())).thenReturn(Optional.of(testLocation));
            when(productRepository.findById(testProduct.getId())).thenReturn(Optional.of(testProduct));
            when(householdMemberRepository.existsByHouseholdIdAndUserId
                    (testHousehold.getId(), testUser.getId()))
                    .thenReturn(true);
            when(pantryItemRepository.findByLocationIdAndProductId(testLocation.getId(), testProduct.getId()))
                    .thenReturn(Optional.empty());
            when(pantryItemRepository.save(any(PantryItem.class))).thenReturn(testPantryItem);

            // When
            PantryItemResponse response = pantryItemService.createPantryItem(request, token);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo(testPantryItem.getId());
            assertThat(response.getQuantity()).isEqualTo(testPantryItem.getQuantity());
            assertThat(response.getUnitOfMeasure()).isEqualTo(testPantryItem.getUnitOfMeasure());

            verify(pantryItemRepository).save(any(PantryItem.class));
            verify(pantryItemRepository).findByLocationIdAndProductId(testLocation.getId(), testProduct.getId());
        }

        @Test
        @DisplayName("Should consolidate quantity when item already exists")
        void shouldConsolidateQuantityWhenItemAlreadyExists() {
            // Given
            CreatePantryItemRequest request = CreatePantryItemRequest.builder()
                    .productId(testProduct.getId())
                    .locationId(testLocation.getId())
                    .quantity(3)
                    .unitOfMeasure("liters")
                    .expirationDate(LocalDate.now().plusDays(7))
                    .notes("Additional milk")
                    .build();

            PantryItem existingItem = PantryItem.builder()
                    .id(UUID.randomUUID())
                    .product(testProduct)
                    .location(testLocation)
                    .quantity(2)
                    .unitOfMeasure("liters")
                    .build();

            PantryItem updatedItem = PantryItem.builder()
                    .id(existingItem.getId())
                    .product(testProduct)
                    .location(testLocation)
                    .quantity(5) // 2 + 3
                    .unitOfMeasure("liters")
                    .build();

            when(userService.getCurrentUser(token)).thenReturn(testUser);
            when(locationRepository.findById(testLocation.getId())).thenReturn(Optional.of(testLocation));
            when(productRepository.findById(testProduct.getId())).thenReturn(Optional.of(testProduct));
            when(householdMemberRepository.existsByHouseholdIdAndUserId
                    (testHousehold.getId(), testUser.getId()))
                    .thenReturn(true);
            when(pantryItemRepository.findByLocationIdAndProductId(testLocation.getId(), testProduct.getId()))
                    .thenReturn(Optional.of(existingItem));
            when(pantryItemRepository.save(any(PantryItem.class))).thenReturn(updatedItem);

            // When
            PantryItemResponse response = pantryItemService.createPantryItem(request, token);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getQuantity()).isEqualTo(5); // Consolidated quantity

            verify(pantryItemRepository).findByLocationIdAndProductId(testLocation.getId(), testProduct.getId());
            verify(pantryItemRepository).save(existingItem);
            verify(pantryItemRepository, never()).save(argThat(item -> !item.getId().equals(existingItem.getId())));
        }

        @Test
        @DisplayName("Should throw AccessDeniedException when user not member of household")
        void shouldThrowAccessDeniedExceptionWhenUserNotMemberOfHousehold() {
            // Given
            CreatePantryItemRequest request = CreatePantryItemRequest.builder()
                    .productId(testProduct.getId())
                    .locationId(testLocation.getId())
                    .quantity(2)
                    .unitOfMeasure("liters")
                    .build();

            when(userService.getCurrentUser(token)).thenReturn(otherUser);
            when(locationRepository.findById(testLocation.getId())).thenReturn(Optional.of(testLocation));
            when(productRepository.findById(testProduct.getId())).thenReturn(Optional.of(testProduct));
            when(householdMemberRepository.existsByHouseholdIdAndUserId
                    (testHousehold.getId(), otherUser.getId()))
                    .thenReturn(false);

            // When & Then
            assertThrows(AccessDeniedException.class, () ->
                    pantryItemService.createPantryItem(request, token)
            );

            verify(pantryItemRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw NotFoundException when location not found")
        void shouldThrowNotFoundExceptionWhenLocationNotFound() {
            // Given
            CreatePantryItemRequest request = CreatePantryItemRequest.builder()
                    .productId(testProduct.getId())
                    .locationId(UUID.randomUUID())
                    .quantity(2)
                    .unitOfMeasure("liters")
                    .build();

            when(userService.getCurrentUser(token)).thenReturn(testUser);
            when(locationRepository.findById(any())).thenReturn(Optional.empty());

            // When & Then
            assertThrows(NotFoundException.class, () ->
                    pantryItemService.createPantryItem(request, token)
            );

            verify(pantryItemRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw NotFoundException when product not found")
        void shouldThrowNotFoundExceptionWhenProductNotFound() {
            // Given
            CreatePantryItemRequest request = CreatePantryItemRequest.builder()
                    .productId(UUID.randomUUID())
                    .locationId(testLocation.getId())
                    .quantity(2)
                    .unitOfMeasure("liters")
                    .build();

            when(userService.getCurrentUser(token)).thenReturn(testUser);
            when(locationRepository.findById(testLocation.getId())).thenReturn(Optional.of(testLocation));
            when(productRepository.findById(any())).thenReturn(Optional.empty());

            // When & Then
            assertThrows(NotFoundException.class, () ->
                    pantryItemService.createPantryItem(request, token)
            );

            verify(pantryItemRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException for null parameters")
        void shouldThrowIllegalArgumentExceptionForNullParameters() {
            // When & Then
            assertThrows(IllegalArgumentException.class, () ->
                    pantryItemService.createPantryItem(null, token)
            );

            assertThrows(IllegalArgumentException.class, () ->
                    pantryItemService.createPantryItem(CreatePantryItemRequest.builder().build(), null)
            );

            verify(pantryItemRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Get Pantry Item Tests")
    class GetPantryItemTests {

        @Test
        @DisplayName("Should get pantry item by ID successfully")
        void shouldGetPantryItemByIdSuccessfully() {
            // Given
            when(userService.getCurrentUser(token)).thenReturn(testUser);
            when(pantryItemRepository.findById(testPantryItem.getId())).thenReturn(Optional.of(testPantryItem));
            when(householdMemberRepository.existsByHouseholdIdAndUserId
                    (testHousehold.getId(), testUser.getId()))
                    .thenReturn(true);

            // When
            PantryItemResponse response = pantryItemService.getPantryItemById(testPantryItem.getId(), token);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo(testPantryItem.getId());
            assertThat(response.getQuantity()).isEqualTo(testPantryItem.getQuantity());
        }

        @Test
        @DisplayName("Should throw NotFoundException when pantry item not found")
        void shouldThrowNotFoundExceptionWhenPantryItemNotFound() {
            // Given
            UUID nonExistentId = UUID.randomUUID();
            when(userService.getCurrentUser(token)).thenReturn(testUser);
            when(pantryItemRepository.findById(nonExistentId)).thenReturn(Optional.empty());

            // When & Then
            assertThrows(NotFoundException.class, () ->
                    pantryItemService.getPantryItemById(nonExistentId, token)
            );
        }

        @Test
        @DisplayName("Should throw AccessDeniedException when user not member of household")
        void shouldThrowAccessDeniedExceptionWhenUserNotMemberOfHousehold() {
            // Given
            when(userService.getCurrentUser(token)).thenReturn(otherUser);
            when(pantryItemRepository.findById(testPantryItem.getId())).thenReturn(Optional.of(testPantryItem));
            when(householdMemberRepository.existsByHouseholdIdAndUserId(testHousehold.getId(), otherUser.getId()))
                    .thenReturn(false);

            // When & Then
            assertThrows(AccessDeniedException.class, () ->
                    pantryItemService.getPantryItemById(testPantryItem.getId(), token)
            );
        }
    }

    @Nested
    @DisplayName("Update Pantry Item Tests")
    class UpdatePantryItemTests {

        @Test
        @DisplayName("Should update pantry item successfully")
        void shouldUpdatePantryItemSuccessfully() {
            // Given
            UpdatePantryItemRequest request = UpdatePantryItemRequest.builder()
                    .quantity(5)
                    .unitOfMeasure("bottles")
                    .expirationDate(LocalDate.now().plusDays(10))
                    .notes("Updated notes")
                    .build();

            PantryItem updatedItem = PantryItem.builder()
                    .id(testPantryItem.getId())
                    .product(testPantryItem.getProduct())
                    .location(testPantryItem.getLocation())
                    .quantity(5)
                    .unitOfMeasure("bottles")
                    .expirationDate(LocalDate.now().plusDays(10))
                    .notes("Updated notes")
                    .build();

            when(userService.getCurrentUser(token)).thenReturn(testUser);
            when(pantryItemRepository.findById(testPantryItem.getId())).thenReturn(Optional.of(testPantryItem));
            when(householdMemberRepository.existsByHouseholdIdAndUserId
                    (testHousehold.getId(), testUser.getId()))
                    .thenReturn(true);
            when(pantryItemRepository.save(any(PantryItem.class))).thenReturn(updatedItem);

            // When
            PantryItemResponse response = pantryItemService.updatePantryItem(testPantryItem.getId(), request, token);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getQuantity()).isEqualTo(5);
            assertThat(response.getUnitOfMeasure()).isEqualTo("bottles");
            assertThat(response.getNotes()).isEqualTo("Updated notes");

            verify(pantryItemRepository).save(any(PantryItem.class));
        }

        @Test
        @DisplayName("Should update location when location ID provided")
        void shouldUpdateLocationWhenLocationIdProvided() {
            // Given
            Location newLocation = Location.builder()
                    .id(UUID.randomUUID())
                    .name("Refrigerator")
                    .household(testHousehold)
                    .build();

            UpdatePantryItemRequest request = UpdatePantryItemRequest.builder()
                    .locationId(newLocation.getId())
                    .quantity(3)
                    .build();

            when(userService.getCurrentUser(token)).thenReturn(testUser);
            when(pantryItemRepository.findById(testPantryItem.getId())).thenReturn(Optional.of(testPantryItem));
            when(locationRepository.findById(newLocation.getId())).thenReturn(Optional.of(newLocation));
            when(householdMemberRepository.existsByHouseholdIdAndUserId
                    (testHousehold.getId(), testUser.getId()))
                    .thenReturn(true);
            when(pantryItemRepository.save(any(PantryItem.class))).thenReturn(testPantryItem);

            // When
            pantryItemService.updatePantryItem(testPantryItem.getId(), request, token);

            // Then
            verify(locationRepository).findById(newLocation.getId());
            verify(pantryItemRepository).save(any(PantryItem.class));
        }
    }

    @Nested
    @DisplayName("Delete Pantry Item Tests")
    class DeletePantryItemTests {

        @Test
        @DisplayName("Should delete pantry item successfully")
        void shouldDeletePantryItemSuccessfully() {
            // Given
            when(userService.getCurrentUser(token)).thenReturn(testUser);
            when(pantryItemRepository.findById(testPantryItem.getId())).thenReturn(Optional.of(testPantryItem));
            when(householdMemberRepository.existsByHouseholdIdAndUserId
                    (testHousehold.getId(), testUser.getId()))
                    .thenReturn(true);

            // When
            pantryItemService.deletePantryItem(testPantryItem.getId(), token);

            // Then
            verify(pantryItemRepository).delete(testPantryItem);
        }

        @Test
        @DisplayName("Should throw NotFoundException when pantry item not found")
        void shouldThrowNotFoundExceptionWhenPantryItemNotFound() {
            // Given
            UUID nonExistentId = UUID.randomUUID();
            when(userService.getCurrentUser(token)).thenReturn(testUser);
            when(pantryItemRepository.findById(nonExistentId)).thenReturn(Optional.empty());

            // When & Then
            assertThrows(NotFoundException.class, () ->
                    pantryItemService.deletePantryItem(nonExistentId, token)
            );

            verify(pantryItemRepository, never()).delete(any());
        }

        @Test
        @DisplayName("Should throw AccessDeniedException when user not member of household")
        void shouldThrowAccessDeniedExceptionWhenUserNotMemberOfHousehold() {
            // Given
            when(userService.getCurrentUser(token)).thenReturn(otherUser);
            when(pantryItemRepository.findById(testPantryItem.getId())).thenReturn(Optional.of(testPantryItem));
            when(householdMemberRepository.existsByHouseholdIdAndUserId(testHousehold.getId(), otherUser.getId()))
                    .thenReturn(false);

            // When & Then
            assertThrows(AccessDeniedException.class, () ->
                    pantryItemService.deletePantryItem(testPantryItem.getId(), token)
            );

            verify(pantryItemRepository, never()).delete(any());
        }
    }

    @Nested
    @DisplayName("Get Pantry Items by Household Tests")
    class GetPantryItemsByHouseholdTests {

        @Test
        @DisplayName("Should get pantry items by household with pagination")
        void shouldGetPantryItemsByHouseholdWithPagination() {
            // Given
            Pageable pageable = PageRequest.of(0, 10);
            Page<PantryItem> pantryItemPage = new PageImpl<>(List.of(testPantryItem));

            when(userService.getCurrentUser(token)).thenReturn(testUser);
            when(householdMemberRepository.existsByHouseholdIdAndUserId
                    (testHousehold.getId(), testUser.getId()))
                    .thenReturn(true);
            when(pantryItemRepository.findByLocation_HouseholdId(testHousehold.getId(), pageable))
                    .thenReturn(pantryItemPage);

            // When
            Page<PantryItemResponse> response = pantryItemService
                    .getPantryItemsByHousehold(testHousehold.getId(), pageable, token);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getContent()).hasSize(1);
            assertThat(response.getContent().getFirst().getId()).isEqualTo(testPantryItem.getId());
        }

        @Test
        @DisplayName("Should throw AccessDeniedException when user not member of household")
        void shouldThrowAccessDeniedExceptionWhenUserNotMemberOfHousehold() {
            // Given
            Pageable pageable = PageRequest.of(0, 10);
            when(userService.getCurrentUser(token)).thenReturn(otherUser);
            when(householdMemberRepository.existsByHouseholdIdAndUserId(testHousehold.getId(), otherUser.getId()))
                    .thenReturn(false);

            // When & Then
            assertThrows(AccessDeniedException.class, () ->
                    pantryItemService.getPantryItemsByHousehold(testHousehold.getId(), pageable, token)
            );
        }
    }

    @Nested
    @DisplayName("Get Pantry Items by Location Tests")
    class GetPantryItemsByLocationTests {

        @Test
        @DisplayName("Should get pantry items by location")
        void shouldGetPantryItemsByLocation() {
            // Given
            List<PantryItem> pantryItems = List.of(testPantryItem);

            when(userService.getCurrentUser(token)).thenReturn(testUser);
            when(locationRepository.findById(testLocation.getId())).thenReturn(Optional.of(testLocation));
            when(householdMemberRepository.existsByHouseholdIdAndUserId
                    (testHousehold.getId(), testUser.getId()))
                    .thenReturn(true);
            when(pantryItemRepository.findByLocationId(testLocation.getId()))
                    .thenReturn(pantryItems);

            // When
            List<PantryItemResponse> response = pantryItemService
                    .getPantryItemsByLocation(testLocation.getId(), token);

            // Then
            assertThat(response).isNotNull();
            assertThat(response).hasSize(1);
            assertThat(response.getFirst().getId()).isEqualTo(testPantryItem.getId());
        }

        @Test
        @DisplayName("Should throw NotFoundException when location not found")
        void shouldThrowNotFoundExceptionWhenLocationNotFound() {
            // Given
            UUID nonExistentLocationId = UUID.randomUUID();
            when(userService.getCurrentUser(token)).thenReturn(testUser);
            when(locationRepository.findById(nonExistentLocationId)).thenReturn(Optional.empty());

            // When & Then
            assertThrows(NotFoundException.class, () ->
                    pantryItemService.getPantryItemsByLocation(nonExistentLocationId, token)
            );
        }
    }

    @Nested
    @DisplayName("Low Stock Items Tests")
    class LowStockItemsTests {

        @Test
        @DisplayName("Should get low stock items for household")
        void shouldGetLowStockItemsForHousehold() {
            // Given
            Integer threshold = 5;
            List<PantryItem> lowStockItems = List.of(testPantryItem);

            when(userService.getCurrentUser(token)).thenReturn(testUser);
            when(householdMemberRepository.existsByHouseholdIdAndUserId
                    (testHousehold.getId(), testUser.getId()))
                    .thenReturn(true);
            when(pantryItemRepository.findLowStockItemsInHousehold(testHousehold.getId(), threshold))
                    .thenReturn(lowStockItems);

            // When
            List<PantryItemResponse> response = pantryItemService
                    .getLowStockItems(testHousehold.getId(), threshold, token);

            // Then
            assertThat(response).isNotNull();
            assertThat(response).hasSize(1);
            assertThat(response.getFirst().getId()).isEqualTo(testPantryItem.getId());
        }

        @Test
        @DisplayName("Should throw AccessDeniedException for unauthorized user")
        void shouldThrowAccessDeniedExceptionForUnauthorizedUser() {
            // Given
            Integer threshold = 5;
            when(userService.getCurrentUser(token)).thenReturn(otherUser);
            when(householdMemberRepository.existsByHouseholdIdAndUserId(testHousehold.getId(), otherUser.getId()))
                    .thenReturn(false);

            // When & Then
            assertThrows(AccessDeniedException.class, () ->
                    pantryItemService.getLowStockItems(testHousehold.getId(), threshold, token)
            );
        }
    }

    @Nested
    @DisplayName("Expiring Items Tests")
    class ExpiringItemsTests {

        @Test
        @DisplayName("Should get expiring items for household")
        void shouldGetExpiringItemsForHousehold() {
            // Given
            LocalDate startDate = LocalDate.now();
            LocalDate endDate = LocalDate.now().plusDays(7);
            List<PantryItem> expiringItems = List.of(testPantryItem);

            when(userService.getCurrentUser(token)).thenReturn(testUser);
            when(householdMemberRepository.existsByHouseholdIdAndUserId
                    (testHousehold.getId(), testUser.getId()))
                    .thenReturn(true);
            when(pantryItemRepository.findExpiringItemsInHouseholdBetweenDates(testHousehold.getId(), startDate, endDate))
                    .thenReturn(expiringItems);

            // When
            List<PantryItemResponse> response = pantryItemService
                    .getExpiringItems(testHousehold.getId(), startDate, endDate, token);

            // Then
            assertThat(response).isNotNull();
            assertThat(response).hasSize(1);
            assertThat(response.getFirst().getId()).isEqualTo(testPantryItem.getId());
        }
    }

    @Nested
    @DisplayName("Search Tests")
    class SearchTests {

        @Test
        @DisplayName("Should search items in household")
        void shouldSearchItemsInHousehold() {
            // Given
            String searchTerm = "milk";
            List<PantryItem> searchResults = List.of(testPantryItem);

            when(userService.getCurrentUser(token)).thenReturn(testUser);
            when(householdMemberRepository.existsByHouseholdIdAndUserId
                    (testHousehold.getId(), testUser.getId()))
                    .thenReturn(true);
            when(pantryItemRepository.searchItemsInHousehold(testHousehold.getId(), searchTerm))
                    .thenReturn(searchResults);

            // When
            List<PantryItemResponse> response = pantryItemService
                    .searchPantryItems(testHousehold.getId(), searchTerm, token);

            // Then
            assertThat(response).isNotNull();
            assertThat(response).hasSize(1);
            assertThat(response.getFirst().getId()).isEqualTo(testPantryItem.getId());
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException for empty search term")
        void shouldThrowIllegalArgumentExceptionForEmptySearchTerm() {
            // When & Then
            assertThrows(IllegalArgumentException.class, () ->
                    pantryItemService.searchPantryItems(testHousehold.getId(), "", token)
            );

            assertThrows(IllegalArgumentException.class, () ->
                    pantryItemService.searchPantryItems(testHousehold.getId(), "   ", token)
            );
        }
    }

    @Nested
    @DisplayName("Bulk Operations Tests")
    class BulkOperationsTests {

        @Test
        @DisplayName("Should create multiple pantry items")
        void shouldCreateMultiplePantryItems() {
            // Given
            List<CreatePantryItemRequest> requests = List.of(
                    CreatePantryItemRequest.builder()
                            .productId(testProduct.getId())
                            .locationId(testLocation.getId())
                            .quantity(2)
                            .unitOfMeasure("liters")
                            .build()
            );

            when(userService.getCurrentUser(token)).thenReturn(testUser);
            when(locationRepository.findById(testLocation.getId())).thenReturn(Optional.of(testLocation));
            when(productRepository.findById(testProduct.getId())).thenReturn(Optional.of(testProduct));
            when(householdMemberRepository.existsByHouseholdIdAndUserId
                    (testHousehold.getId(), testUser.getId()))
                    .thenReturn(true);
            when(pantryItemRepository.findByLocationIdAndProductId(testLocation.getId(), testProduct.getId()))
                    .thenReturn(Optional.empty());
            when(pantryItemRepository.save(any(PantryItem.class))).thenReturn(testPantryItem);

            // When
            List<PantryItemResponse> response = pantryItemService.createMultiplePantryItems(requests, token);

            // Then
            assertThat(response).isNotNull();
            assertThat(response).hasSize(1);
            assertThat(response.getFirst().getId()).isEqualTo(testPantryItem.getId());

            verify(pantryItemRepository).save(any(PantryItem.class));
        }

        @Test
        @DisplayName("Should delete multiple pantry items")
        void shouldDeleteMultiplePantryItems() {
            // Given
            List<UUID> itemIds = List.of(testPantryItem.getId());

            when(userService.getCurrentUser(token)).thenReturn(testUser);
            when(pantryItemRepository.findById(testPantryItem.getId())).thenReturn(Optional.of(testPantryItem));
            when(householdMemberRepository.existsByHouseholdIdAndUserId
                    (testHousehold.getId(), testUser.getId()))
                    .thenReturn(true);

            // When
            int deletedCount = pantryItemService.deleteMultiplePantryItems(itemIds, token);

            // Then
            assertThat(deletedCount).isEqualTo(1);
            verify(pantryItemRepository).delete(testPantryItem);
        }
    }
}
