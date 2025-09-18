package org.cubord.cubordbackend.repository;

import org.cubord.cubordbackend.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class PantryItemRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private PantryItemRepository pantryItemRepository;

    private Household testHousehold1;
    private Household testHousehold2;
    private Location testLocation1;
    private Location testLocation2;
    private Location testLocation3;
    private Product testProduct1;
    private Product testProduct2;
    private Product testProduct3;
    private PantryItem testPantryItem1;
    private PantryItem testPantryItem2;
    private PantryItem testPantryItem3;
    private PantryItem testPantryItem4;

    @BeforeEach
    void setUp() {
        // Create test households
        testHousehold1 = Household.builder()
                .id(UUID.randomUUID())
                .name("Test Household 1")
                .build();
        
        testHousehold2 = Household.builder()
                .id(UUID.randomUUID())
                .name("Test Household 2")
                .build();

        entityManager.persistAndFlush(testHousehold1);
        entityManager.persistAndFlush(testHousehold2);

        // Create test locations
        testLocation1 = Location.builder()
                .id(UUID.randomUUID())
                .name("Kitchen Pantry")
                .description("Main kitchen storage")
                .household(testHousehold1)
                .build();

        testLocation2 = Location.builder()
                .id(UUID.randomUUID())
                .name("Refrigerator")
                .description("Main fridge")
                .household(testHousehold1)
                .build();

        testLocation3 = Location.builder()
                .id(UUID.randomUUID())
                .name("Basement Storage")
                .description("Bulk storage area")
                .household(testHousehold2)
                .build();

        entityManager.persistAndFlush(testLocation1);
        entityManager.persistAndFlush(testLocation2);
        entityManager.persistAndFlush(testLocation3);

        // Create test products
        testProduct1 = Product.builder()
                .id(UUID.randomUUID())
                .upc("123456789012")
                .name("Milk")
                .brand("FreshDairy")
                .category("Dairy")
                .defaultExpirationDays(7)
                .dataSource(ProductDataSource.MANUAL)
                .requiresApiRetry(false)
                .retryAttempts(0)
                .build();

        testProduct2 = Product.builder()
                .id(UUID.randomUUID())
                .upc("234567890123")
                .name("Bread")
                .brand("BakeryBest")
                .category("Bakery")
                .defaultExpirationDays(5)
                .dataSource(ProductDataSource.MANUAL)
                .requiresApiRetry(false)
                .retryAttempts(0)
                .build();

        testProduct3 = Product.builder()
                .id(UUID.randomUUID())
                .upc("345678901234")
                .name("Canned Beans")
                .brand("HealthyEats")
                .category("Canned Goods")
                .defaultExpirationDays(365)
                .dataSource(ProductDataSource.MANUAL)
                .requiresApiRetry(false)
                .retryAttempts(0)
                .build();

        entityManager.persistAndFlush(testProduct1);
        entityManager.persistAndFlush(testProduct2);
        entityManager.persistAndFlush(testProduct3);

        // Create test pantry items
        testPantryItem1 = PantryItem.builder()
                .id(UUID.randomUUID())
                .product(testProduct1)
                .location(testLocation2) // Milk in Refrigerator
                .expirationDate(LocalDate.now().plusDays(5))
                .quantity(2)
                .unitOfMeasure("liters")
                .notes("Whole milk")
                .createdAt(LocalDateTime.now().minusDays(2))
                .updatedAt(LocalDateTime.now().minusDays(2))
                .build();

        testPantryItem2 = PantryItem.builder()
                .id(UUID.randomUUID())
                .product(testProduct2)
                .location(testLocation1) // Bread in Kitchen Pantry
                .expirationDate(LocalDate.now().plusDays(3))
                .quantity(1)
                .unitOfMeasure("loaf")
                .notes("Whole wheat bread")
                .createdAt(LocalDateTime.now().minusDays(1))
                .updatedAt(LocalDateTime.now().minusDays(1))
                .build();

        testPantryItem3 = PantryItem.builder()
                .id(UUID.randomUUID())
                .product(testProduct3)
                .location(testLocation1) // Canned Beans in Kitchen Pantry
                .expirationDate(LocalDate.now().plusDays(300))
                .quantity(5)
                .unitOfMeasure("cans")
                .notes("Black beans")
                .createdAt(LocalDateTime.now().minusDays(3))
                .updatedAt(LocalDateTime.now().minusDays(3))
                .build();

        testPantryItem4 = PantryItem.builder()
                .id(UUID.randomUUID())
                .product(testProduct1)
                .location(testLocation3) // Milk in different household
                .expirationDate(LocalDate.now().plusDays(6))
                .quantity(1)
                .unitOfMeasure("liters")
                .notes("Skim milk")
                .createdAt(LocalDateTime.now().minusDays(1))
                .updatedAt(LocalDateTime.now().minusDays(1))
                .build();

        entityManager.persistAndFlush(testPantryItem1);
        entityManager.persistAndFlush(testPantryItem2);
        entityManager.persistAndFlush(testPantryItem3);
        entityManager.persistAndFlush(testPantryItem4);

        entityManager.clear();
    }

    // Basic CRUD Tests

    @Test
    @DisplayName("Test save and findById")
    void testSaveAndFindById() {
        // Given
        PantryItem newItem = PantryItem.builder()
                .id(UUID.randomUUID())
                .product(testProduct1)
                .location(testLocation1)
                .expirationDate(LocalDate.now().plusDays(10))
                .quantity(3)
                .unitOfMeasure("bottles")
                .notes("Test item")
                .build();

        // When
        PantryItem savedItem = pantryItemRepository.save(newItem);
        Optional<PantryItem> foundItem = pantryItemRepository.findById(savedItem.getId());

        // Then
        assertThat(foundItem).isPresent();
        assertThat(foundItem.get().getId()).isEqualTo(savedItem.getId());
        assertThat(foundItem.get().getQuantity()).isEqualTo(3);
        assertThat(foundItem.get().getUnitOfMeasure()).isEqualTo("bottles");
        assertThat(foundItem.get().getNotes()).isEqualTo("Test item");
    }

    @Test
    @DisplayName("Test findAll returns all pantry items")
    void testFindAll() {
        // When
        List<PantryItem> allItems = pantryItemRepository.findAll();

        // Then
        assertThat(allItems).hasSize(4);
        assertThat(allItems).extracting(PantryItem::getId)
                .containsExactlyInAnyOrder(
                        testPantryItem1.getId(),
                        testPantryItem2.getId(),
                        testPantryItem3.getId(),
                        testPantryItem4.getId()
                );
    }

    @Test
    @DisplayName("Test delete pantry item")
    void testDelete() {
        // When
        pantryItemRepository.delete(testPantryItem1);
        Optional<PantryItem> deletedItem = pantryItemRepository.findById(testPantryItem1.getId());

        // Then
        assertThat(deletedItem).isEmpty();
        assertThat(pantryItemRepository.findAll()).hasSize(3);
    }

    @Test
    @DisplayName("Test update pantry item")
    void testUpdate() {
        // Given
        testPantryItem1.setQuantity(10);
        testPantryItem1.setNotes("Updated notes");

        // When
        PantryItem updatedItem = pantryItemRepository.save(testPantryItem1);

        // Then
        assertThat(updatedItem.getQuantity()).isEqualTo(10);
        assertThat(updatedItem.getNotes()).isEqualTo("Updated notes");
    }

    // Custom Query Method Tests

    @Test
    @DisplayName("Test findByLocationId returns items for specific location")
    void testFindByLocationId() {
        // When
        List<PantryItem> itemsInLocation1 = pantryItemRepository.findByLocationId(testLocation1.getId());
        List<PantryItem> itemsInLocation2 = pantryItemRepository.findByLocationId(testLocation2.getId());

        // Then
        assertThat(itemsInLocation1).hasSize(2);
        assertThat(itemsInLocation1).extracting(item -> item.getProduct().getName())
                .containsExactlyInAnyOrder("Bread", "Canned Beans");

        assertThat(itemsInLocation2).hasSize(1);
        assertThat(itemsInLocation2).extracting(item -> item.getProduct().getName())
                .containsExactly("Milk");
    }

    @Test
    @DisplayName("Test findByProductId returns items for specific product")
    void testFindByProductId() {
        // When
        List<PantryItem> milkItems = pantryItemRepository.findByProductId(testProduct1.getId());
        List<PantryItem> breadItems = pantryItemRepository.findByProductId(testProduct2.getId());

        // Then
        assertThat(milkItems).hasSize(2); // Milk in two different locations
        assertThat(milkItems).extracting(item -> item.getLocation().getName())
                .containsExactlyInAnyOrder("Refrigerator", "Basement Storage");

        assertThat(breadItems).hasSize(1);
        assertThat(breadItems).extracting(item -> item.getLocation().getName())
                .containsExactly("Kitchen Pantry");
    }

    @Test
    @DisplayName("Test findByLocation_HouseholdId returns items for household")
    void testFindByLocationHouseholdId() {
        // When
        List<PantryItem> household1Items = pantryItemRepository
                .findByLocation_HouseholdId(testHousehold1.getId());
        List<PantryItem> household2Items = pantryItemRepository
                .findByLocation_HouseholdId(testHousehold2.getId());

        // Then
        assertThat(household1Items).hasSize(3);
        assertThat(household1Items).extracting(item -> item.getProduct().getName())
                .containsExactlyInAnyOrder("Milk", "Bread", "Canned Beans");

        assertThat(household2Items).hasSize(1);
        assertThat(household2Items).extracting(item -> item.getProduct().getName())
                .containsExactly("Milk");
    }

    // Expiration Date Query Tests

    @Test
    @DisplayName("Test findByExpirationDateBefore returns expiring items")
    void testFindByExpirationDateBefore() {
        // When
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        LocalDate nextWeek = LocalDate.now().plusDays(7);
        
        List<PantryItem> expiringTomorrow = pantryItemRepository.findByExpirationDateBefore(tomorrow);
        List<PantryItem> expiringNextWeek = pantryItemRepository.findByExpirationDateBefore(nextWeek);

        // Then
        assertThat(expiringTomorrow).isEmpty(); // No items expire before tomorrow
        
        assertThat(expiringNextWeek).hasSize(3); // Milk and Bread expire within a week
        assertThat(expiringNextWeek).extracting(item -> item.getProduct().getName())
                .containsExactlyInAnyOrder("Milk", "Bread", "Milk");
    }

    @Test
    @DisplayName("Test findByExpirationDateBetween returns items in date range")
    void testFindByExpirationDateBetween() {
        // When
        LocalDate startDate = LocalDate.now().plusDays(1);
        LocalDate endDate = LocalDate.now().plusDays(10);
        
        List<PantryItem> itemsInRange = pantryItemRepository
                .findByExpirationDateBetween(startDate, endDate);

        // Then
        assertThat(itemsInRange).hasSize(3); // Milk and Bread items
        assertThat(itemsInRange).extracting(item -> item.getProduct().getName())
                .containsExactlyInAnyOrder("Milk", "Bread", "Milk");
    }

    @Test
    @DisplayName("Test findByExpirationDateBeforeAndLocation_HouseholdId for household expiring items")
    void testFindByExpirationDateBeforeAndLocationHouseholdId() {
        // When
        LocalDate nextWeek = LocalDate.now().plusDays(7);
        List<PantryItem> household1ExpiringItems = pantryItemRepository
                .findByExpirationDateBeforeAndLocation_HouseholdId(nextWeek, testHousehold1.getId());

        // Then
        assertThat(household1ExpiringItems).hasSize(2);
        assertThat(household1ExpiringItems).extracting(item -> item.getProduct().getName())
                .containsExactlyInAnyOrder("Milk", "Bread");
    }

    // Low Stock Query Tests

    @Test
    @DisplayName("Test findByQuantityLessThanEqual for low stock items")
    void testFindByQuantityLessThanEqual() {
        // When
        List<PantryItem> lowStockItems = pantryItemRepository.findByQuantityLessThanEqual(2);

        // Then
        assertThat(lowStockItems).hasSize(3); // Items with quantity <= 2
        assertThat(lowStockItems).extracting(PantryItem::getQuantity)
                .containsExactlyInAnyOrder(2, 1, 1);
    }

    @Test
    @DisplayName("Test findByQuantityLessThanEqualAndLocation_HouseholdId for household low stock")
    void testFindByQuantityLessThanEqualAndLocationHouseholdId() {
        // When
        List<PantryItem> household1LowStock = pantryItemRepository
                .findByQuantityLessThanEqualAndLocation_HouseholdId(2, testHousehold1.getId());

        // Then
        assertThat(household1LowStock).hasSize(2);
        assertThat(household1LowStock).extracting(item -> item.getProduct().getName())
                .containsExactlyInAnyOrder("Milk", "Bread");
    }

    // Sorting and Pagination Tests

    @Test
    @DisplayName("Test findByLocationId with sorting")
    void testFindByLocationIdWithSorting() {
        // When
        Sort sortByQuantityDesc = Sort.by(Sort.Direction.DESC, "quantity");
        List<PantryItem> sortedItems = pantryItemRepository
                .findByLocationId(testLocation1.getId(), sortByQuantityDesc);

        // Then
        assertThat(sortedItems).hasSize(2);
        assertThat(sortedItems.get(0).getQuantity()).isEqualTo(5); // Canned Beans first
        assertThat(sortedItems.get(1).getQuantity()).isEqualTo(1); // Bread second
    }

    @Test
    @DisplayName("Test findByLocation_HouseholdId with pagination")
    void testFindByLocationHouseholdIdWithPagination() {
        // When
        Pageable pageable = PageRequest.of(0, 2, Sort.by("createdAt"));
        Page<PantryItem> firstPage = pantryItemRepository
                .findByLocation_HouseholdId(testHousehold1.getId(), pageable);

        // Then
        assertThat(firstPage.getTotalElements()).isEqualTo(3);
        assertThat(firstPage.getTotalPages()).isEqualTo(2);
        assertThat(firstPage.getContent()).hasSize(2);
        assertThat(firstPage.isFirst()).isTrue();
        assertThat(firstPage.hasNext()).isTrue();
    }

    // Complex Query Tests

    @Test
    @DisplayName("Test findByProductNameContainingIgnoreCase for product search")
    void testFindByProductNameContainingIgnoreCase() {
        // When
        List<PantryItem> milkItems = pantryItemRepository
                .findByProduct_NameContainingIgnoreCase("milk");
        List<PantryItem> beanItems = pantryItemRepository
                .findByProduct_NameContainingIgnoreCase("BEAN");

        // Then
        assertThat(milkItems).hasSize(2);
        assertThat(milkItems).extracting(item -> item.getProduct().getName())
                .allMatch(name -> name.toLowerCase().contains("milk"));

        assertThat(beanItems).hasSize(1);
        assertThat(beanItems).extracting(item -> item.getProduct().getName())
                .allMatch(name -> name.toLowerCase().contains("beans"));
    }

    @Test
    @DisplayName("Test findByProduct_CategoryAndLocation_HouseholdId for category filtering")
    void testFindByProductCategoryAndLocationHouseholdId() {
        // When
        List<PantryItem> dairyItems = pantryItemRepository
                .findByProduct_CategoryAndLocation_HouseholdId("Dairy", testHousehold1.getId());

        // Then
        assertThat(dairyItems).hasSize(1);
        assertThat(dairyItems.getFirst().getProduct().getName()).isEqualTo("Milk");
    }

    // Edge Cases and Error Conditions

    @Test
    @DisplayName("Test queries with null parameters return empty results")
    void testQueriesWithNullParameters() {
        // When & Then
        assertThat(pantryItemRepository.findByLocationId(null)).isEmpty();
        assertThat(pantryItemRepository.findByProductId(null)).isEmpty();
        assertThat(pantryItemRepository.findByLocation_HouseholdId(null)).isEmpty();
    }

    @Test
    @DisplayName("Test queries with non-existent IDs return empty results")
    void testQueriesWithNonExistentIds() {
        // Given
        UUID nonExistentId = UUID.randomUUID();

        // When & Then
        assertThat(pantryItemRepository.findByLocationId(nonExistentId)).isEmpty();
        assertThat(pantryItemRepository.findByProductId(nonExistentId)).isEmpty();
        assertThat(pantryItemRepository.findByLocation_HouseholdId(nonExistentId)).isEmpty();
    }

    @Test
    @DisplayName("Test countByLocationId returns correct counts")
    void testCountByLocationId() {
        // When & Then
        assertThat(pantryItemRepository.countByLocationId(testLocation1.getId())).isEqualTo(2);
        assertThat(pantryItemRepository.countByLocationId(testLocation2.getId())).isEqualTo(1);
        assertThat(pantryItemRepository.countByLocationId(testLocation3.getId())).isEqualTo(1);
    }

    @Test
    @DisplayName("Test countByLocation_HouseholdId returns correct counts")
    void testCountByLocationHouseholdId() {
        // When & Then
        assertThat(pantryItemRepository.countByLocation_HouseholdId(testHousehold1.getId())).isEqualTo(3);
        assertThat(pantryItemRepository.countByLocation_HouseholdId(testHousehold2.getId())).isEqualTo(1);
    }



    // Transaction and Concurrency Tests

    @Test
    @DisplayName("Test batch save operation")
    void testBatchSave() {
        // Given
        List<PantryItem> newItems = List.of(
                PantryItem.builder()
                        .id(UUID.randomUUID())
                        .product(testProduct1)
                        .location(testLocation1)
                        .quantity(2)
                        .unitOfMeasure("cartons")
                        .build(),
                PantryItem.builder()
                        .id(UUID.randomUUID())
                        .product(testProduct2)
                        .location(testLocation2)
                        .quantity(3)
                        .unitOfMeasure("loaves")
                        .build()
        );

        // When
        List<PantryItem> savedItems = pantryItemRepository.saveAll(newItems);

        // Then
        assertThat(savedItems).hasSize(2);
        assertThat(pantryItemRepository.findAll()).hasSize(6); // 4 existing + 2 new
    }

    // Add these test methods to your existing PantryItemRepositoryTest class

