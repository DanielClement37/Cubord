package org.cubord.cubordbackend.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cubord.cubordbackend.domain.HouseholdMember;
import org.cubord.cubordbackend.domain.HouseholdRole;
import org.cubord.cubordbackend.domain.User;
import org.cubord.cubordbackend.domain.UserRole;
import org.cubord.cubordbackend.exception.AuthenticationRequiredException;
import org.cubord.cubordbackend.repository.HouseholdMemberRepository;
import org.cubord.cubordbackend.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Centralized security service for authentication and authorization checks.
 * 
 * <p>This service is the primary entry point for security checks throughout the application.
 * It is designed to be used in two ways:</p>
 * 
 * <ol>
 *   <li><strong>Declarative:</strong> Via SpEL expressions in {@code @PreAuthorize} annotations
 *       <pre>{@code @PreAuthorize("@security.canAccessHousehold(#householdId)")}</pre>
 *   </li>
 *   <li><strong>Programmatic:</strong> Direct injection where complex security logic is needed</li>
 * </ol>
 * 
 * <h2>Design Principles</h2>
 * <ul>
 *   <li>All authorization methods return {@code boolean} - never throw for access denial</li>
 *   <li>Null inputs always result in {@code false} (fail-secure)</li>
 *   <li>Authentication failures return {@code false} rather than propagating exceptions</li>
 *   <li>Logging is consistent: DEBUG for authorization results, WARN for suspicious activity</li>
 * </ul>
 * 
 * <h2>Thread Safety</h2>
 * <p>This service is thread-safe. It relies on {@link SecurityContextProvider} which uses
 * thread-local storage for authentication context.</p>
 *
 * @see HouseholdPermissionEvaluator for integration with Spring Security's hasPermission()
 * @see SecurityContextProvider for authentication context access
 */
@Component("security")
@RequiredArgsConstructor
@Slf4j
public class SecurityService {

    private final UserRepository userRepository;
    private final HouseholdMemberRepository householdMemberRepository;
    private final SecurityContextProvider securityContextProvider;

    /**
     * Roles that grant administrative access to household resources.
     */
    private static final Set<HouseholdRole> ADMIN_ROLES = EnumSet.of(
            HouseholdRole.OWNER, 
            HouseholdRole.ADMIN
    );

    // ==================== Current User Resolution ====================

    /**
     * Gets the currently authenticated user from the security context.
     * 
     * <p>If the user doesn't exist in the database (first-time login via JWT),
     * a new user record is created with basic information from the token.</p>
     *
     * @return The current authenticated user, never null
     * @throws AuthenticationRequiredException if no valid authentication exists
     */
    @Transactional
    public User getCurrentUser() {
        JwtAuthenticationToken jwtToken = getJwtAuthentication();
        UUID userId = extractUserId(jwtToken);
    
        return userRepository.findById(userId)
                .orElseGet(() -> createUserFromToken(jwtToken));
    }

    /**
     * Gets the current user's ID without loading the full user entity.
     * 
     * <p>This is more efficient than {@link #getCurrentUser()} when only the ID is needed,
     * as it avoids a database query.</p>
     *
     * @return The current user's UUID
     * @throws AuthenticationRequiredException if no valid authentication exists
     */
    public UUID getCurrentUserId() {
        return extractUserId(getJwtAuthentication());
    }

    /**
     * Optionally gets the current user's ID if authenticated.
     * 
     * <p>Unlike {@link #getCurrentUserId()}, this method does not throw exceptions.
     * Useful for optional security context checks.</p>
     *
     * @return Optional containing the user ID if authenticated, empty otherwise
     */
    public Optional<UUID> getCurrentUserIdIfPresent() {
        try {
            return Optional.of(getCurrentUserId());
        } catch (AuthenticationRequiredException e) {
            log.trace("No authenticated user when checking for optional user ID", e);
            return Optional.empty();
        }
    }

    /**
     * Checks if there is a valid-authenticated user in the current context.
     *
     * @return true if a user is authenticated with a valid JWT, false otherwise
     */
    public boolean isAuthenticated() {
        Authentication authentication = securityContextProvider.getAuthentication();
        boolean authenticated = authentication != null
                && authentication.isAuthenticated()
                && authentication instanceof JwtAuthenticationToken;
        
        log.trace("Authentication check: {}", authenticated);
        return authenticated;
    }

