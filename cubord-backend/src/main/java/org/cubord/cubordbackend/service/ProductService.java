package org.cubord.cubordbackend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cubord.cubordbackend.domain.Product;
import org.cubord.cubordbackend.domain.ProductDataSource;
import org.cubord.cubordbackend.domain.User;
import org.cubord.cubordbackend.domain.UserRole;
import org.cubord.cubordbackend.dto.product.ProductRequest;
import org.cubord.cubordbackend.dto.product.ProductResponse;
import org.cubord.cubordbackend.dto.product.ProductUpdateRequest;
import org.cubord.cubordbackend.exception.*;
import org.cubord.cubordbackend.repository.ProductRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service class for managing products.
 * Provides operations for creating, retrieving, updating, and deleting products,
 * with proper authorization checks and exception handling.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProductService {

    private final ProductRepository productRepository;
    private final UpcApiService upcApiService;
    private final UserService userService;

    private static final int MAX_RETRY_ATTEMPTS = 5;

    /**
     * Validates that the current user has admin privileges.
     *
     * @param currentUser User to validate
     * @param operation Operation being attempted
     * @throws InsufficientPermissionException if user is not an admin
     */
    private void validateAdminAccess(User currentUser, String operation) {
        if (currentUser.getRole() != UserRole.ADMIN) {
            log.warn("User {} attempted {} operation without admin privileges",
                    currentUser.getId(), operation);
            throw new InsufficientPermissionException("Only administrators can " + operation + " products");
        }
        log.debug("Admin user {} authorized for {} operation", currentUser.getId(), operation);
    }

    /**
     * Validates that the user is authenticated for create operations.
     * All authenticated users can create products.
     *
     * @param currentUser User to validate
     */
    private void validateCreateAccess(User currentUser) {
        // All authenticated users can create products
        log.debug("User {} authorized for create operation", currentUser.getId());
    }

    /**
     * Validates that the user is authenticated for read operations.
     * All authenticated users can read products.
     *
     * @param currentUser User to validate
     */
    private void validateReadAccess(User currentUser) {
        // All authenticated users can read products
        log.debug("User {} authorized for read operation", currentUser.getId());
    }

    /**
     * Creates a new product with the provided information.
     * Attempts to enrich product data from external API.
     *
     * @param request DTO containing the product information
     * @param token JWT authentication token of the current user
     * @return ProductResponse containing the created product's details
     * @throws ValidationException if request or token is null
     * @throws ConflictException if product with UPC already exists
     * @throws DataIntegrityException if product creation fails
     */
    @Transactional
    public ProductResponse createProduct(ProductRequest request, JwtAuthenticationToken token) {
        // Validate inputs
        if (request == null) {
            throw new ValidationException("Product request cannot be null");
        }
        if (token == null) {
            throw new ValidationException("Authentication token cannot be null");
        }

        // Validate authentication - allow all authenticated users to create
        User currentUser = userService.getCurrentUser(token);
        validateCreateAccess(currentUser);

        log.debug("User {} creating product with UPC: {}", currentUser.getId(), request.getUpc());

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
            log.info("User {} successfully created product with ID: {}", currentUser.getId(), savedProduct.getId());
            return mapToResponse(savedProduct);
        } catch (Exception e) {
            log.error("Failed to save product with UPC: {}", request.getUpc(), e);
            throw new DataIntegrityException("Failed to save product: " + e.getMessage(), e);
        }
    }

    /**
     * Retrieves a product by its ID.
     *
     * @param productId UUID of the product to retrieve
     * @param token JWT authentication token of the current user
     * @return ProductResponse containing the product's details
     * @throws ValidationException if productId or token is null
     * @throws NotFoundException if product not found
     */
    @Transactional(readOnly = true)
    public ProductResponse getProductById(UUID productId, JwtAuthenticationToken token) {
        // Validate inputs
        if (productId == null) {
            throw new ValidationException("Product ID cannot be null");
        }
        if (token == null) {
            throw new ValidationException("Authentication token cannot be null");
        }

        // Validate authentication
        User currentUser = userService.getCurrentUser(token);
        validateReadAccess(currentUser);

        log.debug("User {} getting product by ID: {}", currentUser.getId(), productId);

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new NotFoundException("Product not found"));

        return mapToResponse(product);
    }

    /**
     * Retrieves a product by its UPC code.
     *
     * @param upc UPC code of the product to retrieve
     * @param token JWT authentication token of the current user
     * @return ProductResponse containing the product's details
     * @throws ValidationException if upc or token is null or empty
     * @throws NotFoundException if product not found
     */
    @Transactional(readOnly = true)
    public ProductResponse getProductByUpc(String upc, JwtAuthenticationToken token) {
        // Validate inputs
        if (upc == null || upc.trim().isEmpty()) {
            throw new ValidationException("UPC cannot be null or empty");
        }
        if (token == null) {
            throw new ValidationException("Authentication token cannot be null");
        }

        // Validate authentication
        User currentUser = userService.getCurrentUser(token);
        validateReadAccess(currentUser);

        log.debug("User {} getting product by UPC: {}", currentUser.getId(), upc);

        Product product = productRepository.findByUpc(upc)
                .orElseThrow(() -> new NotFoundException("Product not found"));

        return mapToResponse(product);
    }

    /**
     * Updates a product with the provided information.
     * Only administrators can update products.
     *
     * @param productId UUID of the product to update
     * @param request DTO containing the updated product information
     * @param token JWT authentication token of the current user
     * @return ProductResponse containing the updated product's details
     * @throws ValidationException if inputs are null
     * @throws InsufficientPermissionException if user is not an admin
     * @throws NotFoundException if product not found
     * @throws DataIntegrityException if update fails
     */
    @Transactional
    public ProductResponse updateProduct(UUID productId, ProductUpdateRequest request, JwtAuthenticationToken token) {
        // Validate inputs
        if (productId == null) {
            throw new ValidationException("Product ID cannot be null");
        }
        if (request == null) {
            throw new ValidationException("Update request cannot be null");
        }
        if (token == null) {
            throw new ValidationException("Authentication token cannot be null");
        }

        // Validate authentication and authorization - only admins can update
        User currentUser = userService.getCurrentUser(token);
        validateAdminAccess(currentUser, "update");

        log.debug("Admin user {} updating product: {}", currentUser.getId(), productId);

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new NotFoundException("Product not found"));

        updateProductFromRequest(product, request);

        try {
            Product savedProduct = productRepository.save(product);
            log.info("Admin user {} successfully updated product with ID: {}", currentUser.getId(), productId);
            return mapToResponse(savedProduct);
        } catch (Exception e) {
            log.error("Failed to update product with ID: {}", productId, e);
            throw new DataIntegrityException("Failed to update product: " + e.getMessage(), e);
        }
    }

    /**
     * Partially updates a product with the provided field values.
     * Only administrators can patch products.
     *
     * @param productId UUID of the product to patch
     * @param patchData Map containing field names and their new values
     * @param token JWT authentication token of the current user
     * @return ProductResponse containing the updated product's details
     * @throws ValidationException if inputs are null or invalid
     * @throws InsufficientPermissionException if user is not an admin
     * @throws NotFoundException if product not found
     * @throws DataIntegrityException if patch fails
     */
    @Transactional
    public ProductResponse patchProduct(UUID productId, Map<String, Object> patchData, JwtAuthenticationToken token) {
        // Validate inputs
        if (productId == null) {
            throw new ValidationException("Product ID cannot be null");
        }
        if (patchData == null) {
            throw new ValidationException("Patch data cannot be null");
        }
        if (patchData.isEmpty()) {
            throw new ValidationException("Patch data cannot be empty");
        }
        if (token == null) {
            throw new ValidationException("Authentication token cannot be null");
        }

        // Validate authentication and authorization - only admins can patch
        User currentUser = userService.getCurrentUser(token);
        validateAdminAccess(currentUser, "update");

        log.debug("Admin user {} patching product: {}", currentUser.getId(), productId);

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new NotFoundException("Product not found"));

        applyPatchToProduct(product, patchData);

        try {
            Product savedProduct = productRepository.save(product);
            log.info("Admin user {} successfully patched product with ID: {}", currentUser.getId(), productId);
            return mapToResponse(savedProduct);
        } catch (Exception e) {
            log.error("Failed to patch product with ID: {}", productId, e);
            throw new DataIntegrityException("Failed to patch product: " + e.getMessage(), e);
        }
    }

    /**
     * Deletes a product.
     * Only administrators can delete products.
     *
     * @param productId UUID of the product to delete
     * @param token JWT authentication token of the current user
     * @throws ValidationException if productId or token is null
     * @throws InsufficientPermissionException if user is not an admin
     * @throws NotFoundException if product not found
     * @throws DataIntegrityException if deletion fails
     */
    @Transactional
    public void deleteProduct(UUID productId, JwtAuthenticationToken token) {
        // Validate inputs
        if (productId == null) {
            throw new ValidationException("Product ID cannot be null");
        }
        if (token == null) {
            throw new ValidationException("Authentication token cannot be null");
        }

        // Validate authentication and authorization - only admins can delete
        User currentUser = userService.getCurrentUser(token);
        validateAdminAccess(currentUser, "delete");

        log.debug("Admin user {} deleting product: {}", currentUser.getId(), productId);

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new NotFoundException("Product not found"));

        try {
            productRepository.delete(product);
            log.info("Admin user {} successfully deleted product with ID: {}", currentUser.getId(), productId);
        } catch (Exception e) {
            log.error("Failed to delete product with ID: {}", productId, e);
            throw new DataIntegrityException("Failed to delete product: " + e.getMessage(), e);
        }
    }

    /**
     * Searches products by name using case-insensitive partial matching.
     *
     * @param searchTerm Term to search for in product names
     * @param token JWT authentication token of the current user
     * @return List of ProductResponse objects matching the search
     * @throws ValidationException if searchTerm is null/empty or token is null
     */
    @Transactional(readOnly = true)
    public List<ProductResponse> searchProductsByName(String searchTerm, JwtAuthenticationToken token) {
        // Validate inputs
        if (searchTerm == null || searchTerm.trim().isEmpty()) {
            throw new ValidationException("Search term cannot be null or empty");
        }
        if (token == null) {
            throw new ValidationException("Authentication token cannot be null");
        }

        // Validate authentication
        User currentUser = userService.getCurrentUser(token);
        validateReadAccess(currentUser);

        log.debug("User {} searching products by name: {}", currentUser.getId(), searchTerm);

        List<Product> products = productRepository.findByNameContainingIgnoreCase(searchTerm);
        return products.stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    /**
     * Retrieves all products in a specific category.
     *
     * @param category Category to filter by
     * @param token JWT authentication token of the current user
     * @return List of ProductResponse objects in the category
     * @throws ValidationException if category or token is null
     */
    @Transactional(readOnly = true)
    public List<ProductResponse> getProductsByCategory(String category, JwtAuthenticationToken token) {
        // Validate inputs
        if (category == null) {
            throw new ValidationException("Category cannot be null");
        }
        if (token == null) {
            throw new ValidationException("Authentication token cannot be null");
        }

        // Validate authentication
        User currentUser = userService.getCurrentUser(token);
        validateReadAccess(currentUser);

        log.debug("User {} getting products by category: {}", currentUser.getId(), category);

        List<Product> products = productRepository.findByCategory(category);
        return products.stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    /**
     * Retrieves all products from a specific brand.
     *
     * @param brand Brand to filter by
     * @param token JWT authentication token of the current user
     * @return List of ProductResponse objects from the brand
     * @throws ValidationException if brand or token is null
     */
    @Transactional(readOnly = true)
    public List<ProductResponse> getProductsByBrand(String brand, JwtAuthenticationToken token) {
        // Validate inputs
        if (brand == null) {
            throw new ValidationException("Brand cannot be null");
        }
        if (token == null) {
            throw new ValidationException("Authentication token cannot be null");
        }

        // Validate authentication
        User currentUser = userService.getCurrentUser(token);
        validateReadAccess(currentUser);

        log.debug("User {} getting products by brand: {}", currentUser.getId(), brand);

        List<Product> products = productRepository.findByBrand(brand);
        return products.stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    /**
     * Retrieves all products with pagination.
     *
     * @param pageable Pagination information
     * @param token JWT authentication token of the current user
     * @return Page of ProductResponse objects
     * @throws ValidationException if pageable or token is null
     */
    @Transactional(readOnly = true)
    public Page<ProductResponse> getAllProducts(Pageable pageable, JwtAuthenticationToken token) {
        // Validate inputs
        if (pageable == null) {
            throw new ValidationException("Pageable cannot be null");
        }
        if (token == null) {
            throw new ValidationException("Authentication token cannot be null");
        }

        // Validate authentication
        User currentUser = userService.getCurrentUser(token);
        validateReadAccess(currentUser);

        log.debug("User {} getting all products with pagination", currentUser.getId());

        Page<Product> products = productRepository.findAll(pageable);
        return products.map(this::mapToResponse);
    }

    /**
     * Retrieves products that require API retry enrichment.
     *
     * @param token JWT authentication token of the current user
     * @return List of ProductResponse objects requiring retry
     * @throws ValidationException if token is null
     */
    @Transactional(readOnly = true)
    public List<ProductResponse> getProductsRequiringRetry(JwtAuthenticationToken token) {
        if (token == null) {
            throw new ValidationException("Authentication token cannot be null");
        }

        User currentUser = userService.getCurrentUser(token);
        validateReadAccess(currentUser);

        log.debug("User {} getting products requiring retry", currentUser.getId());

        List<Product> products = productRepository.findByRequiresApiRetryTrue();
        return products.stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    /**
     * Retries API enrichment for a specific product.
     * Only administrators can retry API enrichment.
     *
     * @param productId UUID of the product to retry
     * @param token JWT authentication token of the current user
     * @return ProductResponse containing the updated product's details
     * @throws ValidationException if productId or token is null
     * @throws InsufficientPermissionException if user is not an admin
     * @throws NotFoundException if product not found
     * @throws ExternalServiceException if API enrichment fails
     */
    @Transactional
    public ProductResponse retryApiEnrichment(UUID productId, JwtAuthenticationToken token) {
        if (productId == null) {
            throw new ValidationException("Product ID cannot be null");
        }
        if (token == null) {
            throw new ValidationException("Authentication token cannot be null");
        }

        User currentUser = userService.getCurrentUser(token);
        validateAdminAccess(currentUser, "retry API enrichment for");

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new NotFoundException("Product not found"));

        if (product.getRetryAttempts() >= MAX_RETRY_ATTEMPTS) {
            throw new BusinessRuleViolationException("Maximum retry attempts exceeded for product");
        }

        try {
            ProductResponse apiData = upcApiService.fetchProductData(product.getUpc());
            enrichProductWithApiData(product, apiData);
            product.setDataSource(ProductDataSource.OPEN_FOOD_FACTS);
            product.setRequiresApiRetry(false);
            product.setRetryAttempts(0);

            Product savedProduct = productRepository.save(product);
            log.info("Successfully enriched product {} with API data", productId);
            return mapToResponse(savedProduct);
        } catch (Exception e) {
            product.setRetryAttempts(product.getRetryAttempts() + 1);
            productRepository.save(product);
            log.warn("Failed to enrich product {} with API data, attempt {}", productId, product.getRetryAttempts(), e);
            throw new ExternalServiceException("Failed to enrich product with API data: " + e.getMessage(), e);
        }
    }

    /**
     * Retrieves products eligible for retry based on retry attempts.
     *
     * @param maxRetryAttempts Maximum retry attempts to filter by
     * @param token JWT authentication token of the current user
     * @return List of ProductResponse objects eligible for retry
     * @throws ValidationException if token is null or maxRetryAttempts is invalid
     */
    @Transactional(readOnly = true)
    public List<ProductResponse> getProductsEligibleForRetry(int maxRetryAttempts, JwtAuthenticationToken token) {
        if (token == null) {
            throw new ValidationException("Authentication token cannot be null");
        }
        if (maxRetryAttempts < 0) {
            throw new ValidationException("Max retry attempts cannot be negative");
        }

        User currentUser = userService.getCurrentUser(token);
        validateReadAccess(currentUser);

        List<Product> products = productRepository.findByRequiresApiRetryTrueAndRetryAttemptsLessThan(maxRetryAttempts);
        return products.stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    /**
     * Processes a batch of products for API retry enrichment.
     * Only administrators can process batch retries.
     *
     * @param maxRetryAttempts Maximum retry attempts threshold
     * @param token JWT authentication token of the current user
     * @return Number of successfully processed products
     * @throws ValidationException if token is null or maxRetryAttempts is invalid
     * @throws InsufficientPermissionException if user is not an admin
     */
    @Transactional
    public int processBatchRetry(int maxRetryAttempts, JwtAuthenticationToken token) {
        if (token == null) {
            throw new ValidationException("Authentication token cannot be null");
        }
        if (maxRetryAttempts < 0) {
            throw new ValidationException("Max retry attempts cannot be negative");
        }

        User currentUser = userService.getCurrentUser(token);
        validateAdminAccess(currentUser, "process batch retry for");

        List<Product> products = productRepository.findByRequiresApiRetryTrueAndRetryAttemptsLessThan(maxRetryAttempts);
        int successCount = 0;

        for (Product product : products) {
            try {
                ProductResponse apiData = upcApiService.fetchProductData(product.getUpc());
                enrichProductWithApiData(product, apiData);
                product.setDataSource(ProductDataSource.OPEN_FOOD_FACTS);
                product.setRequiresApiRetry(false);
                product.setRetryAttempts(0);
                productRepository.save(product);
                successCount++;
            } catch (Exception e) {
                product.setRetryAttempts(product.getRetryAttempts() + 1);
                productRepository.save(product);
                log.warn("Failed to enrich product {} in batch retry", product.getId(), e);
            }
        }

        log.info("Batch retry processed {} out of {} products successfully", successCount, products.size());
        return successCount;
    }

    /**
     * Retrieves products by their data source.
     *
     * @param dataSource Data source to filter by
     * @param token JWT authentication token of the current user
     * @return List of ProductResponse objects from the data source
     * @throws ValidationException if dataSource or token is null
     */
    @Transactional(readOnly = true)
    public List<ProductResponse> getProductsByDataSource(ProductDataSource dataSource, JwtAuthenticationToken token) {
        if (dataSource == null) {
            throw new ValidationException("Data source cannot be null");
        }
        if (token == null) {
            throw new ValidationException("Authentication token cannot be null");
        }

        User currentUser = userService.getCurrentUser(token);
        validateReadAccess(currentUser);

        List<Product> products = productRepository.findByDataSource(dataSource);
        return products.stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    /**
     * Retrieves statistics about products in the system.
     *
     * @param token JWT authentication token of the current user
     * @return Map containing various product statistics
     * @throws ValidationException if token is null
     */
    @Transactional(readOnly = true)
    public Map<String, Long> getProductStatistics(JwtAuthenticationToken token) {
        if (token == null) {
            throw new ValidationException("Authentication token cannot be null");
        }

        User currentUser = userService.getCurrentUser(token);
        validateReadAccess(currentUser);

        Map<String, Long> stats = new HashMap<>();
        stats.put("total", productRepository.count());
        stats.put("manual", (long) productRepository.findByDataSource(ProductDataSource.MANUAL).size());
        stats.put("api", (long) productRepository.findByDataSource(ProductDataSource.OPEN_FOOD_FACTS).size());
        stats.put("requiresRetry", (long) productRepository.findByRequiresApiRetryTrue().size());

        return stats;
    }

    /**
     * Checks if a UPC is available (not already used).
     *
     * @param upc UPC code to check
     * @param token JWT authentication token of the current user
     * @return true if UPC is available, false otherwise
     * @throws ValidationException if upc or token is null/empty
     */
    @Transactional(readOnly = true)
    public boolean isUpcAvailable(String upc, JwtAuthenticationToken token) {
        if (upc == null || upc.trim().isEmpty()) {
            throw new ValidationException("UPC cannot be null or empty");
        }
        if (token == null) {
            throw new ValidationException("Authentication token cannot be null");
        }

        User currentUser = userService.getCurrentUser(token);
        validateReadAccess(currentUser);

        return productRepository.findByUpc(upc).isEmpty();
    }

    /**
     * Bulk imports multiple products.
     * Only administrators can bulk import products.
     *
     * @param productRequests List of product requests to import
     * @param token JWT authentication token of the current user
     * @return List of ProductResponse objects for successfully imported products
     * @throws ValidationException if productRequests or token is null/empty
     * @throws InsufficientPermissionException if user is not an admin
     */
    @Transactional
    public List<ProductResponse> bulkImportProducts(List<ProductRequest> productRequests, JwtAuthenticationToken token) {
        if (productRequests == null || productRequests.isEmpty()) {
            throw new ValidationException("Product requests list cannot be null or empty");
        }
        if (token == null) {
            throw new ValidationException("Authentication token cannot be null");
        }

        User currentUser = userService.getCurrentUser(token);
        validateAdminAccess(currentUser, "bulk import");

        List<ProductResponse> responses = new ArrayList<>();

        for (ProductRequest request : productRequests) {
            try {
                ProductResponse response = createProduct(request, token);
                responses.add(response);
            } catch (ConflictException e) {
                log.warn("Skipping duplicate product with UPC: {}", request.getUpc());
            } catch (Exception e) {
                log.error("Failed to import product with UPC: {}", request.getUpc(), e);
            }
        }

        log.info("Bulk import completed: {} out of {} products imported", responses.size(), productRequests.size());
        return responses;
    }

    /**
     * Bulk deletes multiple products by their IDs.
     * Only administrators can bulk delete products.
     *
     * @param productIds List of product IDs to delete
     * @param token JWT authentication token of the current user
     * @return Number of successfully deleted products
     * @throws ValidationException if productIds or token is null/empty
     * @throws InsufficientPermissionException if user is not an admin
     */
    @Transactional
    public int bulkDeleteProducts(List<UUID> productIds, JwtAuthenticationToken token) {
        if (productIds == null || productIds.isEmpty()) {
            throw new ValidationException("Product IDs list cannot be null or empty");
        }
        if (token == null) {
            throw new ValidationException("Authentication token cannot be null");
        }

        User currentUser = userService.getCurrentUser(token);
        validateAdminAccess(currentUser, "bulk delete");

        int deleteCount = 0;

        for (UUID productId : productIds) {
            try {
                Optional<Product> productOpt = productRepository.findById(productId);
                if (productOpt.isPresent()) {
                    productRepository.delete(productOpt.get());
                    deleteCount++;
                }
            } catch (Exception e) {
                log.error("Failed to delete product with ID: {}", productId, e);
            }
        }

        log.info("Bulk delete completed: {} out of {} products deleted", deleteCount, productIds.size());
        return deleteCount;
    }

    /**
     * Validates UPC format.
     *
     * @param upc UPC code to validate
     * @return true if UPC format is valid, false otherwise
     */
    public boolean isValidUpc(String upc) {
        if (upc == null || upc.trim().isEmpty()) {
            return false;
        }

        // UPC codes should be 12 or 13 digits
        String trimmedUpc = upc.trim();
        return trimmedUpc.matches("^\\d{12,13}$");
    }

    // Private helper methods

    /**
     * Creates a Product entity from a ProductRequest DTO.
     */
    private Product createProductFromRequest(ProductRequest request) {
        Product product = new Product();
        product.setUpc(request.getUpc());
        product.setName(request.getName());
        product.setBrand(request.getBrand());
        product.setCategory(request.getCategory());
        product.setDefaultExpirationDays(request.getDefaultExpirationDays());
        product.setCreatedAt(LocalDateTime.now());
        product.setUpdatedAt(LocalDateTime.now());
        return product;
    }

    /**
     * Updates a Product entity from a ProductUpdateRequest DTO.
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
     */
    private void applyPatchToProduct(Product product, Map<String, Object> patchData) {
        patchData.forEach((key, value) -> {
            switch (key) {
                case "name":
                    if (value instanceof String) {
                        product.setName((String) value);
                    }
                    break;
                case "brand":
                    if (value instanceof String) {
                        product.setBrand((String) value);
                    }
                    break;
                case "category":
                    if (value instanceof String) {
                        product.setCategory((String) value);
                    }
                    break;
                case "defaultExpirationDays":
                    if (value instanceof Integer) {
                        product.setDefaultExpirationDays((Integer) value);
                    }
                    break;
                default:
                    log.debug("Ignoring unknown field in patch: {}", key);
            }
        });
        product.setUpdatedAt(LocalDateTime.now());
    }

    /**
     * Enriches a Product entity with data from API response.
     */
    private void enrichProductWithApiData(Product product, ProductResponse apiData) {
        if (apiData == null) {
            return;
        }

        if (apiData.getName() != null && !apiData.getName().isEmpty()) {
            product.setName(apiData.getName());
        }
        if (apiData.getBrand() != null && !apiData.getBrand().isEmpty()) {
            product.setBrand(apiData.getBrand());
        }
        if (apiData.getCategory() != null && !apiData.getCategory().isEmpty()) {
            product.setCategory(apiData.getCategory());
        }
    }

    /**
     * Maps a Product entity to a ProductResponse DTO.
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
}