@Nested
@DisplayName("Expiration-Date-Aware Duplicate Detection Tests")
class ExpirationDateDuplicateDetectionTests {

    @Test
    @DisplayName("Test findByLocationIdAndProductIdAndExpirationDate finds exact matches")
    void testFindByLocationIdAndProductIdAndExpirationDate() {
        // Given
        LocalDate specificDate = LocalDate.now().plusDays(5);
        
        // When
        Optional<PantryItem> exactMatch = pantryItemRepository
                .findByLocationIdAndProductIdAndExpirationDate(
                        testLocation2.getId(), 
                        testProduct1.getId(), 
                        specificDate);
        
        Optional<PantryItem> noMatch = pantryItemRepository
                .findByLocationIdAndProductIdAndExpirationDate(
                        testLocation2.getId(), 
                        testProduct1.getId(), 
                        LocalDate.now().plusDays(10)); // Different date
        
        // Then
        assertThat(exactMatch).isPresent();
        assertThat(exactMatch.get().getId()).isEqualTo(testPantryItem1.getId());
        assertThat(noMatch).isEmpty();
    }

    @Test
    @DisplayName("Test findByLocationIdAndProductIdAndExpirationDateIsNull for items without expiration")
    void testFindByLocationIdAndProductIdAndExpirationDateIsNull() {
        // Given - Create item without expiration date
        PantryItem itemWithoutExpiration = PantryItem.builder()
                .id(UUID.randomUUID())
                .product(testProduct3)
                .location(testLocation1)
                .quantity(3)
                .unitOfMeasure("cans")
                .expirationDate(null) // No expiration date
                .build();
        entityManager.persistAndFlush(itemWithoutExpiration);
        entityManager.clear();
        
        // When
        Optional<PantryItem> foundItem = pantryItemRepository
                .findByLocationIdAndProductIdAndExpirationDateIsNull(
                        testLocation1.getId(), 
                        testProduct3.getId());
        
        // Then
        assertThat(foundItem).isPresent();
        assertThat(foundItem.get().getExpirationDate()).isNull();
    }

