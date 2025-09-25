
package org.cubord.cubordbackend.service;

import org.cubord.cubordbackend.domain.*;
import org.cubord.cubordbackend.dto.pantryItem.CreatePantryItemRequest;
import org.cubord.cubordbackend.dto.pantryItem.PantryItemResponse;
import org.cubord.cubordbackend.dto.pantryItem.UpdatePantryItemRequest;
import org.cubord.cubordbackend.exception.NotFoundException;
import org.cubord.cubordbackend.exception.ValidationException;
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
import java.util.*;

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
            when(pantryItemRepository.findByLocationIdAndProductIdAndExpirationDate(
                    testLocation.getId(), testProduct.getId(), request.getExpirationDate()))
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
            verify(pantryItemRepository).findByLocationIdAndProductIdAndExpirationDate(
                    testLocation.getId(), testProduct.getId(), request.getExpirationDate());
        }

        @Test
        @DisplayName("Should consolidate quantity when item with same expiration date exists")
        void shouldConsolidateQuantityWhenItemWithSameExpirationDateExists() {
            // Given
            LocalDate expirationDate = LocalDate.now().plusDays(7);
            CreatePantryItemRequest request = CreatePantryItemRequest.builder()
                    .productId(testProduct.getId())
                    .locationId(testLocation.getId())
                    .quantity(3)
                    .unitOfMeasure("liters")
                    .expirationDate(expirationDate)
                    .notes("Additional milk")
                    .build();

            PantryItem existingItem = PantryItem.builder()
                    .id(UUID.randomUUID())
                    .product(testProduct)
                    .location(testLocation)
                    .quantity(2)
                    .unitOfMeasure("liters")
                    .expirationDate(expirationDate)
                    .build();

            PantryItem updatedItem = PantryItem.builder()
                    .id(existingItem.getId())
                    .product(testProduct)
                    .location(testLocation)
                    .quantity(5) // 2 + 3
                    .unitOfMeasure("liters")
                    .expirationDate(expirationDate)
                    .build();

            when(userService.getCurrentUser(token)).thenReturn(testUser);
            when(locationRepository.findById(testLocation.getId())).thenReturn(Optional.of(testLocation));
            when(productRepository.findById(testProduct.getId())).thenReturn(Optional.of(testProduct));
            when(householdMemberRepository.existsByHouseholdIdAndUserId
                    (testHousehold.getId(), testUser.getId()))
                    .thenReturn(true);
            when(pantryItemRepository.findByLocationIdAndProductIdAndExpirationDate(
                    testLocation.getId(), testProduct.getId(), expirationDate))
                    .thenReturn(Optional.of(existingItem));
            when(pantryItemRepository.save(any(PantryItem.class))).thenReturn(updatedItem);

            // When
            PantryItemResponse response = pantryItemService.createPantryItem(request, token);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getQuantity()).isEqualTo(5); // Consolidated quantity

            verify(pantryItemRepository).findByLocationIdAndProductIdAndExpirationDate(
                    testLocation.getId(), testProduct.getId(), expirationDate);
            verify(pantryItemRepository).save(existingItem);
            verify(pantryItemRepository, never()).save(argThat(item -> !item.getId().equals(existingItem.getId())));
        }

        @Test
        @DisplayName("Should create separate item when item with different expiration date exists")
        void shouldCreateSeparateItemWhenItemWithDifferentExpirationDateExists() {
            // Given
            LocalDate existingExpirationDate = LocalDate.now().plusDays(5);
            LocalDate newExpirationDate = LocalDate.now().plusDays(10);

            CreatePantryItemRequest request = CreatePantryItemRequest.builder()
                    .productId(testProduct.getId())
                    .locationId(testLocation.getId())
                    .quantity(3)
                    .unitOfMeasure("liters")
                    .expirationDate(newExpirationDate)
                    .notes("Fresh milk")
                    .build();

            // Create an existing item with different expiration date
            PantryItem existingItem = PantryItem.builder()
                    .id(UUID.randomUUID())
                    .product(testProduct)
                    .location(testLocation)
                    .quantity(2)
                    .expirationDate(existingExpirationDate)
                    .build();

            when(userService.getCurrentUser(token)).thenReturn(testUser);
            when(locationRepository.findById(testLocation.getId())).thenReturn(Optional.of(testLocation));
            when(productRepository.findById(testProduct.getId())).thenReturn(Optional.of(testProduct));
            when(householdMemberRepository.existsByHouseholdIdAndUserId(
                    testHousehold.getId(), testUser.getId()))
                    .thenReturn(true);

            when(pantryItemRepository.findByLocationIdAndProductIdAndExpirationDate(
                    testLocation.getId(), testProduct.getId(), newExpirationDate))
                    .thenReturn(Optional.empty());

            when(pantryItemRepository.save(any(PantryItem.class))).thenReturn(testPantryItem);

            // When
            PantryItemResponse response = pantryItemService.createPantryItem(request, token);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo(testPantryItem.getId());

            // Verify it checked for the NEW expiration date (not existing)
            verify(pantryItemRepository).findByLocationIdAndProductIdAndExpirationDate(
                    testLocation.getId(), testProduct.getId(), newExpirationDate);

            // Verify a NEW item was saved (not consolidated)
            verify(pantryItemRepository).save(any(PantryItem.class));

            // Verify it did NOT try to consolidate with the existing item
            verify(pantryItemRepository, never()).save(existingItem);
        }

        @Test
        @DisplayName("Should consolidate items with null expiration dates")
        void shouldConsolidateItemsWithNullExpirationDates() {
            // Given
            CreatePantryItemRequest request = CreatePantryItemRequest.builder()
                    .productId(testProduct.getId())
                    .locationId(testLocation.getId())
                    .quantity(3)
                    .unitOfMeasure("liters")
                    .expirationDate(null)
                    .notes("Non-perishable item")
                    .build();

            PantryItem existingItem = PantryItem.builder()
                    .id(UUID.randomUUID())
                    .product(testProduct)
                    .location(testLocation)
                    .quantity(2)
                    .unitOfMeasure("liters")
                    .expirationDate(null)
                    .build();

            PantryItem updatedItem = PantryItem.builder()
                    .id(existingItem.getId())
                    .product(testProduct)
                    .location(testLocation)
                    .quantity(5) // 2 + 3
                    .unitOfMeasure("liters")
                    .expirationDate(null)
                    .build();

            when(userService.getCurrentUser(token)).thenReturn(testUser);
            when(locationRepository.findById(testLocation.getId())).thenReturn(Optional.of(testLocation));
            when(productRepository.findById(testProduct.getId())).thenReturn(Optional.of(testProduct));
            when(householdMemberRepository.existsByHouseholdIdAndUserId
                    (testHousehold.getId(), testUser.getId()))
                    .thenReturn(true);
            when(pantryItemRepository.findByLocationIdAndProductIdAndExpirationDateIsNull(
                    testLocation.getId(), testProduct.getId()))
                    .thenReturn(Optional.of(existingItem));
            when(pantryItemRepository.save(any(PantryItem.class))).thenReturn(updatedItem);

            // When
            PantryItemResponse response = pantryItemService.createPantryItem(request, token);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getQuantity()).isEqualTo(5); // Consolidated quantity

            verify(pantryItemRepository).findByLocationIdAndProductIdAndExpirationDateIsNull(
                    testLocation.getId(), testProduct.getId());
            verify(pantryItemRepository).save(existingItem);
        }

        @Test
        @DisplayName("Should not consolidate null expiration item with dated expiration item")
        void shouldNotConsolidateNullExpirationItemWithDatedExpirationItem() {
            // Given
            CreatePantryItemRequest request = CreatePantryItemRequest.builder()
                    .productId(testProduct.getId())
                    .locationId(testLocation.getId())
                    .quantity(3)
                    .unitOfMeasure("liters")
                    .expirationDate(null)
                    .notes("Non-perishable item")
                    .build();

            when(userService.getCurrentUser(token)).thenReturn(testUser);
            when(locationRepository.findById(testLocation.getId())).thenReturn(Optional.of(testLocation));
            when(productRepository.findById(testProduct.getId())).thenReturn(Optional.of(testProduct));
            when(householdMemberRepository.existsByHouseholdIdAndUserId
                    (testHousehold.getId(), testUser.getId()))
                    .thenReturn(true);
            when(pantryItemRepository.findByLocationIdAndProductIdAndExpirationDateIsNull(
                    testLocation.getId(), testProduct.getId()))
                    .thenReturn(Optional.empty());
            when(pantryItemRepository.save(any(PantryItem.class))).thenReturn(testPantryItem);

            // When
            PantryItemResponse response = pantryItemService.createPantryItem(request, token);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo(testPantryItem.getId());

            verify(pantryItemRepository).findByLocationIdAndProductIdAndExpirationDateIsNull(
                    testLocation.getId(), testProduct.getId());
            verify(pantryItemRepository).save(any(PantryItem.class));
            // Should not look for items with specific expiration dates when request has null expiration
            verify(pantryItemRepository, never()).findByLocationIdAndProductIdAndExpirationDate(
                    any(UUID.class), any(UUID.class), any(LocalDate.class));
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
    @DisplayName("Patch Pantry Item Tests")
    class PatchPantryItemTests {

        @Test
        @DisplayName("Should patch single field successfully")
        void shouldPatchSingleFieldSuccessfully() {
            // Given
            Map<String, Object> patchFields = Map.of("quantity", 10);

            PantryItem updatedItem = PantryItem.builder()
                    .id(testPantryItem.getId())
                    .product(testPantryItem.getProduct())
                    .location(testPantryItem.getLocation())
                    .quantity(10)
                    .unitOfMeasure(testPantryItem.getUnitOfMeasure())
                    .expirationDate(testPantryItem.getExpirationDate())
                    .notes(testPantryItem.getNotes())
                    .build();

            when(userService.getCurrentUser(token)).thenReturn(testUser);
            when(pantryItemRepository.findById(testPantryItem.getId())).thenReturn(Optional.of(testPantryItem));
            when(householdMemberRepository.existsByHouseholdIdAndUserId(testHousehold.getId(), testUser.getId()))
                    .thenReturn(true);
            when(pantryItemRepository.save(any(PantryItem.class))).thenReturn(updatedItem);

            // When
            PantryItemResponse response = pantryItemService.patchPantryItem(testPantryItem.getId(), patchFields, token);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getQuantity()).isEqualTo(10);

            verify(pantryItemRepository).save(any(PantryItem.class));
        }

        @Test
        @DisplayName("Should patch multiple fields successfully")
        void shouldPatchMultipleFieldsSuccessfully() {
            // Given
            Map<String, Object> patchFields = Map.of(
                    "quantity", 15,
                    "unitOfMeasure", "kg",
                    "notes", "Updated notes",
                    "expirationDate", "2025-01-01"
            );

            PantryItem updatedItem = PantryItem.builder()
                    .id(testPantryItem.getId())
                    .product(testPantryItem.getProduct())
                    .location(testPantryItem.getLocation())
                    .quantity(15)
                    .unitOfMeasure("kg")
                    .expirationDate(LocalDate.of(2025, 1, 1))
                    .notes("Updated notes")
                    .build();

            when(userService.getCurrentUser(token)).thenReturn(testUser);
            when(pantryItemRepository.findById(testPantryItem.getId())).thenReturn(Optional.of(testPantryItem));
            when(householdMemberRepository.existsByHouseholdIdAndUserId(testHousehold.getId(), testUser.getId()))
                    .thenReturn(true);
            when(pantryItemRepository.save(any(PantryItem.class))).thenReturn(updatedItem);

            // When
            PantryItemResponse response = pantryItemService.patchPantryItem(testPantryItem.getId(), patchFields, token);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getQuantity()).isEqualTo(15);
            assertThat(response.getUnitOfMeasure()).isEqualTo("kg");
            assertThat(response.getNotes()).isEqualTo("Updated notes");
            assertThat(response.getExpirationDate()).isEqualTo(LocalDate.of(2025, 1, 1));

            verify(pantryItemRepository).save(any(PantryItem.class));
        }

        @Test
        @DisplayName("Should patch location successfully")
        void shouldPatchLocationSuccessfully() {
            // Given
            Location newLocation = Location.builder()
                    .id(UUID.randomUUID())
                    .name("Freezer")
                    .household(testHousehold) // Same household
                    .build();

            Map<String, Object> patchFields = Map.of("locationId", newLocation.getId().toString());

            when(userService.getCurrentUser(token)).thenReturn(testUser);
            when(pantryItemRepository.findById(testPantryItem.getId())).thenReturn(Optional.of(testPantryItem));
            when(householdMemberRepository.existsByHouseholdIdAndUserId(testHousehold.getId(), testUser.getId()))
                    .thenReturn(true);
            when(locationRepository.findById(newLocation.getId())).thenReturn(Optional.of(newLocation));
            when(pantryItemRepository.save(any(PantryItem.class))).thenReturn(testPantryItem);

            // When
            PantryItemResponse response = pantryItemService.patchPantryItem(testPantryItem.getId(), patchFields, token);

            // Then
            assertThat(response).isNotNull();

            verify(locationRepository).findById(newLocation.getId());
            verify(pantryItemRepository).save(any(PantryItem.class));
        }

        @Test
        @DisplayName("Should set expiration date to null when null value provided")
        void shouldSetExpirationDateToNullWhenNullValueProvided() {
            // Given
            Map<String, Object> patchFields = new HashMap<>();
            patchFields.put("expirationDate", null);

            PantryItem updatedItem = PantryItem.builder()
                    .id(testPantryItem.getId())
                    .product(testPantryItem.getProduct())
                    .location(testPantryItem.getLocation())
                    .quantity(testPantryItem.getQuantity())
                    .unitOfMeasure(testPantryItem.getUnitOfMeasure())
                    .expirationDate(null)
                    .notes(testPantryItem.getNotes())
                    .build();

            when(userService.getCurrentUser(token)).thenReturn(testUser);
            when(pantryItemRepository.findById(testPantryItem.getId())).thenReturn(Optional.of(testPantryItem));
            when(householdMemberRepository.existsByHouseholdIdAndUserId(testHousehold.getId(), testUser.getId()))
                    .thenReturn(true);
            when(pantryItemRepository.save(any(PantryItem.class))).thenReturn(updatedItem);

            // When
            PantryItemResponse response = pantryItemService.patchPantryItem(testPantryItem.getId(), patchFields, token);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getExpirationDate()).isNull();

            verify(pantryItemRepository).save(any(PantryItem.class));
        }

        @Test
        @DisplayName("Should set unit of measure and notes to null when null values provided")
        void shouldSetFieldsToNullWhenNullValuesProvided() {
            // Given
            Map<String, Object> patchFields = new HashMap<>();
            patchFields.put("unitOfMeasure", null);
            patchFields.put("notes", null);

            PantryItem updatedItem = PantryItem.builder()
                    .id(testPantryItem.getId())
                    .product(testPantryItem.getProduct())
                    .location(testPantryItem.getLocation())
                    .quantity(testPantryItem.getQuantity())
                    .unitOfMeasure(null)
                    .expirationDate(testPantryItem.getExpirationDate())
                    .notes(null)
                    .build();

            when(userService.getCurrentUser(token)).thenReturn(testUser);
            when(pantryItemRepository.findById(testPantryItem.getId())).thenReturn(Optional.of(testPantryItem));
            when(householdMemberRepository.existsByHouseholdIdAndUserId(testHousehold.getId(), testUser.getId()))
                    .thenReturn(true);
            when(pantryItemRepository.save(any(PantryItem.class))).thenReturn(updatedItem);

            // When
            PantryItemResponse response = pantryItemService.patchPantryItem(testPantryItem.getId(), patchFields, token);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getUnitOfMeasure()).isNull();
            assertThat(response.getNotes()).isNull();

            verify(pantryItemRepository).save(any(PantryItem.class));
        }

        @Test
        @DisplayName("Should ignore unknown fields")
        void shouldIgnoreUnknownFields() {
            // Given
            Map<String, Object> patchFields = Map.of(
                    "quantity", 5,
                    "unknownField", "unknown value",
                    "anotherUnknownField", 123
            );

            PantryItem updatedItem = PantryItem.builder()
                    .id(testPantryItem.getId())
                    .product(testPantryItem.getProduct())
                    .location(testPantryItem.getLocation())
                    .quantity(5)
                    .unitOfMeasure(testPantryItem.getUnitOfMeasure())
                    .expirationDate(testPantryItem.getExpirationDate())
                    .notes(testPantryItem.getNotes())
                    .build();

            when(userService.getCurrentUser(token)).thenReturn(testUser);
            when(pantryItemRepository.findById(testPantryItem.getId())).thenReturn(Optional.of(testPantryItem));
            when(householdMemberRepository.existsByHouseholdIdAndUserId(testHousehold.getId(), testUser.getId()))
                    .thenReturn(true);
            when(pantryItemRepository.save(any(PantryItem.class))).thenReturn(updatedItem);

            // When
            PantryItemResponse response = pantryItemService.patchPantryItem(testPantryItem.getId(), patchFields, token);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getQuantity()).isEqualTo(5);

            verify(pantryItemRepository).save(any(PantryItem.class));
        }

        // Bad path tests

        @Test
        @DisplayName("Should throw IllegalArgumentException when pantry item ID is null")
        void shouldThrowIllegalArgumentExceptionWhenPantryItemIdIsNull() {
            // Given
            Map<String, Object> patchFields = Map.of("quantity", 5);

            // When & Then
            assertThrows(IllegalArgumentException.class, () ->
                    pantryItemService.patchPantryItem(null, patchFields, token)
            );

            verify(pantryItemRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException when patch fields is null")
        void shouldThrowIllegalArgumentExceptionWhenPatchFieldsIsNull() {
            // When & Then
            assertThrows(IllegalArgumentException.class, () ->
                    pantryItemService.patchPantryItem(testPantryItem.getId(), null, token)
            );

            verify(pantryItemRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException when token is null")
        void shouldThrowIllegalArgumentExceptionWhenTokenIsNull() {
            // Given
            Map<String, Object> patchFields = Map.of("quantity", 5);

            // When & Then
            assertThrows(IllegalArgumentException.class, () ->
                    pantryItemService.patchPantryItem(testPantryItem.getId(), patchFields, null)
            );

            verify(pantryItemRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException when patch fields is empty")
        void shouldThrowIllegalArgumentExceptionWhenPatchFieldsIsEmpty() {
            // Given
            Map<String, Object> patchFields = new HashMap<>();

            // When & Then
            assertThrows(IllegalArgumentException.class, () ->
                    pantryItemService.patchPantryItem(testPantryItem.getId(), patchFields, token)
            );

            verify(pantryItemRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw NotFoundException when pantry item not found")
        void shouldThrowNotFoundExceptionWhenPantryItemNotFound() {
            // Given
            UUID nonExistentId = UUID.randomUUID();
            Map<String, Object> patchFields = Map.of("quantity", 5);

            when(userService.getCurrentUser(token)).thenReturn(testUser);
            when(pantryItemRepository.findById(nonExistentId)).thenReturn(Optional.empty());

            // When & Then
            assertThrows(NotFoundException.class, () ->
                    pantryItemService.patchPantryItem(nonExistentId, patchFields, token)
            );

            verify(pantryItemRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw AccessDeniedException when user not member of household")
        void shouldThrowAccessDeniedExceptionWhenUserNotMemberOfHousehold() {
            // Given
            Map<String, Object> patchFields = Map.of("quantity", 5);

            when(userService.getCurrentUser(token)).thenReturn(otherUser);
            when(pantryItemRepository.findById(testPantryItem.getId())).thenReturn(Optional.of(testPantryItem));
            when(householdMemberRepository.existsByHouseholdIdAndUserId(testHousehold.getId(), otherUser.getId()))
                    .thenReturn(false);

            // When & Then
            assertThrows(AccessDeniedException.class, () ->
                    pantryItemService.patchPantryItem(testPantryItem.getId(), patchFields, token)
            );

            verify(pantryItemRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw ValidationException for invalid location ID format")
        void shouldThrowValidationExceptionForInvalidLocationIdFormat() {
            // Given
            Map<String, Object> patchFields = Map.of("locationId", "invalid-uuid-format");

            when(userService.getCurrentUser(token)).thenReturn(testUser);
            when(pantryItemRepository.findById(testPantryItem.getId())).thenReturn(Optional.of(testPantryItem));
            when(householdMemberRepository.existsByHouseholdIdAndUserId(testHousehold.getId(), testUser.getId()))
                    .thenReturn(true);

            // When & Then
            assertThrows(ValidationException.class, () ->
                    pantryItemService.patchPantryItem(testPantryItem.getId(), patchFields, token)
            );

            verify(pantryItemRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw NotFoundException when new location not found")
        void shouldThrowNotFoundExceptionWhenNewLocationNotFound() {
            // Given
            UUID nonExistentLocationId = UUID.randomUUID();
            Map<String, Object> patchFields = Map.of("locationId", nonExistentLocationId.toString());

            when(userService.getCurrentUser(token)).thenReturn(testUser);
            when(pantryItemRepository.findById(testPantryItem.getId())).thenReturn(Optional.of(testPantryItem));
            when(householdMemberRepository.existsByHouseholdIdAndUserId(testHousehold.getId(), testUser.getId()))
                    .thenReturn(true);
            when(locationRepository.findById(nonExistentLocationId)).thenReturn(Optional.empty());

            // When & Then
            assertThrows(NotFoundException.class, () ->
                    pantryItemService.patchPantryItem(testPantryItem.getId(), patchFields, token)
            );

            verify(pantryItemRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw AccessDeniedException when trying to move item to different household")
        void shouldThrowAccessDeniedExceptionWhenTryingToMoveItemToDifferentHousehold() {
            // Given
            Location locationInDifferentHousehold = Location.builder()
                    .id(UUID.randomUUID())
                    .name("Other Kitchen")
                    .household(otherHousehold)
                    .build();

            Map<String, Object> patchFields = Map.of("locationId", locationInDifferentHousehold.getId().toString());

            when(userService.getCurrentUser(token)).thenReturn(testUser);
            when(pantryItemRepository.findById(testPantryItem.getId())).thenReturn(Optional.of(testPantryItem));
            when(householdMemberRepository.existsByHouseholdIdAndUserId(testHousehold.getId(), testUser.getId()))
                    .thenReturn(true);
            when(locationRepository.findById(locationInDifferentHousehold.getId()))
                    .thenReturn(Optional.of(locationInDifferentHousehold));

            // When & Then
            assertThrows(AccessDeniedException.class, () ->
                    pantryItemService.patchPantryItem(testPantryItem.getId(), patchFields, token)
            );

            verify(pantryItemRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw ValidationException for invalid quantity format")
        void shouldThrowValidationExceptionForInvalidQuantityFormat() {
            // Given
            Map<String, Object> patchFields = Map.of("quantity", "not-a-number");

            when(userService.getCurrentUser(token)).thenReturn(testUser);
            when(pantryItemRepository.findById(testPantryItem.getId())).thenReturn(Optional.of(testPantryItem));
            when(householdMemberRepository.existsByHouseholdIdAndUserId(testHousehold.getId(), testUser.getId()))
                    .thenReturn(true);

            // When & Then
            assertThrows(ValidationException.class, () ->
                    pantryItemService.patchPantryItem(testPantryItem.getId(), patchFields, token)
            );

            verify(pantryItemRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw ValidationException for zero quantity")
        void shouldThrowValidationExceptionForZeroQuantity() {
            // Given
            Map<String, Object> patchFields = Map.of("quantity", 0);

            when(userService.getCurrentUser(token)).thenReturn(testUser);
            when(pantryItemRepository.findById(testPantryItem.getId())).thenReturn(Optional.of(testPantryItem));
            when(householdMemberRepository.existsByHouseholdIdAndUserId(testHousehold.getId(), testUser.getId()))
                    .thenReturn(true);

            // When & Then
            assertThrows(ValidationException.class, () ->
                    pantryItemService.patchPantryItem(testPantryItem.getId(), patchFields, token)
            );

            verify(pantryItemRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw ValidationException for negative quantity")
        void shouldThrowValidationExceptionForNegativeQuantity() {
            // Given
            Map<String, Object> patchFields = Map.of("quantity", -5);

            when(userService.getCurrentUser(token)).thenReturn(testUser);
            when(pantryItemRepository.findById(testPantryItem.getId())).thenReturn(Optional.of(testPantryItem));
            when(householdMemberRepository.existsByHouseholdIdAndUserId(testHousehold.getId(), testUser.getId()))
                    .thenReturn(true);

            // When & Then
            assertThrows(ValidationException.class, () ->
                    pantryItemService.patchPantryItem(testPantryItem.getId(), patchFields, token)
            );

            verify(pantryItemRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw ValidationException for invalid expiration date format")
        void shouldThrowValidationExceptionForInvalidExpirationDateFormat() {
            // Given
            Map<String, Object> patchFields = Map.of("expirationDate", "invalid-date-format");

            when(userService.getCurrentUser(token)).thenReturn(testUser);
            when(pantryItemRepository.findById(testPantryItem.getId())).thenReturn(Optional.of(testPantryItem));
            when(householdMemberRepository.existsByHouseholdIdAndUserId(testHousehold.getId(), testUser.getId()))
                    .thenReturn(true);

            // When & Then
            assertThrows(ValidationException.class, () ->
                    pantryItemService.patchPantryItem(testPantryItem.getId(), patchFields, token)
            );

            verify(pantryItemRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should handle mixed valid and invalid fields gracefully")
        void shouldHandleMixedValidAndInvalidFieldsGracefully() {
            // Given - Mix of valid fields, unknown fields, and one invalid field
            Map<String, Object> patchFields = Map.of(
                    "notes", "Valid notes update",
                    "unknownField", "unknown value",
                    "quantity", -1 // This should cause a ValidationException
            );

            when(userService.getCurrentUser(token)).thenReturn(testUser);
            when(pantryItemRepository.findById(testPantryItem.getId())).thenReturn(Optional.of(testPantryItem));
            when(householdMemberRepository.existsByHouseholdIdAndUserId(testHousehold.getId(), testUser.getId()))
                    .thenReturn(true);

            // When & Then
            assertThrows(ValidationException.class, () ->
                    pantryItemService.patchPantryItem(testPantryItem.getId(), patchFields, token)
            );

            verify(pantryItemRepository, never()).save(any());
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
        @DisplayName("Should create multiple pantry items with expiration-date-aware logic")
        void shouldCreateMultiplePantryItemsWithExpirationDateAwareLogic() {
            // Given
            LocalDate expirationDate1 = LocalDate.now().plusDays(7);
            LocalDate expirationDate2 = LocalDate.now().plusDays(14);

            List<CreatePantryItemRequest> requests = List.of(
                    CreatePantryItemRequest.builder()
                            .productId(testProduct.getId())
                            .locationId(testLocation.getId())
                            .quantity(2)
                            .unitOfMeasure("liters")
                            .expirationDate(expirationDate1)
                            .build(),
                    CreatePantryItemRequest.builder()
                            .productId(testProduct.getId())
                            .locationId(testLocation.getId())
                            .quantity(3)
                            .unitOfMeasure("liters")
                            .expirationDate(expirationDate2)
                            .build()
            );

            PantryItem savedItem1 = PantryItem.builder()
                    .id(UUID.randomUUID())
                    .product(testProduct)
                    .location(testLocation)
                    .quantity(2)
                    .unitOfMeasure("liters")
                    .expirationDate(expirationDate1)
                    .build();

            PantryItem savedItem2 = PantryItem.builder()
                    .id(UUID.randomUUID())
                    .product(testProduct)
                    .location(testLocation)
                    .quantity(3)
                    .unitOfMeasure("liters")
                    .expirationDate(expirationDate2)
                    .build();

            when(userService.getCurrentUser(token)).thenReturn(testUser);
            when(locationRepository.findById(testLocation.getId())).thenReturn(Optional.of(testLocation));
            when(productRepository.findById(testProduct.getId())).thenReturn(Optional.of(testProduct));
            when(householdMemberRepository.existsByHouseholdIdAndUserId
                    (testHousehold.getId(), testUser.getId()))
                    .thenReturn(true);
            when(pantryItemRepository.findByLocationIdAndProductIdAndExpirationDate(
                    testLocation.getId(), testProduct.getId(), expirationDate1))
                    .thenReturn(Optional.empty());
            when(pantryItemRepository.findByLocationIdAndProductIdAndExpirationDate(
                    testLocation.getId(), testProduct.getId(), expirationDate2))
                    .thenReturn(Optional.empty());
            when(pantryItemRepository.save(any(PantryItem.class)))
                    .thenReturn(savedItem1)
                    .thenReturn(savedItem2);

            // When
            List<PantryItemResponse> response = pantryItemService.createMultiplePantryItems(requests, token);

            // Then
            assertThat(response).isNotNull();
            assertThat(response).hasSize(2);
            assertThat(response.get(0).getId()).isEqualTo(savedItem1.getId());
            assertThat(response.get(1).getId()).isEqualTo(savedItem2.getId());

            verify(pantryItemRepository, times(2)).save(any(PantryItem.class));
            verify(pantryItemRepository).findByLocationIdAndProductIdAndExpirationDate(
                    testLocation.getId(), testProduct.getId(), expirationDate1);
            verify(pantryItemRepository).findByLocationIdAndProductIdAndExpirationDate(
                    testLocation.getId(), testProduct.getId(), expirationDate2);
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

    @Nested
    @DisplayName("Product Variant Tests")
    class ProductVariantTests {

        @Test
        @DisplayName("Should get product variants by location ordered by expiration date")
        void shouldGetProductVariantsByLocationOrderedByExpirationDate() {
            // Given
            LocalDate expirationDate1 = LocalDate.now().plusDays(5);
            LocalDate expirationDate2 = LocalDate.now().plusDays(10);

            PantryItem variant1 = PantryItem.builder()
                    .id(UUID.randomUUID())
                    .product(testProduct)
                    .location(testLocation)
                    .quantity(2)
                    .expirationDate(expirationDate1)
                    .build();

            PantryItem variant2 = PantryItem.builder()
                    .id(UUID.randomUUID())
                    .product(testProduct)
                    .location(testLocation)
                    .quantity(3)
                    .expirationDate(expirationDate2)
                    .build();

            List<PantryItem> variants = List.of(variant1, variant2);

            when(userService.getCurrentUser(token)).thenReturn(testUser);
            when(locationRepository.findById(testLocation.getId())).thenReturn(Optional.of(testLocation));
            when(productRepository.findById(testProduct.getId())).thenReturn(Optional.of(testProduct));
            when(householdMemberRepository.existsByHouseholdIdAndUserId(testHousehold.getId(), testUser.getId()))
                    .thenReturn(true);
            when(pantryItemRepository.findByLocationIdAndProductIdOrderByExpirationDateNullsLast(
                    testLocation.getId(), testProduct.getId()))
                    .thenReturn(variants);

            // When
            List<PantryItemResponse> response = pantryItemService.getProductVariantsByLocation(
                    testLocation.getId(), testProduct.getId(), token);

            // Then
            assertThat(response).isNotNull();
            assertThat(response).hasSize(2);
            assertThat(response.get(0).getId()).isEqualTo(variant1.getId());
            assertThat(response.get(1).getId()).isEqualTo(variant2.getId());

            verify(pantryItemRepository).findByLocationIdAndProductIdOrderByExpirationDateNullsLast(
                    testLocation.getId(), testProduct.getId());
        }

        @Test
        @DisplayName("Should check if product variant exists with expiration date")
        void shouldCheckIfProductVariantExistsWithExpirationDate() {
            // Given
            LocalDate expirationDate = LocalDate.now().plusDays(7);

            when(userService.getCurrentUser(token)).thenReturn(testUser);
            when(locationRepository.findById(testLocation.getId())).thenReturn(Optional.of(testLocation));
            when(householdMemberRepository.existsByHouseholdIdAndUserId(testHousehold.getId(), testUser.getId()))
                    .thenReturn(true);
            when(pantryItemRepository.existsByLocationIdAndProductIdAndExpirationDate(
                    testLocation.getId(), testProduct.getId(), expirationDate))
                    .thenReturn(true);

            // When
            boolean exists = pantryItemService.productVariantExists(
                    testLocation.getId(), testProduct.getId(), expirationDate, token);

            // Then
            assertThat(exists).isTrue();

            verify(pantryItemRepository).existsByLocationIdAndProductIdAndExpirationDate(
                    testLocation.getId(), testProduct.getId(), expirationDate);
        }

        @Test
        @DisplayName("Should check if product variant exists without expiration date")
        void shouldCheckIfProductVariantExistsWithoutExpirationDate() {
            // Given
            when(userService.getCurrentUser(token)).thenReturn(testUser);
            when(locationRepository.findById(testLocation.getId())).thenReturn(Optional.of(testLocation));
            when(householdMemberRepository.existsByHouseholdIdAndUserId(testHousehold.getId(), testUser.getId()))
                    .thenReturn(true);
            when(pantryItemRepository.existsByLocationIdAndProductIdAndExpirationDateIsNull(
                    testLocation.getId(), testProduct.getId()))
                    .thenReturn(false);

            // When
            boolean exists = pantryItemService.productVariantExists(
                    testLocation.getId(), testProduct.getId(), null, token);

            // Then
            assertThat(exists).isFalse();

            verify(pantryItemRepository).existsByLocationIdAndProductIdAndExpirationDateIsNull(
                    testLocation.getId(), testProduct.getId());
        }

        @Test
        @DisplayName("Should delete product variant with expiration date")
        void shouldDeleteProductVariantWithExpirationDate() {
            // Given
            LocalDate expirationDate = LocalDate.now().plusDays(7);

            when(userService.getCurrentUser(token)).thenReturn(testUser);
            when(locationRepository.findById(testLocation.getId())).thenReturn(Optional.of(testLocation));
            when(householdMemberRepository.existsByHouseholdIdAndUserId(testHousehold.getId(), testUser.getId()))
                    .thenReturn(true);
            when(pantryItemRepository.deleteByLocationIdAndProductIdAndExpirationDate(
                    testLocation.getId(), testProduct.getId(), expirationDate))
                    .thenReturn(1);

            // When
            int deletedCount = pantryItemService.deleteProductVariant(
                    testLocation.getId(), testProduct.getId(), expirationDate, token);

            // Then
            assertThat(deletedCount).isEqualTo(1);

            verify(pantryItemRepository).deleteByLocationIdAndProductIdAndExpirationDate(
                    testLocation.getId(), testProduct.getId(), expirationDate);
        }

        @Test
        @DisplayName("Should delete product variant without expiration date")
        void shouldDeleteProductVariantWithoutExpirationDate() {
            // Given
            when(userService.getCurrentUser(token)).thenReturn(testUser);
            when(locationRepository.findById(testLocation.getId())).thenReturn(Optional.of(testLocation));
            when(householdMemberRepository.existsByHouseholdIdAndUserId(testHousehold.getId(), testUser.getId()))
                    .thenReturn(true);
            when(pantryItemRepository.deleteByLocationIdAndProductIdAndExpirationDateIsNull(
                    testLocation.getId(), testProduct.getId()))
                    .thenReturn(2);

            // When
            int deletedCount = pantryItemService.deleteProductVariant(
                    testLocation.getId(), testProduct.getId(), null, token);

            // Then
            assertThat(deletedCount).isEqualTo(2);

            verify(pantryItemRepository).deleteByLocationIdAndProductIdAndExpirationDateIsNull(
                    testLocation.getId(), testProduct.getId());
        }

        @Test
        @DisplayName("Should throw AccessDeniedException for unauthorized user in product variant operations")
        void shouldThrowAccessDeniedExceptionForUnauthorizedUserInProductVariantOperations() {
            // Given
            when(userService.getCurrentUser(token)).thenReturn(otherUser);
            when(locationRepository.findById(testLocation.getId())).thenReturn(Optional.of(testLocation));
            when(householdMemberRepository.existsByHouseholdIdAndUserId(testHousehold.getId(), otherUser.getId()))
                    .thenReturn(false);

            // When & Then
            assertThrows(AccessDeniedException.class, () ->
                    pantryItemService.getProductVariantsByLocation(testLocation.getId(), testProduct.getId(), token)
            );

            assertThrows(AccessDeniedException.class, () ->
                    pantryItemService.productVariantExists(testLocation.getId(), testProduct.getId(), LocalDate.now(), token)
            );

            assertThrows(AccessDeniedException.class, () ->
                    pantryItemService.deleteProductVariant(testLocation.getId(), testProduct.getId(), LocalDate.now(), token)
            );
        }
    }
}