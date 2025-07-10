package org.cubord.cubordbackend.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

@ExtendWith(MockitoExtension.class)
class ProductDomainTest {

    @Test
    @DisplayName("Test Product builder with all fields")
    void testProductBuilder() {
        // Given
        UUID id = UUID.randomUUID();
        String upc = "123456789012";
        String name = "Test Product";
        String brand = "Test Brand";
        String category = "Test Category";
        Integer defaultExpirationDays = 30;
        Boolean requiresApiRetry = false;
        Integer retryAttempts = 0;
        LocalDateTime lastRetryAttempt = LocalDateTime.now();
        ProductDataSource dataSource = ProductDataSource.UPC_API;
        LocalDateTime createdAt = LocalDateTime.now();
        LocalDateTime updatedAt = LocalDateTime.now();

        // When
        Product product = Product.builder()
                .id(id)
                .upc(upc)
                .name(name)
                .brand(brand)
                .category(category)
                .defaultExpirationDays(defaultExpirationDays)
                .requiresApiRetry(requiresApiRetry)
                .retryAttempts(retryAttempts)
                .lastRetryAttempt(lastRetryAttempt)
                .dataSource(dataSource)
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .build();

        // Then
        assertThat(product.getId()).isEqualTo(id);
        assertThat(product.getUpc()).isEqualTo(upc);
        assertThat(product.getName()).isEqualTo(name);
        assertThat(product.getBrand()).isEqualTo(brand);
        assertThat(product.getCategory()).isEqualTo(category);
        assertThat(product.getDefaultExpirationDays()).isEqualTo(defaultExpirationDays);
        assertThat(product.getRequiresApiRetry()).isEqualTo(requiresApiRetry);
        assertThat(product.getRetryAttempts()).isEqualTo(retryAttempts);
        assertThat(product.getLastRetryAttempt()).isEqualTo(lastRetryAttempt);
        assertThat(product.getDataSource()).isEqualTo(dataSource);
        assertThat(product.getCreatedAt()).isEqualTo(createdAt);
        assertThat(product.getUpdatedAt()).isEqualTo(updatedAt);
    }

    @Test
    @DisplayName("Test Product builder with required fields only")
    void testProductBuilderWithRequiredFields() {
        // Given
        UUID id = UUID.randomUUID();
        String upc = "123456789012";
        String name = "Minimal Product";

        // When
        Product product = Product.builder()
                .id(id)
                .upc(upc)
                .name(name)
                .build();

        // Then
        assertThat(product.getId()).isEqualTo(id);
        assertThat(product.getUpc()).isEqualTo(upc);
        assertThat(product.getName()).isEqualTo(name);
        assertThat(product.getBrand()).isNull();
        assertThat(product.getCategory()).isNull();
        assertThat(product.getDefaultExpirationDays()).isNull();
        assertThat(product.getRequiresApiRetry()).isNull();
        assertThat(product.getRetryAttempts()).isNull();
        assertThat(product.getLastRetryAttempt()).isNull();
        assertThat(product.getDataSource()).isNull();
        assertThat(product.getCreatedAt()).isNull();
        assertThat(product.getUpdatedAt()).isNull();
    }

    @Test
    @DisplayName("Test Product builder with retry fields")
    void testProductBuilderWithRetryFields() {
        // Given
        UUID id = UUID.randomUUID();
        String upc = "123456789012";
        String name = "Manual Entry Product";
        Boolean requiresApiRetry = true;
        Integer retryAttempts = 2;
        LocalDateTime lastRetryAttempt = LocalDateTime.now();
        ProductDataSource dataSource = ProductDataSource.MANUAL;

        // When
        Product product = Product.builder()
                .id(id)
                .upc(upc)
                .name(name)
                .requiresApiRetry(requiresApiRetry)
                .retryAttempts(retryAttempts)
                .lastRetryAttempt(lastRetryAttempt)
                .dataSource(dataSource)
                .build();

        // Then
        assertThat(product.getId()).isEqualTo(id);
        assertThat(product.getUpc()).isEqualTo(upc);
        assertThat(product.getName()).isEqualTo(name);
        assertThat(product.getRequiresApiRetry()).isEqualTo(requiresApiRetry);
        assertThat(product.getRetryAttempts()).isEqualTo(retryAttempts);
        assertThat(product.getLastRetryAttempt()).isEqualTo(lastRetryAttempt);
        assertThat(product.getDataSource()).isEqualTo(dataSource);
    }

