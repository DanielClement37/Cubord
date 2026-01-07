package org.cubord.cubordbackend.service;

import org.cubord.cubordbackend.dto.product.ProductRequest;
import org.cubord.cubordbackend.dto.product.ProductResponse;
import org.cubord.cubordbackend.dto.product.ProductUpdateRequest;
import org.cubord.cubordbackend.domain.Product;
import org.cubord.cubordbackend.domain.ProductDataSource;
import org.cubord.cubordbackend.domain.User;
import org.cubord.cubordbackend.domain.UserRole;
import org.cubord.cubordbackend.exception.*;
import org.cubord.cubordbackend.repository.ProductRepository;
import org.cubord.cubordbackend.security.SecurityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Comprehensive test suite for ProductService following the modernized security architecture.
 *
 * <p>Test Structure:</p>
 * <ul>
 *   <li>No token validation tests (handled by Spring Security filters)</li>
 *   <li>Authorization tested via SecurityService mocking</li>
 *   <li>Focus on business logic and data validation</li>
 *   <li>Clear separation between user and admin operations</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ProductService Tests")
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private UpcApiService upcApiService;

    @Mock
    private SecurityService securityService;

    @InjectMocks
    private ProductService productService;

    // Test data
    private UUID testUserId;
    private UUID adminUserId;
    private UUID productId;
    private User testUser;
    private User adminUser;
    private Product testProduct;
    private ProductRequest testProductRequest;
    private ProductUpdateRequest testProductUpdateRequest;

    @BeforeEach
    void setUp() {
        testUserId = UUID.randomUUID();
        adminUserId = UUID.randomUUID();
        productId = UUID.randomUUID();

        testUser = User.builder()
                .id(testUserId)
                .username("testuser")
                .email("test@example.com")
                .displayName("Test User")
                .role(UserRole.USER)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        adminUser = User.builder()
                .id(adminUserId)
                .username("adminuser")
                .email("admin@example.com")
                .displayName("Admin User")
                .role(UserRole.ADMIN)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        testProduct = Product.builder()
                .id(productId)
                .upc("123456789012")
                .name("Test Product")
                .brand("Test Brand")
                .category("Test Category")
                .defaultExpirationDays(30)
                .dataSource(ProductDataSource.MANUAL)
                .requiresApiRetry(false)
                .retryAttempts(0)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        testProductRequest = ProductRequest.builder()
                .upc("123456789012")
                .name("Test Product")
                .brand("Test Brand")
                .category("Test Category")
                .defaultExpirationDays(30)
                .build();

        testProductUpdateRequest = ProductUpdateRequest.builder()
                .name("Updated Product")
                .brand("Updated Brand")
                .category("Updated Category")
                .defaultExpirationDays(45)
                .build();
    }

    // ==================== Test Utilities ====================

    private void mockAuthenticatedUser(UUID userId) {
        when(securityService.getCurrentUserId()).thenReturn(userId);
    }

    private void mockAuthenticatedAdmin() {
        when(securityService.getCurrentUserId()).thenReturn(adminUserId);
        when(securityService.getCurrentUser()).thenReturn(adminUser);
        when(securityService.isAdmin()).thenReturn(true);
    }

    private void mockAuthenticatedRegularUser() {
        when(securityService.getCurrentUserId()).thenReturn(testUserId);
        when(securityService.getCurrentUser()).thenReturn(testUser);
        when(securityService.isAdmin()).thenReturn(false);
    }

    // ==================== Query Operation Tests ====================

    @Nested
    @DisplayName("getProductById")
    class GetProductByIdTests {

        @Test
        @DisplayName("should retrieve product successfully when authenticated")
        void shouldGetProductByIdSuccessfully() {
            // Given
            mockAuthenticatedUser(testUserId);
            when(productRepository.findById(eq(productId))).thenReturn(Optional.of(testProduct));

            // When
            ProductResponse response = productService.getProductById(productId);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo(productId);
            assertThat(response.getUpc()).isEqualTo(testProduct.getUpc());
            assertThat(response.getName()).isEqualTo(testProduct.getName());

            verify(securityService).getCurrentUserId();
            verify(productRepository).findById(eq(productId));
        }

        @Test
        @DisplayName("should throw ValidationException when product ID is null")
        void shouldThrowValidationExceptionWhenProductIdIsNull() {
            // When/Then
            assertThatThrownBy(() -> productService.getProductById(null))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Product ID cannot be null");

            verify(securityService, never()).getCurrentUserId();
            verify(productRepository, never()).findById(any());
        }

        @Test
        @DisplayName("should throw NotFoundException when product doesn't exist")
        void shouldThrowNotFoundExceptionWhenProductDoesntExist() {
            // Given
            mockAuthenticatedUser(testUserId);
            when(productRepository.findById(eq(productId))).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> productService.getProductById(productId))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("Product not found");

            verify(securityService).getCurrentUserId();
            verify(productRepository).findById(eq(productId));
        }
    }

    @Nested
    @DisplayName("getProductByUpc")
    class GetProductByUpcTests {

        @Test
        @DisplayName("should retrieve product by UPC successfully")
        void shouldGetProductByUpcSuccessfully() {
            // Given
            String upc = "123456789012";
            mockAuthenticatedUser(testUserId);
            when(productRepository.findByUpc(eq(upc))).thenReturn(Optional.of(testProduct));

            // When
            ProductResponse response = productService.getProductByUpc(upc);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getUpc()).isEqualTo(upc);

            verify(securityService).getCurrentUserId();
            verify(productRepository).findByUpc(eq(upc));
        }

        @Test
        @DisplayName("should throw ValidationException when UPC is null")
        void shouldThrowValidationExceptionWhenUpcIsNull() {
            // When/Then
            assertThatThrownBy(() -> productService.getProductByUpc(null))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("UPC cannot be null or empty");

            verify(securityService, never()).getCurrentUserId();
            verify(productRepository, never()).findByUpc(any());
        }

        @Test
        @DisplayName("should throw ValidationException when UPC is empty")
        void shouldThrowValidationExceptionWhenUpcIsEmpty() {
            // When/Then
            assertThatThrownBy(() -> productService.getProductByUpc("   "))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("UPC cannot be null or empty");

            verify(securityService, never()).getCurrentUserId();
            verify(productRepository, never()).findByUpc(any());
        }

        @Test
        @DisplayName("should throw NotFoundException when product with UPC doesn't exist")
        void shouldThrowNotFoundExceptionWhenProductWithUpcDoesntExist() {
            // Given
            String upc = "999999999999";
            mockAuthenticatedUser(testUserId);
            when(productRepository.findByUpc(eq(upc))).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> productService.getProductByUpc(upc))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("Product not found");

            verify(securityService).getCurrentUserId();
            verify(productRepository).findByUpc(eq(upc));
        }
    }

    @Nested
    @DisplayName("getAllProducts")
    class GetAllProductsTests {

        @Test
        @DisplayName("should retrieve all products with pagination")
        void shouldGetAllProductsWithPagination() {
            // Given
            mockAuthenticatedUser(testUserId);
            Pageable pageable = PageRequest.of(0, 10);
            Page<Product> productPage = new PageImpl<>(List.of(testProduct));
            when(productRepository.findAll(eq(pageable))).thenReturn(productPage);

            // When
            Page<ProductResponse> response = productService.getAllProducts(pageable);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getContent()).hasSize(1);
            assertThat(response.getContent().get(0).getId()).isEqualTo(productId);

            verify(securityService).getCurrentUserId();
            verify(productRepository).findAll(eq(pageable));
        }

        @Test
        @DisplayName("should return empty page when no products exist")
        void shouldReturnEmptyPageWhenNoProducts() {
            // Given
            mockAuthenticatedUser(testUserId);
            Pageable pageable = PageRequest.of(0, 10);
            Page<Product> emptyPage = new PageImpl<>(Collections.emptyList());
            when(productRepository.findAll(eq(pageable))).thenReturn(emptyPage);

            // When
            Page<ProductResponse> response = productService.getAllProducts(pageable);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getContent()).isEmpty();

            verify(securityService).getCurrentUserId();
            verify(productRepository).findAll(eq(pageable));
        }
    }

    @Nested
    @DisplayName("searchProducts")
    class SearchProductsTests {

        @Test
        @DisplayName("should search products successfully")
        void shouldSearchProductsSuccessfully() {
            // Given
            String query = "test";
            mockAuthenticatedUser(testUserId);
            Pageable pageable = PageRequest.of(0, 10);
            Page<Product> productPage = new PageImpl<>(List.of(testProduct));
            when(productRepository.searchByNameBrandOrCategory(eq(query), eq(pageable)))
                    .thenReturn(productPage);

            // When
            Page<ProductResponse> response = productService.searchProducts(query, pageable);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getContent()).hasSize(1);

            verify(securityService).getCurrentUserId();
            verify(productRepository).searchByNameBrandOrCategory(eq(query), eq(pageable));
        }

        @Test
        @DisplayName("should throw ValidationException when query is null")
        void shouldThrowValidationExceptionWhenQueryIsNull() {
            // Given
            Pageable pageable = PageRequest.of(0, 10);

            // When/Then
            assertThatThrownBy(() -> productService.searchProducts(null, pageable))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Search query cannot be null or empty");

            verify(securityService, never()).getCurrentUserId();
            verify(productRepository, never()).searchByNameBrandOrCategory(any(), any());
        }

        @Test
        @DisplayName("should throw ValidationException when query is empty")
        void shouldThrowValidationExceptionWhenQueryIsEmpty() {
            // Given
            Pageable pageable = PageRequest.of(0, 10);

            // When/Then
            assertThatThrownBy(() -> productService.searchProducts("   ", pageable))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Search query cannot be null or empty");

            verify(securityService, never()).getCurrentUserId();
            verify(productRepository, never()).searchByNameBrandOrCategory(any(), any());
        }
    }

    // ==================== Create Operation Tests ====================

    @Nested
    @DisplayName("createProduct")
    class CreateProductTests {

        @Test
        @DisplayName("should create product with API enrichment successfully")
        void shouldCreateProductWithApiEnrichment() {
            // Given
            mockAuthenticatedUser(testUserId);
            when(productRepository.findByUpc(eq(testProductRequest.getUpc()))).thenReturn(Optional.empty());

            ProductResponse apiResponse = ProductResponse.builder()
                    .upc(testProductRequest.getUpc())
                    .name("API Product Name")
                    .brand("API Brand")
                    .category("API Category")
                    .build();
            when(upcApiService.fetchProductData(eq(testProductRequest.getUpc()))).thenReturn(apiResponse);
            when(productRepository.save(any(Product.class))).thenAnswer(inv -> {
                Product p = inv.getArgument(0);
                p.setId(productId);
                return p;
            });

            // When
            ProductResponse response = productService.createProduct(testProductRequest);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getUpc()).isEqualTo(testProductRequest.getUpc());

            verify(securityService).getCurrentUserId();
            verify(productRepository).findByUpc(eq(testProductRequest.getUpc()));
            verify(upcApiService).fetchProductData(eq(testProductRequest.getUpc()));
            verify(productRepository).save(argThat(product ->
                    product.getDataSource() == ProductDataSource.OPEN_FOOD_FACTS &&
                            !product.getRequiresApiRetry()
            ));
        }

        @Test
        @DisplayName("should create manual product when API fails")
        void shouldCreateManualProductWhenApiFails() {
            // Given
            mockAuthenticatedUser(testUserId);
            when(productRepository.findByUpc(eq(testProductRequest.getUpc()))).thenReturn(Optional.empty());
            when(upcApiService.fetchProductData(eq(testProductRequest.getUpc())))
                    .thenThrow(new RuntimeException("API unavailable"));
            when(productRepository.save(any(Product.class))).thenAnswer(inv -> {
                Product p = inv.getArgument(0);
                p.setId(productId);
                return p;
            });

            // When
            ProductResponse response = productService.createProduct(testProductRequest);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getUpc()).isEqualTo(testProductRequest.getUpc());

            verify(securityService).getCurrentUserId();
            verify(productRepository).save(argThat(product ->
                    product.getDataSource() == ProductDataSource.MANUAL &&
                            product.getRequiresApiRetry() &&
                            product.getRetryAttempts() == 0
            ));
        }

        @Test
        @DisplayName("should throw ValidationException when request is null")
        void shouldThrowValidationExceptionWhenRequestIsNull() {
            // When/Then
            assertThatThrownBy(() -> productService.createProduct(null))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Product request cannot be null");

            verify(securityService, never()).getCurrentUserId();
            verify(productRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw ValidationException when UPC is null")
        void shouldThrowValidationExceptionWhenUpcIsNull() {
            // Given
            mockAuthenticatedUser(testUserId);
            ProductRequest invalidRequest = ProductRequest.builder()
                    .name("Test")
                    .build();

            // When/Then
            assertThatThrownBy(() -> productService.createProduct(invalidRequest))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("UPC cannot be null or empty");

            verify(securityService).getCurrentUserId();
            verify(productRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw ConflictException when UPC already exists")
        void shouldThrowConflictExceptionWhenUpcExists() {
            // Given
            mockAuthenticatedUser(testUserId);
            when(productRepository.findByUpc(eq(testProductRequest.getUpc())))
                    .thenReturn(Optional.of(testProduct));

            // When/Then
            assertThatThrownBy(() -> productService.createProduct(testProductRequest))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("Product with UPC '" + testProductRequest.getUpc() + "' already exists");

            verify(securityService).getCurrentUserId();
            verify(productRepository).findByUpc(eq(testProductRequest.getUpc()));
            verify(productRepository, never()).save(any());
        }
    }

    // ==================== Update Operation Tests ====================

    @Nested
    @DisplayName("updateProduct")
    class UpdateProductTests {

        @Test
        @DisplayName("should update product successfully as admin")
        void shouldUpdateProductSuccessfullyAsAdmin() {
            // Given
            when(productRepository.findById(eq(productId))).thenReturn(Optional.of(testProduct));
            when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

            // When
            ProductResponse response = productService.updateProduct(productId, testProductUpdateRequest);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getName()).isEqualTo(testProductUpdateRequest.getName());
            assertThat(response.getBrand()).isEqualTo(testProductUpdateRequest.getBrand());

            verify(securityService).getCurrentUserId();
            verify(productRepository).findById(eq(productId));
            verify(productRepository).save(any(Product.class));
        }

        @Test
        @DisplayName("should throw ValidationException when product ID is null")
        void shouldThrowValidationExceptionWhenProductIdIsNull() {
            // When/Then
            assertThatThrownBy(() -> productService.updateProduct(null, testProductUpdateRequest))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Product ID cannot be null");

            verify(securityService, never()).getCurrentUserId();
            verify(productRepository, never()).findById(any());
        }

        @Test
        @DisplayName("should throw ValidationException when request is null")
        void shouldThrowValidationExceptionWhenRequestIsNull() {
            // When/Then
            assertThatThrownBy(() -> productService.updateProduct(productId, null))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Update request cannot be null");

            verify(securityService, never()).getCurrentUserId();
            verify(productRepository, never()).findById(any());
        }

        @Test
        @DisplayName("should throw NotFoundException when product doesn't exist")
        void shouldThrowNotFoundExceptionWhenProductDoesntExist() {
            // Given
            when(productRepository.findById(eq(productId))).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> productService.updateProduct(productId, testProductUpdateRequest))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("Product not found");

            verify(securityService).getCurrentUserId();
            verify(productRepository).findById(eq(productId));
            verify(productRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("patchProduct")
    class PatchProductTests {

        @Test
        @DisplayName("should patch product successfully as admin")
        void shouldPatchProductSuccessfullyAsAdmin() {
            // Given
            Map<String, Object> patchData = new HashMap<>();
            patchData.put("name", "Patched Name");
            patchData.put("defaultExpirationDays", 60);

            when(productRepository.findById(eq(productId))).thenReturn(Optional.of(testProduct));
            when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

            // When
            ProductResponse response = productService.patchProduct(productId, patchData);

            // Then
            assertThat(response).isNotNull();

            verify(securityService).getCurrentUserId();
            verify(productRepository).findById(eq(productId));
            verify(productRepository).save(argThat(product ->
                    product.getName().equals("Patched Name") &&
                            product.getDefaultExpirationDays() == 60
            ));
        }

        @Test
        @DisplayName("should throw ValidationException when product ID is null")
        void shouldThrowValidationExceptionWhenProductIdIsNull() {
            // Given
            Map<String, Object> patchData = Map.of("name", "Test");

            // When/Then
            assertThatThrownBy(() -> productService.patchProduct(null, patchData))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Product ID cannot be null");

            verify(securityService, never()).getCurrentUserId();
        }

        @Test
        @DisplayName("should throw ValidationException when patch data is null")
        void shouldThrowValidationExceptionWhenPatchDataIsNull() {
            // When/Then
            assertThatThrownBy(() -> productService.patchProduct(productId, null))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Patch data cannot be null or empty");

            verify(securityService, never()).getCurrentUserId();
        }

        @Test
        @DisplayName("should throw ValidationException when patch data is empty")
        void shouldThrowValidationExceptionWhenPatchDataIsEmpty() {
            // When/Then
            assertThatThrownBy(() -> productService.patchProduct(productId, Collections.emptyMap()))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Patch data cannot be null or empty");

            verify(securityService, never()).getCurrentUserId();
        }

        @Test
        @DisplayName("should throw ValidationException for unsupported field")
        void shouldThrowValidationExceptionForUnsupportedField() {
            // Given
            Map<String, Object> patchData = Map.of("invalidField", "value");
            when(productRepository.findById(eq(productId))).thenReturn(Optional.of(testProduct));

            // When/Then
            assertThatThrownBy(() -> productService.patchProduct(productId, patchData))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Unsupported field for patching: invalidField");

            verify(securityService).getCurrentUserId();
            verify(productRepository, never()).save(any());
        }
    }

    // ==================== Delete Operation Tests ====================

    @Nested
    @DisplayName("deleteProduct")
    class DeleteProductTests {

        @Test
        @DisplayName("should delete product successfully as admin")
        void shouldDeleteProductSuccessfullyAsAdmin() {
            // Given
            when(productRepository.findById(eq(productId))).thenReturn(Optional.of(testProduct));
            doNothing().when(productRepository).delete(any(Product.class));

            // When
            productService.deleteProduct(productId);

            // Then
            verify(securityService).getCurrentUserId();
            verify(productRepository).findById(eq(productId));
            verify(productRepository).delete(eq(testProduct));
        }

        @Test
        @DisplayName("should throw ValidationException when product ID is null")
        void shouldThrowValidationExceptionWhenProductIdIsNull() {
            // When/Then
            assertThatThrownBy(() -> productService.deleteProduct(null))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Product ID cannot be null");

            verify(securityService, never()).getCurrentUserId();
            verify(productRepository, never()).delete(any());
        }

        @Test
        @DisplayName("should throw NotFoundException when product doesn't exist")
        void shouldThrowNotFoundExceptionWhenProductDoesntExist() {
            // Given
            when(productRepository.findById(eq(productId))).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> productService.deleteProduct(productId))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("Product not found");

            verify(securityService).getCurrentUserId();
            verify(productRepository).findById(eq(productId));
            verify(productRepository, never()).delete(any());
        }
    }

    // ==================== Retry Operations Tests ====================

    @Nested
    @DisplayName("retryApiEnrichment")
    class RetryApiEnrichmentTests {

        @Test
        @DisplayName("should retry API enrichment successfully as admin")
        void shouldRetryApiEnrichmentSuccessfully() {
            // Given
            Product productToRetry = Product.builder()
                    .id(UUID.randomUUID())
                    .upc("999999999999")
                    .name("Retry Product")
                    .dataSource(ProductDataSource.MANUAL)
                    .requiresApiRetry(true)
                    .retryAttempts(1)
                    .build();

            when(productRepository.findByRequiresApiRetryTrueAndRetryAttemptsLessThan(5))
                    .thenReturn(List.of(productToRetry));

            ProductResponse apiResponse = ProductResponse.builder()
                    .upc(productToRetry.getUpc())
                    .name("API Enriched Name")
                    .build();
            when(upcApiService.fetchProductData(eq(productToRetry.getUpc()))).thenReturn(apiResponse);
            when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

            // When
            int result = productService.retryApiEnrichment();

            // Then
            assertThat(result).isEqualTo(1);

            verify(securityService).getCurrentUserId();
            verify(productRepository).findByRequiresApiRetryTrueAndRetryAttemptsLessThan(5);
            verify(upcApiService).fetchProductData(eq(productToRetry.getUpc()));
            verify(productRepository).save(argThat(product ->
                    product.getDataSource() == ProductDataSource.OPEN_FOOD_FACTS &&
                            !product.getRequiresApiRetry()
            ));
        }

        @Test
        @DisplayName("should increment retry attempts when API fails")
        void shouldIncrementRetryAttemptsWhenApiFails() {
            // Given
            Product productToRetry = Product.builder()
                    .id(UUID.randomUUID())
                    .upc("999999999999")
                    .name("Retry Product")
                    .dataSource(ProductDataSource.MANUAL)
                    .requiresApiRetry(true)
                    .retryAttempts(1)
                    .build();

            when(productRepository.findByRequiresApiRetryTrueAndRetryAttemptsLessThan(5))
                    .thenReturn(List.of(productToRetry));
            when(upcApiService.fetchProductData(eq(productToRetry.getUpc())))
                    .thenThrow(new RuntimeException("API failed"));
            when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

            // When
            int result = productService.retryApiEnrichment();

            // Then
            assertThat(result).isEqualTo(0);

            verify(securityService).getCurrentUserId();
            verify(productRepository).save(argThat(product ->
                    product.getRetryAttempts() == 2 &&
                            product.getRequiresApiRetry()
            ));
        }

        @Test
        @DisplayName("should disable retry after max attempts")
        void shouldDisableRetryAfterMaxAttempts() {
            // Given
            Product productToRetry = Product.builder()
                    .id(UUID.randomUUID())
                    .upc("999999999999")
                    .name("Retry Product")
                    .dataSource(ProductDataSource.MANUAL)
                    .requiresApiRetry(true)
                    .retryAttempts(4) // One below max
                    .build();

            when(productRepository.findByRequiresApiRetryTrueAndRetryAttemptsLessThan(5))
                    .thenReturn(List.of(productToRetry));
            when(upcApiService.fetchProductData(eq(productToRetry.getUpc())))
                    .thenThrow(new RuntimeException("API failed"));
            when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

            // When
            int result = productService.retryApiEnrichment();

            // Then
            assertThat(result).isEqualTo(0);

            verify(securityService).getCurrentUserId();
            verify(productRepository).save(argThat(product ->
                    product.getRetryAttempts() == 5 &&
                            !product.getRequiresApiRetry() // Disabled after max attempts
            ));
        }
    }
}