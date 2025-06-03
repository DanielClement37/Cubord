package org.cubord.cubordbackend.config;

import io.micrometer.common.util.StringUtils;
import org.cubord.cubordbackend.security.HouseholdPermissionEvaluator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final Logger logger = LoggerFactory.getLogger(SecurityConfig.class);

    @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}")
    private String jwkSetUri;
    
    @Value("${JWT_SECRET}")
    private String jwtSecret;

    @Autowired
    private Environment env;

    private final HouseholdPermissionEvaluator householdPermissionEvaluator;

    /**
     * Creates a new SecurityConfig with the specified permission evaluator.
     * 
     * @param householdPermissionEvaluator Evaluator for household-related permissions
     */
    public SecurityConfig(HouseholdPermissionEvaluator householdPermissionEvaluator) {
        this.householdPermissionEvaluator = householdPermissionEvaluator;
    }

    /**
     * Configures the security filter chain for the application.
     * 
     * @param http HttpSecurity to be configured
     * @return Configured SecurityFilterChain
     * @throws Exception if configuration fails
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/public/**", "/actuator/**").permitAll()
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.decoder(jwtDecoder())
                                      .jwtAuthenticationConverter(jwtAuthenticationConverter()))
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.getWriter().write("{\"error\":\"Unauthorized\",\"message\":\"" 
                                    + authException.getMessage() + "\"}");
                        })
                );

        return http.build();
    }

    /**
     * Creates a JWT decoder configured with the application's secret key.
     * 
     * @return JwtDecoder for validating JWT tokens
     * @throws IllegalStateException if JWT_SECRET environment variable is not set
     */
    @Bean
    public JwtDecoder jwtDecoder() {
        logger.info("Configuring JWT Decoder with HS256 algorithm");
        
        if (StringUtils.isBlank(jwtSecret)) {
            throw new IllegalStateException("JWT_SECRET environment variable is not set");
        }
        
        logger.debug("JWT Secret length: {}", jwtSecret.length());

        byte[] secretBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        SecretKey key = new SecretKeySpec(secretBytes, "HmacSHA256");

        return NimbusJwtDecoder.withSecretKey(key)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }

    /**
     * Configures CORS settings for the application.
     * 
     * @return CorsConfigurationSource with configured origins and methods
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        String allowedOrigins = env.getProperty("ALLOWED_ORIGINS", "http://localhost:3000");
        configuration.setAllowedOriginPatterns(Arrays.asList(allowedOrigins.split(",")));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "X-Requested-With"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    /**
     * Creates a JWT authentication converter for extracting authorities and principal.
     * 
     * @return JwtAuthenticationConverter configured for the application
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter grantedAuthoritiesConverter = new JwtGrantedAuthoritiesConverter();
        grantedAuthoritiesConverter.setAuthoritiesClaimName("role");
        grantedAuthoritiesConverter.setAuthorityPrefix("ROLE_");

        JwtAuthenticationConverter jwtAuthenticationConverter = new JwtAuthenticationConverter();
        jwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter(grantedAuthoritiesConverter);
        jwtAuthenticationConverter.setPrincipalClaimName("sub");

        return jwtAuthenticationConverter;
    }

    /**
     * Configures method security expression handling with custom permission evaluator.
     * 
     * @return MethodSecurityExpressionHandler with household permissions
     */
    @Bean
    public MethodSecurityExpressionHandler methodSecurityExpressionHandler() {
        DefaultMethodSecurityExpressionHandler expressionHandler = new DefaultMethodSecurityExpressionHandler();
        expressionHandler.setPermissionEvaluator(householdPermissionEvaluator);
        return expressionHandler;
    }
}