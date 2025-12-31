
package org.cubord.cubordbackend.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cubord.cubordbackend.dto.user.UserResponse;
import org.cubord.cubordbackend.dto.user.UserUpdateRequest;
import org.cubord.cubordbackend.service.UserService;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * REST controller for user management operations.
 *
 * <p>This controller follows the modernized security architecture where:</p>
 * <ul>
 *   <li>Authentication is handled by Spring Security filters (JWT validation)</li>
 *   <li>Authorization is declarative via {@code @PreAuthorize} annotations</li>
 *   <li>No manual token validation or security checks in controller methods</li>
 *   <li>Business logic is delegated entirely to the service layer</li>
 * </ul>
 *
 * <h2>Authorization Rules</h2>
 * <ul>
 *   <li><strong>GET /me:</strong> Any authenticated user</li>
 *   <li><strong>GET /{id}:</strong> Own profile or shared household member</li>
 *   <li><strong>GET /username/{username}:</strong> Own profile or shared household member</li>
 *   <li><strong>PUT /{id}:</strong> Own profile only</li>
 *   <li><strong>PATCH /{id}:</strong> Own profile only</li>
 *   <li><strong>DELETE /{id}:</strong> Own account only</li>
 * </ul>
 *
 * <h2>Exception Handling</h2>
 * <p>All exceptions are handled by {@link org.cubord.cubordbackend.exception.RestExceptionHandler}
 * which provides consistent error responses with correlation IDs.</p>
 *
 * @see UserService
 * @see org.cubord.cubordbackend.security.SecurityService
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Validated
@Slf4j
public class UserController {

    private final UserService userService;

    /**
     * Retrieves the currently authenticated user's details.
     *
     * <p>This endpoint returns the profile of the user making the request,
     * identified via the JWT token in the Authorization header.</p>
     *
     * @return ResponseEntity with the current user's details
     */
    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<UserResponse> getCurrentUser() {
        log.debug("Retrieving current user details");

        UserResponse user = userService.getCurrentUserDetails();

        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(5, TimeUnit.MINUTES))
                .contentType(MediaType.APPLICATION_JSON)
                .body(user);
    }

    /**
     * Retrieves a user by their ID.
     *
     * <p>Authorization: User must be able to access the target profile
     * (own profile or shared household member).</p>
     *
     * @param id UUID of the user to retrieve
     * @return ResponseEntity with the user's details
     */
    @GetMapping("/{id}")
    @PreAuthorize("@security.canAccessUserProfile(#id)")
    public ResponseEntity<UserResponse> getUser(@PathVariable @NotNull UUID id) {
        log.debug("Retrieving user by ID: {}", id);

        UserResponse user = userService.getUser(id);

        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(5, TimeUnit.MINUTES))
                .contentType(MediaType.APPLICATION_JSON)
                .body(user);
    }

    /**
     * Retrieves a user by their username.
     *
     * <p>Authorization is checked in the service layer after resolving the username
     * to a user ID, since we need the ID for the permission check.</p>
     *
     * @param username Username of the user to retrieve (URL-encoded if special characters)
     * @return ResponseEntity with the user's details
     */
    @GetMapping("/username/{username}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<UserResponse> getUserByUsername(
            @PathVariable @NotBlank String username) {

        // Decode URL-encoded username to handle special characters
        String decodedUsername = URLDecoder.decode(username, StandardCharsets.UTF_8);
        log.debug("Retrieving user by username: {}", decodedUsername);

        // Service layer handles authorization after resolving username to ID
        UserResponse user = userService.getUserByUsername(decodedUsername);

        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(5, TimeUnit.MINUTES))
                .contentType(MediaType.APPLICATION_JSON)
                .body(user);
    }

    /**
     * Updates a user's profile completely.
     *
     * <p>Authorization: Users can only update their own profile.</p>
     *
     * @param id UUID of the user to update
     * @param updateRequest User update request with new details
     * @return ResponseEntity with the updated user's details
     */
    @PutMapping("/{id}")
    @PreAuthorize("@security.canModifyUserProfile(#id)")
    public ResponseEntity<UserResponse> updateUser(
            @PathVariable @NotNull UUID id,
            @RequestBody @Valid UserUpdateRequest updateRequest) {

        log.debug("Updating user: {}", id);

        UserResponse updatedUser = userService.updateUser(id, updateRequest);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(updatedUser);
    }

    /**
     * Partially updates a user's profile.
     *
     * <p>Authorization: Users can only patch their own profile.</p>
     *
     * <p>Supported fields: displayName, email, username</p>
     *
     * @param id UUID of the user to update
     * @param patchData Map of fields to update
     * @return ResponseEntity with the updated user's details
     */
    @PatchMapping("/{id}")
    @PreAuthorize("@security.canModifyUserProfile(#id)")
    public ResponseEntity<UserResponse> patchUser(
            @PathVariable @NotNull UUID id,
            @RequestBody Map<String, Object> patchData) {

        log.debug("Patching user {} with fields: {}", id, patchData.keySet());

        UserResponse patchedUser = userService.patchUser(id, patchData);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(patchedUser);
    }

    /**
     * Deletes a user account.
     *
     * <p>Authorization: Users can only delete their own account.</p>
     *
     * @param id UUID of the user to delete
     * @return ResponseEntity with no content (204)
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("@security.canModifyUserProfile(#id)")
    public ResponseEntity<Void> deleteUser(@PathVariable @NotNull UUID id) {
        log.debug("Deleting user: {}", id);

        userService.deleteUser(id);

        return ResponseEntity.noContent().build();
    }
}