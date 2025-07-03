
package org.cubord.cubordbackend.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

@ExtendWith(MockitoExtension.class)
class LocationDomainTest {

    @Test
    @DisplayName("Test Location builder")
    void testLocationBuilder() {
        // Given
        UUID id = UUID.randomUUID();
        String name = "Kitchen";
        String description = "Main kitchen storage";
        Household household = new Household();
        LocalDateTime createdAt = LocalDateTime.now();
        LocalDateTime updatedAt = LocalDateTime.now();

        // When
        Location location = Location.builder()
                .id(id)
                .name(name)
                .description(description)
                .household(household)
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .build();

        // Then
        assertThat(location.getId()).isEqualTo(id);
        assertThat(location.getName()).isEqualTo(name);
        assertThat(location.getDescription()).isEqualTo(description);
        assertThat(location.getHousehold()).isSameAs(household);
        assertThat(location.getCreatedAt()).isEqualTo(createdAt);
        assertThat(location.getUpdatedAt()).isEqualTo(updatedAt);
    }

    @Test
    @DisplayName("Test Location builder with null fields")
    void testLocationBuilderWithNullFields() {
        // Given
        UUID id = UUID.randomUUID();
        String name = "Pantry";

        // When
        Location location = Location.builder()
                .id(id)
                .name(name)
                .build();

        // Then
        assertThat(location.getId()).isEqualTo(id);
        assertThat(location.getName()).isEqualTo(name);
        assertThat(location.getDescription()).isNull();
        assertThat(location.getHousehold()).isNull();
        assertThat(location.getCreatedAt()).isNull();
        assertThat(location.getUpdatedAt()).isNull();
    }

    @Test
    @DisplayName("Test no-args constructor")
    void testNoArgsConstructor() {
        // When
        Location location = new Location();

        // Then
        assertThat(location).isNotNull();
        assertThat(location.getId()).isNull();
        assertThat(location.getName()).isNull();
        assertThat(location.getDescription()).isNull();
        assertThat(location.getHousehold()).isNull();
        assertThat(location.getCreatedAt()).isNull();
        assertThat(location.getUpdatedAt()).isNull();
    }

    @Test
    @DisplayName("Test all-args constructor")
    void testAllArgsConstructor() {
        // Given
        UUID id = UUID.randomUUID();
        String name = "Refrigerator";
        String description = "Cold storage unit";
        Household household = new Household();
        LocalDateTime createdAt = LocalDateTime.now();
        LocalDateTime updatedAt = LocalDateTime.now();

        // When
        Location location = new Location(id, name, description, household, createdAt, updatedAt);

        // Then
        assertThat(location.getId()).isEqualTo(id);
        assertThat(location.getName()).isEqualTo(name);
        assertThat(location.getDescription()).isEqualTo(description);
        assertThat(location.getHousehold()).isSameAs(household);
        assertThat(location.getCreatedAt()).isEqualTo(createdAt);
        assertThat(location.getUpdatedAt()).isEqualTo(updatedAt);
    }

    @Test
    @DisplayName("Test onCreate lifecycle callback")
    void testOnCreate() {
        // Given
        Location location = new Location();

        // When
        location.onCreate();

        // Then
        assertThat(location.getCreatedAt()).isNotNull();
        assertThat(location.getUpdatedAt()).isNotNull();
        assertThat(location.getCreatedAt()).isEqualToIgnoringNanos(location.getUpdatedAt());
    }

    @Test
    @DisplayName("Test onUpdate lifecycle callback")
    void testOnUpdate() {
        // Given
        Location location = new Location();
        LocalDateTime fixedCreatedTime = LocalDateTime.of(2025, 1, 1, 12, 0);
        LocalDateTime fixedUpdatedTime = LocalDateTime.of(2025, 1, 1, 12, 30);
        
        try (MockedStatic<LocalDateTime> mockedStatic = mockStatic(LocalDateTime.class)) {
            // First call for onCreate
            mockedStatic.when(LocalDateTime::now).thenReturn(fixedCreatedTime);
            location.onCreate();
            
            // Verify createdAt and updatedAt are both set to fixedCreatedTime
            assertThat(location.getCreatedAt()).isEqualTo(fixedCreatedTime);
            assertThat(location.getUpdatedAt()).isEqualTo(fixedCreatedTime);
            
            // Now mock a later time for onUpdate
            mockedStatic.when(LocalDateTime::now).thenReturn(fixedUpdatedTime);
            
            // When
            location.onUpdate();
            
            // Then
            assertThat(location.getCreatedAt()).isEqualTo(fixedCreatedTime); // Should not change
            assertThat(location.getUpdatedAt()).isEqualTo(fixedUpdatedTime); // Should be updated
        }
    }

    @Test
    @DisplayName("Test equals and hashCode")
    void testEqualsAndHashCode() {
        // Given
        UUID id = UUID.randomUUID();
        
        Location location1 = Location.builder()
                .id(id)
                .name("Kitchen")
                .description("Main kitchen")
                .household(new Household())
                .build();
        
        Location location2 = Location.builder()
                .id(id) // Same ID
                .name("Pantry") // Different name
                .description("Food storage") // Different description
                .household(new Household()) // Different household object
                .build();

        Location location3 = Location.builder()
                .id(UUID.randomUUID()) // Different ID
                .name("Kitchen") // Same name
                .description("Main kitchen") // Same description
                .household(location1.getHousehold()) // Same household object
                .build();

        // Then - With @EqualsAndHashCode(of = "id"), only ID matters for equality
        assertThat(location1).isEqualTo(location1); // Same instance
        assertThat(location1).isEqualTo(location2); // Same ID, so should be equal
        assertThat(location1).isNotEqualTo(location3); // Different ID
        
        assertThat(location1.hashCode()).isEqualTo(location2.hashCode()); // Same ID, so same hash
        assertThat(location1.hashCode()).isNotEqualTo(location3.hashCode()); // Different ID
    }

