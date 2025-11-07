package org.cubord.cubordbackend.service;

import org.cubord.cubordbackend.domain.*;
import org.cubord.cubordbackend.dto.pantryItem.CreatePantryItemRequest;
import org.cubord.cubordbackend.dto.pantryItem.PantryItemResponse;
import org.cubord.cubordbackend.dto.pantryItem.UpdatePantryItemRequest;
import org.cubord.cubordbackend.exception.DataIntegrityException;
import org.cubord.cubordbackend.exception.InsufficientPermissionException;
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
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Pantry Item Service Tests")
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
    private JwtAuthenticationToken validToken;
    @Mock
    private JwtAuthenticationToken invalidToken;

    @InjectMocks
    private PantryItemService pantryItemService;

    // Test data
    private User testUser;
    private User otherUser;
    private Household testHousehold;
    private Household otherHousehold;
    private HouseholdMember testHouseholdMember;
    private Location testLocation;
    private Location otherLocation;
    private Product testProduct;
    private PantryItem testPantryItem;
    private CreatePantryItemRequest testCreateRequest;
    private UpdatePantryItemRequest testUpdateRequest;
    private UUID pantryItemId;
    private UUID userId;
    private UUID locationId;
    private UUID productId;
    private UUID householdId;

    @BeforeEach
    void setUp() {
        reset(pantryItemRepository, locationRepository, productRepository,
                householdMemberRepository, userService, validToken, invalidToken);

        pantryItemId = UUID.randomUUID();
        userId = UUID.randomUUID();
        locationId = UUID.randomUUID();
        productId = UUID.randomUUID();
        householdId = UUID.randomUUID();

        testUser = User.builder()
                .id(userId)
                .username("testuser")
                .email("test@example.com")
                .displayName("Test User")
                .role(UserRole.USER)
                .createdAt(LocalDateTime.now())
                .build();

        otherUser = User.builder()
                .id(UUID.randomUUID())
                .username("otheruser")
                .email("other@example.com")
                .displayName("Other User")
                .role(UserRole.USER)
                .createdAt(LocalDateTime.now())
                .build();

        testHousehold = Household.builder()
                .id(householdId)
                .name("Test Household")
                .createdAt(LocalDateTime.now())
                .build();

        otherHousehold = Household.builder()
                .id(UUID.randomUUID())
                .name("Other Household")
                .createdAt(LocalDateTime.now())
                .build();

        testHouseholdMember = HouseholdMember.builder()
                .id(UUID.randomUUID())
                .user(testUser)
                .household(testHousehold)
                .role(HouseholdRole.MEMBER)
                .build();

        testLocation = Location.builder()
                .id(locationId)
                .name("Kitchen Pantry")
                .household(testHousehold)
                .createdAt(LocalDateTime.now())
                .build();

        otherLocation = Location.builder()
                .id(UUID.randomUUID())
                .name("Other Kitchen")
                .household(otherHousehold)
                .createdAt(LocalDateTime.now())
                .build();

        testProduct = Product.builder()
                .id(productId)
                .upc("123456789012")
                .name("Milk")
                .brand("Fresh Dairy")
                .category("Dairy")
                .defaultExpirationDays(7)
                .dataSource(ProductDataSource.MANUAL)
                .createdAt(LocalDateTime.now())
                .build();

        testPantryItem = PantryItem.builder()
                .id(pantryItemId)
                .product(testProduct)
                .location(testLocation)
                .quantity(2)
                .unitOfMeasure("liters")
                .expirationDate(LocalDate.now().plusDays(5))
                .notes("Test notes")
                .createdAt(LocalDateTime.now().minusDays(1))
                .updatedAt(LocalDateTime.now().minusDays(1))
                .build();

        testCreateRequest = CreatePantryItemRequest.builder()
                .productId(productId)
                .locationId(locationId)
                .quantity(2)
                .unitOfMeasure("liters")
                .expirationDate(LocalDate.now().plusDays(7))
                .notes("Fresh milk")
                .build();

        testUpdateRequest = UpdatePantryItemRequest.builder()
                .quantity(3)
                .unitOfMeasure("gallons")
                .expirationDate(LocalDate.now().plusDays(10))
                .notes("Updated notes")
                .build();
    }

    // Helper method to set up authentication behavior
    private void setupValidAuthentication() {
        when(userService.getCurrentUser(eq(validToken))).thenReturn(testUser);
    }

    private void setupInvalidAuthentication() {
        when(userService.getCurrentUser(eq(invalidToken))).thenThrow(new NotFoundException("User not found"));
    }

    private void setupHouseholdMembership() {
        when(householdMemberRepository.existsByHouseholdIdAndUserId(eq(householdId), eq(userId)))
                .thenReturn(true);
    }

    private void setupNoHouseholdMembership() {
        when(householdMemberRepository.existsByHouseholdIdAndUserId(eq(householdId), eq(userId)))
                .thenReturn(false);
    }

    @Nested
    @DisplayName("Authentication Tests")
    class AuthenticationTests {

        @Test
        @DisplayName("Should throw ValidationException when token is null for create")
        void shouldThrowValidationExceptionWhenTokenIsNullForCreate() {
            assertThatThrownBy(() -> pantryItemService.createPantryItem(testCreateRequest, null))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("token cannot be null");

            verify(userService, never()).getCurrentUser(any(JwtAuthenticationToken.class));
            verify(pantryItemRepository, never()).save(any(PantryItem.class));
        }

        @Test
        @DisplayName("Should throw ValidationException when token is null for getById")
        void shouldThrowValidationExceptionWhenTokenIsNullForGetById() {
            assertThatThrownBy(() -> pantryItemService.getPantryItemById(pantryItemId, null))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("token cannot be null");

            verify(userService, never()).getCurrentUser(any(JwtAuthenticationToken.class));
            verify(pantryItemRepository, never()).findById(any(UUID.class));
        }

        @Test
        @DisplayName("Should throw ValidationException when token is null for update")
        void shouldThrowValidationExceptionWhenTokenIsNullForUpdate() {
            assertThatThrownBy(() -> pantryItemService.updatePantryItem(pantryItemId, testUpdateRequest, null))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("token cannot be null");

            verify(userService, never()).getCurrentUser(any(JwtAuthenticationToken.class));
            verify(pantryItemRepository, never()).save(any(PantryItem.class));
        }

        @Test
        @DisplayName("Should throw ValidationException when token is null for delete")
        void shouldThrowValidationExceptionWhenTokenIsNullForDelete() {
            assertThatThrownBy(() -> pantryItemService.deletePantryItem(pantryItemId, null))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("token cannot be null");

            verify(userService, never()).getCurrentUser(any(JwtAuthenticationToken.class));
            verify(pantryItemRepository, never()).delete(any(PantryItem.class));
        }

        @Test
        @DisplayName("Should throw ValidationException when request is null for create")
        void shouldThrowValidationExceptionWhenRequestIsNull() {
            assertThatThrownBy(() -> pantryItemService.createPantryItem(null, validToken))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("request cannot be null");

            verify(userService, never()).getCurrentUser(any(JwtAuthenticationToken.class));
            verify(pantryItemRepository, never()).save(any(PantryItem.class));
        }

        @Test
        @DisplayName("Should throw ValidationException when pantry item ID is null for getById")
        void shouldThrowValidationExceptionWhenPantryItemIdIsNull() {
            assertThatThrownBy(() -> pantryItemService.getPantryItemById(null, validToken))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Pantry item ID cannot be null");

            verify(userService, never()).getCurrentUser(any(JwtAuthenticationToken.class));
            verify(pantryItemRepository, never()).findById(any(UUID.class));
        }

        @Test
        @DisplayName("Should throw NotFoundException when user not found")
        void shouldThrowNotFoundExceptionWhenUserNotFound() {
            setupInvalidAuthentication();

            assertThatThrownBy(() -> pantryItemService.createPantryItem(testCreateRequest, invalidToken))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("User not found");

            verify(userService).getCurrentUser(eq(invalidToken));
            verify(pantryItemRepository, never()).save(any(PantryItem.class));
        }
    }

    @Nested
    @DisplayName("Create Pantry Item Tests")
    class CreatePantryItemTests {

        @Test
        @DisplayName("Should create new pantry item successfully")
        void shouldCreateNewPantryItemSuccessfully() {
            // Given
            setupValidAuthentication();
            setupHouseholdMembership();
            when(locationRepository.findById(eq(locationId))).thenReturn(Optional.of(testLocation));
            when(productRepository.findById(eq(productId))).thenReturn(Optional.of(testProduct));
            when(pantryItemRepository.findByLocationIdAndProductIdAndExpirationDate(
                    eq(locationId), eq(productId), eq(testCreateRequest.getExpirationDate())))
                    .thenReturn(Optional.empty());
            when(pantryItemRepository.save(any(PantryItem.class))).thenReturn(testPantryItem);

            // When
            PantryItemResponse response = pantryItemService.createPantryItem(testCreateRequest, validToken);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo(pantryItemId);
            assertThat(response.getQuantity()).isEqualTo(testPantryItem.getQuantity());
            assertThat(response.getUnitOfMeasure()).isEqualTo(testPantryItem.getUnitOfMeasure());

            verify(userService).getCurrentUser(eq(validToken));
            verify(locationRepository).findById(eq(locationId));
            verify(productRepository).findById(eq(productId));
            verify(householdMemberRepository).existsByHouseholdIdAndUserId(eq(householdId), eq(userId));
            verify(pantryItemRepository).save(any(PantryItem.class));
        }

        @Test
        @DisplayName("Should consolidate quantity when item with same expiration date exists")
        void shouldConsolidateQuantityWhenItemWithSameExpirationDateExists() {
            // Given
            LocalDate expirationDate = LocalDate.now().plusDays(7);
            CreatePantryItemRequest request = CreatePantryItemRequest.builder()
                    .productId(productId)
                    .locationId(locationId)
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
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();

            PantryItem updatedItem = PantryItem.builder()
                    .id(existingItem.getId())
                    .product(testProduct)
                    .location(testLocation)
                    .quantity(5) // 2 + 3
                    .unitOfMeasure("liters")
                    .expirationDate(expirationDate)
                    .notes("Additional milk")
                    .createdAt(existingItem.getCreatedAt())
                    .updatedAt(LocalDateTime.now())
                    .build();

            setupValidAuthentication();
            setupHouseholdMembership();
            when(locationRepository.findById(eq(locationId))).thenReturn(Optional.of(testLocation));
            when(productRepository.findById(eq(productId))).thenReturn(Optional.of(testProduct));
            when(pantryItemRepository.findByLocationIdAndProductIdAndExpirationDate(
                    eq(locationId), eq(productId), eq(expirationDate)))
                    .thenReturn(Optional.of(existingItem));
            when(pantryItemRepository.save(any(PantryItem.class))).thenReturn(updatedItem);

            // When
            PantryItemResponse response = pantryItemService.createPantryItem(request, validToken);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getQuantity()).isEqualTo(5);

            verify(pantryItemRepository).findByLocationIdAndProductIdAndExpirationDate(
                    eq(locationId), eq(productId), eq(expirationDate));
            verify(pantryItemRepository).save(argThat(item ->
                    item.getQuantity() == 5 && item.getId().equals(existingItem.getId())));
        }

        @Test
        @DisplayName("Should create separate item when item with different expiration date exists")
        void shouldCreateSeparateItemWhenItemWithDifferentExpirationDateExists() {
            // Given
            LocalDate differentDate = LocalDate.now().plusDays(14);
            CreatePantryItemRequest request = CreatePantryItemRequest.builder()
                    .productId(productId)
                    .locationId(locationId)
                    .quantity(3)
                    .unitOfMeasure("liters")
                    .expirationDate(differentDate)
                    .build();

            setupValidAuthentication();
            setupHouseholdMembership();
            when(locationRepository.findById(eq(locationId))).thenReturn(Optional.of(testLocation));
            when(productRepository.findById(eq(productId))).thenReturn(Optional.of(testProduct));
            when(pantryItemRepository.findByLocationIdAndProductIdAndExpirationDate(
                    eq(locationId), eq(productId), eq(differentDate)))
                    .thenReturn(Optional.empty());
            when(pantryItemRepository.save(any(PantryItem.class))).thenReturn(testPantryItem);

            // When
            PantryItemResponse response = pantryItemService.createPantryItem(request, validToken);

            // Then
            assertThat(response).isNotNull();
            verify(pantryItemRepository).save(argThat(item ->
                    item.getId() != null && item.getQuantity() == 3));
        }

        @Test
        @DisplayName("Should consolidate items with null expiration dates")
        void shouldConsolidateItemsWithNullExpirationDates() {
            // Given
            CreatePantryItemRequest request = CreatePantryItemRequest.builder()
                    .productId(productId)
                    .locationId(locationId)
                    .quantity(3)
                    .unitOfMeasure("units")
                    .expirationDate(null)
                    .build();

            PantryItem existingItem = PantryItem.builder()
                    .id(UUID.randomUUID())
                    .product(testProduct)
                    .location(testLocation)
                    .quantity(2)
                    .unitOfMeasure("units")
                    .expirationDate(null)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();

            setupValidAuthentication();
            setupHouseholdMembership();
            when(locationRepository.findById(eq(locationId))).thenReturn(Optional.of(testLocation));
            when(productRepository.findById(eq(productId))).thenReturn(Optional.of(testProduct));
            when(pantryItemRepository.findByLocationIdAndProductIdAndExpirationDateIsNull(
                    eq(locationId), eq(productId)))
                    .thenReturn(Optional.of(existingItem));
            when(pantryItemRepository.save(any(PantryItem.class))).thenReturn(existingItem);

            // When
            PantryItemResponse response = pantryItemService.createPantryItem(request, validToken);

            // Then
            assertThat(response).isNotNull();
            verify(pantryItemRepository).findByLocationIdAndProductIdAndExpirationDateIsNull(
                    eq(locationId), eq(productId));
            verify(pantryItemRepository).save(argThat(item ->
                    item.getQuantity() == 5 && item.getId().equals(existingItem.getId())));
        }

        @Test
        @DisplayName("Should not consolidate null expiration item with dated expiration item")
        void shouldNotConsolidateNullExpirationItemWithDatedExpirationItem() {
            // Given
            CreatePantryItemRequest request = CreatePantryItemRequest.builder()
                    .productId(productId)
                    .locationId(locationId)
                    .quantity(3)
                    .unitOfMeasure("units")
                    .expirationDate(null)
                    .build();

            setupValidAuthentication();
            setupHouseholdMembership();
            when(locationRepository.findById(eq(locationId))).thenReturn(Optional.of(testLocation));
            when(productRepository.findById(eq(productId))).thenReturn(Optional.of(testProduct));
            when(pantryItemRepository.findByLocationIdAndProductIdAndExpirationDateIsNull(
                    eq(locationId), eq(productId)))
                    .thenReturn(Optional.empty());
            when(pantryItemRepository.save(any(PantryItem.class))).thenReturn(testPantryItem);

            // When
            PantryItemResponse response = pantryItemService.createPantryItem(request, validToken);

            // Then
            assertThat(response).isNotNull();
            verify(pantryItemRepository).save(argThat(item ->
                    item.getExpirationDate() == null && item.getQuantity() == 3));
        }

        @Test
        @DisplayName("Should throw NotFoundException when location not found")
        void shouldThrowNotFoundExceptionWhenLocationNotFound() {
            // Given
            setupValidAuthentication();
            when(locationRepository.findById(eq(locationId))).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> pantryItemService.createPantryItem(testCreateRequest, validToken))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("Location not found");

            verify(userService).getCurrentUser(eq(validToken));
            verify(locationRepository).findById(eq(locationId));
            verify(pantryItemRepository, never()).save(any(PantryItem.class));
        }

        @Test
        @DisplayName("Should throw NotFoundException when product not found")
        void shouldThrowNotFoundExceptionWhenProductNotFound() {
            // Given
            setupValidAuthentication();
            when(locationRepository.findById(eq(locationId))).thenReturn(Optional.of(testLocation));
            when(productRepository.findById(eq(productId))).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> pantryItemService.createPantryItem(testCreateRequest, validToken))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("Product not found");

            verify(userService).getCurrentUser(eq(validToken));
            verify(productRepository).findById(eq(productId));
            verify(pantryItemRepository, never()).save(any(PantryItem.class));
        }

        @Test
        @DisplayName("Should throw InsufficientPermissionException when user not member of household")
        void shouldThrowInsufficientPermissionExceptionWhenUserNotMemberOfHousehold() {
            // Given
            setupValidAuthentication();
            setupNoHouseholdMembership();
            when(locationRepository.findById(eq(locationId))).thenReturn(Optional.of(testLocation));
            when(productRepository.findById(eq(productId))).thenReturn(Optional.of(testProduct));

            // When & Then
            assertThatThrownBy(() -> pantryItemService.createPantryItem(testCreateRequest, validToken))
                    .isInstanceOf(InsufficientPermissionException.class)
                    .hasMessageContaining("not a member of this household");

            verify(householdMemberRepository).existsByHouseholdIdAndUserId(eq(householdId), eq(userId));
            verify(pantryItemRepository, never()).save(any(PantryItem.class));
        }

        @Test
        @DisplayName("Should throw ValidationException when product ID is null")
        void shouldThrowValidationExceptionWhenProductIdIsNull() {
            // Given
            CreatePantryItemRequest invalidRequest = CreatePantryItemRequest.builder()
                    .productId(null)
                    .locationId(locationId)
                    .quantity(2)
                    .build();

            // When & Then
            assertThatThrownBy(() -> pantryItemService.createPantryItem(invalidRequest, validToken))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Product ID");

            verify(userService, never()).getCurrentUser(any(JwtAuthenticationToken.class));
            verify(pantryItemRepository, never()).save(any(PantryItem.class));
        }

        @Test
        @DisplayName("Should throw ValidationException when location ID is null")
        void shouldThrowValidationExceptionWhenLocationIdIsNull() {
            // Given
            CreatePantryItemRequest invalidRequest = CreatePantryItemRequest.builder()
                    .productId(productId)
                    .locationId(null)
                    .quantity(2)
                    .build();

            // When & Then
            assertThatThrownBy(() -> pantryItemService.createPantryItem(invalidRequest, validToken))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Location ID");

            verify(userService, never()).getCurrentUser(any(JwtAuthenticationToken.class));
            verify(pantryItemRepository, never()).save(any(PantryItem.class));
        }

        @Test
        @DisplayName("Should throw ValidationException when quantity is negative")
        void shouldThrowValidationExceptionWhenQuantityIsNegative() {
            // Given
            CreatePantryItemRequest invalidRequest = CreatePantryItemRequest.builder()
                    .productId(productId)
                    .locationId(locationId)
                    .quantity(-1)
                    .build();

            // When & Then
            assertThatThrownBy(() -> pantryItemService.createPantryItem(invalidRequest, validToken))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Quantity");

            verify(userService, never()).getCurrentUser(any(JwtAuthenticationToken.class));
            verify(pantryItemRepository, never()).save(any(PantryItem.class));
        }

        @Test
        @DisplayName("Should throw DataIntegrityException when save fails")
        void shouldThrowDataIntegrityExceptionWhenSaveFails() {
            // Given
            setupValidAuthentication();
            setupHouseholdMembership();
            when(locationRepository.findById(eq(locationId))).thenReturn(Optional.of(testLocation));
            when(productRepository.findById(eq(productId))).thenReturn(Optional.of(testProduct));
            when(pantryItemRepository.findByLocationIdAndProductIdAndExpirationDate(
                    any(), any(), any())).thenReturn(Optional.empty());
            when(pantryItemRepository.save(any(PantryItem.class)))
                    .thenThrow(new RuntimeException("Database error"));

            // When & Then
            assertThatThrownBy(() -> pantryItemService.createPantryItem(testCreateRequest, validToken))
                    .isInstanceOf(DataIntegrityException.class)
                    .hasMessageContaining("Failed to save pantry item");

            verify(pantryItemRepository).save(any(PantryItem.class));
        }
    }

    @Nested
    @DisplayName("Get Pantry Item Tests")
    class GetPantryItemTests {

        @Test
        @DisplayName("Should get pantry item by ID successfully")
        void shouldGetPantryItemByIdSuccessfully() {
            // Given
            setupValidAuthentication();
            setupHouseholdMembership();
            when(pantryItemRepository.findById(eq(pantryItemId))).thenReturn(Optional.of(testPantryItem));

            // When
            PantryItemResponse response = pantryItemService.getPantryItemById(pantryItemId, validToken);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo(pantryItemId);
            assertThat(response.getQuantity()).isEqualTo(testPantryItem.getQuantity());

            verify(userService).getCurrentUser(eq(validToken));
            verify(pantryItemRepository).findById(eq(pantryItemId));
            verify(householdMemberRepository).existsByHouseholdIdAndUserId(eq(householdId), eq(userId));
        }

        @Test
        @DisplayName("Should throw NotFoundException when pantry item not found")
        void shouldThrowNotFoundExceptionWhenPantryItemNotFound() {
            // Given
            setupValidAuthentication();
            when(pantryItemRepository.findById(eq(pantryItemId))).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> pantryItemService.getPantryItemById(pantryItemId, validToken))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("Pantry item not found");

            verify(userService).getCurrentUser(eq(validToken));
            verify(pantryItemRepository).findById(eq(pantryItemId));
        }

        @Test
        @DisplayName("Should throw InsufficientPermissionException when user not member of household")
        void shouldThrowInsufficientPermissionExceptionWhenUserNotMemberOfHousehold() {
            // Given
            setupValidAuthentication();
            setupNoHouseholdMembership();
            when(pantryItemRepository.findById(eq(pantryItemId))).thenReturn(Optional.of(testPantryItem));

            // When & Then
            assertThatThrownBy(() -> pantryItemService.getPantryItemById(pantryItemId, validToken))
                    .isInstanceOf(InsufficientPermissionException.class)
                    .hasMessageContaining("not a member of this household");

            verify(householdMemberRepository).existsByHouseholdIdAndUserId(eq(householdId), eq(userId));
        }
    }

    @Nested
    @DisplayName("Update Pantry Item Tests")
    class UpdatePantryItemTests {

        @Test
        @DisplayName("Should update pantry item successfully")
        void shouldUpdatePantryItemSuccessfully() {
            // Given
            setupValidAuthentication();
            setupHouseholdMembership();
            when(pantryItemRepository.findById(eq(pantryItemId))).thenReturn(Optional.of(testPantryItem));
            when(pantryItemRepository.save(any(PantryItem.class))).thenReturn(testPantryItem);

            // When
            PantryItemResponse response = pantryItemService.updatePantryItem(
                    pantryItemId, testUpdateRequest, validToken);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo(pantryItemId);

            verify(userService).getCurrentUser(eq(validToken));
            verify(pantryItemRepository).findById(eq(pantryItemId));
            verify(pantryItemRepository).save(any(PantryItem.class));
        }

        @Test
        @DisplayName("Should update location when location ID provided")
        void shouldUpdateLocationWhenLocationIdProvided() {
            // Given
            Location newLocation = Location.builder()
                    .id(UUID.randomUUID())
                    .name("Fridge")
                    .household(testHousehold)
                    .build();

            UpdatePantryItemRequest requestWithLocation = UpdatePantryItemRequest.builder()
                    .locationId(newLocation.getId())
                    .quantity(5)
                    .build();

            setupValidAuthentication();
            when(pantryItemRepository.findById(eq(pantryItemId))).thenReturn(Optional.of(testPantryItem));
            when(locationRepository.findById(eq(newLocation.getId()))).thenReturn(Optional.of(newLocation));
            when(householdMemberRepository.existsByHouseholdIdAndUserId(
                    eq(testHousehold.getId()), eq(userId))).thenReturn(true);
            when(pantryItemRepository.save(any(PantryItem.class))).thenReturn(testPantryItem);

            // When
            PantryItemResponse response = pantryItemService.updatePantryItem(
                    pantryItemId, requestWithLocation, validToken);

            // Then
            assertThat(response).isNotNull();
            verify(locationRepository).findById(eq(newLocation.getId()));
            verify(pantryItemRepository).save(any(PantryItem.class));
        }

        @Test
        @DisplayName("Should throw NotFoundException when pantry item not found")
        void shouldThrowNotFoundExceptionWhenPantryItemNotFound() {
            // Given
            setupValidAuthentication();
            when(pantryItemRepository.findById(eq(pantryItemId))).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> pantryItemService.updatePantryItem(
                    pantryItemId, testUpdateRequest, validToken))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("Pantry item not found");

            verify(pantryItemRepository, never()).save(any(PantryItem.class));
        }

        @Test
        @DisplayName("Should throw NotFoundException when new location not found")
        void shouldThrowNotFoundExceptionWhenNewLocationNotFound() {
            // Given
            UUID newLocationId = UUID.randomUUID();
            UpdatePantryItemRequest requestWithLocation = UpdatePantryItemRequest.builder()
                    .locationId(newLocationId)
                    .quantity(5)
                    .build();

            setupValidAuthentication();
            setupHouseholdMembership();
            when(pantryItemRepository.findById(eq(pantryItemId))).thenReturn(Optional.of(testPantryItem));
            when(locationRepository.findById(eq(newLocationId))).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> pantryItemService.updatePantryItem(
                    pantryItemId, requestWithLocation, validToken))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("Location not found");

            verify(pantryItemRepository, never()).save(any(PantryItem.class));
        }

        @Test
        @DisplayName("Should throw InsufficientPermissionException when user not member of household")
        void shouldThrowInsufficientPermissionExceptionWhenUserNotMemberOfHousehold() {
            // Given
            setupValidAuthentication();
            setupNoHouseholdMembership();
            when(pantryItemRepository.findById(eq(pantryItemId))).thenReturn(Optional.of(testPantryItem));

            // When & Then
            assertThatThrownBy(() -> pantryItemService.updatePantryItem(
                    pantryItemId, testUpdateRequest, validToken))
                    .isInstanceOf(InsufficientPermissionException.class)
                    .hasMessageContaining("not a member of this household");

            verify(pantryItemRepository, never()).save(any(PantryItem.class));
        }

        @Test
        @DisplayName("Should throw ValidationException when quantity is negative")
        void shouldThrowValidationExceptionWhenQuantityIsNegative() {
            // Given
            UpdatePantryItemRequest invalidRequest = UpdatePantryItemRequest.builder()
                    .quantity(-5)
                    .build();

            // When & Then
            assertThatThrownBy(() -> pantryItemService.updatePantryItem(
                    pantryItemId, invalidRequest, validToken))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Quantity");

            verify(userService, never()).getCurrentUser(any(JwtAuthenticationToken.class));
            verify(pantryItemRepository, never()).save(any(PantryItem.class));
        }

        @Test
        @DisplayName("Should throw ValidationException when request is null")
        void shouldThrowValidationExceptionWhenRequestIsNull() {
            assertThatThrownBy(() -> pantryItemService.updatePantryItem(pantryItemId, null, validToken))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("request cannot be null");

            verify(userService, never()).getCurrentUser(any(JwtAuthenticationToken.class));
            verify(pantryItemRepository, never()).save(any(PantryItem.class));
        }
    }

    @Nested
    @DisplayName("Delete Pantry Item Tests")
    class DeletePantryItemTests {

        @Test
        @DisplayName("Should delete pantry item successfully")
        void shouldDeletePantryItemSuccessfully() {
            // Given
            setupValidAuthentication();
            setupHouseholdMembership();
            when(pantryItemRepository.findById(eq(pantryItemId))).thenReturn(Optional.of(testPantryItem));

            // When
            pantryItemService.deletePantryItem(pantryItemId, validToken);

            // Then
            verify(userService).getCurrentUser(eq(validToken));
            verify(pantryItemRepository).findById(eq(pantryItemId));
            verify(householdMemberRepository).existsByHouseholdIdAndUserId(eq(householdId), eq(userId));
            verify(pantryItemRepository).delete(eq(testPantryItem));
        }

        @Test
        @DisplayName("Should throw NotFoundException when pantry item not found")
        void shouldThrowNotFoundExceptionWhenPantryItemNotFound() {
            // Given
            setupValidAuthentication();
            when(pantryItemRepository.findById(eq(pantryItemId))).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> pantryItemService.deletePantryItem(pantryItemId, validToken))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("Pantry item not found");

            verify(pantryItemRepository, never()).delete(any(PantryItem.class));
        }

        @Test
        @DisplayName("Should throw InsufficientPermissionException when user not member of household")
        void shouldThrowInsufficientPermissionExceptionWhenUserNotMemberOfHousehold() {
            // Given
            setupValidAuthentication();
            setupNoHouseholdMembership();
            when(pantryItemRepository.findById(eq(pantryItemId))).thenReturn(Optional.of(testPantryItem));

            // When & Then
            assertThatThrownBy(() -> pantryItemService.deletePantryItem(pantryItemId, validToken))
                    .isInstanceOf(InsufficientPermissionException.class)
                    .hasMessageContaining("not a member of this household");

            verify(pantryItemRepository, never()).delete(any(PantryItem.class));
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
            List<PantryItem> items = List.of(testPantryItem);
            Page<PantryItem> page = new PageImpl<>(items, pageable, items.size());

            setupValidAuthentication();
            setupHouseholdMembership();
            when(pantryItemRepository.findByLocation_HouseholdId(eq(householdId), eq(pageable)))
                    .thenReturn(page);

            // When
            Page<PantryItemResponse> response = pantryItemService.getPantryItemsByHousehold(
                    householdId, pageable, validToken);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getContent()).hasSize(1);
            assertThat(response.getTotalElements()).isEqualTo(1);

            verify(userService).getCurrentUser(eq(validToken));
            verify(householdMemberRepository).existsByHouseholdIdAndUserId(eq(householdId), eq(userId));
            verify(pantryItemRepository).findByLocation_HouseholdId(eq(householdId), eq(pageable));
        }

        @Test
        @DisplayName("Should throw ValidationException when household ID is null")
        void shouldThrowValidationExceptionWhenHouseholdIdIsNull() {
            Pageable pageable = PageRequest.of(0, 10);

            assertThatThrownBy(() -> pantryItemService.getPantryItemsByHousehold(null, pageable, validToken))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Household ID cannot be null");

            verify(userService, never()).getCurrentUser(any(JwtAuthenticationToken.class));
        }

        @Test
        @DisplayName("Should throw InsufficientPermissionException when user not member of household")
        void shouldThrowInsufficientPermissionExceptionWhenUserNotMemberOfHousehold() {
            // Given
            Pageable pageable = PageRequest.of(0, 10);
            setupValidAuthentication();
            setupNoHouseholdMembership();

            // When & Then
            assertThatThrownBy(() -> pantryItemService.getPantryItemsByHousehold(
                    householdId, pageable, validToken))
                    .isInstanceOf(InsufficientPermissionException.class)
                    .hasMessageContaining("not a member of this household");

            verify(pantryItemRepository, never()).findByLocation_HouseholdId(any(), any());
        }
    }

    @Nested
    @DisplayName("Get Pantry Items by Location Tests")
    class GetPantryItemsByLocationTests {

        @Test
        @DisplayName("Should get pantry items by location")
        void shouldGetPantryItemsByLocation() {
            // Given
            List<PantryItem> items = List.of(testPantryItem);
            setupValidAuthentication();
            setupHouseholdMembership();
            when(locationRepository.findById(eq(locationId))).thenReturn(Optional.of(testLocation));
            when(pantryItemRepository.findByLocationId(eq(locationId))).thenReturn(items);

            // When
            List<PantryItemResponse> response = pantryItemService.getPantryItemsByLocation(
                    locationId, validToken);

            // Then
            assertThat(response).isNotNull();
            assertThat(response).hasSize(1);

            verify(userService).getCurrentUser(eq(validToken));
            verify(locationRepository).findById(eq(locationId));
            verify(householdMemberRepository).existsByHouseholdIdAndUserId(eq(householdId), eq(userId));
            verify(pantryItemRepository).findByLocationId(eq(locationId));
        }

        @Test
        @DisplayName("Should throw NotFoundException when location not found")
        void shouldThrowNotFoundExceptionWhenLocationNotFound() {
            // Given
            setupValidAuthentication();
            when(locationRepository.findById(eq(locationId))).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> pantryItemService.getPantryItemsByLocation(locationId, validToken))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("Location not found");

            verify(pantryItemRepository, never()).findByLocationId(any());
        }

        @Test
        @DisplayName("Should throw InsufficientPermissionException when user not member of household")
        void shouldThrowInsufficientPermissionExceptionWhenUserNotMemberOfHousehold() {
            // Given
            setupValidAuthentication();
            setupNoHouseholdMembership();
            when(locationRepository.findById(eq(locationId))).thenReturn(Optional.of(testLocation));

            // When & Then
            assertThatThrownBy(() -> pantryItemService.getPantryItemsByLocation(locationId, validToken))
                    .isInstanceOf(InsufficientPermissionException.class)
                    .hasMessageContaining("not a member of this household");

            verify(pantryItemRepository, never()).findByLocationId(any());
        }
    }

    @Nested
    @DisplayName("Patch Pantry Item Tests")
    class PatchPantryItemTests {

        @Test
        @DisplayName("Should patch single field successfully")
        void shouldPatchSingleFieldSuccessfully() {
            // Given
            Map<String, Object> patchFields = new HashMap<>();
            patchFields.put("quantity", 10);

            setupValidAuthentication();
            setupHouseholdMembership();
            when(pantryItemRepository.findById(eq(pantryItemId))).thenReturn(Optional.of(testPantryItem));
            when(pantryItemRepository.save(any(PantryItem.class))).thenReturn(testPantryItem);

            // When
            PantryItemResponse response = pantryItemService.patchPantryItem(
                    pantryItemId, patchFields, validToken);

            // Then
            assertThat(response).isNotNull();
            verify(pantryItemRepository).save(argThat(item -> item.getQuantity() == 10));
        }

        @Test
        @DisplayName("Should patch multiple fields successfully")
        void shouldPatchMultipleFieldsSuccessfully() {
            // Given
            Map<String, Object> patchFields = new HashMap<>();
            patchFields.put("quantity", 8);
            patchFields.put("unitOfMeasure", "kg");
            patchFields.put("notes", "Patched notes");

            setupValidAuthentication();
            setupHouseholdMembership();
            when(pantryItemRepository.findById(eq(pantryItemId))).thenReturn(Optional.of(testPantryItem));
            when(pantryItemRepository.save(any(PantryItem.class))).thenReturn(testPantryItem);

            // When
            PantryItemResponse response = pantryItemService.patchPantryItem(
                    pantryItemId, patchFields, validToken);

            // Then
            assertThat(response).isNotNull();
            verify(pantryItemRepository).save(argThat(item ->
                    item.getQuantity() == 8 &&
                            "kg".equals(item.getUnitOfMeasure()) &&
                            "Patched notes".equals(item.getNotes())));
        }

        @Test
        @DisplayName("Should patch location successfully")
        void shouldPatchLocationSuccessfully() {
            // Given
            Location newLocation = Location.builder()
                    .id(UUID.randomUUID())
                    .name("Freezer")
                    .household(testHousehold)
                    .build();

            Map<String, Object> patchFields = new HashMap<>();
            patchFields.put("locationId", newLocation.getId().toString());

            setupValidAuthentication();
            when(pantryItemRepository.findById(eq(pantryItemId))).thenReturn(Optional.of(testPantryItem));
            when(locationRepository.findById(eq(newLocation.getId()))).thenReturn(Optional.of(newLocation));
            when(householdMemberRepository.existsByHouseholdIdAndUserId(
                    eq(testHousehold.getId()), eq(userId))).thenReturn(true);
            when(pantryItemRepository.save(any(PantryItem.class))).thenReturn(testPantryItem);

            // When
            PantryItemResponse response = pantryItemService.patchPantryItem(
                    pantryItemId, patchFields, validToken);

            // Then
            assertThat(response).isNotNull();
            verify(locationRepository).findById(eq(newLocation.getId()));
            verify(pantryItemRepository).save(any(PantryItem.class));
        }

        @Test
        @DisplayName("Should set expiration date to null when null value provided")
        void shouldSetExpirationDateToNullWhenNullValueProvided() {
            // Given
            Map<String, Object> patchFields = new HashMap<>();
            patchFields.put("expirationDate", null);

            setupValidAuthentication();
            setupHouseholdMembership();
            when(pantryItemRepository.findById(eq(pantryItemId))).thenReturn(Optional.of(testPantryItem));
            when(pantryItemRepository.save(any(PantryItem.class))).thenReturn(testPantryItem);

            // When
            PantryItemResponse response = pantryItemService.patchPantryItem(
                    pantryItemId, patchFields, validToken);

            // Then
            assertThat(response).isNotNull();
            verify(pantryItemRepository).save(argThat(item -> item.getExpirationDate() == null));
        }

        @Test
        @DisplayName("Should set unit of measure and notes to null when null values provided")
        void shouldSetFieldsToNullWhenNullValuesProvided() {
            // Given
            Map<String, Object> patchFields = new HashMap<>();
            patchFields.put("unitOfMeasure", null);
            patchFields.put("notes", null);

            setupValidAuthentication();
            setupHouseholdMembership();
            when(pantryItemRepository.findById(eq(pantryItemId))).thenReturn(Optional.of(testPantryItem));
            when(pantryItemRepository.save(any(PantryItem.class))).thenReturn(testPantryItem);

            // When
            PantryItemResponse response = pantryItemService.patchPantryItem(
                    pantryItemId, patchFields, validToken);

            // Then
            assertThat(response).isNotNull();
            verify(pantryItemRepository).save(argThat(item ->
                    item.getUnitOfMeasure() == null && item.getNotes() == null));
        }

        @Test
        @DisplayName("Should ignore unknown fields")
        void shouldIgnoreUnknownFields() {
            // Given
            Map<String, Object> patchFields = new HashMap<>();
            patchFields.put("quantity", 5);
            patchFields.put("unknownField", "should be ignored");

            setupValidAuthentication();
            setupHouseholdMembership();
            when(pantryItemRepository.findById(eq(pantryItemId))).thenReturn(Optional.of(testPantryItem));
            when(pantryItemRepository.save(any(PantryItem.class))).thenReturn(testPantryItem);

            // When
            PantryItemResponse response = pantryItemService.patchPantryItem(
                    pantryItemId, patchFields, validToken);

            // Then
            assertThat(response).isNotNull();
            verify(pantryItemRepository).save(any(PantryItem.class));
        }

        @Test
        @DisplayName("Should throw ValidationException when patch fields is null")
        void shouldThrowValidationExceptionWhenPatchFieldsIsNull() {
            assertThatThrownBy(() -> pantryItemService.patchPantryItem(pantryItemId, null, validToken))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Patch fields cannot be null");

            verify(userService, never()).getCurrentUser(any(JwtAuthenticationToken.class));
            verify(pantryItemRepository, never()).save(any(PantryItem.class));
        }

        @Test
        @DisplayName("Should throw ValidationException when patch fields is empty")
        void shouldThrowValidationExceptionWhenPatchFieldsIsEmpty() {
            Map<String, Object> emptyPatchFields = new HashMap<>();

            assertThatThrownBy(() -> pantryItemService.patchPantryItem(
                    pantryItemId, emptyPatchFields, validToken))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Patch fields cannot be empty");

            verify(userService, never()).getCurrentUser(any(JwtAuthenticationToken.class));
            verify(pantryItemRepository, never()).save(any(PantryItem.class));
        }

        @Test
        @DisplayName("Should throw ValidationException when quantity is negative in patch")
        void shouldThrowValidationExceptionWhenQuantityIsNegativeInPatch() {
            // Given
            Map<String, Object> patchFields = new HashMap<>();
            patchFields.put("quantity", -5);

            setupValidAuthentication();
            setupHouseholdMembership();
            when(pantryItemRepository.findById(eq(pantryItemId))).thenReturn(Optional.of(testPantryItem));

            // When & Then
            assertThatThrownBy(() -> pantryItemService.patchPantryItem(
                    pantryItemId, patchFields, validToken))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Quantity");

            verify(pantryItemRepository, never()).save(any(PantryItem.class));
        }

        @Test
        @DisplayName("Should throw NotFoundException when pantry item not found for patch")
        void shouldThrowNotFoundExceptionWhenPantryItemNotFoundForPatch() {
            // Given
            Map<String, Object> patchFields = new HashMap<>();
            patchFields.put("quantity", 5);

            setupValidAuthentication();
            when(pantryItemRepository.findById(eq(pantryItemId))).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> pantryItemService.patchPantryItem(
                    pantryItemId, patchFields, validToken))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("Pantry item not found");

            verify(pantryItemRepository, never()).save(any(PantryItem.class));
        }

        @Test
        @DisplayName("Should throw InsufficientPermissionException when user not member of household for patch")
        void shouldThrowInsufficientPermissionExceptionWhenUserNotMemberOfHouseholdForPatch() {
            // Given
            Map<String, Object> patchFields = new HashMap<>();
            patchFields.put("quantity", 5);

            setupValidAuthentication();
            setupNoHouseholdMembership();
            when(pantryItemRepository.findById(eq(pantryItemId))).thenReturn(Optional.of(testPantryItem));

            // When & Then
            assertThatThrownBy(() -> pantryItemService.patchPantryItem(
                    pantryItemId, patchFields, validToken))
                    .isInstanceOf(InsufficientPermissionException.class)
                    .hasMessageContaining("not a member of this household");

            verify(pantryItemRepository, never()).save(any(PantryItem.class));
        }
    }

    @Nested
    @DisplayName("Batch Operations Tests")
    class BatchOperationsTests {

        @Test
        @DisplayName("Should create multiple pantry items successfully")
        void shouldCreateMultiplePantryItemsSuccessfully() {
            // Given
            CreatePantryItemRequest request1 = CreatePantryItemRequest.builder()
                    .productId(productId)
                    .locationId(locationId)
                    .quantity(2)
                    .build();

            CreatePantryItemRequest request2 = CreatePantryItemRequest.builder()
                    .productId(productId)
                    .locationId(locationId)
                    .quantity(3)
                    .expirationDate(LocalDate.now().plusDays(10))
                    .build();

            List<CreatePantryItemRequest> requests = List.of(request1, request2);

            setupValidAuthentication();
            setupHouseholdMembership();
            when(locationRepository.findById(eq(locationId))).thenReturn(Optional.of(testLocation));
            when(productRepository.findById(eq(productId))).thenReturn(Optional.of(testProduct));
            when(pantryItemRepository.findByLocationIdAndProductIdAndExpirationDateIsNull(any(), any()))
                    .thenReturn(Optional.empty());
            when(pantryItemRepository.findByLocationIdAndProductIdAndExpirationDate(any(), any(), any()))
                    .thenReturn(Optional.empty());
            when(pantryItemRepository.save(any(PantryItem.class))).thenReturn(testPantryItem);

            // When
            List<PantryItemResponse> responses = pantryItemService.createMultiplePantryItems(
                    requests, validToken);

            // Then
            assertThat(responses).hasSize(2);
            verify(pantryItemRepository, times(2)).save(any(PantryItem.class));
        }

        @Test
        @DisplayName("Should throw ValidationException when batch requests list is null")
        void shouldThrowValidationExceptionWhenBatchRequestsIsNull() {
            assertThatThrownBy(() -> pantryItemService.createMultiplePantryItems(null, validToken))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Requests list cannot be null");

            verify(userService, never()).getCurrentUser(any(JwtAuthenticationToken.class));
            verify(pantryItemRepository, never()).save(any(PantryItem.class));
        }

        @Test
        @DisplayName("Should throw ValidationException when batch requests list is empty")
        void shouldThrowValidationExceptionWhenBatchRequestsIsEmpty() {
            assertThatThrownBy(() -> pantryItemService.createMultiplePantryItems(
                    Collections.emptyList(), validToken))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Requests list cannot be empty");

            verify(userService, never()).getCurrentUser(any(JwtAuthenticationToken.class));
            verify(pantryItemRepository, never()).save(any(PantryItem.class));
        }

        @Test
        @DisplayName("Should delete multiple pantry items successfully")
        void shouldDeleteMultiplePantryItemsSuccessfully() {
            // Given
            UUID itemId1 = UUID.randomUUID();
            UUID itemId2 = UUID.randomUUID();
            List<UUID> itemIds = List.of(itemId1, itemId2);

            PantryItem item1 = PantryItem.builder()
                    .id(itemId1)
                    .product(testProduct)
                    .location(testLocation)
                    .quantity(1)
                    .build();

            PantryItem item2 = PantryItem.builder()
                    .id(itemId2)
                    .product(testProduct)
                    .location(testLocation)
                    .quantity(2)
                    .build();

            setupValidAuthentication();
            setupHouseholdMembership();
            when(pantryItemRepository.findById(eq(itemId1))).thenReturn(Optional.of(item1));
            when(pantryItemRepository.findById(eq(itemId2))).thenReturn(Optional.of(item2));

            // When
            int deletedCount = pantryItemService.deleteMultiplePantryItems(itemIds, validToken);

            // Then
            assertThat(deletedCount).isEqualTo(2);
            verify(pantryItemRepository).delete(eq(item1));
            verify(pantryItemRepository).delete(eq(item2));
        }

        @Test
        @DisplayName("Should return partial success count when some items not found in batch delete")
        void shouldReturnPartialSuccessCountWhenSomeItemsNotFoundInBatchDelete() {
            // Given
            UUID itemId1 = UUID.randomUUID();
            UUID itemId2 = UUID.randomUUID();
            List<UUID> itemIds = List.of(itemId1, itemId2);

            PantryItem item1 = PantryItem.builder()
                    .id(itemId1)
                    .product(testProduct)
                    .location(testLocation)
                    .quantity(1)
                    .build();

            setupValidAuthentication();
            setupHouseholdMembership();
            when(pantryItemRepository.findById(eq(itemId1))).thenReturn(Optional.of(item1));
            when(pantryItemRepository.findById(eq(itemId2))).thenReturn(Optional.empty());

            // When
            int deletedCount = pantryItemService.deleteMultiplePantryItems(itemIds, validToken);

            // Then
            assertThat(deletedCount).isEqualTo(1);
            verify(pantryItemRepository).delete(eq(item1));
            verify(pantryItemRepository, times(1)).delete(any(PantryItem.class));
        }

        @Test
        @DisplayName("Should throw ValidationException when item IDs list is null for batch delete")
        void shouldThrowValidationExceptionWhenItemIdsIsNullForBatchDelete() {
            assertThatThrownBy(() -> pantryItemService.deleteMultiplePantryItems(null, validToken))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Item IDs list cannot be null");

            verify(userService, never()).getCurrentUser(any(JwtAuthenticationToken.class));
            verify(pantryItemRepository, never()).delete(any(PantryItem.class));
        }

        @Test
        @DisplayName("Should throw ValidationException when item IDs list is empty for batch delete")
        void shouldThrowValidationExceptionWhenItemIdsIsEmptyForBatchDelete() {
            assertThatThrownBy(() -> pantryItemService.deleteMultiplePantryItems(
                    Collections.emptyList(), validToken))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Item IDs list cannot be empty");

            verify(userService, never()).getCurrentUser(any(JwtAuthenticationToken.class));
            verify(pantryItemRepository, never()).delete(any(PantryItem.class));
        }
    }

    @Nested
    @DisplayName("Search and Filter Tests")
    class SearchAndFilterTests {

        @Test
        @DisplayName("Should search pantry items successfully")
        void shouldSearchPantryItemsSuccessfully() {
            // Given
            String searchTerm = "milk";
            List<PantryItem> items = List.of(testPantryItem);

            setupValidAuthentication();
            setupHouseholdMembership();
            when(pantryItemRepository.searchItemsInHousehold(eq(householdId), eq(searchTerm)))
                    .thenReturn(items);

            // When
            List<PantryItemResponse> responses = pantryItemService.searchPantryItems(
                    householdId, searchTerm, validToken);

            // Then
            assertThat(responses).hasSize(1);
            verify(pantryItemRepository).searchItemsInHousehold(eq(householdId), eq(searchTerm));
        }

        @Test
        @DisplayName("Should throw ValidationException when search term is null")
        void shouldThrowValidationExceptionWhenSearchTermIsNull() {
            assertThatThrownBy(() -> pantryItemService.searchPantryItems(householdId, null, validToken))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Search term cannot be null");

            verify(userService, never()).getCurrentUser(any(JwtAuthenticationToken.class));
        }

        @Test
        @DisplayName("Should throw ValidationException when search term is empty")
        void shouldThrowValidationExceptionWhenSearchTermIsEmpty() {
            assertThatThrownBy(() -> pantryItemService.searchPantryItems(householdId, "   ", validToken))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Search term cannot be empty");

            verify(userService, never()).getCurrentUser(any(JwtAuthenticationToken.class));
        }

        @Test
        @DisplayName("Should get low stock items successfully")
        void shouldGetLowStockItemsSuccessfully() {
            // Given
            Integer threshold = 5;
            List<PantryItem> items = List.of(testPantryItem);

            setupValidAuthentication();
            setupHouseholdMembership();
            when(pantryItemRepository.findLowStockItemsInHousehold(eq(householdId), eq(threshold)))
                    .thenReturn(items);

            // When
            List<PantryItemResponse> responses = pantryItemService.getLowStockItems(
                    householdId, threshold, validToken);

            // Then
            assertThat(responses).hasSize(1);
            verify(pantryItemRepository).findLowStockItemsInHousehold(eq(householdId), eq(threshold));
        }

        @Test
        @DisplayName("Should throw ValidationException when threshold is null")
        void shouldThrowValidationExceptionWhenThresholdIsNull() {
            assertThatThrownBy(() -> pantryItemService.getLowStockItems(householdId, null, validToken))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Threshold cannot be null");

            verify(userService, never()).getCurrentUser(any(JwtAuthenticationToken.class));
        }

        @Test
        @DisplayName("Should throw ValidationException when threshold is negative")
        void shouldThrowValidationExceptionWhenThresholdIsNegative() {
            assertThatThrownBy(() -> pantryItemService.getLowStockItems(householdId, -1, validToken))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Threshold must be non-negative");

            verify(userService, never()).getCurrentUser(any(JwtAuthenticationToken.class));
        }

        @Test
        @DisplayName("Should get expiring items successfully")
        void shouldGetExpiringItemsSuccessfully() {
            // Given
            LocalDate startDate = LocalDate.now();
            LocalDate endDate = LocalDate.now().plusDays(7);
            List<PantryItem> items = List.of(testPantryItem);

            setupValidAuthentication();
            setupHouseholdMembership();
            when(pantryItemRepository.findExpiringItemsInHouseholdBetweenDates(
                    eq(householdId), eq(startDate), eq(endDate))).thenReturn(items);

            // When
            List<PantryItemResponse> responses = pantryItemService.getExpiringItems(
                    householdId, startDate, endDate, validToken);

            // Then
            assertThat(responses).hasSize(1);
            verify(pantryItemRepository).findExpiringItemsInHouseholdBetweenDates(
                    eq(householdId), eq(startDate), eq(endDate));
        }

        @Test
        @DisplayName("Should throw ValidationException when start date is after end date")
        void shouldThrowValidationExceptionWhenStartDateIsAfterEndDate() {
            // Given
            LocalDate startDate = LocalDate.now().plusDays(10);
            LocalDate endDate = LocalDate.now();

            // When & Then
            assertThatThrownBy(() -> pantryItemService.getExpiringItems(
                    householdId, startDate, endDate, validToken))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Start date must be before or equal to end date");

            verify(pantryItemRepository, never()).findExpiringItemsInHouseholdBetweenDates(
                    any(), any(), any());
        }
    }

    @Nested
    @DisplayName("Pantry Statistics Tests")
    class PantryStatisticsTests {

        @Test
        @DisplayName("Should get pantry statistics successfully")
        void shouldGetPantryStatisticsSuccessfully() {
            // Given
            setupValidAuthentication();
            setupHouseholdMembership();
            when(pantryItemRepository.countByLocation_HouseholdId(eq(householdId))).thenReturn(10L);
            when(pantryItemRepository.countDistinctProductsByHouseholdId(eq(householdId))).thenReturn(5L);
            when(pantryItemRepository.countExpiringItemsByHouseholdIdAndDate(
                    eq(householdId), any(LocalDate.class))).thenReturn(3L);
            when(pantryItemRepository.findLowStockItemsInHousehold(eq(householdId), eq(5)))
                    .thenReturn(List.of(testPantryItem));

            // When
            Map<String, Object> statistics = pantryItemService.getPantryStatistics(householdId, validToken);

            // Then
            assertThat(statistics).isNotNull();
            assertThat(statistics).containsEntry("totalItems", 10L);
            assertThat(statistics).containsEntry("uniqueProducts", 5L);
            assertThat(statistics).containsEntry("expiringInWeek", 3L);
            assertThat(statistics).containsEntry("lowStockCount", 1L);

            verify(userService).getCurrentUser(eq(validToken));
            verify(householdMemberRepository).existsByHouseholdIdAndUserId(eq(householdId), eq(userId));
            verify(pantryItemRepository).countByLocation_HouseholdId(eq(householdId));
            verify(pantryItemRepository).countDistinctProductsByHouseholdId(eq(householdId));
            verify(pantryItemRepository).countExpiringItemsByHouseholdIdAndDate(eq(householdId), any(LocalDate.class));
            verify(pantryItemRepository).findLowStockItemsInHousehold(eq(householdId), eq(5));
        }

        @Test
        @DisplayName("Should throw ValidationException when household ID is null for statistics")
        void shouldThrowValidationExceptionWhenHouseholdIdIsNullForStatistics() {
            assertThatThrownBy(() -> pantryItemService.getPantryStatistics(null, validToken))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Household ID cannot be null");

            verify(userService, never()).getCurrentUser(any(JwtAuthenticationToken.class));
        }

        @Test
        @DisplayName("Should throw ValidationException when token is null for statistics")
        void shouldThrowValidationExceptionWhenTokenIsNullForStatistics() {
            assertThatThrownBy(() -> pantryItemService.getPantryStatistics(householdId, null))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("token cannot be null");

            verify(userService, never()).getCurrentUser(any(JwtAuthenticationToken.class));
        }

        @Test
        @DisplayName("Should throw InsufficientPermissionException when user not member of household for statistics")
        void shouldThrowInsufficientPermissionExceptionWhenUserNotMemberOfHouseholdForStatistics() {
            // Given
            setupValidAuthentication();
            setupNoHouseholdMembership();

            // When & Then
            assertThatThrownBy(() -> pantryItemService.getPantryStatistics(householdId, validToken))
                    .isInstanceOf(InsufficientPermissionException.class)
                    .hasMessageContaining("not a member of this household");

            verify(pantryItemRepository, never()).countByLocation_HouseholdId(any());
        }
    }

    @Nested
    @DisplayName("Product Variant Tests")
    class ProductVariantTests {

        @Test
        @DisplayName("Should get product variants by location successfully")
        void shouldGetProductVariantsByLocationSuccessfully() {
            // Given
            List<PantryItem> variants = List.of(testPantryItem);

            setupValidAuthentication();
            setupHouseholdMembership();
            when(locationRepository.findById(eq(locationId))).thenReturn(Optional.of(testLocation));
            when(productRepository.existsById(eq(productId))).thenReturn(true);
            when(pantryItemRepository.findByLocationIdAndProductIdOrderByExpirationDateNullsLast(
                    eq(locationId), eq(productId))).thenReturn(variants);

            // When
            List<PantryItemResponse> responses = pantryItemService.getProductVariantsByLocation(
                    locationId, productId, validToken);

            // Then
            assertThat(responses).hasSize(1);
            verify(pantryItemRepository).findByLocationIdAndProductIdOrderByExpirationDateNullsLast(
                    eq(locationId), eq(productId));
        }

        @Test
        @DisplayName("Should throw ValidationException when location ID is null for variants")
        void shouldThrowValidationExceptionWhenLocationIdIsNullForVariants() {
            assertThatThrownBy(() -> pantryItemService.getProductVariantsByLocation(
                    null, productId, validToken))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Location ID cannot be null");

            verify(userService, never()).getCurrentUser(any(JwtAuthenticationToken.class));
        }

        @Test
        @DisplayName("Should throw ValidationException when product ID is null for variants")
        void shouldThrowValidationExceptionWhenProductIdIsNullForVariants() {
            assertThatThrownBy(() -> pantryItemService.getProductVariantsByLocation(
                    locationId, null, validToken))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Product ID cannot be null");

            verify(userService, never()).getCurrentUser(any(JwtAuthenticationToken.class));
        }

        @Test
        @DisplayName("Should throw NotFoundException when location not found for variants")
        void shouldThrowNotFoundExceptionWhenLocationNotFoundForVariants() {
            // Given
            setupValidAuthentication();
            when(locationRepository.findById(eq(locationId))).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> pantryItemService.getProductVariantsByLocation(
                    locationId, productId, validToken))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("Location not found");

            verify(pantryItemRepository, never()).findByLocationIdAndProductIdOrderByExpirationDateNullsLast(any(), any());
        }

        @Test
        @DisplayName("Should throw NotFoundException when product not found for variants")
        void shouldThrowNotFoundExceptionWhenProductNotFoundForVariants() {
            // Given
            setupValidAuthentication();
            setupHouseholdMembership();
            when(locationRepository.findById(eq(locationId))).thenReturn(Optional.of(testLocation));
            when(productRepository.existsById(eq(productId))).thenReturn(false);

            // When & Then
            assertThatThrownBy(() -> pantryItemService.getProductVariantsByLocation(
                    locationId, productId, validToken))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("Product not found");

            verify(pantryItemRepository, never()).findByLocationIdAndProductIdOrderByExpirationDateNullsLast(any(), any());
        }

        @Test
        @DisplayName("Should check if product variant exists with expiration date")
        void shouldCheckIfProductVariantExistsWithExpirationDate() {
            // Given
            LocalDate expirationDate = LocalDate.now().plusDays(7);

            setupValidAuthentication();
            setupHouseholdMembership();
            when(locationRepository.findById(eq(locationId))).thenReturn(Optional.of(testLocation));
            when(pantryItemRepository.existsByLocationIdAndProductIdAndExpirationDate(
                    eq(locationId), eq(productId), eq(expirationDate))).thenReturn(true);

            // When
            boolean exists = pantryItemService.productVariantExists(
                    locationId, productId, expirationDate, validToken);

            // Then
            assertThat(exists).isTrue();
            verify(pantryItemRepository).existsByLocationIdAndProductIdAndExpirationDate(
                    eq(locationId), eq(productId), eq(expirationDate));
        }

        @Test
        @DisplayName("Should check if product variant exists with null expiration date")
        void shouldCheckIfProductVariantExistsWithNullExpirationDate() {
            // Given
            setupValidAuthentication();
            setupHouseholdMembership();
            when(locationRepository.findById(eq(locationId))).thenReturn(Optional.of(testLocation));
            when(pantryItemRepository.existsByLocationIdAndProductIdAndExpirationDateIsNull(
                    eq(locationId), eq(productId))).thenReturn(false);

            // When
            boolean exists = pantryItemService.productVariantExists(
                    locationId, productId, null, validToken);

            // Then
            assertThat(exists).isFalse();
            verify(pantryItemRepository).existsByLocationIdAndProductIdAndExpirationDateIsNull(
                    eq(locationId), eq(productId));
        }

        @Test
        @DisplayName("Should delete product variant with expiration date")
        void shouldDeleteProductVariantWithExpirationDate() {
            // Given
            LocalDate expirationDate = LocalDate.now().plusDays(7);

            setupValidAuthentication();
            setupHouseholdMembership();
            when(locationRepository.findById(eq(locationId))).thenReturn(Optional.of(testLocation));
            when(pantryItemRepository.deleteByLocationIdAndProductIdAndExpirationDate(
                    eq(locationId), eq(productId), eq(expirationDate))).thenReturn(1);

            // When
            int deletedCount = pantryItemService.deleteProductVariant(
                    locationId, productId, expirationDate, validToken);

            // Then
            assertThat(deletedCount).isEqualTo(1);
            verify(pantryItemRepository).deleteByLocationIdAndProductIdAndExpirationDate(
                    eq(locationId), eq(productId), eq(expirationDate));
        }

        @Test
        @DisplayName("Should delete product variant with null expiration date")
        void shouldDeleteProductVariantWithNullExpirationDate() {
            // Given
            setupValidAuthentication();
            setupHouseholdMembership();
            when(locationRepository.findById(eq(locationId))).thenReturn(Optional.of(testLocation));
            when(pantryItemRepository.deleteByLocationIdAndProductIdAndExpirationDateIsNull(
                    eq(locationId), eq(productId))).thenReturn(2);

            // When
            int deletedCount = pantryItemService.deleteProductVariant(
                    locationId, productId, null, validToken);

            // Then
            assertThat(deletedCount).isEqualTo(2);
            verify(pantryItemRepository).deleteByLocationIdAndProductIdAndExpirationDateIsNull(
                    eq(locationId), eq(productId));
        }
    }

    @Nested
    @DisplayName("Quantity Update Tests")
    class QuantityUpdateTests {

        @Test
        @DisplayName("Should update quantities for multiple items successfully")
        void shouldUpdateQuantitiesForMultipleItemsSuccessfully() {
            // Given
            UUID itemId1 = UUID.randomUUID();
            UUID itemId2 = UUID.randomUUID();

            PantryItem item1 = PantryItem.builder()
                    .id(itemId1)
                    .product(testProduct)
                    .location(testLocation)
                    .quantity(5)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();

            PantryItem item2 = PantryItem.builder()
                    .id(itemId2)
                    .product(testProduct)
                    .location(testLocation)
                    .quantity(10)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();

            Map<UUID, Integer> quantityUpdates = Map.of(
                    itemId1, 8,
                    itemId2, 12
            );

            setupValidAuthentication();
            setupHouseholdMembership();
            when(pantryItemRepository.findById(eq(itemId1))).thenReturn(Optional.of(item1));
            when(pantryItemRepository.findById(eq(itemId2))).thenReturn(Optional.of(item2));
            when(pantryItemRepository.save(any(PantryItem.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            List<PantryItemResponse> responses = pantryItemService.updateQuantities(quantityUpdates, validToken);

            // Then
            assertThat(responses).hasSize(2);
            verify(pantryItemRepository, times(2)).save(any(PantryItem.class));
        }

        @Test
        @DisplayName("Should throw ValidationException when quantity updates map is null")
        void shouldThrowValidationExceptionWhenQuantityUpdatesMapIsNull() {
            assertThatThrownBy(() -> pantryItemService.updateQuantities(null, validToken))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Quantity updates map cannot be null");

            verify(userService, never()).getCurrentUser(any(JwtAuthenticationToken.class));
        }

        @Test
        @DisplayName("Should throw ValidationException when quantity updates map is empty")
        void shouldThrowValidationExceptionWhenQuantityUpdatesMapIsEmpty() {
            assertThatThrownBy(() -> pantryItemService.updateQuantities(Collections.emptyMap(), validToken))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Quantity updates map cannot be empty");

            verify(userService, never()).getCurrentUser(any(JwtAuthenticationToken.class));
        }

        @Test
        @DisplayName("Should throw ValidationException when quantity is negative in updates")
        void shouldThrowValidationExceptionWhenQuantityIsNegativeInUpdates() {
            // Given
            UUID itemId = UUID.randomUUID();
            Map<UUID, Integer> quantityUpdates = Map.of(itemId, -5);

            setupValidAuthentication();

            // When & Then
            assertThatThrownBy(() -> pantryItemService.updateQuantities(quantityUpdates, validToken))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Quantity cannot be negative");

            verify(pantryItemRepository, never()).save(any(PantryItem.class));
        }

        @Test
        @DisplayName("Should continue with other items when one item is not found")
        void shouldContinueWithOtherItemsWhenOneItemIsNotFound() {
            // Given
            UUID itemId1 = UUID.randomUUID();
            UUID itemId2 = UUID.randomUUID();

            PantryItem item2 = PantryItem.builder()
                    .id(itemId2)
                    .product(testProduct)
                    .location(testLocation)
                    .quantity(10)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();

            Map<UUID, Integer> quantityUpdates = Map.of(
                    itemId1, 8,
                    itemId2, 12
            );

            setupValidAuthentication();
            setupHouseholdMembership();
            when(pantryItemRepository.findById(eq(itemId1))).thenReturn(Optional.empty());
            when(pantryItemRepository.findById(eq(itemId2))).thenReturn(Optional.of(item2));
            when(pantryItemRepository.save(any(PantryItem.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            List<PantryItemResponse> responses = pantryItemService.updateQuantities(quantityUpdates, validToken);

            // Then
            assertThat(responses).hasSize(1);
            verify(pantryItemRepository, times(1)).save(any(PantryItem.class));
        }

        @Test
        @DisplayName("Should continue with other items when user lacks permission for one item")
        void shouldContinueWithOtherItemsWhenUserLacksPermissionForOneItem() {
            // Given
            UUID itemId1 = UUID.randomUUID();
            UUID itemId2 = UUID.randomUUID();

            PantryItem item1 = PantryItem.builder()
                    .id(itemId1)
                    .product(testProduct)
                    .location(otherLocation) // Different household
                    .quantity(5)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();

            PantryItem item2 = PantryItem.builder()
                    .id(itemId2)
                    .product(testProduct)
                    .location(testLocation)
                    .quantity(10)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();

            Map<UUID, Integer> quantityUpdates = Map.of(
                    itemId1, 8,
                    itemId2, 12
            );

            setupValidAuthentication();
            when(pantryItemRepository.findById(eq(itemId1))).thenReturn(Optional.of(item1));
            when(pantryItemRepository.findById(eq(itemId2))).thenReturn(Optional.of(item2));
            when(householdMemberRepository.existsByHouseholdIdAndUserId(
                    eq(otherHousehold.getId()), eq(userId))).thenReturn(false);
            when(householdMemberRepository.existsByHouseholdIdAndUserId(
                    eq(householdId), eq(userId))).thenReturn(true);
            when(pantryItemRepository.save(any(PantryItem.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            List<PantryItemResponse> responses = pantryItemService.updateQuantities(quantityUpdates, validToken);

            // Then
            assertThat(responses).hasSize(1);
            verify(pantryItemRepository, times(1)).save(any(PantryItem.class));
        }

        @Test
        @DisplayName("Should allow setting quantity to null")
        void shouldAllowSettingQuantityToNull() {
            // Given
            UUID itemId = UUID.randomUUID();

            PantryItem item = PantryItem.builder()
                    .id(itemId)
                    .product(testProduct)
                    .location(testLocation)
                    .quantity(5)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();

            Map<UUID, Integer> quantityUpdates = new HashMap<>();
            quantityUpdates.put(itemId, null);

            setupValidAuthentication();
            setupHouseholdMembership();
            when(pantryItemRepository.findById(eq(itemId))).thenReturn(Optional.of(item));
            when(pantryItemRepository.save(any(PantryItem.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            List<PantryItemResponse> responses = pantryItemService.updateQuantities(quantityUpdates, validToken);

            // Then
            assertThat(responses).hasSize(1);
            verify(pantryItemRepository).save(argThat(savedItem -> savedItem.getQuantity() == null));
        }
    }
}