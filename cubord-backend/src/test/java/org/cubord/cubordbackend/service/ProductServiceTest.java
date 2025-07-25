package org.cubord.cubordbackend.service;

import org.cubord.cubordbackend.domain.Product;
import org.cubord.cubordbackend.domain.ProductDataSource;
import org.cubord.cubordbackend.domain.User;
import org.cubord.cubordbackend.dto.ProductRequest;
import org.cubord.cubordbackend.dto.ProductResponse;
import org.cubord.cubordbackend.dto.ProductUpdateRequest;
import org.cubord.cubordbackend.exception.ConflictException;
import org.cubord.cubordbackend.exception.NotFoundException;
import org.cubord.cubordbackend.repository.ProductRepository;
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
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.LocalDateTime;
import java.util.*;

import org.springframework.security.access.AccessDeniedException;
import org.cubord.cubordbackend.domain.UserRole;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Product Service Tests")
class ProductServiceTest {

    @Mock private ProductRepository productRepository;
    @Mock private UpcApiService upcApiService;
    @Mock private UserService userService;
    @Mock private JwtAuthenticationToken validToken;
    @Mock private JwtAuthenticationToken invalidToken;

    @InjectMocks
    private ProductService productService;

    // Test data
    private User testUser;
    private User adminUser;
    private Product testProduct;
    private ProductRequest testProductRequest;
    private ProductUpdateRequest testProductUpdateRequest;
    private UUID productId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        productId = UUID.randomUUID();
        userId = UUID.randomUUID();
        
        testUser = User.builder()
                .id(userId)
                .username("testuser")
                .email("test@example.com")
                .displayName("Test User")
                .role(UserRole.USER)  // Default to regular user
                .createdAt(LocalDateTime.now())
                .build();
        
        adminUser = User.builder()
                .id(UUID.randomUUID())
                .username("adminuser")
                .email("admin@example.com")
                .displayName("Admin User")
                .role(UserRole.ADMIN)
                .createdAt(LocalDateTime.now())
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

    // Helper method to set up authentication behavior
    private void setupValidAuthentication() {
        when(userService.getCurrentUser(validToken)).thenReturn(testUser);
    }

    private void setupInvalidAuthentication() {
        when(userService.getCurrentUser(invalidToken)).thenThrow(new NotFoundException("User not found"));
    }

    private void setupAdminAuthentication() {
        when(userService.getCurrentUser(validToken)).thenReturn(adminUser);
    }

    @Nested
    @DisplayName("Authentication Tests")
    class AuthenticationTests {

