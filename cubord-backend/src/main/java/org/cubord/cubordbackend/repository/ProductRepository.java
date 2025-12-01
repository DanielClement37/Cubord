package org.cubord.cubordbackend.repository;

import org.cubord.cubordbackend.domain.Product;
import org.cubord.cubordbackend.domain.ProductDataSource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductRepository extends JpaRepository<Product, UUID> {
    
    // Basic queries
    Optional<Product> findByUpc(String upc);
    
    List<Product> findByCategory(String category);
    
    List<Product> findByBrand(String brand);
    
    List<Product> findByNameContainingIgnoreCase(String name);
    
    List<Product> findByDataSource(ProductDataSource dataSource);
    
    // Retry mechanism queries
    List<Product> findByRequiresApiRetryTrue();
    
    List<Product> findByRequiresApiRetryTrueAndRetryAttemptsLessThan(Integer maxRetryAttempts);
    
    List<Product> findByRequiresApiRetryTrueAndLastRetryAttemptBefore(LocalDateTime cutoffTime);
    
    List<Product> findByRequiresApiRetryTrueAndLastRetryAttemptIsNull();
    
    // Count queries
    long countByRequiresApiRetryTrue();
    
    long countByDataSource(ProductDataSource dataSource);
    
    long countByCategory(String category);

    // Search query with pagination
    @Query("SELECT p FROM Product p WHERE " +
           "LOWER(p.name) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
           "OR LOWER(COALESCE(p.brand, '')) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
           "OR LOWER(COALESCE(p.category, '')) LIKE LOWER(CONCAT('%', :searchTerm, '%'))")
    Page<Product> searchByNameBrandOrCategory(@Param("searchTerm") String searchTerm, Pageable pageable);
}