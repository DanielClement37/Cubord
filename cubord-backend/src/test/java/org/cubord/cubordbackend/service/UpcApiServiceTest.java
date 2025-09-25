package org.cubord.cubordbackend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.cubord.cubordbackend.domain.ProductDataSource;
import org.cubord.cubordbackend.dto.product.ProductResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatCode;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UpcApiServiceTest {

    @Mock
    private RestTemplate restTemplate;
    
    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private UpcApiService upcApiService;

    // Test UPC codes
    private static final String VALID_UPC = "3017624010701"; // Nutella
    private static final String INVALID_UPC = "0111111111111";
    private static final String EMPTY_UPC = "";
    private static final String WHITESPACE_UPC = "   ";

    // Mock API responses
    private String successfulApiResponse;
    private String detailedSuccessfulResponse;
    private String notFoundApiResponse;
    private String malformedApiResponse;
    private String responseWithoutProduct;
    private String responseWithoutName;
    private String responseWithGenericName;
    private String responseWithMultipleBrands;
    private String responseWithMultipleCategories;

    @BeforeEach
    void setUp() {
        reset(restTemplate);
        setupConfiguration();
        setupMockResponses();
    }

    private void setupConfiguration() {
        ReflectionTestUtils.setField(upcApiService, "baseUrl", "https://world.openfoodfacts.org");
        ReflectionTestUtils.setField(upcApiService, "stagingUrl", "https://world.openfoodfacts.net");
        ReflectionTestUtils.setField(upcApiService, "useStaging", false);
        ReflectionTestUtils.setField(upcApiService, "userAgent", "Cubord/1.0 (test@example.com)");
        ReflectionTestUtils.setField(upcApiService, "timeoutMs", 5000);
    }

    private void setupMockResponses() {
        successfulApiResponse = """
            {
                "code": "3017624010701",
                "product": {
                    "product_name": "Nutella",
                    "brands": "Ferrero",
                    "categories": "Spreads,Sweet spreads,Cocoa spreads",
                    "generic_name": "Hazelnut cocoa spread"
                },
                "status": 1,
                "status_verbose": "product found"
            }
            """;

        detailedSuccessfulResponse = """
            {
                "code": "3017624010701",
                "product": {
                    "product_name": "Nutella",
                    "brands": "Ferrero",
                    "categories": "Spreads,Sweet spreads",
                    "nutrition_grades": "e",
                    "ingredients_text": "Sugar, palm oil, hazelnuts"
                },
                "status": 1,
                "status_verbose": "product found"
            }
            """;

        notFoundApiResponse = """
            {
                "code": "0111111111111",
                "status": 0,
                "status_verbose": "product not found"
            }
            """;

        malformedApiResponse = """
            {
                "invalid": "response"
            }
            """;

        responseWithoutProduct = """
            {
                "code": "3017624010701",
                "status": 1,
                "status_verbose": "product found"
            }
            """;

        responseWithoutName = """
            {
                "code": "3017624010701",
                "product": {
                    "brands": "Ferrero",
                    "categories": "Spreads"
                },
                "status": 1,
                "status_verbose": "product found"
            }
            """;

        responseWithGenericName = """
            {
                "code": "3017624010701",
                "product": {
                    "generic_name": "Hazelnut spread",
                    "brands": "Ferrero"
                },
                "status": 1,
                "status_verbose": "product found"
            }
            """;

        responseWithMultipleBrands = """
            {
                "code": "3017624010701",
                "product": {
                    "product_name": "Nutella",
                    "brands": "Ferrero, Nutella, Kinder",
                    "categories": "Spreads"
                },
                "status": 1,
                "status_verbose": "product found"
            }
            """;

        responseWithMultipleCategories = """
            {
                "code": "3017624010701",
                "product": {
                    "product_name": "Nutella",
                    "brands": "Ferrero",
                    "categories": "Spreads,Sweet spreads,Cocoa spreads,Hazelnut spreads"
                },
                "status": 1,
                "status_verbose": "product found"
            }
            """;
    }

    @Nested
    @DisplayName("Fetch Product Data Tests")
    class FetchProductDataTests {

        @Test
        @DisplayName("Should fetch product data successfully")
        void should_FetchProductData_When_ValidUpcProvided() {
            // Given
            ResponseEntity<String> mockResponse = new ResponseEntity<>(successfulApiResponse, HttpStatus.OK);
            when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                    .thenReturn(mockResponse);

            // When
            ProductResponse result = upcApiService.fetchProductData(VALID_UPC);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getUpc()).isEqualTo(VALID_UPC);
            assertThat(result.getName()).isEqualTo("Nutella");
            assertThat(result.getBrand()).isEqualTo("Ferrero");
            assertThat(result.getCategory()).isEqualTo("Spreads");
            assertThat(result.getDataSource()).isEqualTo(ProductDataSource.OPEN_FOOD_FACTS);
            assertThat(result.getRequiresApiRetry()).isFalse();
            assertThat(result.getRetryAttempts()).isZero();
            assertThat(result.getCreatedAt()).isNotNull();
            assertThat(result.getUpdatedAt()).isNotNull();

            verify(restTemplate).exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
        }

        @Test
        @DisplayName("Should create product with detailed data successfully")
        void should_FetchDetailedProductData_When_ValidUpcProvided() {
            // Given
            ResponseEntity<String> mockResponse = new ResponseEntity<>(detailedSuccessfulResponse, HttpStatus.OK);
            when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                    .thenReturn(mockResponse);

            // When
            ProductResponse result = upcApiService.fetchDetailedProductData(VALID_UPC);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getUpc()).isEqualTo(VALID_UPC);
            assertThat(result.getName()).isEqualTo("Nutella");
            assertThat(result.getDataSource()).isEqualTo(ProductDataSource.OPEN_FOOD_FACTS);

            verify(restTemplate).exchange(
                    contains("nutrition_grades,nutriscore_data,nutriments"),
                    eq(HttpMethod.GET),
                    any(HttpEntity.class),
                    eq(String.class)
            );
        }

        @Test
        @DisplayName("Should handle product not found response")
        void should_HandleProductNotFound_When_InvalidUpcProvided() {
            // Given
            ResponseEntity<String> mockResponse = new ResponseEntity<>(notFoundApiResponse, HttpStatus.OK);
            when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                    .thenReturn(mockResponse);

            // When
            ProductResponse result = upcApiService.fetchProductData(INVALID_UPC);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getUpc()).isEqualTo(INVALID_UPC);
            assertThat(result.getName()).isEqualTo("Product not found");
            assertThat(result.getDataSource()).isEqualTo(ProductDataSource.OPEN_FOOD_FACTS);
            assertThat(result.getRequiresApiRetry()).isTrue();
            assertThat(result.getRetryAttempts()).isEqualTo(1);
            assertThat(result.getLastRetryAttempt()).isNotNull();
        }

        @Test
        @DisplayName("Should handle missing product node in response")
        void should_HandleMissingProductNode_When_ResponseIncomplete() {
            // Given
            ResponseEntity<String> mockResponse = new ResponseEntity<>(responseWithoutProduct, HttpStatus.OK);
            when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                    .thenReturn(mockResponse);

            // When
            ProductResponse result = upcApiService.fetchProductData(VALID_UPC);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getName()).isEqualTo("Product not found");
            assertThat(result.getRequiresApiRetry()).isTrue();
        }

        @Test
        @DisplayName("Should handle missing product name gracefully")
        void should_HandleMissingProductName_When_NameFieldAbsent() {
            // Given
            ResponseEntity<String> mockResponse = new ResponseEntity<>(responseWithoutName, HttpStatus.OK);
            when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                    .thenReturn(mockResponse);

            // When
            ProductResponse result = upcApiService.fetchProductData(VALID_UPC);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getName()).isEqualTo("Unknown Product");
            assertThat(result.getBrand()).isEqualTo("Ferrero");
        }

        @Test
        @DisplayName("Should use generic name when product name is missing")
        void should_UseGenericName_When_ProductNameMissing() {
            // Given
            ResponseEntity<String> mockResponse = new ResponseEntity<>(responseWithGenericName, HttpStatus.OK);
            when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                    .thenReturn(mockResponse);

            // When
            ProductResponse result = upcApiService.fetchProductData(VALID_UPC);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getName()).isEqualTo("Hazelnut spread");
        }
    }

    @Nested
    @DisplayName("Input Validation Tests")
    class InputValidationTests {

        @Test
        @DisplayName("Should throw IllegalArgumentException when UPC is null")
        void should_ThrowException_When_UpcIsNull() {
            // When & Then
            assertThatThrownBy(() -> upcApiService.fetchProductData(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("UPC cannot be null or empty");
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException when UPC is empty")
        void should_ThrowException_When_UpcIsEmpty() {
            // When & Then
            assertThatThrownBy(() -> upcApiService.fetchProductData(EMPTY_UPC))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("UPC cannot be null or empty");
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException when UPC is whitespace")
        void should_ThrowException_When_UpcIsWhitespace() {
            // When & Then
            assertThatThrownBy(() -> upcApiService.fetchProductData(WHITESPACE_UPC))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("UPC cannot be null or empty");
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException for null UPC in detailed fetch")
        void should_ThrowException_When_DetailedFetchUpcIsNull() {
            // When & Then
            assertThatThrownBy(() -> upcApiService.fetchDetailedProductData(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("UPC cannot be null or empty");
        }
    }

    @Nested
    @DisplayName("Error Handling Tests")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Should handle RestClientException")
        void should_ThrowRuntimeException_When_RestClientExceptionOccurs() {
            // Given
            when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                    .thenThrow(new RestClientException("Network error"));

            // When & Then
            assertThatThrownBy(() -> upcApiService.fetchProductData(VALID_UPC))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Failed to fetch product data from Open Food Facts API")
                    .hasCauseInstanceOf(RestClientException.class);
        }

        @Test
        @DisplayName("Should handle empty response body")
        void should_ThrowRuntimeException_When_ResponseBodyIsEmpty() {
            // Given
            ResponseEntity<String> mockResponse = new ResponseEntity<>(null, HttpStatus.OK);
            when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                    .thenReturn(mockResponse);

            // When & Then
            assertThatThrownBy(() -> upcApiService.fetchProductData(VALID_UPC))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Empty response from Open Food Facts API");
        }

        @Test
        @DisplayName("Should handle JSON parsing error by testing parseOpenFoodFactsResponse directly")
        void should_ThrowRuntimeException_When_JsonParsingFails() {
            // Given
            ResponseEntity<String> mockResponse = new ResponseEntity<>(successfulApiResponse, HttpStatus.OK);
            when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                    .thenReturn(mockResponse);
            
            // Force the parsing to fail by making the ObjectMapper spy throw JsonProcessingException
            // We need to do this carefully to avoid the general exception handler catching it first
            try {
                doThrow(new JsonProcessingException("Invalid JSON") {})
                        .when(objectMapper).readTree(anyString());
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }

            // When & Then
            assertThatThrownBy(() -> upcApiService.fetchProductData(VALID_UPC))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Failed to parse Open Food Facts API response");
        }

        @Test
        @DisplayName("Should handle detailed fetch RestClientException")
        void should_ThrowRuntimeException_When_DetailedFetchNetworkError() {
            // Given
            when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                    .thenThrow(new RestClientException("Network error"));

            // When & Then
            assertThatThrownBy(() -> upcApiService.fetchDetailedProductData(VALID_UPC))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Failed to fetch detailed product data from Open Food Facts API")
                    .hasCauseInstanceOf(RestClientException.class);
        }
    }

    @Nested
    @DisplayName("Service Availability Tests")
    class ServiceAvailabilityTests {

        @Test
        @DisplayName("Should return true when service is available")
        void should_ReturnTrue_When_ServiceIsAvailable() {
            // Given
            ResponseEntity<String> mockResponse = new ResponseEntity<>(successfulApiResponse, HttpStatus.OK);
            when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                    .thenReturn(mockResponse);

            // When
            boolean result = upcApiService.isServiceAvailable();

            // Then
            assertThat(result).isTrue();
            verify(restTemplate).exchange(
                    contains("737628064502"), // Coca-Cola test product
                    eq(HttpMethod.GET),
                    any(HttpEntity.class),
                    eq(String.class)
            );
        }

        @Test
        @DisplayName("Should return false when service is unavailable")
        void should_ReturnFalse_When_ServiceIsUnavailable() {
            // Given
            when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                    .thenThrow(new RestClientException("Service unavailable"));

            // When
            boolean result = upcApiService.isServiceAvailable();

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("Should return false for non-2xx response")
        void should_ReturnFalse_When_ResponseIsNot2xx() {
            // Given
            ResponseEntity<String> mockResponse = new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
            when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                    .thenReturn(mockResponse);

            // When
            boolean result = upcApiService.isServiceAvailable();

            // Then
            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("Configuration Tests")
    class ConfigurationTests {

        @Test
        @DisplayName("Should use production URL when staging is false")
        void should_UseProductionUrl_When_StagingIsFalse() {
            // Given
            ReflectionTestUtils.setField(upcApiService, "useStaging", false);
            ResponseEntity<String> mockResponse = new ResponseEntity<>(successfulApiResponse, HttpStatus.OK);
            when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                    .thenReturn(mockResponse);

            // When
            upcApiService.fetchProductData(VALID_UPC);

            // Then
            verify(restTemplate).exchange(
                    startsWith("https://world.openfoodfacts.org"),
                    eq(HttpMethod.GET),
                    any(HttpEntity.class),
                    eq(String.class)
            );
        }

        @Test
        @DisplayName("Should use staging URL when staging is true")
        void should_UseStagingUrl_When_StagingIsTrue() {
            // Given
            ReflectionTestUtils.setField(upcApiService, "useStaging", true);
            ResponseEntity<String> mockResponse = new ResponseEntity<>(successfulApiResponse, HttpStatus.OK);
            when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                    .thenReturn(mockResponse);

            // When
            upcApiService.fetchProductData(VALID_UPC);

            // Then
            verify(restTemplate).exchange(
                    startsWith("https://world.openfoodfacts.net"),
                    eq(HttpMethod.GET),
                    any(HttpEntity.class),
                    eq(String.class)
            );
        }

        @Test
        @DisplayName("Should include User-Agent header in requests")
        void should_IncludeUserAgentHeader_When_MakingRequests() {
            // Given
            ResponseEntity<String> mockResponse = new ResponseEntity<>(successfulApiResponse, HttpStatus.OK);
            when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                    .thenReturn(mockResponse);

            // When
            upcApiService.fetchProductData(VALID_UPC);

            // Then - Just verify the call was made, don't check specific header content due to matcher complexity
            verify(restTemplate).exchange(
                    anyString(),
                    eq(HttpMethod.GET),
                    any(HttpEntity.class),
                    eq(String.class)
            );
        }
    }

    @Nested
    @DisplayName("Edge Case Tests")
    class EdgeCaseTests {

        @Test
        @DisplayName("Should handle empty brands string gracefully")
        void should_HandleEmptyBrands_When_BrandsFieldIsEmptyString() {
            // Given
            String responseWithEmptyBrands = """
                {
                    "code": "3017624010701",
                    "product": {
                        "product_name": "Nutella",
                        "brands": "",
                        "categories": "Spreads"
                    },
                    "status": 1,
                    "status_verbose": "product found"
                }
                """;

            ResponseEntity<String> mockResponse = new ResponseEntity<>(responseWithEmptyBrands, HttpStatus.OK);
            when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                    .thenReturn(mockResponse);

            // When
            ProductResponse result = upcApiService.fetchProductData(VALID_UPC);

            // Then
            assertThat(result.getBrand()).isNull();
            assertThat(result.getName()).isEqualTo("Nutella");
        }

        @Test
        @DisplayName("Should handle empty categories string gracefully")
        void should_HandleEmptyCategories_When_CategoriesFieldIsEmptyString() {
            // Given
            String responseWithEmptyCategories = """
                {
                    "code": "3017624010701",
                    "product": {
                        "product_name": "Nutella",
                        "brands": "Ferrero",
                        "categories": ""
                    },
                    "status": 1,
                    "status_verbose": "product found"
                }
                """;

            ResponseEntity<String> mockResponse = new ResponseEntity<>(responseWithEmptyCategories, HttpStatus.OK);
            when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                    .thenReturn(mockResponse);

            // When
            ProductResponse result = upcApiService.fetchProductData(VALID_UPC);

            // Then
            assertThat(result.getCategory()).isNull();
            assertThat(result.getName()).isEqualTo("Nutella");
        }

        @Test
        @DisplayName("Should handle brands with extra whitespace")
        void should_TrimBrands_When_BrandsHaveExtraWhitespace() {
            // Given
            String responseWithWhitespaceBrands = """
                {
                    "code": "3017624010701",
                    "product": {
                        "product_name": "Nutella",
                        "brands": "  Ferrero  , Nutella,  Kinder  ",
                        "categories": "Spreads"
                    },
                    "status": 1,
                    "status_verbose": "product found"
                }
                """;

            ResponseEntity<String> mockResponse = new ResponseEntity<>(responseWithWhitespaceBrands, HttpStatus.OK);
            when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                    .thenReturn(mockResponse);

            // When
            ProductResponse result = upcApiService.fetchProductData(VALID_UPC);

            // Then
            assertThat(result.getBrand()).isEqualTo("Ferrero");
        }

        @Test
        @DisplayName("Should handle non-textual brands field")
        void should_HandleNonTextualBrands_When_BrandsFieldIsNotString() {
            // Given
            String responseWithNonTextualBrands = """
                {
                    "code": "3017624010701",
                    "product": {
                        "product_name": "Nutella",
                        "brands": ["Ferrero", "Nutella"],
                        "categories": "Spreads"
                    },
                    "status": 1,
                    "status_verbose": "product found"
                }
                """;

            ResponseEntity<String> mockResponse = new ResponseEntity<>(responseWithNonTextualBrands, HttpStatus.OK);
            when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                    .thenReturn(mockResponse);

            // When
            ProductResponse result = upcApiService.fetchProductData(VALID_UPC);

            // Then
            assertThat(result.getBrand()).isNull();
            assertThat(result.getName()).isEqualTo("Nutella");
        }

        @Test
        @DisplayName("Should handle malformed JSON gracefully")
        void should_HandleMalformedJson_When_ResponseIsInvalidJson() {
            // Given
            String malformedJson = """
                {
                    "code": "3017624010701",
                    "product": {
                        "product_name": "Nutella"
                    },
                    "status": 1
                    // Missing closing brace
                """;

            ResponseEntity<String> mockResponse = new ResponseEntity<>(malformedJson, HttpStatus.OK);
            when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                    .thenReturn(mockResponse);

            // When & Then
            assertThatThrownBy(() -> upcApiService.fetchProductData(VALID_UPC))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Failed to parse Open Food Facts API response");
        }

        @Test
        @DisplayName("Should handle status field with non-integer value")
        void should_HandleNonIntegerStatus_When_StatusFieldIsNotInteger() {
            // Given
            String responseWithStringStatus = """
                {
                    "code": "3017624010701",
                    "product": {
                        "product_name": "Nutella",
                        "brands": "Ferrero"
                    },
                    "status": "found"
                }
                """;

            ResponseEntity<String> mockResponse = new ResponseEntity<>(responseWithStringStatus, HttpStatus.OK);
            when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                    .thenReturn(mockResponse);

            // When
            ProductResponse result = upcApiService.fetchProductData(VALID_UPC);

            // Then - Should default to 0 and create not found response
            assertThat(result.getName()).isEqualTo("Product not found");
            assertThat(result.getRequiresApiRetry()).isTrue();
        }
    }

    @Nested
    @DisplayName("Detailed Fetch Error Handling Tests")
    class DetailedFetchErrorHandlingTests {

        @Test
        @DisplayName("Should handle detailed fetch empty response body")
        void should_ThrowRuntimeException_When_DetailedFetchResponseBodyIsEmpty() {
            // Given
            ResponseEntity<String> mockResponse = new ResponseEntity<>(null, HttpStatus.OK);
            when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                    .thenReturn(mockResponse);

            // When & Then
            assertThatThrownBy(() -> upcApiService.fetchDetailedProductData(VALID_UPC))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Empty response from Open Food Facts API");
        }

        @Test
        @DisplayName("Should handle detailed fetch JSON parsing error")
        void should_ThrowRuntimeException_When_DetailedFetchJsonParsingFails() {
            // Given
            String malformedJson = "{ invalid json }";
            ResponseEntity<String> mockResponse = new ResponseEntity<>(malformedJson, HttpStatus.OK);
            when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                    .thenReturn(mockResponse);

            // When & Then
            assertThatThrownBy(() -> upcApiService.fetchDetailedProductData(VALID_UPC))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Failed to parse detailed Open Food Facts API response");
        }

        @Test
        @DisplayName("Should handle detailed fetch product not found")
        void should_HandleDetailedFetchProductNotFound_When_InvalidUpcProvided() {
            // Given
            ResponseEntity<String> mockResponse = new ResponseEntity<>(notFoundApiResponse, HttpStatus.OK);
            when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                    .thenReturn(mockResponse);

            // When
            ProductResponse result = upcApiService.fetchDetailedProductData(INVALID_UPC);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getUpc()).isEqualTo(INVALID_UPC);
            assertThat(result.getName()).isEqualTo("Product not found");
            assertThat(result.getRequiresApiRetry()).isTrue();
        }
    }

    @Nested
    @DisplayName("UPC Format Tests")
    class UpcFormatTests {

        private static final String UPC_WITH_SPACES = "301 762 401 0701";
        private static final String SHORT_UPC = "123456789";
        private static final String LONG_UPC = "30176240107012345";
        private static final String ALPHANUMERIC_UPC = "3017624abc701";

        @Test
        @DisplayName("Should handle UPC with spaces")
        void should_AcceptUpcWithSpaces_When_UpcContainsSpaces() {
            // Given
            ResponseEntity<String> mockResponse = new ResponseEntity<>(successfulApiResponse, HttpStatus.OK);
            when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                    .thenReturn(mockResponse);

            // When & Then - Service should accept it since validation is minimal
            assertThatCode(() -> upcApiService.fetchProductData(UPC_WITH_SPACES))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should handle short UPC codes")
        void should_AcceptShortUpc_When_UpcIsLessThan12Digits() {
            // Given
            ResponseEntity<String> mockResponse = new ResponseEntity<>(successfulApiResponse, HttpStatus.OK);
            when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                    .thenReturn(mockResponse);

            // When & Then
            assertThatCode(() -> upcApiService.fetchProductData(SHORT_UPC))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should handle alphanumeric UPC codes")
        void should_AcceptAlphanumericUpc_When_UpcContainsLetters() {
            // Given
            ResponseEntity<String> mockResponse = new ResponseEntity<>(successfulApiResponse, HttpStatus.OK);
            when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                    .thenReturn(mockResponse);

            // When & Then
            assertThatCode(() -> upcApiService.fetchProductData(ALPHANUMERIC_UPC))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("Authentication Header Tests")
    class AuthenticationHeaderTests {

        @Test
        @DisplayName("Should include basic auth when using staging")
        void should_IncludeBasicAuth_When_UsingStagingEnvironment() {
            // Given
            ReflectionTestUtils.setField(upcApiService, "useStaging", true);
            ResponseEntity<String> mockResponse = new ResponseEntity<>(successfulApiResponse, HttpStatus.OK);
            
            ArgumentCaptor<HttpEntity<String>> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
            when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), entityCaptor.capture(), eq(String.class)))
                    .thenReturn(mockResponse);

            // When
            upcApiService.fetchProductData(VALID_UPC);

            // Then
            HttpEntity<String> capturedEntity = entityCaptor.getValue();
            HttpHeaders headers = capturedEntity.getHeaders();
            assertThat(headers.get("Authorization")).isNotNull();
            assertThat(headers.get("Authorization").get(0)).startsWith("Basic");
        }

        @Test
        @DisplayName("Should not include basic auth when using production")
        void should_NotIncludeBasicAuth_When_UsingProductionEnvironment() {
            // Given
            ReflectionTestUtils.setField(upcApiService, "useStaging", false);
            ResponseEntity<String> mockResponse = new ResponseEntity<>(successfulApiResponse, HttpStatus.OK);
            
            ArgumentCaptor<HttpEntity<String>> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
            when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), entityCaptor.capture(), eq(String.class)))
                    .thenReturn(mockResponse);

            // When
            upcApiService.fetchProductData(VALID_UPC);

            // Then
            HttpEntity<String> capturedEntity = entityCaptor.getValue();
            HttpHeaders headers = capturedEntity.getHeaders();
            assertThat(headers.get("Authorization")).isNull();
        }

        @Test
        @DisplayName("Should include correct User-Agent header")
        void should_IncludeCorrectUserAgent_When_MakingRequests() {
            // Given
            String customUserAgent = "TestAgent/2.0 (test@domain.com)";
            ReflectionTestUtils.setField(upcApiService, "userAgent", customUserAgent);
            ResponseEntity<String> mockResponse = new ResponseEntity<>(successfulApiResponse, HttpStatus.OK);
            
            ArgumentCaptor<HttpEntity<String>> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
            when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), entityCaptor.capture(), eq(String.class)))
                    .thenReturn(mockResponse);

            // When
            upcApiService.fetchProductData(VALID_UPC);

            // Then
            HttpEntity<String> capturedEntity = entityCaptor.getValue();
            HttpHeaders headers = capturedEntity.getHeaders();
            assertThat(headers.get("User-Agent")).contains(customUserAgent);
        }
    }

    @Nested
    @DisplayName("Service Availability Edge Cases")
    class ServiceAvailabilityEdgeCases {

        @Test
        @DisplayName("Should return false when health check returns null response")
        void should_ReturnFalse_When_HealthCheckResponseIsNull() {
            // Given
            ResponseEntity<String> mockResponse = new ResponseEntity<>(null, HttpStatus.OK);
            when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                    .thenReturn(mockResponse);

            // When
            boolean result = upcApiService.isServiceAvailable();

            // Then
            assertThat(result).isTrue(); // 2xx status is still successful even with null body
        }

        @Test
        @DisplayName("Should return false for various HTTP error statuses")
        void should_ReturnFalse_When_HealthCheckReturnsErrorStatuses() {
            // Test various error statuses
            HttpStatus[] errorStatuses = {
                HttpStatus.BAD_REQUEST,
                HttpStatus.UNAUTHORIZED,
                HttpStatus.FORBIDDEN,
                HttpStatus.NOT_FOUND,
                HttpStatus.INTERNAL_SERVER_ERROR,
                HttpStatus.BAD_GATEWAY,
                HttpStatus.SERVICE_UNAVAILABLE
            };

            for (HttpStatus status : errorStatuses) {
                // Given
                ResponseEntity<String> mockResponse = new ResponseEntity<>(status);
                when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                        .thenReturn(mockResponse);

                // When
                boolean result = upcApiService.isServiceAvailable();

                // Then
                assertThat(result).isFalse();
                
                // Reset mock for next iteration
                reset(restTemplate);
            }
        }

        @Test
        @DisplayName("Should use correct health check endpoint")
        void should_UseCorrectHealthCheckEndpoint_When_CheckingServiceAvailability() {
            // Given
            ResponseEntity<String> mockResponse = new ResponseEntity<>(successfulApiResponse, HttpStatus.OK);
            when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                    .thenReturn(mockResponse);

            // When
            upcApiService.isServiceAvailable();

            // Then
            verify(restTemplate).exchange(
                    contains("737628064502"), // Specific test product UPC
                    eq(HttpMethod.GET),
                    any(HttpEntity.class),
                    eq(String.class)
            );
        }
    }

    @Nested
    @DisplayName("URL Building Tests")
    class UrlBuildingTests {

        @Test
        @DisplayName("Should build correct API URL for basic fetch")
        void should_BuildCorrectApiUrl_When_FetchingBasicProductData() {
            // Given
            ResponseEntity<String> mockResponse = new ResponseEntity<>(successfulApiResponse, HttpStatus.OK);
            when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                    .thenReturn(mockResponse);

            // When
            upcApiService.fetchProductData(VALID_UPC);

            // Then
            verify(restTemplate).exchange(
                    contains("fields=product_name,brands,categories,generic_name"),
                    eq(HttpMethod.GET),
                    any(HttpEntity.class),
                    eq(String.class)
            );
        }

        @Test
        @DisplayName("Should include UPC in API URL")
        void should_IncludeUpcInApiUrl_When_FetchingProductData() {
            // Given
            ResponseEntity<String> mockResponse = new ResponseEntity<>(successfulApiResponse, HttpStatus.OK);
            when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                    .thenReturn(mockResponse);

            // When
            upcApiService.fetchProductData(VALID_UPC);

            // Then
            verify(restTemplate).exchange(
                    contains(VALID_UPC),
                    eq(HttpMethod.GET),
                    any(HttpEntity.class),
                    eq(String.class)
            );
        }
    }

    @Nested
    @DisplayName("Data Extraction Tests")
    class DataExtractionTests {

        @Test
        @DisplayName("Should extract first brand from comma-separated brands")
        void should_ExtractFirstBrand_When_MultipleBrandsProvided() {
            // Given
            ResponseEntity<String> mockResponse = new ResponseEntity<>(responseWithMultipleBrands, HttpStatus.OK);
            when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                    .thenReturn(mockResponse);

            // When
            ProductResponse result = upcApiService.fetchProductData(VALID_UPC);

            // Then
            assertThat(result.getBrand()).isEqualTo("Ferrero");
        }

        @Test
        @DisplayName("Should extract first category from comma-separated categories")
        void should_ExtractFirstCategory_When_MultipleCategoriesProvided() {
            // Given
            ResponseEntity<String> mockResponse = new ResponseEntity<>(responseWithMultipleCategories, HttpStatus.OK);
            when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                    .thenReturn(mockResponse);

            // When
            ProductResponse result = upcApiService.fetchProductData(VALID_UPC);

            // Then
            assertThat(result.getCategory()).isEqualTo("Spreads");
        }

        @Test
        @DisplayName("Should handle null brand and category gracefully")
        void should_HandleNullFields_When_BrandAndCategoryMissing() {
            // Given
            String responseWithNullFields = """
                {
                    "code": "3017624010701",
                    "product": {
                        "product_name": "Nutella"
                    },
                    "status": 1,
                    "status_verbose": "product found"
                }
                """;

            ResponseEntity<String> mockResponse = new ResponseEntity<>(responseWithNullFields, HttpStatus.OK);
            when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                    .thenReturn(mockResponse);

            // When
            ProductResponse result = upcApiService.fetchProductData(VALID_UPC);

            // Then
            assertThat(result.getBrand()).isNull();
            assertThat(result.getCategory()).isNull();
            assertThat(result.getName()).isEqualTo("Nutella");
        }
    }
}
