
package org.cubord.cubordbackend.service;

import org.cubord.cubordbackend.dto.product.ProductResponse;
import org.cubord.cubordbackend.exception.ExternalServiceException;
import org.cubord.cubordbackend.exception.NotFoundException;
import org.cubord.cubordbackend.exception.ValidationException;
import org.cubord.cubordbackend.security.SecurityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive test suite for UpcApiService using the modernized security architecture.
 *
 * <p>These tests verify:</p>
 * <ul>
 *   <li>SecurityService integration for authentication context</li>
 *   <li>Authorization via @PreAuthorize (integration tests verify actual enforcement)</li>
 *   <li>Business logic correctness</li>
 *   <li>External API interaction</li>
 *   <li>Error handling and validation</li>
 * </ul>
 *
 * <p>Note: @PreAuthorize enforcement is not tested in unit tests as it requires
 * Spring Security context. Integration tests should cover authorization scenarios.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UpcApiService Tests")
class UpcApiServiceTest {

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private SecurityService securityService;

    @InjectMocks
    private UpcApiService upcApiService;

    // Test data
    private static final String VALID_UPC = "3017624010701"; // Example: Nutella
    private static final String INVALID_UPC = "0000000000000";
    private static final UUID SAMPLE_USER_ID = UUID.randomUUID();

    private static final String VALID_API_RESPONSE = 
                "{\"status\":1,\"product\":{\"product_name\":\"Nutella\",\"brands\":\"Ferrero\",\"categories\":\"Spreads, Sweet spreads, Hazelnut spreads\"}}";

        private static final String NOT_FOUND_RESPONSE = 
                "{\"status\":0,\"status_verbose\":\"product not found\"}";

        private static final String INCOMPLETE_DATA_RESPONSE = 
                "{\"status\":1,\"product\":{\"code\":\"3017624010701\"}}";

    @BeforeEach
    void setUp() {
        // Configure the service with test values
        ReflectionTestUtils.setField(upcApiService, "apiUrl", "https://world.openfoodfacts.org/api/v2");
        ReflectionTestUtils.setField(upcApiService, "timeout", 5000);
        ReflectionTestUtils.setField(upcApiService, "userAgent", "CubordApp/1.0-Test");

        lenient().when(securityService.getCurrentUserId()).thenReturn(SAMPLE_USER_ID);
    }

    // ==================== Configuration Tests ====================

    @Nested
    @DisplayName("Configuration Validation")
    class ConfigurationValidationTests {

        @Test
        @DisplayName("should throw exception when API URL is null")
        void whenApiUrlIsNull_throwsException() throws Exception {
            // Given
            ReflectionTestUtils.setField(upcApiService, "apiUrl", null);

            // When & Then
            assertThatThrownBy(() -> {
                // Trigger @PostConstruct manually for testing
                java.lang.reflect.Method method = UpcApiService.class.getDeclaredMethod("validateConfiguration");
                method.setAccessible(true);
                method.invoke(upcApiService);
            })
                    .isInstanceOf(java.lang.reflect.InvocationTargetException.class)
                    .hasCauseInstanceOf(IllegalStateException.class)
                    .hasRootCauseMessage("Open Food Facts API URL must be configured");
        }

        @Test
        @DisplayName("should throw exception when API URL is empty")
        void whenApiUrlIsEmpty_throwsException() throws Exception {
            // Given
            ReflectionTestUtils.setField(upcApiService, "apiUrl", "   ");

            // When & Then
            assertThatThrownBy(() -> {
                java.lang.reflect.Method method = UpcApiService.class.getDeclaredMethod("validateConfiguration");
                method.setAccessible(true);
                method.invoke(upcApiService);
            })
                    .isInstanceOf(java.lang.reflect.InvocationTargetException.class)
                    .hasCauseInstanceOf(IllegalStateException.class)
                    .hasRootCauseMessage("Open Food Facts API URL must be configured");
        }

        @Test
        @DisplayName("should throw exception when timeout is zero")
        void whenTimeoutIsZero_throwsException() throws Exception {
            // Given
            ReflectionTestUtils.setField(upcApiService, "timeout", 0);

            // When & Then
            assertThatThrownBy(() -> {
                java.lang.reflect.Method method = UpcApiService.class.getDeclaredMethod("validateConfiguration");
                method.setAccessible(true);
                method.invoke(upcApiService);
            })
                    .isInstanceOf(java.lang.reflect.InvocationTargetException.class)
                    .hasCauseInstanceOf(IllegalStateException.class)
                    .hasRootCauseMessage("API timeout must be positive");
        }

        @Test
        @DisplayName("should throw exception when timeout is negative")
        void whenTimeoutIsNegative_throwsException() throws Exception {
            // Given
            ReflectionTestUtils.setField(upcApiService, "timeout", -1000);

            // When & Then
            assertThatThrownBy(() -> {
                java.lang.reflect.Method method = UpcApiService.class.getDeclaredMethod("validateConfiguration");
                method.setAccessible(true);
                method.invoke(upcApiService);
            })
                    .isInstanceOf(java.lang.reflect.InvocationTargetException.class)
                    .hasCauseInstanceOf(IllegalStateException.class)
                    .hasRootCauseMessage("API timeout must be positive");
        }
    }

