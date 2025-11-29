package org.cubord.cubordbackend.security;

import org.junit.jupiter.api.*;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.Collections;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@DisplayName("SpringSecurityContextProvider Tests")
class SpringSecurityContextProviderTest {

    private SpringSecurityContextProvider provider;

    @BeforeEach
    void setUp() {
        provider = new SpringSecurityContextProvider();
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ==================== getAuthentication Tests ====================

    @Nested
    @DisplayName("getAuthentication")
    class GetAuthenticationTests {

        @Test
        @DisplayName("returns null when no SecurityContext exists")
        void whenNoSecurityContext_returnsNull() {
            // Given - SecurityContextHolder is cleared in setUp()
            
            // When
            Authentication result = provider.getAuthentication();
            
            // Then
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("returns null when SecurityContext has no authentication")
        void whenNoAuthentication_returnsNull() {
            // Given
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            SecurityContextHolder.setContext(context);
            
            // When
            Authentication result = provider.getAuthentication();
            
            // Then
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("returns authentication when set in SecurityContext")
        void whenAuthenticationSet_returnsAuthentication() {
            // Given
            UUID userId = UUID.randomUUID();
            JwtAuthenticationToken expectedAuth = TestSecurityUtils.createJwtAuth(userId);
            setAuthentication(expectedAuth);
            
            // When
            Authentication result = provider.getAuthentication();
            
            // Then
            assertThat(result).isEqualTo(expectedAuth);
            assertThat(result.getName()).isEqualTo(userId.toString());
        }

        @Test
        @DisplayName("returns JWT authentication correctly")
        void whenJwtAuth_returnsCorrectly() {
            // Given
            Jwt jwt = Jwt.withTokenValue("test-token")
                    .header("alg", "HS256")
                    .subject("user-123")
                    .claim("email", "test@example.com")
                    .issuedAt(Instant.now())
                    .expiresAt(Instant.now().plusSeconds(3600))
                    .build();
            JwtAuthenticationToken jwtAuth = new JwtAuthenticationToken(jwt);
            setAuthentication(jwtAuth);
            
            // When
            Authentication result = provider.getAuthentication();
            
            // Then
            assertThat(result).isInstanceOf(JwtAuthenticationToken.class);
            JwtAuthenticationToken jwtResult = (JwtAuthenticationToken) result;
            assertThat(jwtResult.getToken().getSubject()).isEqualTo("user-123");
            assertThat(jwtResult.getToken().getClaimAsString("email")).isEqualTo("test@example.com");
        }

        @Test
        @DisplayName("returns anonymous authentication")
        void whenAnonymousAuth_returnsCorrectly() {
            // Given
            AnonymousAuthenticationToken anonymousAuth = new AnonymousAuthenticationToken(
                    "key", 
                    "anonymous", 
                    Collections.singletonList(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))
            );
            setAuthentication(anonymousAuth);
            
            // When
            Authentication result = provider.getAuthentication();
            
            // Then
            assertThat(result).isInstanceOf(AnonymousAuthenticationToken.class);
            assertThat(result.getName()).isEqualTo("anonymous");
        }
    }

    // ==================== isAuthenticated Tests ====================

    @Nested
    @DisplayName("isAuthenticated")
    class IsAuthenticatedTests {

        @Test
        @DisplayName("returns false when no SecurityContext exists")
        void whenNoSecurityContext_returnsFalse() {
            // Given - SecurityContextHolder is cleared
            
            // When
            boolean result = provider.isAuthenticated();
            
            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("returns false when no authentication in context")
        void whenNoAuthentication_returnsFalse() {
            // Given
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            SecurityContextHolder.setContext(context);
            
            // When
            boolean result = provider.isAuthenticated();
            
            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("returns false when authentication is not authenticated")
        void whenNotAuthenticated_returnsFalse() {
            // Given
            TestingAuthenticationToken auth = new TestingAuthenticationToken(
                    "user", "password"
            );
            auth.setAuthenticated(false);
            setAuthentication(auth);
            
            // When
            boolean result = provider.isAuthenticated();
            
            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("returns true when authentication is authenticated")
        void whenAuthenticated_returnsTrue() {
            // Given
            TestingAuthenticationToken auth = new TestingAuthenticationToken(
                    "user", "password"
            );
            auth.setAuthenticated(true);
            setAuthentication(auth);
            
            // When
            boolean result = provider.isAuthenticated();
            
            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("returns true for valid JWT authentication")
        void whenJwtAuthenticated_returnsTrue() {
            // Given
            UUID userId = UUID.randomUUID();
            JwtAuthenticationToken jwtAuth = TestSecurityUtils.createJwtAuth(userId);
            setAuthentication(jwtAuth);
            
            // When
            boolean result = provider.isAuthenticated();
            
            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("returns true for anonymous authentication")
        void whenAnonymous_returnsTrue() {
            // Given
            AnonymousAuthenticationToken anonymousAuth = new AnonymousAuthenticationToken(
                    "key", 
                    "anonymous", 
                    Collections.singletonList(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))
            );
            setAuthentication(anonymousAuth);
            
            // When
            boolean result = provider.isAuthenticated();
            
            // Then - Anonymous auth is considered "authenticated" by Spring Security
            assertThat(result).isTrue();
        }
    }

    // ==================== getAuthenticatedName Tests ====================

    @Nested
    @DisplayName("getAuthenticatedName")
    class GetAuthenticatedNameTests {

        @Test
        @DisplayName("returns null when not authenticated")
        void whenNotAuthenticated_returnsNull() {
            // Given - no authentication set
            
            // When
            String result = provider.getAuthenticatedName();
            
            // Then
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("returns principal name when authenticated")
        void whenAuthenticated_returnsName() {
            // Given
            UUID userId = UUID.randomUUID();
            JwtAuthenticationToken auth = TestSecurityUtils.createJwtAuth(userId);
            setAuthentication(auth);
            
            // When
            String result = provider.getAuthenticatedName();
            
            // Then
            assertThat(result).isEqualTo(userId.toString());
        }

        @Test
        @DisplayName("returns username from simple authentication")
        void whenSimpleAuth_returnsUsername() {
            // Given
            TestingAuthenticationToken auth = new TestingAuthenticationToken(
                    "testuser", "password"
            );
            auth.setAuthenticated(true);
            setAuthentication(auth);
            
            // When
            String result = provider.getAuthenticatedName();
            
            // Then
            assertThat(result).isEqualTo("testuser");
        }
    }

    // ==================== Thread Safety Tests ====================

    @Nested
    @DisplayName("Thread Safety")
    class ThreadSafetyTests {

        @Test
        @DisplayName("different threads have isolated security contexts")
        void threadIsolation() throws InterruptedException {
            // Given
            UUID userId1 = UUID.randomUUID();
            UUID userId2 = UUID.randomUUID();
            
            // When - Set auth in the main thread
            setAuthentication(TestSecurityUtils.createJwtAuth(userId1));
            
            // Create a new thread and set different auth
            Thread otherThread = new Thread(() -> {
                setAuthentication(TestSecurityUtils.createJwtAuth(userId2));
                
                // Verify in another thread
                Authentication auth = provider.getAuthentication();
                assertThat(auth).isNotNull();
                assertThat(auth.getName()).isEqualTo(userId2.toString());
            });
            
            otherThread.start();
            otherThread.join();
            
            // Then - Main thread should still have original auth
            Authentication mainAuth = provider.getAuthentication();
            assertThat(mainAuth).isNotNull();
            assertThat(mainAuth.getName()).isEqualTo(userId1.toString());
        }
    }

    // ==================== Helper Methods ====================

    private void setAuthentication(Authentication authentication) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
    }
}
