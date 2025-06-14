package org.cubord.cubordbackend.security;

import lombok.RequiredArgsConstructor;
import org.cubord.cubordbackend.domain.HouseholdRole;
import org.cubord.cubordbackend.repository.HouseholdMemberRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class HouseholdPermissionEvaluator implements PermissionEvaluator {
    
    private static final Logger logger = LoggerFactory.getLogger(HouseholdPermissionEvaluator.class);
    private final HouseholdMemberRepository householdMemberRepository;

    /**
     * Evaluates if the authenticated user has the specified permission for a target object.
     * Specifically handles household permissions based on member roles.
     *
     * @param authentication The authenticated user
     * @param targetDomainObject The object to check permissions for
     * @param permission The permission to check (e.g., "ADMIN", "OWNER")
     * @return true if the user has the required permission, false otherwise
     */
    @Override
    public boolean hasPermission(Authentication authentication, Object targetDomainObject, Object permission) {
        logger.debug("Checking permission: {} for target: {}", permission, targetDomainObject);
        
        if (authentication == null || targetDomainObject == null || !(permission instanceof String)) {
            return false;
        }
        
        return false; // Default implementation - override for specific domain objects
    }

    /**
     * Evaluates if the authenticated user has the specified permission for an object identified by type and ID.
     * Handles household permissions by checking member roles against the required permission.
     *
     * @param authentication The authenticated user
     * @param targetId The ID of the target object
     * @param targetType The type of the target object
     * @param permission The permission to check (e.g., "ADMIN", "OWNER")
     * @return true if the user has the required permission, false otherwise
     */
    @Override
    public boolean hasPermission(Authentication authentication, Serializable targetId, String targetType, Object permission) {
        logger.debug("Checking permission: {} for targetType: {} with id: {}", permission, targetType, targetId);
        
        if (authentication == null || targetId == null || !(targetId instanceof UUID) || 
            targetType == null || !(permission instanceof String)) {
            return false;
        }
        
        UUID userId = getUserIdFromAuthentication(authentication);
        if (userId == null) {
            return false;
        }
        
        UUID objectId = (UUID) targetId;
        String requiredPermission = (String) permission;
        
        if ("HOUSEHOLD".equalsIgnoreCase(targetType)) {
            return hasHouseholdPermission(userId, objectId, requiredPermission);
        }
        
        return false;
    }
    
    /**
     * Checks if a user has view permission for a household.
     * 
     * @param authentication The authentication object
     * @param householdId The household ID as string
     * @return true if the user has view permission, false otherwise
     */
    public boolean hasViewPermission(Authentication authentication, String householdId) {
        if (authentication == null || householdId == null) {
            return false;
        }
        
        UUID userId = getUserIdFromAuthentication(authentication);
        if (userId == null) {
            return false;
        }
        
        try {
            UUID householdUuid = UUID.fromString(householdId);
            return hasHouseholdPermission(userId, householdUuid, "MEMBER");
        } catch (IllegalArgumentException e) {
            logger.error("Invalid household UUID: {}", householdId, e);
            return false;
        }
    }
    
    /**
     * Checks if a user has edit permission for a household.
     * 
     * @param authentication The authentication object
     * @param householdId The household ID as string
     * @return true if the user has edit permission, false otherwise
     */
    public boolean hasEditPermission(Authentication authentication, String householdId) {
        if (authentication == null || householdId == null) {
            return false;
        }
        
        UUID userId = getUserIdFromAuthentication(authentication);
        if (userId == null) {
            return false;
        }
        
        try {
            UUID householdUuid = UUID.fromString(householdId);
            return hasHouseholdPermission(userId, householdUuid, "ADMIN");
        } catch (IllegalArgumentException e) {
            logger.error("Invalid household UUID: {}", householdId, e);
            return false;
        }
    }
    
    /**
     * Extracts the user ID from the authentication object.
     *
     * @param authentication The authentication object
     * @return UUID of the authenticated user, or null if not available
     */
    private UUID getUserIdFromAuthentication(Authentication authentication) {
        try {
            return UUID.fromString(authentication.getName());
        } catch (IllegalArgumentException e) {
            logger.error("Invalid UUID in authentication name: {}", authentication.getName(), e);
            return null;
        }
    }
    
    /**
     * Checks if a user has the required permission for a household.
     *
     * @param userId ID of the user
     * @param householdId ID of the household
     * @param permission Permission string to check
     * @return true if the user has the required permission, false otherwise
     */
    private boolean hasHouseholdPermission(UUID userId, UUID householdId, String permission) {
        return householdMemberRepository.findByHouseholdIdAndUserId(householdId, userId)
                .map(member -> {
                    HouseholdRole role = member.getRole();
                    
                    if ("OWNER".equals(permission)) {
                        return role == HouseholdRole.OWNER;
                    } else if ("ADMIN".equals(permission)) {
                        return role == HouseholdRole.OWNER || role == HouseholdRole.ADMIN;
                    } else if ("MEMBER".equals(permission)) {
                        return true; // All members have MEMBER permission
                    }
                    
                    return false;
                })
                .orElse(false);
    }
}