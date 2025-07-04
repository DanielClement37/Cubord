package org.cubord.cubordbackend.repository;

import org.cubord.cubordbackend.domain.Location;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
     * Find all locations belonging to a specific household with pagination
     */
    Page<Location> findByHouseholdId(UUID householdId, Pageable pageable);
    
    /**
     * Find a location by household ID and name
     */
    Optional<Location> findByHouseholdIdAndName(UUID householdId, String name);
    
    /**
     * Check if a location exists with the given household ID and name
     */
    boolean existsByHouseholdIdAndName(UUID householdId, String name);
    
    /**
     * Find all locations by household ID ordered by name ascending
     */
    List<Location> findByHouseholdIdOrderByNameAsc(UUID householdId);
    
    /**
     * Find all locations by household ID ordered by name descending
     */
    List<Location> findByHouseholdIdOrderByNameDesc(UUID householdId);
    
    /**
     * Count locations in a specific household
     */
    long countByHouseholdId(UUID householdId);
    
    /**
     * Find locations by household ID and name containing (case-insensitive)
     */
    List<Location> findByHouseholdIdAndNameContainingIgnoreCase(UUID householdId, String nameFragment);
    
    /**
     * Find locations by household ID and description containing (case-insensitive)
     */
    List<Location> findByHouseholdIdAndDescriptionContainingIgnoreCase(UUID householdId, String descriptionFragment);
    
    /**
     * Delete all locations belonging to a specific household
     */
    void deleteByHouseholdId(UUID householdId);
    
    /**
     * Custom query to find locations with optional name filtering
     */
    @Query("SELECT l FROM Location l WHERE l.household.id = :householdId " +
           "AND (:name IS NULL OR LOWER(l.name) LIKE LOWER(CONCAT('%', :name, '%')))")
    List<Location> findLocationsByHouseholdIdWithOptionalNameFilter(
            @Param("householdId") UUID householdId, 
            @Param("name") String name);
    
    /**
     * Custom query to find locations with optional name and description filtering
     */
    @Query("SELECT l FROM Location l WHERE l.household.id = :householdId " +
           "AND (:name IS NULL OR LOWER(l.name) LIKE LOWER(CONCAT('%', :name, '%'))) " +
           "AND (:description IS NULL OR LOWER(l.description) LIKE LOWER(CONCAT('%', :description, '%')))")
    Page<Location> findLocationsByHouseholdIdWithFilters(
            @Param("householdId") UUID householdId, 
            @Param("name") String name,
            @Param("description") String description,
            Pageable pageable);
}