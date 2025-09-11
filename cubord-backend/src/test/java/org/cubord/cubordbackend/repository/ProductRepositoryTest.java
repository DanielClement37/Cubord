package org.cubord.cubordbackend.repository;

import org.cubord.cubordbackend.domain.Product;
import org.cubord.cubordbackend.domain.ProductDataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class ProductRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ProductRepository productRepository;

    @Test
    @DisplayName("Should find product by UPC")
    void shouldFindProductByUpc() {
        // Given
        String upc = "123456789012";
        Product product = Product.builder()
                .id(UUID.randomUUID())
                .upc(upc)
                .name("Test Product")
                .brand("Test Brand")
                .category("Test Category")
                .defaultExpirationDays(30)
                .dataSource(ProductDataSource.OPEN_FOOD_FACTS)
                .requiresApiRetry(false)
                .retryAttempts(0)
                .build();
        
        entityManager.persistAndFlush(product);

        // When
        Optional<Product> foundProduct = productRepository.findByUpc(upc);

        // Then
        assertThat(foundProduct).isPresent();
        assertThat(foundProduct.get().getUpc()).isEqualTo(upc);
        assertThat(foundProduct.get().getName()).isEqualTo("Test Product");
        assertThat(foundProduct.get().getBrand()).isEqualTo("Test Brand");
    }

    @Test
    @DisplayName("Should return empty when product not found by UPC")
    void shouldReturnEmptyWhenProductNotFoundByUpc() {
        // Given
        String nonExistentUpc = "999999999999";

        // When
        Optional<Product> foundProduct = productRepository.findByUpc(nonExistentUpc);

        // Then
        assertThat(foundProduct).isEmpty();
    }

    @Test
    @DisplayName("Should find all products requiring API retry")
    void shouldFindAllProductsRequiringApiRetry() {
        // Given
        Product product1 = Product.builder()
                .id(UUID.randomUUID())
                .upc("123456789012")
                .name("Manual Product 1")
                .dataSource(ProductDataSource.MANUAL)
                .requiresApiRetry(true)
                .retryAttempts(0)
                .build();
        
        Product product2 = Product.builder()
                .id(UUID.randomUUID())
                .upc("123456789013")
                .name("Manual Product 2")
                .dataSource(ProductDataSource.MANUAL)
                .requiresApiRetry(true)
                .retryAttempts(1)
                .build();
        
        Product product3 = Product.builder()
                .id(UUID.randomUUID())
                .upc("123456789014")
                .name("API Product")
                .dataSource(ProductDataSource.OPEN_FOOD_FACTS)
                .requiresApiRetry(false)
                .retryAttempts(0)
                .build();
        
        entityManager.persistAndFlush(product1);
        entityManager.persistAndFlush(product2);
        entityManager.persistAndFlush(product3);

        // When
        List<Product> productsRequiringRetry = productRepository.findByRequiresApiRetryTrue();

        // Then
        assertThat(productsRequiringRetry).hasSize(2);
        assertThat(productsRequiringRetry).extracting(Product::getUpc)
                .containsExactlyInAnyOrder("123456789012", "123456789013");
    }

    @Test
    @DisplayName("Should find products by retry attempts less than max")
    void shouldFindProductsByRetryAttemptsLessThanMax() {
        // Given
        int maxRetryAttempts = 3;
        
        Product product1 = Product.builder()
                .id(UUID.randomUUID())
                .upc("123456789012")
                .name("Low Retry Product")
                .requiresApiRetry(true)
                .retryAttempts(1)
                .build();
        
        Product product2 = Product.builder()
                .id(UUID.randomUUID())
                .upc("123456789013")
                .name("Medium Retry Product")
                .requiresApiRetry(true)
                .retryAttempts(2)
                .build();
        
        Product product3 = Product.builder()
                .id(UUID.randomUUID())
                .upc("123456789014")
                .name("Max Retry Product")
                .requiresApiRetry(true)
                .retryAttempts(3)
                .build();
        
        entityManager.persistAndFlush(product1);
        entityManager.persistAndFlush(product2);
        entityManager.persistAndFlush(product3);

        // When
        List<Product> eligibleProducts = productRepository.findByRequiresApiRetryTrueAndRetryAttemptsLessThan(maxRetryAttempts);

        // Then
        assertThat(eligibleProducts).hasSize(2);
        assertThat(eligibleProducts).extracting(Product::getRetryAttempts)
                .containsExactlyInAnyOrder(1, 2);
    }

    @Test
    @DisplayName("Should find products by data source")
    void shouldFindProductsByDataSource() {
        // Given
        Product manualProduct = Product.builder()
                .id(UUID.randomUUID())
                .upc("123456789012")
                .name("Manual Product")
                .dataSource(ProductDataSource.MANUAL)
                .build();
        
        Product apiProduct = Product.builder()
                .id(UUID.randomUUID())
                .upc("123456789013")
                .name("API Product")
                .dataSource(ProductDataSource.OPEN_FOOD_FACTS)
                .build();
        
        Product hybridProduct = Product.builder()
                .id(UUID.randomUUID())
                .upc("123456789014")
                .name("Hybrid Product")
                .dataSource(ProductDataSource.HYBRID)
                .build();
        
        entityManager.persistAndFlush(manualProduct);
        entityManager.persistAndFlush(apiProduct);
        entityManager.persistAndFlush(hybridProduct);

        // When
        List<Product> manualProducts = productRepository.findByDataSource(ProductDataSource.MANUAL);
        List<Product> apiProducts = productRepository.findByDataSource(ProductDataSource.OPEN_FOOD_FACTS);

        // Then
        assertThat(manualProducts).hasSize(1);
        assertThat(manualProducts.getFirst().getName()).isEqualTo("Manual Product");
        
        assertThat(apiProducts).hasSize(1);
        assertThat(apiProducts.getFirst().getName()).isEqualTo("API Product");
    }

    @Test
    @DisplayName("Should find products by category")
    void shouldFindProductsByCategory() {
        // Given
        Product product1 = Product.builder()
                .id(UUID.randomUUID())
                .upc("123456789012")
                .name("Bread")
                .category("Bakery")
                .build();
        
        Product product2 = Product.builder()
                .id(UUID.randomUUID())
                .upc("123456789013")
                .name("Croissant")
                .category("Bakery")
                .build();
        
        Product product3 = Product.builder()
                .id(UUID.randomUUID())
                .upc("123456789014")
                .name("Milk")
                .category("Dairy")
                .build();
        
        entityManager.persistAndFlush(product1);
        entityManager.persistAndFlush(product2);
        entityManager.persistAndFlush(product3);

        // When
        List<Product> bakeryProducts = productRepository.findByCategory("Bakery");
        List<Product> dairyProducts = productRepository.findByCategory("Dairy");

        // Then
        assertThat(bakeryProducts).hasSize(2);
        assertThat(bakeryProducts).extracting(Product::getName)
                .containsExactlyInAnyOrder("Bread", "Croissant");
        
        assertThat(dairyProducts).hasSize(1);
        assertThat(dairyProducts.getFirst().getName()).isEqualTo("Milk");
    }

    @Test
    @DisplayName("Should find products by brand")
    void shouldFindProductsByBrand() {
        // Given
        Product product1 = Product.builder()
                .id(UUID.randomUUID())
                .upc("123456789012")
                .name("Cola")
                .brand("Coca-Cola")
                .build();
        
        Product product2 = Product.builder()
                .id(UUID.randomUUID())
                .upc("123456789013")
                .name("Sprite")
                .brand("Coca-Cola")
                .build();
        
        Product product3 = Product.builder()
                .id(UUID.randomUUID())
                .upc("123456789014")
                .name("Pepsi")
                .brand("PepsiCo")
                .build();
        
        entityManager.persistAndFlush(product1);
        entityManager.persistAndFlush(product2);
        entityManager.persistAndFlush(product3);

        // When
        List<Product> cocaColaProducts = productRepository.findByBrand("Coca-Cola");
        List<Product> pepsiProducts = productRepository.findByBrand("PepsiCo");

        // Then
        assertThat(cocaColaProducts).hasSize(2);
        assertThat(cocaColaProducts).extracting(Product::getName)
                .containsExactlyInAnyOrder("Cola", "Sprite");
        
        assertThat(pepsiProducts).hasSize(1);
        assertThat(pepsiProducts.getFirst().getName()).isEqualTo("Pepsi");
    }

    @Test
    @DisplayName("Should search products by name containing")
    void shouldSearchProductsByNameContaining() {
        // Given
        Product product1 = Product.builder()
                .id(UUID.randomUUID())
                .upc("123456789012")
                .name("Whole Wheat Bread")
                .build();
        
        Product product2 = Product.builder()
                .id(UUID.randomUUID())
                .upc("123456789013")
                .name("White Bread")
                .build();
        
        Product product3 = Product.builder()
                .id(UUID.randomUUID())
                .upc("123456789014")
                .name("Bagel")
                .build();
        
        entityManager.persistAndFlush(product1);
        entityManager.persistAndFlush(product2);
        entityManager.persistAndFlush(product3);

        // When
        List<Product> breadProducts = productRepository.findByNameContainingIgnoreCase("bread");
        List<Product> wheatProducts = productRepository.findByNameContainingIgnoreCase("wheat");

        // Then
        assertThat(breadProducts).hasSize(2);
        assertThat(breadProducts).extracting(Product::getName)
                .containsExactlyInAnyOrder("Whole Wheat Bread", "White Bread");
        
        assertThat(wheatProducts).hasSize(1);
        assertThat(wheatProducts.getFirst().getName()).isEqualTo("Whole Wheat Bread");
    }

    @Test
    @DisplayName("Should find products with last retry attempt before given time")
    void shouldFindProductsWithLastRetryAttemptBefore() {
        // Given
        LocalDateTime cutoffTime = LocalDateTime.of(2025, 1, 1, 12, 0);
        LocalDateTime beforeTime = cutoffTime.minusHours(2);
        LocalDateTime afterTime = cutoffTime.plusHours(2);
        
        Product product1 = Product.builder()
                .id(UUID.randomUUID())
                .upc("123456789012")
                .name("Old Retry Product")
                .requiresApiRetry(true)
                .lastRetryAttempt(beforeTime)
                .build();
        
        Product product2 = Product.builder()
                .id(UUID.randomUUID())
                .upc("123456789013")
                .name("Recent Retry Product")
                .requiresApiRetry(true)
                .lastRetryAttempt(afterTime)
                .build();
        
        Product product3 = Product.builder()
                .id(UUID.randomUUID())
                .upc("123456789014")
                .name("Never Retried Product")
                .requiresApiRetry(true)
                .lastRetryAttempt(null)
                .build();
        
        entityManager.persistAndFlush(product1);
        entityManager.persistAndFlush(product2);
        entityManager.persistAndFlush(product3);

        // When
        List<Product> eligibleProducts = productRepository.findByRequiresApiRetryTrueAndLastRetryAttemptBefore(cutoffTime);
        List<Product> nullRetryProducts = productRepository.findByRequiresApiRetryTrueAndLastRetryAttemptIsNull();

        // Then
        assertThat(eligibleProducts).hasSize(1);
        assertThat(eligibleProducts.getFirst().getName()).isEqualTo("Old Retry Product");
        
        assertThat(nullRetryProducts).hasSize(1);
        assertThat(nullRetryProducts.getFirst().getName()).isEqualTo("Never Retried Product");
    }

    @Test
    @DisplayName("Should save and retrieve product with all fields")
    void shouldSaveAndRetrieveProductWithAllFields() {
        // Given
        UUID productId = UUID.randomUUID();
        Product product = Product.builder()
                .id(productId)
                .upc("123456789012")
                .name("Complete Product")
                .brand("Test Brand")
                .category("Test Category")
                .defaultExpirationDays(60)
                .dataSource(ProductDataSource.OPEN_FOOD_FACTS)
                .requiresApiRetry(false)
                .retryAttempts(0)
                .build();

        // When
        Product savedProduct = productRepository.save(product);
        entityManager.flush();
        entityManager.clear();
        
        Optional<Product> retrievedProduct = productRepository.findById(productId);

        // Then
        assertThat(savedProduct).isNotNull();
        assertThat(savedProduct.getCreatedAt()).isNotNull();
        assertThat(savedProduct.getUpdatedAt()).isNotNull();
        
        assertThat(retrievedProduct).isPresent();
        Product retrieved = retrievedProduct.get();
        assertThat(retrieved.getId()).isEqualTo(productId);
        assertThat(retrieved.getUpc()).isEqualTo("123456789012");
        assertThat(retrieved.getName()).isEqualTo("Complete Product");
        assertThat(retrieved.getBrand()).isEqualTo("Test Brand");
        assertThat(retrieved.getCategory()).isEqualTo("Test Category");
        assertThat(retrieved.getDefaultExpirationDays()).isEqualTo(60);
        assertThat(retrieved.getDataSource()).isEqualTo(ProductDataSource.OPEN_FOOD_FACTS);
        assertThat(retrieved.getRequiresApiRetry()).isFalse();
        assertThat(retrieved.getRetryAttempts()).isEqualTo(0);
        assertThat(retrieved.getCreatedAt()).isNotNull();
        assertThat(retrieved.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("Should update product and modify updatedAt timestamp")
    void shouldUpdateProductAndModifyUpdatedAtTimestamp() {
        // Given
        Product product = Product.builder()
                .id(UUID.randomUUID())
                .upc("123456789012")
                .name("Original Product")
                .brand("Original Brand")
                .build();
        
        Product savedProduct = productRepository.save(product);
        entityManager.flush();
        
        LocalDateTime originalUpdatedAt = savedProduct.getUpdatedAt();
        
        // When
        savedProduct.setName("Updated Product");
        savedProduct.setBrand("Updated Brand");
        Product updatedProduct = productRepository.save(savedProduct);
        entityManager.flush();

        // Then
        assertThat(updatedProduct.getName()).isEqualTo("Updated Product");
        assertThat(updatedProduct.getBrand()).isEqualTo("Updated Brand");
        assertThat(updatedProduct.getUpdatedAt()).isAfter(originalUpdatedAt);
        assertThat(updatedProduct.getCreatedAt()).isEqualTo(savedProduct.getCreatedAt());
    }

    @Test
    @DisplayName("Should delete product by ID")
    void shouldDeleteProductById() {
        // Given
        Product product = Product.builder()
                .id(UUID.randomUUID())
                .upc("123456789012")
                .name("To Delete Product")
                .build();
        
        Product savedProduct = productRepository.save(product);
        UUID productId = savedProduct.getId();
        entityManager.flush();

        // When
        productRepository.deleteById(productId);
        entityManager.flush();

        // Then
        Optional<Product> deletedProduct = productRepository.findById(productId);
        assertThat(deletedProduct).isEmpty();
    }

    @Test
    @DisplayName("Should count products by various criteria")
    void shouldCountProductsByVariousCriteria() {
        // Given
        Product product1 = Product.builder()
                .id(UUID.randomUUID())
                .upc("123456789012")
                .name("Manual Product")
                .dataSource(ProductDataSource.MANUAL)
                .requiresApiRetry(true)
                .category("Food")
                .build();
        
        Product product2 = Product.builder()
                .id(UUID.randomUUID())
                .upc("123456789013")
                .name("API Product")
                .dataSource(ProductDataSource.OPEN_FOOD_FACTS)
                .requiresApiRetry(false)
                .category("Food")
                .build();
        
        Product product3 = Product.builder()
                .id(UUID.randomUUID())
                .upc("123456789014")
                .name("Beverage Product")
                .dataSource(ProductDataSource.OPEN_FOOD_FACTS)
                .requiresApiRetry(false)
                .category("Beverages")
                .build();
        
        entityManager.persistAndFlush(product1);
        entityManager.persistAndFlush(product2);
        entityManager.persistAndFlush(product3);

        // When & Then
        assertThat(productRepository.count()).isEqualTo(3);
        assertThat(productRepository.countByRequiresApiRetryTrue()).isEqualTo(1);
        assertThat(productRepository.countByDataSource(ProductDataSource.OPEN_FOOD_FACTS)).isEqualTo(2);
        assertThat(productRepository.countByCategory("Food")).isEqualTo(2);
    }
}