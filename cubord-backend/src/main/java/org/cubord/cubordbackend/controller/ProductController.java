package org.cubord.cubordbackend.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cubord.cubordbackend.dto.product.ProductRequest;
import org.cubord.cubordbackend.dto.product.ProductResponse;
import org.cubord.cubordbackend.dto.product.ProductUpdateRequest;
import org.cubord.cubordbackend.exception.ValidationException;
import org.cubord.cubordbackend.service.ProductService;
import org.cubord.cubordbackend.util.UrlValidator;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * REST controller for product management operations.
 *
 * <p>This controller follows the modernized security architecture where:</p>
 * <ul>
 *   <li>Authentication is handled by Spring Security filters (JWT validation)</li>
 *   <li>Authorization is declarative via {@code @PreAuthorize} annotations</li>
 *   <li>No manual token validation or security checks in controller methods</li>
 *   <li>Business logic is delegated entirely to the service layer</li>
 *   <li><strong>Input Validation:</strong> Security-critical inputs validated at controller level</li>
 * </ul>
 *
 * <h2>Authorization Rules</h2>
 * <ul>
 *   <li><strong>POST /products:</strong> All authenticated users can create products</li>
 *   <li><strong>GET /products/*:</strong> All authenticated users can view products</li>
 *   <li><strong>PUT /products/{id}:</strong> Only administrators can modify products</li>
 *   <li><strong>PATCH /products/{id}:</strong> Only administrators can patch products</li>
 *   <li><strong>DELETE /products/{id}:</strong> Only administrators can delete products</li>
 *   <li><strong>POST /products/bulk-import:</strong> Only administrators can bulk import</li>
 *   <li><strong>DELETE /products/bulk:</strong> Only administrators can bulk delete</li>
 *   <li><strong>POST /products/{id}/retry:</strong> Only administrators can retry API enrichment</li>
 * </ul>
 *
 * <h2>Product Data Sources</h2>
 * <p>Products can be created from multiple sources:</p>
 * <ul>
 *   <li><strong>OPEN_FOOD_FACTS:</strong> Enriched from external API</li>
 *   <li><strong>MANUAL:</strong> User-created entries that may require API enrichment retry</li>
 * </ul>
 *
 * <h2>Exception Handling</h2>
 * <p>All exceptions are handled by {@link org.cubord.cubordbackend.exception.RestExceptionHandler}
 * which provides consistent error responses with correlation IDs.</p>
 *
 * @see org.cubord.cubordbackend.service.ProductService
 * @see org.cubord.cubordbackend.security.SecurityService
 */
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
@Validated
@Slf4j
public class ProductController {

    private final ProductService productService;

    /**
     * UPC/EAN validation pattern: 8-14 digits only.
     * Covers UPC-A (12), EAN-13 (13), EAN-8 (8), and UPC-E (6-8) formats.
     */
    private static final Pattern UPC_PATTERN = Pattern.compile("^[0-9]{8,14}$");

    /**
     * Base URL for Open Food Facts API - must match UpcApiService configuration.
     * Used for pre-validating URLs at controller level to prevent SSRF.
     */
    private static final String OPEN_FOOD_FACTS_BASE_URL = "https://world.openfoodfacts.org/api/v2";

    /**
     * Maximum length for search queries to prevent resource exhaustion.
     */
    private static final int MAX_SEARCH_QUERY_LENGTH = 100;

    // ==================== Create Operations ====================