    @Test
    @DisplayName("Test existsByLocationIdAndProductIdAndExpirationDate for exact duplicate checking")
    void testExistsByLocationIdAndProductIdAndExpirationDate() {
        // Given
        LocalDate existingDate = LocalDate.now().plusDays(5);
        LocalDate nonExistentDate = LocalDate.now().plusDays(15);
        
        // When & Then
        assertThat(pantryItemRepository.existsByLocationIdAndProductIdAndExpirationDate(
                testLocation2.getId(), testProduct1.getId(), existingDate)).isTrue();
        
        assertThat(pantryItemRepository.existsByLocationIdAndProductIdAndExpirationDate(
                testLocation2.getId(), testProduct1.getId(), nonExistentDate)).isFalse();
    }

    @Test
    @DisplayName("Test existsByLocationIdAndProductIdAndExpirationDateIsNull")
    void testExistsByLocationIdAndProductIdAndExpirationDateIsNull() {
        // Given - Create item without expiration date
        PantryItem itemWithoutExpiration = PantryItem.builder()
                .id(UUID.randomUUID())
                .product(testProduct2)
                .location(testLocation2)
                .quantity(2)
                .expirationDate(null)
                .build();
        entityManager.persistAndFlush(itemWithoutExpiration);
        entityManager.clear();
        
        // When & Then
        assertThat(pantryItemRepository.existsByLocationIdAndProductIdAndExpirationDateIsNull(
                testLocation2.getId(), testProduct2.getId())).isTrue();
        
        assertThat(pantryItemRepository.existsByLocationIdAndProductIdAndExpirationDateIsNull(
                testLocation1.getId(), testProduct2.getId())).isFalse(); // Different location
    }

