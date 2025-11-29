package org.cubord.cubordbackend.security;

import org.junit.jupiter.api.*;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@DisplayName("TestSecurityUtils Tests")
class TestSecurityUtilsTest {

    @Nested
    @DisplayName("createJwtAuth")
    class CreateJwtAuthTests {

        @Test
        @DisplayName("creates basic JWT auth with user ID")
        void createsBasicJwtAuth() {
            // Given
            UUID userId = UUID.randomUUID();
            
            // When
            JwtAuthenticationToken auth = TestSecurityUtils.createJwtAuth(userId);
            
            // Then
            assertThat(auth).isNotNull();
            assertThat(auth.isAuthenticated()).isTrue();
            assertThat(auth.getName()).isEqualTo(userId.toString());
            
            Jwt jwt = auth.getToken();
            assertThat(jwt.getSubject()).isEqualTo(userId.toString());
            assertThat(jwt.getClaimAsString("email")).isEqualTo(userId + "@test.com");
            assertThat(jwt.getClaimAsString("name")).isEqualTo("Test User");
        }

        @Test
        @DisplayName("creates JWT auth with custom details")
        void createsJwtAuthWithDetails() {
            // Given
            UUID userId = UUID.randomUUID();
            String email = "custom@example.com";
            String name = "Custom User";
            
            // When
            JwtAuthenticationToken auth = TestSecurityUtils.createJwtAuth(userId, email, name);
            
            // Then
            Jwt jwt = auth.getToken();
            assertThat(jwt.getClaimAsString("email")).isEqualTo(email);
            assertThat(jwt.getClaimAsString("name")).isEqualTo(name);
        }

        @Test
        @DisplayName("creates JWT auth with custom claims")
        void createsJwtAuthWithClaims() {
            // Given
            UUID userId = UUID.randomUUID();
            Map<String, Object> claims = new HashMap<>();
            claims.put("role", "ADMIN");
            claims.put("organization", "test-org");
            
            // When
            JwtAuthenticationToken auth = TestSecurityUtils.createJwtAuth(userId, claims);
            
            // Then
            Jwt jwt = auth.getToken();
            assertThat(jwt.getClaimAsString("role")).isEqualTo("ADMIN");
            assertThat(jwt.getClaimAsString("organization")).isEqualTo("test-org");
        }

        @Test
        @DisplayName("creates JWT auth with role")
        void createsJwtAuthWithRole() {
            // Given
            UUID userId = UUID.randomUUID();
            
            // When
            JwtAuthenticationToken auth = TestSecurityUtils.createJwtAuthWithRole(userId, "ADMIN");
            
            // Then
            Jwt jwt = auth.getToken();
            assertThat(jwt.getClaimAsString("role")).isEqualTo("ADMIN");
        }

        @Test
        @DisplayName("creates JWT with valid timestamps")
        void createsJwtWithTimestamps() {
            // Given
            UUID userId = UUID.randomUUID();
            Instant before = Instant.now();
            
            // When
            JwtAuthenticationToken auth = TestSecurityUtils.createJwtAuth(userId);
            
            // Then
            Jwt jwt = auth.getToken();
            Instant issuedAt = jwt.getIssuedAt();
            Instant expiresAt = jwt.getExpiresAt();
            
            assertThat(issuedAt).isNotNull();
            assertThat(expiresAt).isNotNull();
            assertThat(issuedAt).isAfterOrEqualTo(before);
            assertThat(expiresAt).isAfter(issuedAt);
        }
    }

    @Nested
    @DisplayName("createExpiredJwtAuth")
    class CreateExpiredJwtAuthTests {

        @Test
        @DisplayName("creates expired JWT")
        void createsExpiredJwt() {
            // Given
            UUID userId = UUID.randomUUID();
            
            // When
            JwtAuthenticationToken auth = TestSecurityUtils.createExpiredJwtAuth(userId);
            
            // Then
            Jwt jwt = auth.getToken();
            assertThat(jwt.getExpiresAt()).isBefore(Instant.now());
        }
    }

    @Nested
    @DisplayName("createMinimalJwtAuth")
    class CreateMinimalJwtAuthTests {

        @Test
        @DisplayName("creates JWT with minimal claims")
        void createsMinimalJwt() {
            // Given
            UUID userId = UUID.randomUUID();
            
            // When
            JwtAuthenticationToken auth = TestSecurityUtils.createMinimalJwtAuth(userId);
            
            // Then
            Jwt jwt = auth.getToken();
            assertThat(jwt.getSubject()).isEqualTo(userId.toString());
            assertThat(jwt.getClaims()).hasSize(1); // Only subject
        }
    }

    @Nested
    @DisplayName("Provider Factories")
    class ProviderFactoriesTests {

        @Test
        @DisplayName("mockProvider returns configured auth")
        void mockProviderWorks() {
            // Given
            UUID userId = UUID.randomUUID();
            JwtAuthenticationToken auth = TestSecurityUtils.createJwtAuth(userId);
            
            // When
            SecurityContextProvider provider = TestSecurityUtils.mockProvider(auth);
            
            // Then
            assertThat(provider.getAuthentication()).isEqualTo(auth);
            assertThat(provider.isAuthenticated()).isTrue();
        }

        @Test
        @DisplayName("unauthenticatedProvider returns null")
        void unauthenticatedProviderWorks() {
            // When
            SecurityContextProvider provider = TestSecurityUtils.unauthenticatedProvider();
            
            // Then
            assertThat(provider.getAuthentication()).isNull();
            assertThat(provider.isAuthenticated()).isFalse();
        }
    }

    @Test
    @DisplayName("cannot instantiate utility class")
    void cannotInstantiate() {
        assertThatThrownBy(() -> {
            var constructor = TestSecurityUtils.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            constructor.newInstance();
        }).hasCauseInstanceOf(UnsupportedOperationException.class);
    }
}
