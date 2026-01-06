
package org.cubord.cubordbackend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.cubord.cubordbackend.config.TestSecurityConfig;
import org.cubord.cubordbackend.domain.ProductDataSource;
import org.cubord.cubordbackend.dto.product.ProductRequest;
import org.cubord.cubordbackend.dto.product.ProductResponse;
import org.cubord.cubordbackend.dto.product.ProductUpdateRequest;
import org.cubord.cubordbackend.exception.*;
import org.cubord.cubordbackend.security.SecurityService;
import org.cubord.cubordbackend.service.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for {@link ProductController}.
 *
 * <p>Tests verify:</p>
 * <ul>
 *   <li>Endpoint functionality and response formats</li>
 *   <li>Authorization via {@code @PreAuthorize} with mocked SecurityService</li>
 *   <li>Proper exception handling and HTTP status codes</li>
 *   <li>Input validation</li>
 *   <li>Admin-only operations are properly protected</li>
 * </ul>
 */
@WebMvcTest(ProductController.class)
@Import(TestSecurityConfig.class)
@ActiveProfiles("test")
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ProductService productService;

    @MockitoBean(name = "security")
    private SecurityService securityService;

    @MockitoBean
    private org.cubord.cubordbackend.security.HouseholdPermissionEvaluator householdPermissionEvaluator;

    private UUID productId;
    private UUID currentUserId;
    private String sampleUpc;
    private ProductRequest sampleProductRequest;
    private ProductResponse sampleProductResponse;
    private Jwt jwt;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        productId = UUID.randomUUID();
        currentUserId = UUID.randomUUID();
        sampleUpc = "012345678905";

        sampleProductRequest = ProductRequest.builder()
                .upc(sampleUpc)
                .name("Test Product")
                .brand("Test Brand")
                .category("Test Category")
                .defaultExpirationDays(30)
                .build();

        sampleProductResponse = ProductResponse.builder()
                .id(productId)
                .upc(sampleUpc)
                .name("Test Product")
                .brand("Test Brand")
                .category("Test Category")
                .defaultExpirationDays(30)
                .dataSource(ProductDataSource.OPEN_FOOD_FACTS)
                .requiresApiRetry(false)
                .retryAttempts(0)
                .createdAt(LocalDateTime.now().minusDays(7))
                .updatedAt(LocalDateTime.now())
                .build();

        jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(currentUserId.toString())
                .claim("email", "test@example.com")
                .claim("name", "Test User")
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        // Default security service mock behavior
        when(securityService.isAdmin()).thenReturn(false);
    }

    // ==================== Create Operations Tests ====================

    @Nested
    @DisplayName("POST /api/products")
    class CreateProduct {

        @Test
        @DisplayName("should create product for authenticated user")
        void shouldCreateProductForAuthenticatedUser() throws Exception {
            when(productService.createProduct(any(ProductRequest.class)))
                    .thenReturn(sampleProductResponse);

            mockMvc.perform(post("/api/products")
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(sampleProductRequest)))
                    .andExpect(status().isCreated())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.id").value(productId.toString()))
                    .andExpect(jsonPath("$.upc").value(sampleUpc))
                    .andExpect(jsonPath("$.name").value("Test Product"));

            verify(productService).createProduct(any(ProductRequest.class));
        }

        @Test
        @DisplayName("should return 401 for unauthenticated request")
        void shouldReturn401ForUnauthenticatedRequest() throws Exception {
            mockMvc.perform(post("/api/products")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(sampleProductRequest)))
                    .andExpect(status().isUnauthorized());

            verifyNoInteractions(productService);
        }

        @Test
        @DisplayName("should return 400 when request body is invalid")
        void shouldReturn400WhenRequestBodyIsInvalid() throws Exception {
            ProductRequest invalidRequest = ProductRequest.builder()
                    .upc("") // Invalid: empty UPC
                    .build();

            mockMvc.perform(post("/api/products")
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(invalidRequest)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(productService);
        }

        @Test
        @DisplayName("should return 409 when product with UPC already exists")
        void shouldReturn409WhenProductWithUpcAlreadyExists() throws Exception {
            when(productService.createProduct(any(ProductRequest.class)))
                    .thenThrow(new ConflictException("Product with UPC '" + sampleUpc + "' already exists"));

            mockMvc.perform(post("/api/products")
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(sampleProductRequest)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error_code").value("RESOURCE_CONFLICT"));

            verify(productService).createProduct(any(ProductRequest.class));
        }

        @Test
        @DisplayName("should return 409 when data integrity exception occurs")
        void shouldReturn409WhenDataIntegrityExceptionOccurs() throws Exception {
            when(productService.createProduct(any(ProductRequest.class)))
                    .thenThrow(new DataIntegrityException("Failed to save product"));

            mockMvc.perform(post("/api/products")
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(sampleProductRequest)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error_code").value("DATA_INTEGRITY_VIOLATION"));

            verify(productService).createProduct(any(ProductRequest.class));
        }
    }

    @Nested
    @DisplayName("POST /api/products/bulk-import")
    class BulkImportProducts {

        @Test
        @DisplayName("should bulk import products for admin user")
        void shouldBulkImportProductsForAdminUser() throws Exception {
            when(securityService.isAdmin()).thenReturn(true);
            when(productService.bulkImportProducts(anyList()))
                    .thenReturn(List.of(sampleProductResponse));

            List<ProductRequest> requests = List.of(sampleProductRequest);

            mockMvc.perform(post("/api/products/bulk-import")
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(requests)))
                    .andExpect(status().isCreated())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$[0].id").value(productId.toString()));

            verify(securityService).isAdmin();
            verify(productService).bulkImportProducts(anyList());
        }

        @Test
        @DisplayName("should return 403 for non-admin user")
        void shouldReturn403ForNonAdminUser() throws Exception {
            when(securityService.isAdmin()).thenReturn(false);

            List<ProductRequest> requests = List.of(sampleProductRequest);

            mockMvc.perform(post("/api/products/bulk-import")
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(requests)))
                    .andExpect(status().isForbidden());

            verify(securityService).isAdmin();
            verifyNoInteractions(productService);
        }

        @Test
        @DisplayName("should return 400 when list is empty")
        void shouldReturn400WhenListIsEmpty() throws Exception {
            when(securityService.isAdmin()).thenReturn(true);

            mockMvc.perform(post("/api/products/bulk-import")
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(List.of())))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(productService);
        }

        @Test
        @DisplayName("should return 400 when request contains invalid product")
        void shouldReturn400WhenContainsInvalidProduct() throws Exception {
            when(securityService.isAdmin()).thenReturn(true);

            ProductRequest invalidRequest = ProductRequest.builder().upc("").build();
            List<ProductRequest> requests = List.of(invalidRequest);

            mockMvc.perform(post("/api/products/bulk-import")
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(requests)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(productService);
        }
    }

    // ==================== Read Operations Tests ====================

    @Nested
    @DisplayName("GET /api/products/{id}")
    class GetProductById {

        @Test
        @DisplayName("should return product details for authenticated user")
        void shouldReturnProductDetailsForAuthenticatedUser() throws Exception {
            when(productService.getProductById(productId))
                    .thenReturn(sampleProductResponse);

            mockMvc.perform(get("/api/products/" + productId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.id").value(productId.toString()))
                    .andExpect(jsonPath("$.upc").value(sampleUpc))
                    .andExpect(header().string("Cache-Control", "max-age=300"));

            verify(productService).getProductById(productId);
        }

        @Test
        @DisplayName("should return 404 when product not found")
        void shouldReturn404WhenProductNotFound() throws Exception {
            when(productService.getProductById(productId))
                    .thenThrow(new NotFoundException("Product not found"));

            mockMvc.perform(get("/api/products/" + productId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error_code").value("RESOURCE_NOT_FOUND"));

            verify(productService).getProductById(productId);
        }

        @Test
        @DisplayName("should return 401 for unauthenticated request")
        void shouldReturn401ForUnauthenticatedRequest() throws Exception {
            mockMvc.perform(get("/api/products/" + productId))
                    .andExpect(status().isUnauthorized());

            verifyNoInteractions(productService);
        }

        @Test
        @DisplayName("should return 400 when invalid UUID format provided")
        void shouldReturn400WhenInvalidUuidProvided() throws Exception {
            mockMvc.perform(get("/api/products/invalid-uuid")
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(productService);
        }
    }

    @Nested
    @DisplayName("GET /api/products/upc/{upc}")
    class GetProductByUpc {

        @Test
        @DisplayName("should return product details by UPC")
        void shouldReturnProductDetailsByUpc() throws Exception {
            when(productService.getProductByUpc(sampleUpc))
                    .thenReturn(sampleProductResponse);

            mockMvc.perform(get("/api/products/upc/" + sampleUpc)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.upc").value(sampleUpc))
                    .andExpect(header().string("Cache-Control", "max-age=300"));

            verify(productService).getProductByUpc(sampleUpc);
        }

        @Test
        @DisplayName("should return 404 when product with UPC not found")
        void shouldReturn404WhenProductWithUpcNotFound() throws Exception {
            when(productService.getProductByUpc(sampleUpc))
                    .thenThrow(new NotFoundException("Product not found"));

            mockMvc.perform(get("/api/products/upc/" + sampleUpc)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error_code").value("RESOURCE_NOT_FOUND"));

            verify(productService).getProductByUpc(sampleUpc);
        }

        @Test
        @DisplayName("should return 400 when UPC is invalid")
        void shouldReturn400WhenUpcIsInvalid() throws Exception {
            // Test with an invalid format UPC that reaches the controller
            String invalidUpc = "abc123"; // Not purely numeric

            mockMvc.perform(get("/api/products/upc/" + invalidUpc)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(productService);
        }
    }

    @Nested
    @DisplayName("GET /api/products")
    class GetAllProducts {

        @Test
        @DisplayName("should return paginated products")
        void shouldReturnPaginatedProducts() throws Exception {
            Page<ProductResponse> page = new PageImpl<>(
                    List.of(sampleProductResponse),
                    PageRequest.of(0, 20),
                    1
            );

            when(productService.getAllProducts(any(PageRequest.class)))
                    .thenReturn(page);

            mockMvc.perform(get("/api/products")
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                            .param("page", "0")
                            .param("size", "20"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.content[0].id").value(productId.toString()))
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(header().string("Cache-Control", "max-age=60"));

            verify(productService).getAllProducts(any(PageRequest.class));
        }

        @Test
        @DisplayName("should return 401 for unauthenticated request")
        void shouldReturn401ForUnauthenticatedRequest() throws Exception {
            mockMvc.perform(get("/api/products"))
                    .andExpect(status().isUnauthorized());

            verifyNoInteractions(productService);
        }
    }

    @Nested
    @DisplayName("GET /api/products/search")
    class SearchProducts {

        @Test
        @DisplayName("should search products by query")
        void shouldSearchProductsByQuery() throws Exception {
            Page<ProductResponse> page = new PageImpl<>(
                    List.of(sampleProductResponse),
                    PageRequest.of(0, 20),
                    1
            );

            when(productService.searchProducts(eq("Test"), any(PageRequest.class)))
                    .thenReturn(page);

            mockMvc.perform(get("/api/products/search")
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                            .param("query", "Test")
                            .param("page", "0")
                            .param("size", "20"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.content[0].name").value("Test Product"))
                    .andExpect(header().string("Cache-Control", "max-age=60"));

            verify(productService).searchProducts(eq("Test"), any(PageRequest.class));
        }

        @Test
        @DisplayName("should return 400 when query is missing")
        void shouldReturn400WhenQueryIsMissing() throws Exception {
            mockMvc.perform(get("/api/products/search")
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(productService);
        }

        @Test
        @DisplayName("should return 400 when query is blank")
        void shouldReturn400WhenQueryIsBlank() throws Exception {
            mockMvc.perform(get("/api/products/search")
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                            .param("query", "   "))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(productService);
        }

        @Test
        @DisplayName("should return empty page when no results found")
        void shouldReturnEmptyPageWhenNoResultsFound() throws Exception {
            Page<ProductResponse> emptyPage = new PageImpl<>(
                    List.of(),
                    PageRequest.of(0, 20),
                    0
            );

            when(productService.searchProducts(eq("NonExistent"), any(PageRequest.class)))
                    .thenReturn(emptyPage);

            mockMvc.perform(get("/api/products/search")
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                            .param("query", "NonExistent"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isEmpty())
                    .andExpect(jsonPath("$.totalElements").value(0));

            verify(productService).searchProducts(eq("NonExistent"), any(PageRequest.class));
        }
    }

    // ==================== Update Operations Tests ====================

    @Nested
    @DisplayName("PUT /api/products/{id}")
    class UpdateProduct {

        private ProductUpdateRequest updateRequest;

        @BeforeEach
        void setUp() {
            updateRequest = ProductUpdateRequest.builder()
                    .name("Updated Product")
                    .brand("Updated Brand")
                    .category("Updated Category")
                    .defaultExpirationDays(45)
                    .build();
        }

        @Test
        @DisplayName("should update product for admin user")
        void shouldUpdateProductForAdminUser() throws Exception {
            when(securityService.isAdmin()).thenReturn(true);
            when(productService.updateProduct(eq(productId), any(ProductUpdateRequest.class)))
                    .thenReturn(sampleProductResponse);

            mockMvc.perform(put("/api/products/" + productId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(updateRequest)))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.id").value(productId.toString()));

            verify(securityService).isAdmin();
            verify(productService).updateProduct(eq(productId), any(ProductUpdateRequest.class));
        }

        @Test
        @DisplayName("should return 403 for non-admin user")
        void shouldReturn403ForNonAdminUser() throws Exception {
            when(securityService.isAdmin()).thenReturn(false);

            mockMvc.perform(put("/api/products/" + productId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(updateRequest)))
                    .andExpect(status().isForbidden());

            verify(securityService).isAdmin();
            verifyNoInteractions(productService);
        }

        @Test
        @DisplayName("should return 404 when product not found")
        void shouldReturn404WhenProductNotFound() throws Exception {
            when(securityService.isAdmin()).thenReturn(true);
            when(productService.updateProduct(eq(productId), any(ProductUpdateRequest.class)))
                    .thenThrow(new NotFoundException("Product not found"));

            mockMvc.perform(put("/api/products/" + productId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(updateRequest)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error_code").value("RESOURCE_NOT_FOUND"));

            verify(productService).updateProduct(eq(productId), any(ProductUpdateRequest.class));
        }
    }

    @Nested
    @DisplayName("PATCH /api/products/{id}")
    class PatchProduct {

        @Test
        @DisplayName("should patch product for admin user")
        void shouldPatchProductForAdminUser() throws Exception {
            when(securityService.isAdmin()).thenReturn(true);
            when(productService.patchProduct(eq(productId), anyMap()))
                    .thenReturn(sampleProductResponse);

            Map<String, Object> patchData = Map.of("name", "Patched Name");

            mockMvc.perform(patch("/api/products/" + productId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(patchData)))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.id").value(productId.toString()));

            verify(securityService).isAdmin();
            verify(productService).patchProduct(eq(productId), anyMap());
        }

        @Test
        @DisplayName("should return 403 for non-admin user")
        void shouldReturn403ForNonAdminUser() throws Exception {
            when(securityService.isAdmin()).thenReturn(false);

            Map<String, Object> patchData = Map.of("name", "Patched Name");

            mockMvc.perform(patch("/api/products/" + productId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(patchData)))
                    .andExpect(status().isForbidden());

            verify(securityService).isAdmin();
            verifyNoInteractions(productService);
        }

        @Test
        @DisplayName("should return 400 when unsupported field provided")
        void shouldReturn400WhenUnsupportedFieldProvided() throws Exception {
            when(securityService.isAdmin()).thenReturn(true);
            when(productService.patchProduct(eq(productId), anyMap()))
                    .thenThrow(new ValidationException("Unsupported field for patching: invalidField"));

            Map<String, Object> patchData = Map.of("invalidField", "value");

            mockMvc.perform(patch("/api/products/" + productId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(patchData)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error_code").value("VALIDATION_ERROR"));

            verify(productService).patchProduct(eq(productId), anyMap());
        }

        @Test
        @DisplayName("should return 400 when patch data is empty")
        void shouldReturn400WhenPatchDataIsEmpty() throws Exception {
            when(securityService.isAdmin()).thenReturn(true);

            Map<String, Object> emptyPatchData = Map.of();

            mockMvc.perform(patch("/api/products/" + productId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(emptyPatchData)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(productService);
        }
    }

    // ==================== Delete Operations Tests ====================

    @Nested
    @DisplayName("DELETE /api/products/{id}")
    class DeleteProduct {

        @Test
        @DisplayName("should delete product for admin user")
        void shouldDeleteProductForAdminUser() throws Exception {
            when(securityService.isAdmin()).thenReturn(true);
            doNothing().when(productService).deleteProduct(productId);

            mockMvc.perform(delete("/api/products/" + productId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isNoContent());

            verify(securityService).isAdmin();
            verify(productService).deleteProduct(productId);
        }

        @Test
        @DisplayName("should return 403 for non-admin user")
        void shouldReturn403ForNonAdminUser() throws Exception {
            when(securityService.isAdmin()).thenReturn(false);

            mockMvc.perform(delete("/api/products/" + productId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isForbidden());

            verify(securityService).isAdmin();
            verifyNoInteractions(productService);
        }

        @Test
        @DisplayName("should return 404 when product not found")
        void shouldReturn404WhenProductNotFound() throws Exception {
            when(securityService.isAdmin()).thenReturn(true);
            doThrow(new NotFoundException("Product not found"))
                    .when(productService).deleteProduct(productId);

            mockMvc.perform(delete("/api/products/" + productId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error_code").value("RESOURCE_NOT_FOUND"));

            verify(productService).deleteProduct(productId);
        }
    }

    @Nested
    @DisplayName("DELETE /api/products/bulk")
    class BulkDeleteProducts {

        @Test
        @DisplayName("should bulk delete products for admin user")
        void shouldBulkDeleteProductsForAdminUser() throws Exception {
            when(securityService.isAdmin()).thenReturn(true);
            when(productService.bulkDeleteProducts(anyList()))
                    .thenReturn(2);

            List<UUID> productIds = List.of(UUID.randomUUID(), UUID.randomUUID());

            mockMvc.perform(delete("/api/products/bulk")
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(productIds)))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.deletedCount").value(2));

            verify(securityService).isAdmin();
            verify(productService).bulkDeleteProducts(anyList());
        }

        @Test
        @DisplayName("should return 403 for non-admin user")
        void shouldReturn403ForNonAdminUser() throws Exception {
            when(securityService.isAdmin()).thenReturn(false);

            List<UUID> productIds = List.of(UUID.randomUUID(), UUID.randomUUID());

            mockMvc.perform(delete("/api/products/bulk")
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(productIds)))
                    .andExpect(status().isForbidden());

            verify(securityService).isAdmin();
            verifyNoInteractions(productService);
        }

        @Test
        @DisplayName("should return 400 when list is empty")
        void shouldReturn400WhenListIsEmpty() throws Exception {
            when(securityService.isAdmin()).thenReturn(true);

            mockMvc.perform(delete("/api/products/bulk")
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(List.of())))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(productService);
        }

        @Test
        @DisplayName("should handle partial deletion failure gracefully")
        void shouldHandlePartialDeletionFailureGracefully() throws Exception {
            when(securityService.isAdmin()).thenReturn(true);
            when(productService.bulkDeleteProducts(anyList()))
                    .thenReturn(1); // Only 1 of 2 deleted

            List<UUID> productIds = List.of(UUID.randomUUID(), UUID.randomUUID());

            mockMvc.perform(delete("/api/products/bulk")
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(productIds)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.deletedCount").value(1));

            verify(productService).bulkDeleteProducts(anyList());
        }
    }

    // ==================== Retry Operations Tests ====================

    @Nested
    @DisplayName("POST /api/products/{id}/retry")
    class RetryApiEnrichment {

        @Test
        @DisplayName("should retry API enrichment for admin user")
        void shouldRetryApiEnrichmentForAdminUser() throws Exception {
            when(securityService.isAdmin()).thenReturn(true);
            when(productService.retryApiEnrichment(eq(productId)))
                    .thenReturn(sampleProductResponse);

            mockMvc.perform(post("/api/products/" + productId + "/retry")
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.id").value(productId.toString()));

            verify(securityService).isAdmin();
            verify(productService).retryApiEnrichment(eq(productId));
        }

        @Test
        @DisplayName("should return 403 for non-admin user")
        void shouldReturn403ForNonAdminUser() throws Exception {
            when(securityService.isAdmin()).thenReturn(false);

            mockMvc.perform(post("/api/products/" + productId + "/retry")
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isForbidden());

            verify(securityService).isAdmin();
            verifyNoInteractions(productService);
        }
    }

    @Nested
    @DisplayName("POST /api/products/retry/batch")
    class ProcessBatchRetry {

        @Test
        @DisplayName("should process batch retry for admin user")
        void shouldProcessBatchRetryForAdminUser() throws Exception {
            when(securityService.isAdmin()).thenReturn(true);
            when(productService.processBatchRetry(eq(3)))
                    .thenReturn(5);

            mockMvc.perform(post("/api/products/retry/batch")
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                            .param("maxRetryAttempts", "3"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.processedCount").value(5));

            verify(securityService).isAdmin();
            verify(productService).processBatchRetry(eq(3));
        }

        @Test
        @DisplayName("should return 403 for non-admin user")
        void shouldReturn403ForNonAdminUser() throws Exception {
            when(securityService.isAdmin()).thenReturn(false);

            mockMvc.perform(post("/api/products/retry/batch")
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isForbidden());

            verify(securityService).isAdmin();
            verifyNoInteractions(productService);
        }
    }

    // ==================== Exception Handling Tests ====================

    @Nested
    @DisplayName("Exception Handling")
    class ExceptionHandling {

        @Test
        @DisplayName("should return 500 when SecurityService throws unexpected exception")
        void shouldReturn500WhenSecurityServiceFails() throws Exception {
            when(securityService.isAdmin())
                    .thenThrow(new RuntimeException("Security context unavailable"));

            mockMvc.perform(delete("/api/products/" + productId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isInternalServerError());

            verifyNoInteractions(productService);
        }

        @Test
        @DisplayName("should return 400 when ValidationException is thrown")
        void shouldReturn400WhenValidationExceptionIsThrown() throws Exception {
            when(productService.getProductById(productId))
                    .thenThrow(new ValidationException("Product ID cannot be null"));

            mockMvc.perform(get("/api/products/" + productId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error_code").value("VALIDATION_ERROR"));

            verify(productService).getProductById(productId);
        }

        @Test
        @DisplayName("should return 403 when InsufficientPermissionException is thrown")
        void shouldReturn403WhenInsufficientPermissionExceptionIsThrown() throws Exception {
            when(securityService.isAdmin()).thenReturn(true);
            doThrow(new InsufficientPermissionException("Only administrators can delete products"))
                    .when(productService).deleteProduct(productId);

            mockMvc.perform(delete("/api/products/" + productId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error_code").value("INSUFFICIENT_PERMISSION"));

            verify(productService).deleteProduct(productId);
        }
    }


}