    @Test
    @DisplayName("Test deleteByLocationIdAndProductIdAndExpirationDate removes specific variant")
    void testDeleteByLocationIdAndProductIdAndExpirationDate() {
        // Given
        LocalDate targetDate = LocalDate.now().plusDays(5);
        
        // When
        int deletedCount = pantryItemRepository.deleteByLocationIdAndProductIdAndExpirationDate(
                testLocation2.getId(), testProduct1.getId(), targetDate);
        
        // Then
        assertThat(deletedCount).isEqualTo(1);
        assertThat(pantryItemRepository.findById(testPantryItem1.getId())).isEmpty();
        assertThat(pantryItemRepository.findAll()).hasSize(3); // One removed
    }

    @Test
    @DisplayName("Test deleteByLocationIdAndProductIdAndExpirationDateIsNull")
    void testDeleteByLocationIdAndProductIdAndExpirationDateIsNull() {
        // Given - Create item without expiration date
        PantryItem itemWithoutExpiration = PantryItem.builder()
                .id(UUID.randomUUID())
                .product(testProduct1)
                .location(testLocation1)
                .quantity(4)
                .expirationDate(null)
                .build();
        PantryItem saved = entityManager.persistAndFlush(itemWithoutExpiration);
        entityManager.clear();
        
        // When
        int deletedCount = pantryItemRepository.deleteByLocationIdAndProductIdAndExpirationDateIsNull(
                testLocation1.getId(), testProduct1.getId());
        
        // Then
        assertThat(deletedCount).isEqualTo(1);
        assertThat(pantryItemRepository.findById(saved.getId())).isEmpty();
    }
}