    // ==================== Household Access Checks ====================

    /**
     * Checks if the current user has any access to the specified household.
     * 
     * <p>A user has access if they are a member with any role (OWNER, ADMIN, or MEMBER).</p>
     *
     * @param householdId The household to check access for
     * @return true if the user is a member of the household
     */
    @Transactional(readOnly = true)
    public boolean canAccessHousehold(UUID householdId) {
        if (householdId == null) {
            log.debug("Household ID is null, denying access");
            return false;
        }

        try {
            UUID userId = getCurrentUserId();
            boolean hasAccess = householdMemberRepository
                    .existsByHouseholdIdAndUserId(householdId, userId);

            log.debug("User {} access to household {}: {}", userId, householdId, hasAccess);
            return hasAccess;
        } catch (AuthenticationRequiredException e) {
            log.debug("No authenticated user, denying household access");
            return false;
        }
    }

    /**
     * Checks if the current user can modify the specified household.
     * 
     * <p>Modification requires an OWNER or ADMIN role. This includes:</p>
     * <ul>
     *   <li>Updating household settings</li>
     *   <li>Managing locations within the household</li>
     *   <li>Managing invitations</li>
     * </ul>
     *
     * @param householdId The household to check modify permission for
     * @return true if the user has modified permission
     */
    @Transactional(readOnly = true)
    public boolean canModifyHousehold(UUID householdId) {
        if (householdId == null) {
            log.debug("Household ID is null, denying modify permission");
            return false;
        }

        try {
            UUID userId = getCurrentUserId();
            Optional<HouseholdMember> member = householdMemberRepository
                    .findByHouseholdIdAndUserId(householdId, userId);

            boolean canModify = member
                    .map(m -> ADMIN_ROLES.contains(m.getRole()))
                    .orElse(false);

            log.debug("User {} modify permission for household {}: {}", userId, householdId, canModify);
            return canModify;
        } catch (AuthenticationRequiredException e) {
            log.debug("No authenticated user, denying modify permission");
            return false;
        }
    }

    /**
     * Checks if the current user is the owner of the specified household.
     * 
     * <p>Owner-only operations include:</p>
     * <ul>
     *   <li>Deleting the household</li>
     *   <li>Transferring ownership</li>
     *   <li>Promoting members to ADMIN</li>
     *   <li>Removing ADMINS</li>
     * </ul>
     *
     * @param householdId The household to check ownership for
     * @return true if the user is the owner
     */
    @Transactional(readOnly = true)
    public boolean isHouseholdOwner(UUID householdId) {
        if (householdId == null) {
            log.debug("Household ID is null, denying owner status");
            return false;
        }

        try {
            UUID userId = getCurrentUserId();
            Optional<HouseholdMember> member = householdMemberRepository
                    .findByHouseholdIdAndUserId(householdId, userId);

            boolean isOwner = member
                    .map(m -> m.getRole() == HouseholdRole.OWNER)
                    .orElse(false);

            log.debug("User {} owner status for household {}: {}", userId, householdId, isOwner);
            return isOwner;
        } catch (AuthenticationRequiredException e) {
            log.debug("No authenticated user, denying owner status");
            return false;
        }
    }

    /**
     * Checks if the current user can manage members in the specified household.
     * Requires OWNER or ADMIN role.
     *
     * @param householdId The household to check member management permission for
     * @return true if the user can manage members
     */
    @Transactional(readOnly = true)
    public boolean canManageHouseholdMembers(UUID householdId) {
        return canModifyHousehold(householdId);
    }

    /**
     * Checks if the current user can send invitations for the specified household.
     * Requires OWNER or ADMIN role.
     *
     * @param householdId The household to check invitation permission for
     * @return true if the user can send invitations
     */
    @Transactional(readOnly = true)
    public boolean canSendHouseholdInvitations(UUID householdId) {
        return canModifyHousehold(householdId);
    }

    // ==================== Location Access Checks ====================

