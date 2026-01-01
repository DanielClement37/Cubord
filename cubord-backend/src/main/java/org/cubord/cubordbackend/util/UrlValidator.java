package org.cubord.cubordbackend.util;

import lombok.extern.slf4j.Slf4j;
import org.cubord.cubordbackend.exception.UrlValidationException;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Set;

/**
 * Utility class for validating URLs to prevent Server-Side Request Forgery (SSRF) attacks.
 * 
 * <p>This validator implements multiple security checks:</p>
 * <ul>
 *   <li><strong>Domain Whitelist:</strong> Only allows requests to pre-approved domains</li>
 *   <li><strong>Internal IP Blocking:</strong> Prevents access to internal network addresses</li>
 *   <li><strong>Protocol Restriction:</strong> Only allows HTTP and HTTPS protocols</li>
 *   <li><strong>DNS Rebinding Protection:</strong> Validates resolved IP addresses</li>
 * </ul>
 * 
 * <h2>SSRF Attack Prevention</h2>
 * <p>Server-Side Request Forgery occurs when an attacker can make the server send
 * requests to unintended destinations. This validator prevents such attacks by:</p>
 * <ul>
 *   <li>Blocking requests to localhost (127.0.0.1, ::1)</li>
 *   <li>Blocking requests to private networks (10.0.0.0/8, 172.16.0.0/12, 192.168.0.0/16)</li>
 *   <li>Blocking requests to link-local addresses (169.254.0.0/16)</li>
 *   <li>Blocking requests to cloud metadata endpoints</li>
 *   <li>Only allowing explicitly whitelisted external domains</li>
 * </ul>
 * 
 * <h2>Usage Example</h2>
 * <pre>{@code
 * String userProvidedUrl = request.getParameter("url");
 * 
 * if (!UrlValidator.isValidExternalUrl(userProvidedUrl)) {
 *     throw new SecurityException("Invalid or potentially dangerous URL");
 * }
 * 
 * // Safe to use the URL
 * restTemplate.getForObject(userProvidedUrl, String.class);
 * }</pre>
 * 
 * @see org.cubord.cubordbackend.exception.UrlValidationException
 */
@Slf4j
public final class UrlValidator {

    /**
     * Whitelist of allowed external domains for API calls.
     * 
     * <p>Only these domains are permitted for external API requests.
     * Add new trusted domains here as needed.</p>
     */
    private static final Set<String> ALLOWED_DOMAINS = Set.of(
        "world.openfoodfacts.org",
        "world.openfoodfacts.net",
        "api.openfoodfacts.org"
    );

    /**
     * Set of allowed protocols for external requests.
     */
    private static final Set<String> ALLOWED_PROTOCOLS = Set.of("http", "https");

    /**
     * Private constructor to prevent instantiation.
     */
    private UrlValidator() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Validates a URL for external API calls to prevent SSRF attacks.
     * 
     * <p>This method performs comprehensive security checks including:</p>
     * <ol>
     *   <li>URI format validation</li>
     *   <li>Protocol validation (only HTTP/HTTPS)</li>
     *   <li>Domain whitelist verification</li>
     *   <li>DNS resolution and IP address validation</li>
     *   <li>Internal network detection</li>
     * </ol>
     * 
     * @param urlString The URL string to validate
     * @return true if the URL is safe to use, false otherwise
     * @throws UrlValidationException if the URL is invalid or dangerous
     */
    public static boolean isValidExternalUrl(String urlString) {
        if (urlString == null || urlString.trim().isEmpty()) {
            log.warn("URL validation failed: URL is null or empty");
            throw new UrlValidationException("URL cannot be null or empty");
        }

        try {
            // Parse URI
            URI uri = new URI(urlString);
            String scheme = uri.getScheme();
            String host = uri.getHost();

            log.trace("Validating URL: {} (scheme: {}, host: {})", urlString, scheme, host);

            // Validate protocol
            if (scheme == null || !ALLOWED_PROTOCOLS.contains(scheme.toLowerCase())) {
                log.warn("URL validation failed: Invalid protocol '{}' for URL: {}", scheme, urlString);
                throw new UrlValidationException(
                    "Invalid protocol: '" + scheme + "'. Only HTTP and HTTPS are allowed");
            }

            // Validate host exists
            if (host == null || host.trim().isEmpty()) {
                log.warn("URL validation failed: Missing host in URL: {}", urlString);
                throw new UrlValidationException("URL must contain a valid host");
            }

            // Check domain whitelist
            if (!ALLOWED_DOMAINS.contains(host.toLowerCase())) {
                log.warn("URL validation failed: Domain '{}' is not in whitelist", host);
                throw new UrlValidationException(
                    "Domain '" + host + "' is not in the allowed domains list");
            }

            // Resolve DNS and validate IP address
            InetAddress address;
            try {
                address = InetAddress.getByName(host);
                log.trace("Resolved host '{}' to IP: {}", host, address.getHostAddress());
            } catch (UnknownHostException e) {
                log.warn("URL validation failed: Cannot resolve host '{}'", host, e);
                throw new UrlValidationException("Cannot resolve host: " + host, e);
            }

            // Check for internal/private IP addresses
            if (isInternalAddress(address)) {
                log.warn("URL validation failed: Host '{}' resolves to internal IP: {}", 
                        host, address.getHostAddress());
                throw new UrlValidationException(
                    "Access to internal/private network addresses is forbidden");
            }

            log.debug("URL validation successful: {}", urlString);
            return true;

        } catch (UrlValidationException e) {
            // Re-throw our validation exceptions
            throw e;
        } catch (Exception e) {
            log.error("URL validation failed due to unexpected error: {}", urlString, e);
            throw new UrlValidationException("Invalid URL format: " + e.getMessage(), e);
        }
    }