    @Test
    @DisplayName("Test no-args constructor")
    void testNoArgsConstructor() {
        // When
        Product product = new Product();

        // Then
        assertThat(product).isNotNull();
        assertThat(product.getId()).isNull();
        assertThat(product.getUpc()).isNull();
        assertThat(product.getName()).isNull();
        assertThat(product.getBrand()).isNull();
        assertThat(product.getCategory()).isNull();
        assertThat(product.getDefaultExpirationDays()).isNull();
        assertThat(product.getRequiresApiRetry()).isNull();
        assertThat(product.getRetryAttempts()).isNull();
        assertThat(product.getLastRetryAttempt()).isNull();
        assertThat(product.getDataSource()).isNull();
        assertThat(product.getCreatedAt()).isNull();
        assertThat(product.getUpdatedAt()).isNull();
    }

    @Test
    @DisplayName("Test all-args constructor")
    void testAllArgsConstructor() {
        // Given
        UUID id = UUID.randomUUID();
        String upc = "123456789012";
        String name = "All Args Product";
        String brand = "All Args Brand";
        String category = "All Args Category";
        Integer defaultExpirationDays = 45;
        Boolean requiresApiRetry = true;
        Integer retryAttempts = 1;
        LocalDateTime lastRetryAttempt = LocalDateTime.now();
        ProductDataSource dataSource = ProductDataSource.HYBRID;
        LocalDateTime createdAt = LocalDateTime.now();
        LocalDateTime updatedAt = LocalDateTime.now();

        // When
        Product product = new Product(id, upc, name, brand, category, defaultExpirationDays, 
                                    requiresApiRetry, retryAttempts, lastRetryAttempt, dataSource, 
                                    createdAt, updatedAt);

        // Then
        assertThat(product.getId()).isEqualTo(id);
        assertThat(product.getUpc()).isEqualTo(upc);
        assertThat(product.getName()).isEqualTo(name);
        assertThat(product.getBrand()).isEqualTo(brand);
        assertThat(product.getCategory()).isEqualTo(category);
        assertThat(product.getDefaultExpirationDays()).isEqualTo(defaultExpirationDays);
        assertThat(product.getRequiresApiRetry()).isEqualTo(requiresApiRetry);
        assertThat(product.getRetryAttempts()).isEqualTo(retryAttempts);
        assertThat(product.getLastRetryAttempt()).isEqualTo(lastRetryAttempt);
        assertThat(product.getDataSource()).isEqualTo(dataSource);
        assertThat(product.getCreatedAt()).isEqualTo(createdAt);
        assertThat(product.getUpdatedAt()).isEqualTo(updatedAt);
    }

    @Test
    @DisplayName("Test onCreate lifecycle callback")
    void testOnCreate() {
        // Given
        Product product = new Product();

        // When
        product.onCreate();

        // Then
        assertThat(product.getCreatedAt()).isNotNull();
        assertThat(product.getUpdatedAt()).isNotNull();
        assertThat(product.getCreatedAt()).isEqualToIgnoringNanos(product.getUpdatedAt());
    }

