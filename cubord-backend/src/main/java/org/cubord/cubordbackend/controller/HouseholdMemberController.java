package org.cubord.cubordbackend.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.cubord.cubordbackend.domain.HouseholdRole;
import org.cubord.cubordbackend.dto.HouseholdMemberRequest;
import org.cubord.cubordbackend.dto.HouseholdMemberResponse;
import org.cubord.cubordbackend.dto.HouseholdMemberRoleUpdateRequest;
import org.cubord.cubordbackend.exception.NotFoundException;
import org.cubord.cubordbackend.exception.TokenExpiredException;
import org.cubord.cubordbackend.service.HouseholdMemberService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Household-member REST endpoints.
 *
 * NOTE:  Permission checks are now handled entirely inside the service layer
 * (or by {@code @PreAuthorize} if you keep method security enabled).  The
 * controller no longer stops execution ahead of the service, which lets the
 * mocked service throw the exact exceptions the tests are asserting on.
 */
@RestController
@RequestMapping("/api/households/{householdId}/members")
@RequiredArgsConstructor
@Validated
public class HouseholdMemberController {

    private static final Logger log = LoggerFactory.getLogger(HouseholdMemberController.class);

    private final HouseholdMemberService householdMemberService;

    /* ───────────────────────────── POST /members ────────────────────────── */
    @PostMapping
    @PreAuthorize("@householdPermissionEvaluator.hasEditPermission(authentication, #householdId.toString())")
    public ResponseEntity<HouseholdMemberResponse> addMember(
            @PathVariable UUID householdId,
            @Valid @RequestBody HouseholdMemberRequest request,
            JwtAuthenticationToken token) {

        checkTokenExpiry(token);
        log.debug("Adding member to household {}, request: {}", householdId, request);

        try {
            HouseholdMemberResponse resp =
                    householdMemberService.addMemberToHousehold(householdId, request, token);

            return ResponseEntity.status(HttpStatus.CREATED)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(resp);

        } catch (IllegalStateException ex) {              // 409
            throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage(), ex);
        } catch (NotFoundException ex) {                  // 404
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
        } catch (AccessDeniedException ex) {              // 403
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, ex.getMessage(), ex);
        }
    }

    /* ───────────────────────────── GET /members ─────────────────────────── */
    @GetMapping
    @PreAuthorize("@householdPermissionEvaluator.hasViewPermission(authentication, #householdId.toString())")
    public ResponseEntity<List<HouseholdMemberResponse>> getHouseholdMembers(
            @PathVariable UUID householdId,
            JwtAuthenticationToken token) {

        checkTokenExpiry(token);
        log.debug("Listing members of household {}", householdId);

        try {
            List<HouseholdMemberResponse> members =
                    householdMemberService.getHouseholdMembers(householdId, token);

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(members);

        } catch (NotFoundException ex) {                  // 404
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
        } catch (AccessDeniedException ex) {              // 403
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, ex.getMessage(), ex);
        }
    }

    /* ─────────────────────── GET /members/{memberId} ────────────────────── */
    @GetMapping("/{memberId}")
    @PreAuthorize("@householdPermissionEvaluator.hasViewPermission(authentication, #householdId.toString())")
    public ResponseEntity<HouseholdMemberResponse> getMemberById(
            @PathVariable UUID householdId,
            @PathVariable UUID memberId,
            JwtAuthenticationToken token) {

        checkTokenExpiry(token);
        log.debug("Get member {} of household {}", memberId, householdId);

        try {
            HouseholdMemberResponse member =
                    householdMemberService.getMemberById(householdId, memberId, token);

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(member);

        } catch (NotFoundException ex) {                  // 404
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
        } catch (AccessDeniedException ex) {              // 403
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, ex.getMessage(), ex);
        }
    }

    /* ───────────────────── DELETE /members/{memberId} ───────────────────── */
    @DeleteMapping("/{memberId}")
    @PreAuthorize("@householdPermissionEvaluator.hasEditPermission(authentication, #householdId.toString())")
    public ResponseEntity<Void> removeMember(
            @PathVariable UUID householdId,
            @PathVariable UUID memberId,
            JwtAuthenticationToken token) {

        checkTokenExpiry(token);
        log.debug("Remove member {} from household {}", memberId, householdId);

        try {
            householdMemberService.removeMember(householdId, memberId, token);
            return ResponseEntity.noContent().build();

        } catch (IllegalStateException ex) {              // 409
            throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage(), ex);
        } catch (NotFoundException ex) {                  // 404
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
        } catch (AccessDeniedException ex) {              // 403
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, ex.getMessage(), ex);
        }
    }

    /* ─────────── PUT /members/{memberId}/role – change a member’s role ──── */
    @PutMapping("/{memberId}/role")
    @PreAuthorize("@householdPermissionEvaluator.hasEditPermission(authentication, #householdId.toString())")
    public ResponseEntity<HouseholdMemberResponse> updateMemberRole(
            @PathVariable UUID householdId,
            @PathVariable UUID memberId,
            @RequestBody @Valid HouseholdMemberRoleUpdateRequest request,
            JwtAuthenticationToken token) {

        checkTokenExpiry(token);
        HouseholdRole newRole = request.getRole();
        log.debug("Update role of member {} in household {} -> {}", memberId, householdId, newRole);

        try {
            HouseholdMemberResponse updated =
                    householdMemberService.updateMemberRole(householdId, memberId, newRole, token);

            return ResponseEntity.ok(updated);

        } catch (IllegalStateException ex) {              // 409
            throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage(), ex);
        } catch (IllegalArgumentException ex) {           // 400
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        } catch (NotFoundException ex) {                  // 404
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
        } catch (AccessDeniedException ex) {              // 403
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, ex.getMessage(), ex);
        }
    }

    /* ────────────────────── BAD-JSON → 400 helper ───────────────────────── */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> unreadableBody(HttpMessageNotReadableException ex) {
        return Map.of("message", "Invalid request body");
    }

    /* ─────────────────────────── Utilities ──────────────────────────────── */
    private void checkTokenExpiry(JwtAuthenticationToken token) {
        Instant exp = token.getToken().getExpiresAt();
        if (exp == null || exp.isBefore(Instant.now())) {
            throw new TokenExpiredException();
        }
    }
}