    /**
     * Creates a new product.
     *
     * <p>Authorization: All authenticated users can create products.</p>
     *
     * <p>The system will automatically attempt to enrich the product with data from
     * external APIs. If enrichment fails, the product is created as a manual entry
     * and marked for retry.</p>
     *
     * @param request DTO containing product information
     * @return ResponseEntity containing the created product's details with 201 Created statuses
     */
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ProductResponse> createProduct(@Valid @RequestBody ProductRequest request) {
        log.debug("Creating product with UPC: {}", request.getUpc());

        // Validate a UPC format for SSRF protection
        validateUpcFormat(request.getUpc());

        ProductResponse response = productService.createProduct(request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .contentType(MediaType.APPLICATION_JSON)
                .body(response);
    }

    /**
     * Bulk imports multiple products.
     *
     * <p>Authorization: Only administrators can bulk import products.</p>
     *
     * @param requests List of product requests to import
     * @return ResponseEntity containing a list of created products with 201 Created statuses
     */
    @PostMapping("/bulk-import")
    @PreAuthorize("@security.isAdmin()")
    public ResponseEntity<List<ProductResponse>> bulkImportProducts(
            @Valid @RequestBody @NotEmpty(message = "Product requests list cannot be empty") List<@Valid ProductRequest> requests) {

        log.debug("Bulk importing {} products", requests.size());

        // Validate all UPC formats for SSRF protection
        requests.forEach(request -> validateUpcFormat(request.getUpc()));

        List<ProductResponse> response = productService.bulkImportProducts(requests);

        return ResponseEntity.status(HttpStatus.CREATED)
                .contentType(MediaType.APPLICATION_JSON)
                .body(response);
    }

    // ==================== Read Operations ====================

    /**
     * Gets a product by ID.
     *
     * <p>Authorization: All authenticated users can view products.</p>
     *
     * @param id The UUID of the product
     * @return ResponseEntity containing the product's details
     */
    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ProductResponse> getProduct(@PathVariable @NotNull UUID id) {
        log.debug("Retrieving product by ID: {}", id);

        ProductResponse response = productService.getProductById(id);

        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(5, TimeUnit.MINUTES))
                .contentType(MediaType.APPLICATION_JSON)
                .body(response);
    }

