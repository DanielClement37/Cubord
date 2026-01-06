package org.cubord.cubordbackend.util;

import org.cubord.cubordbackend.exception.UrlValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Unit tests for UrlValidator.
 * 
 * <p>These tests verify SSRF protection mechanisms including:</p>
 * <ul>
 *   <li>Valid external URLs are accepted</li>
 *   <li>Internal/private IP addresses are blocked</li>
 *   <li>Invalid protocols are rejected</li>
 *   <li>Domain whitelist is enforced</li>
 *   <li>Malformed URLs are rejected</li>
 * </ul>
 */
@SuppressWarnings("HttpUrlsUsage")
class UrlValidatorTest {

    // ==================== Valid URL Tests ====================

    @Test
    void testValidExternalUrl_AcceptsWhitelistedDomain() {
        // Given
        String validUrl = "https://world.openfoodfacts.org/api/v2/product/123456.json";

        // When/Then
        assertDoesNotThrow(() -> UrlValidator.isValidExternalUrl(validUrl));
        assertThat(UrlValidator.isValidExternalUrl(validUrl)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "https://world.openfoodfacts.org/api/v2/product/123.json",
        "http://world.openfoodfacts.org/api/v2/product/456.json",
        "https://world.openfoodfacts.net/api/v2/product/789.json",
        "https://api.openfoodfacts.org/v2/product/012.json"
    })
    void testValidExternalUrl_AcceptsAllWhitelistedDomains(String url) {
        // When/Then
        assertDoesNotThrow(() -> UrlValidator.isValidExternalUrl(url));
        assertThat(UrlValidator.isValidExternalUrl(url)).isTrue();
    }

    // ==================== Null/Empty URL Tests ====================

    @Test
    void testValidExternalUrl_RejectsNullUrl() {
        // When/Then
        assertThatThrownBy(() -> UrlValidator.isValidExternalUrl(null))
                .isInstanceOf(UrlValidationException.class)
                .hasMessageContaining("cannot be null or empty");
    }

    @Test
    void testValidExternalUrl_RejectsEmptyUrl() {
        // When/Then
        assertThatThrownBy(() -> UrlValidator.isValidExternalUrl(""))
                .isInstanceOf(UrlValidationException.class)
                .hasMessageContaining("cannot be null or empty");
    }

    @Test
    void testValidExternalUrl_RejectsWhitespaceUrl() {
        // When/Then
        assertThatThrownBy(() -> UrlValidator.isValidExternalUrl("   "))
                .isInstanceOf(UrlValidationException.class)
                .hasMessageContaining("cannot be null or empty");
    }

    // ==================== Protocol Tests ====================

    @ParameterizedTest
    @ValueSource(strings = {
        "ftp://world.openfoodfacts.org/api/v2/product/123.json",
        "file://world.openfoodfacts.org/api/v2/product/123.json",
        "javascript://world.openfoodfacts.org/api/v2/product/123.json",
        "data://world.openfoodfacts.org/api/v2/product/123.json"
    })
    void testValidExternalUrl_RejectsInvalidProtocols(String url) {
        // When/Then
        assertThatThrownBy(() -> UrlValidator.isValidExternalUrl(url))
                .isInstanceOf(UrlValidationException.class)
                .hasMessageContaining("Invalid protocol");
    }

    @Test
    void testValidExternalUrl_RejectsMissingProtocol() {
        // Given
        String url = "world.openfoodfacts.org/api/v2/product/123.json";

        // When/Then
        assertThatThrownBy(() -> UrlValidator.isValidExternalUrl(url))
                .isInstanceOf(UrlValidationException.class);
    }

    // ==================== Domain Whitelist Tests ====================

    @ParameterizedTest
    @ValueSource(strings = {
        "https://evil.com/api/v2/product/123.json",
        "https://google.com/search",
        "https://example.com/test",
        "https://192.168.1.1/admin"
    })
    void testValidExternalUrl_RejectsNonWhitelistedDomains(String url) {
        // When/Then
        assertThatThrownBy(() -> UrlValidator.isValidExternalUrl(url))
                .isInstanceOf(UrlValidationException.class)
                .hasMessageContaining("not in the allowed domains list");
    }

    // ==================== Internal IP Address Tests ====================

