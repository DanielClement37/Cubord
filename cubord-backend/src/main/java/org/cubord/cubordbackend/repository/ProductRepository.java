package org.cubord.cubordbackend.repository;

import org.cubord.cubordbackend.domain.Product;
import org.cubord.cubordbackend.domain.ProductDataSource;
import org.springframework.data.jpa.repository.JpaRepository;
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
}