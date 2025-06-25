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
class HouseholdMemberDomainTest {

    @Test
    @DisplayName("Test HouseholdMember builder")
    void testHouseholdMemberBuilder() {
        // Given
        UUID id = UUID.randomUUID();
        User user = new User();
        Household household = new Household();
        HouseholdRole role = HouseholdRole.MEMBER;
        LocalDateTime createdAt = LocalDateTime.now();
        LocalDateTime updatedAt = LocalDateTime.now();

        // When
        HouseholdMember member = HouseholdMember.builder()
                .id(id)
                .user(user)
                .household(household)
                .role(role)
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .build();

        // Then
        assertThat(member.getId()).isEqualTo(id);
        assertThat(member.getUser()).isSameAs(user);
        assertThat(member.getHousehold()).isSameAs(household);
        assertThat(member.getRole()).isEqualTo(role);
        assertThat(member.getCreatedAt()).isEqualTo(createdAt);
        assertThat(member.getUpdatedAt()).isEqualTo(updatedAt);
    }

    @Test
    @DisplayName("Test HouseholdMember builder with null fields")
    void testHouseholdMemberBuilderWithNullFields() {
        // Given
        UUID id = UUID.randomUUID();
        HouseholdRole role = HouseholdRole.ADMIN;

        // When
        HouseholdMember member = HouseholdMember.builder()
                .id(id)
                .role(role)
                .build();

        // Then
        assertThat(member.getId()).isEqualTo(id);
        assertThat(member.getUser()).isNull();
        assertThat(member.getHousehold()).isNull();
        assertThat(member.getRole()).isEqualTo(role);
        assertThat(member.getCreatedAt()).isNull();
        assertThat(member.getUpdatedAt()).isNull();
    }

    @Test
    @DisplayName("Test no-args constructor")
    void testNoArgsConstructor() {
        // When
        HouseholdMember member = new HouseholdMember();

        // Then
        assertThat(member).isNotNull();
        assertThat(member.getId()).isNull();
        assertThat(member.getUser()).isNull();
        assertThat(member.getHousehold()).isNull();
        assertThat(member.getRole()).isNull();
        assertThat(member.getCreatedAt()).isNull();
        assertThat(member.getUpdatedAt()).isNull();
    }

    @Test
    @DisplayName("Test all-args constructor")
    void testAllArgsConstructor() {
        // Given
        UUID id = UUID.randomUUID();
        User user = new User();
        Household household = new Household();
        HouseholdRole role = HouseholdRole.OWNER;
        LocalDateTime createdAt = LocalDateTime.now();
        LocalDateTime updatedAt = LocalDateTime.now();

        // When
        HouseholdMember member = new HouseholdMember(id, user, household, role, createdAt, updatedAt);

        // Then
        assertThat(member.getId()).isEqualTo(id);
        assertThat(member.getUser()).isSameAs(user);
        assertThat(member.getHousehold()).isSameAs(household);
        assertThat(member.getRole()).isEqualTo(role);
        assertThat(member.getCreatedAt()).isEqualTo(createdAt);
        assertThat(member.getUpdatedAt()).isEqualTo(updatedAt);
    }

    @Test
    @DisplayName("Test onCreate lifecycle callback")
    void testOnCreate() {
        // Given
        HouseholdMember member = new HouseholdMember();

        // When
        member.onCreate();

        // Then
        assertThat(member.getCreatedAt()).isNotNull();
        assertThat(member.getUpdatedAt()).isNotNull();
        assertThat(member.getCreatedAt()).isEqualToIgnoringNanos(member.getUpdatedAt());
    }

