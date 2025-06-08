package org.cubord.cubordbackend.repository;

import org.cubord.cubordbackend.domain.Household;
import org.cubord.cubordbackend.domain.HouseholdMember;
import org.cubord.cubordbackend.domain.HouseholdRole;
import org.cubord.cubordbackend.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class HouseholdRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private HouseholdRepository householdRepository;

    private User user1;
    private User user3;
    private Household household1;
    private Household household2;
    private Household household3;
    private HouseholdMember member1;
    private HouseholdMember member2;
    private HouseholdMember member3;
    
    // Fixed dates to prevent flaky tests
    private final LocalDateTime now = LocalDateTime.of(2025, 6, 5, 12, 0);
    private final LocalDateTime yesterday = now.minusDays(1);
    private final LocalDateTime twoDaysAgo = now.minusDays(2);

    @BeforeEach
    void setUp() {
        // Create test users with correct fields matching User entity
        user1 = User.builder()
                .id(UUID.randomUUID())
                .username("johndoe")
                .email("user1@example.com")
                .displayName("John Doe")
                .householdMembers(new HashSet<>())
                .build();

        User user2 = User.builder()
                .id(UUID.randomUUID())
                .username("janesmith")
                .email("user2@example.com")
                .displayName("Jane Smith")
                .householdMembers(new HashSet<>())
                .build();
        
        user3 = User.builder()
                .id(UUID.randomUUID())
                .username("sarahjohnson")
                .email("user3@example.com")
                .displayName("Sarah Johnson")
                .householdMembers(new HashSet<>())
                .build();
        
        entityManager.persist(user1);
        entityManager.persist(user2);
        entityManager.persist(user3);
        
        // Create test households
        household1 = Household.builder()
                .id(UUID.randomUUID())
                .name("Smith Family")
                .locations(new HashSet<>())
                .members(new HashSet<>())
                .createdAt(now)
                .updatedAt(now)
                .build();

        household2 = Household.builder()
                .id(UUID.randomUUID())
                .name("Johnson Family")
                .locations(new HashSet<>())
                .members(new HashSet<>())
                .createdAt(yesterday)
                .updatedAt(yesterday)
                .build();

        household3 = Household.builder()
                .id(UUID.randomUUID())
                .name("Smith-Johnson Household")
                .locations(new HashSet<>())
                .members(new HashSet<>())
                .createdAt(twoDaysAgo)
                .updatedAt(twoDaysAgo)
                .build();
        
        entityManager.persist(household1);
        entityManager.persist(household2);
        entityManager.persist(household3);
        
        // Create household members
        member1 = HouseholdMember.builder()
                .id(UUID.randomUUID())
                .user(user1)
                .household(household1)
                .role(HouseholdRole.OWNER)
                .createdAt(now)
                .updatedAt(now)
                .build();

        member2 = HouseholdMember.builder()
                .id(UUID.randomUUID())
                .user(user2)
                .household(household2)
                .role(HouseholdRole.OWNER)
                .createdAt(yesterday)
                .updatedAt(yesterday)
                .build();

        member3 = HouseholdMember.builder()
                .id(UUID.randomUUID())
                .user(user3)
                .household(household3)
                .role(HouseholdRole.MEMBER)
                .createdAt(twoDaysAgo)
                .updatedAt(twoDaysAgo)
                .build();
        
        entityManager.persist(member1);
        entityManager.persist(member2);
        entityManager.persist(member3);
        
        // Set up complete bidirectional relationships
        // Household -> Members
        household1.getMembers().add(member1);
        household2.getMembers().add(member2);
        household3.getMembers().add(member3);
        
        // User -> HouseholdMembers
        user1.getHouseholdMembers().add(member1);
        user2.getHouseholdMembers().add(member2);
        user3.getHouseholdMembers().add(member3);
        
        entityManager.flush();
    }

    @Test
    void findByName_ShouldReturnHousehold_WhenNameExists() {
        // When
        Optional<Household> foundHousehold = householdRepository.findByName("Smith Family");
        
        // Then
        assertThat(foundHousehold).isPresent();
        assertThat(foundHousehold.get().getId()).isEqualTo(household1.getId());
    }
    
    @Test
    void findByName_ShouldReturnEmptyOptional_WhenNameDoesNotExist() {
        // When
        Optional<Household> foundHousehold = householdRepository.findByName("Non-existent Household");
        
        // Then
        assertThat(foundHousehold).isEmpty();
    }
    
    @Test
    void findByNameContainingIgnoreCase_ShouldReturnMatchingHouseholds() {
        // When
        List<Household> foundHouseholds = householdRepository.findByNameContainingIgnoreCase("smith");
        
        // Then
        assertThat(foundHouseholds).hasSize(2);
        assertThat(foundHouseholds).extracting(Household::getName)
                .containsExactlyInAnyOrder("Smith Family", "Smith-Johnson Household");
    }

    @Test
    void findByMembersUserId_ShouldReturnHouseholdsWhereUserIsMember() {
        // When
        List<Household> user1Households = householdRepository.findByMembersUserId(user1.getId());
        
        // Then
        assertThat(user1Households).hasSize(1);
        assertThat(user1Households.getFirst().getName()).isEqualTo("Smith Family");
    }

    @Test
    void navigateFromUserToHousehold_ShouldReturnCorrectHousehold() {
        // When - navigate through bidirectional relationship
        Household household = user1.getHouseholdMembers()
                .stream()
                .findFirst()
                .map(HouseholdMember::getHousehold)
                .orElse(null);
        
        // Then
        assertThat(household).isNotNull();
        assertThat(household.getId()).isEqualTo(household1.getId());
        assertThat(household.getName()).isEqualTo("Smith Family");
    }
    
    @Test
    void existsByName_ShouldReturnTrue_WhenNameExists() {
        // When
        boolean exists = householdRepository.existsByName("Smith Family");
        
        // Then
        assertThat(exists).isTrue();
    }
    
    @Test
    void existsByName_ShouldReturnFalse_WhenNameDoesNotExist() {
        // When
        boolean exists = householdRepository.existsByName("Non-existent Household");
        
        // Then
        assertThat(exists).isFalse();
    }
    
    @Test
    void testHousehold3HasMember() {
        // When
        List<Household> user3Households = householdRepository.findByMembersUserId(user3.getId());
        
        // Then
        assertThat(user3Households).hasSize(1);
        assertThat(user3Households.getFirst().getName()).isEqualTo("Smith-Johnson Household");
    }
    
    @Test
    void deleteHousehold_ShouldCascadeToMembers() {
        // Given
        UUID householdId = household3.getId();
        
        // When
        householdRepository.deleteById(householdId);
        entityManager.flush();
        entityManager.clear();
        
        // Then
        Optional<Household> deletedHousehold = householdRepository.findById(householdId);
        assertThat(deletedHousehold).isEmpty();
        
        // Verify user3 still exists
        User foundUser = entityManager.find(User.class, user3.getId());
        assertThat(foundUser).isNotNull();
        
        // HouseholdMember should be deleted through cascade
        HouseholdMember foundMember = entityManager.find(HouseholdMember.class, member3.getId());
        assertThat(foundMember).isNull();
    }
}