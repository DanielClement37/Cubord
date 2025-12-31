package org.cubord.cubordbackend.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.firewall.HttpFirewall;
import org.springframework.security.web.firewall.StrictHttpFirewall;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;


/**
 * Test security configuration that enables JWT authentication and method security for tests
 * but does not enforce token validation.
 */
@TestConfiguration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)  // CRITICAL: Enable @PreAuthorize support
public class TestSecurityConfig {
    private static final Logger logger = LoggerFactory.getLogger(TestSecurityConfig.class);

    /**
     * Configure security for tests to use JWT authentication but without
     * strict validation.
     */
    @Bean
    @Primary
    public SecurityFilterChain testFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/public/**", "/actuator/**").permitAll()
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.decoder(mockJwtDecoder())));
        
        return http.build();
    }

    /**
     * Custom JWT validator that strictly checks token expiration.
     */
    static class StrictExpiryValidator implements OAuth2TokenValidator<Jwt> {
        private final OAuth2Error error = new OAuth2Error(
                "invalid_token",
                "The token has expired",
                null
        );

        @Override
        public OAuth2TokenValidatorResult validate(Jwt jwt) {
            Instant now = Instant.now();
            if (jwt.getExpiresAt() == null || jwt.getExpiresAt().isBefore(now)) {
                logger.debug("Token expiry validation failed. Token expires at: {}, current time: {}", 
                        jwt.getExpiresAt(), now);
                return OAuth2TokenValidatorResult.failure(error);
            }
            
            logger.debug("Token expiry validation passed. Token expires at: {}, current time: {}", 
                    jwt.getExpiresAt(), now);
            return OAuth2TokenValidatorResult.success();
        }
    }
    
    /**
     * Mock JWT decoder that properly validates tokens in test environment.
     * This decoder respects token expiration but ignores signature validation.
     */
    @Bean
    @Primary
    public JwtDecoder mockJwtDecoder() {
        // Use JWK uri that won't be called but is required for configuration
        NimbusJwtDecoder jwtDecoder = NimbusJwtDecoder.withJwkSetUri("https://example.com/.well-known/jwks.json")
                .build();
        
        // Create a validator that only checks token expiration, ignoring other validations
        OAuth2TokenValidator<Jwt> expiryValidator = new StrictExpiryValidator();
        
        // Set our validator
        jwtDecoder.setJwtValidator(expiryValidator);
        
        return jwtDecoder;
    }
    
    /**
     * Configure HTTP firewall to allow URL encoded characters in paths.
     */
    @Bean
    @Primary
    public HttpFirewall allowUrlEncodedHttpFirewall() {
        StrictHttpFirewall firewall = new StrictHttpFirewall();
        firewall.setAllowUrlEncodedPercent(true);
        firewall.setAllowSemicolon(true);
        firewall.setAllowUrlEncodedPeriod(true);
        firewall.setAllowUrlEncodedSlash(true);
        firewall.setAllowBackSlash(true);
        firewall.setAllowUrlEncodedDoubleSlash(true);
        return firewall;
    }
    
    /**
     * Configure CORS for test environment.
     */
    @Bean
    @Primary
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(Collections.singletonList("http://localhost:3000"));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "X-Requested-With"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}