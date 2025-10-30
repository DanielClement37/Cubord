package org.cubord.cubordbackend.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cubord.cubordbackend.domain.ProductDataSource;
import org.cubord.cubordbackend.dto.product.ProductResponse;
import org.cubord.cubordbackend.exception.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.MalformedURLException;
import java.net.URI;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class UpcApiService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.openfoodfacts.base-url:https://world.openfoodfacts.org}")
    private String baseUrl;

    @Value("${app.openfoodfacts.staging-url:https://world.openfoodfacts.net}")
    private String stagingUrl;

    @Value("${app.openfoodfacts.use-staging:true}")
    private boolean useStaging;

    @Value("${app.openfoodfacts.user-agent:Cubord/1.0 (cubord@example.com)}")
    private String userAgent;

    @Value("${app.openfoodfacts.timeout:5000}")
    private int timeoutMs;

    /**
     * Fetches product data from Open Food Facts API.
     *
     * @param upc The UPC/barcode to lookup
     * @return ProductResponse with API data
     * @throws ValidationException         if UPC is null or empty
     * @throws ExternalServiceException    if API communication fails
     * @throws ServiceUnavailableException if API is unavailable
     * @throws UnsupportedFormatException  if API response cannot be parsed
     */
    public ProductResponse fetchProductData(String upc) {
        log.debug("Fetching product data for UPC: {} from Open Food Facts", upc);

        validateUpc(upc, "fetchProductData");

        try {
            String apiUrl = buildApiUrl(upc);
            HttpHeaders headers = createHeaders();
            HttpEntity<String> entity = new HttpEntity<>(headers);

            log.debug("Making API request to: {}", apiUrl);
            ResponseEntity<String> response = executeApiCall(apiUrl, entity, upc);

            if (response.getBody() == null) {
                log.warn("Received empty response body from Open Food Facts API for UPC: {}", upc);
                throw new ExternalServiceException("Open Food Facts API", "Empty response received");
            }

            return parseOpenFoodFactsResponse(response.getBody(), upc);

        } catch (ValidationException | ExternalServiceException | ServiceUnavailableException |
                 UnsupportedFormatException | RateLimitExceededException e) {
            // Re-throw our custom exceptions as-is
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error while fetching product data for UPC: {}", upc, e);
            throw new ExternalServiceException("Open Food Facts API", "Unexpected error during API call", e);
        }
    }

    /**
     * Fetches detailed product data including nutritional information.
     *
     * @param upc The UPC/barcode to lookup
     * @return ProductResponse with detailed nutritional data
     * @throws ValidationException         if UPC is null or empty
     * @throws ExternalServiceException    if API communication fails
     * @throws ServiceUnavailableException if API is unavailable
     * @throws UnsupportedFormatException  if API response cannot be parsed
     */
    public ProductResponse fetchDetailedProductData(String upc) {
        log.debug("Fetching detailed product data for UPC: {} from Open Food Facts", upc);

        validateUpc(upc, "fetchDetailedProductData");

        try {
            String apiUrl = buildDetailedApiUrl(upc);
            HttpHeaders headers = createHeaders();
            HttpEntity<String> entity = new HttpEntity<>(headers);

            log.debug("Making detailed API request to: {}", apiUrl);
            ResponseEntity<String> response = executeApiCall(apiUrl, entity, upc);

            if (response.getBody() == null) {
                log.warn("Received empty response body from Open Food Facts API for detailed UPC: {}", upc);
                throw new ExternalServiceException("Open Food Facts API", "Empty response received for detailed request");
            }

            return parseDetailedOpenFoodFactsResponse(response.getBody(), upc);

        } catch (ValidationException | ExternalServiceException | ServiceUnavailableException |
                 UnsupportedFormatException | RateLimitExceededException e) {
            // Re-throw our custom exceptions as-is
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error while fetching detailed product data for UPC: {}", upc, e);
            throw new ExternalServiceException("Open Food Facts API", "Unexpected error during detailed API call", e);
        }
    }

    /**
     * Checks if the Open Food Facts API service is available.
     *
     * @return true if service is available, false otherwise
     */
    public boolean isServiceAvailable() {
        try {
            String healthCheckUrl = buildHealthCheckUrl();
            HttpHeaders headers = createHeaders();
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    healthCheckUrl,
                    HttpMethod.GET,
                    entity,
                    String.class
            );

            boolean isAvailable = response.getStatusCode().is2xxSuccessful();
            log.debug("Open Food Facts API health check: {}", isAvailable ? "AVAILABLE" : "UNAVAILABLE");
            return isAvailable;

        } catch (Exception e) {
            log.warn("Open Food Facts API health check failed", e);
            return false;
        }
    }

    /**
     * Validates the UPC parameter for API operations.
     *
     * @param upc       The UPC to validate
     * @param operation The operation being performed (for logging)
     * @throws ValidationException if UPC is invalid
     */
    private void validateUpc(String upc, String operation) {
        if (upc == null) {
            log.warn("Null UPC provided to {}", operation);
            throw new ValidationException("UPC cannot be null");
        }

        String trimmedUpc = upc.trim();
        if (trimmedUpc.isEmpty()) {
            log.warn("Empty or whitespace-only UPC provided to {}", operation);
            throw new ValidationException("UPC cannot be empty or whitespace");
        }

        // Additional UPC format validation could be added here
        log.debug("UPC validation passed for operation: {}", operation);
    }

    /**
     * Executes the API call with proper error handling and retry logic.
     *
     * @param apiUrl The API URL to call
     * @param entity The HTTP entity with headers
     * @param upc    The UPC being requested (for logging)
     * @return ResponseEntity with the API response
     * @throws ExternalServiceException    if the API call fails
     * @throws ServiceUnavailableException if the API service is unavailable
     */
    private ResponseEntity<String> executeApiCall(String apiUrl, HttpEntity<String> entity, String upc) {
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    apiUrl,
                    HttpMethod.GET,
                    entity,
                    String.class
            );

            log.debug("Received response with status: {} for UPC: {}", response.getStatusCode(), upc);
            return response;

        } catch (org.springframework.web.client.ResourceAccessException e) {
            // Network connectivity issues, timeouts, etc.
            log.error("Network error while calling Open Food Facts API for UPC: {}", upc, e);
            throw new ServiceUnavailableException("Open Food Facts API Network connectivity issues", e);
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            // 4xx client errors
            log.error("Client error response from Open Food Facts API for UPC: {} - Status: {}",
                    upc, e.getStatusCode(), e);

            if (e.getStatusCode().value() == 429) {
                throw new RateLimitExceededException("Open Food Facts API rate limit exceeded");
            }

            throw new ExternalServiceException("Open Food Facts API",
                    "Client error: " + e.getStatusCode() + " - " + e.getStatusText(), e);
        } catch (org.springframework.web.client.HttpServerErrorException e) {
            // 5xx server errors
            log.error("Server error response from Open Food Facts API for UPC: {} - Status: {}",
                    upc, e.getStatusCode(), e);
            throw new ServiceUnavailableException("Open Food Facts API Server error: " + e.getStatusCode() + " - " + e.getStatusText(), e);
        } catch (RestClientException e) {
            // Other REST client exceptions
            log.error("REST client error while calling Open Food Facts API for UPC: {}", upc, e);
            throw new ExternalServiceException("Open Food Facts API",
                    "REST client error: " + e.getMessage(), e);
        }
    }

    /**
     * Validates configuration properties at startup.
     *
     * @throws ConfigurationException if configuration is invalid
     */
    @PostConstruct
    private void validateConfiguration() {
        try {
            if (baseUrl == null || baseUrl.trim().isEmpty()) {
                throw new ConfigurationException("app.openfoodfacts.base-url", "Base URL cannot be null or empty");
            }

            if (stagingUrl == null || stagingUrl.trim().isEmpty()) {
                throw new ConfigurationException("app.openfoodfacts.staging-url", "Staging URL cannot be null or empty");
            }

            if (userAgent == null || userAgent.trim().isEmpty()) {
                throw new ConfigurationException("app.openfoodfacts.user-agent", "User agent cannot be null or empty");
            }

            if (timeoutMs <= 0) {
                throw new ConfigurationException("app.openfoodfacts.timeout", "Timeout must be positive");
            }

            // Validate URL formats
            try {
                URI.create(baseUrl).toURL();
                URI.create(stagingUrl).toURL();
            } catch (MalformedURLException | IllegalArgumentException e) {
                throw new ConfigurationException("URL configuration", "Invalid URL format");
            }

            log.info("UpcApiService configuration validated successfully");
            log.debug("Configuration - BaseURL: {}, StagingURL: {}, UseStaging: {}, Timeout: {}ms",
                    baseUrl, stagingUrl, useStaging, timeoutMs);

        } catch (ConfigurationException e) {
            log.error("Configuration validation failed for UpcApiService", e);
            throw e;
        }
    }

    private String buildApiUrl(String upc) {
        return String.format("%s/api/v2/product/%s?fields=product_name,brands,categories,generic_name",
                getBaseUrl(), upc);
    }

    private String buildDetailedApiUrl(String upc) {
        return String.format("%s/api/v2/product/%s?fields=product_name,brands,categories,generic_name,nutrition_grades,nutriscore_data,nutriments,ingredients_text,allergens,labels",
                getBaseUrl(), upc);
    }

    private String buildHealthCheckUrl() {
        return getBaseUrl() + "/api/v2/product/737628064502"; // Coca-Cola as test product
    }

    private String getBaseUrl() {
        String selectedUrl = useStaging ? stagingUrl : baseUrl;
        log.debug("Using {} environment: {}", useStaging ? "staging" : "production", selectedUrl);
        return selectedUrl;
    }

    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("User-Agent", userAgent);
        headers.set("Accept", "application/json");
        headers.set("Cache-Control", "no-cache");

        // Add basic auth for staging environment
        if (useStaging) {
            headers.setBasicAuth("off", "off");
            log.debug("Added basic authentication for staging environment");
        }

        return headers;
    }

    private ProductResponse parseOpenFoodFactsResponse(String responseBody, String upc) {
        try {
            JsonNode rootNode = objectMapper.readTree(responseBody);

            int status = rootNode.path("status").asInt(0);
            if (status != 1) {
                log.info("Product not found in Open Food Facts for UPC: {} (status: {})", upc, status);
                return createNotFoundResponse(upc);
            }

            JsonNode productNode = rootNode.path("product");
            if (productNode.isMissingNode() || productNode.isNull()) {
                log.warn("Product data missing in Open Food Facts response for UPC: {}", upc);
                return createNotFoundResponse(upc);
            }

            ProductResponse result = ProductResponse.builder()
                    .upc(upc)
                    .name(extractProductName(productNode))
                    .brand(extractBrand(productNode))
                    .category(extractCategory(productNode))
                    .dataSource(ProductDataSource.OPEN_FOOD_FACTS)
                    .requiresApiRetry(false)
                    .retryAttempts(0)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();

            log.debug("Successfully parsed product data for UPC: {} - Name: {}", upc, result.getName());
            return result;

        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            log.error("JSON parsing error for Open Food Facts response, UPC: {}", upc, e);
            throw new UnsupportedFormatException("Open Food Facts API response Invalid JSON format");
        } catch (Exception e) {
            log.error("Unexpected error while parsing Open Food Facts response for UPC: {}", upc, e);
            throw new UnsupportedFormatException("Open Food Facts API response Unexpected parsing error: " + e.getMessage());
        }
    }

    private ProductResponse parseDetailedOpenFoodFactsResponse(String responseBody, String upc) {
        try {
            JsonNode rootNode = objectMapper.readTree(responseBody);

            int status = rootNode.path("status").asInt(0);
            if (status != 1) {
                log.info("Detailed product not found in Open Food Facts for UPC: {} (status: {})", upc, status);
                return createNotFoundResponse(upc);
            }

            JsonNode productNode = rootNode.path("product");
            if (productNode.isMissingNode() || productNode.isNull()) {
                log.warn("Detailed product data missing in Open Food Facts response for UPC: {}", upc);
                return createNotFoundResponse(upc);
            }

            // For detailed response, we could extend ProductResponse or create a separate detailed DTO
            // For now, using the existing ProductResponse structure
            ProductResponse result = ProductResponse.builder()
                    .upc(upc)
                    .name(extractProductName(productNode))
                    .brand(extractBrand(productNode))
                    .category(extractCategory(productNode))
                    .dataSource(ProductDataSource.OPEN_FOOD_FACTS)
                    .requiresApiRetry(false)
                    .retryAttempts(0)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();

            log.debug("Successfully parsed detailed product data for UPC: {} - Name: {}", upc, result.getName());
            return result;

        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            log.error("JSON parsing error for detailed Open Food Facts response, UPC: {}", upc, e);
            throw new UnsupportedFormatException("Open Food Facts detailed API response Invalid JSON format");
        } catch (Exception e) {
            log.error("Unexpected error while parsing detailed Open Food Facts response for UPC: {}", upc, e);
            throw new UnsupportedFormatException("Open Food Facts detailed API response Unexpected parsing error: " + e.getMessage());
        }
    }

    private ProductResponse createNotFoundResponse(String upc) {
        log.debug("Creating not-found response for UPC: {}", upc);
        return ProductResponse.builder()
                .upc(upc)
                .name("Product not found")
                .dataSource(ProductDataSource.OPEN_FOOD_FACTS)
                .requiresApiRetry(true)
                .retryAttempts(1)
                .lastRetryAttempt(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private String extractProductName(JsonNode productNode) {
        // Try multiple fields for product name with priority order
        String productName = getTextFromNode(productNode, "product_name");
        if (isBlankString(productName)) {
            productName = getTextFromNode(productNode, "generic_name");
        }

        String result = isBlankString(productName) ? "Unknown Product" : productName.trim();
        log.debug("Extracted product name: '{}'", result);
        return result;
    }

    private String extractBrand(JsonNode productNode) {
        String brandsText = getTextFromNode(productNode, "brands");
        if (isBlankString(brandsText)) {
            return null;
        }

        // Brands can be comma-separated, take the first one and trim whitespace
        String firstBrand = brandsText.split(",")[0].trim();
        String result = isBlankString(firstBrand) ? null : firstBrand;
        log.debug("Extracted brand: '{}'", result);
        return result;
    }

    private String extractCategory(JsonNode productNode) {
        String categoriesText = getTextFromNode(productNode, "categories");
        if (isBlankString(categoriesText)) {
            return null;
        }

        // Categories can be comma-separated, take the first one and trim whitespace
        String firstCategory = categoriesText.split(",")[0].trim();
        String result = isBlankString(firstCategory) ? null : firstCategory;
        log.debug("Extracted category: '{}'", result);
        return result;
    }

    /**
     * Safely extracts text from a JSON node, handling various node types.
     *
     * @param parentNode The parent JSON node
     * @param fieldName  The field name to extract
     * @return The text value or empty string if not found/not textual
     */
    private String getTextFromNode(JsonNode parentNode, String fieldName) {
        JsonNode fieldNode = parentNode.path(fieldName);
        if (fieldNode.isMissingNode() || fieldNode.isNull()) {
            return "";
        }

        // Only extract if it's actually a textual node
        if (fieldNode.isTextual()) {
            return fieldNode.asText("");
        }

        log.debug("Field '{}' exists but is not textual (type: {}), ignoring",
                fieldName, fieldNode.getNodeType());
        return "";
    }

    /**
     * Checks if a string is null, empty, or contains only whitespace.
     *
     * @param str The string to check
     * @return true if the string is blank
     */
    private boolean isBlankString(String str) {
        return str == null || str.trim().isEmpty();
    }

    /**
     * Data class for detailed Open Food Facts API response
     */
    @Data
    public static class OpenFoodFactsResponse {
        private int status;
        @JsonProperty("status_verbose")
        private String statusVerbose;
        private String code;
        private OpenFoodFactsProduct product;
    }

    /**
     * Data class for Open Food Facts product data
     */
    @Data
    public static class OpenFoodFactsProduct {
        @JsonProperty("product_name")
        private String productName;
        private String brands;
        private String categories;
        @JsonProperty("generic_name")
        private String genericName;
        @JsonProperty("nutrition_grades")
        private String nutritionGrades;
        @JsonProperty("nutriscore_data")
        private JsonNode nutriscoreData;
        private JsonNode nutriments;
        @JsonProperty("ingredients_text")
        private String ingredientsText;
        private String allergens;
        private String labels;
    }
}