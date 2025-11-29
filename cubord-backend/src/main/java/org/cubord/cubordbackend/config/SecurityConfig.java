package org.cubord.cubordbackend.config;

import io.micrometer.common.util.StringUtils;
import org.cubord.cubordbackend.security.HouseholdPermissionEvaluator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import java.util.List;

/**
 * Security configuration for the Cubord application.
 *
 * <p>This configuration establishes a layered security architecture:</p>
 * <ol>
 *   <li><strong>Transport Security:</strong> JWT-based stateless authentication via OAuth2 Resource Server</li>
 *   <li><strong>Method Security:</strong> Declarative authorization using @PreAuthorize/@PostAuthorize</li>
 *   <li><strong>Custom Permissions:</strong> Domain-specific authorization via {@link HouseholdPermissionEvaluator}</li>
 * </ol>
 *
 * <p>Authorization is handled declaratively at the service layer, keeping controllers
 * and services free from manual token validation and permission checks.</p>
 *
 * @see HouseholdPermissionEvaluator
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(securedEnabled = true)
public class SecurityConfig {

    private static final Logger logger = LoggerFactory.getLogger(SecurityConfig.class);

    /**
     * Claim name in JWT for role/authority extraction.
     */
    private static final String AUTHORITIES_CLAIM_NAME = "role";
    
    /**
     * Prefix applied to authorities extracted from JWT.
     */
    private static final String AUTHORITY_PREFIX = "ROLE_";
    
    /**
     * Claim name for extracting the principal (user identifier).
     */
    private static final String PRINCIPAL_CLAIM_NAME = "sub";

    private final String jwtSecret;
    private final Environment env;
    private final HouseholdPermissionEvaluator householdPermissionEvaluator;

    /**
     * Creates a new SecurityConfig with required dependencies.
     * 
     * @param jwtSecret Secret key for JWT validation (injected from JWT_SECRET env var)
     * @param env Spring environment for configuration property access
     * @param householdPermissionEvaluator Evaluator for household-related permissions
     */
    public SecurityConfig(
            @Value("${JWT_SECRET}") String jwtSecret,
            Environment env,
            HouseholdPermissionEvaluator householdPermissionEvaluator) {
        this.jwtSecret = jwtSecret;
        this.env = env;
        this.householdPermissionEvaluator = householdPermissionEvaluator;
    }

    /**
     * Configures the main security filter chain for the application.
     * 
     * <p>Security rules:</p>
     * <ul>
     *   <li>CSRF disabled (stateless API, tokens provide CSRF protection)</li>
     *   <li>Stateless session management (no server-side session)</li>
     *   <li>Public endpoints: /api/public/**, /actuator/health, /actuator/info</li>
     *   <li>All other endpoints require authentication</li>
     * </ul>
     * 
     * @param http HttpSecurity to be configured
     * @return Configured SecurityFilterChain
     * @throws Exception if configuration fails
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(Customizer.withDefaults())
            .sessionManagement(session -> 
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                .requestMatchers("/api/public/**").permitAll()
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt
                    .decoder(jwtDecoder())
                    .jwtAuthenticationConverter(jwtAuthenticationConverter()))
                .authenticationEntryPoint(this::handleAuthenticationFailure)
            );

        return http.build();
    }

    /**
     * Creates a JWT decoder configured with the application's secret key.
     * 
     * <p>Uses HS256 (HMAC with SHA-256) symmetric algorithm for token validation.</p>
     * 
     * @return JwtDecoder for validating JWT tokens
     * @throws IllegalStateException if JWT_SECRET environment variable is not set or blank
     */
    @Bean
    public JwtDecoder jwtDecoder() {
        logger.info("Configuring JWT Decoder with HS256 algorithm");
        
        if (StringUtils.isBlank(jwtSecret)) {
            throw new IllegalStateException(
                "JWT_SECRET environment variable is not set. " +
                "Please configure a secure secret key for JWT validation.");
        }
        
        logger.debug("JWT Secret configured with length: {}", jwtSecret.length());

        byte[] secretBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        SecretKey key = new SecretKeySpec(secretBytes, "HmacSHA256");

        return NimbusJwtDecoder.withSecretKey(key)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }

    /**
     * Creates a JWT authentication converter for extracting authorities and principal.
     * 
     * <p>Extracts:</p>
     * <ul>
     *   <li>Principal from the "sub" claim</li>
     *   <li>Authorities from the "role" claim with "ROLE_" prefix</li>
     * </ul>
     * 
     * @return JwtAuthenticationConverter configured for the application
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter grantedAuthoritiesConverter = new JwtGrantedAuthoritiesConverter();
        grantedAuthoritiesConverter.setAuthoritiesClaimName(AUTHORITIES_CLAIM_NAME);
        grantedAuthoritiesConverter.setAuthorityPrefix(AUTHORITY_PREFIX);

        JwtAuthenticationConverter jwtAuthenticationConverter = new JwtAuthenticationConverter();
        jwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter(grantedAuthoritiesConverter);
        jwtAuthenticationConverter.setPrincipalClaimName(PRINCIPAL_CLAIM_NAME);

        return jwtAuthenticationConverter;
    }

    /**
     * Configures method security expression handling with custom permission evaluator.
     * 
     * <p>Enables SpEL expressions in @PreAuthorize/@PostAuthorize annotations:</p>
     * <ul>
     *   <li>{@code hasPermission(#householdId, 'Household', 'ADMIN')} - Check household admin role</li>
     *   <li>{@code @householdPermissionEvaluator.hasViewPermission(authentication, #id)} - Direct bean call</li>
     * </ul>
     * 
     * @return MethodSecurityExpressionHandler configured with household permissions
     */
    @Bean
    public MethodSecurityExpressionHandler methodSecurityExpressionHandler() {
        DefaultMethodSecurityExpressionHandler expressionHandler = new DefaultMethodSecurityExpressionHandler();
        expressionHandler.setPermissionEvaluator(householdPermissionEvaluator);
        return expressionHandler;
    }

    /**
     * Configures CORS settings for the application.
     * 
     * <p>Allowed origins are configured via ALLOWED_ORIGINS environment variable
     * (comma-separated), defaulting to localhost:3000 for development.</p>
     * 
     * @return CorsConfigurationSource with configured origins and methods
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        String allowedOrigins = env.getProperty("ALLOWED_ORIGINS", "http://localhost:3000");
        List<String> originPatterns = Arrays.asList(allowedOrigins.split(","));
        configuration.setAllowedOriginPatterns(originPatterns);
        
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "X-Requested-With"));
        configuration.setExposedHeaders(Arrays.asList("X-Total-Count", "X-Page-Number", "X-Page-Size"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        
        logger.info("CORS configured with allowed origins: {}", originPatterns);
        return source;
    }

    /**
     * Handles authentication failures with a standardized JSON error response.
     * 
     * @param request The HTTP request
     * @param response The HTTP response
     * @param authException The authentication exception that occurred
     */
    private void handleAuthenticationFailure(
            jakarta.servlet.http.HttpServletRequest request,
            HttpServletResponse response,
            org.springframework.security.core.AuthenticationException authException) throws java.io.IOException {
        
        logger.debug("Authentication failed for request to {}: {}", 
            request.getRequestURI(), authException.getMessage());
        
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(
            "{\"error\":\"Unauthorized\",\"message\":\"Authentication required to access this resource\"}");
    }
}