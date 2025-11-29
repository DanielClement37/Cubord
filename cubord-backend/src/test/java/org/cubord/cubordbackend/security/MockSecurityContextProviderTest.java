package org.cubord.cubordbackend.security;

import org.junit.jupiter.api.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@DisplayName("MockSecurityContextProvider Tests")
class MockSecurityContextProviderTest {

    private MockSecurityContextProvider mockProvider;

    @BeforeEach
    void setUp() {
        mockProvider = new MockSecurityContextProvider();
    }

    @Nested
    @DisplayName("authenticateAs")
    class AuthenticateAsTests {

        @Test
        @DisplayName("authenticates with user ID only")
        void authenticateWithUserId() {
            // Given
            UUID userId = UUID.randomUUID();
            
            // When
            mockProvider.authenticateAs(userId);
            
            // Then
            assertThat(mockProvider.isAuthenticated()).isTrue();
            assertThat(mockProvider.getAuthenticatedName()).isEqualTo(userId.toString());
        }

        @Test
        @DisplayName("authenticates with user details")
        void authenticateWithDetails() {
            // Given
            UUID userId = UUID.randomUUID();
            String email = "custom@example.com";
            String name = "Custom Name";
            
            // When
            mockProvider.authenticateAs(userId, email, name);
            
            // Then
            assertThat(mockProvider.isAuthenticated()).isTrue();
            JwtAuthenticationToken auth = (JwtAuthenticationToken) mockProvider.getAuthentication();
            assertThat(auth.getToken().getSubject()).isEqualTo(userId.toString());
            assertThat(auth.getToken().getClaimAsString("email")).isEqualTo(email);
            assertThat(auth.getToken().getClaimAsString("name")).isEqualTo(name);
        }

        @Test
        @DisplayName("authenticates with custom claims")
        void authenticateWithCustomClaims() {
            // Given
            UUID userId = UUID.randomUUID();
            Map<String, Object> claims = new HashMap<>();
            claims.put("email", "test@example.com");
            claims.put("role", "ADMIN");
            claims.put("custom_claim", "custom_value");
            
            // When
            mockProvider.authenticateAs(userId, claims);
            
            // Then
            assertThat(mockProvider.isAuthenticated()).isTrue();
            JwtAuthenticationToken auth = (JwtAuthenticationToken) mockProvider.getAuthentication();
            assertThat(auth.getToken().getClaimAsString("role")).isEqualTo("ADMIN");
            assertThat(auth.getToken().getClaimAsString("custom_claim")).isEqualTo("custom_value");
        }
    }

    @Nested
    @DisplayName("clear")
    class ClearTests {

        @Test
        @DisplayName("clears authentication")
        void clearsAuth() {
            // Given
            mockProvider.authenticateAs(UUID.randomUUID());
            assertThat(mockProvider.isAuthenticated()).isTrue();
            
            // When
            mockProvider.clear();
            
            // Then
            assertThat(mockProvider.isAuthenticated()).isFalse();
            assertThat(mockProvider.getAuthentication()).isNull();
        }

        @Test
        @DisplayName("can authenticate again after clear")
        void canReauthenticate() {
            // Given
            UUID firstUserId = UUID.randomUUID();
            UUID secondUserId = UUID.randomUUID();
            
            mockProvider.authenticateAs(firstUserId);
            mockProvider.clear();
            
            // When
            mockProvider.authenticateAs(secondUserId);
            
            // Then
            assertThat(mockProvider.isAuthenticated()).isTrue();
            assertThat(mockProvider.getAuthenticatedName()).isEqualTo(secondUserId.toString());
        }
    }

    @Nested
    @DisplayName("setAuthentication")
    class SetAuthenticationTests {

        @Test
        @DisplayName("sets custom authentication")
        void setsCustomAuth() {
            // Given
            UUID userId = UUID.randomUUID();
            JwtAuthenticationToken customAuth = TestSecurityUtils.createJwtAuth(userId);
            
            // When
            mockProvider.setAuthentication(customAuth);
            
            // Then
            assertThat(mockProvider.getAuthentication()).isEqualTo(customAuth);
            assertThat(mockProvider.isAuthenticated()).isTrue();
        }

        @Test
        @DisplayName("can set null authentication")
        void setsNullAuth() {
            // Given
            mockProvider.authenticateAs(UUID.randomUUID());
            
            // When
            mockProvider.setAuthentication(null);
            
            // Then
            assertThat(mockProvider.getAuthentication()).isNull();
            assertThat(mockProvider.isAuthenticated()).isFalse();
        }
    }
}
