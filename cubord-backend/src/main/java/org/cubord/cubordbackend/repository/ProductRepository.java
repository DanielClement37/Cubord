package org.cubord.cubordbackend.repository;

import org.cubord.cubordbackend.domain.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ProductRepository extends JpaRepository<Product, UUID> {
    // e.g., findByUpc(String upc);
}