@Nested
@DisplayName("Product Variant Query Tests")
class ProductVariantQueryTests {

    @Test
    @DisplayName("Test findByLocationIdAndProductIdOrderByExpirationDateAsc returns variants sorted by expiration")
    void testFindByLocationIdAndProductIdOrderByExpirationDateAsc() {
        // Given - Create multiple variants of same product with different expiration dates
        PantryItem variant1 = PantryItem.builder()
                .id(UUID.randomUUID())
                .product(testProduct1)
                .location(testLocation1)
                .quantity(2)
                .expirationDate(LocalDate.now().plusDays(3))
                .build();
        
        PantryItem variant2 = PantryItem.builder()
                .id(UUID.randomUUID())
                .product(testProduct1)
                .location(testLocation1)
                .quantity(1)
                .expirationDate(LocalDate.now().plusDays(7))
                .build();
        
        PantryItem variant3 = PantryItem.builder()
                .id(UUID.randomUUID())
                .product(testProduct1)
                .location(testLocation1)
                .quantity(3)
                .expirationDate(LocalDate.now().plusDays(1))
                .build();
        
        entityManager.persistAndFlush(variant1);
        entityManager.persistAndFlush(variant2);
        entityManager.persistAndFlush(variant3);
        entityManager.clear();
        
        // When
        List<PantryItem> variants = pantryItemRepository
                .findByLocationIdAndProductIdOrderByExpirationDateAsc(
                        testLocation1.getId(), testProduct1.getId());
        
        // Then
        assertThat(variants).hasSize(3);
        assertThat(variants.get(0).getExpirationDate()).isEqualTo(LocalDate.now().plusDays(1)); // Earliest first
        assertThat(variants.get(1).getExpirationDate()).isEqualTo(LocalDate.now().plusDays(3));
        assertThat(variants.get(2).getExpirationDate()).isEqualTo(LocalDate.now().plusDays(7)); // Latest last
    }