    @Test
    @DisplayName("Test onUpdate lifecycle callback")
    void testOnUpdate() {
        // Given
        Product product = new Product();
        LocalDateTime fixedCreatedTime = LocalDateTime.of(2025, 1, 1, 12, 0);
        LocalDateTime fixedUpdatedTime = LocalDateTime.of(2025, 1, 1, 12, 30);
        
        try (MockedStatic<LocalDateTime> mockedStatic = mockStatic(LocalDateTime.class)) {
            // First call for onCreate
            mockedStatic.when(LocalDateTime::now).thenReturn(fixedCreatedTime);
            product.onCreate();
            
            // Verify createdAt and updatedAt are both set to fixedCreatedTime
            assertThat(product.getCreatedAt()).isEqualTo(fixedCreatedTime);
            assertThat(product.getUpdatedAt()).isEqualTo(fixedCreatedTime);
            
            // Now mock a later time for onUpdate
            mockedStatic.when(LocalDateTime::now).thenReturn(fixedUpdatedTime);
            
            // When
            product.onUpdate();
            
            // Then
            assertThat(product.getCreatedAt()).isEqualTo(fixedCreatedTime); // Should not change
            assertThat(product.getUpdatedAt()).isEqualTo(fixedUpdatedTime); // Should be updated
        }
    }

    @Test
    @DisplayName("Test equals and hashCode")
    void testEqualsAndHashCode() {
        // Given
        UUID id = UUID.randomUUID();
        
        Product product1 = Product.builder()
                .id(id)
                .upc("123456789012")
                .name("Product 1")
                .brand("Brand 1")
                .build();
        
        Product product2 = Product.builder()
                .id(id) // Same ID
                .upc("987654321098") // Different UPC
                .name("Product 2") // Different name
                .brand("Brand 2") // Different brand
                .build();

        Product product3 = Product.builder()
                .id(UUID.randomUUID()) // Different ID
                .upc("123456789012") // Same UPC
                .name("Product 1") // Same name
                .brand("Brand 1") // Same brand
                .build();

        // Then - With @EqualsAndHashCode(of = "id"), only ID matters for equality
        assertThat(product1).isEqualTo(product1); // Same instance
        assertThat(product1).isEqualTo(product2); // Same ID, so should be equal
        assertThat(product1).isNotEqualTo(product3); // Different ID
        
        assertThat(product1.hashCode()).isEqualTo(product2.hashCode()); // Same ID, so same hash
        assertThat(product1.hashCode()).isNotEqualTo(product3.hashCode()); // Different ID
    }

    @Test
    @DisplayName("Test toString method")
    void testToString() {
        // Given
        UUID id = UUID.randomUUID();
        String upc = "123456789012";
        String name = "Test Product";
        String brand = "Test Brand";
        
        Product product = Product.builder()
                .id(id)
                .upc(upc)
                .name(name)
                .brand(brand)
                .build();

        // When
        String toStringResult = product.toString();

        // Then
        assertThat(toStringResult).contains("Product");
        assertThat(toStringResult).contains(id.toString());
        assertThat(toStringResult).contains(upc);
        assertThat(toStringResult).contains(name);
        assertThat(toStringResult).contains(brand);
    }

    @Test
    @DisplayName("Test product with UPC validation scenarios")
    void testProductWithUpcValidationScenarios() {
        // Given - Valid UPC formats
        UUID id = UUID.randomUUID();
        String upc12 = "123456789012"; // UPC-A
        String upc13 = "1234567890123"; // EAN-13
        String upc8 = "12345678"; // UPC-E

        // When - Create products with different UPC formats
        Product product12 = Product.builder().id(id).upc(upc12).name("UPC-12 Product").build();
        Product product13 = Product.builder().id(UUID.randomUUID()).upc(upc13).name("UPC-13 Product").build();
        Product product8 = Product.builder().id(UUID.randomUUID()).upc(upc8).name("UPC-8 Product").build();

        // Then
        assertThat(product12.getUpc()).isEqualTo(upc12);
        assertThat(product13.getUpc()).isEqualTo(upc13);
        assertThat(product8.getUpc()).isEqualTo(upc8);
    }

