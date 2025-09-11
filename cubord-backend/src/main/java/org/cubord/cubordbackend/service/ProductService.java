package org.cubord.cubordbackend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cubord.cubordbackend.domain.Product;
import org.cubord.cubordbackend.domain.ProductDataSource;
import org.cubord.cubordbackend.domain.User;
import org.cubord.cubordbackend.dto.ProductRequest;
import org.cubord.cubordbackend.dto.ProductResponse;
import org.cubord.cubordbackend.dto.ProductUpdateRequest;
import org.cubord.cubordbackend.exception.ConflictException;
import org.cubord.cubordbackend.exception.NotFoundException;
import org.cubord.cubordbackend.repository.ProductRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Add these imports
import org.springframework.security.access.AccessDeniedException;
import org.cubord.cubordbackend.domain.UserRole;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductService {
    
    private final ProductRepository productRepository;
    private final UpcApiService upcApiService;
    private final UserService userService;
    
    private static final int MAX_RETRY_ATTEMPTS = 5;

    // Update the authorization methods
    private void validateAdminAccess(User currentUser, String operation) {
        if (currentUser.getRole() != UserRole.ADMIN) {
            log.warn("User {} attempted {} operation without admin privileges", 
                     currentUser.getId(), operation);
            throw new AccessDeniedException("Only administrators can " + operation + " products");
        }
        log.debug("Admin user {} authorized for {} operation", currentUser.getId(), operation);
    }

    private void validateCreateAccess(User currentUser) {
        // All authenticated users can create products
        log.debug("User {} authorized for create operation", currentUser.getId());
    }

    private void validateReadAccess(User currentUser) {
        // All authenticated users can read products
        log.debug("User {} authorized for read operation", currentUser.getId());
    }

    // Update the createProduct method to allow all users
    @Transactional
    public ProductResponse createProduct(ProductRequest request, JwtAuthenticationToken token) {
        if (request == null || token == null) {
            throw new IllegalArgumentException("Product request and token cannot be null");
        }
        
        // Validate authentication - allow all authenticated users to create
        User currentUser = userService.getCurrentUser(token);
        validateCreateAccess(currentUser);
        
        log.debug("User {} creating product with UPC: {}", currentUser.getId(), request.getUpc());
        
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
        } catch (Exception e) {
            log.warn("Failed to fetch API data for UPC: {}, creating manual entry", request.getUpc());
            product.setDataSource(ProductDataSource.MANUAL);
            product.setRequiresApiRetry(true);
            product.setRetryAttempts(0);
        }
        
        Product savedProduct = productRepository.save(product);
        return mapToResponse(savedProduct);
    }
    
    /**
     * Gets a product by ID.
     */
    @Transactional(readOnly = true)
    public ProductResponse getProductById(UUID productId, JwtAuthenticationToken token) {
        if (productId == null || token == null) {
            throw new IllegalArgumentException("Product ID and token cannot be null");
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
     * Gets a product by UPC.
     */
    @Transactional(readOnly = true)
    public ProductResponse getProductByUpc(String upc, JwtAuthenticationToken token) {
        if (upc == null || token == null) {
            throw new IllegalArgumentException("UPC and token cannot be null");
        }
        
        // Validate authentication
        User currentUser = userService.getCurrentUser(token);
        log.debug("User {} getting product by UPC: {}", currentUser.getId(), upc);
        
        Product product = productRepository.findByUpc(upc)
                .orElseThrow(() -> new NotFoundException("Product not found"));
        
        return mapToResponse(product);
    }
    
    /**
     * Updates a product.
     */
    
    // Keep updateProduct method restricted to admins
    @Transactional
    public ProductResponse updateProduct(UUID productId, ProductUpdateRequest request, JwtAuthenticationToken token) {
        if (productId == null || request == null || token == null) {
            throw new IllegalArgumentException("Product ID, update request, and token cannot be null");
        }
        
        // Validate authentication and authorization - only admins can update
        User currentUser = userService.getCurrentUser(token);
        validateAdminAccess(currentUser, "update");
        
        log.debug("Admin user {} updating product: {}", currentUser.getId(), productId);
        
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new NotFoundException("Product not found"));
        
        updateProductFromRequest(product, request);
        Product savedProduct = productRepository.save(product);
        
        return mapToResponse(savedProduct);
    }
    
    /**
     * Patches a product with partial updates.
     */
    
    // Keep patchProduct method restricted to admins
    @Transactional
    public ProductResponse patchProduct(UUID productId, Map<String, Object> patchData, JwtAuthenticationToken token) {
        if (productId == null || patchData == null || token == null) {
            throw new IllegalArgumentException("Product ID, patch data, and token cannot be null");
        }
        
        if (patchData.isEmpty()) {
            throw new IllegalArgumentException("Patch data cannot be empty");
        }
        
        // Validate authentication and authorization - only admins can patch
        User currentUser = userService.getCurrentUser(token);
        validateAdminAccess(currentUser, "update");
        
        log.debug("Admin user {} patching product: {}", currentUser.getId(), productId);
        
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new NotFoundException("Product not found"));
        
        applyPatchToProduct(product, patchData);
        Product savedProduct = productRepository.save(product);
        
        return mapToResponse(savedProduct);
    }
    
    /**
     * Deletes a product.
     */
    
    // Keep deleteProduct method restricted to admins
    @Transactional
    public void deleteProduct(UUID productId, JwtAuthenticationToken token) {
        if (productId == null || token == null) {
            throw new IllegalArgumentException("Product ID and token cannot be null");
        }
        
        // Validate authentication and authorization - only admins can delete
        User currentUser = userService.getCurrentUser(token);
        validateAdminAccess(currentUser, "delete");
        
        log.debug("Admin user {} deleting product: {}", currentUser.getId(), productId);
        
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new NotFoundException("Product not found"));
        
        productRepository.delete(product);
    }
    
    /**
     * Searches products by name.
     */
    @Transactional(readOnly = true)
    public List<ProductResponse> searchProductsByName(String searchTerm, JwtAuthenticationToken token) {
        if (searchTerm == null || searchTerm.trim().isEmpty() || token == null) {
            throw new IllegalArgumentException("Search term and token cannot be empty");
        }
        
        // Validate authentication
        User currentUser = userService.getCurrentUser(token);
        log.debug("User {} searching products by name: {}", currentUser.getId(), searchTerm);
        
        List<Product> products = productRepository.findByNameContainingIgnoreCase(searchTerm);
        return products.stream().map(this::mapToResponse).collect(Collectors.toList());
    }
    
    /**
     * Gets products by category.
     */
    @Transactional(readOnly = true)
    public List<ProductResponse> getProductsByCategory(String category, JwtAuthenticationToken token) {
        if (category == null || token == null) {
            throw new IllegalArgumentException("Category and token cannot be null");
        }
        
        // Validate authentication
        User currentUser = userService.getCurrentUser(token);
        log.debug("User {} getting products by category: {}", currentUser.getId(), category);
        
        List<Product> products = productRepository.findByCategory(category);
        return products.stream().map(this::mapToResponse).collect(Collectors.toList());
    }
    
    /**
     * Gets products by brand.
     */
    @Transactional(readOnly = true)
    public List<ProductResponse> getProductsByBrand(String brand, JwtAuthenticationToken token) {
        if (brand == null || token == null) {
            throw new IllegalArgumentException("Brand and token cannot be null");
        }
        
        // Validate authentication
        User currentUser = userService.getCurrentUser(token);
        log.debug("User {} getting products by brand: {}", currentUser.getId(), brand);
        
        List<Product> products = productRepository.findByBrand(brand);
        return products.stream().map(this::mapToResponse).collect(Collectors.toList());
    }
    
    /**
     * Gets all products with pagination.
     */
    @Transactional(readOnly = true)
    public Page<ProductResponse> getAllProducts(Pageable pageable, JwtAuthenticationToken token) {
        if (pageable == null || token == null) {
            throw new IllegalArgumentException("Pageable and token cannot be null");
        }
        
        // Validate authentication
        User currentUser = userService.getCurrentUser(token);
        log.debug("User {} getting all products with pagination", currentUser.getId());
        
        Page<Product> products = productRepository.findAll(pageable);
        return products.map(this::mapToResponse);
    }
    
    /**
     * Gets products requiring API retry.
     */
    @Transactional(readOnly = true)
    public List<ProductResponse> getProductsRequiringRetry(JwtAuthenticationToken token) {
        if (token == null) {
            throw new IllegalArgumentException("Token cannot be null");
        }
        
        // Validate authentication
        User currentUser = userService.getCurrentUser(token);
        log.debug("User {} getting products requiring retry", currentUser.getId());
        
        List<Product> products = productRepository.findByRequiresApiRetryTrue();
        return products.stream().map(this::mapToResponse).collect(Collectors.toList());
    }
    
    /**
     * Retries API enrichment for a product.
     */
    @Transactional
    public ProductResponse retryApiEnrichment(UUID productId, JwtAuthenticationToken token) {
        if (productId == null || token == null) {
            throw new IllegalArgumentException("Product ID and token cannot be null");
        }
        
        // Validate authentication
        User currentUser = userService.getCurrentUser(token);
        log.debug("User {} retrying API enrichment for product: {}", currentUser.getId(), productId);
        
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new NotFoundException("Product not found"));
        
        try {
            ProductResponse apiData = upcApiService.fetchProductData(product.getUpc());
            enrichProductWithApiData(product, apiData);
            product.setDataSource(ProductDataSource.HYBRID);
            product.setRequiresApiRetry(false);
        } catch (Exception e) {
            log.warn("API retry failed for product {}", productId);
            product.setRetryAttempts(product.getRetryAttempts() + 1);
            product.setLastRetryAttempt(LocalDateTime.now());
            
            if (product.getRetryAttempts() >= MAX_RETRY_ATTEMPTS) {
                product.setRequiresApiRetry(false);
            }
        }
        
        Product savedProduct = productRepository.save(product);
        return mapToResponse(savedProduct);
    }
    
    /**
     * Gets products eligible for retry.
     */
    @Transactional(readOnly = true)
    public List<ProductResponse> getProductsEligibleForRetry(int maxRetryAttempts, JwtAuthenticationToken token) {
        if (token == null) {
            throw new IllegalArgumentException("Token cannot be null");
        }
        
        // Validate authentication
        User currentUser = userService.getCurrentUser(token);
        log.debug("User {} getting products eligible for retry", currentUser.getId());
        
        List<Product> products = productRepository.findByRequiresApiRetryTrueAndRetryAttemptsLessThan(maxRetryAttempts);
        return products.stream().map(this::mapToResponse).collect(Collectors.toList());
    }
    
    /**
     * Processes a batch of products for retry.
     */
    @Transactional
    public int processBatchRetry(int maxRetryAttempts, JwtAuthenticationToken token) {
        if (token == null) {
            throw new IllegalArgumentException("Token cannot be null");
        }
        
        // Validate authentication
        User currentUser = userService.getCurrentUser(token);
        log.debug("User {} processing batch retry", currentUser.getId());
        
        List<Product> products = productRepository.findByRequiresApiRetryTrueAndRetryAttemptsLessThan(maxRetryAttempts);
        
        for (Product product : products) {
            try {
                ProductResponse apiData = upcApiService.fetchProductData(product.getUpc());
                enrichProductWithApiData(product, apiData);
                product.setDataSource(ProductDataSource.HYBRID);
                product.setRequiresApiRetry(false);
            } catch (Exception e) {
                product.setRetryAttempts(product.getRetryAttempts() + 1);
                product.setLastRetryAttempt(LocalDateTime.now());
            }
        }
        
        productRepository.saveAll(products);
        return products.size();
    }
    
    /**
     * Gets products by data source.
     */
    @Transactional(readOnly = true)
    public List<ProductResponse> getProductsByDataSource(ProductDataSource dataSource, JwtAuthenticationToken token) {
        if (dataSource == null || token == null) {
            throw new IllegalArgumentException("Data source and token cannot be null");
        }
        
        // Validate authentication
        User currentUser = userService.getCurrentUser(token);
        log.debug("User {} getting products by data source: {}", currentUser.getId(), dataSource);
        
        List<Product> products = productRepository.findByDataSource(dataSource);
        return products.stream().map(this::mapToResponse).collect(Collectors.toList());
    }
    
    /**
     * Gets product statistics.
     */
    @Transactional(readOnly = true)
    public Map<String, Long> getProductStatistics(JwtAuthenticationToken token) {
        if (token == null) {
            throw new IllegalArgumentException("Token cannot be null");
        }
        
        // Validate authentication
        User currentUser = userService.getCurrentUser(token);
        log.debug("User {} getting product statistics", currentUser.getId());
        
        return Map.of(
                "total", productRepository.count(),
                "apiSource", productRepository.countByDataSource(ProductDataSource.OPEN_FOOD_FACTS),
                "manualSource", productRepository.countByDataSource(ProductDataSource.MANUAL),
                "hybridSource", productRepository.countByDataSource(ProductDataSource.HYBRID),
                "pendingRetry", productRepository.countByRequiresApiRetryTrue()
        );
    }
    
    /**
     * Checks if UPC is available.
     */
    @Transactional(readOnly = true)
    public boolean isUpcAvailable(String upc, JwtAuthenticationToken token) {
        if (upc == null || token == null) {
            throw new IllegalArgumentException("UPC and token cannot be null");
        }
        
        // Validate authentication
        User currentUser = userService.getCurrentUser(token);
        log.debug("User {} checking UPC availability: {}", currentUser.getId(), upc);
        
        return productRepository.findByUpc(upc).isEmpty();
    }
    
    /**
     * Bulk imports products.
     */
    
    // Keep bulk operations restricted to admins
    @Transactional
    public List<ProductResponse> bulkImportProducts(List<ProductRequest> productRequests, JwtAuthenticationToken token) {
        if (productRequests == null || token == null) {
            throw new IllegalArgumentException("Product requests and token cannot be null");
        }
        
        // Validate authentication and authorization - only admins can bulk import
        User currentUser = userService.getCurrentUser(token);
        validateAdminAccess(currentUser, "bulk import");
        
        log.debug("Admin user {} bulk importing {} products", currentUser.getId(), productRequests.size());
        
        List<Product> productsToSave = productRequests.stream()
                .filter(request -> productRepository.findByUpc(request.getUpc()).isEmpty())
                .map(this::createProductFromRequest)
                .collect(Collectors.toList());
        
        if (productsToSave.isEmpty()) {
            return List.of();
        }
        
        List<Product> savedProducts = productRepository.saveAll(productsToSave);
        return savedProducts.stream().map(this::mapToResponse).collect(Collectors.toList());
    }
    
    /**
     * Bulk deletes products by IDs.
     */
    
    @Transactional
    public int bulkDeleteProducts(List<UUID> productIds, JwtAuthenticationToken token) {
        if (productIds == null || token == null) {
            throw new IllegalArgumentException("Product IDs and token cannot be null");
        }
        
        // Validate authentication and authorization - only admins can bulk delete
        User currentUser = userService.getCurrentUser(token);
        validateAdminAccess(currentUser, "bulk delete");
        
        log.debug("Admin user {} bulk deleting {} products", currentUser.getId(), productIds.size());
        
        List<Product> products = productRepository.findAllById(productIds);
        productRepository.deleteAll(products);
        return products.size();
    }
    
    /**
     * Validates UPC format.
     */
    public boolean isValidUpc(String upc) {
        if (upc == null) {
            return false;
        }
        // Basic UPC validation - 12 or 13 digits
        return upc.matches("\\d{12,13}");
    }
    
    // Private helper methods
    
    private Product createProductFromRequest(ProductRequest request) {
        return Product.builder()
                .id(UUID.randomUUID())
                .upc(request.getUpc())
                .name(request.getName())
                .brand(request.getBrand())
                .category(request.getCategory())
                .defaultExpirationDays(request.getDefaultExpirationDays())
                .requiresApiRetry(false)
                .retryAttempts(0)
                .build();
    }
    
    private void updateProductFromRequest(Product product, ProductUpdateRequest request) {
        product.setName(request.getName());
        product.setBrand(request.getBrand());
        product.setCategory(request.getCategory());
        product.setDefaultExpirationDays(request.getDefaultExpirationDays());
    }
    
    private void applyPatchToProduct(Product product, Map<String, Object> patchData) {
        for (Map.Entry<String, Object> entry : patchData.entrySet()) {
            String field = entry.getKey();
            Object value = entry.getValue();
            
            switch (field) {
                case "name" -> product.setName((String) value);
                case "brand" -> product.setBrand((String) value);
                case "category" -> product.setCategory((String) value);
                case "defaultExpirationDays" -> product.setDefaultExpirationDays((Integer) value);
                default -> throw new IllegalArgumentException("Invalid field: " + field);
            }
        }
    }
    
    private void enrichProductWithApiData(Product product, ProductResponse apiData) {
        if (apiData.getName() != null) {
            product.setName(apiData.getName());
        }
        if (apiData.getBrand() != null) {
            product.setBrand(apiData.getBrand());
        }
        if (apiData.getCategory() != null) {
            product.setCategory(apiData.getCategory());
        }
    }
    
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
                .lastRetryAttempt(product.getLastRetryAttempt())
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .build();
    }
}