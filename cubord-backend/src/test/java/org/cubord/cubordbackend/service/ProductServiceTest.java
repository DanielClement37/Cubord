
package org.cubord.cubordbackend.service;

import org.cubord.cubordbackend.domain.*;
import org.cubord.cubordbackend.dto.product.ProductRequest;
import org.cubord.cubordbackend.dto.product.ProductResponse;
import org.cubord.cubordbackend.dto.product.ProductUpdateRequest;
import org.cubord.cubordbackend.exception.*;
import org.cubord.cubordbackend.repository.ProductRepository;
import org.junit.jupiter.api.*;
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

import static org.assertj.core.api.Assertions.*;
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
        reset(productRepository, upcApiService, userService, validToken, invalidToken);

        productId = UUID.randomUUID();
        userId = UUID.randomUUID();

        testUser = User.builder()
                .id(userId)
                .username("testuser")
                .email("test@example.com")
                .displayName("Test User")
                .role(UserRole.USER)
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
        when(userService.getCurrentUser(eq(validToken))).thenReturn(testUser);
    }

    private void setupInvalidAuthentication() {
        when(userService.getCurrentUser(eq(invalidToken))).thenThrow(new NotFoundException("User not found"));
    }

    private void setupAdminAuthentication() {
        when(userService.getCurrentUser(eq(validToken))).thenReturn(adminUser);
    }

    @Nested
    @DisplayName("Authentication Tests")
    class AuthenticationTests {

        @Test
        @DisplayName("Should throw ValidationException when token is null for create")
        void shouldThrowValidationExceptionWhenTokenIsNullForCreate() {
            assertThatThrownBy(() -> productService.createProduct(testProductRequest, null))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("token cannot be null");

            verify(userService, never()).getCurrentUser(any(JwtAuthenticationToken.class));
            verify(productRepository, never()).save(any(Product.class));
        }

        @Test
        @DisplayName("Should throw ValidationException when token is null for getById")
        void shouldThrowValidationExceptionWhenTokenIsNullForGetById() {
            assertThatThrownBy(() -> productService.getProductById(productId, null))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("token cannot be null");

            verify(userService, never()).getCurrentUser(any(JwtAuthenticationToken.class));
            verify(productRepository, never()).findById(any(UUID.class));
        }

        @Test
        @DisplayName("Should throw ValidationException when token is null for delete")
        void shouldThrowValidationExceptionWhenTokenIsNullForDelete() {
            assertThatThrownBy(() -> productService.deleteProduct(productId, null))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("token cannot be null");

            verify(userService, never()).getCurrentUser(any(JwtAuthenticationToken.class));
            verify(productRepository, never()).delete(any(Product.class));
        }

        @Test
        @DisplayName("Should throw ValidationException when request is null for create")
        void shouldThrowValidationExceptionWhenRequestIsNull() {
            assertThatThrownBy(() -> productService.createProduct(null, validToken))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Product request");

            verify(userService, never()).getCurrentUser(any(JwtAuthenticationToken.class));
            verify(productRepository, never()).save(any(Product.class));
        }

        @Test
        @DisplayName("Should throw NotFoundException when user not found")
        void shouldThrowNotFoundExceptionWhenUserNotFound() {
            setupInvalidAuthentication();

            assertThatThrownBy(() -> productService.createProduct(testProductRequest, invalidToken))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("User not found");

            verify(userService).getCurrentUser(eq(invalidToken));
            verify(productRepository, never()).save(any(Product.class));
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
            when(productRepository.findByUpc(eq(testProductRequest.getUpc()))).thenReturn(Optional.empty());
            when(productRepository.save(any(Product.class))).thenReturn(testProduct);
            when(upcApiService.fetchProductData(eq(testProductRequest.getUpc())))
                    .thenThrow(new RuntimeException("API unavailable"));

            // When
            ProductResponse response = productService.createProduct(testProductRequest, validToken);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getUpc()).isEqualTo(testProductRequest.getUpc());
            verify(userService).getCurrentUser(eq(validToken));
            verify(productRepository).findByUpc(eq(testProductRequest.getUpc()));
            verify(productRepository).save(any(Product.class));
        }

        @Test
        @DisplayName("Should throw ConflictException when UPC already exists")
        void shouldThrowConflictExceptionWhenUpcExists() {
            // Given
            setupValidAuthentication();
            when(productRepository.findByUpc(eq(testProductRequest.getUpc()))).thenReturn(Optional.of(testProduct));

            // When & Then
            assertThatThrownBy(() -> productService.createProduct(testProductRequest, validToken))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("Product with UPC '" + testProductRequest.getUpc() + "' already exists");

            verify(userService).getCurrentUser(eq(validToken));
            verify(productRepository).findByUpc(eq(testProductRequest.getUpc()));
            verify(productRepository, never()).save(any(Product.class));
        }

        @Test
        @DisplayName("Should get product by ID successfully")
        void shouldGetProductByIdSuccessfully() {
            // Given
            setupValidAuthentication();
            when(productRepository.findById(eq(productId))).thenReturn(Optional.of(testProduct));

            // When
            ProductResponse response = productService.getProductById(productId, validToken);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo(productId);
            verify(userService).getCurrentUser(eq(validToken));
            verify(productRepository).findById(eq(productId));
        }

        @Test
        @DisplayName("Should throw ValidationException when product ID is null")
        void shouldThrowValidationExceptionWhenProductIdIsNull() {
            assertThatThrownBy(() -> productService.getProductById(null, validToken))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Product ID");

            verify(userService, never()).getCurrentUser(any(JwtAuthenticationToken.class));
            verify(productRepository, never()).findById(any(UUID.class));
        }

        @Test
        @DisplayName("Should throw NotFoundException when product doesn't exist")
        void shouldThrowNotFoundExceptionWhenProductDoesntExist() {
            // Given
            setupValidAuthentication();
            when(productRepository.findById(eq(productId))).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> productService.getProductById(productId, validToken))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("Product not found");

            verify(userService).getCurrentUser(eq(validToken));
            verify(productRepository).findById(eq(productId));
        }

        @Test
        @DisplayName("Should get product by UPC successfully")
        void shouldGetProductByUpcSuccessfully() {
            // Given
            setupValidAuthentication();
            String upc = "123456789012";
            when(productRepository.findByUpc(eq(upc))).thenReturn(Optional.of(testProduct));

            // When
            ProductResponse response = productService.getProductByUpc(upc, validToken);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getUpc()).isEqualTo(upc);
            verify(userService).getCurrentUser(eq(validToken));
            verify(productRepository).findByUpc(eq(upc));
        }

        @Test
        @DisplayName("Should throw ValidationException when UPC is null")
        void shouldThrowValidationExceptionWhenUpcIsNull() {
            assertThatThrownBy(() -> productService.getProductByUpc(null, validToken))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("UPC");

            verify(userService, never()).getCurrentUser(any(JwtAuthenticationToken.class));
            verify(productRepository, never()).findByUpc(any(String.class));
        }

        @Test
        @DisplayName("Should throw NotFoundException when product with UPC doesn't exist")
        void shouldThrowNotFoundExceptionWhenProductWithUpcDoesntExist() {
            // Given
            setupValidAuthentication();
            String upc = "999999999999";
            when(productRepository.findByUpc(eq(upc))).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> productService.getProductByUpc(upc, validToken))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("Product not found");

            verify(userService).getCurrentUser(eq(validToken));
            verify(productRepository).findByUpc(eq(upc));
        }

        @Test
        @DisplayName("Should update product successfully with admin access")
        void shouldUpdateProductSuccessfully() {
            // Given
            setupAdminAuthentication();
            when(productRepository.findById(eq(productId))).thenReturn(Optional.of(testProduct));
            when(productRepository.save(any(Product.class))).thenReturn(testProduct);

            // When
            ProductResponse response = productService.updateProduct(productId, testProductUpdateRequest, validToken);

            // Then
            assertThat(response).isNotNull();
            verify(userService).getCurrentUser(eq(validToken));
            verify(productRepository).findById(eq(productId));
            verify(productRepository).save(any(Product.class));
        }

        @Test
        @DisplayName("Should throw ValidationException when update request is null")
        void shouldThrowValidationExceptionWhenUpdateRequestIsNull() {
            assertThatThrownBy(() -> productService.updateProduct(productId, null, validToken))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Update request");

            verify(userService, never()).getCurrentUser(any(JwtAuthenticationToken.class));
            verify(productRepository, never()).save(any(Product.class));
        }

        @Test
        @DisplayName("Should throw NotFoundException when product not found for update")
        void shouldThrowNotFoundExceptionWhenProductNotFoundForUpdate() {
            // Given
            setupAdminAuthentication();
            when(productRepository.findById(eq(productId))).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> productService.updateProduct(productId, testProductUpdateRequest, validToken))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("Product not found");

            verify(userService).getCurrentUser(eq(validToken));
            verify(productRepository).findById(eq(productId));
            verify(productRepository, never()).save(any(Product.class));
        }

        @Test
        @DisplayName("Should delete product successfully with admin access")
        void shouldDeleteProductSuccessfully() {
            // Given
            setupAdminAuthentication();
            when(productRepository.findById(eq(productId))).thenReturn(Optional.of(testProduct));

            // When
            productService.deleteProduct(productId, validToken);

            // Then
            verify(userService).getCurrentUser(eq(validToken));
            verify(productRepository).findById(eq(productId));
            verify(productRepository).delete(eq(testProduct));
        }

        @Test
        @DisplayName("Should throw NotFoundException when product not found for deletion")
        void shouldThrowNotFoundExceptionWhenProductNotFoundForDeletion() {
            // Given
            setupAdminAuthentication();
            when(productRepository.findById(eq(productId))).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> productService.deleteProduct(productId, validToken))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("Product not found");

            verify(userService).getCurrentUser(eq(validToken));
            verify(productRepository).findById(eq(productId));
            verify(productRepository, never()).delete(any(Product.class));
        }

        @Test
        @DisplayName("Should throw DataIntegrityException when save operation fails")
        void shouldThrowDataIntegrityExceptionWhenSaveOperationFails() {
            // Given
            setupValidAuthentication();
            when(productRepository.findByUpc(eq(testProductRequest.getUpc()))).thenReturn(Optional.empty());
            when(upcApiService.fetchProductData(eq(testProductRequest.getUpc())))
                    .thenThrow(new RuntimeException("API unavailable"));
            when(productRepository.save(any(Product.class)))
                    .thenThrow(new RuntimeException("Database constraint violation"));

            // When & Then
            assertThatThrownBy(() -> productService.createProduct(testProductRequest, validToken))
                    .isInstanceOf(DataIntegrityException.class)
                    .hasMessageContaining("Failed to save product");

            verify(userService).getCurrentUser(eq(validToken));
            verify(productRepository).save(any(Product.class));
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
            when(productRepository.findByNameContainingIgnoreCase(eq(searchTerm)))
                    .thenReturn(List.of(testProduct));

            // When
            List<ProductResponse> responses = productService.searchProductsByName(searchTerm, validToken);

            // Then
            assertThat(responses).hasSize(1);
            assertThat(responses.getFirst().getName()).isEqualTo(testProduct.getName());
            verify(userService).getCurrentUser(eq(validToken));
            verify(productRepository).findByNameContainingIgnoreCase(eq(searchTerm));
        }

        @Test
        @DisplayName("Should throw ValidationException when search term is null")
        void shouldThrowValidationExceptionWhenSearchTermIsNull() {
            assertThatThrownBy(() -> productService.searchProductsByName(null, validToken))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Search term");

            verify(userService, never()).getCurrentUser(any(JwtAuthenticationToken.class));
            verify(productRepository, never()).findByNameContainingIgnoreCase(any(String.class));
        }

        @Test
        @DisplayName("Should throw ValidationException when search term is empty")
        void shouldThrowValidationExceptionWhenSearchTermIsEmpty() {
            assertThatThrownBy(() -> productService.searchProductsByName("   ", validToken))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Search term");

            verify(userService, never()).getCurrentUser(any(JwtAuthenticationToken.class));
            verify(productRepository, never()).findByNameContainingIgnoreCase(any(String.class));
        }

        @Test
        @DisplayName("Should get products by category")
        void shouldGetProductsByCategory() {
            // Given
            setupValidAuthentication();
            String category = "Test Category";
            when(productRepository.findByCategory(eq(category))).thenReturn(List.of(testProduct));

            // When
            List<ProductResponse> responses = productService.getProductsByCategory(category, validToken);

            // Then
            assertThat(responses).hasSize(1);
            assertThat(responses.getFirst().getCategory()).isEqualTo(category);
            verify(userService).getCurrentUser(eq(validToken));
            verify(productRepository).findByCategory(eq(category));
        }

        @Test
        @DisplayName("Should throw ValidationException when category is null")
        void shouldThrowValidationExceptionWhenCategoryIsNull() {
            assertThatThrownBy(() -> productService.getProductsByCategory(null, validToken))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Category");

            verify(userService, never()).getCurrentUser(any(JwtAuthenticationToken.class));
            verify(productRepository, never()).findByCategory(any(String.class));
        }

        @Test
        @DisplayName("Should get products by brand")
        void shouldGetProductsByBrand() {
            // Given
            setupValidAuthentication();
            String brand = "Test Brand";
            when(productRepository.findByBrand(eq(brand))).thenReturn(List.of(testProduct));

            // When
            List<ProductResponse> responses = productService.getProductsByBrand(brand, validToken);

            // Then
            assertThat(responses).hasSize(1);
            assertThat(responses.getFirst().getBrand()).isEqualTo(brand);
            verify(userService).getCurrentUser(eq(validToken));
            verify(productRepository).findByBrand(eq(brand));
        }

        @Test
        @DisplayName("Should throw ValidationException when brand is null")
        void shouldThrowValidationExceptionWhenBrandIsNull() {
            assertThatThrownBy(() -> productService.getProductsByBrand(null, validToken))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Brand");

            verify(userService, never()).getCurrentUser(any(JwtAuthenticationToken.class));
            verify(productRepository, never()).findByBrand(any(String.class));
        }

        @Test
        @DisplayName("Should get all products with pagination")
        void shouldGetAllProductsWithPagination() {
            // Given
            setupValidAuthentication();
            Pageable pageable = PageRequest.of(0, 10);
            Page<Product> productPage = new PageImpl<>(List.of(testProduct));
            when(productRepository.findAll(eq(pageable))).thenReturn(productPage);

            // When
            Page<ProductResponse> responses = productService.getAllProducts(pageable, validToken);

            // Then
            assertThat(responses.getContent()).hasSize(1);
            assertThat(responses.getContent().getFirst().getId()).isEqualTo(productId);
            verify(userService).getCurrentUser(eq(validToken));
            verify(productRepository).findAll(eq(pageable));
        }

        @Test
        @DisplayName("Should throw ValidationException when pageable is null")
        void shouldThrowValidationExceptionWhenPageableIsNull() {
            assertThatThrownBy(() -> productService.getAllProducts(null, validToken))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Pageable");

            verify(userService, never()).getCurrentUser(any(JwtAuthenticationToken.class));
            verify(productRepository, never()).findAll(any(Pageable.class));
        }
    }

    @Nested
    @DisplayName("Patch Operations")
    class PatchOperations {

        @Test
        @DisplayName("Should patch product successfully with admin access")
        void shouldPatchProductSuccessfully() {
            // Given
            setupAdminAuthentication();
            Map<String, Object> patchData = Map.of("name", "Patched Product");
            when(productRepository.findById(eq(productId))).thenReturn(Optional.of(testProduct));
            when(productRepository.save(any(Product.class))).thenReturn(testProduct);

            // When
            ProductResponse response = productService.patchProduct(productId, patchData, validToken);

            // Then
            assertThat(response).isNotNull();
            verify(userService).getCurrentUser(eq(validToken));
            verify(productRepository).findById(eq(productId));
            verify(productRepository).save(any(Product.class));
        }

        @Test
        @DisplayName("Should throw ValidationException when patch data is null")
        void shouldThrowValidationExceptionWhenPatchDataIsNull() {
            assertThatThrownBy(() -> productService.patchProduct(productId, null, validToken))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Patch data");

            verify(userService, never()).getCurrentUser(any(JwtAuthenticationToken.class));
            verify(productRepository, never()).save(any(Product.class));
        }

        @Test
        @DisplayName("Should throw ValidationException when patch data is empty")
        void shouldThrowValidationExceptionWhenPatchDataIsEmpty() {
            Map<String, Object> emptyPatchData = new HashMap<>();

            assertThatThrownBy(() -> productService.patchProduct(productId, emptyPatchData, validToken))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Patch data cannot be empty");

            verify(userService, never()).getCurrentUser(any(JwtAuthenticationToken.class));
            verify(productRepository, never()).save(any(Product.class));
        }

        @Test
        @DisplayName("Should throw NotFoundException when product not found for patch")
        void shouldThrowNotFoundExceptionWhenProductNotFoundForPatch() {
            // Given
            setupAdminAuthentication();
            Map<String, Object> patchData = Map.of("name", "Patched Product");
            when(productRepository.findById(eq(productId))).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> productService.patchProduct(productId, patchData, validToken))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("Product not found");

            verify(userService).getCurrentUser(eq(validToken));
            verify(productRepository).findById(eq(productId));
            verify(productRepository, never()).save(any(Product.class));
        }
    }

    @Nested
    @DisplayName("Authorization Tests")
    class AuthorizationTests {

        @Test
        @DisplayName("Should allow regular user to create product")
        void shouldAllowRegularUserToCreateProduct() {
            // Given
            setupValidAuthentication();
            when(productRepository.findByUpc(eq(testProductRequest.getUpc()))).thenReturn(Optional.empty());
            when(productRepository.save(any(Product.class))).thenReturn(testProduct);
            when(upcApiService.fetchProductData(eq(testProductRequest.getUpc())))
                    .thenThrow(new RuntimeException("API unavailable"));

            // When
            ProductResponse response = productService.createProduct(testProductRequest, validToken);

            // Then
            assertThat(response).isNotNull();
            verify(userService).getCurrentUser(eq(validToken));
            verify(productRepository).save(any(Product.class));
        }

        @Test
        @DisplayName("Should allow admin to create product")
        void shouldAllowAdminToCreateProduct() {
            // Given
            setupAdminAuthentication();
            when(productRepository.findByUpc(eq(testProductRequest.getUpc()))).thenReturn(Optional.empty());
            when(productRepository.save(any(Product.class))).thenReturn(testProduct);
            when(upcApiService.fetchProductData(eq(testProductRequest.getUpc())))
                    .thenThrow(new RuntimeException("API unavailable"));

            // When
            ProductResponse response = productService.createProduct(testProductRequest, validToken);

            // Then
            assertThat(response).isNotNull();
            verify(userService).getCurrentUser(eq(validToken));
            verify(productRepository).save(any(Product.class));
        }

        @Test
        @DisplayName("Should allow admin to update product")
        void shouldAllowAdminToUpdateProduct() {
            // Given
            setupAdminAuthentication();
            when(productRepository.findById(eq(productId))).thenReturn(Optional.of(testProduct));
            when(productRepository.save(any(Product.class))).thenReturn(testProduct);

            // When
            ProductResponse response = productService.updateProduct(productId, testProductUpdateRequest, validToken);

            // Then
            assertThat(response).isNotNull();
            verify(userService).getCurrentUser(eq(validToken));
            verify(productRepository).save(any(Product.class));
        }

        @Test
        @DisplayName("Should deny regular user from updating product")
        void shouldDenyRegularUserFromUpdatingProduct() {
            // Given
            setupValidAuthentication();

            // When & Then
            assertThatThrownBy(() -> productService.updateProduct(productId, testProductUpdateRequest, validToken))
                    .isInstanceOf(InsufficientPermissionException.class)
                    .hasMessageContaining("Only administrators can update products");

            verify(userService).getCurrentUser(eq(validToken));
            verify(productRepository, never()).findById(any(UUID.class));
            verify(productRepository, never()).save(any(Product.class));
        }

        @Test
        @DisplayName("Should allow admin to delete product")
        void shouldAllowAdminToDeleteProduct() {
            // Given
            setupAdminAuthentication();
            when(productRepository.findById(eq(productId))).thenReturn(Optional.of(testProduct));

            // When
            productService.deleteProduct(productId, validToken);

            // Then
            verify(userService).getCurrentUser(eq(validToken));
            verify(productRepository).delete(eq(testProduct));
        }

        @Test
        @DisplayName("Should deny regular user from deleting product")
        void shouldDenyRegularUserFromDeletingProduct() {
            // Given
            setupValidAuthentication();

            // When & Then
            assertThatThrownBy(() -> productService.deleteProduct(productId, validToken))
                    .isInstanceOf(InsufficientPermissionException.class)
                    .hasMessageContaining("Only administrators can delete products");

            verify(userService).getCurrentUser(eq(validToken));
            verify(productRepository, never()).findById(any(UUID.class));
            verify(productRepository, never()).delete(any(Product.class));
        }

        @Test
        @DisplayName("Should allow admin to patch product")
        void shouldAllowAdminToPatchProduct() {
            // Given
            setupAdminAuthentication();
            Map<String, Object> patchData = Map.of("name", "Patched Product");
            when(productRepository.findById(eq(productId))).thenReturn(Optional.of(testProduct));
            when(productRepository.save(any(Product.class))).thenReturn(testProduct);

            // When
            ProductResponse response = productService.patchProduct(productId, patchData, validToken);

            // Then
            assertThat(response).isNotNull();
            verify(userService).getCurrentUser(eq(validToken));
            verify(productRepository).save(any(Product.class));
        }

        @Test
        @DisplayName("Should deny regular user from patching product")
        void shouldDenyRegularUserFromPatchingProduct() {
            // Given
            setupValidAuthentication();
            Map<String, Object> patchData = Map.of("name", "Patched Product");

            // When & Then
            assertThatThrownBy(() -> productService.patchProduct(productId, patchData, validToken))
                    .isInstanceOf(InsufficientPermissionException.class)
                    .hasMessageContaining("Only administrators can update products");

            verify(userService).getCurrentUser(eq(validToken));
            verify(productRepository, never()).findById(any(UUID.class));
            verify(productRepository, never()).save(any(Product.class));
        }

        @Test
        @DisplayName("Should allow regular user to read product")
        void shouldAllowRegularUserToReadProduct() {
            // Given
            setupValidAuthentication();
            when(productRepository.findById(eq(productId))).thenReturn(Optional.of(testProduct));

            // When
            ProductResponse response = productService.getProductById(productId, validToken);

            // Then
            assertThat(response).isNotNull();
            verify(userService).getCurrentUser(eq(validToken));
            verify(productRepository).findById(eq(productId));
        }
    }
}