    @Test
    @DisplayName("Test findByLocationIdAndProductIdOrderByExpirationDateNullsLast handles null expiration dates")
    void testFindByLocationIdAndProductIdOrderByExpirationDateNullsLast() {
        // Given
        PantryItem variantWithExpiration = PantryItem.builder()
                .id(UUID.randomUUID())
                .product(testProduct3)
                .location(testLocation1)
                .quantity(2)
                .expirationDate(LocalDate.now().plusDays(30))
                .build();
        
        PantryItem variantWithoutExpiration = PantryItem.builder()
                .id(UUID.randomUUID())
                .product(testProduct3)
                .location(testLocation1)
                .quantity(1)
                .expirationDate(null) // No expiration date
                .build();
        
        entityManager.persistAndFlush(variantWithExpiration);
        entityManager.persistAndFlush(variantWithoutExpiration);
        entityManager.clear();
        
        // When
        List<PantryItem> variants = pantryItemRepository
                .findByLocationIdAndProductIdOrderByExpirationDateNullsLast(
                        testLocation1.getId(), testProduct3.getId());
        
        // Then
        assertThat(variants).hasSize(3); // Including existing testPantryItem3
        
        // Items with expiration dates should come first
        List<PantryItem> itemsWithExpiration = variants.stream()
                .filter(item -> item.getExpirationDate() != null)
                .toList();
        List<PantryItem> itemsWithoutExpiration = variants.stream()
                .filter(item -> item.getExpirationDate() == null)
                .toList();
        
        assertThat(itemsWithExpiration).hasSize(2);
        assertThat(itemsWithoutExpiration).hasSize(1);
        
        // Verify nulls are last in the list
        assertThat(variants.getLast().getExpirationDate()).isNull();
    }
}

