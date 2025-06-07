package org.cubord.cubordbackend.repository;

import org.cubord.cubordbackend.domain.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    void testFindByUsername() {
        // Given
        UUID userId = UUID.randomUUID();
        String username = "testuser";
        User user = User.builder()
                .id(userId)
                .username(username)
                .email("test@example.com")
                .displayName("Test User")
                .build();
        
        userRepository.save(user);

        // When
        Optional<User> foundUser = userRepository.findByUsername(username);

        // Then
        assertThat(foundUser).isPresent();
        assertThat(foundUser.get().getId()).isEqualTo(userId);
        assertThat(foundUser.get().getUsername()).isEqualTo(username);
    }

    @Test
    void testFindByEmail() {
        // Given
        UUID userId = UUID.randomUUID();
        String email = "test@example.com";
        User user = User.builder()
                .id(userId)
                .username("testuser")
                .email(email)
                .displayName("Test User")
                .build();
        
        userRepository.save(user);

        // When
        Optional<User> foundUser = userRepository.findByEmail(email);

        // Then
        assertThat(foundUser).isPresent();
        assertThat(foundUser.get().getId()).isEqualTo(userId);
        assertThat(foundUser.get().getEmail()).isEqualTo(email);
    }

    @Test
    void testFindByUsername_NotFound() {
        // When
        Optional<User> foundUser = userRepository.findByUsername("nonexistentuser");

        // Then
        assertThat(foundUser).isEmpty();
    }

    @Test
    void testFindByEmail_NotFound() {
        // When
        Optional<User> foundUser = userRepository.findByEmail("nonexistent@example.com");

        // Then
        assertThat(foundUser).isEmpty();
    }

    @Test
    void testCrudOperations() {
        // Create
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .id(userId)
                .username("cruduser")
                .email("crud@example.com")
                .displayName("CRUD User")
                .build();
        
        User savedUser = userRepository.save(user);
        assertThat(savedUser).isNotNull();
        assertThat(savedUser.getId()).isEqualTo(userId);

        // Read
        Optional<User> foundUser = userRepository.findById(userId);
        assertThat(foundUser).isPresent();
        assertThat(foundUser.get().getUsername()).isEqualTo("cruduser");

        // Update
        foundUser.get().setDisplayName("Updated CRUD User");
        User updatedUser = userRepository.save(foundUser.get());
        assertThat(updatedUser.getDisplayName()).isEqualTo("Updated CRUD User");

        // Delete
        userRepository.delete(updatedUser);
        assertThat(userRepository.findById(userId)).isEmpty();
    }
}