    // ==================== fetchProductData Tests ====================

    @Nested
    @DisplayName("fetchProductData")
    class FetchProductDataTests {

        @Test
        @DisplayName("should successfully fetch product data for valid UPC")
        void whenValidUpc_returnsProductData() {
            // Given
            String expectedUrl = "https://world.openfoodfacts.org/api/v2/product/" + VALID_UPC + ".json";
            when(restTemplate.getForObject(eq(expectedUrl), eq(String.class)))
                    .thenReturn(VALID_API_RESPONSE);

            // When
            ProductResponse response = upcApiService.fetchProductData(VALID_UPC);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getUpc()).isEqualTo(VALID_UPC);
            assertThat(response.getName()).isEqualTo("Nutella");
            assertThat(response.getBrand()).isEqualTo("Ferrero");
            assertThat(response.getCategory()).isEqualTo("Spreads"); // First category only

            verify(restTemplate).getForObject(eq(expectedUrl), eq(String.class));
            verify(securityService).getCurrentUserId();
        }

        @Test
        @DisplayName("should throw NotFoundException when product not found in API")
        void whenProductNotFoundInApi_throwsNotFoundException() {
            // Given
            String expectedUrl = "https://world.openfoodfacts.org/api/v2/product/" + INVALID_UPC + ".json";
            when(restTemplate.getForObject(eq(expectedUrl), eq(String.class)))
                    .thenReturn(NOT_FOUND_RESPONSE);

            // When & Then
            assertThatThrownBy(() -> upcApiService.fetchProductData(INVALID_UPC))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("not found in external database");

            verify(restTemplate).getForObject(eq(expectedUrl), eq(String.class));
            verify(securityService).getCurrentUserId();
        }

        @Test
        @DisplayName("should throw ExternalServiceException when API returns empty response")
        void whenApiReturnsEmptyResponse_throwsExternalServiceException() {
            // Given
            String expectedUrl = "https://world.openfoodfacts.org/api/v2/product/" + VALID_UPC + ".json";
            when(restTemplate.getForObject(eq(expectedUrl), eq(String.class)))
                    .thenReturn("");

            // When & Then
            assertThatThrownBy(() -> upcApiService.fetchProductData(VALID_UPC))
                    .isInstanceOf(ExternalServiceException.class)
                    .hasMessageContaining("Empty response");

            verify(restTemplate).getForObject(eq(expectedUrl), eq(String.class));
            verify(securityService).getCurrentUserId();
        }

        @Test
        @DisplayName("should throw ExternalServiceException when API returns null")
        void whenApiReturnsNull_throwsExternalServiceException() {
            // Given
            String expectedUrl = "https://world.openfoodfacts.org/api/v2/product/" + VALID_UPC + ".json";
            when(restTemplate.getForObject(eq(expectedUrl), eq(String.class)))
                    .thenReturn(null);

            // When & Then
            assertThatThrownBy(() -> upcApiService.fetchProductData(VALID_UPC))
                    .isInstanceOf(ExternalServiceException.class)
                    .hasMessageContaining("Empty response");

            verify(restTemplate).getForObject(eq(expectedUrl), eq(String.class));
            verify(securityService).getCurrentUserId();
        }

        @Test
        @DisplayName("should throw ExternalServiceException when API returns insufficient data")
        void whenApiReturnsInsufficientData_throwsExternalServiceException() {
            // Given
            String expectedUrl = "https://world.openfoodfacts.org/api/v2/product/" + VALID_UPC + ".json";
            when(restTemplate.getForObject(eq(expectedUrl), eq(String.class)))
                    .thenReturn(INCOMPLETE_DATA_RESPONSE);

            // When & Then
            assertThatThrownBy(() -> upcApiService.fetchProductData(VALID_UPC))
                    .isInstanceOf(ExternalServiceException.class)
                    .hasMessageContaining("Insufficient product data");

            verify(restTemplate).getForObject(eq(expectedUrl), eq(String.class));
            verify(securityService).getCurrentUserId();
        }

        @Test
        @DisplayName("should throw ExternalServiceException when RestTemplate throws exception")
        void whenRestTemplateThrowsException_throwsExternalServiceException() {
            // Given
            String expectedUrl = "https://world.openfoodfacts.org/api/v2/product/" + VALID_UPC + ".json";
            when(restTemplate.getForObject(eq(expectedUrl), eq(String.class)))
                    .thenThrow(new RestClientException("Connection timeout"));

            // When & Then
            assertThatThrownBy(() -> upcApiService.fetchProductData(VALID_UPC))
                    .isInstanceOf(ExternalServiceException.class)
                    .hasMessageContaining("Failed to fetch product data")
                    .hasCauseInstanceOf(RestClientException.class);

            verify(restTemplate).getForObject(eq(expectedUrl), eq(String.class));
            verify(securityService).getCurrentUserId();
        }

