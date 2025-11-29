package org.cubord.cubordbackend.security;

import org.springframework.security.core.Authentication;

/**
 * Abstraction for accessing the Spring Security context.
 * 
 * <p>This interface exists primarily to improve testability by decoupling
 * components from the static {@link org.springframework.security.core.context.SecurityContextHolder}.
 * In production, this delegates to Spring's SecurityContextHolder. In tests,
 * it can be easily mocked or stubbed.</p>
 * 
 * <h2>Usage in Production Code:</h2>
 * <pre>{@code
 * @Service
 * public class MyService {
 *     private final SecurityContextProvider securityContextProvider;
 *     
 *     public void doSomething() {
 *         Authentication auth = securityContextProvider.getAuthentication();
 *         if (auth != null && auth.isAuthenticated()) {
 *             // Process authenticated user
 *         }
 *     }
 * }
 * }</pre>
 * 
 * <h2>Usage in Tests:</h2>
 * <pre>{@code
 * @Mock
 * private SecurityContextProvider securityContextProvider;
 * 
 * @Test
 * void testAuthenticated() {
 *     when(securityContextProvider.getAuthentication())
 *         .thenReturn(mockAuthentication);
 *     // Test your service...
 * }
 * }</pre>
 *
 * @see org.springframework.security.core.context.SecurityContextHolder
 */
public interface SecurityContextProvider {
    
    /**
     * Gets the current authentication from the security context.
     * 
     * <p>Returns the {@link Authentication} object representing the currently
     * authenticated principal, or {@code null} if no authentication is present.</p>
     *
     * @return The current Authentication, or null if not authenticated
     */
    Authentication getAuthentication();
    
    /**
     * Checks if there is an authenticated user in the current context.
     * 
     * <p>A user is considered authenticated if:</p>
     * <ul>
     *   <li>An authentication object exists</li>
     *   <li>The authentication is marked as authenticated</li>
     * </ul>
     * 
     * <p>This is a convenience method equivalent to:</p>
     * <pre>{@code
     * Authentication auth = getAuthentication();
     * return auth != null && auth.isAuthenticated();
     * }</pre>
     *
     * @return true if there is an authenticated user, false otherwise
     */
    default boolean isAuthenticated() {
        Authentication auth = getAuthentication();
        return auth != null && auth.isAuthenticated();
    }
    
    /**
     * Gets the name of the currently authenticated principal.
     * 
     * <p>This is typically the username or user ID. Returns null if not authenticated.</p>
     * 
     * <p>This is a convenience method equivalent to:</p>
     * <pre>{@code
     * Authentication auth = getAuthentication();
     * return auth != null ? auth.getName() : null;
     * }</pre>
     *
     * @return The name of the authenticated principal, or null if not authenticated
     */
    default String getAuthenticatedName() {
        Authentication auth = getAuthentication();
        return auth != null ? auth.getName() : null;
    }
}