    /**
     * Gets a product by UPC.
     *
     * <p>Authorization: All authenticated users can view products.</p>
     *
     * <p><strong>Security Note:</strong> UPC is validated to prevent SSRF attacks
     * when fetching data from external APIs.</p>
     *
     * @param upc The UPC of the product (8-14 digits)
     * @return ResponseEntity containing the product's details
     * @throws ValidationException if a UPC format is invalid
     */
    @GetMapping("/upc/{upc}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ProductResponse> getProductByUpc(
            @PathVariable @NotBlank(message = "UPC cannot be blank") String upc) {

        log.debug("Retrieving product by UPC: {}", upc);

        // Validate a UPC format for SSRF protection
        validateUpcFormat(upc);

        ProductResponse response = productService.getProductByUpc(upc);

        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(5, TimeUnit.MINUTES))
                .contentType(MediaType.APPLICATION_JSON)
                .body(response);
    }

    /**
     * Gets all products with pagination.
     *
     * <p>Authorization: All authenticated users can view products.</p>
     *
     * @param pageable Pagination information (page, size, sort)
     * @return ResponseEntity containing a page of products
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Page<ProductResponse>> getAllProducts(Pageable pageable) {
        log.debug("Retrieving all products with pagination: page={}, size={}",
                pageable.getPageNumber(), pageable.getPageSize());

        Page<ProductResponse> response = productService.getAllProducts(pageable);

        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(1, TimeUnit.MINUTES))
                .contentType(MediaType.APPLICATION_JSON)
                .body(response);
    }

    /**
     * Searches products by name, brand, or category.
     *
     * <p>Authorization: All authenticated users can search for products.</p>
     *
     * <p><strong>Security Note:</strong> Search query is validated to prevent
     * SQL injection and resource exhaustion attacks.</p>
     *
     * @param query Search query string (max 100 characters)
     * @param pageable Pagination information (page, size, sort)
     * @return ResponseEntity containing a page of matching products
     * @throws ValidationException if a query is too long or contains dangerous patterns
     */
    @GetMapping("/search")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Page<ProductResponse>> searchProducts(
            @RequestParam @NotBlank(message = "Search query cannot be blank") String query,
            Pageable pageable) {

        log.debug("Searching products with query: {}", query);

        // Validate search query
        validateSearchQuery(query);

        Page<ProductResponse> response = productService.searchProducts(query, pageable);

        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(1, TimeUnit.MINUTES))
                .contentType(MediaType.APPLICATION_JSON)
                .body(response);
    }

    // ==================== Update Operations ====================

    /**
     * Updates a product completely.
     *
     * <p>Authorization: Only administrators can modify products.</p>
     *
     * @param id The UUID of the product
     * @param request DTO containing updated product information
     * @return ResponseEntity containing the updated product's details
     */
    @PutMapping("/{id}")
    @PreAuthorize("@security.isAdmin()")
    public ResponseEntity<ProductResponse> updateProduct(
            @PathVariable @NotNull UUID id,
            @Valid @RequestBody ProductUpdateRequest request) {

        log.debug("Updating product: {}", id);

        ProductResponse response = productService.updateProduct(id, request);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(response);
    }

    /**
     * Partially updates a product.
     *
     * <p>Authorization: Only administrators can patch products.</p>
     *
     * <p>Supported fields: name, brand, category, defaultExpirationDays</p>
     *
     * @param id The UUID of the product
     * @param patchData Map containing fields to update
     * @return ResponseEntity containing the updated product's details
     */
    @PatchMapping("/{id}")
    @PreAuthorize("@security.isAdmin()")
    public ResponseEntity<ProductResponse> patchProduct(
            @PathVariable @NotNull UUID id,
            @RequestBody Map<String, Object> patchData) {

        log.debug("Patching product {} with fields: {}", id, patchData.keySet());

        // Validate patch data is not empty
        if (patchData.isEmpty()) {
            throw new ValidationException("Patch data cannot be empty");
        }

        ProductResponse response = productService.patchProduct(id, patchData);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(response);
    }

    /**
     * Retries API enrichment for a product.
     *
     * <p>Authorization: Only administrators can retry API enrichment.</p>
     *
     * @param id The UUID of the product
     * @return ResponseEntity containing the updated product's details
     */
    @PostMapping("/{id}/retry")
    @PreAuthorize("@security.isAdmin()")
    public ResponseEntity<ProductResponse> retryApiEnrichment(@PathVariable @NotNull UUID id) {
        log.debug("Retrying API enrichment for product: {}", id);

        ProductResponse response = productService.retryApiEnrichment(id);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(response);
    }

    /**
     * Processes batch retry for products requiring API enrichment.
     *
     * <p>Authorization: Only administrators can process batch retries.</p>
     *
     * @param maxRetryAttempts Maximum number of retry attempts (default: 3, max: 10)
     * @return ResponseEntity containing the number of processed products
     * @throws ValidationException if maxRetryAttempts is invalid
     */
    @PostMapping("/retry/batch")
    @PreAuthorize("@security.isAdmin()")
    public ResponseEntity<Map<String, Integer>> processBatchRetry(
            @RequestParam(defaultValue = "3") int maxRetryAttempts) {

        log.debug("Processing batch retry with maxRetryAttempts: {}", maxRetryAttempts);

        // Validate retry attempts range
        if (maxRetryAttempts < 1 || maxRetryAttempts > 10) {
            throw new ValidationException("maxRetryAttempts must be between 1 and 10");
        }

        int processedCount = productService.processBatchRetry(maxRetryAttempts);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("processedCount", processedCount));
    }

    // ==================== Delete Operations ====================

    /**
     * Deletes a product.
     *
     * <p>Authorization: Only administrators can delete products.</p>
     *
     * @param id The UUID of the product to delete
     * @return ResponseEntity with no content (204)
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("@security.isAdmin()")
    public ResponseEntity<Void> deleteProduct(@PathVariable @NotNull UUID id) {
        log.debug("Deleting product: {}", id);

        productService.deleteProduct(id);

        return ResponseEntity.noContent().build();
    }

    /**
     * Bulk deletes products.
     *
     * <p>Authorization: Only administrators can bulk delete products.</p>
     *
     * @param productIds List of product UUIDs to delete (max 100)
     * @return ResponseEntity containing the number of deleted products
     * @throws ValidationException if a list is too large
     */
    @DeleteMapping("/bulk")
    @PreAuthorize("@security.isAdmin()")
    public ResponseEntity<Map<String, Integer>> bulkDeleteProducts(
            @RequestBody @NotEmpty(message = "Product IDs list cannot be empty") List<@NotNull UUID> productIds) {

        log.debug("Bulk deleting {} products", productIds.size());

        // Prevent resource exhaustion
        if (productIds.size() > 100) {
            throw new ValidationException("Cannot delete more than 100 products at once");
        }

        int deletedCount = productService.bulkDeleteProducts(productIds);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("deletedCount", deletedCount));
    }

    // ==================== Validation Helper Methods ====================

    /**
     * Validates a UPC format to prevent SSRF attacks.
     *
     * <p><strong>Security Rationale:</strong> UPCs are used to construct URLs for
     * external API calls. This validation ensures only numeric characters can be
     * used, preventing URL injection attacks.</p>
     *
     * <p>This method performs two-layer validation:</p>
     * <ol>
     *   <li>Format validation: UPC must be 8-14 digits only</li>
     *   <li>URL validation: The resulting external API URL must pass SSRF checks</li>
     * </ol>
     *
     * @param upc The UPC to validate
     * @throws ValidationException if a UPC format is invalid or would create an unsafe URL
     */
    private void validateUpcFormat(String upc) {
        if (upc == null || upc.isBlank()) {
            throw new ValidationException("UPC cannot be null or empty");
        }

        String trimmedUpc = upc.trim();

        // Layer 1: Format validation
        if (!UPC_PATTERN.matcher(trimmedUpc).matches()) {
            log.warn("SECURITY: Invalid UPC format attempted: {}",
                    trimmedUpc.replaceAll("[0-9]", "X")); // Log safely
            throw new ValidationException(
                "Invalid UPC format. Must be 8-14 digits (UPC-A, EAN-8, EAN-13, or UPC-E)");
        }

        // Layer 2: Pre-validate the URL that would be constructed
        // This stops SSRF attempts at the controller level before reaching any service
        String potentialUrl = OPEN_FOOD_FACTS_BASE_URL + "/product/" + trimmedUpc + ".json";
        try {
            UrlValidator.isValidExternalUrl(potentialUrl);
        } catch (Exception e) {
            log.warn("SECURITY: UPC would create invalid external URL: {}", trimmedUpc);
            throw new ValidationException("UPC validation failed: " + e.getMessage());
        }
    }

    /**
     * Validates a search query to prevent injection attacks and resource exhaustion.
     * 
     * <p><strong>Security Measures:</strong></p>
     * <ul>
     *   <li>Maximum length check (prevents resource exhaustion)</li>
     *   <li>SQL wildcard character limits (prevents performance issues)</li>
     *   <li>Dangerous pattern detection (prevents injection attempts)</li>
     * </ul>
     *
     * @param query The search query to validate
     * @throws ValidationException if a query is invalid or dangerous
     */
    private void validateSearchQuery(String query) {
        if (query == null || query.isBlank()) {
            throw new ValidationException("Search query cannot be null or empty");
        }

        String trimmedQuery = query.trim();

        // Prevent resource exhaustion with overly long queries
        if (trimmedQuery.length() > MAX_SEARCH_QUERY_LENGTH) {
            log.warn("SECURITY: Search query exceeds maximum length: {} characters", 
                    trimmedQuery.length());
            throw new ValidationException(
                String.format("Search query exceeds maximum length of %d characters", 
                        MAX_SEARCH_QUERY_LENGTH));
        }

        // Prevent excessive wildcard usage (can cause database performance issues)
        long wildcardCount = trimmedQuery.chars().filter(ch -> ch == '%' || ch == '_').count();
        if (wildcardCount > 5) {
            log.warn("SECURITY: Search query contains excessive wildcards: {}", wildcardCount);
            throw new ValidationException("Search query contains too many wildcard characters");
        }

        // Detect potential SQL injection patterns
        String lowerQuery = trimmedQuery.toLowerCase();
        if (lowerQuery.contains("--") || 
            lowerQuery.contains("/*") || 
            lowerQuery.contains("*/") ||
            lowerQuery.contains(";") ||
            lowerQuery.matches(".*\\b(union|select|insert|update|delete|drop|exec|execute)\\b.*")) {
            
            log.warn("SECURITY: Potentially dangerous search query detected: {}", 
                    trimmedQuery.substring(0, Math.min(20, trimmedQuery.length())));
            throw new ValidationException("Search query contains invalid characters or patterns");
        }
    }
}