    @Test
    @DisplayName("Test toString method excludes household")
    void testToStringExcludesHousehold() {
        // Given
        UUID id = UUID.randomUUID();
        String name = "Freezer";
        String description = "Frozen food storage";
        Household household = Household.builder()
                .id(UUID.randomUUID())
                .name("Test Household")
                .build();
        
        Location location = Location.builder()
                .id(id)
                .name(name)
                .description(description)
                .household(household)
                .build();
                
        // When
        String result = location.toString();
        
        // Then
        assertThat(result).contains(id.toString());
        assertThat(result).contains(name);
        assertThat(result).contains(description);
        assertThat(result).doesNotContain("Test Household"); // Household should be excluded
    }

    @Test
    @DisplayName("Test setters")
    void testSetters() {
        // Given
        Location location = new Location();
        UUID id = UUID.randomUUID();
        String name = "Garage";
        String description = "Vehicle storage area";
        Household household = new Household();
        LocalDateTime createdAt = LocalDateTime.now();
        LocalDateTime updatedAt = LocalDateTime.now();
        
        // When
        location.setId(id);
        location.setName(name);
        location.setDescription(description);
        location.setHousehold(household);
        location.setCreatedAt(createdAt);
        location.setUpdatedAt(updatedAt);
        
        // Then
        assertThat(location.getId()).isEqualTo(id);
        assertThat(location.getName()).isEqualTo(name);
        assertThat(location.getDescription()).isEqualTo(description);
        assertThat(location.getHousehold()).isSameAs(household);
        assertThat(location.getCreatedAt()).isEqualTo(createdAt);
        assertThat(location.getUpdatedAt()).isEqualTo(updatedAt);
    }

    @Test
    @DisplayName("Test getters")
    void testGetters() {
        // Given
        UUID id = UUID.randomUUID();
        String name = "Basement";
        String description = "Underground storage";
        Household household = new Household();
        LocalDateTime createdAt = LocalDateTime.now();
        LocalDateTime updatedAt = LocalDateTime.now();
        
        Location location = Location.builder()
                .id(id)
                .name(name)
                .description(description)
                .household(household)
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .build();
        
        // When & Then
        assertThat(location.getId()).isEqualTo(id);
        assertThat(location.getName()).isEqualTo(name);
        assertThat(location.getDescription()).isEqualTo(description);
        assertThat(location.getHousehold()).isSameAs(household);
        assertThat(location.getCreatedAt()).isEqualTo(createdAt);
        assertThat(location.getUpdatedAt()).isEqualTo(updatedAt);
    }

    @Test
    @DisplayName("Test Location with empty strings")
    void testLocationWithEmptyStrings() {
        // Given
        UUID id = UUID.randomUUID();
        String name = "";
        String description = "";
        
        // When
        Location location = Location.builder()
                .id(id)
                .name(name)
                .description(description)
                .build();
        
        // Then
        assertThat(location.getId()).isEqualTo(id);
        assertThat(location.getName()).isEmpty();
        assertThat(location.getDescription()).isEmpty();
    }

    @Test
    @DisplayName("Test Location with null ID")
    void testLocationWithNullId() {
        // Given
        String name = "Storage Room";
        String description = "General storage";
        
        // When
        Location location = Location.builder()
                .name(name)
                .description(description)
                .build();
        
        // Then
        assertThat(location.getId()).isNull();
        assertThat(location.getName()).isEqualTo(name);
        assertThat(location.getDescription()).isEqualTo(description);
    }

    @Test
    @DisplayName("Test Location equals with null")
    void testLocationEqualsWithNull() {
        // Given
        Location location = Location.builder()
                .id(UUID.randomUUID())
                .name("Test Location")
                .build();
        
        // When & Then
        assertThat(location).isNotEqualTo(null);
        assertThat(location.equals(null)).isFalse();
    }

    @Test
    @DisplayName("Test Location equals with different type")
    void testLocationEqualsWithDifferentType() {
        // Given
        Location location = Location.builder()
                .id(UUID.randomUUID())
                .name("Test Location")
                .build();
        
        String notALocation = "Not a location";
        
        // When & Then
        assertThat(location).isNotEqualTo(notALocation);
        assertThat(location.equals(notALocation)).isFalse();
    }

    @Test
    @DisplayName("Test Location equals with null ID")
    void testLocationEqualsWithNullId() {
        // Given
        Location location1 = Location.builder()
                .name("Test Location 1")
                .build(); // No ID set, so it's null
        
        Location location2 = Location.builder()
                .name("Test Location 2")
                .build(); // No ID set, so it's null
        
        Location location3 = Location.builder()
                .id(UUID.randomUUID())
                .name("Test Location 3")
                .build();
        
        // When & Then
        assertThat(location1).isEqualTo(location2); // Both have null IDs
        assertThat(location1).isNotEqualTo(location3); // One has null ID, other doesn't
        assertThat(location1.hashCode()).isEqualTo(location2.hashCode()); // Both null IDs have same hash
    }
}
