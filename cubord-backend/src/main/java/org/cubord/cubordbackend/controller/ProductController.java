package org.cubord.cubordbackend.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.cubord.cubordbackend.domain.ProductDataSource;
import org.cubord.cubordbackend.dto.ProductRequest;
import org.cubord.cubordbackend.dto.ProductResponse;
import org.cubord.cubordbackend.dto.ProductUpdateRequest;
import org.cubord.cubordbackend.service.ProductService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST controller for product management operations.
 * Handles HTTP requests related to creating, retrieving, updating, and deleting products.
 * All authentication and authorization is handled at the service layer.
 * The controller validates input parameters and delegates business logic to the service layer.
 */
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {
    
    private final ProductService productService;
    
    /**
     * Creates a new product.
     *
     * @param request DTO containing product information
     * @param token JWT authentication token of the current user
     * @return ResponseEntity containing the created product's details
     */
    @PostMapping
    public ResponseEntity<ProductResponse> createProduct(
            @Valid @RequestBody ProductRequest request,
            JwtAuthenticationToken token) {
        ProductResponse response = productService.createProduct(request, token);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
    
    /**
     * Gets a product by ID.
     *
     * @param id The UUID of the product
     * @param token JWT authentication token of the current user
     * @return ResponseEntity containing the product's details
     */
    @GetMapping("/{id}")
    public ResponseEntity<ProductResponse> getProduct(
            @PathVariable UUID id,
            JwtAuthenticationToken token) {
        ProductResponse response = productService.getProductById(id, token);
        return ResponseEntity.ok(response);
    }
    
    /**
     * Gets a product by UPC.
     *
     * @param upc The UPC code of the product
     * @param token JWT authentication token of the current user
     * @return ResponseEntity containing the product's details
     */
    @GetMapping("/upc/{upc}")
    public ResponseEntity<ProductResponse> getProductByUpc(
            @PathVariable String upc,
            JwtAuthenticationToken token) {
        ProductResponse response = productService.getProductByUpc(upc, token);
        return ResponseEntity.ok(response);
    }
    
    /**
     * Gets all products with pagination.
     *
     * @param pageable Pagination information
     * @param token JWT authentication token of the current user
     * @return ResponseEntity containing a page of products
     */
    @GetMapping
    public ResponseEntity<Page<ProductResponse>> getAllProducts(
            Pageable pageable,
            JwtAuthenticationToken token) {
        Page<ProductResponse> response = productService.getAllProducts(pageable, token);
        return ResponseEntity.ok(response);
    }
    
    /**
     * Updates a product completely.
     *
     * @param id The UUID of the product
     * @param request DTO containing updated product information
     * @param token JWT authentication token of the current user
     * @return ResponseEntity containing the updated product's details
     */
    @PutMapping("/{id}")
    public ResponseEntity<ProductResponse> updateProduct(
            @PathVariable UUID id,
            @Valid @RequestBody ProductUpdateRequest request,
            JwtAuthenticationToken token) {
        ProductResponse response = productService.updateProduct(id, request, token);
        return ResponseEntity.ok(response);
    }
    
    /**
     * Partially updates a product.
     *
     * @param id The UUID of the product
     * @param patchData Map containing fields to update
     * @param token JWT authentication token of the current user
     * @return ResponseEntity containing the updated product's details
     */
    @PatchMapping("/{id}")
    public ResponseEntity<ProductResponse> patchProduct(
            @PathVariable UUID id,
            @RequestBody Map<String, Object> patchData,
            JwtAuthenticationToken token) {
        ProductResponse response = productService.patchProduct(id, patchData, token);
        return ResponseEntity.ok(response);
    }
    
    /**
     * Deletes a product.
     *
     * @param id The UUID of the product to delete
     * @param token JWT authentication token of the current user
     * @return ResponseEntity with no content
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProduct(
            @PathVariable UUID id,
            JwtAuthenticationToken token) {
        productService.deleteProduct(id, token);
        return ResponseEntity.noContent().build();
    }
    
    /**
     * Searches products by name.
     *
     * @param name The search term for product names
     * @param token JWT authentication token of the current user
     * @return ResponseEntity containing a list of matching products
     */
    @GetMapping("/search")
    public ResponseEntity<List<ProductResponse>> searchProducts(
            @RequestParam String name,
            JwtAuthenticationToken token) {
        List<ProductResponse> response = productService.searchProductsByName(name, token);
        return ResponseEntity.ok(response);
    }
    
    /**
     * Gets products by category.
     *
     * @param category The product category
     * @param token JWT authentication token of the current user
     * @return ResponseEntity containing a list of products in the category
     */
    @GetMapping("/category/{category}")
    public ResponseEntity<List<ProductResponse>> getProductsByCategory(
            @PathVariable String category,
            JwtAuthenticationToken token) {
        List<ProductResponse> response = productService.getProductsByCategory(category, token);
        return ResponseEntity.ok(response);
    }
    
    /**
     * Gets products by brand.
     *
     * @param brand The product brand
     * @param token JWT authentication token of the current user
     * @return ResponseEntity containing a list of products from the brand
     */
    @GetMapping("/brand/{brand}")
    public ResponseEntity<List<ProductResponse>> getProductsByBrand(
            @PathVariable String brand,
            JwtAuthenticationToken token) {
        List<ProductResponse> response = productService.getProductsByBrand(brand, token);
        return ResponseEntity.ok(response);
    }
    
    /**
     * Gets products by data source.
     *
     * @param dataSource The product data source
     * @param token JWT authentication token of the current user
     * @return ResponseEntity containing a list of products from the data source
     */
    @GetMapping("/data-source/{dataSource}")
    public ResponseEntity<List<ProductResponse>> getProductsByDataSource(
            @PathVariable ProductDataSource dataSource,
            JwtAuthenticationToken token) {
        List<ProductResponse> response = productService.getProductsByDataSource(dataSource, token);
        return ResponseEntity.ok(response);
    }
    
    /**
     * Gets product statistics.
     *
     * @param token JWT authentication token of the current user
     * @return ResponseEntity containing product statistics
     */
    @GetMapping("/statistics")
    public ResponseEntity<Map<String, Long>> getProductStatistics(JwtAuthenticationToken token) {
        Map<String, Long> response = productService.getProductStatistics(token);
        return ResponseEntity.ok(response);
    }
    
    /**
     * Checks if UPC is available.
     *
     * @param upc The UPC code to check
     * @param token JWT authentication token of the current user
     * @return ResponseEntity containing availability status
     */
    @GetMapping("/upc/{upc}/availability")
    public ResponseEntity<Map<String, Boolean>> checkUpcAvailability(
            @PathVariable String upc,
            JwtAuthenticationToken token) {
        boolean available = productService.isUpcAvailable(upc, token);
        return ResponseEntity.ok(Map.of("available", available));
    }
    
    /**
     * Gets products requiring retry.
     *
     * @param token JWT authentication token of the current user
     * @return ResponseEntity containing a list of products requiring API retry
     */
    @GetMapping("/retry/pending")
    public ResponseEntity<List<ProductResponse>> getProductsRequiringRetry(JwtAuthenticationToken token) {
        List<ProductResponse> response = productService.getProductsRequiringRetry(token);
        return ResponseEntity.ok(response);
    }
    
    /**
     * Retries API enrichment for a product.
     *
     * @param id The UUID of the product
     * @param token JWT authentication token of the current user
     * @return ResponseEntity containing the updated product's details
     */
    @PostMapping("/{id}/retry")
    public ResponseEntity<ProductResponse> retryApiEnrichment(
            @PathVariable UUID id,
            JwtAuthenticationToken token) {
        ProductResponse response = productService.retryApiEnrichment(id, token);
        return ResponseEntity.ok(response);
    }
    
    /**
     * Processes batch retry for products requiring API enrichment.
     *
     * @param maxRetryAttempts Maximum number of retry attempts
     * @param token JWT authentication token of the current user
     * @return ResponseEntity containing the number of processed products
     */
    @PostMapping("/retry/batch")
    public ResponseEntity<Map<String, Integer>> processBatchRetry(
            @RequestParam(defaultValue = "3") int maxRetryAttempts,
            JwtAuthenticationToken token) {
        int processedCount = productService.processBatchRetry(maxRetryAttempts, token);
        return ResponseEntity.ok(Map.of("processedCount", processedCount));
    }
    
    /**
     * Bulk imports products.
     *
     * @param requests List of product requests to import
     * @param token JWT authentication token of the current user
     * @return ResponseEntity containing a list of created products
     */
    @PostMapping("/bulk-import")
    public ResponseEntity<List<ProductResponse>> bulkImportProducts(
            @Valid @RequestBody List<ProductRequest> requests,
            JwtAuthenticationToken token) {
        List<ProductResponse> response = productService.bulkImportProducts(requests, token);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
    
    /**
     * Bulk deletes products.
     *
     * @param productIds List of product UUIDs to delete
     * @param token JWT authentication token of the current user
     * @return ResponseEntity containing the number of deleted products
     */
    @DeleteMapping("/bulk")
    public ResponseEntity<Map<String, Integer>> bulkDeleteProducts(
            @RequestBody List<UUID> productIds,
            JwtAuthenticationToken token) {
        int deletedCount = productService.bulkDeleteProducts(productIds, token);
        return ResponseEntity.ok(Map.of("deletedCount", deletedCount));
    }
}