@Nested
@DisplayName("Enhanced Query Tests")
class EnhancedQueryTests {

    @Test
    @DisplayName("Test findByLocation_HouseholdIdOrderByProductAndExpiration returns sorted results")
    void testFindByLocationHouseholdIdOrderByProductAndExpiration() {
        // When
        List<PantryItem> sortedItems = pantryItemRepository
                .findByLocation_HouseholdIdOrderByProductAndExpiration(testHousehold1.getId());
        
        // Then
        assertThat(sortedItems).hasSize(3);
        
        // Should be sorted by product name first, then by expiration date
        assertThat(sortedItems.get(0).getProduct().getName()).isEqualTo("Bread");
        assertThat(sortedItems.get(1).getProduct().getName()).isEqualTo("Canned Beans");
        assertThat(sortedItems.get(2).getProduct().getName()).isEqualTo("Milk");
    }

    @Test
    @DisplayName("Test findByLocationIdOrderByProductAndExpiration")
    void testFindByLocationIdOrderByProductAndExpiration() {
        // When
        List<PantryItem> sortedItems = pantryItemRepository
                .findByLocationIdOrderByProductAndExpiration(testLocation1.getId());
        
        // Then
        assertThat(sortedItems).hasSize(2);
        assertThat(sortedItems.get(0).getProduct().getName()).isEqualTo("Bread");
        assertThat(sortedItems.get(1).getProduct().getName()).isEqualTo("Canned Beans");
    }

