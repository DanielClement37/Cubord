package org.cubord.cubordbackend.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cubord.cubordbackend.dto.product.ProductResponse;
import org.cubord.cubordbackend.exception.ExternalServiceException;
import org.cubord.cubordbackend.exception.NotFoundException;
import org.cubord.cubordbackend.exception.UrlValidationException;
import org.cubord.cubordbackend.exception.ValidationException;
import org.cubord.cubordbackend.security.SecurityService;
import org.cubord.cubordbackend.util.UrlValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

/**
 * Service class for fetching product data from external UPC/EAN databases.
 *
 * <p>This service follows the modernized security architecture where:</p>
 * <ul>
 *   <li>Authentication is handled by Spring Security filters</li>
 *   <li>Authorization is declarative via @PreAuthorize annotations</li>
 *   <li>SecurityService provides business-level security context access</li>
 *   <li>No manual token validation or permission checks in business logic</li>
 *   <li><strong>SSRF Protection:</strong> All external URLs are validated before use</li>
 * </ul>
 *
 * <h2>Authorization Rules</h2>
 * <ul>
 *   <li><strong>Fetch Product Data:</strong> All authenticated users can fetch product data from external APIs</li>
 * </ul>
 *
 * <h2>External API Integration</h2>
 * <p>Currently integrates with Open Food Facts API for product information retrieval.
 * The service handles API failures gracefully and provides detailed error information.</p>
 *
 * <h2>SSRF Protection</h2>
 * <p>This service implements comprehensive Server-Side Request Forgery (SSRF) protection:</p>
 * <ul>
 *   <li>All URLs are validated against a whitelist of allowed domains</li>
 *   <li>Access to internal/private network addresses is blocked</li>
 *   <li>Only HTTP and HTTPS protocols are permitted</li>
 *   <li>DNS rebinding attacks are prevented through IP validation</li>
 * </ul>
 *
 * @see SecurityService
 * @see ProductService
 * @see UrlValidator
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UpcApiService {

    private final RestTemplate restTemplate;
    private final SecurityService securityService;

    @Value("${app.openfoodfacts.api-url:https://world.openfoodfacts.org/api/v2}")
    private String apiUrl;

    @Value("${app.openfoodfacts.timeout:5000}")
    private int timeout;

    @Value("${app.openfoodfacts.use-staging:true}")
    private boolean useStaging;

    @Value("${app.openfoodfacts.user-agent:Cubord/1.0 (cubord@example.com)}")
    private String userAgent;

    // ==================== Configuration Validation ====================

    /**
     * Validates service configuration on startup.
     *
     * <p>This method validates:</p>
     * <ul>
     *   <li>API URL is configured</li>
     *   <li>API URL is valid and safe (SSRF protection)</li>
     *   <li>Timeout is positive</li>
     * </ul>
     *
     * @throws IllegalStateException  if the configuration is invalid
     * @throws UrlValidationException if the API URL fails SSRF validation
     */
    @PostConstruct
    private void validateConfiguration() {
        if (apiUrl == null || apiUrl.trim().isEmpty()) {
            throw new IllegalStateException("Open Food Facts API URL must be configured");
        }
        if (timeout <= 0) {
            throw new IllegalStateException("API timeout must be positive");
        }

        // Validate the base API URL for SSRF protection
        try {
            // Extract just the base URL (scheme + host + port) for validation
            String baseUrl = extractBaseUrl(apiUrl);
            UrlValidator.isValidExternalUrl(baseUrl);
            log.info("UpcApiService initialized with validated API URL: {} (timeout: {}ms)",
                    apiUrl, timeout);
        } catch (UrlValidationException e) {
            log.error("SECURITY: Configured API URL failed SSRF validation: {}", apiUrl, e);
            throw new IllegalStateException(
                    "Configured API URL failed security validation: " + e.getMessage(), e);
        }
    }

    // ==================== Query Operations ====================

    /**
     * Fetches product data from the external UPC database.
     *
     * <p>Authorization: All authenticated users can fetch product data.
     * This is a read-only operation against an external API.</p>
     *
     * <p>The method attempts to retrieve product information from Open Food Facts.
     * If the product is not found or the API fails, appropriate exceptions are thrown.</p>
     *
     * <p><strong>SSRF Protection:</strong> Multi-layered approach including input validation,
     * URL encoding, and full URL security checks before making external requests.</p>
     *
     * @param upc UPC/EAN code of the product to fetch
     * @return ProductResponse containing the product data from the external API
     * @throws ValidationException      if the UPC format is invalid
     * @throws NotFoundException        if the product is not found in the external database
     * @throws ExternalServiceException if the external API call fails
     * @throws UrlValidationException   if URL validation fails (SSRF protection)
     */
    @PreAuthorize("isAuthenticated()")
    public ProductResponse fetchProductData(String upc) {
        // LAYER 1: Early input validation - returns sanitized UPC
        String sanitizedUpc = validateAndSanitizeUpc(upc);

        UUID currentUserId = securityService.getCurrentUserId();
        log.debug("User {} fetching product data for UPC: {}", currentUserId, sanitizedUpc);

        try {
            // LAYER 2: Build URL using ONLY the pre-validated base URL and sanitized UPC
            // The sanitizedUpc is guaranteed to be 8-14 digits only (no special chars)
            URI uri = buildSafeApiUri(sanitizedUpc);

            UrlValidator.isValidExternalUrl(uri.toString());
            log.trace("Constructed safe API URL: {}", uri);

            // Safe to make an external request - URL is constructed from trusted base + validated numeric input
            String response = restTemplate.getForObject(uri.toString(), String.class);

            if (response == null || response.trim().isEmpty()) {
                log.warn("Empty response from external API for UPC: {}", sanitizedUpc);
                throw new ExternalServiceException("Open Food Facts API", "Empty response received");
            }

            ProductResponse productResponse = parseApiResponse(response, sanitizedUpc);

            log.info("User {} successfully fetched product data for UPC: {}", currentUserId, sanitizedUpc);
            return productResponse;

        } catch (UrlValidationException e) {
            log.error("SECURITY: URL validation failed when fetching UPC: {}", sanitizedUpc, e);
            throw new ExternalServiceException("Open Food Facts API",
                    "Security validation failed: " + e.getMessage(), e);
        } catch (RestClientException e) {
            log.error("External API call failed for UPC: {}", sanitizedUpc, e);
            throw new ExternalServiceException("Open Food Facts API",
                    "Failed to fetch product data: " + e.getMessage(), e);
        }
    }

    // ==================== Helper Methods ====================

    private URI buildSafeApiUri(String sanitizedUpc) {
        return UriComponentsBuilder
                .fromUriString(apiUrl)               // trusted config value (validated at startup)
                .path("/product/{upc}.json")         // fixed template
                .buildAndExpand(sanitizedUpc)        // safe substitution (and encoding if needed)
                .toUri();
    }

    /**
     * Validates and sanitizes UPC input to prevent injection attacks.
     *
     * <p><strong>Security Rationale:</strong></p>
     * <ul>
     *   <li>UPC-A: 12 digits</li>
     *   <li>EAN-13: 13 digits</li>
     *   <li>EAN-8: 8 digits</li>
     *   <li>UPC-E: 6-8 digits</li>
     *   <li>We allow 8-14 digits to cover all standard formats</li>
     * </ul>
     *
     * <p>This strict validation prevents URL injection attacks by ensuring
     * only numeric characters can be used in URL construction.</p>
     *
     * @param upc The raw UPC input from a user
     * @return Sanitized UPC (guaranteed to be 8-14 digits)
     * @throws ValidationException if a UPC format is invalid
     */
    private String validateAndSanitizeUpc(String upc) {
        if (upc == null || upc.trim().isEmpty()) {
            throw new ValidationException("UPC cannot be null or empty");
        }

        String trimmed = upc.trim();

        // UPC/EAN codes are numeric only - no letters, no special characters
        // This is the strictest validation that still supports real-world UPC formats
        if (!trimmed.matches("^[0-9]{8,14}$")) {
            log.warn("SECURITY: Invalid UPC format attempted: {}",
                    trimmed.replaceAll("[0-9]", "X")); // Log safely
            throw new ValidationException(
                    "Invalid UPC format. Must be 8-14 digits (UPC-A, EAN-8, EAN-13, or UPC-E)");
        }

        return trimmed;
    }

    /**
     * Extracts the base URL (scheme + host + port) from a full URL.
     * Used for initial configuration validation.
     *
     * @param fullUrl The full URL
     * @return Base URL for validation
     */
    private String extractBaseUrl(String fullUrl) {
        try {
            java.net.URI uri = new java.net.URI(fullUrl);
            int port = uri.getPort();
            String baseUrl = uri.getScheme() + "://" + uri.getHost();
            if (port != -1 && port != 80 && port != 443) {
                baseUrl += ":" + port;
            }
            return baseUrl;
        } catch (Exception e) {
            log.error("Failed to extract base URL from: {}", fullUrl, e);
            return fullUrl;
        }
    }

    /**
     * Parses the JSON response from the external API.
     *
     * <p>This is a simplified implementation. In production, you would use
     * a JSON parser (like Jackson) to properly deserialize the response.</p>
     *
     * @param response JSON response from the API
     * @param upc      UPC being fetched
     * @return ProductResponse object
     * @throws NotFoundException        if product not found
     * @throws ExternalServiceException if parsing fails
     */
    private ProductResponse parseApiResponse(String response, String upc) {
        try {
            log.debug("=== Starting JSON parsing for UPC: {} ===", upc);
            log.debug("Full response: {}", response);

            // Check if a product was found (Open Food Facts returns status field)
            if (response.contains("\"status\":0") || response.contains("\"status\": 0")) {
                log.debug("Product not found in external database for UPC: {}", upc);
                throw new NotFoundException("Product not found in external database for UPC: " + upc);
            }

            // Extract the product object from the response
            String productJson = extractProductObject(response);
            log.debug("Extracted product JSON: {}", productJson);

            if (productJson == null) {
                log.warn("No product data found in API response for UPC: {}", upc);
                throw new ExternalServiceException("Open Food Facts API", "No product data in response");
            }

            // Parse the product data and build ProductResponse
            ProductResponse.ProductResponseBuilder builder = ProductResponse.builder();
            builder.upc(upc);

            // Extract product name
            String productName = extractJsonField(productJson, "product_name");
            log.debug("Extracted product_name: '{}'", productName);
            if (productName != null && !productName.isEmpty()) {
                builder.name(productName);
                log.debug("Set name to: '{}'", productName);
            } else {
                log.debug("Product name is null or empty");
            }

            // Extract brand
            String brand = extractJsonField(productJson, "brands");
            log.debug("Extracted brands: '{}'", brand);
            if (brand != null && !brand.isEmpty()) {
                builder.brand(brand);
                log.debug("Set brand to: '{}'", brand);
            } else {
                log.debug("Brand is null or empty");
            }

            // Extract category
            String category = extractJsonField(productJson, "categories");
            log.debug("Extracted categories: '{}'", category);
            if (category != null && !category.isEmpty()) {
                // Get the first category if comma-separated
                int commaIndex = category.indexOf(',');
                if (commaIndex > 0) {
                    category = category.substring(0, commaIndex).trim();
                }
                builder.category(category);
                log.debug("Set category to: '{}'", category);
            } else {
                log.debug("Category is null or empty");
            }

            ProductResponse productResponse = builder.build();
            log.debug("Built ProductResponse - name: '{}', brand: '{}', category: '{}'",
                    productResponse.getName(), productResponse.getBrand(), productResponse.getCategory());

            // Validate that we got at least some useful data
            // Accept if we have either name OR brand
            boolean hasName = productResponse.getName() != null && !productResponse.getName().isEmpty();
            boolean hasBrand = productResponse.getBrand() != null && !productResponse.getBrand().isEmpty();

            log.debug("Validation check - hasName: {}, hasBrand: {}", hasName, hasBrand);

            if (!hasName && !hasBrand) {
                log.warn("Insufficient product data returned from API for UPC: {} (name: '{}', brand: '{}')",
                        upc, productResponse.getName(), productResponse.getBrand());
                throw new ExternalServiceException("Open Food Facts API", "Insufficient product data returned");
            }

            log.debug("=== Successfully parsed product for UPC: {} ===", upc);
            return productResponse;

        } catch (NotFoundException e) {
            throw e; // Re-throw NotFoundException as-is
        } catch (ExternalServiceException e) {
            throw e; // Re-throw ExternalServiceException as-is
        } catch (Exception e) {
            log.error("Failed to parse API response for UPC: {}", upc, e);
            throw new ExternalServiceException("Open Food Facts API", "Failed to parse response", e);
        }
    }

    /**
     * Extracts the product object from the JSON response.
     *
     * <p>Open Food Facts returns data in format: {"product": {...}, "status": 1}
     * This method extracts just the product portion.</p>
     *
     * @param json Full JSON response
     * @return Product JSON object content, or null if not found
     */
    private String extractProductObject(String json) {
        try {
            int productStart = json.indexOf("\"product\":");
            if (productStart == -1) {
                return null;
            }

            // Find the opening brace of the product object
            int braceStart = json.indexOf('{', productStart);
            if (braceStart == -1) {
                return null;
            }

            // Count braces to find the matching closing brace
            int braceCount = 1;
            int i = braceStart + 1;
            while (i < json.length() && braceCount > 0) {
                char c = json.charAt(i);
                if (c == '{') {
                    braceCount++;
                } else if (c == '}') {
                    braceCount--;
                }
                i++;
            }

            if (braceCount == 0) {
                return json.substring(braceStart, i);
            }

            return null;
        } catch (Exception e) {
            log.trace("Failed to extract product object from JSON", e);
            return null;
        }
    }

    /**
     * Extracts a field value from a JSON string.
     *
     * <p>This is a simplified string-based extraction. In production,
     * use a proper JSON library like Jackson ObjectMapper.</p>
     *
     * @param json      JSON string
     * @param fieldName Field name to extract
     * @return Field value or null if not found
     */
    private String extractJsonField(String json, String fieldName) {
        try {
            String searchPattern = "\"" + fieldName + "\":\"";
            int startIndex = json.indexOf(searchPattern);
            if (startIndex == -1) {
                return null;
            }

            startIndex += searchPattern.length();
            int endIndex = json.indexOf("\"", startIndex);
            if (endIndex == -1) {
                return null;
            }

            return json.substring(startIndex, endIndex);
        } catch (Exception e) {
            log.trace("Failed to extract field '{}' from JSON", fieldName, e);
            return null;
        }
    }


    // ==================== Deprecated Methods ====================

    /**
     * Fetches product data from the external UPC database using JWT token.
     *
     * @param upc   UPC/EAN code of the product to fetch
     * @param token JWT authentication token (ignored in favor of SecurityContext)
     * @return ProductResponse containing the product data from the external API
     * @throws ValidationException      if the UPC is null or empty
     * @throws NotFoundException        if the product is not found in the external database
     * @throws ExternalServiceException if the external API call fails
     * @deprecated Use {@link #fetchProductData(String)} instead.
     * This method is maintained for backward compatibility with services
     * that haven't been migrated to the new security architecture.
     * Token-based authentication is now handled by Spring Security filters,
     * and authorization is managed through @PreAuthorize annotations.
     */
    @Deprecated(since = "2.0", forRemoval = true)
    @PreAuthorize("isAuthenticated()")
    public ProductResponse fetchProductData(String upc, org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken token) {
        log.warn("DEPRECATED: fetchProductData(String, JwtAuthenticationToken) called. " +
                "Migrate to fetchProductData(String) for improved security architecture. " +
                "The token parameter is ignored - using SecurityContext instead.");

        // Delegate to the modern method which uses SecurityService
        return fetchProductData(upc);
    }
}