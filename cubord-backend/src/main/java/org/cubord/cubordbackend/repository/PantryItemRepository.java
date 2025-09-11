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
    
    // Duplicate item detection queries (key for your business requirement)
    Optional<PantryItem> findByLocationIdAndProductId(UUID locationId, UUID productId);
    boolean existsByLocationIdAndProductId(UUID locationId, UUID productId);
    int deleteByLocationIdAndProductId(UUID locationId, UUID productId);
    
    // Household-based queries (through location relationship)
    List<PantryItem> findByLocation_HouseholdId(UUID householdId);
    Page<PantryItem> findByLocation_HouseholdId(UUID householdId, Pageable pageable);
    long countByLocation_HouseholdId(UUID householdId);
    
    // Expiration date queries
    List<PantryItem> findByExpirationDateBefore(LocalDate date);
    List<PantryItem> findByExpirationDateBetween(LocalDate startDate, LocalDate endDate);
    List<PantryItem> findByExpirationDateBeforeAndLocation_HouseholdId(LocalDate date, UUID householdId);
    
    // Low stock notification queries (key for your business requirement)
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
}