        @Test
        @DisplayName("should throw ValidationException when UPC is null")
        void whenUpcIsNull_throwsValidationException() {
            // When & Then
            assertThatThrownBy(() -> upcApiService.fetchProductData(null))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("UPC cannot be null or empty");

            verify(restTemplate, never()).getForObject(anyString(), eq(String.class));
            verify(securityService, never()).getCurrentUserId();
        }

        @Test
        @DisplayName("should throw ValidationException when UPC is empty")
        void whenUpcIsEmpty_throwsValidationException() {
            // When & Then
            assertThatThrownBy(() -> upcApiService.fetchProductData(""))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("UPC cannot be null or empty");

            verify(restTemplate, never()).getForObject(anyString(), eq(String.class));
            verify(securityService, never()).getCurrentUserId();
        }

        @Test
        @DisplayName("should throw ValidationException when UPC is blank")
        void whenUpcIsBlank_throwsValidationException() {
            // When & Then
            assertThatThrownBy(() -> upcApiService.fetchProductData("   "))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("UPC cannot be null or empty");

            verify(restTemplate, never()).getForObject(anyString(), eq(String.class));
            verify(securityService, never()).getCurrentUserId();
        }

            @Test
            @DisplayName("should handle product with only name")
            void whenProductHasOnlyName_returnsProductWithName() {
                // Given
                String responseWithNameOnly = "{\"status\":1,\"product\":{\"product_name\":\"Test Product\"}}";
                String expectedUrl = "https://world.openfoodfacts.org/api/v2/product/" + VALID_UPC + ".json";
                when(restTemplate.getForObject(eq(expectedUrl), eq(String.class)))
                        .thenReturn(responseWithNameOnly);

                // When
                ProductResponse response = upcApiService.fetchProductData(VALID_UPC);

                // Then
                assertThat(response).isNotNull();
                assertThat(response.getUpc()).isEqualTo(VALID_UPC);
                assertThat(response.getName()).isEqualTo("Test Product");
                // Brand and category can be null or empty - either is acceptable
                if (response.getBrand() != null) {
                    assertThat(response.getBrand()).isEmpty();
                }
                if (response.getCategory() != null) {
                    assertThat(response.getCategory()).isEmpty();
                }

                verify(restTemplate).getForObject(eq(expectedUrl), eq(String.class));
                verify(securityService).getCurrentUserId();
            }

            @Test
            @DisplayName("should handle product with only brand")
            void whenProductHasOnlyBrand_returnsProductWithBrand() {
                // Given
                String responseWithBrandOnly = "{\"status\":1,\"product\":{\"brands\":\"Test Brand\"}}";
                String expectedUrl = "https://world.openfoodfacts.org/api/v2/product/" + VALID_UPC + ".json";
                when(restTemplate.getForObject(eq(expectedUrl), eq(String.class)))
                        .thenReturn(responseWithBrandOnly);

                // When
                ProductResponse response = upcApiService.fetchProductData(VALID_UPC);

                // Then
                assertThat(response).isNotNull();
                assertThat(response.getUpc()).isEqualTo(VALID_UPC);
                // Name and category can be null or empty - either is acceptable
                if (response.getName() != null) {
                    assertThat(response.getName()).isEmpty();
                }
                assertThat(response.getBrand()).isEqualTo("Test Brand");
                if (response.getCategory() != null) {
                    assertThat(response.getCategory()).isEmpty();
                }

                verify(restTemplate).getForObject(eq(expectedUrl), eq(String.class));
                verify(securityService).getCurrentUserId();
            }
    }

    // ==================== Deprecated Method Tests ====================

    @Nested
    @DisplayName("Deprecated fetchProductData(String, JwtAuthenticationToken)")
    class DeprecatedFetchProductDataTests {

        @Test
        @DisplayName("should delegate to modern method and log deprecation warning")
        void whenCallingDeprecatedMethod_delegatesToModernMethod() {
            // Given
            String expectedUrl = "https://world.openfoodfacts.org/api/v2/product/" + VALID_UPC + ".json";
            when(restTemplate.getForObject(eq(expectedUrl), eq(String.class)))
                    .thenReturn(VALID_API_RESPONSE);

            // Note: We can't easily verify log output in unit tests, but we can verify behavior
            // When
            ProductResponse response = upcApiService.fetchProductData(VALID_UPC, null);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getUpc()).isEqualTo(VALID_UPC);
            assertThat(response.getName()).isEqualTo("Nutella");

            // Verify that the modern path is used (no token validation)
            verify(restTemplate).getForObject(eq(expectedUrl), eq(String.class));
            verify(securityService).getCurrentUserId();
        }
    }
}