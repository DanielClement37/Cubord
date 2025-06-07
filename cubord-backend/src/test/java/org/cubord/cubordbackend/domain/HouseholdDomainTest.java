package org.cubord.cubordbackend.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mockStatic;

@ExtendWith(MockitoExtension.class)
class HouseholdDomainTest {

    @Test
    void testHouseholdBuilder() {
        // Given
        UUID id = UUID.randomUUID();
        String name = "Test Household";
        Set<Location> locations = new HashSet<>();
        Set<HouseholdMember> members = new HashSet<>();

        // When
        Household household = Household.builder()
                .id(id)
                .name(name)
                .locations(locations)
                .members(members)
                .build();

        // Then
        assertThat(household.getId()).isEqualTo(id);
        assertThat(household.getName()).isEqualTo(name);
        assertThat(household.getLocations()).isSameAs(locations);
        assertThat(household.getMembers()).isSameAs(members);
        assertThat(household.getCreatedAt()).isNull();
        assertThat(household.getUpdatedAt()).isNull();
    }
    
    @Test
    void testHouseholdBuilderWithNullCollections() {
        // Given
        UUID id = UUID.randomUUID();
        String name = "Test Household";

        // When
        Household household = Household.builder()
                .id(id)
                .name(name)
                .build();

        // Then
        assertThat(household.getId()).isEqualTo(id);
        assertThat(household.getName()).isEqualTo(name);
        assertThat(household.getLocations()).isNull();
        assertThat(household.getMembers()).isNull();
    }

    @Test
    void testNoArgsConstructor() {
        // When
        Household household = new Household();

        // Then
        assertThat(household).isNotNull();
        // Verify collections are null and not initialized
        assertThat(household.getLocations()).isNull();
        assertThat(household.getMembers()).isNull();
    }

    @Test
    void testAllArgsConstructor() {
        // Given
        UUID id = UUID.randomUUID();
        String name = "Test Household";
        Set<Location> locations = new HashSet<>();
        Set<HouseholdMember> members = new HashSet<>();
        LocalDateTime createdAt = LocalDateTime.now();
        LocalDateTime updatedAt = LocalDateTime.now();

        // When
        Household household = new Household(id, name, locations, members, createdAt, updatedAt);

        // Then
        assertThat(household.getId()).isEqualTo(id);
        assertThat(household.getName()).isEqualTo(name);
        assertThat(household.getLocations()).isSameAs(locations);
        assertThat(household.getMembers()).isSameAs(members);
        assertThat(household.getCreatedAt()).isEqualTo(createdAt);
        assertThat(household.getUpdatedAt()).isEqualTo(updatedAt);
    }

    @Test
    void testOnCreate() {
        // Given
        Household household = new Household();

        // When
        household.onCreate();

        // Then
        assertThat(household.getCreatedAt()).isNotNull();
        assertThat(household.getUpdatedAt()).isNotNull();
        assertThat(household.getCreatedAt()).isEqualToIgnoringNanos(household.getUpdatedAt());
    }

    @Test
    void testOnUpdate() {
        // Given
        Household household = new Household();
        LocalDateTime fixedCreatedTime = LocalDateTime.of(2025, 1, 1, 12, 0);
        LocalDateTime fixedUpdatedTime = LocalDateTime.of(2025, 1, 1, 12, 30);
        
        try (MockedStatic<LocalDateTime> mockedStatic = mockStatic(LocalDateTime.class)) {
            // First call for onCreate
            mockedStatic.when(LocalDateTime::now).thenReturn(fixedCreatedTime);
            household.onCreate();
            
            // Verify createdAt and updatedAt are both set to fixedCreatedTime
            assertThat(household.getCreatedAt()).isEqualTo(fixedCreatedTime);
            assertThat(household.getUpdatedAt()).isEqualTo(fixedCreatedTime);
            
            // Now mock a later time for onUpdate
            mockedStatic.when(LocalDateTime::now).thenReturn(fixedUpdatedTime);
            
            // When
            household.onUpdate();
            
            // Then
            assertThat(household.getCreatedAt()).isEqualTo(fixedCreatedTime); // Should not change
            assertThat(household.getUpdatedAt()).isEqualTo(fixedUpdatedTime); // Should be updated
        }
    }
    
    @Test
    void testCollectionInitialization() {
        // Given
        Household household = new Household();
        
        // Initialize null collections
        if (household.getLocations() == null) {
            household.setLocations(new HashSet<>());
        }
        if (household.getMembers() == null) {
            household.setMembers(new HashSet<>());
        }
        
        // Then
        assertThatCode(() -> {
            household.getLocations().add(new Location());
            household.getMembers().add(new HouseholdMember());
        }).doesNotThrowAnyException();
    }
    
    @Test
    void testBidirectionalRelationship() {
        // Given
        Household household = new Household();
        household.setId(UUID.randomUUID());
        household.setName("Test Household");
        household.setLocations(new HashSet<>());
        household.setMembers(new HashSet<>());
        
        Location location = new Location();
        location.setId(UUID.randomUUID());
        location.setName("Test Location");
        
        // When - establish bidirectional relationship
        location.setHousehold(household);
        household.getLocations().add(location);
        
        // Then
        assertThat(household.getLocations()).contains(location);
        assertThat(location.getHousehold()).isSameAs(household);
    }

    @Test
    void testEqualsAndHashCodeWithLombok() {
        // Given
        UUID id = UUID.randomUUID();
        
        // Create two households with the same ID but different names
        Household household1 = Household.builder()
                .id(id)
                .name("Home 1")
                .build();
        
        Household household2 = Household.builder()
                .id(id)
                .name("Home 2")
                .build();
        
        // Create a third household with different ID
        Household household3 = Household.builder()
                .id(UUID.randomUUID())
                .name("Home 1")
                .build();

        // Then - with @EqualsAndHashCode(of = "id"), only ID matters for equality
        assertThat(household1).isEqualTo(household1);  // Same instance
        assertThat(household1).isEqualTo(household2);  // Same ID, so should be equal despite different name
        assertThat(household1).isNotEqualTo(household3);  // Different ID
        
        assertThat(household1.hashCode()).isEqualTo(household2.hashCode());  // Same ID, so same hash
        assertThat(household1.hashCode()).isNotEqualTo(household3.hashCode());  // Different ID
    }
    
    @Test
    void testAddLocationToHousehold() {
        // Given
        Household household = new Household();
        household.setId(UUID.randomUUID());
        household.setLocations(new HashSet<>());
        
        Location location = new Location();
        location.setId(UUID.randomUUID());
        
        // When
        household.getLocations().add(location);
        location.setHousehold(household);
        
        // Then
        assertThat(household.getLocations()).hasSize(1);
        assertThat(household.getLocations()).contains(location);
        assertThat(location.getHousehold()).isSameAs(household);
    }
    
    @Test
    void testAddMemberToHousehold() {
        // Given
        Household household = new Household();
        household.setId(UUID.randomUUID());
        household.setMembers(new HashSet<>());
        
        User user = new User();
        user.setId(UUID.randomUUID());
        
        HouseholdMember member = new HouseholdMember();
        member.setId(UUID.randomUUID());
        member.setUser(user);
        
        // When
        household.getMembers().add(member);
        member.setHousehold(household);
        
        // Then
        assertThat(household.getMembers()).hasSize(1);
        assertThat(household.getMembers()).contains(member);
        assertThat(member.getHousehold()).isSameAs(household);
    }
}