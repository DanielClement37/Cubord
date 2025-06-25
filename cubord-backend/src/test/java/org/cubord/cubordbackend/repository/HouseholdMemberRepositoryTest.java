package org.cubord.cubordbackend.repository;

import org.cubord.cubordbackend.domain.Household;
import org.cubord.cubordbackend.domain.HouseholdMember;
import org.cubord.cubordbackend.domain.HouseholdRole;
import org.cubord.cubordbackend.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class HouseholdMemberRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private HouseholdMemberRepository householdMemberRepository;

    private User user1;
    private User user2;
    private User user3;
    private Household household1;
    private Household household2;
    private HouseholdMember member1;
    private HouseholdMember member2;
    private HouseholdMember member3;
    private HouseholdMember member4;

    @BeforeEach
    void setUp() {
        // Create test users
        user1 = User.builder()
                .id(UUID.randomUUID())
                .username("user1")
                .email("user1@example.com")
                .displayName("User One")
                .build();
        
        user2 = User.builder()
                .id(UUID.randomUUID())
                .username("user2")
                .email("user2@example.com")
                .displayName("User Two")
                .build();
        
        user3 = User.builder()
                .id(UUID.randomUUID())
                .username("user3")
                .email("user3@example.com")
                .displayName("User Three")
                .build();
        
        entityManager.persist(user1);
        entityManager.persist(user2);
        entityManager.persist(user3);
        
        // Create test households
        household1 = Household.builder()
                .id(UUID.randomUUID())
                .name("Household One")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        
        household2 = Household.builder()
                .id(UUID.randomUUID())
                .name("Household Two")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        
        entityManager.persist(household1);
        entityManager.persist(household2);
        
        // Create household members
        member1 = HouseholdMember.builder()
                .id(UUID.randomUUID())
                .user(user1)
                .household(household1)
                .role(HouseholdRole.OWNER)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        
        member2 = HouseholdMember.builder()
                .id(UUID.randomUUID())
                .user(user2)
                .household(household1)
                .role(HouseholdRole.ADMIN)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        
        member3 = HouseholdMember.builder()
                .id(UUID.randomUUID())
                .user(user1)
                .household(household2)
                .role(HouseholdRole.OWNER)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        
        member4 = HouseholdMember.builder()
                .id(UUID.randomUUID())
                .user(user3)
                .household(household2)
                .role(HouseholdRole.MEMBER)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        
        entityManager.persist(member1);
        entityManager.persist(member2);
        entityManager.persist(member3);
        entityManager.persist(member4);
        
        entityManager.flush();
    }

    @Test
    @DisplayName("Should find all members by user ID")
    void shouldFindAllMembersByUserId() {
        // When
        List<HouseholdMember> user1Members = householdMemberRepository.findByUserId(user1.getId());
        List<HouseholdMember> user2Members = householdMemberRepository.findByUserId(user2.getId());
        List<HouseholdMember> user3Members = householdMemberRepository.findByUserId(user3.getId());
        
        // Then
        assertThat(user1Members).hasSize(2);
        assertThat(user1Members).extracting("household.name")
                .containsExactlyInAnyOrder("Household One", "Household Two");
        
        assertThat(user2Members).hasSize(1);
        assertThat(user2Members.getFirst().getHousehold().getName()).isEqualTo("Household One");
        
        assertThat(user3Members).hasSize(1);
        assertThat(user3Members.getFirst().getHousehold().getName()).isEqualTo("Household Two");
    }

    @Test
    @DisplayName("Should find all members by household ID")
    void shouldFindAllMembersByHouseholdId() {
        // When
        List<HouseholdMember> household1Members = householdMemberRepository.findByHouseholdId(household1.getId());
        List<HouseholdMember> household2Members = householdMemberRepository.findByHouseholdId(household2.getId());
        
        // Then
        assertThat(household1Members).hasSize(2);
        assertThat(household1Members).extracting("user.username")
                .containsExactlyInAnyOrder("user1", "user2");
        
        assertThat(household2Members).hasSize(2);
        assertThat(household2Members).extracting("user.username")
                .containsExactlyInAnyOrder("user1", "user3");
    }

    @Test
    @DisplayName("Should find member by household ID and user ID")
    void shouldFindMemberByHouseholdIdAndUserId() {
        // When
        Optional<HouseholdMember> foundMember = householdMemberRepository
                .findByHouseholdIdAndUserId(household1.getId(), user1.getId());
        
        // Then
        assertThat(foundMember).isPresent();
        assertThat(foundMember.get().getRole()).isEqualTo(HouseholdRole.OWNER);
    }

    @Test
    @DisplayName("Should return empty when member not found by household ID and user ID")
    void shouldReturnEmptyWhenMemberNotFoundByHouseholdIdAndUserId() {
        // When
        Optional<HouseholdMember> notFoundMember = householdMemberRepository
                .findByHouseholdIdAndUserId(household1.getId(), user3.getId());
        
        // Then
        assertThat(notFoundMember).isEmpty();
    }

    @Test
    @DisplayName("Should save new household member")
    void shouldSaveNewHouseholdMember() {
        // Given
        User newUser = User.builder()
                .id(UUID.randomUUID())
                .username("newuser")
                .email("newuser@example.com")
                .displayName("New User")
                .build();
        entityManager.persist(newUser);
        
        HouseholdMember newMember = HouseholdMember.builder()
                .id(UUID.randomUUID())
                .user(newUser)
                .household(household1)
                .role(HouseholdRole.MEMBER)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        
        // When
        HouseholdMember savedMember = householdMemberRepository.save(newMember);
        
        // Then
        assertThat(savedMember).isNotNull();
        assertThat(savedMember.getId()).isEqualTo(newMember.getId());
        
        // Verify it can be retrieved
        Optional<HouseholdMember> retrievedMember = householdMemberRepository
                .findByHouseholdIdAndUserId(household1.getId(), newUser.getId());
        assertThat(retrievedMember).isPresent();
        assertThat(retrievedMember.get().getRole()).isEqualTo(HouseholdRole.MEMBER);
    }

    @Test
    @DisplayName("Should delete household member")
    void shouldDeleteHouseholdMember() {
        // Given
        HouseholdMember memberToDelete = member4; // user3 in household2
        
        // When
        householdMemberRepository.delete(memberToDelete);
        
        // Then
        Optional<HouseholdMember> deletedMember = householdMemberRepository
                .findByHouseholdIdAndUserId(household2.getId(), user3.getId());
        assertThat(deletedMember).isEmpty();
        
        // Verify other members still exist
        List<HouseholdMember> remainingMembers = householdMemberRepository.findAll();
        assertThat(remainingMembers).hasSize(3);
    }

    @Test
    @DisplayName("Should update household member role")
    void shouldUpdateHouseholdMemberRole() {
        // Given
        HouseholdMember memberToUpdate = member2; // user2 in household1 with ADMIN role
        memberToUpdate.setRole(HouseholdRole.MEMBER); // Change role to MEMBER
        
        // When
        HouseholdMember updatedMember = householdMemberRepository.save(memberToUpdate);
        
        // Then
        assertThat(updatedMember.getRole()).isEqualTo(HouseholdRole.MEMBER);
        
        // Verify change persisted
        Optional<HouseholdMember> retrievedMember = householdMemberRepository
                .findByHouseholdIdAndUserId(household1.getId(), user2.getId());
        assertThat(retrievedMember).isPresent();
        assertThat(retrievedMember.get().getRole()).isEqualTo(HouseholdRole.MEMBER);
    }

    @Test
    @DisplayName("Should find no members for non-existent user")
    void shouldFindNoMembersForNonExistentUser() {
        // When
        List<HouseholdMember> nonExistentUserMembers = householdMemberRepository
                .findByUserId(UUID.randomUUID());
        
        // Then
        assertThat(nonExistentUserMembers).isEmpty();
    }

    @Test
    @DisplayName("Should find no members for non-existent household")
    void shouldFindNoMembersForNonExistentHousehold() {
        // When
        List<HouseholdMember> nonExistentHouseholdMembers = householdMemberRepository
                .findByHouseholdId(UUID.randomUUID());
        
        // Then
        assertThat(nonExistentHouseholdMembers).isEmpty();
    }
}