package org.cubord.cubordbackend.repository;


import org.cubord.cubordbackend.domain.PantryItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PantryItemRepository extends JpaRepository<PantryItem, UUID> {
    
    // Basic location-based queries
    List<PantryItem> findByLocationId(UUID locationId);
    List<PantryItem> findByLocationId(UUID locationId, Sort sort);
    long countByLocationId(UUID locationId);
    
    // Basic product-based queries
    List<PantryItem> findByProductId(UUID productId);
    
    // Expiration-date-aware duplicate detection queries
    Optional<PantryItem> findByLocationIdAndProductIdAndExpirationDate(UUID locationId, UUID productId, LocalDate expirationDate);
    Optional<PantryItem> findByLocationIdAndProductIdAndExpirationDateIsNull(UUID locationId, UUID productId);
    boolean existsByLocationIdAndProductIdAndExpirationDate(UUID locationId, UUID productId, LocalDate expirationDate);
    boolean existsByLocationIdAndProductIdAndExpirationDateIsNull(UUID locationId, UUID productId);
    int deleteByLocationIdAndProductIdAndExpirationDate(UUID locationId, UUID productId, LocalDate expirationDate);
    int deleteByLocationIdAndProductIdAndExpirationDateIsNull(UUID locationId, UUID productId);
    
    // Product variant queries (all variants of same product in location)
    List<PantryItem> findByLocationIdAndProductIdOrderByExpirationDateAsc(UUID locationId, UUID productId);
    @Query("SELECT p FROM PantryItem p WHERE p.location.id = :locationId AND p.product.id = :productId " +
           "ORDER BY p.expirationDate ASC NULLS LAST")
    List<PantryItem> findByLocationIdAndProductIdOrderByExpirationDateNullsLast(
            @Param("locationId") UUID locationId, 
            @Param("productId") UUID productId);
    
    // Household-based queries (through location relationship)
    List<PantryItem> findByLocation_HouseholdId(UUID householdId);
    Page<PantryItem> findByLocation_HouseholdId(UUID householdId, Pageable pageable);
    long countByLocation_HouseholdId(UUID householdId);
    
    // Expiration date queries
    List<PantryItem> findByExpirationDateBefore(LocalDate date);
    List<PantryItem> findByExpirationDateBetween(LocalDate startDate, LocalDate endDate);
    List<PantryItem> findByExpirationDateBeforeAndLocation_HouseholdId(LocalDate date, UUID householdId);
    List<PantryItem> findByExpirationDateIsNull();
    List<PantryItem> findByExpirationDateIsNullAndLocation_HouseholdId(UUID householdId);
    
    // Low stock notification queries
    List<PantryItem> findByQuantityLessThanEqual(Integer quantity);
    List<PantryItem> findByQuantityLessThanEqualAndLocation_HouseholdId(Integer quantity, UUID householdId);
    
    // Product search queries
    List<PantryItem> findByProduct_NameContainingIgnoreCase(String productName);
    List<PantryItem> findByProduct_CategoryAndLocation_HouseholdId(String category, UUID householdId);
    
    // Advanced queries using @Query annotation
    @Query("SELECT p FROM PantryItem p WHERE p.location.household.id = :householdId " +
           "AND p.expirationDate BETWEEN :startDate AND :endDate " +
           "ORDER BY p.expirationDate ASC")
    List<PantryItem> findExpiringItemsInHouseholdBetweenDates(
            @Param("householdId") UUID householdId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);
    
    @Query("SELECT p FROM PantryItem p WHERE p.location.household.id = :householdId " +
           "AND p.quantity <= :threshold " +
           "ORDER BY p.quantity ASC, p.product.name ASC")
    List<PantryItem> findLowStockItemsInHousehold(
            @Param("householdId") UUID householdId,
            @Param("threshold") Integer threshold);
    
    @Query("SELECT p FROM PantryItem p WHERE p.location.id = :locationId " +
           "AND (LOWER(p.product.name) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
           "OR LOWER(p.product.brand) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
           "OR LOWER(COALESCE(p.notes, '')) LIKE LOWER(CONCAT('%', :searchTerm, '%')))")
    List<PantryItem> searchItemsInLocation(
            @Param("locationId") UUID locationId,
            @Param("searchTerm") String searchTerm);
    
    @Query("SELECT p FROM PantryItem p WHERE p.location.household.id = :householdId " +
           "AND (LOWER(p.product.name) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
           "OR LOWER(p.product.brand) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
           "OR LOWER(COALESCE(p.notes, '')) LIKE LOWER(CONCAT('%', :searchTerm, '%')))")
    List<PantryItem> searchItemsInHousehold(
            @Param("householdId") UUID householdId,
            @Param("searchTerm") String searchTerm);
    
    // Enhanced queries for better UI support
    @Query("SELECT p FROM PantryItem p WHERE p.location.household.id = :householdId " +
           "ORDER BY p.product.name ASC, p.expirationDate ASC NULLS LAST")
    List<PantryItem> findByLocation_HouseholdIdOrderByProductAndExpiration(UUID householdId);
    
    @Query("SELECT p FROM PantryItem p WHERE p.location.id = :locationId " +
           "ORDER BY p.product.name ASC, p.expirationDate ASC NULLS LAST")
    List<PantryItem> findByLocationIdOrderByProductAndExpiration(UUID locationId);
    
    // Aggregate queries for statistics
    @Query("SELECT COUNT(DISTINCT p.product.id) FROM PantryItem p WHERE p.location.household.id = :householdId")
    long countDistinctProductsByHouseholdId(@Param("householdId") UUID householdId);
    
    @Query("SELECT COUNT(p) FROM PantryItem p WHERE p.location.household.id = :householdId " +
           "AND p.expirationDate IS NOT NULL AND p.expirationDate <= :date")
    long countExpiringItemsByHouseholdIdAndDate(@Param("householdId") UUID householdId, @Param("date") LocalDate date);
    
    @Query("SELECT SUM(p.quantity) FROM PantryItem p WHERE p.location.id = :locationId AND p.product.id = :productId")
    Integer sumQuantityByLocationAndProduct(@Param("locationId") UUID locationId, @Param("productId") UUID productId);
    
    // Security check queries
    /**
     * Checks if a pantry item exists and the user has access to it through household membership.
     * Used for authorization checks in SecurityService.
     */
    @Query("SELECT CASE WHEN COUNT(p) > 0 THEN true ELSE false END FROM PantryItem p " +
           "JOIN p.location l " +
           "JOIN l.household h " +
           "JOIN h.members m " +
           "WHERE p.id = :pantryItemId AND m.user.id = :userId")
    boolean existsByIdAndLocationHouseholdMembers_UserId(
            @Param("pantryItemId") UUID pantryItemId,
            @Param("userId") UUID userId);
}