        @Test
        @DisplayName("Should reject operations with null token")
        void shouldRejectOperationsWithNullToken() {
            assertThatThrownBy(() -> productService.createProduct(testProductRequest, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("token cannot be null");

            assertThatThrownBy(() -> productService.getProductById(productId, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("token cannot be null");

            assertThatThrownBy(() -> productService.deleteProduct(productId, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("token cannot be null");
        }

        @Test
        @DisplayName("Should reject operations with invalid token")
        void shouldRejectOperationsWithInvalidToken() {
            setupInvalidAuthentication();
            
            assertThatThrownBy(() -> productService.createProduct(testProductRequest, invalidToken))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("User not found");

            assertThatThrownBy(() -> productService.getProductById(productId, invalidToken))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("User not found");
        }
    }

    @Nested
    @DisplayName("Product CRUD Operations")
    class ProductCrudOperations {

        @Test
        @DisplayName("Should create product manually when UPC doesn't exist")
        void shouldCreateProductManuallyWhenUpcDoesntExist() {
            // Given
            setupValidAuthentication();
            when(productRepository.findByUpc(testProductRequest.getUpc())).thenReturn(Optional.empty());
            when(productRepository.save(any(Product.class))).thenReturn(testProduct);
            when(upcApiService.fetchProductData(anyString())).thenThrow(new RuntimeException("API unavailable"));

            // When
            ProductResponse response = productService.createProduct(testProductRequest, validToken);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getUpc()).isEqualTo(testProductRequest.getUpc());
            verify(userService).getCurrentUser(validToken);
            verify(productRepository).save(any(Product.class));
        }

        @Test
        @DisplayName("Should throw ConflictException when UPC already exists")
        void shouldThrowConflictExceptionWhenUpcExists() {
            // Given
            setupValidAuthentication();
            when(productRepository.findByUpc(testProductRequest.getUpc())).thenReturn(Optional.of(testProduct));

            // When & Then
            assertThatThrownBy(() -> productService.createProduct(testProductRequest, validToken))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("already exists");
        }

        @Test
        @DisplayName("Should get product by ID successfully")
        void shouldGetProductByIdSuccessfully() {
            // Given
            setupValidAuthentication();
            when(productRepository.findById(productId)).thenReturn(Optional.of(testProduct));

            // When
            ProductResponse response = productService.getProductById(productId, validToken);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo(productId);
            verify(userService).getCurrentUser(validToken);
        }

        @Test
        @DisplayName("Should throw NotFoundException when product doesn't exist")
        void shouldThrowNotFoundExceptionWhenProductDoesntExist() {
            // Given
            setupValidAuthentication();
            when(productRepository.findById(productId)).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> productService.getProductById(productId, validToken))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("Product not found");
        }

        @Test
        @DisplayName("Should update product successfully with admin access")
        void shouldUpdateProductSuccessfully() {
            // Given - Use admin authentication for update operations
            setupAdminAuthentication();
            when(productRepository.findById(productId)).thenReturn(Optional.of(testProduct));
            when(productRepository.save(any(Product.class))).thenReturn(testProduct);

            // When
            ProductResponse response = productService.updateProduct(productId, testProductUpdateRequest, validToken);

            // Then
            assertThat(response).isNotNull();
            verify(userService).getCurrentUser(validToken);
            verify(productRepository).save(any(Product.class));
        }

        @Test
        @DisplayName("Should delete product successfully with admin access")
        void shouldDeleteProductSuccessfully() {
            // Given - Use admin authentication for delete operations
            setupAdminAuthentication();
            when(productRepository.findById(productId)).thenReturn(Optional.of(testProduct));

            // When
            productService.deleteProduct(productId, validToken);

            // Then
            verify(userService).getCurrentUser(validToken);
            verify(productRepository).delete(testProduct);
        }
    }

    @Nested
    @DisplayName("Product Search and Queries")
    class ProductSearchAndQueries {

        @Test
        @DisplayName("Should search products by name")
        void shouldSearchProductsByName() {
            // Given
            setupValidAuthentication();
            String searchTerm = "test";
            when(productRepository.findByNameContainingIgnoreCase(searchTerm)).thenReturn(List.of(testProduct));

            // When
            List<ProductResponse> responses = productService.searchProductsByName(searchTerm, validToken);

            // Then
            assertThat(responses).hasSize(1);
            verify(userService).getCurrentUser(validToken);
        }

        @Test
        @DisplayName("Should get products by category")
        void shouldGetProductsByCategory() {
            // Given
            setupValidAuthentication();
            String category = "Test Category";
            when(productRepository.findByCategory(category)).thenReturn(List.of(testProduct));

            // When
            List<ProductResponse> responses = productService.getProductsByCategory(category, validToken);

            // Then
            assertThat(responses).hasSize(1);
            verify(userService).getCurrentUser(validToken);
        }

        @Test
        @DisplayName("Should get all products with pagination")
        void shouldGetAllProductsWithPagination() {
            // Given
            setupValidAuthentication();
            Pageable pageable = PageRequest.of(0, 10);
            Page<Product> productPage = new PageImpl<>(List.of(testProduct));
            when(productRepository.findAll(pageable)).thenReturn(productPage);

            // When
            Page<ProductResponse> responses = productService.getAllProducts(pageable, validToken);

            // Then
            assertThat(responses.getContent()).hasSize(1);
            verify(userService).getCurrentUser(validToken);
        }

        @Test
        @DisplayName("Should get product statistics")
        void shouldGetProductStatistics() {
            // Given
            setupValidAuthentication();
            when(productRepository.count()).thenReturn(100L);
            when(productRepository.countByDataSource(any(ProductDataSource.class))).thenReturn(30L);
            when(productRepository.countByRequiresApiRetryTrue()).thenReturn(15L);

            // When
            Map<String, Long> stats = productService.getProductStatistics(validToken);

            // Then
            assertThat(stats).containsKeys("total", "apiSource", "manualSource", "hybridSource", "pendingRetry");
            verify(userService).getCurrentUser(validToken);
        }
    }

    @Nested
    @DisplayName("Patch Operations")
    class PatchOperations {

        @Test
        @DisplayName("Should patch product successfully with admin access")
        void shouldPatchProductSuccessfully() {
            // Given - Use admin authentication for patch operations
            setupAdminAuthentication();
            Map<String, Object> patchData = Map.of("name", "Updated Name", "brand", "Updated Brand");
            when(productRepository.findById(productId)).thenReturn(Optional.of(testProduct));
            when(productRepository.save(any(Product.class))).thenReturn(testProduct);

            // When
            ProductResponse response = productService.patchProduct(productId, patchData, validToken);

            // Then
            assertThat(response).isNotNull();
            verify(userService).getCurrentUser(validToken);
            verify(productRepository).save(any(Product.class));
        }

        @Test
        @DisplayName("Should throw exception for invalid patch field")
        void shouldThrowExceptionForInvalidPatchField() {
            // Given - Use admin authentication since authorization is checked first
            setupAdminAuthentication();
            Map<String, Object> patchData = Map.of("invalidField", "value");
            when(productRepository.findById(productId)).thenReturn(Optional.of(testProduct));

            // When & Then
            assertThatThrownBy(() -> productService.patchProduct(productId, patchData, validToken))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid field");
        }

        @Test
        @DisplayName("Should throw exception for empty patch data")
        void shouldThrowExceptionForEmptyPatchData() {
            // When & Then
            assertThatThrownBy(() -> productService.patchProduct(productId, Map.of(), validToken))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Patch data cannot be empty");
        }
    }

    @Nested
    @DisplayName("Authorization Tests")
    class AuthorizationTests {

        @Test
        @DisplayName("Should allow regular user to create product")
        void shouldAllowRegularUserToCreateProduct() {
            // Given
            setupValidAuthentication(); // This sets up regular user
            when(productRepository.findByUpc(testProductRequest.getUpc())).thenReturn(Optional.empty());
            when(productRepository.save(any(Product.class))).thenReturn(testProduct);
            when(upcApiService.fetchProductData(anyString())).thenThrow(new RuntimeException("API unavailable"));

            // When
            ProductResponse response = productService.createProduct(testProductRequest, validToken);

            // Then
            assertThat(response).isNotNull();
            verify(productRepository).save(any(Product.class));
        }

        @Test
        @DisplayName("Should allow admin to create product")
        void shouldAllowAdminToCreateProduct() {
            // Given
            setupAdminAuthentication();
            when(productRepository.findByUpc(testProductRequest.getUpc())).thenReturn(Optional.empty());
            when(productRepository.save(any(Product.class))).thenReturn(testProduct);
            when(upcApiService.fetchProductData(anyString())).thenThrow(new RuntimeException("API unavailable"));

            // When
            ProductResponse response = productService.createProduct(testProductRequest, validToken);

            // Then
            assertThat(response).isNotNull();
            verify(productRepository).save(any(Product.class));
        }

        @Test
        @DisplayName("Should allow admin to update product")
        void shouldAllowAdminToUpdateProduct() {
            // Given
            setupAdminAuthentication();
            when(productRepository.findById(productId)).thenReturn(Optional.of(testProduct));
            when(productRepository.save(any(Product.class))).thenReturn(testProduct);

            // When
            ProductResponse response = productService.updateProduct(productId, testProductUpdateRequest, validToken);

            // Then
            assertThat(response).isNotNull();
            verify(productRepository).save(any(Product.class));
        }

        @Test
        @DisplayName("Should deny regular user from updating product")
        void shouldDenyRegularUserFromUpdatingProduct() {
            // Given
            setupValidAuthentication(); // This sets up regular user
            // Don't stub repository methods since authorization check happens first

            // When & Then
            assertThatThrownBy(() -> productService.updateProduct(productId, testProductUpdateRequest, validToken))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("Only administrators can update products");
        
            verify(productRepository, never()).save(any(Product.class));
        }

        @Test
        @DisplayName("Should allow admin to delete product")
        void shouldAllowAdminToDeleteProduct() {
            // Given
            setupAdminAuthentication();
            when(productRepository.findById(productId)).thenReturn(Optional.of(testProduct));

            // When
            productService.deleteProduct(productId, validToken);

            // Then
            verify(productRepository).delete(testProduct);
        }

        @Test
        @DisplayName("Should deny regular user from deleting product")
        void shouldDenyRegularUserFromDeletingProduct() {
            // Given
            setupValidAuthentication(); // This sets up regular user
            // Don't stub repository methods since authorization check happens first

            // When & Then
            assertThatThrownBy(() -> productService.deleteProduct(productId, validToken))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("Only administrators can delete products");
        
            verify(productRepository, never()).delete(any(Product.class));
        }

        @Test
        @DisplayName("Should allow admin to patch product")
        void shouldAllowAdminToPatchProduct() {
            // Given
            setupAdminAuthentication();
            when(productRepository.findById(productId)).thenReturn(Optional.of(testProduct));
            when(productRepository.save(any(Product.class))).thenReturn(testProduct);
            Map<String, Object> patchData = Map.of("name", "Updated Product Name");

            // When
            ProductResponse response = productService.patchProduct(productId, patchData, validToken);

            // Then
            assertThat(response).isNotNull();
            verify(productRepository).save(any(Product.class));
        }

        @Test
        @DisplayName("Should deny regular user from patching product")
        void shouldDenyRegularUserFromPatchingProduct() {
            // Given
            setupValidAuthentication(); // This sets up regular user
            // Don't stub repository methods since authorization check happens first
            Map<String, Object> patchData = Map.of("name", "Updated Product Name");

            // When & Then
            assertThatThrownBy(() -> productService.patchProduct(productId, patchData, validToken))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("Only administrators can update products");
        
            verify(productRepository, never()).save(any(Product.class));
        }

        @Test
        @DisplayName("Should allow regular user to read product")
        void shouldAllowRegularUserToReadProduct() {
            // Given
            setupValidAuthentication(); // This sets up regular user
            when(productRepository.findById(productId)).thenReturn(Optional.of(testProduct));

            // When
            ProductResponse response = productService.getProductById(productId, validToken);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo(productId);
        }

        @Test
        @DisplayName("Should allow admin to bulk import products")
        void shouldAllowAdminToBulkImportProducts() {
            // Given
            setupAdminAuthentication();
            List<ProductRequest> requests = List.of(testProductRequest);
            when(productRepository.findByUpc(testProductRequest.getUpc())).thenReturn(Optional.empty());
            when(productRepository.saveAll(any())).thenReturn(List.of(testProduct));

            // When
            List<ProductResponse> responses = productService.bulkImportProducts(requests, validToken);

            // Then
            assertThat(responses).hasSize(1);
            verify(productRepository).saveAll(any());
        }

        @Test
        @DisplayName("Should deny regular user from bulk importing products")
        void shouldDenyRegularUserFromBulkImportingProducts() {
            // Given
            setupValidAuthentication(); // This sets up regular user
            List<ProductRequest> requests = List.of(testProductRequest);

            // When & Then
            assertThatThrownBy(() -> productService.bulkImportProducts(requests, validToken))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Only administrators can bulk import products");
        
            verify(productRepository, never()).saveAll(any());
        }

        @Test
        @DisplayName("Should allow admin to bulk delete products")
        void shouldAllowAdminToBulkDeleteProducts() {
            // Given
            setupAdminAuthentication();
            List<UUID> productIds = List.of(productId);
            when(productRepository.findAllById(productIds)).thenReturn(List.of(testProduct));

            // When
            int deletedCount = productService.bulkDeleteProducts(productIds, validToken);

            // Then
            assertThat(deletedCount).isEqualTo(1);
            verify(productRepository).deleteAll(List.of(testProduct));
        }

        @Test
        @DisplayName("Should deny regular user from bulk deleting products")
        void shouldDenyRegularUserFromBulkDeletingProducts() {
            // Given
            setupValidAuthentication(); // This sets up regular user
            List<UUID> productIds = List.of(productId);

            // When & Then
            assertThatThrownBy(() -> productService.bulkDeleteProducts(productIds, validToken))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Only administrators can bulk delete products");
        
            verify(productRepository, never()).deleteAll(any());
        }
    }
}