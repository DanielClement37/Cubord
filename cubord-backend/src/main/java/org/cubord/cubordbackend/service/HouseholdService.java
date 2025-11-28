
package org.cubord.cubordbackend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cubord.cubordbackend.domain.*;
import org.cubord.cubordbackend.dto.household.HouseholdRequest;
import org.cubord.cubordbackend.dto.household.HouseholdResponse;
import org.cubord.cubordbackend.exception.*;
import org.cubord.cubordbackend.repository.HouseholdMemberRepository;
import org.cubord.cubordbackend.repository.HouseholdRepository;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service class for managing households.
 * Provides operations for creating, retrieving, updating, and deleting households,
 * with proper authorization checks and exception handling.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class HouseholdService {

    private final HouseholdRepository householdRepository;
    private final HouseholdMemberRepository householdMemberRepository;
    private final UserService userService;

    /**
     * Validates that both request and token are not null.
     *
     * @param request Request object to validate
     * @param token Authentication token to validate
     * @param operation Operation being attempted
     * @throws ValidationException if request or token is null
     */
    private void validateInputs(Object request, JwtAuthenticationToken token, String operation) {
        if (request == null) {
            throw new ValidationException("request cannot be null for " + operation + " operation");
        }
        if (token == null) {
            throw new ValidationException("token cannot be null for " + operation + " operation");
        }
    }


    /**
     * Validates that only the token is not null.
     *
     * @param token Authentication token to validate
     * @param operation Operation being attempted
     * @throws ValidationException if token is null
     */
    private void validateToken(JwtAuthenticationToken token, String operation) {
        if (token == null) {
            throw new ValidationException("token cannot be null for " + operation + " operation");
        }
    }

    /**
     * Validates household name is not null or empty.
     *
     * @param name Household name to validate
     * @throws ValidationException if name is null or empty
     */
    private void validateHouseholdName(String name) {
        if (name == null) {
            throw new ValidationException("Household name cannot be null");
        }
        if (name.trim().isEmpty()) {
            throw new ValidationException("Household name cannot be empty");
        }
    }

    /**
     * Validates that the user has admin or owner permissions.
     *
     * @param member HouseholdMember to validate
     * @param operation Operation being attempted
     * @throws InsufficientPermissionException if user lacks permission
     */
    private void validateAdminOrOwnerAccess(HouseholdMember member, String operation) {
        if (member.getRole() != HouseholdRole.OWNER && member.getRole() != HouseholdRole.ADMIN) {
            log.warn("User {} attempted {} operation without sufficient privileges",
                    member.getUser().getId(), operation);
            throw new InsufficientPermissionException("You don't have permission to " + operation);
        }
        log.debug("User {} authorized for {} operation with role {}",
                member.getUser().getId(), operation, member.getRole());
    }

    /**
     * Validates that the user is the household owner.
     *
     * @param member HouseholdMember to validate
     * @param operation Operation being attempted
     * @throws InsufficientPermissionException if user is not the owner
     */
    private void validateOwnerAccess(HouseholdMember member, String operation) {
        if (member.getRole() != HouseholdRole.OWNER) {
            log.warn("User {} attempted {} operation without owner privileges",
                    member.getUser().getId(), operation);
            throw new InsufficientPermissionException("Only the household owner can " + operation);
        }
        log.debug("Owner user {} authorized for {} operation", member.getUser().getId(), operation);
    }

    /**
     * Creates a new household with the current user as the owner.
     *
     * @param request DTO containing household information
     * @param token JWT authentication token of the current user
     * @return HouseholdResponse containing the created household's details
     * @throws ValidationException if request or token is null, or name is invalid
     * @throws ConflictException if a household with the same name already exists
     */
    @Transactional
    public HouseholdResponse createHousehold(HouseholdRequest request, JwtAuthenticationToken token) {
        // Validate inputs
        validateInputs(request, token, "create");
        validateHouseholdName(request.getName());

        // Get current user
        User currentUser = userService.getCurrentUser(token);
        log.debug("User {} creating household with name: {}", currentUser.getId(), request.getName());

        // Check if a household with this name already exists
        if (householdRepository.existsByName(request.getName())) {
            throw new ConflictException("Household with name '" + request.getName() + "' already exists");
        }

        // Create new household
        Household household = Household.builder()
                .name(request.getName())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        // Save household to get ID
        household = householdRepository.save(household);
        log.debug("Created household with ID: {}", household.getId());

        // Create household member with OWNER role for current user
        HouseholdMember member = HouseholdMember.builder()
                .household(household)
                .user(currentUser)
                .role(HouseholdRole.OWNER)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        householdMemberRepository.save(member);
        log.info("User {} successfully created household '{}' with ID: {}",
                currentUser.getId(), household.getName(), household.getId());

        // Convert to response DTO
        return mapToHouseholdResponse(household);
    }

    /**
     * Retrieves a household by its ID if the current user is a member.
     *
     * @param householdId UUID of the household to retrieve
     * @param token JWT authentication token of the current user
     * @return HouseholdResponse containing the household's details
     * @throws ValidationException if token is null
     * @throws NotFoundException if the household doesn't exist
     * @throws InsufficientPermissionException if the current user is not a member of the household
     */
    @Transactional(readOnly = true)
    public HouseholdResponse getHouseholdById(UUID householdId, JwtAuthenticationToken token) {
        // Validate inputs
        validateToken(token, "get household");

        User currentUser = userService.getCurrentUser(token);
        log.debug("User {} retrieving household with ID: {}", currentUser.getId(), householdId);

        // Find household
        Household household = householdRepository.findById(householdId)
                .orElseThrow(() -> new NotFoundException("Household not found"));

        // Check if user is a member
        householdMemberRepository.findByHouseholdIdAndUserId(householdId, currentUser.getId())
                .orElseThrow(() -> new InsufficientPermissionException(
                        "You don't have access to this household"));

        log.debug("User {} successfully retrieved household: {}", currentUser.getId(), householdId);

        // Convert to response DTO
        return mapToHouseholdResponse(household);
    }

    /**
     * Retrieves all households where the current user is a member.
     *
     * @param token JWT authentication token of the current user
     * @return List of HouseholdResponse objects containing household details
     * @throws ValidationException if token is null
     */
    @Transactional(readOnly = true)
    public List<HouseholdResponse> getUserHouseholds(JwtAuthenticationToken token) {
        // Validate inputs
        validateToken(token, "get user households");

        User currentUser = userService.getCurrentUser(token);
        log.debug("User {} retrieving all their households", currentUser.getId());

        // Find all households where user is a member
        List<Household> households = householdRepository.findByMembersUserId(currentUser.getId());

        log.debug("User {} has {} households", currentUser.getId(), households.size());

        // Convert to response DTOs
        return households.stream()
                .map(this::mapToHouseholdResponse)
                .collect(Collectors.toList());
    }

    /**
     * Updates a household's information if the current user has appropriate permissions.
     *
     * @param householdId UUID of the household to update
     * @param request DTO containing updated household information
     * @param token JWT authentication token of the current user
     * @return HouseholdResponse containing the updated household's details
     * @throws ValidationException if request or token is null, or name is invalid
     * @throws NotFoundException if the household doesn't exist
     * @throws InsufficientPermissionException if the current user lacks permission to update the household
     * @throws ConflictException if the new name is already used by another household
     */
    @Transactional
    public HouseholdResponse updateHousehold(UUID householdId, HouseholdRequest request, JwtAuthenticationToken token) {
        // Validate inputs
        validateInputs(request, token, "update");
        validateHouseholdName(request.getName());

        User currentUser = userService.getCurrentUser(token);
        log.debug("User {} updating household {} with new name: {}",
                currentUser.getId(), householdId, request.getName());

        // Find household
        Household household = householdRepository.findById(householdId)
                .orElseThrow(() -> new NotFoundException("Household not found"));

        // Check if user is a member with appropriate permissions
        HouseholdMember member = householdMemberRepository.findByHouseholdIdAndUserId(householdId, currentUser.getId())
                .orElseThrow(() -> new InsufficientPermissionException(
                        "You don't have access to this household"));

        // Validate user has admin or owner role
        validateAdminOrOwnerAccess(member, "update this household");

        // Check if name is already used by a different household
        Optional<Household> existingHousehold = householdRepository.findByName(request.getName());
        if (existingHousehold.isPresent() && !existingHousehold.get().getId().equals(householdId)) {
            throw new ConflictException("Household with name '" + request.getName() + "' already exists");
        }

        // Update household
        household.setName(request.getName());
        household.setUpdatedAt(LocalDateTime.now());

        household = householdRepository.save(household);
        log.info("User {} successfully updated household {} to name: {}",
                currentUser.getId(), householdId, request.getName());

        // Convert to response DTO
        return mapToHouseholdResponse(household);
    }

    /**
     * Deletes a household if the current user is the owner.
     *
     * @param householdId UUID of the household to delete
     * @param token JWT authentication token of the current user
     * @throws ValidationException if token is null
     * @throws NotFoundException if the household doesn't exist
     * @throws InsufficientPermissionException if the current user is not the owner of the household
     */
    @Transactional
    public void deleteHousehold(UUID householdId, JwtAuthenticationToken token) {
        // Validate inputs
        validateToken(token, "delete household");

        User currentUser = userService.getCurrentUser(token);
        log.debug("User {} attempting to delete household: {}", currentUser.getId(), householdId);

        // Find household
        Household household = householdRepository.findById(householdId)
                .orElseThrow(() -> new NotFoundException("Household not found"));

        // Check if user is the owner
        HouseholdMember member = householdMemberRepository.findByHouseholdIdAndUserId(householdId, currentUser.getId())
                .orElseThrow(() -> new InsufficientPermissionException(
                        "You don't have access to this household"));

        validateOwnerAccess(member, "delete a household");

        // Delete household
        householdRepository.delete(household);
        log.info("User {} successfully deleted household: {}", currentUser.getId(), householdId);
    }

    /**
     * Allows a user to leave a household unless they are the owner.
     *
     * @param householdId UUID of the household to leave
     * @param token JWT authentication token of the current user
     * @throws ValidationException if token is null
     * @throws NotFoundException if the household doesn't exist
     * @throws InsufficientPermissionException if the current user is not a member of the household
     * @throws ResourceStateException if the current user is the owner of the household
     */
    @Transactional
    public void leaveHousehold(UUID householdId, JwtAuthenticationToken token) {
        // Validate inputs
        validateToken(token, "leave household");

        User currentUser = userService.getCurrentUser(token);
        log.debug("User {} attempting to leave household: {}", currentUser.getId(), householdId);

        // Verify household exists
        Household household = householdRepository.findById(householdId)
                .orElseThrow(() -> new NotFoundException("Household not found"));

        // Check if user is a member
        HouseholdMember member = householdMemberRepository.findByHouseholdIdAndUserId(householdId, currentUser.getId())
                .orElseThrow(() -> new InsufficientPermissionException(
                        "You are not a member of this household"));

        // Owners cannot leave, they must transfer ownership first
        if (member.getRole() == HouseholdRole.OWNER) {
            throw new ResourceStateException("Owner cannot leave a household. Transfer ownership first.");
        }

        // Remove the member
        householdMemberRepository.delete(member);
        log.info("User {} successfully left household: {}", currentUser.getId(), householdId);
    }

    /**
     * Transfers ownership of a household to another member.
     *
     * @param householdId UUID of the household
     * @param newOwnerId UUID of the member to become the new owner
     * @param token JWT authentication token of the current user
     * @throws ValidationException if token or newOwnerId is null
     * @throws NotFoundException if the household or new owner doesn't exist or is not a member
     * @throws InsufficientPermissionException if the current user is not the owner of the household
     */
    @Transactional
    public void transferOwnership(UUID householdId, UUID newOwnerId, JwtAuthenticationToken token) {
        // Validate inputs
        validateToken(token, "transfer ownership");
        if (newOwnerId == null) {
            throw new ValidationException("New owner ID cannot be null");
        }

        User currentUser = userService.getCurrentUser(token);
        log.debug("User {} attempting to transfer ownership of household {} to user {}",
                currentUser.getId(), householdId, newOwnerId);

        // Verify household exists
        Household household = householdRepository.findById(householdId)
                .orElseThrow(() -> new NotFoundException("Household not found"));

        // Check if current user is the owner
        HouseholdMember currentMember = householdMemberRepository.findByHouseholdIdAndUserId(householdId, currentUser.getId())
                .orElseThrow(() -> new InsufficientPermissionException(
                        "You don't have access to this household"));

        validateOwnerAccess(currentMember, "transfer ownership");

        // Check if new owner is a member
        HouseholdMember newOwnerMember = householdMemberRepository.findByHouseholdIdAndUserId(householdId, newOwnerId)
                .orElseThrow(() -> new NotFoundException("New owner is not a member of this household"));

        // Update roles
        currentMember.setRole(HouseholdRole.ADMIN);
        currentMember.setUpdatedAt(LocalDateTime.now());
        newOwnerMember.setRole(HouseholdRole.OWNER);
        newOwnerMember.setUpdatedAt(LocalDateTime.now());

        // Save changes
        householdMemberRepository.save(currentMember);
        householdMemberRepository.save(newOwnerMember);

        log.info("User {} successfully transferred ownership of household {} to user {}",
                currentUser.getId(), householdId, newOwnerId);
    }

    /**
     * Searches for households by name and returns only those the current user is a member of.
     *
     * @param searchTerm Term to search for in household names
     * @param token JWT authentication token of the current user
     * @return List of HouseholdResponse objects matching the search criteria
     * @throws ValidationException if token or searchTerm is null
     */
    @Transactional(readOnly = true)
    public List<HouseholdResponse> searchHouseholds(String searchTerm, JwtAuthenticationToken token) {
        // Validate inputs
        validateToken(token, "search households");
        if (searchTerm == null) {
            throw new ValidationException("Search term cannot be null");
        }

        User currentUser = userService.getCurrentUser(token);
        log.debug("User {} searching households with term: {}", currentUser.getId(), searchTerm);

        // Find households matching search term
        List<Household> households = householdRepository.findByNameContainingIgnoreCase(searchTerm);

        // Filter to only include households where user is a member
        List<HouseholdResponse> results = households.stream()
                .filter(household -> householdMemberRepository.findByHouseholdIdAndUserId(
                        household.getId(), currentUser.getId()).isPresent())
                .map(this::mapToHouseholdResponse)
                .collect(Collectors.toList());

        log.debug("User {} found {} households matching search term: {}",
                currentUser.getId(), results.size(), searchTerm);

        return results;
    }

    /**
     * Changes a member's role within a household if the current user has appropriate permissions.
     *
     * @param householdId UUID of the household
     * @param memberId UUID of the member whose role is to be changed
     * @param role New role to assign to the member
     * @param token JWT authentication token of the current user
     * @throws ValidationException if token is null or attempting to set role to OWNER
     * @throws NotFoundException if the household or member doesn't exist
     * @throws InsufficientPermissionException if the current user lacks permission to change roles
     */
    @Transactional
    public void changeMemberRole(UUID householdId, UUID memberId, HouseholdRole role, JwtAuthenticationToken token) {
        // Validate inputs
        validateToken(token, "change member role");
        if (role == HouseholdRole.OWNER) {
            throw new ValidationException("Cannot set role to OWNER. Use transferOwnership method instead.");
        }

        User currentUser = userService.getCurrentUser(token);
        log.debug("User {} attempting to change role of member {} in household {} to {}",
                currentUser.getId(), memberId, householdId, role);

        // Verify household exists
        Household household = householdRepository.findById(householdId)
                .orElseThrow(() -> new NotFoundException("Household not found"));

        // Check if current user is a member with appropriate permissions
        HouseholdMember currentMember = householdMemberRepository.findByHouseholdIdAndUserId(householdId, currentUser.getId())
                .orElseThrow(() -> new InsufficientPermissionException(
                        "You don't have access to this household"));

        validateAdminOrOwnerAccess(currentMember, "change member roles");

        // Find target member
        HouseholdMember targetMember = householdMemberRepository.findById(memberId)
                .orElseThrow(() -> new NotFoundException("Member not found"));

        // Verify member belongs to the household
        if (!targetMember.getHousehold().getId().equals(householdId)) {
            throw new NotFoundException("Member not found in this household");
        }

        // Admin cannot change owner's role
        if (currentMember.getRole() == HouseholdRole.ADMIN && targetMember.getRole() == HouseholdRole.OWNER) {
            throw new InsufficientPermissionException("Admin cannot change owner's role");
        }

        // Update role
        targetMember.setRole(role);
        targetMember.setUpdatedAt(LocalDateTime.now());

        householdMemberRepository.save(targetMember);
        log.info("User {} successfully changed role of member {} in household {} to {}",
                currentUser.getId(), memberId, householdId, role);
    }

    /**
     * Partially updates a household's information if the current user has appropriate permissions.
     *
     * @param householdId UUID of the household to update
     * @param fields Map of field names to updated values
     * @param token JWT authentication token of the current user
     * @return HouseholdResponse containing the updated household's details
     * @throws ValidationException if token or fields is null/empty
     * @throws NotFoundException if the household doesn't exist
     * @throws InsufficientPermissionException if the current user lacks permission to update the household
     * @throws ConflictException if the new name is already used by another household
     */
    @Transactional
    public HouseholdResponse patchHousehold(UUID householdId, Map<String, Object> fields, JwtAuthenticationToken token) {
        // Validate inputs
        validateToken(token, "patch household");
        if (fields == null) {
            throw new ValidationException("Update fields cannot be null");
        }
        if (fields.isEmpty()) {
            throw new ValidationException("Update fields cannot be empty");
        }

        User currentUser = userService.getCurrentUser(token);
        log.debug("User {} patching household {} with fields: {}", currentUser.getId(), householdId, fields.keySet());

        // Find household
        Household household = householdRepository.findById(householdId)
                .orElseThrow(() -> new NotFoundException("Household not found"));

        // Check if user is a member with appropriate permissions
        HouseholdMember member = householdMemberRepository.findByHouseholdIdAndUserId(householdId, currentUser.getId())
                .orElseThrow(() -> new InsufficientPermissionException(
                        "You don't have access to this household"));

        validateAdminOrOwnerAccess(member, "update this household");

        for (Map.Entry<String, Object> entry : fields.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            
            switch (key) {
                case "name":
                    String newName = (String) value;
                    validateHouseholdName(newName);

                    // Check if name is already used by a different household
                    Optional<Household> existingHousehold = householdRepository.findByName(newName);
                    if (existingHousehold.isPresent() && !existingHousehold.get().getId().equals(householdId)) {
                        throw new ConflictException("Household with name '" + newName + "' already exists");
                    }

                    household.setName(newName);
                    break;
                default:
                    log.debug("Ignoring unknown field: {}", key);
                    break;
            }
        }

        household.setUpdatedAt(LocalDateTime.now());
        household = householdRepository.save(household);

        log.info("User {} successfully patched household {}", currentUser.getId(), householdId);

        return mapToHouseholdResponse(household);
    }

    /**
     * Maps a Household entity to a HouseholdResponse DTO.
     *
     * @param household Household entity to map
     * @return HouseholdResponse containing the household's details
     */
    private HouseholdResponse mapToHouseholdResponse(Household household) {
        return HouseholdResponse.builder()
                .id(household.getId())
                .name(household.getName())
                .createdAt(household.getCreatedAt())
                .updatedAt(household.getUpdatedAt())
                .build();
    }
}