    /**
     * Checks if the current user can access the specified location.
     * 
     * <p>Location access is derived from household membership - any household member
     * can view locations within that household.</p>
     *
     * @param locationId The location ID (currently unused, reserved for future fine-grained control)
     * @param householdId The household that owns the location
     * @return true if the user can access the location
     */
    @Transactional(readOnly = true)
    public boolean canAccessLocation(UUID locationId, UUID householdId) {
        // Currently location access is tied to household access
        // locationId parameter reserved for future location-specific permissions
        return canAccessHousehold(householdId);
    }

    /**
     * Checks if the current user can modify the specified location.
     * Requires OWNER or ADMIN role in the household.
     *
     * @param locationId The location ID (currently unused, reserved for future fine-grained control)
     * @param householdId The household that owns the location
     * @return true if the user can modify the location
     */
    @Transactional(readOnly = true)
    public boolean canModifyLocation(UUID locationId, UUID householdId) {
        return canModifyHousehold(householdId);
    }

    // ==================== Pantry Item Access Checks ====================

    /**
     * Checks if the current user can access pantry items in the specified household.
     *
     * @param householdId The household to check access for
     * @return true if the user can access pantry items
     */
    @Transactional(readOnly = true)
    public boolean canAccessPantryItems(UUID householdId) {
        return canAccessHousehold(householdId);
    }

    /**
     * Checks if the current user can modify pantry items.
     * 
     * <p>All household members can add, update, and remove pantry items.
     * This is intentionally less restrictive than household modification.</p>
     *
     * @param householdId The household that owns the pantry items
     * @return true if the user can modify pantry items
     */
    @Transactional(readOnly = true)
    public boolean canModifyPantryItems(UUID householdId) {
        // All household members can modify pantry items
        return canAccessHousehold(householdId);
    }

    // ==================== User Access Checks ====================

    /**
     * Checks if the current user has administrator privileges.
     * 
     * <p>This check is used for operations that require admin access,
     * such as modifying or deleting system-wide resources like products.</p>
     *
     * @return true if the current user is an admin
     */
    @Transactional(readOnly = true)
    public boolean isAdmin() {
        try {
            User currentUser = getCurrentUser();
            boolean isAdmin = currentUser.getRole() == UserRole.ADMIN;
            
            log.debug("User {} admin status: {}", currentUser.getId(), isAdmin);
            return isAdmin;
        } catch (AuthenticationRequiredException e) {
            log.debug("No authenticated user, denying admin status");
            return false;
        }
    }

    /**
     * Checks if the current user can access another user's profile.

    /**
     * Checks if the current user can access another user's profile.
     * 
     * <p>Access rules:</p>
     * <ul>
     *   <li>Users can always access their own profile</li>
     *   <li>Users can view profiles of others in shared households</li>
     * </ul>
     *
     * @param userId The user ID to check access for
     * @return true if the current user can access the profile
     */
    @Transactional(readOnly = true)
    public boolean canAccessUserProfile(UUID userId) {
        if (userId == null) {
            log.debug("User ID is null, denying profile access");
            return false;
        }

        return getCurrentUserIdIfPresent()
                .map(currentUserId -> {
                    // Users can always access their own profile
                    if (currentUserId.equals(userId)) {
                        log.debug("User {} accessing own profile", currentUserId);
                        return true;
                    }
                    
                    // Users can view profiles of shared household members
                    boolean hasShared = hasSharedHousehold(currentUserId, userId);
                    log.debug("User {} access to profile {}: {} (shared household)", 
                            currentUserId, userId, hasShared);
                    return hasShared;
                })
                .orElse(false);
    }

    /**
     * Checks if the current user can modify another user's profile.
     * Users can only modify their own profile.
     *
     * @param userId The user ID to check modify permission for
     * @return true if the current user can modify the profile
     */
    @Transactional(readOnly = true)
    public boolean canModifyUserProfile(UUID userId) {
        if (userId == null) {
            log.debug("User ID is null, denying profile modification");
            return false;
        }

        return getCurrentUserIdIfPresent()
                .map(currentUserId -> {
                    boolean canModify = currentUserId.equals(userId);
                    log.debug("User {} can modify profile {}: {}", currentUserId, userId, canModify);
                    return canModify;
                })
                .orElse(false);
    }

    // ==================== Invitation Access Checks ====================

