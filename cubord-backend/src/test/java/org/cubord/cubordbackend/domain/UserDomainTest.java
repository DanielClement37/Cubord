package org.cubord.cubordbackend.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UserDomainTest {

    @Test
    void testUserBuilder() {
        // Given
        UUID id = UUID.randomUUID();
        String username = "testuser";
        String email = "test@example.com";
        String displayName = "Test User";

        // When
        User user = User.builder()
                .id(id)
                .username(username)
                .email(email)
                .displayName(displayName)
                .householdMembers(new HashSet<>())
                .build();

        // Then
        assertThat(user.getId()).isEqualTo(id);
        assertThat(user.getUsername()).isEqualTo(username);
        assertThat(user.getEmail()).isEqualTo(email);
        assertThat(user.getDisplayName()).isEqualTo(displayName);
        assertThat(user.getHouseholdMembers()).isEmpty();
    }

    @Test
    void testNoArgsConstructor() {
        // When
        User user = new User();

        // Then
        assertThat(user).isNotNull();
    }

    @Test
    void testAllArgsConstructor() {
        // Given
        UUID id = UUID.randomUUID();
        String username = "testuser";
        String email = "test@example.com";
        String displayName = "Test User";
        HashSet<HouseholdMember> members = new HashSet<>();
        LocalDateTime createdAt = LocalDateTime.now();
        LocalDateTime updatedAt = LocalDateTime.now();

        // When
        User user = new User(id, username, email, displayName, members, createdAt, updatedAt);

        // Then
        assertThat(user.getId()).isEqualTo(id);
        assertThat(user.getUsername()).isEqualTo(username);
        assertThat(user.getEmail()).isEqualTo(email);
        assertThat(user.getDisplayName()).isEqualTo(displayName);
        assertThat(user.getHouseholdMembers()).isSameAs(members);
        assertThat(user.getCreatedAt()).isEqualTo(createdAt);
        assertThat(user.getUpdatedAt()).isEqualTo(updatedAt);
    }

    @Test
    void testEqualsAndHashCode() {
        // Given
        UUID id = UUID.randomUUID();
        
        User user1 = User.builder()
                .id(id)
                .username("user1")
                .email("user1@example.com")
                .build();
        
        User user2 = User.builder()
                .id(id)  // Same ID
                .username("user2")  // Different username
                .email("user2@example.com")  // Different email
                .build();

        User user3 = User.builder()
                .id(UUID.randomUUID())  // Different ID
                .username("user1")  // Same username
                .email("user1@example.com")  // Same email
                .build();

        // Then - Lombok's @Data provides equals/hashCode based on all fields
        assertThat(user1).isEqualTo(user1);  // Same instance
        assertThat(user1).isNotEqualTo(user2);  // Same ID but different fields
        assertThat(user1).isNotEqualTo(user3);  // Different ID
        
        assertThat(user1.hashCode()).isNotEqualTo(user2.hashCode());
        assertThat(user1.hashCode()).isNotEqualTo(user3.hashCode());
    }
}