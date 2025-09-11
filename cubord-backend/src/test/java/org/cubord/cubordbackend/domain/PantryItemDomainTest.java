package org.cubord.cubordbackend.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

@ExtendWith(MockitoExtension.class)
class PantryItemDomainTest {

    @Test
    @DisplayName("Test PantryItem builder with all fields")
    void testPantryItemBuilder() {
        // Given
        UUID id = UUID.randomUUID();
        Product product = Product.builder().id(UUID.randomUUID()).name("Test Product").build();
        Location location = Location.builder().id(UUID.randomUUID()).name("Test Location").build();
        LocalDate expirationDate = LocalDate.now().plusDays(7);
        Integer quantity = 5;
        String unitOfMeasure = "pieces";
        String notes = "Test notes";
        LocalDateTime now = LocalDateTime.now();

        // When
        PantryItem pantryItem = PantryItem.builder()
                .id(id)
                .product(product)
                .location(location)
                .expirationDate(expirationDate)
                .quantity(quantity)
                .unitOfMeasure(unitOfMeasure)
                .notes(notes)
                .createdAt(now)
                .updatedAt(now)
                .build();

        // Then
        assertThat(pantryItem.getId()).isEqualTo(id);
        assertThat(pantryItem.getProduct()).isEqualTo(product);
        assertThat(pantryItem.getLocation()).isEqualTo(location);
        assertThat(pantryItem.getExpirationDate()).isEqualTo(expirationDate);
        assertThat(pantryItem.getQuantity()).isEqualTo(quantity);
        assertThat(pantryItem.getUnitOfMeasure()).isEqualTo(unitOfMeasure);
        assertThat(pantryItem.getNotes()).isEqualTo(notes);
        assertThat(pantryItem.getCreatedAt()).isEqualTo(now);
        assertThat(pantryItem.getUpdatedAt()).isEqualTo(now);
    }

    @Test
    @DisplayName("Test PantryItem no-args constructor")
    void testPantryItemNoArgsConstructor() {
        // When
        PantryItem pantryItem = new PantryItem();

        // Then
        assertThat(pantryItem.getId()).isNull();
        assertThat(pantryItem.getProduct()).isNull();
        assertThat(pantryItem.getLocation()).isNull();
        assertThat(pantryItem.getExpirationDate()).isNull();
        assertThat(pantryItem.getQuantity()).isNull();
        assertThat(pantryItem.getUnitOfMeasure()).isNull();
        assertThat(pantryItem.getNotes()).isNull();
        assertThat(pantryItem.getCreatedAt()).isNull();
        assertThat(pantryItem.getUpdatedAt()).isNull();
    }

    @Test
    @DisplayName("Test PantryItem all-args constructor")
    void testPantryItemAllArgsConstructor() {
        // Given
        UUID id = UUID.randomUUID();
        Product product = Product.builder().id(UUID.randomUUID()).name("Test Product").build();
        Location location = Location.builder().id(UUID.randomUUID()).name("Test Location").build();
        LocalDate expirationDate = LocalDate.now().plusDays(7);
        Integer quantity = 5;
        String unitOfMeasure = "pieces";
        String notes = "Test notes";
        LocalDateTime now = LocalDateTime.now();

        // When
        PantryItem pantryItem = new PantryItem(id, product, location, expirationDate, 
                quantity, unitOfMeasure, notes, now, now);

        // Then
        assertThat(pantryItem.getId()).isEqualTo(id);
        assertThat(pantryItem.getProduct()).isEqualTo(product);
        assertThat(pantryItem.getLocation()).isEqualTo(location);
        assertThat(pantryItem.getExpirationDate()).isEqualTo(expirationDate);
        assertThat(pantryItem.getQuantity()).isEqualTo(quantity);
        assertThat(pantryItem.getUnitOfMeasure()).isEqualTo(unitOfMeasure);
        assertThat(pantryItem.getNotes()).isEqualTo(notes);
        assertThat(pantryItem.getCreatedAt()).isEqualTo(now);
        assertThat(pantryItem.getUpdatedAt()).isEqualTo(now);
    }