    /**
     * Checks if the current user is the invited user for an invitation.
     *
     * @param invitedUserId The invited user's ID
     * @return true if the current user is the invited user
     */
    @Transactional(readOnly = true)
    public boolean isInvitedUser(UUID invitedUserId) {
        if (invitedUserId == null) {
            log.debug("Invited user ID is null, denying invitation access");
            return false;
        }

        return getCurrentUserIdIfPresent()
                .map(currentUserId -> {
                    boolean isInvited = currentUserId.equals(invitedUserId);
                    log.debug("User {} is invited user {}: {}", currentUserId, invitedUserId, isInvited);
                    return isInvited;
                })
                .orElse(false);
    }

    // ==================== Helper Methods (Public) ====================

    /**
     * Gets the current user's membership in a household.
     *
     * @param householdId The household to get membership for
     * @return Optional containing the membership if found
     */
    @Transactional(readOnly = true)
    public Optional<HouseholdMember> getCurrentUserMembership(UUID householdId) {
        if (householdId == null) {
            return Optional.empty();
        }

        return getCurrentUserIdIfPresent()
                .flatMap(userId -> householdMemberRepository
                        .findByHouseholdIdAndUserId(householdId, userId));
    }

    /**
     * Gets the current user's role in a household.
     *
     * @param householdId The household to get role for
     * @return Optional containing the role if user is a member
     */
    @Transactional(readOnly = true)
    public Optional<HouseholdRole> getCurrentUserRole(UUID householdId) {
        return getCurrentUserMembership(householdId)
                .map(HouseholdMember::getRole);
    }

    // ==================== Private Helper Methods ====================

    /**
     * Extracts and validates the JWT authentication from the security context.
     *
     * @return The JWT authentication token
     * @throws AuthenticationRequiredException if authentication is missing or invalid
     */
    private JwtAuthenticationToken getJwtAuthentication() {
        Authentication authentication = securityContextProvider.getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AuthenticationRequiredException("No authenticated user found");
        }

        if (!(authentication instanceof JwtAuthenticationToken jwtToken)) {
            throw new AuthenticationRequiredException(
                    "Invalid authentication type - expected JWT, got " + 
                    authentication.getClass().getSimpleName());
        }

        return jwtToken;
    }

    /**
     * Extracts the user ID from a JWT token.
     *
     * @param jwtToken The JWT authentication token
     * @return The user's UUID
     * @throws AuthenticationRequiredException if the subject claim is missing or invalid
     */
    private UUID extractUserId(JwtAuthenticationToken jwtToken) {
        String subject = jwtToken.getToken().getSubject();
        
        if (subject == null || subject.isBlank()) {
            throw new AuthenticationRequiredException("JWT token missing subject claim");
        }

        try {
            return UUID.fromString(subject);
        } catch (IllegalArgumentException e) {
            log.error("Invalid UUID format in JWT subject: {}", subject, e);
            throw new AuthenticationRequiredException("Invalid user identifier in token");
        }
    }

    /**
     * Checks if two users share at least one household.
     *
     * @param userId1 First user's ID
     * @param userId2 Second user's ID
     * @return true if the users share at least one household
     */
    private boolean hasSharedHousehold(UUID userId1, UUID userId2) {
        return householdMemberRepository.findByUserId(userId1).stream()
                .map(member -> member.getHousehold().getId())
                .anyMatch(householdId -> 
                        householdMemberRepository.existsByHouseholdIdAndUserId(householdId, userId2));
    }

    /**
     * Creates a new user from JWT token claims.
     * 
     * <p>This is called on first login when the user exists in the auth provider
     * but not yet in our database.</p>
     *
     * @param token The JWT authentication token
     * @return The newly created and persisted user
     */
    private User createUserFromToken(JwtAuthenticationToken token) {
        String subject = token.getToken().getSubject();
        String email = token.getToken().getClaimAsString("email");
        String displayName = token.getToken().getClaimAsString("name");

        log.info("Creating new user from JWT token for subject: {}", subject);

        User user = User.builder()
                .id(UUID.fromString(subject))
                .email(email != null ? email : subject + "@unknown.com")
                .displayName(displayName != null ? displayName : "User")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        User savedUser = userRepository.save(user);
        log.info("Successfully created user with ID: {}", savedUser.getId());
        
        return savedUser;
    }
}