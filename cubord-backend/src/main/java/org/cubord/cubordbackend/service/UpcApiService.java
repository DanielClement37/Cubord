package org.cubord.cubordbackend.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cubord.cubordbackend.domain.ProductDataSource;
import org.cubord.cubordbackend.dto.product.ProductResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

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
     * @throws RuntimeException if API call fails
     */
    public ProductResponse fetchProductData(String upc) {
        log.debug("Fetching product data for UPC: {} from Open Food Facts", upc);
        
        if (upc == null || upc.trim().isEmpty()) {
            throw new IllegalArgumentException("UPC cannot be null or empty");
        }
        
        try {
            String apiUrl = buildApiUrl(upc);
            HttpHeaders headers = createHeaders();
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            log.debug("Making API request to: {}", apiUrl);
            ResponseEntity<String> response = restTemplate.exchange(
                apiUrl, 
                HttpMethod.GET, 
                entity, 
                String.class
            );
            
            if (response.getBody() == null) {
                throw new RuntimeException("Empty response from Open Food Facts API");
            }
            
            return parseOpenFoodFactsResponse(response.getBody(), upc);
            
        } catch (RestClientException e) {
            log.error("Failed to fetch product data for UPC: {} from Open Food Facts", upc, e);
            throw new RuntimeException("Failed to fetch product data from Open Food Facts API", e);
        } catch (RuntimeException e) {
            // If it's already a RuntimeException, just re-throw it to preserve the original message
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error while fetching product data for UPC: {}", upc, e);
            throw new RuntimeException("Unexpected error during API call", e);
        }
    }
    
    /**
     * Fetches detailed product data including nutritional information.
     * 
     * @param upc The UPC/barcode to lookup
     * @return ProductResponse with detailed nutritional data
     */
    public ProductResponse fetchDetailedProductData(String upc) {
        log.debug("Fetching detailed product data for UPC: {} from Open Food Facts", upc);
        
        if (upc == null || upc.trim().isEmpty()) {
            throw new IllegalArgumentException("UPC cannot be null or empty");
        }
        
        try {
            String apiUrl = buildDetailedApiUrl(upc);
            HttpHeaders headers = createHeaders();
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            log.debug("Making detailed API request to: {}", apiUrl);
            ResponseEntity<String> response = restTemplate.exchange(
                apiUrl, 
                HttpMethod.GET, 
                entity, 
                String.class
            );
            
            if (response.getBody() == null) {
                throw new RuntimeException("Empty response from Open Food Facts API");
            }
            
            return parseDetailedOpenFoodFactsResponse(response.getBody(), upc);
            
        } catch (RestClientException e) {
            log.error("Failed to fetch detailed product data for UPC: {} from Open Food Facts", upc, e);
            throw new RuntimeException("Failed to fetch detailed product data from Open Food Facts API", e);
        } catch (RuntimeException e) {
            // If it's already a RuntimeException, just re-throw it to preserve the original message
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error while fetching detailed product data for UPC: {}", upc, e);
            throw new RuntimeException("Unexpected error during detailed API call", e);
        }
    }
    
    /**
     * Checks if the Open Food Facts API service is available.
     * 
     * @return true if service is available
     */
    public boolean isServiceAvailable() {
        try {
            String healthCheckUrl = getBaseUrl() + "/api/v2/product/737628064502"; // Coca-Cola as test product
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
    
    private String buildApiUrl(String upc) {
        return String.format("%s/api/v2/product/%s?fields=product_name,brands,categories,generic_name",
                getBaseUrl(), upc);
    }
    
    private String buildDetailedApiUrl(String upc) {
        return String.format("%s/api/v2/product/%s?fields=product_name,brands,categories,generic_name,nutrition_grades,nutriscore_data,nutriments,ingredients_text,allergens,labels",
                getBaseUrl(), upc);
    }
    
    private String getBaseUrl() {
        return useStaging ? stagingUrl : baseUrl;
    }
    
    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("User-Agent", userAgent);
        
        // Add basic auth for staging environment
        if (useStaging) {
            headers.setBasicAuth("off", "off");
        }
        
        return headers;
    }
    
    private ProductResponse parseOpenFoodFactsResponse(String responseBody, String upc) {
        try {
            JsonNode rootNode = objectMapper.readTree(responseBody);
            
            int status = rootNode.path("status").asInt(0);
            if (status != 1) {
                log.warn("Product not found in Open Food Facts for UPC: {}", upc);
                return createNotFoundResponse(upc);
            }
            
            JsonNode productNode = rootNode.path("product");
            if (productNode.isMissingNode()) {
                log.warn("Product data missing in Open Food Facts response for UPC: {}", upc);
                return createNotFoundResponse(upc);
            }
            
            return ProductResponse.builder()
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
                    
        } catch (Exception e) {
            log.error("Failed to parse Open Food Facts response for UPC: {}", upc, e);
            throw new RuntimeException("Failed to parse Open Food Facts API response", e);
        }
    }
    
    private ProductResponse parseDetailedOpenFoodFactsResponse(String responseBody, String upc) {
        try {
            JsonNode rootNode = objectMapper.readTree(responseBody);
            
            int status = rootNode.path("status").asInt(0);
            if (status != 1) {
                log.warn("Product not found in Open Food Facts for UPC: {}", upc);
                return createNotFoundResponse(upc);
            }
            
            JsonNode productNode = rootNode.path("product");
            if (productNode.isMissingNode()) {
                log.warn("Product data missing in Open Food Facts response for UPC: {}", upc);
                return createNotFoundResponse(upc);
            }
            
            // For detailed response, we could extend ProductResponse or create a separate detailed DTO
            // For now, using the existing ProductResponse structure
            return ProductResponse.builder()
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
                    
        } catch (Exception e) {
            log.error("Failed to parse detailed Open Food Facts response for UPC: {}", upc, e);
            throw new RuntimeException("Failed to parse detailed Open Food Facts API response", e);
        }
    }
    
    private ProductResponse createNotFoundResponse(String upc) {
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
        // Try multiple fields for product name
        String productName = productNode.path("product_name").asText("");
        if (productName.isEmpty()) {
            productName = productNode.path("generic_name").asText("");
        }
        return productName.isEmpty() ? "Unknown Product" : productName;
    }
    
    private String extractBrand(JsonNode productNode) {
        JsonNode brandsNode = productNode.path("brands");
        if (brandsNode.isTextual() && !brandsNode.asText().isEmpty()) {
            // Brands can be comma-separated, take the first one
            String brands = brandsNode.asText();
            return brands.split(",")[0].trim();
        }
        return null;
    }
    
    private String extractCategory(JsonNode productNode) {
        JsonNode categoriesNode = productNode.path("categories");
        if (categoriesNode.isTextual() && !categoriesNode.asText().isEmpty()) {
            // Categories can be comma-separated, take the first one
            String categories = categoriesNode.asText();
            return categories.split(",")[0].trim();
        }
        return null;
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