    @Test
    @DisplayName("Test product with expiration days scenarios")
    void testProductWithExpirationDaysScenarios() {
        // Given
        UUID id = UUID.randomUUID();
        
        // When - Create products with different expiration scenarios
        Product freshProduct = Product.builder()
                .id(id)
                .name("Fresh Bread")
                .defaultExpirationDays(3)
                .build();
                
        Product cannedProduct = Product.builder()
                .id(UUID.randomUUID())
                .name("Canned Beans")
                .defaultExpirationDays(365)
                .build();
                
        Product frozenProduct = Product.builder()
                .id(UUID.randomUUID())
                .name("Frozen Vegetables")
                .defaultExpirationDays(180)
                .build();

        // Then
        assertThat(freshProduct.getDefaultExpirationDays()).isEqualTo(3);
        assertThat(cannedProduct.getDefaultExpirationDays()).isEqualTo(365);
        assertThat(frozenProduct.getDefaultExpirationDays()).isEqualTo(180);
    }

    @Test
    @DisplayName("Test product data source scenarios")
    void testProductDataSourceScenarios() {
        // Given
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        UUID id3 = UUID.randomUUID();
        
        // When - Create products with different data sources
        Product apiProduct = Product.builder()
                .id(id1)
                .name("API Product")
                .dataSource(ProductDataSource.UPC_API)
                .requiresApiRetry(false)
                .build();
                
        Product manualProduct = Product.builder()
                .id(id2)
                .name("Manual Product")
                .dataSource(ProductDataSource.MANUAL)
                .requiresApiRetry(true)
                .retryAttempts(0)
                .build();
                
        Product hybridProduct = Product.builder()
                .id(id3)
                .name("Hybrid Product")
                .dataSource(ProductDataSource.HYBRID)
                .requiresApiRetry(false)
                .retryAttempts(2)
                .build();

        // Then
        assertThat(apiProduct.getDataSource()).isEqualTo(ProductDataSource.UPC_API);
        assertThat(apiProduct.getRequiresApiRetry()).isFalse();
        
        assertThat(manualProduct.getDataSource()).isEqualTo(ProductDataSource.MANUAL);
        assertThat(manualProduct.getRequiresApiRetry()).isTrue();
        assertThat(manualProduct.getRetryAttempts()).isEqualTo(0);
        
        assertThat(hybridProduct.getDataSource()).isEqualTo(ProductDataSource.HYBRID);
        assertThat(hybridProduct.getRequiresApiRetry()).isFalse();
        assertThat(hybridProduct.getRetryAttempts()).isEqualTo(2);
    }

    @Test
    @DisplayName("Test product retry management")
    void testProductRetryManagement() {
        // Given
        Product product = Product.builder()
                .id(UUID.randomUUID())
                .name("Retry Test Product")
                .dataSource(ProductDataSource.MANUAL)
                .requiresApiRetry(true)
                .retryAttempts(0)
                .build();
        
        LocalDateTime retryTime = LocalDateTime.now();
        
        // When - Simulate retry attempts
        product.setRetryAttempts(1);
        product.setLastRetryAttempt(retryTime);
        
        // Then
        assertThat(product.getRetryAttempts()).isEqualTo(1);
        assertThat(product.getLastRetryAttempt()).isEqualTo(retryTime);
        assertThat(product.getRequiresApiRetry()).isTrue();
        
        // When - Successful retry
        product.setRequiresApiRetry(false);
        product.setDataSource(ProductDataSource.HYBRID);
        
        // Then
        assertThat(product.getRequiresApiRetry()).isFalse();
        assertThat(product.getDataSource()).isEqualTo(ProductDataSource.HYBRID);
    }

    @Test
    @DisplayName("Test ProductDataSource enum values")
    void testProductDataSourceEnumValues() {
        // Given & When & Then
        assertThat(ProductDataSource.values()).containsExactly(
            ProductDataSource.UPC_API,
            ProductDataSource.MANUAL,
            ProductDataSource.HYBRID
        );
        
        // Test enum string values
        assertThat(ProductDataSource.UPC_API.name()).isEqualTo("UPC_API");
        assertThat(ProductDataSource.MANUAL.name()).isEqualTo("MANUAL");
        assertThat(ProductDataSource.HYBRID.name()).isEqualTo("HYBRID");
    }
}