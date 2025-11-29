package org.cubord.cubordbackend.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Utility class for creating test authentication objects.
 * 
 * <p>Provides factory methods for creating JWT tokens and authentication
 * objects commonly needed in tests.</p>
 */
public final class TestSecurityUtils {

    private TestSecurityUtils() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Creates a basic JWT authentication token for testing.
     * 
     * <p>Creates a token with standard claims:</p>
     * <ul>
     *   <li>sub: userId</li>
     *   <li>email: {userId}@test.com</li>
     *   <li>name: "Test User"</li>
     *   <li>iat: now</li>
     *   <li>exp: now + 1 hour</li>
     * </ul>
     *
     * @param userId The user ID to use as the subject
     * @return A JwtAuthenticationToken for the user
     */
    public static JwtAuthenticationToken createJwtAuth(UUID userId) {
        return createJwtAuth(userId, userId + "@test.com", "Test User");
    }

    /**
     * Creates a JWT authentication token with custom user details.
     *
     * @param userId The user ID to use as the subject
     * @param email The user's email address
     * @param name The user's display name
     * @return A JwtAuthenticationToken for the user
     */
    public static JwtAuthenticationToken createJwtAuth(UUID userId, String email, String name) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("email", email);
        claims.put("name", name);
        return createJwtAuth(userId, claims);
    }

    /**
     * Creates a JWT authentication token with custom claims.
     *
     * @param userId The user ID to use as the subject
     * @param additionalClaims Additional claims to include in the JWT
     * @return A JwtAuthenticationToken for the user
     */
    public static JwtAuthenticationToken createJwtAuth(UUID userId, Map<String, Object> additionalClaims) {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(3600); // 1 hour
        
        Jwt.Builder builder = Jwt.withTokenValue("test-token-" + userId)
                .header("alg", "HS256")
                .header("typ", "JWT")
                .subject(userId.toString())
                .issuedAt(now)
                .expiresAt(expiry);
        
        // Add all custom claims
        additionalClaims.forEach(builder::claim);
        
        Jwt jwt = builder.build();
        
        // Create authorities from role claim if present, otherwise use default
        List<GrantedAuthority> authorities = extractAuthorities(additionalClaims);
        
        // Use a 3-argument constructor which automatically marks the token as authenticated
        return new JwtAuthenticationToken(jwt, authorities);
    }

    /**
     * Creates a JWT authentication token with a role claim.
     *
     * @param userId The user ID to use as the subject
     * @param role The role to assign (e.g., "USER", "ADMIN")
     * @return A JwtAuthenticationToken with the specified role
     */
    public static JwtAuthenticationToken createJwtAuthWithRole(UUID userId, String role) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("email", userId + "@test.com");
        claims.put("name", "Test User");
        claims.put("role", role);
        return createJwtAuth(userId, claims);
    }

    /**
     * Creates an expired JWT authentication token for testing expiration scenarios.
     *
     * @param userId The user ID to use as the subject
     * @return A JwtAuthenticationToken that has already expired
     */
    public static JwtAuthenticationToken createExpiredJwtAuth(UUID userId) {
        Instant now = Instant.now();
        Instant past = now.minusSeconds(3600); // 1 hour ago
        
        Jwt jwt = Jwt.withTokenValue("expired-token-" + userId)
                .header("alg", "HS256")
                .subject(userId.toString())
                .claim("email", userId + "@test.com")
                .issuedAt(past)
                .expiresAt(now.minusSeconds(1)) // Expired 1 second ago
                .build();
    
        // Use 3-argument constructor to ensure token is marked as authenticated
        List<GrantedAuthority> authorities = Collections.singletonList(
                new SimpleGrantedAuthority("ROLE_USER"));
        return new JwtAuthenticationToken(jwt, authorities);
    }

    /**
     * Creates a JWT with minimal claims (only subject).
     * Useful for testing error handling of incomplete tokens.
     *
     * @param userId The user ID to use as the subject
     * @return A JwtAuthenticationToken with minimal claims
     */
    public static JwtAuthenticationToken createMinimalJwtAuth(UUID userId) {
        Jwt jwt = Jwt.withTokenValue("minimal-token")
                .header("alg", "none")
                .subject(userId.toString())
                .build();
    
        // Use 3-argument constructor to ensure token is marked as authenticated
        List<GrantedAuthority> authorities = Collections.emptyList();
        return new JwtAuthenticationToken(jwt, authorities);
    }

    /**
     * Creates a mock SecurityContextProvider that returns the given authentication.
     *
     * @param auth The authentication to return
     * @return A SecurityContextProvider that returns the specified authentication
     */
    public static SecurityContextProvider mockProvider(JwtAuthenticationToken auth) {
        return () -> auth;
    }

    /**
     * Creates a mock SecurityContextProvider that returns null (unauthenticated).
     *
     * @return A SecurityContextProvider representing an unauthenticated state
     */
    public static SecurityContextProvider unauthenticatedProvider() {
        return () -> null;
    }
    
    /**
     * Extracts authorities from a claims map.
     * If a "role" claim exists, creates a ROLE_ prefixed authority.
     * Otherwise, returns a default ROLE_USER authority.
     *
     * @param claims The claims map
     * @return List of granted authorities
     */
    private static List<GrantedAuthority> extractAuthorities(Map<String, Object> claims) {
        if (claims.containsKey("role")) {
            String role = claims.get("role").toString();
            // Add ROLE_ prefix if not already present
            String authority = role.startsWith("ROLE_") ? role : "ROLE_" + role;
            return Collections.singletonList(new SimpleGrantedAuthority(authority));
        }
        // Default authority for authenticated users
        return Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"));
    }
}