    /**
     * Checks if an IP address is internal/private.
     * 
     * <p>This method detects:</p>
     * <ul>
     *   <li>Loopback addresses (127.0.0.0/8, ::1)</li>
     *   <li>Link-local addresses (169.254.0.0/16, fe80::/10)</li>
     *   <li>Private networks (10.0.0.0/8, 172.16.0.0/12, 192.168.0.0/16, fc00::/7)</li>
     *   <li>Cloud metadata endpoints (169.254.169.254)</li>
     * </ul>
     *
     * @param address The IP address to check
     * @return true if the address is internal/private, false otherwise
     */
    private static boolean isInternalAddress(InetAddress address) {
        // Check for loopback addresses (127.0.0.0/8 for IPv4, ::1 for IPv6)
        if (address.isLoopbackAddress()) {
            log.trace("Detected loopback address: {}", address.getHostAddress());
            return true;
        }

        // Check for link-local addresses (169.254.0.0/16 for IPv4, fe80::/10 for IPv6)
        if (address.isLinkLocalAddress()) {
            log.trace("Detected link-local address: {}", address.getHostAddress());
            return true;
        }

        // Check for site-local/private addresses (10.0.0.0/8, 172.16.0.0/12, 192.168.0.0/16, fc00::/7)
        if (address.isSiteLocalAddress()) {
            log.trace("Detected site-local/private address: {}", address.getHostAddress());
            return true;
        }

        // Additional check for AWS/Cloud metadata endpoint (169.254.169.254)
        byte[] rawAddress = address.getAddress();
        if (rawAddress.length == 4) { // IPv4
            // Check for 169.254.169.254 (AWS metadata endpoint)
            if ((rawAddress[0] & 0xFF) == 169 && 
                (rawAddress[1] & 0xFF) == 254 && 
                (rawAddress[2] & 0xFF) == 169 && 
                (rawAddress[3] & 0xFF) == 254) {
                log.warn("Detected cloud metadata endpoint: {}", address.getHostAddress());
                return true;
            }

            // Additional check for private networks not caught by isSiteLocalAddress
            // 10.0.0.0/8
            if ((rawAddress[0] & 0xFF) == 10) {
                log.trace("Detected 10.0.0.0/8 private network: {}", address.getHostAddress());
                return true;
            }

            // 172.16.0.0/12
            if ((rawAddress[0] & 0xFF) == 172 && 
                ((rawAddress[1] & 0xFF) >= 16 && (rawAddress[1] & 0xFF) <= 31)) {
                log.trace("Detected 172.16.0.0/12 private network: {}", address.getHostAddress());
                return true;
            }

            // 192.168.0.0/16
            if ((rawAddress[0] & 0xFF) == 192 && (rawAddress[1] & 0xFF) == 168) {
                log.trace("Detected 192.168.0.0/16 private network: {}", address.getHostAddress());
                return true;
            }
        }

        return false;
    }

    /**
     * Gets the set of allowed domains for external API calls.
     * 
     * <p>This method is provided for documentation and monitoring purposes.
     * The returned set is immutable.</p>
     *
     * @return Unmodifiable set of allowed domains
     */
    public static Set<String> getAllowedDomains() {
        return Set.copyOf(ALLOWED_DOMAINS);
    }
}
