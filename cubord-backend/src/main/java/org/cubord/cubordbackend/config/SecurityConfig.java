
package org.cubord.cubordbackend.config;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.RemoteJWKSet;
import com.nimbusds.jose.proc.JWSKeySelector;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import jakarta.servlet.http.HttpServletResponse;
import org.cubord.cubordbackend.security.HouseholdPermissionEvaluator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.net.URL;
import java.util.Arrays;
import java.util.List;

/**
 * Security configuration for the Cubord application.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(securedEnabled = true)
public class SecurityConfig {

    private static final Logger logger = LoggerFactory.getLogger(SecurityConfig.class);

    private static final String AUTHORITIES_CLAIM_NAME = "role";
    private static final String AUTHORITY_PREFIX = "ROLE_";
    private static final String PRINCIPAL_CLAIM_NAME = "sub";

    private final String jwtJwkSetUri;
    private final String jwtIssuerUri;
    private final Environment env;
    private final HouseholdPermissionEvaluator householdPermissionEvaluator;

    public SecurityConfig(
            @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}") String jwtJwkSetUri,
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri:}") String jwtIssuerUri,
            Environment env,
            HouseholdPermissionEvaluator householdPermissionEvaluator) {
        this.jwtJwkSetUri = jwtJwkSetUri;
        this.jwtIssuerUri = jwtIssuerUri;
        this.env = env;
        this.householdPermissionEvaluator = householdPermissionEvaluator;
    }

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
     * Creates a JWT decoder configured with Supabase's JWKS endpoint for ES256.
     *
     * <p>Supabase uses ES256 (ECDSA with P-256) for JWT signing. The default
     * NimbusJwtDecoder only supports RSA algorithms, so we must explicitly
     * configure it for ES256.</p>
     */
    @Bean
    public JwtDecoder jwtDecoder() {
        logger.info("Configuring JWT Decoder with JWKS endpoint for ES256: {}", jwtJwkSetUri);

        try {
            // Create a remote JWK source
            JWKSource<SecurityContext> jwkSource = new RemoteJWKSet<>(new URL(jwtJwkSetUri));

            // Configure for ES256 algorithm (what Supabase uses)
            JWSKeySelector<SecurityContext> jwsKeySelector =
                    new JWSVerificationKeySelector<>(JWSAlgorithm.ES256, jwkSource);

            DefaultJWTProcessor<SecurityContext> jwtProcessor = new DefaultJWTProcessor<>();
            jwtProcessor.setJWSKeySelector(jwsKeySelector);

            NimbusJwtDecoder jwtDecoder = new NimbusJwtDecoder(jwtProcessor);

            // Add issuer validation if configured
            if (jwtIssuerUri != null && !jwtIssuerUri.isBlank()) {
                jwtDecoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(jwtIssuerUri));
            }

            return jwtDecoder;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to configure JWT decoder with JWKS endpoint: " + jwtJwkSetUri, e);
        }
    }

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

    @Bean
    public MethodSecurityExpressionHandler methodSecurityExpressionHandler() {
        DefaultMethodSecurityExpressionHandler expressionHandler = new DefaultMethodSecurityExpressionHandler();
        expressionHandler.setPermissionEvaluator(householdPermissionEvaluator);
        return expressionHandler;
    }

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