    @ParameterizedTest
    @ValueSource(strings = {
        "http://127.0.0.1/admin",
        "http://127.0.0.1:8080/api",
        "http://localhost/admin",
        "http://localhost:8080/api"
    })
    void testValidExternalUrl_RejectsLoopbackAddresses(String url) {
        // Note: These will fail on domain whitelist first, but demonstrates protection
        assertThatThrownBy(() -> UrlValidator.isValidExternalUrl(url))
                .isInstanceOf(UrlValidationException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "http://10.0.0.1/admin",
        "http://10.255.255.255/admin",
        "http://172.16.0.1/admin",
        "http://172.31.255.255/admin",
        "http://192.168.0.1/admin",
        "http://192.168.255.255/admin"
    })
    void testValidExternalUrl_RejectsPrivateNetworkAddresses(String url) {
        // Note: These will fail on domain whitelist first, but demonstrates protection
        assertThatThrownBy(() -> UrlValidator.isValidExternalUrl(url))
                .isInstanceOf(UrlValidationException.class);
    }

    @Test
    void testValidExternalUrl_RejectsCloudMetadataEndpoint() {
        // Given - AWS metadata endpoint
        String url = "http://169.254.169.254/latest/meta-data/";

        // When/Then
        assertThatThrownBy(() -> UrlValidator.isValidExternalUrl(url))
                .isInstanceOf(UrlValidationException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "http://169.254.0.1/admin",
        "http://169.254.255.255/admin"
    })
    void testValidExternalUrl_RejectsLinkLocalAddresses(String url) {
        // Note: These will fail on domain whitelist first, but demonstrates protection
        assertThatThrownBy(() -> UrlValidator.isValidExternalUrl(url))
                .isInstanceOf(UrlValidationException.class);
    }

    // ==================== Malformed URL Tests ====================

    @ParameterizedTest
    @ValueSource(strings = {
        "not a url",
        "http://",
        "https://",
        "://world.openfoodfacts.org",
        "http:/world.openfoodfacts.org"
    })
    void testValidExternalUrl_RejectsMalformedUrls(String url) {
        // When/Then
        assertThatThrownBy(() -> UrlValidator.isValidExternalUrl(url))
                .isInstanceOf(UrlValidationException.class);
    }

    // ==================== Edge Cases ====================

    @Test
    void testValidExternalUrl_AcceptsUrlWithQueryParameters() {
        // Given
        String url = "https://world.openfoodfacts.org/api/v2/product/123.json?fields=product_name,brands";

        // When/Then
        assertDoesNotThrow(() -> UrlValidator.isValidExternalUrl(url));
        assertThat(UrlValidator.isValidExternalUrl(url)).isTrue();
    }

    @Test
    void testValidExternalUrl_AcceptsUrlWithFragment() {
        // Given
        String url = "https://world.openfoodfacts.org/api/v2/product/123.json#section";

        // When/Then
        assertDoesNotThrow(() -> UrlValidator.isValidExternalUrl(url));
        assertThat(UrlValidator.isValidExternalUrl(url)).isTrue();
    }

    @Test
    void testValidExternalUrl_AcceptsUrlWithPort() {
        // Given - Though unusual, if whitelisted domain has port
        String url = "https://world.openfoodfacts.org:443/api/v2/product/123.json";

        // When/Then
        assertDoesNotThrow(() -> UrlValidator.isValidExternalUrl(url));
        assertThat(UrlValidator.isValidExternalUrl(url)).isTrue();
    }

    @Test
    void testValidExternalUrl_IsCaseInsensitive() {
        // Given
        String url = "HTTPS://WORLD.OPENFOODFACTS.ORG/api/v2/product/123.json";

        // When/Then
        assertDoesNotThrow(() -> UrlValidator.isValidExternalUrl(url));
        assertThat(UrlValidator.isValidExternalUrl(url)).isTrue();
    }

    // ==================== Utility Method Tests ====================

    @Test
    void testGetAllowedDomains_ReturnsNonEmptySet() {
        // When
        var allowedDomains = UrlValidator.getAllowedDomains();

        // Then
        assertThat(allowedDomains).isNotEmpty();
        assertThat(allowedDomains).contains("world.openfoodfacts.org");
    }

    @Test
    void testGetAllowedDomains_ReturnsImmutableSet() {
        // When
        var allowedDomains = UrlValidator.getAllowedDomains();

        // Then
        assertThatThrownBy(() -> allowedDomains.add("evil.com"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void testUrlValidator_CannotBeInstantiated() {
        // When/Then
        assertThatThrownBy(() -> {
            var constructor = UrlValidator.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            constructor.newInstance();
        })
        .hasCauseInstanceOf(UnsupportedOperationException.class)
        .hasStackTraceContaining("Utility class cannot be instantiated");
    }
}
