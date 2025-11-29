package org.cubord.cubordbackend.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Mock implementation of {@link SecurityContextProvider} for testing.
 * 
 * <p>This class allows tests to easily control the authentication state
 * without manipulating Spring's SecurityContextHolder directly.</p>
 * 
 * <h2>Usage Example:</h2>
 * <pre>{@code
 * @Test
 * void testWithAuthentication() {
 *     // Given
 *     MockSecurityContextProvider mockProvider = new MockSecurityContextProvider();
 *     mockProvider.authenticateAs(userId);
 *     
 *     MyService service = new MyService(repository, mockProvider);
 *     
 *     // When
 *     service.doSomething();
 *     
 *     // Then - assertions
 * }
 * }</pre>
 *
 * @see SecurityContextProvider
 */
public class MockSecurityContextProvider implements SecurityContextProvider {
    
    private Authentication authentication;
    
    /**
     * Gets the configured authentication.
     *
     * @return The mock authentication, or null if not set
     */
    @Override
    public Authentication getAuthentication() {
        return authentication;
    }
    
    /**
     * Directly sets the authentication object.
     *
     * @param authentication The authentication to return from {@link #getAuthentication()}
     */
    public void setAuthentication(Authentication authentication) {
        this.authentication = authentication;
    }
    
    /**
     * Creates and sets a JWT authentication for the specified user ID.
     * 
     * <p>Creates a valid JWT token with standard claims (email, name) and
     * wraps it in a {@link JwtAuthenticationToken}.</p>
     *
     * @param userId The user ID to authenticate as
     */
    public void authenticateAs(UUID userId) {
        authenticateAs(userId, userId + "@test.com", "Test User");
    }
    
    /**
     * Creates and sets a JWT authentication with custom user details.
     *
     * @param userId The user ID to authenticate as
     * @param email The user's email address
     * @param name The user's display name
     */
    public void authenticateAs(UUID userId, String email, String name) {
        this.authentication = TestSecurityUtils.createJwtAuth(userId, email, name);
    }
    
    /**
     * Creates and sets a JWT authentication with custom claims.
     *
     * @param userId The user ID to authenticate as
     * @param additionalClaims Additional JWT claims to include
     */
    public void authenticateAs(UUID userId, Map<String, Object> additionalClaims) {
        Map<String, Object> claims = new HashMap<>(additionalClaims);
        claims.putIfAbsent("email", userId + "@test.com");
        claims.putIfAbsent("name", "Test User");
        
        this.authentication = TestSecurityUtils.createJwtAuth(userId, claims);
    }
    
    /**
     * Clears the authentication, simulating an unauthenticated state.
     */
    public void clear() {
        this.authentication = null;
    }
    
    /**
     * Checks if authentication is currently set.
     *
     * @return true if authentication is set and authenticated
     */
    @Override
    public boolean isAuthenticated() {
        return authentication != null && authentication.isAuthenticated();
    }
}
