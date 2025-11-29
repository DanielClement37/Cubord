package org.cubord.cubordbackend.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Production implementation of {@link SecurityContextProvider} that delegates
 * to Spring Security's {@link SecurityContextHolder}.
 * 
 * <p>This implementation uses {@link SecurityContextHolder} which maintains
 * the security context using a thread-local storage strategy. This means each
 * thread has its own isolated security context.</p>
 * 
 * <h2>Thread Safety</h2>
 * <p>This implementation is thread-safe as it relies on {@link SecurityContextHolder}'s
 * thread-local storage. Each thread accessing the security context gets its own
 * isolated copy.</p>
 * 
 * <h2>Spring Integration</h2>
 * <p>This bean is automatically registered as a Spring component and can be
 * injected into any service or component that needs access to the security context.</p>
 *
 * @see SecurityContextProvider
 * @see SecurityContextHolder
 */
@Component
@Slf4j
public class SpringSecurityContextProvider implements SecurityContextProvider {
    
    /**
     * Gets the current authentication from Spring Security's SecurityContextHolder.
     * 
     * <p>The security context is maintained in thread-local storage, so this method
     * returns the authentication for the current thread.</p>
     * 
     * <p>If no security context exists or the context contains no authentication,
     * this method returns {@code null}.</p>
     *
     * @return The current Authentication from the security context, or null if not present
     */
    @Override
    public Authentication getAuthentication() {
        SecurityContext context = SecurityContextHolder.getContext();
        
        if (context == null) {
            log.trace("No SecurityContext found in SecurityContextHolder");
            return null;
        }
        
        Authentication authentication = context.getAuthentication();
        
        if (log.isTraceEnabled()) {
            if (authentication != null) {
                log.trace("Retrieved authentication for principal: {} (authenticated: {})",
                        authentication.getName(), authentication.isAuthenticated());
            } else {
                log.trace("No authentication found in SecurityContext");
            }
        }
        
        return authentication;
    }
    
    /**
     * Checks if the current thread has an authenticated user.
     * 
     * <p>This method is optimized to avoid unnecessary object creation
     * by directly checking the security context.</p>
     *
     * @return true if there is an authenticated user in the current thread's context
     */
    @Override
    public boolean isAuthenticated() {
        SecurityContext context = SecurityContextHolder.getContext();
        
        if (context == null) {
            return false;
        }
        
        Authentication authentication = context.getAuthentication();
        boolean authenticated = authentication != null && authentication.isAuthenticated();
        
        log.trace("Authentication check result: {}", authenticated);
        
        return authenticated;
    }
}
