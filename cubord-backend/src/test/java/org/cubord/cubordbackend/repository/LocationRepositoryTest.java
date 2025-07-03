package org.cubord.cubordbackend.repository;

import org.cubord.cubordbackend.domain.Household;
import org.cubord.cubordbackend.domain.Location;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class LocationRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private LocationRepository locationRepository;

    @Autowired
    private HouseholdRepository householdRepository;

    private Household testHousehold1;
    private Household testHousehold2;
    private Location testLocation1;
    private Location testLocation2;
    private Location testLocation3;

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

        // Persist households
        testHousehold1 = entityManager.persistAndFlush(testHousehold1);
        testHousehold2 = entityManager.persistAndFlush(testHousehold2);

        // Create test locations
        testLocation1 = Location.builder()
                .id(UUID.randomUUID())
                .name("Kitchen")
                .description("Main kitchen storage")
                .household(testHousehold1)
                .build();

        testLocation2 = Location.builder()
                .id(UUID.randomUUID())
                .name("Pantry")
                .description("Food storage area")
                .household(testHousehold1)
                .build();

        testLocation3 = Location.builder()
                .id(UUID.randomUUID())
                .name("Garage")
                .description("Vehicle storage")
                .household(testHousehold2)
                .build();

        // Persist locations
        testLocation1 = entityManager.persistAndFlush(testLocation1);
        testLocation2 = entityManager.persistAndFlush(testLocation2);
        testLocation3 = entityManager.persistAndFlush(testLocation3);
        
        entityManager.clear();
    }

    @Test
    @DisplayName("Test save location")
    void testSaveLocation() {
        // Given
        Location newLocation = Location.builder()
                .id(UUID.randomUUID())
                .name("Basement")
                .description("Underground storage")
                .household(testHousehold1)
                .build();

        // When
        Location savedLocation = locationRepository.save(newLocation);

        // Then
        assertThat(savedLocation).isNotNull();
        assertThat(savedLocation.getId()).isEqualTo(newLocation.getId());
        assertThat(savedLocation.getName()).isEqualTo("Basement");
        assertThat(savedLocation.getDescription()).isEqualTo("Underground storage");
        assertThat(savedLocation.getHousehold().getId()).isEqualTo(testHousehold1.getId());
        assertThat(savedLocation.getCreatedAt()).isNotNull();
        assertThat(savedLocation.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("Test find by ID")
    void testFindById() {
        // When
        Optional<Location> found = locationRepository.findById(testLocation1.getId());

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(testLocation1.getId());
        assertThat(found.get().getName()).isEqualTo("Kitchen");
        assertThat(found.get().getDescription()).isEqualTo("Main kitchen storage");
    }

    @Test
    @DisplayName("Test find by ID not found")
    void testFindByIdNotFound() {
        // Given
        UUID nonExistentId = UUID.randomUUID();

        // When
        Optional<Location> found = locationRepository.findById(nonExistentId);

        // Then
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("Test find all locations")
    void testFindAll() {
        // When
        List<Location> locations = locationRepository.findAll();

        // Then
        assertThat(locations).hasSize(3);
        assertThat(locations).extracting(Location::getName)
                .containsExactlyInAnyOrder("Kitchen", "Pantry", "Garage");
    }

    @Test
    @DisplayName("Test find all with pagination")
    void testFindAllWithPagination() {
        // Given
        Pageable pageable = PageRequest.of(0, 2, Sort.by("name"));

        // When
        Page<Location> page = locationRepository.findAll(pageable);

        // Then
        assertThat(page.getContent()).hasSize(2);
        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getTotalPages()).isEqualTo(2);
        assertThat(page.getContent()).extracting(Location::getName)
                .containsExactly("Garage", "Kitchen"); // Sorted alphabetically
    }

    @Test
    @DisplayName("Test find all with sorting")
    void testFindAllWithSorting() {
        // Given
        Sort sort = Sort.by(Sort.Direction.DESC, "name");

        // When
        List<Location> locations = locationRepository.findAll(sort);

        // Then
        assertThat(locations).hasSize(3);
        assertThat(locations).extracting(Location::getName)
                .containsExactly("Pantry", "Kitchen", "Garage"); // Sorted descending
    }

    @Test
    @DisplayName("Test update location")
    void testUpdateLocation() {
        // Given
        Location locationToUpdate = locationRepository.findById(testLocation1.getId()).orElseThrow();
        String newName = "Updated Kitchen";
        String newDescription = "Updated description";

        // When
        locationToUpdate.setName(newName);
        locationToUpdate.setDescription(newDescription);
        Location updatedLocation = locationRepository.save(locationToUpdate);

        // Then
        assertThat(updatedLocation.getName()).isEqualTo(newName);
        assertThat(updatedLocation.getDescription()).isEqualTo(newDescription);
        assertThat(updatedLocation.getUpdatedAt()).isAfter(updatedLocation.getCreatedAt());
    }

    @Test
    @DisplayName("Test delete location")
    void testDeleteLocation() {
        // Given
        UUID locationId = testLocation1.getId();

        // When
        locationRepository.deleteById(locationId);

        // Then
        Optional<Location> deletedLocation = locationRepository.findById(locationId);
        assertThat(deletedLocation).isEmpty();
        
        // Verify other locations still exist
        List<Location> remainingLocations = locationRepository.findAll();
        assertThat(remainingLocations).hasSize(2);
    }

    @Test
    @DisplayName("Test delete location by entity")
    void testDeleteLocationByEntity() {
        // Given
        Location locationToDelete = locationRepository.findById(testLocation2.getId()).orElseThrow();

        // When
        locationRepository.delete(locationToDelete);

        // Then
        Optional<Location> deletedLocation = locationRepository.findById(testLocation2.getId());
        assertThat(deletedLocation).isEmpty();
        
        // Verify other locations still exist
        List<Location> remainingLocations = locationRepository.findAll();
        assertThat(remainingLocations).hasSize(2);
    }

    @Test
    @DisplayName("Test exists by ID")
    void testExistsById() {
        // When & Then
        assertThat(locationRepository.existsById(testLocation1.getId())).isTrue();
        assertThat(locationRepository.existsById(UUID.randomUUID())).isFalse();
    }

    @Test
    @DisplayName("Test count locations")
    void testCountLocations() {
        // When
        long count = locationRepository.count();

        // Then
        assertThat(count).isEqualTo(3);
    }

    @Test
    @DisplayName("Test save and flush")
    void testSaveAndFlush() {
        // Given
        Location newLocation = Location.builder()
                .id(UUID.randomUUID())
                .name("Attic")
                .description("Overhead storage")
                .household(testHousehold1)
                .build();

        // When
        Location savedLocation = locationRepository.saveAndFlush(newLocation);

        // Then
        assertThat(savedLocation).isNotNull();
        assertThat(savedLocation.getId()).isEqualTo(newLocation.getId());
        
        // Verify it's immediately available in the database
        Optional<Location> foundLocation = locationRepository.findById(savedLocation.getId());
        assertThat(foundLocation).isPresent();
    }

    @Test
    @DisplayName("Test save all locations")
    void testSaveAllLocations() {
        // Given
        Location location1 = Location.builder()
                .id(UUID.randomUUID())
                .name("Closet")
                .description("Clothing storage")
                .household(testHousehold1)
                .build();

        Location location2 = Location.builder()
                .id(UUID.randomUUID())
                .name("Shed")
                .description("Outdoor storage")
                .household(testHousehold2)
                .build();

        List<Location> locationsToSave = List.of(location1, location2);

        // When
        List<Location> savedLocations = locationRepository.saveAll(locationsToSave);

        // Then
        assertThat(savedLocations).hasSize(2);
        assertThat(savedLocations).extracting(Location::getName)
                .containsExactly("Closet", "Shed");
        
        // Verify total count
        long totalCount = locationRepository.count();
        assertThat(totalCount).isEqualTo(5); // 3 original + 2 new
    }

    @Test
    @DisplayName("Test find all by IDs")
    void testFindAllByIds() {
        // Given
        List<UUID> ids = List.of(testLocation1.getId(), testLocation3.getId());

        // When
        List<Location> locations = locationRepository.findAllById(ids);

        // Then
        assertThat(locations).hasSize(2);
        assertThat(locations).extracting(Location::getName)
                .containsExactlyInAnyOrder("Kitchen", "Garage");
    }

    @Test
    @DisplayName("Test find all by empty IDs list")
    void testFindAllByEmptyIds() {
        // Given
        List<UUID> emptyIds = List.of();

        // When
        List<Location> locations = locationRepository.findAllById(emptyIds);

        // Then
        assertThat(locations).isEmpty();
    }

    @Test
    @DisplayName("Test delete all locations")
    void testDeleteAllLocations() {
        // When
        locationRepository.deleteAll();

        // Then
        long count = locationRepository.count();
        assertThat(count).isEqualTo(0);
    }

    @Test
    @DisplayName("Test delete all by entities")
    void testDeleteAllByEntities() {
        // Given
        List<Location> locationsToDelete = List.of(testLocation1, testLocation2);

        // When
        locationRepository.deleteAll(locationsToDelete);

        // Then
        long count = locationRepository.count();
        assertThat(count).isEqualTo(1); // Only testLocation3 should remain
        
        Optional<Location> remainingLocation = locationRepository.findById(testLocation3.getId());
        assertThat(remainingLocation).isPresent();
        assertThat(remainingLocation.get().getName()).isEqualTo("Garage");
    }

    @Test
    @DisplayName("Test delete all by IDs")
    void testDeleteAllByIds() {
        // Given
        List<UUID> idsToDelete = List.of(testLocation1.getId(), testLocation2.getId());

        // When
        locationRepository.deleteAllById(idsToDelete);

        // Then
        long count = locationRepository.count();
        assertThat(count).isEqualTo(1); // Only testLocation3 should remain
        
        Optional<Location> remainingLocation = locationRepository.findById(testLocation3.getId());
        assertThat(remainingLocation).isPresent();
        assertThat(remainingLocation.get().getName()).isEqualTo("Garage");
    }

    @Test
    @DisplayName("Test location with minimal data")
    void testLocationWithMinimalData() {
        // Given
        Location minimalLocation = Location.builder()
                .id(UUID.randomUUID())
                .name("Minimal")
                .household(testHousehold1)
                .build(); // No description

        // When
        Location savedLocation = locationRepository.save(minimalLocation);

        // Then
        assertThat(savedLocation).isNotNull();
        assertThat(savedLocation.getName()).isEqualTo("Minimal");
        assertThat(savedLocation.getDescription()).isNull();
        assertThat(savedLocation.getHousehold()).isNotNull();
        assertThat(savedLocation.getCreatedAt()).isNotNull();
        assertThat(savedLocation.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("Test location lifecycle callbacks")
    void testLocationLifecycleCallbacks() {
        // Given
        Location newLocation = Location.builder()
                .id(UUID.randomUUID())
                .name("Callback Test")
                .description("Testing lifecycle callbacks")
                .household(testHousehold1)
                .build();

        // When - Save (triggers @PrePersist)
        Location savedLocation = locationRepository.save(newLocation);
        LocalDateTime initialCreatedAt = savedLocation.getCreatedAt();
        LocalDateTime initialUpdatedAt = savedLocation.getUpdatedAt();

        // Then
        assertThat(initialCreatedAt).isNotNull();
        assertThat(initialUpdatedAt).isNotNull();
        assertThat(initialCreatedAt).isEqualToIgnoringNanos(initialUpdatedAt);

        // When - Update (triggers @PreUpdate)
        savedLocation.setName("Updated Name");
        Location updatedLocation = locationRepository.save(savedLocation);

        // Then
        assertThat(updatedLocation.getCreatedAt()).isEqualTo(initialCreatedAt); // Should not change
        assertThat(updatedLocation.getUpdatedAt()).isAfter(initialUpdatedAt); // Should be updated
    }

    @Test
    @DisplayName("Test location with household relationship")
    void testLocationWithHouseholdRelationship() {
        // Given
        Location location = locationRepository.findById(testLocation1.getId()).orElseThrow();

        // When
        Household household = location.getHousehold();

        // Then
        assertThat(household).isNotNull();
        assertThat(household.getId()).isEqualTo(testHousehold1.getId());
        assertThat(household.getName()).isEqualTo("Test Household 1");
    }

    @Test
    @DisplayName("Test location equals and hashCode in persistence context")
    void testLocationEqualsAndHashCodeInPersistenceContext() {
        // Given
        Location location1 = locationRepository.findById(testLocation1.getId()).orElseThrow();
        Location location2 = locationRepository.findById(testLocation1.getId()).orElseThrow();

        // When & Then
        assertThat(location1).isEqualTo(location2); // Same ID
        assertThat(location1.hashCode()).isEqualTo(location2.hashCode());
    }

    @Test
    @DisplayName("Test location toString in persistence context")
    void testLocationToStringInPersistenceContext() {
        // Given
        Location location = locationRepository.findById(testLocation1.getId()).orElseThrow();

        // When
        String toString = location.toString();

        // Then
        assertThat(toString).contains("Kitchen");
        assertThat(toString).contains("Main kitchen storage");
        assertThat(toString).contains(testLocation1.getId().toString());
        // Household should be excluded from toString due to @ToString(exclude = "household")
        assertThat(toString).doesNotContain("Test Household 1");
    }
}