    @Test
    @DisplayName("Test countDistinctProductsByHouseholdId")
    void testCountDistinctProductsByHouseholdId() {
        // When
        long distinctProductCount = pantryItemRepository
                .countDistinctProductsByHouseholdId(testHousehold1.getId());
        
        // Then
        assertThat(distinctProductCount).isEqualTo(3); // Milk, Bread, Canned Beans
    }

    @Test
    @DisplayName("Test countExpiringItemsByHouseholdIdAndDate")
    void testCountExpiringItemsByHouseholdIdAndDate() {
        // When
        LocalDate nextWeek = LocalDate.now().plusDays(7);
        long expiringCount = pantryItemRepository
                .countExpiringItemsByHouseholdIdAndDate(testHousehold1.getId(), nextWeek);
        
        // Then
        assertThat(expiringCount).isEqualTo(2); // Milk and Bread expire within a week
    }

    @Test
    @DisplayName("Test sumQuantityByLocationAndProduct")
    void testSumQuantityByLocationAndProduct() {
        // Given - Create another milk item in same location to test aggregation
        PantryItem additionalMilk = PantryItem.builder()
                .id(UUID.randomUUID())
                .product(testProduct1)
                .location(testLocation2)
                .quantity(1)
                .expirationDate(LocalDate.now().plusDays(4))
                .build();
        entityManager.persistAndFlush(additionalMilk);
        entityManager.clear();
        
        // When
        Integer totalQuantity = pantryItemRepository
                .sumQuantityByLocationAndProduct(testLocation2.getId(), testProduct1.getId());
        
        // Then
        assertThat(totalQuantity).isEqualTo(3); // 2 (original) + 1 (additional)
    }
}

    @Nested
    @DisplayName("Null Expiration Date Handling Tests")
    class NullExpirationDateTests {

        @Test
        @DisplayName("Test findByExpirationDateIsNull")
        void testFindByExpirationDateIsNull() {
            // Given - Create items without expiration dates
            PantryItem itemWithoutExpiration1 = PantryItem.builder()
                    .id(UUID.randomUUID())
                    .product(testProduct3)
                    .location(testLocation1)
                    .quantity(5)
                    .expirationDate(null)
                    .build();

            PantryItem itemWithoutExpiration2 = PantryItem.builder()
                    .id(UUID.randomUUID())
                    .product(testProduct2)
                    .location(testLocation2)
                    .quantity(2)
                    .expirationDate(null)
                    .build();

            entityManager.persistAndFlush(itemWithoutExpiration1);
            entityManager.persistAndFlush(itemWithoutExpiration2);
            entityManager.clear();

            // When
            List<PantryItem> itemsWithoutExpiration = pantryItemRepository.findByExpirationDateIsNull();

            // Then
            assertThat(itemsWithoutExpiration).hasSize(2);
            assertThat(itemsWithoutExpiration).allMatch(item -> item.getExpirationDate() == null);
        }

        @Test
        @DisplayName("Test findByExpirationDateIsNullAndLocation_HouseholdId")
        void testFindByExpirationDateIsNullAndLocationHouseholdId() {
            // Given
            PantryItem itemWithoutExpiration = PantryItem.builder()
                    .id(UUID.randomUUID())
                    .product(testProduct1)
                    .location(testLocation1)
                    .quantity(3)
                    .expirationDate(null)
                    .build();

            entityManager.persistAndFlush(itemWithoutExpiration);
            entityManager.clear();

            // When
            List<PantryItem> itemsInHousehold1 = pantryItemRepository
                    .findByExpirationDateIsNullAndLocation_HouseholdId(testHousehold1.getId());
            List<PantryItem> itemsInHousehold2 = pantryItemRepository
                    .findByExpirationDateIsNullAndLocation_HouseholdId(testHousehold2.getId());

            // Then
            assertThat(itemsInHousehold1).hasSize(1);
            assertThat(itemsInHousehold1.getFirst().getExpirationDate()).isNull();
            assertThat(itemsInHousehold2).isEmpty();
        }
    }
}

