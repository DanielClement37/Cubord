package org.cubord.cubordbackend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cubord.cubordbackend.domain.Product;
import org.cubord.cubordbackend.domain.ProductDataSource;
import org.cubord.cubordbackend.dto.product.ProductRequest;
import org.cubord.cubordbackend.dto.product.ProductResponse;
import org.cubord.cubordbackend.dto.product.ProductUpdateRequest;
import org.cubord.cubordbackend.exception.*;
import org.cubord.cubordbackend.repository.ProductRepository;
import org.cubord.cubordbackend.security.SecurityService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Service class for managing products.
 * 
 * <p>This service follows the modernized security architecture where:</p>
 * <ul>
 *   <li>Authentication is handled by Spring Security filters</li>
 *   <li>Authorization is declarative via @PreAuthorize annotations</li>
 *   <li>SecurityService provides business-level security context access</li>
 *   <li>No manual token validation or permission checks in business logic</li>
 * </ul>
 * 
 * <h2>Authorization Rules</h2>
 * <ul>
 *   <li><strong>Create:</strong> All authenticated users can create products</li>
 *   <li><strong>Read:</strong> All authenticated users can view products</li>
 *   <li><strong>Update:</strong> Only administrators can modify products</li>
 *   <li><strong>Delete:</strong> Only administrators can delete products</li>
 * </ul>
 * 
 * <h2>Product Data Sources</h2>
 * <p>Products can be created from multiple sources:</p>
 * <ul>
 *   <li><strong>OPEN_FOOD_FACTS:</strong> Enriched from external API</li>
 *   <li><strong>MANUAL:</strong> User-created entries</li>
 * </ul>
 *
 * @see SecurityService
 * @see UpcApiService
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProductService {

    private final ProductRepository productRepository;
    private final UpcApiService upcApiService;
    private final SecurityService securityService;

    private static final int MAX_RETRY_ATTEMPTS = 5;

    // ==================== Query Operations ====================

    /**
     * Retrieves a product by its ID.
     * 
     * <p>Authorization: All authenticated users can view products.</p>
     *
     * @param productId UUID of the product to retrieve
     * @return ProductResponse containing the product's details
     * @throws ValidationException if productId is null
     * @throws NotFoundException if product not found
     */
    @Transactional(readOnly = true)
    @PreAuthorize("isAuthenticated()")
    public ProductResponse getProductById(UUID productId) {
        if (productId == null) {
            throw new ValidationException("Product ID cannot be null");
        }

        UUID currentUserId = securityService.getCurrentUserId();
        log.debug("User {} retrieving product by ID: {}", currentUserId, productId);

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new NotFoundException("Product not found"));

        return mapToResponse(product);
    }

    /**
     * Retrieves a product by its UPC.
     * 
     * <p>Authorization: All authenticated users can view products.</p>
     *
     * @param upc UPC of the product to retrieve
     * @return ProductResponse containing the product's details
     * @throws ValidationException if upc is null or empty
     * @throws NotFoundException if product not found
     */
    @Transactional(readOnly = true)
    @PreAuthorize("isAuthenticated()")
    public ProductResponse getProductByUpc(String upc) {
        if (upc == null || upc.trim().isEmpty()) {
            throw new ValidationException("UPC cannot be null or empty");
        }

        UUID currentUserId = securityService.getCurrentUserId();
        log.debug("User {} retrieving product by UPC: {}", currentUserId, upc);

        Product product = productRepository.findByUpc(upc)
                .orElseThrow(() -> new NotFoundException("Product not found"));

        return mapToResponse(product);
    }

    /**
     * Retrieves all products with pagination support.
     * 
     * <p>Authorization: All authenticated users can view products.</p>
     *
     * @param pageable Pagination information
     * @return Page of ProductResponse objects
     */
    @Transactional(readOnly = true)
    @PreAuthorize("isAuthenticated()")
    public Page<ProductResponse> getAllProducts(Pageable pageable) {
        UUID currentUserId = securityService.getCurrentUserId();
        log.debug("User {} retrieving all products with pagination", currentUserId);

        return productRepository.findAll(pageable)
                .map(this::mapToResponse);
    }

    /**
     * Searches for products by name, brand, or category.
     * 
     * <p>Authorization: All authenticated users can search for products.</p>
     *
     * @param query Search query string
     * @param pageable Pagination information
     * @return Page of ProductResponse objects matching the query
     * @throws ValidationException if a query is null or empty
     */
    @Transactional(readOnly = true)
    @PreAuthorize("isAuthenticated()")
    public Page<ProductResponse> searchProducts(String query, Pageable pageable) {
        if (query == null || query.trim().isEmpty()) {
            throw new ValidationException("Search query cannot be null or empty");
        }

        UUID currentUserId = securityService.getCurrentUserId();
        log.debug("User {} searching products with query: {}", currentUserId, query);

        return productRepository.searchByNameBrandOrCategory(query, pageable)
                .map(this::mapToResponse);
    }

    // ==================== Create Operations ====================

    /**
     * Creates a new product with the provided information.
     * Attempts to enrich product data from external API.
     * 
     * <p>Authorization: All authenticated users can create products.</p>
     * 
     * <p>The method first checks if a product with the given UPC already exists.
     * If not, it attempts to fetch data from the Open Food Facts API to enrich
     * the product. If the API call fails, a manual entry is created with retry enabled.</p>
     *
     * @param request DTO containing the product information
     * @return ProductResponse containing the created product's details
     * @throws ValidationException if the request is null or UPC is invalid
     * @throws ConflictException if a product with UPC already exists
     * @throws DataIntegrityException if product creation fails
     */
    @Transactional
    @PreAuthorize("isAuthenticated()")
    public ProductResponse createProduct(ProductRequest request) {
        if (request == null) {
            throw new ValidationException("Product request cannot be null");
        }

        UUID currentUserId = securityService.getCurrentUserId();
        log.debug("User {} creating product with UPC: {}", currentUserId, request.getUpc());

        // Validate UPC format
        if (request.getUpc() == null || request.getUpc().trim().isEmpty()) {
            throw new ValidationException("UPC cannot be null or empty");
        }

        // Check if UPC already exists
        if (productRepository.findByUpc(request.getUpc()).isPresent()) {
            throw new ConflictException("Product with UPC '" + request.getUpc() + "' already exists");
        }

        Product product = createProductFromRequest(request);

        // Try to enrich with API data
        try {
            ProductResponse apiData = upcApiService.fetchProductData(request.getUpc());
            enrichProductWithApiData(product, apiData);
            product.setDataSource(ProductDataSource.OPEN_FOOD_FACTS);
            product.setRequiresApiRetry(false);
            log.debug("Successfully enriched product with UPC: {} from external API", request.getUpc());
        } catch (Exception e) {
            log.warn("Failed to fetch API data for UPC: {}, creating manual entry", request.getUpc(), e);
            product.setDataSource(ProductDataSource.MANUAL);
            product.setRequiresApiRetry(true);
            product.setRetryAttempts(0);
        }

        try {
            Product savedProduct = productRepository.save(product);
            log.info("User {} successfully created product with ID: {}", currentUserId, savedProduct.getId());
            return mapToResponse(savedProduct);
        } catch (Exception e) {
            log.error("Failed to save product with UPC: {}", request.getUpc(), e);
            throw new DataIntegrityException("Failed to save product: " + e.getMessage(), e);
        }
    }

    // ==================== Update Operations ====================

    /**
     * Updates a product with the provided information.
     * 
     * <p>Authorization: Only administrators can update products.</p>
     *
     * @param productId UUID of the product to update
     * @param request DTO containing the updated product information
     * @return ProductResponse containing the updated product's details
     * @throws ValidationException if inputs are null
     * @throws InsufficientPermissionException if the user is not an admin
     * @throws NotFoundException if product not found
     * @throws DataIntegrityException if update fails
     */
    @Transactional
    @PreAuthorize("@security.isAdmin()")
    public ProductResponse updateProduct(UUID productId, ProductUpdateRequest request) {
        if (productId == null) {
            throw new ValidationException("Product ID cannot be null");
        }
        if (request == null) {
            throw new ValidationException("Update request cannot be null");
        }

        UUID currentUserId = securityService.getCurrentUserId();
        log.debug("Admin user {} updating product: {}", currentUserId, productId);

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new NotFoundException("Product not found"));

        updateProductFromRequest(product, request);

        try {
            Product savedProduct = productRepository.save(product);
            log.info("Admin user {} successfully updated product with ID: {}", currentUserId, productId);
            return mapToResponse(savedProduct);
        } catch (Exception e) {
            log.error("Failed to update product with ID: {}", productId, e);
            throw new DataIntegrityException("Failed to update product: " + e.getMessage(), e);
        }
    }

    /**
     * Partially updates a product with the provided field values.
     * 
     * <p>Authorization: Only administrators can patch products.</p>
     * 
     * <p>Supported fields: name, brand, category, defaultExpirationDays</p>
     *
     * @param productId UUID of the product to patch
     * @param patchData Map containing field names and their new values
     * @return ProductResponse containing the updated product's details
     * @throws ValidationException if inputs are null or invalid
     * @throws InsufficientPermissionException if the user is not an admin
     * @throws NotFoundException if product not found
     * @throws DataIntegrityException if the patch fails
     */
    @Transactional
    @PreAuthorize("@security.isAdmin()")
    public ProductResponse patchProduct(UUID productId, Map<String, Object> patchData) {
        if (productId == null) {
            throw new ValidationException("Product ID cannot be null");
        }
        if (patchData == null || patchData.isEmpty()) {
            throw new ValidationException("Patch data cannot be null or empty");
        }

        UUID currentUserId = securityService.getCurrentUserId();
        log.debug("Admin user {} patching product: {} with fields: {}", 
                currentUserId, productId, patchData.keySet());

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new NotFoundException("Product not found"));

        applyPatchToProduct(product, patchData);

        try {
            Product savedProduct = productRepository.save(product);
            log.info("Admin user {} successfully patched product with ID: {}", currentUserId, productId);
            return mapToResponse(savedProduct);
        } catch (Exception e) {
            log.error("Failed to patch product with ID: {}", productId, e);
            throw new DataIntegrityException("Failed to patch product: " + e.getMessage(), e);
        }
    }

    // ==================== Delete Operations ====================

    /**
     * Deletes a product.
     * 
     * <p>Authorization: Only administrators can delete products.</p>
     *
     * @param productId UUID of the product to delete
     * @throws ValidationException if productId is null
     * @throws InsufficientPermissionException if the user is not an admin
     * @throws NotFoundException if product not found
     * @throws DataIntegrityException if deletion fails
     */
    @Transactional
    @PreAuthorize("@security.isAdmin()")
    public void deleteProduct(UUID productId) {
        if (productId == null) {
            throw new ValidationException("Product ID cannot be null");
        }

        UUID currentUserId = securityService.getCurrentUserId();
        log.debug("Admin user {} deleting product: {}", currentUserId, productId);

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new NotFoundException("Product not found"));

        try {
            productRepository.delete(product);
            log.info("Admin user {} successfully deleted product with ID: {}", currentUserId, productId);
        } catch (Exception e) {
            log.error("Failed to delete product with ID: {}", productId, e);
            throw new DataIntegrityException("Failed to delete product: " + e.getMessage(), e);
        }
    }

    // ==================== Retry Operations ====================

    /**
     * Attempts to retry API enrichment for products that previously failed.
     * 
     * <p>Authorization: Only administrators can retry API enrichment.</p>
     * 
     * <p>This method processes products marked for retry, attempting to fetch
     * data from the external API. Products are marked as no longer requiring
     * retry after MAX_RETRY_ATTEMPTS failures.</p>
     *
     * @return Number of products successfully enriched
     */
    @Transactional
    @PreAuthorize("@security.isAdmin()")
    public int retryApiEnrichment() {
        UUID currentUserId = securityService.getCurrentUserId();
        log.info("Admin user {} initiating API retry enrichment", currentUserId);

        var productsToRetry = productRepository.findByRequiresApiRetryTrueAndRetryAttemptsLessThan(MAX_RETRY_ATTEMPTS);
        int successCount = 0;

        for (Product product : productsToRetry) {
            try {
                ProductResponse apiData = upcApiService.fetchProductData(product.getUpc());
                enrichProductWithApiData(product, apiData);
                product.setDataSource(ProductDataSource.OPEN_FOOD_FACTS);
                product.setRequiresApiRetry(false);
                productRepository.save(product);
                successCount++;
                log.debug("Successfully enriched product {} on retry", product.getId());
            } catch (Exception e) {
                product.setRetryAttempts(product.getRetryAttempts() + 1);
                if (product.getRetryAttempts() >= MAX_RETRY_ATTEMPTS) {
                    product.setRequiresApiRetry(false);
                    log.warn("Product {} exceeded max retry attempts", product.getId());
                }
                productRepository.save(product);
                log.debug("Failed to enrich product {} on retry: {}", product.getId(), e.getMessage());
            }
        }

        log.info("Admin user {} completed API retry enrichment: {} of {} products enriched", 
                currentUserId, successCount, productsToRetry.size());
        return successCount;
    }

    // ==================== Helper Methods ====================

    /**
     * Creates a Product entity from a ProductRequest DTO.
     *
     * @param request ProductRequest DTO
     * @return Product entity
     */
    private Product createProductFromRequest(ProductRequest request) {
        LocalDateTime now = LocalDateTime.now();
        return Product.builder()
                .upc(request.getUpc())
                .name(request.getName())
                .brand(request.getBrand())
                .category(request.getCategory())
                .defaultExpirationDays(request.getDefaultExpirationDays())
                .dataSource(ProductDataSource.MANUAL)
                .requiresApiRetry(false)
                .retryAttempts(0)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    /**
     * Enriches a Product entity with data from the external API.
     *
     * @param product Product entity to enrich
     * @param apiData ProductResponse from API
     */
    private void enrichProductWithApiData(Product product, ProductResponse apiData) {
        if (apiData.getName() != null && !apiData.getName().isBlank()) {
            product.setName(apiData.getName());
        }
        if (apiData.getBrand() != null && !apiData.getBrand().isBlank()) {
            product.setBrand(apiData.getBrand());
        }
        if (apiData.getCategory() != null && !apiData.getCategory().isBlank()) {
            product.setCategory(apiData.getCategory());
        }
        product.setUpdatedAt(LocalDateTime.now());
    }

    /**
     * Updates a Product entity from a ProductUpdateRequest DTO.
     *
     * @param product Product entity to update
     * @param request ProductUpdateRequest DTO
     */
    private void updateProductFromRequest(Product product, ProductUpdateRequest request) {
        if (request.getName() != null) {
            product.setName(request.getName());
        }
        if (request.getBrand() != null) {
            product.setBrand(request.getBrand());
        }
        if (request.getCategory() != null) {
            product.setCategory(request.getCategory());
        }
        if (request.getDefaultExpirationDays() != null) {
            product.setDefaultExpirationDays(request.getDefaultExpirationDays());
        }
        product.setUpdatedAt(LocalDateTime.now());
    }

    /**
     * Applies a patch map to a Product entity.
     *
     * @param product Product entity to patch
     * @param patchData Map of field names to new values
     * @throws ValidationException if an unsupported field is provided
     */
    private void applyPatchToProduct(Product product, Map<String, Object> patchData) {
        patchData.forEach((field, value) -> {
            switch (field) {
                case "name":
                    if (value != null) {
                        product.setName(value.toString());
                        log.debug("Patched name for product {}", product.getId());
                    }
                    break;

                case "brand":
                    if (value != null) {
                        product.setBrand(value.toString());
                        log.debug("Patched brand for product {}", product.getId());
                    }
                    break;

                case "category":
                    if (value != null) {
                        product.setCategory(value.toString());
                        log.debug("Patched category for product {}", product.getId());
                    }
                    break;

                case "defaultExpirationDays":
                    if (value != null) {
                        try {
                            Integer days = value instanceof Integer ? (Integer) value : Integer.parseInt(value.toString());
                            product.setDefaultExpirationDays(days);
                            log.debug("Patched defaultExpirationDays for product {}", product.getId());
                        } catch (NumberFormatException e) {
                            throw new ValidationException("Invalid value for defaultExpirationDays: " + value);
                        }
                    }
                    break;

                default:
                    log.warn("Attempted to patch unsupported field: {}", field);
                    throw new ValidationException("Unsupported field for patching: " + field);
            }
        });
        product.setUpdatedAt(LocalDateTime.now());
    }

    /**
     * Maps a Product entity to a ProductResponse DTO.
     *
     * @param product Product entity
     * @return ProductResponse DTO
     */
    private ProductResponse mapToResponse(Product product) {
        return ProductResponse.builder()
                .id(product.getId())
                .upc(product.getUpc())
                .name(product.getName())
                .brand(product.getBrand())
                .category(product.getCategory())
                .defaultExpirationDays(product.getDefaultExpirationDays())
                .dataSource(product.getDataSource())
                .requiresApiRetry(product.getRequiresApiRetry())
                .retryAttempts(product.getRetryAttempts())
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .build();
    }

    // ==================== Deprecated Methods ====================

    /**
     * Creates a new product with the provided information using JWT token.
     * 
     * @deprecated Use {@link #createProduct(ProductRequest)} instead.
     *             This method is maintained for backward compatibility with controllers
     *             that haven't been migrated to the new security architecture.
     *             Token-based authentication is now handled by Spring Security filters.
     * 
     * @param request DTO containing the product information
     * @param token JWT authentication token (ignored in favor of SecurityContext)
     * @return ProductResponse containing the created product's details
     * @throws ValidationException if the request is null
     * @throws ConflictException if a product with UPC already exists
     * @throws DataIntegrityException if product creation fails
     */
    @Deprecated(since = "2.0", forRemoval = true)
    @Transactional
    @PreAuthorize("isAuthenticated()")
    public ProductResponse createProduct(ProductRequest request, org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken token) {
        log.warn("DEPRECATED: createProduct(ProductRequest, JwtAuthenticationToken) called. " +
                "Migrate to createProduct(ProductRequest) for improved security architecture. " +
                "The token parameter is ignored - using SecurityContext instead.");
        
        return createProduct(request);
    }

    /**
     * Retrieves a product by its ID using JWT token.
     * 
     * @deprecated Use {@link #getProductById(UUID)} instead.
     *             This method is maintained for backward compatibility with controllers
     *             that haven't been migrated to the new security architecture.
     * 
     * @param productId UUID of the product to retrieve
     * @param token JWT authentication token (ignored in favor of SecurityContext)
     * @return ProductResponse containing the product's details
     * @throws ValidationException if productId is null
     * @throws NotFoundException if product not found
     */
    @Deprecated(since = "2.0", forRemoval = true)
    @Transactional(readOnly = true)
    @PreAuthorize("isAuthenticated()")
    public ProductResponse getProductById(UUID productId, org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken token) {
        log.warn("DEPRECATED: getProductById(UUID, JwtAuthenticationToken) called. " +
                "Migrate to getProductById(UUID) for improved security architecture.");
        
        return getProductById(productId);
    }

    /**
     * Retrieves a product by its UPC using JWT token.
     * 
     * @deprecated Use {@link #getProductByUpc(String)} instead.
     *             This method is maintained for backward compatibility with controllers
     *             that haven't been migrated to the new security architecture.
     * 
     * @param upc UPC of the product to retrieve
     * @param token JWT authentication token (ignored in favor of SecurityContext)
     * @return ProductResponse containing the product's details
     * @throws ValidationException if upc is null or empty
     * @throws NotFoundException if product not found
     */
    @Deprecated(since = "2.0", forRemoval = true)
    @Transactional(readOnly = true)
    @PreAuthorize("isAuthenticated()")
    public ProductResponse getProductByUpc(String upc, org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken token) {
        log.warn("DEPRECATED: getProductByUpc(String, JwtAuthenticationToken) called. " +
                "Migrate to getProductByUpc(String) for improved security architecture.");
        
        return getProductByUpc(upc);
    }

    /**
     * Retrieves all products with pagination using JWT token.
     * 
     * @deprecated Use {@link #getAllProducts(Pageable)} instead.
     *             This method is maintained for backward compatibility with controllers
     *             that haven't been migrated to the new security architecture.
     * 
     * @param pageable Pagination information
     * @param token JWT authentication token (ignored in favor of SecurityContext)
     * @return Page of ProductResponse objects
     */
    @Deprecated(since = "2.0", forRemoval = true)
    @Transactional(readOnly = true)
    @PreAuthorize("isAuthenticated()")
    public Page<ProductResponse> getAllProducts(Pageable pageable, org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken token) {
        log.warn("DEPRECATED: getAllProducts(Pageable, JwtAuthenticationToken) called. " +
                "Migrate to getAllProducts(Pageable) for improved security architecture.");
        
        return getAllProducts(pageable);
    }

    /**
     * Updates a product with the provided information using JWT token.
     * 
     * @deprecated Use {@link #updateProduct(UUID, ProductUpdateRequest)} instead.
     *             This method is maintained for backward compatibility with controllers
     *             that haven't been migrated to the new security architecture.
     * 
     * @param productId UUID of the product to update
     * @param request DTO containing the updated product information
     * @param token JWT authentication token (ignored in favor of SecurityContext)
     * @return ProductResponse containing the updated product's details
     * @throws ValidationException if inputs are null
     * @throws InsufficientPermissionException if the user is not an admin
     * @throws NotFoundException if product not found
     */
    @Deprecated(since = "2.0", forRemoval = true)
    @Transactional
    @PreAuthorize("@security.isAdmin()")
    public ProductResponse updateProduct(UUID productId, ProductUpdateRequest request, org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken token) {
        log.warn("DEPRECATED: updateProduct(UUID, ProductUpdateRequest, JwtAuthenticationToken) called. " +
                "Migrate to updateProduct(UUID, ProductUpdateRequest) for improved security architecture.");
        
        return updateProduct(productId, request);
    }

    /**
     * Partially updates a product with the provided field values using JWT token.
     * 
     * @deprecated Use {@link #patchProduct(UUID, Map)} instead.
     *             This method is maintained for backward compatibility with controllers
     *             that haven't been migrated to the new security architecture.
     * 
     * @param productId UUID of the product to patch
     * @param patchData Map containing field names and their new values
     * @param token JWT authentication token (ignored in favor of SecurityContext)
     * @return ProductResponse containing the updated product's details
     * @throws ValidationException if inputs are null or invalid
     * @throws InsufficientPermissionException if the user is not an admin
     * @throws NotFoundException if product not found
     */
    @Deprecated(since = "2.0", forRemoval = true)
    @Transactional
    @PreAuthorize("@security.isAdmin()")
    public ProductResponse patchProduct(UUID productId, Map<String, Object> patchData, org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken token) {
        log.warn("DEPRECATED: patchProduct(UUID, Map, JwtAuthenticationToken) called. " +
                "Migrate to patchProduct(UUID, Map) for improved security architecture.");
        
        return patchProduct(productId, patchData);
    }

    /**
     * Deletes a product using JWT token.
     * 
     * @deprecated Use {@link #deleteProduct(UUID)} instead.
     *             This method is maintained for backward compatibility with controllers
     *             that haven't been migrated to the new security architecture.
     * 
     * @param productId UUID of the product to delete
     * @param token JWT authentication token (ignored in favor of SecurityContext)
     * @throws ValidationException if productId is null
     * @throws InsufficientPermissionException if the user is not an admin
     * @throws NotFoundException if product not found
     */
    @Deprecated(since = "2.0", forRemoval = true)
    @Transactional
    @PreAuthorize("@security.isAdmin()")
    public void deleteProduct(UUID productId, org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken token) {
        log.warn("DEPRECATED: deleteProduct(UUID, JwtAuthenticationToken) called. " +
                "Migrate to deleteProduct(UUID) for improved security architecture.");
        
        deleteProduct(productId);
    }

    /**
     * Searches for products by name using JWT token.
     * 
     * @deprecated Use {@link #searchProducts(String, Pageable)} instead.
     *             This method is maintained for backward compatibility with controllers
     *             that haven't been migrated to the new security architecture.
     * 
     * @param name Search term for product names
     * @param token JWT authentication token (ignored in favor of SecurityContext)
     * @return List of ProductResponse objects matching the search
     */
    @Deprecated(since = "2.0", forRemoval = true)
    @Transactional(readOnly = true)
    @PreAuthorize("isAuthenticated()")
    public List<ProductResponse> searchProductsByName(String name, org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken token) {
        log.warn("DEPRECATED: searchProductsByName(String, JwtAuthenticationToken) called. " +
                "Migrate to searchProducts(String, Pageable) for improved security architecture.");
        
        if (name == null || name.trim().isEmpty()) {
            throw new ValidationException("Search name cannot be null or empty");
        }

        UUID currentUserId = securityService.getCurrentUserId();
        log.debug("User {} searching products by name: {}", currentUserId, name);

        return productRepository.findByNameContainingIgnoreCase(name)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    /**
     * Retrieves products by category using JWT token.
     * 
     * @deprecated This method will be replaced by the search functionality.
     *             Use {@link #searchProducts(String, Pageable)} instead.
     * 
     * @param category Product category
     * @param token JWT authentication token (ignored in favor of SecurityContext)
     * @return List of ProductResponse objects in the category
     */
    @Deprecated(since = "2.0", forRemoval = true)
    @Transactional(readOnly = true)
    @PreAuthorize("isAuthenticated()")
    public List<ProductResponse> getProductsByCategory(String category, org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken token) {
        log.warn("DEPRECATED: getProductsByCategory(String, JwtAuthenticationToken) called. " +
                "Migrate to searchProducts(String, Pageable) for improved security architecture.");
        
        if (category == null || category.trim().isEmpty()) {
            throw new ValidationException("Category cannot be null or empty");
        }

        UUID currentUserId = securityService.getCurrentUserId();
        log.debug("User {} retrieving products by category: {}", currentUserId, category);

        return productRepository.findByCategory(category)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    /**
     * Retrieves products by brand using JWT token.
     * 
     * @deprecated This method will be replaced by the search functionality.
     *             Use {@link #searchProducts(String, Pageable)} instead.
     * 
     * @param brand Product brand
     * @param token JWT authentication token (ignored in favor of SecurityContext)
     * @return List of ProductResponse objects from the brand
     */
    @Deprecated(since = "2.0", forRemoval = true)
    @Transactional(readOnly = true)
    @PreAuthorize("isAuthenticated()")
    public List<ProductResponse> getProductsByBrand(String brand, org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken token) {
        log.warn("DEPRECATED: getProductsByBrand(String, JwtAuthenticationToken) called. " +
                "Migrate to searchProducts(String, Pageable) for improved security architecture.");
        
        if (brand == null || brand.trim().isEmpty()) {
            throw new ValidationException("Brand cannot be null or empty");
        }

        UUID currentUserId = securityService.getCurrentUserId();
        log.debug("User {} retrieving products by brand: {}", currentUserId, brand);

        return productRepository.findByBrand(brand)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    /**
     * Retrieves products by data source using JWT token.
     * 
     * @deprecated This method is primarily for admin use. Consider removing or
     *             restricting access in future versions.
     * 
     * @param dataSource Product data source
     * @param token JWT authentication token (ignored in favor of SecurityContext)
     * @return List of ProductResponse objects from the data source
     */
    @Deprecated(since = "2.0", forRemoval = true)
    @Transactional(readOnly = true)
    @PreAuthorize("isAuthenticated()")
    public List<ProductResponse> getProductsByDataSource(ProductDataSource dataSource, org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken token) {
        log.warn("DEPRECATED: getProductsByDataSource(ProductDataSource, JwtAuthenticationToken) called. " +
                "This method may be restricted to admins in future versions.");
        
        if (dataSource == null) {
            throw new ValidationException("Data source cannot be null");
        }

        UUID currentUserId = securityService.getCurrentUserId();
        log.debug("User {} retrieving products by data source: {}", currentUserId, dataSource);

        return productRepository.findByDataSource(dataSource)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    /**
     * Retrieves product statistics using JWT token.
     *
     * @deprecated This method is primarily for admin use. Consider adding proper
     *             admin-only restrictions in future versions.
     *
     * @param token JWT authentication token (ignored in favor of SecurityContext)
     * @return Map containing product statistics
     */
    @Deprecated(since = "2.0", forRemoval = true)
    @Transactional(readOnly = true)
    @PreAuthorize("isAuthenticated()")
    public Map<String, Long> getProductStatistics(org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken token) {
        log.warn("DEPRECATED: getProductStatistics(JwtAuthenticationToken) called. " +
                "This method may be restricted to admins in future versions.");

        UUID currentUserId = securityService.getCurrentUserId();
        log.debug("User {} retrieving product statistics", currentUserId);

        Map<String, Long> stats = new HashMap<>();
        stats.put("totalProducts", productRepository.count());
        stats.put("manualProducts", productRepository.countByDataSource(ProductDataSource.MANUAL));
        stats.put("apiProducts", productRepository.countByDataSource(ProductDataSource.OPEN_FOOD_FACTS));
        stats.put("productsRequiringRetry", productRepository.countByRequiresApiRetryTrue());

        return stats;
    }

    /**
     * Checks if a UPC is available using JWT token.
     *
     * @deprecated This functionality should be part of the create/update validation
     *             rather than a separate endpoint.
     *
     * @param upc UPC code to check
     * @param token JWT authentication token (ignored in favor of SecurityContext)
     * @return true if UPC is available, false otherwise
     */
    @Deprecated(since = "2.0", forRemoval = true)
    @Transactional(readOnly = true)
    @PreAuthorize("isAuthenticated()")
    public boolean isUpcAvailable(String upc, org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken token) {
        log.warn("DEPRECATED: isUpcAvailable(String, JwtAuthenticationToken) called. " +
                "This check is now part of the create/update validation logic.");

        if (upc == null || upc.trim().isEmpty()) {
            throw new ValidationException("UPC cannot be null or empty");
        }

        UUID currentUserId = securityService.getCurrentUserId();
        log.debug("User {} checking UPC availability: {}", currentUserId, upc);

        return productRepository.findByUpc(upc).isEmpty();
    }

    /**
     * Retrieves products requiring API retry using JWT token.
     *
     * @deprecated Use admin dashboard or scheduled jobs for retry management.
     *             This method may be restricted to admins in future versions.
     *
     * @param token JWT authentication token (ignored in favor of SecurityContext)
     * @return List of ProductResponse objects requiring retry
     */
    @Deprecated(since = "2.0", forRemoval = true)
    @Transactional(readOnly = true)
    @PreAuthorize("isAuthenticated()")
    public List<ProductResponse> getProductsRequiringRetry(org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken token) {
        log.warn("DEPRECATED: getProductsRequiringRetry(JwtAuthenticationToken) called. " +
                "This method may be restricted to admins in future versions.");

        UUID currentUserId = securityService.getCurrentUserId();
        log.debug("User {} retrieving products requiring retry", currentUserId);

        return productRepository.findByRequiresApiRetryTrue()
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    /**
     * Retries API enrichment for a specific product using JWT token.
     *
     * @deprecated Use {@link #retryApiEnrichment()} for batch processing instead.
     *             Single product retry is now handled through the batch retry mechanism.
     *
     * @param productId UUID of the product to retry
     * @param token JWT authentication token (ignored in favor of SecurityContext)
     * @return ProductResponse containing the updated product's details
     */
    @Deprecated(since = "2.0", forRemoval = true)
    @Transactional
    @PreAuthorize("@security.isAdmin()")
    public ProductResponse retryApiEnrichment(UUID productId, org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken token) {
        log.warn("DEPRECATED: retryApiEnrichment(UUID, JwtAuthenticationToken) called. " +
                "Migrate to batch retry mechanism via retryApiEnrichment() for improved efficiency.");

        if (productId == null) {
            throw new ValidationException("Product ID cannot be null");
        }

        UUID currentUserId = securityService.getCurrentUserId();
        log.debug("Admin user {} retrying API enrichment for product: {}", currentUserId, productId);

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new NotFoundException("Product not found"));

        try {
            ProductResponse apiData = upcApiService.fetchProductData(product.getUpc());
            enrichProductWithApiData(product, apiData);
            product.setDataSource(ProductDataSource.OPEN_FOOD_FACTS);
            product.setRequiresApiRetry(false);

            Product savedProduct = productRepository.save(product);
            log.info("Admin user {} successfully enriched product {}", currentUserId, productId);
            return mapToResponse(savedProduct);
        } catch (Exception e) {
            product.setRetryAttempts(product.getRetryAttempts() + 1);
            if (product.getRetryAttempts() >= MAX_RETRY_ATTEMPTS) {
                product.setRequiresApiRetry(false);
                log.warn("Product {} exceeded max retry attempts", productId);
            }
            productRepository.save(product);
            log.debug("Failed to enrich product {} on retry: {}", productId, e.getMessage());
            throw new ExternalServiceException("Failed to enrich product from API: " + e.getMessage(), e);
        }
    }

    /**
     * Processes batch retry for products requiring API enrichment using JWT token.
     *
     * @deprecated Use {@link #retryApiEnrichment()} instead.
     *             The max retry attempts parameter is now a constant in the service.
     *
     * @param maxRetryAttempts Maximum number of retry attempts
     * @param token JWT authentication token (ignored in favor of SecurityContext)
     * @return Number of products successfully enriched
     */
    @Deprecated(since = "2.0", forRemoval = true)
    @Transactional
    @PreAuthorize("@security.isAdmin()")
    public int processBatchRetry(int maxRetryAttempts, org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken token) {
        log.warn("DEPRECATED: processBatchRetry(int, JwtAuthenticationToken) called. " +
                "Migrate to retryApiEnrichment() for improved security architecture. " +
                "The maxRetryAttempts parameter is ignored - using service constant instead.");

        return retryApiEnrichment();
    }

    /**
     * Bulk imports products using JWT token.
     *
     * @deprecated Bulk operations should be handled through separate admin tools
     *             or background jobs for better performance and error handling.
     *
     * @param requests List of product requests to import
     * @param token JWT authentication token (ignored in favor of SecurityContext)
     * @return List of created ProductResponse objects
     */
    @Deprecated(since = "2.0", forRemoval = true)
    @Transactional
    @PreAuthorize("@security.isAdmin()")
    public List<ProductResponse> bulkImportProducts(List<ProductRequest> requests, org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken token) {
        log.warn("DEPRECATED: bulkImportProducts(List, JwtAuthenticationToken) called. " +
                "Consider using dedicated bulk import tools for better performance.");

        if (requests == null || requests.isEmpty()) {
            throw new ValidationException("Import requests cannot be null or empty");
        }

        UUID currentUserId = securityService.getCurrentUserId();
        log.info("Admin user {} importing {} products", currentUserId, requests.size());

        List<ProductResponse> results = new ArrayList<>();
        for (ProductRequest request : requests) {
            try {
                results.add(createProduct(request));
            } catch (Exception e) {
                log.warn("Failed to import product with UPC {}: {}", request.getUpc(), e.getMessage());
                // Continue with other products
            }
        }

        log.info("Admin user {} successfully imported {} of {} products",
                currentUserId, results.size(), requests.size());
        return results;
    }

    /**
     * Bulk deletes products using JWT token.
     *
     * @deprecated Bulk operations should be handled through separate admin tools
     *             or background jobs for better performance and error handling.
     *
     * @param productIds List of product UUIDs to delete
     * @param token JWT authentication token (ignored in favor of SecurityContext)
     * @return Number of products successfully deleted
     */
    @Deprecated(since = "2.0", forRemoval = true)
    @Transactional
    @PreAuthorize("@security.isAdmin()")
    public int bulkDeleteProducts(List<UUID> productIds, org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken token) {
        log.warn("DEPRECATED: bulkDeleteProducts(List, JwtAuthenticationToken) called. " +
                "Consider using dedicated bulk delete tools for better performance.");

        if (productIds == null || productIds.isEmpty()) {
            throw new ValidationException("Product IDs cannot be null or empty");
        }

        UUID currentUserId = securityService.getCurrentUserId();
        log.info("Admin user {} deleting {} products", currentUserId, productIds.size());

        int deletedCount = 0;
        for (UUID productId : productIds) {
            try {
                deleteProduct(productId);
                deletedCount++;
            } catch (Exception e) {
                log.warn("Failed to delete product {}: {}", productId, e.getMessage());
                // Continue with other products
            }
        }

        log.info("Admin user {} successfully deleted {} of {} products",
                currentUserId, deletedCount, productIds.size());
        return deletedCount;
    }
}