    @Test
    @DisplayName("Test PantryItem onCreate callback sets timestamps")
    void testOnCreateSetsTimestamps() {
        // Given
        PantryItem pantryItem = new PantryItem();
        LocalDateTime fixedTime = LocalDateTime.of(2024, 1, 15, 10, 30, 0);

        try (MockedStatic<LocalDateTime> mockedLocalDateTime = mockStatic(LocalDateTime.class)) {
            mockedLocalDateTime.when(LocalDateTime::now).thenReturn(fixedTime);

            // When
            pantryItem.onCreate();

            // Then
            assertThat(pantryItem.getCreatedAt()).isEqualTo(fixedTime);
            assertThat(pantryItem.getUpdatedAt()).isEqualTo(fixedTime);
        }
    }

    @Test
    @DisplayName("Test PantryItem onUpdate callback updates timestamp")
    void testOnUpdateUpdatesTimestamp() {
        // Given
        LocalDateTime createdTime = LocalDateTime.of(2024, 1, 15, 10, 30, 0);
        LocalDateTime updatedTime = LocalDateTime.of(2024, 1, 15, 11, 30, 0);
        
        PantryItem pantryItem = PantryItem.builder()
                .createdAt(createdTime)
                .updatedAt(createdTime)
                .build();

        try (MockedStatic<LocalDateTime> mockedLocalDateTime = mockStatic(LocalDateTime.class)) {
            mockedLocalDateTime.when(LocalDateTime::now).thenReturn(updatedTime);

            // When
            pantryItem.onUpdate();

            // Then
            assertThat(pantryItem.getCreatedAt()).isEqualTo(createdTime); // Should not change
            assertThat(pantryItem.getUpdatedAt()).isEqualTo(updatedTime); // Should be updated
        }
    }

    @Test
    @DisplayName("Test PantryItem equals and hashCode with same id")
    void testEqualsAndHashCodeWithSameId() {
        // Given
        UUID id = UUID.randomUUID();
        PantryItem pantryItem1 = PantryItem.builder().id(id).build();
        PantryItem pantryItem2 = PantryItem.builder().id(id).build();

        // When & Then
        assertThat(pantryItem1).isEqualTo(pantryItem2);
        assertThat(pantryItem1.hashCode()).isEqualTo(pantryItem2.hashCode());
    }

    @Test
    @DisplayName("Test PantryItem not equals with different ids")
    void testNotEqualsWithDifferentIds() {
        // Given
        PantryItem pantryItem1 = PantryItem.builder().id(UUID.randomUUID()).build();
        PantryItem pantryItem2 = PantryItem.builder().id(UUID.randomUUID()).build();

        // When & Then
        assertThat(pantryItem1).isNotEqualTo(pantryItem2);
        assertThat(pantryItem1.hashCode()).isNotEqualTo(pantryItem2.hashCode());
    }

    @Test
    @DisplayName("Test PantryItem equals with null")
    void testEqualsWithNull() {
        // Given
        PantryItem pantryItem = PantryItem.builder().id(UUID.randomUUID()).build();

        // When & Then
        assertThat(pantryItem).isNotEqualTo(null);
    }

    @Test
    @DisplayName("Test PantryItem equals with different class")
    void testEqualsWithDifferentClass() {
        // Given
        PantryItem pantryItem = PantryItem.builder().id(UUID.randomUUID()).build();
        String otherObject = "not a pantry item";

        // When & Then
        assertThat(pantryItem).isNotEqualTo(otherObject);
    }

    @Test
    @DisplayName("Test PantryItem toString excludes product and location")
    void testToStringExcludesRelationships() {
        // Given
        UUID id = UUID.randomUUID();
        Product product = Product.builder().id(UUID.randomUUID()).name("Test Product").build();
        Location location = Location.builder().id(UUID.randomUUID()).name("Test Location").build();
        
        PantryItem pantryItem = PantryItem.builder()
                .id(id)
                .product(product)
                .location(location)
                .quantity(5)
                .unitOfMeasure("pieces")
                .notes("Test notes")
                .build();

        // When
        String toString = pantryItem.toString();

        // Then
        assertThat(toString).contains("quantity=5");
        assertThat(toString).contains("unitOfMeasure=pieces");
        assertThat(toString).contains("notes=Test notes");
        assertThat(toString).doesNotContain("product=");
        assertThat(toString).doesNotContain("location=");
    }