    @Test
    @DisplayName("Test onUpdate lifecycle callback")
    void testOnUpdate() {
        // Given
        HouseholdMember member = new HouseholdMember();
        LocalDateTime fixedCreatedTime = LocalDateTime.of(2025, 1, 1, 12, 0);
        LocalDateTime fixedUpdatedTime = LocalDateTime.of(2025, 1, 1, 12, 30);
        
        try (MockedStatic<LocalDateTime> mockedStatic = mockStatic(LocalDateTime.class)) {
            // First call for onCreate
            mockedStatic.when(LocalDateTime::now).thenReturn(fixedCreatedTime);
            member.onCreate();
            
            // Verify createdAt and updatedAt are both set to fixedCreatedTime
            assertThat(member.getCreatedAt()).isEqualTo(fixedCreatedTime);
            assertThat(member.getUpdatedAt()).isEqualTo(fixedCreatedTime);
            
            // Now mock a later time for onUpdate
            mockedStatic.when(LocalDateTime::now).thenReturn(fixedUpdatedTime);
            
            // When
            member.onUpdate();
            
            // Then
            assertThat(member.getCreatedAt()).isEqualTo(fixedCreatedTime); // Should not change
            assertThat(member.getUpdatedAt()).isEqualTo(fixedUpdatedTime); // Should be updated
        }
    }

    @Test
    @DisplayName("Test equals and hashCode")
    void testEqualsAndHashCode() {
        // Given
        UUID id = UUID.randomUUID();
        
        HouseholdMember member1 = HouseholdMember.builder()
                .id(id)
                .user(new User())
                .household(new Household())
                .role(HouseholdRole.MEMBER)
                .build();
        
        HouseholdMember member2 = HouseholdMember.builder()
                .id(id) // Same ID
                .user(new User()) // Different User object
                .household(new Household()) // Different Household object
                .role(HouseholdRole.ADMIN) // Different role
                .build();

        HouseholdMember member3 = HouseholdMember.builder()
                .id(UUID.randomUUID()) // Different ID
                .user(member1.getUser()) // Same User object
                .household(member1.getHousehold()) // Same Household object
                .role(HouseholdRole.MEMBER) // Same role
                .build();

        // Then - With @EqualsAndHashCode(of = "id"), only ID matters for equality
        assertThat(member1).isEqualTo(member1); // Same instance
        assertThat(member1).isEqualTo(member2); // Same ID, so should be equal
        assertThat(member1).isNotEqualTo(member3); // Different ID
        
        assertThat(member1.hashCode()).isEqualTo(member2.hashCode()); // Same ID, so same hash
        assertThat(member1.hashCode()).isNotEqualTo(member3.hashCode()); // Different ID
    }

    @Test
    @DisplayName("Test toString method excludes user and household")
    void testToStringExcludesUserAndHousehold() {
        // Given
        UUID id = UUID.randomUUID();
        User user = User.builder().id(UUID.randomUUID()).username("testuser").build();
        Household household = Household.builder().id(UUID.randomUUID()).name("Test Household").build();
        
        HouseholdMember member = HouseholdMember.builder()
                .id(id)
                .user(user)
                .household(household)
                .role(HouseholdRole.MEMBER)
                .build();
                
        // When
        String result = member.toString();
        
        // Then
        assertThat(result).contains(id.toString());
        assertThat(result).contains("MEMBER");
        assertThat(result).doesNotContain("testuser"); // User should be excluded
        assertThat(result).doesNotContain("Test Household"); // Household should be excluded
    }

    @Test
    @DisplayName("Test setters")
    void testSetters() {
        // Given
        HouseholdMember member = new HouseholdMember();
        UUID id = UUID.randomUUID();
        User user = new User();
        Household household = new Household();
        HouseholdRole role = HouseholdRole.ADMIN;
        LocalDateTime createdAt = LocalDateTime.now();
        LocalDateTime updatedAt = LocalDateTime.now();
        
        // When
        member.setId(id);
        member.setUser(user);
        member.setHousehold(household);
        member.setRole(role);
        member.setCreatedAt(createdAt);
        member.setUpdatedAt(updatedAt);
        
        // Then
        assertThat(member.getId()).isEqualTo(id);
        assertThat(member.getUser()).isSameAs(user);
        assertThat(member.getHousehold()).isSameAs(household);
        assertThat(member.getRole()).isEqualTo(role);
        assertThat(member.getCreatedAt()).isEqualTo(createdAt);
        assertThat(member.getUpdatedAt()).isEqualTo(updatedAt);
    }
}