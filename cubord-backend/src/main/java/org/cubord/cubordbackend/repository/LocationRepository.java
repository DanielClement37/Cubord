package org.cubord.cubordbackend.repository;

import org.cubord.cubordbackend.domain.Location;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LocationRepository extends JpaRepository<Location, UUID> {
    
    /**
     * Find all locations belonging to a specific household
     */
    List<Location> findByHouseholdId(UUID householdId);
    
    /**
     * Find all locations belonging to a specific household with sorting
     */
    List<Location> findByHouseholdId(UUID householdId, Sort sort);
    
    /**
     * Find a location by household ID and name
     */
    Optional<Location> findByHouseholdIdAndName(UUID householdId, String name);
    
    /**
     * Check if a location exists with the given household ID and name
     */
    boolean existsByHouseholdIdAndName(UUID householdId, String name);
    
    /**
     * Count locations in a specific household
     */
    long countByHouseholdId(UUID householdId);
    
    /**
     * Simple search across both name and description fields
     */
    @Query("SELECT l FROM Location l WHERE l.household.id = :householdId " +
           "AND (LOWER(l.name) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
           "OR LOWER(COALESCE(l.description, '')) LIKE LOWER(CONCAT('%', :searchTerm, '%')))")
    List<Location> searchByNameOrDescription(@Param("householdId") UUID householdId, 
                                           @Param("searchTerm") String searchTerm);
    
    /**
     * Delete all locations belonging to a specific household
     */
    void deleteByHouseholdId(UUID householdId);
    
    /**
     * Checks if a location exists and the user has access to it through household membership.
     * Used for authorization checks in SecurityService.
     */
    @Query("SELECT CASE WHEN COUNT(l) > 0 THEN true ELSE false END FROM Location l " +
           "JOIN l.household h " +
           "JOIN h.members m " +
           "WHERE l.id = :locationId AND m.user.id = :userId")
    boolean existsByIdAndHouseholdMembers_UserId(
            @Param("locationId") UUID locationId,
            @Param("userId") UUID userId);
}