    @Test
    @DisplayName("Test PantryItem with minimal required fields")
    void testPantryItemWithMinimalFields() {
        // Given
        UUID id = UUID.randomUUID();
        Product product = Product.builder().id(UUID.randomUUID()).name("Test Product").build();
        Location location = Location.builder().id(UUID.randomUUID()).name("Test Location").build();

        // When
        PantryItem pantryItem = PantryItem.builder()
                .id(id)
                .product(product)
                .location(location)
                .build();

        // Then
        assertThat(pantryItem.getId()).isEqualTo(id);
        assertThat(pantryItem.getProduct()).isEqualTo(product);
        assertThat(pantryItem.getLocation()).isEqualTo(location);
        assertThat(pantryItem.getExpirationDate()).isNull();
        assertThat(pantryItem.getQuantity()).isNull();
        assertThat(pantryItem.getUnitOfMeasure()).isNull();
        assertThat(pantryItem.getNotes()).isNull();
    }

    @Test
    @DisplayName("Test PantryItem with zero quantity")
    void testPantryItemWithZeroQuantity() {
        // Given
        UUID id = UUID.randomUUID();
        Product product = Product.builder().id(UUID.randomUUID()).name("Test Product").build();
        Location location = Location.builder().id(UUID.randomUUID()).name("Test Location").build();

        // When
        PantryItem pantryItem = PantryItem.builder()
                .id(id)
                .product(product)
                .location(location)
                .quantity(0)
                .build();

        // Then
        assertThat(pantryItem.getQuantity()).isEqualTo(0);
    }

    @Test
    @DisplayName("Test PantryItem with negative quantity")
    void testPantryItemWithNegativeQuantity() {
        // Given
        UUID id = UUID.randomUUID();
        Product product = Product.builder().id(UUID.randomUUID()).name("Test Product").build();
        Location location = Location.builder().id(UUID.randomUUID()).name("Test Location").build();

        // When
        PantryItem pantryItem = PantryItem.builder()
                .id(id)
                .product(product)
                .location(location)
                .quantity(-1)
                .build();

        // Then
        assertThat(pantryItem.getQuantity()).isEqualTo(-1);
    }

    @Test
    @DisplayName("Test PantryItem with maximum notes length")
    void testPantryItemWithMaxNotesLength() {
        // Given
        UUID id = UUID.randomUUID();
        Product product = Product.builder().id(UUID.randomUUID()).name("Test Product").build();
        Location location = Location.builder().id(UUID.randomUUID()).name("Test Location").build();
        String maxNotes = "A".repeat(500); // Maximum allowed length

        // When
        PantryItem pantryItem = PantryItem.builder()
                .id(id)
                .product(product)
                .location(location)
                .notes(maxNotes)
                .build();

        // Then
        assertThat(pantryItem.getNotes()).isEqualTo(maxNotes);
        assertThat(pantryItem.getNotes()).hasSize(500);
    }

    @Test
    @DisplayName("Test PantryItem with past expiration date")
    void testPantryItemWithPastExpirationDate() {
        // Given
        UUID id = UUID.randomUUID();
        Product product = Product.builder().id(UUID.randomUUID()).name("Test Product").build();
        Location location = Location.builder().id(UUID.randomUUID()).name("Test Location").build();
        LocalDate pastDate = LocalDate.now().minusDays(5);

        // When
        PantryItem pantryItem = PantryItem.builder()
                .id(id)
                .product(product)
                .location(location)
                .expirationDate(pastDate)
                .build();

        // Then
        assertThat(pantryItem.getExpirationDate()).isEqualTo(pastDate);
        assertThat(pantryItem.getExpirationDate()).isBefore(LocalDate.now());
    }

    @Test
    @DisplayName("Test PantryItem with future expiration date")
    void testPantryItemWithFutureExpirationDate() {
        // Given
        UUID id = UUID.randomUUID();
        Product product = Product.builder().id(UUID.randomUUID()).name("Test Product").build();
        Location location = Location.builder().id(UUID.randomUUID()).name("Test Location").build();
        LocalDate futureDate = LocalDate.now().plusDays(30);

        // When
        PantryItem pantryItem = PantryItem.builder()
                .id(id)
                .product(product)
                .location(location)
                .expirationDate(futureDate)
                .build();

        // Then
        assertThat(pantryItem.getExpirationDate()).isEqualTo(futureDate);
        assertThat(pantryItem.getExpirationDate()).isAfter(LocalDate.now());
    }
}
