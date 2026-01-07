
package org.cubord.cubordbackend.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cubord.cubordbackend.dto.householdMember.HouseholdMemberRequest;
import org.cubord.cubordbackend.dto.householdMember.HouseholdMemberResponse;
import org.cubord.cubordbackend.dto.householdMember.HouseholdMemberRoleUpdateRequest;
import org.cubord.cubordbackend.service.HouseholdMemberService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for household member management operations.
 *
 * <h2>Authorization Rules</h2>
 * <ul>
 *   <li><strong>POST /members:</strong> Requires an OWNER or ADMIN role in a household</li>
 *   <li><strong>GET /members:</strong> Any household member can view all members</li>
 *   <li><strong>GET /members/{memberId}:</strong> Any household member can view member details</li>
 *   <li><strong>DELETE /members/{memberId}:</strong> Requires an OWNER or ADMIN role (with restrictions)</li>
 *   <li><strong>PUT /members/{memberId}/role:</strong> Requires an OWNER or ADMIN role (with restrictions)</li>
 * </ul>
 *
 * <h2>Exception Handling</h2>
 * <p>All exceptions are handled by {@link org.cubord.cubordbackend.exception.RestExceptionHandler}
 * which provides consistent error responses with correlation IDs.</p>
 *
 * @see HouseholdMemberService
 * @see org.cubord.cubordbackend.security.SecurityService
 */
@RestController
@RequestMapping("/api/households/{householdId}/members")
@RequiredArgsConstructor
@Validated
@Slf4j
public class HouseholdMemberController {

    private final HouseholdMemberService householdMemberService;

    /**
     * Adds a new member to a household.
     *
     * <p>Authorization: User must have an OWNER or ADMIN role in the household.</p>
     *
     * @param householdId The UUID of the household
     * @param request DTO containing member information (userId and role)
     * @return ResponseEntity containing the created member's details with HTTP 201 status
     */
    @PostMapping
    @PreAuthorize("@security.canManageHouseholdMembers(#householdId)")
    public ResponseEntity<HouseholdMemberResponse> addMember(
            @PathVariable @NotNull UUID householdId,
            @Valid @RequestBody HouseholdMemberRequest request) {

        log.debug("Adding member to household: {}", householdId);

        HouseholdMemberResponse response = householdMemberService.addMemberToHousehold(householdId, request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .contentType(MediaType.APPLICATION_JSON)
                .body(response);
    }

    /**
     * Retrieves all members of a household.
     *
     * <p>Authorization: User must be a member of the household.</p>
     *
     * @param householdId The UUID of the household
     * @return ResponseEntity containing a list of household members
     */
    @GetMapping
    @PreAuthorize("@security.canAccessHousehold(#householdId)")
    public ResponseEntity<List<HouseholdMemberResponse>> getHouseholdMembers(
            @PathVariable @NotNull UUID householdId) {

        log.debug("Retrieving members for household: {}", householdId);

        List<HouseholdMemberResponse> members = householdMemberService.getHouseholdMembers(householdId);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(members);
    }

    /**
     * Retrieves a specific member by ID.
     *
     * <p>Authorization: User must be a member of the household.</p>
     *
     * @param householdId The UUID of the household
     * @param memberId The UUID of the member
     * @return ResponseEntity containing the member's details
     */
    @GetMapping("/{memberId}")
    @PreAuthorize("@security.canAccessHousehold(#householdId)")
    public ResponseEntity<HouseholdMemberResponse> getMemberById(
            @PathVariable @NotNull UUID householdId,
            @PathVariable @NotNull UUID memberId) {

        log.debug("Retrieving member {} from household: {}", memberId, householdId);

        HouseholdMemberResponse response = householdMemberService.getMemberById(householdId, memberId);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(response);
    }

    /**
     * Removes a member from a household.
     *
     * <p>Authorization: User must have an OWNER or ADMIN role in the household.</p>
     *
     * <p>Business rules:</p>
     * <ul>
     *   <li>Cannot remove the OWNER</li>
     *   <li>ADMIN cannot remove another ADMIN</li>
     * </ul>
     *
     * @param householdId The UUID of the household
     * @param memberId The UUID of the member to remove
     * @return ResponseEntity with no content (HTTP 204)
     */
    @DeleteMapping("/{memberId}")
    @PreAuthorize("@security.canManageHouseholdMembers(#householdId)")
    public ResponseEntity<Void> removeMember(
            @PathVariable @NotNull UUID householdId,
            @PathVariable @NotNull UUID memberId) {

        log.debug("Removing member {} from household: {}", memberId, householdId);

        householdMemberService.removeMember(householdId, memberId);

        return ResponseEntity.noContent().build();
    }

    /**
     * Updates a member's role in a household.
     *
     * <p>Authorization: User must have an OWNER or ADMIN role in the household.</p>
     *
     * <p>Business rules:</p>
     * <ul>
     *   <li>Cannot set a role to OWNER (use ownership transfer instead)</li>
     *   <li>ADMIN cannot update another ADMIN's role</li>
     * </ul>
     *
     * @param householdId The UUID of the household
     * @param memberId The UUID of the member
     * @param request DTO containing the new role information
     * @return ResponseEntity containing the updated member's details
     */
    @PutMapping("/{memberId}/role")
    @PreAuthorize("@security.canManageHouseholdMembers(#householdId)")
    public ResponseEntity<HouseholdMemberResponse> updateMemberRole(
            @PathVariable @NotNull UUID householdId,
            @PathVariable @NotNull UUID memberId,
            @Valid @RequestBody HouseholdMemberRoleUpdateRequest request) {

        log.debug("Updating role for member {} in household: {}", memberId, householdId);

        HouseholdMemberResponse response = householdMemberService.updateMemberRole(
                householdId,
                memberId,
                request.getRole()
        );

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(response);
    }
}