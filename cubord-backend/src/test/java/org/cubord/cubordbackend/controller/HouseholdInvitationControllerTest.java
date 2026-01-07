
package org.cubord.cubordbackend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.cubord.cubordbackend.config.TestSecurityConfig;
import org.cubord.cubordbackend.domain.HouseholdRole;
import org.cubord.cubordbackend.domain.InvitationStatus;
import org.cubord.cubordbackend.dto.householdInvitation.HouseholdInvitationRequest;
import org.cubord.cubordbackend.dto.householdInvitation.HouseholdInvitationResponse;
import org.cubord.cubordbackend.dto.householdInvitation.HouseholdInvitationUpdateRequest;
import org.cubord.cubordbackend.dto.householdInvitation.ResendInvitationRequest;
import org.cubord.cubordbackend.exception.*;
import org.cubord.cubordbackend.security.SecurityService;
import org.cubord.cubordbackend.service.HouseholdInvitationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for {@link HouseholdInvitationController}.
 *
 * <p>Tests verify:</p>
 * <ul>
 *   <li>Endpoint functionality and response formats</li>
 *   <li>Authorization via {@code @PreAuthorize} with mocked SecurityService</li>
 *   <li>Proper exception handling and HTTP status codes</li>
 *   <li>Input validation</li>
 * </ul>
 */
@WebMvcTest(HouseholdInvitationController.class)
@Import(TestSecurityConfig.class)
@ActiveProfiles("test")
class HouseholdInvitationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private HouseholdInvitationService householdInvitationService;

    @MockitoBean(name = "security")
    private SecurityService securityService;

    @MockitoBean
    private org.cubord.cubordbackend.security.HouseholdPermissionEvaluator householdPermissionEvaluator;

    private UUID currentUserId;
    private UUID householdId;
    private UUID invitationId;
    private UUID invitedUserId;
    private Jwt jwt;
    private HouseholdInvitationResponse sampleInvitationResponse;

    @BeforeEach
    void setUp() {
        currentUserId = UUID.randomUUID();
        householdId = UUID.randomUUID();
        invitationId = UUID.randomUUID();
        invitedUserId = UUID.randomUUID();

        jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(currentUserId.toString())
                .claim("email", "test@example.com")
                .claim("name", "Test User")
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        sampleInvitationResponse = HouseholdInvitationResponse.builder()
                .id(invitationId)
                .invitedUserId(invitedUserId)
                .invitedUserEmail("invited@example.com")
                .invitedUserName("Invited User")
                .householdId(householdId)
                .householdName("Test Household")
                .invitedByUserId(currentUserId)
                .invitedByUserName("Test User")
                .proposedRole(HouseholdRole.MEMBER)
                .status(InvitationStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusDays(7))
                .build();

        // Default security service mock behavior
        when(securityService.canSendHouseholdInvitations(householdId)).thenReturn(true);
        when(securityService.canAccessHousehold(householdId)).thenReturn(true);
        when(securityService.canModifyHousehold(householdId)).thenReturn(true);
    }

    @Nested
    @DisplayName("POST /api/households/{householdId}/invitations")
    class SendInvitation {

        @Test
        @DisplayName("should send invitation when valid request provided")
        void shouldSendInvitationWhenValidRequestProvided() throws Exception {
            HouseholdInvitationRequest request = HouseholdInvitationRequest.builder()
                    .invitedUserEmail("invited@example.com")
                    .proposedRole(HouseholdRole.MEMBER)
                    .build();

            when(householdInvitationService.sendInvitation(eq(householdId), any(HouseholdInvitationRequest.class)))
                    .thenReturn(sampleInvitationResponse);

            mockMvc.perform(post("/api/households/{householdId}/invitations", householdId)
                            .with(jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.invitedUserEmail").value("invited@example.com"))
                    .andExpect(jsonPath("$.proposedRole").value("MEMBER"))
                    .andExpect(jsonPath("$.status").value("PENDING"));

            verify(securityService).canSendHouseholdInvitations(householdId);
            verify(householdInvitationService).sendInvitation(eq(householdId), any(HouseholdInvitationRequest.class));
        }

        @Test
        @DisplayName("should send invitation by user ID when provided")
        void shouldSendInvitationByUserIdWhenProvided() throws Exception {
            HouseholdInvitationRequest request = HouseholdInvitationRequest.builder()
                    .invitedUserId(invitedUserId)
                    .proposedRole(HouseholdRole.ADMIN)
                    .build();

            when(householdInvitationService.sendInvitation(eq(householdId), any(HouseholdInvitationRequest.class)))
                    .thenReturn(sampleInvitationResponse);

            mockMvc.perform(post("/api/households/{householdId}/invitations", householdId)
                            .with(jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.invitedUserId").value(invitedUserId.toString()));

            verify(householdInvitationService).sendInvitation(eq(householdId), any(HouseholdInvitationRequest.class));
        }

        @Test
        @DisplayName("should send invitation with custom expiry date")
        void shouldSendInvitationWithCustomExpiryDate() throws Exception {
            LocalDateTime customExpiry = LocalDateTime.now().plusDays(14);
            HouseholdInvitationRequest request = HouseholdInvitationRequest.builder()
                    .invitedUserEmail("invited@example.com")
                    .proposedRole(HouseholdRole.MEMBER)
                    .expiresAt(customExpiry)
                    .build();

            when(householdInvitationService.sendInvitation(eq(householdId), any(HouseholdInvitationRequest.class)))
                    .thenReturn(sampleInvitationResponse);

            mockMvc.perform(post("/api/households/{householdId}/invitations", householdId)
                            .with(jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated());

            verify(householdInvitationService).sendInvitation(eq(householdId), any(HouseholdInvitationRequest.class));
        }

        @Test
        @DisplayName("should return 400 when email is invalid")
        void shouldReturn400WhenEmailIsInvalid() throws Exception {
            HouseholdInvitationRequest request = HouseholdInvitationRequest.builder()
                    .invitedUserEmail("invalid-email")
                    .proposedRole(HouseholdRole.MEMBER)
                    .build();

            mockMvc.perform(post("/api/households/{householdId}/invitations", householdId)
                            .with(jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(householdInvitationService);
        }

        @Test
        @DisplayName("should return 400 when role is missing")
        void shouldReturn400WhenRoleIsMissing() throws Exception {
            HouseholdInvitationRequest request = HouseholdInvitationRequest.builder()
                    .invitedUserEmail("invited@example.com")
                    .build();

            mockMvc.perform(post("/api/households/{householdId}/invitations", householdId)
                            .with(jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(householdInvitationService);
        }

        @Test
        @DisplayName("should return 400 when both email and userId are missing")
        void shouldReturn400WhenBothEmailAndUserIdAreMissing() throws Exception {
            HouseholdInvitationRequest request = HouseholdInvitationRequest.builder()
                    .proposedRole(HouseholdRole.MEMBER)
                    .build();

            when(householdInvitationService.sendInvitation(eq(householdId), any(HouseholdInvitationRequest.class)))
                    .thenThrow(new ValidationException("Either invitedUserEmail or invitedUserId must be provided"));

            mockMvc.perform(post("/api/households/{householdId}/invitations", householdId)
                            .with(jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error_code").value("VALIDATION_ERROR"));

            verify(householdInvitationService).sendInvitation(eq(householdId), any(HouseholdInvitationRequest.class));
        }

        @Test
        @DisplayName("should return 400 when expiry date is in the past")
        void shouldReturn400WhenExpiryDateIsInThePast() throws Exception {
            HouseholdInvitationRequest request = HouseholdInvitationRequest.builder()
                    .invitedUserEmail("invited@example.com")
                    .proposedRole(HouseholdRole.MEMBER)
                    .expiresAt(LocalDateTime.now().minusDays(1))
                    .build();

            when(householdInvitationService.sendInvitation(eq(householdId), any(HouseholdInvitationRequest.class)))
                    .thenThrow(new ValidationException("Expiry date cannot be in the past"));

            mockMvc.perform(post("/api/households/{householdId}/invitations", householdId)
                            .with(jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error_code").value("VALIDATION_ERROR"));

            verify(householdInvitationService).sendInvitation(eq(householdId), any(HouseholdInvitationRequest.class));
        }

        @Test
        @DisplayName("should return 401 when not authenticated")
        void shouldReturn401WhenNotAuthenticated() throws Exception {
            HouseholdInvitationRequest request = HouseholdInvitationRequest.builder()
                    .invitedUserEmail("invited@example.com")
                    .proposedRole(HouseholdRole.MEMBER)
                    .build();

            mockMvc.perform(post("/api/households/{householdId}/invitations", householdId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());

            verifyNoInteractions(householdInvitationService);
        }

        @Test
        @DisplayName("should return 404 when household not found")
        void shouldReturn404WhenHouseholdNotFound() throws Exception {
            HouseholdInvitationRequest request = HouseholdInvitationRequest.builder()
                    .invitedUserEmail("invited@example.com")
                    .proposedRole(HouseholdRole.MEMBER)
                    .build();

            when(householdInvitationService.sendInvitation(eq(householdId), any(HouseholdInvitationRequest.class)))
                    .thenThrow(new NotFoundException("Household not found with ID: " + householdId));

            mockMvc.perform(post("/api/households/{householdId}/invitations", householdId)
                            .with(jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error_code").value("RESOURCE_NOT_FOUND"));

            verify(householdInvitationService).sendInvitation(eq(householdId), any(HouseholdInvitationRequest.class));
        }

        @Test
        @DisplayName("should return 404 when invited user not found")
        void shouldReturn404WhenInvitedUserNotFound() throws Exception {
            HouseholdInvitationRequest request = HouseholdInvitationRequest.builder()
                    .invitedUserEmail("nonexistent@example.com")
                    .proposedRole(HouseholdRole.MEMBER)
                    .build();

            when(householdInvitationService.sendInvitation(eq(householdId), any(HouseholdInvitationRequest.class)))
                    .thenThrow(new NotFoundException("User not found with email: nonexistent@example.com"));

            mockMvc.perform(post("/api/households/{householdId}/invitations", householdId)
                            .with(jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error_code").value("RESOURCE_NOT_FOUND"));

            verify(householdInvitationService).sendInvitation(eq(householdId), any(HouseholdInvitationRequest.class));
        }

        @Test
        @DisplayName("should return 403 when user lacks permission")
        void shouldReturn403WhenUserLacksPermission() throws Exception {
            when(securityService.canSendHouseholdInvitations(householdId)).thenReturn(false);

            HouseholdInvitationRequest request = HouseholdInvitationRequest.builder()
                    .invitedUserEmail("invited@example.com")
                    .proposedRole(HouseholdRole.MEMBER)
                    .build();

            mockMvc.perform(post("/api/households/{householdId}/invitations", householdId)
                            .with(jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());

            verify(securityService).canSendHouseholdInvitations(householdId);
            verifyNoInteractions(householdInvitationService);
        }

        @Test
        @DisplayName("should return 409 when user already invited or member")
        void shouldReturn409WhenUserAlreadyInvitedOrMember() throws Exception {
            HouseholdInvitationRequest request = HouseholdInvitationRequest.builder()
                    .invitedUserEmail("invited@example.com")
                    .proposedRole(HouseholdRole.MEMBER)
                    .build();

            when(householdInvitationService.sendInvitation(eq(householdId), any(HouseholdInvitationRequest.class)))
                    .thenThrow(new ConflictException("User already has a pending invitation to this household"));

            mockMvc.perform(post("/api/households/{householdId}/invitations", householdId)
                            .with(jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error_code").value("RESOURCE_CONFLICT"));

            verify(householdInvitationService).sendInvitation(eq(householdId), any(HouseholdInvitationRequest.class));
        }

        @Test
        @DisplayName("should return 400 when trying to invite as OWNER")
        void shouldReturn400WhenTryingToInviteAsOwner() throws Exception {
            HouseholdInvitationRequest request = HouseholdInvitationRequest.builder()
                    .invitedUserEmail("invited@example.com")
                    .proposedRole(HouseholdRole.OWNER)
                    .build();

            when(householdInvitationService.sendInvitation(eq(householdId), any(HouseholdInvitationRequest.class)))
                    .thenThrow(new ValidationException("Cannot invite user as OWNER. Use ownership transfer instead."));

            mockMvc.perform(post("/api/households/{householdId}/invitations", householdId)
                            .with(jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error_code").value("VALIDATION_ERROR"));

            verify(householdInvitationService).sendInvitation(eq(householdId), any(HouseholdInvitationRequest.class));
        }

        @Test
        @DisplayName("should return 400 when trying to invite self")
        void shouldReturn400WhenTryingToInviteSelf() throws Exception {
            HouseholdInvitationRequest request = HouseholdInvitationRequest.builder()
                    .invitedUserEmail("test@example.com") // Same as JWT email
                    .proposedRole(HouseholdRole.MEMBER)
                    .build();

            when(householdInvitationService.sendInvitation(eq(householdId), any(HouseholdInvitationRequest.class)))
                    .thenThrow(new BusinessRuleViolationException("Cannot invite yourself"));

            mockMvc.perform(post("/api/households/{householdId}/invitations", householdId)
                            .with(jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error_code").value("BUSINESS_RULE_VIOLATION"));

            verify(householdInvitationService).sendInvitation(eq(householdId), any(HouseholdInvitationRequest.class));
        }

        @Test
        @DisplayName("should return 400 when invalid UUID format provided")
        void shouldReturn400WhenInvalidUuidFormatProvided() throws Exception {
            HouseholdInvitationRequest request = HouseholdInvitationRequest.builder()
                    .invitedUserEmail("invited@example.com")
                    .proposedRole(HouseholdRole.MEMBER)
                    .build();

            mockMvc.perform(post("/api/households/{householdId}/invitations", "invalid-uuid")
                            .with(jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(householdInvitationService);
        }
    }

    @Nested
    @DisplayName("GET /api/households/{householdId}/invitations")
    class GetHouseholdInvitations {

        @Test
        @DisplayName("should return all household invitations")
        void shouldReturnAllHouseholdInvitations() throws Exception {
            List<HouseholdInvitationResponse> invitations = List.of(sampleInvitationResponse);

            when(householdInvitationService.getHouseholdInvitations(householdId))
                    .thenReturn(invitations);

            mockMvc.perform(get("/api/households/{householdId}/invitations", householdId)
                            .with(jwt().jwt(jwt)))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].status").value("PENDING"));

            verify(securityService).canAccessHousehold(householdId);
            verify(householdInvitationService).getHouseholdInvitations(householdId);
        }

        @Test
        @DisplayName("should return empty list when no invitations exist")
        void shouldReturnEmptyListWhenNoInvitationsExist() throws Exception {
            when(householdInvitationService.getHouseholdInvitations(householdId))
                    .thenReturn(Collections.emptyList());

            mockMvc.perform(get("/api/households/{householdId}/invitations", householdId)
                            .with(jwt().jwt(jwt)))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(0));

            verify(householdInvitationService).getHouseholdInvitations(householdId);
        }

        @Test
        @DisplayName("should support filtering by status")
        void shouldSupportFilteringByStatus() throws Exception {
            List<HouseholdInvitationResponse> pendingInvitations = List.of(sampleInvitationResponse);

            when(householdInvitationService.getHouseholdInvitationsByStatus(householdId, InvitationStatus.PENDING))
                    .thenReturn(pendingInvitations);

            mockMvc.perform(get("/api/households/{householdId}/invitations", householdId)
                            .param("status", "PENDING")
                            .with(jwt().jwt(jwt)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].status").value("PENDING"));

            verify(householdInvitationService).getHouseholdInvitationsByStatus(householdId, InvitationStatus.PENDING);
        }

        @Test
        @DisplayName("should support filtering by multiple statuses")
        void shouldSupportFilteringByMultipleStatuses() throws Exception {
            List<HouseholdInvitationResponse> invitations = List.of(sampleInvitationResponse);

            when(householdInvitationService.getHouseholdInvitationsByStatus(householdId, InvitationStatus.ACCEPTED))
                    .thenReturn(invitations);

            mockMvc.perform(get("/api/households/{householdId}/invitations", householdId)
                            .param("status", "ACCEPTED")
                            .with(jwt().jwt(jwt)))
                    .andExpect(status().isOk());

            verify(householdInvitationService).getHouseholdInvitationsByStatus(householdId, InvitationStatus.ACCEPTED);
        }

        @Test
        @DisplayName("should return 400 when invalid status provided")
        void shouldReturn400WhenInvalidStatusProvided() throws Exception {
            mockMvc.perform(get("/api/households/{householdId}/invitations", householdId)
                            .param("status", "INVALID_STATUS")
                            .with(jwt().jwt(jwt)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(householdInvitationService);
        }

        @Test
        @DisplayName("should return 401 when not authenticated")
        void shouldReturn401WhenNotAuthenticated() throws Exception {
            mockMvc.perform(get("/api/households/{householdId}/invitations", householdId))
                    .andExpect(status().isUnauthorized());

            verifyNoInteractions(householdInvitationService);
        }

        @Test
        @DisplayName("should return 404 when household not found")
        void shouldReturn404WhenHouseholdNotFound() throws Exception {
            when(householdInvitationService.getHouseholdInvitations(householdId))
                    .thenThrow(new NotFoundException("Household not found with ID: " + householdId));

            mockMvc.perform(get("/api/households/{householdId}/invitations", householdId)
                            .with(jwt().jwt(jwt)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error_code").value("RESOURCE_NOT_FOUND"));

            verify(householdInvitationService).getHouseholdInvitations(householdId);
        }

        @Test
        @DisplayName("should return 403 when user lacks permission")
        void shouldReturn403WhenUserLacksPermission() throws Exception {
            when(securityService.canAccessHousehold(householdId)).thenReturn(false);

            mockMvc.perform(get("/api/households/{householdId}/invitations", householdId)
                            .with(jwt().jwt(jwt)))
                    .andExpect(status().isForbidden());

            verify(securityService).canAccessHousehold(householdId);
            verifyNoInteractions(householdInvitationService);
        }

        @Test
        @DisplayName("should return 400 when invalid UUID format provided")
        void shouldReturn400WhenInvalidUuidFormatProvided() throws Exception {
            mockMvc.perform(get("/api/households/{householdId}/invitations", "invalid-uuid")
                            .with(jwt().jwt(jwt)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(householdInvitationService);
        }
    }

    @Nested
    @DisplayName("GET /api/households/{householdId}/invitations/{invitationId}")
    class GetInvitationById {

        @Test
        @DisplayName("should return invitation details when valid IDs provided")
        void shouldReturnInvitationDetailsWhenValidIdsProvided() throws Exception {
            when(householdInvitationService.getInvitationById(householdId, invitationId))
                    .thenReturn(sampleInvitationResponse);

            mockMvc.perform(get("/api/households/{householdId}/invitations/{invitationId}", householdId, invitationId)
                            .with(jwt().jwt(jwt)))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.id").value(invitationId.toString()))
                    .andExpect(jsonPath("$.householdId").value(householdId.toString()));

            verify(securityService).canAccessHousehold(householdId);
            verify(householdInvitationService).getInvitationById(householdId, invitationId);
        }

        @Test
        @DisplayName("should return 404 when invitation not found")
        void shouldReturn404WhenInvitationNotFound() throws Exception {
            when(householdInvitationService.getInvitationById(householdId, invitationId))
                    .thenThrow(new NotFoundException("Invitation not found with ID: " + invitationId));

            mockMvc.perform(get("/api/households/{householdId}/invitations/{invitationId}", householdId, invitationId)
                            .with(jwt().jwt(jwt)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error_code").value("RESOURCE_NOT_FOUND"));

            verify(householdInvitationService).getInvitationById(householdId, invitationId);
        }

        @Test
        @DisplayName("should return 403 when user lacks permission")
        void shouldReturn403WhenUserLacksPermission() throws Exception {
            when(securityService.canAccessHousehold(householdId)).thenReturn(false);

            mockMvc.perform(get("/api/households/{householdId}/invitations/{invitationId}", householdId, invitationId)
                            .with(jwt().jwt(jwt)))
                    .andExpect(status().isForbidden());

            verify(securityService).canAccessHousehold(householdId);
            verifyNoInteractions(householdInvitationService);
        }

        @Test
        @DisplayName("should return 401 when not authenticated")
        void shouldReturn401WhenNotAuthenticated() throws Exception {
            mockMvc.perform(get("/api/households/{householdId}/invitations/{invitationId}", householdId, invitationId))
                    .andExpect(status().isUnauthorized());

            verifyNoInteractions(householdInvitationService);
        }

        @Test
        @DisplayName("should return 400 when invalid UUID format provided")
        void shouldReturn400WhenInvalidUuidFormatProvided() throws Exception {
            mockMvc.perform(get("/api/households/{householdId}/invitations/{invitationId}", "invalid-uuid", "invalid-uuid")
                            .with(jwt().jwt(jwt)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(householdInvitationService);
        }
    }

    @Nested
    @DisplayName("GET /api/invitations/my")
    class GetMyInvitations {

        @Test
        @DisplayName("should return current user's pending invitations")
        void shouldReturnCurrentUsersPendingInvitations() throws Exception {
            List<HouseholdInvitationResponse> myInvitations = List.of(sampleInvitationResponse);

            when(householdInvitationService.getMyInvitations())
                    .thenReturn(myInvitations);

            mockMvc.perform(get("/api/invitations/my")
                            .with(jwt().jwt(jwt)))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(1));

            verify(householdInvitationService).getMyInvitations();
        }

        @Test
        @DisplayName("should return empty list when user has no invitations")
        void shouldReturnEmptyListWhenUserHasNoInvitations() throws Exception {
            when(householdInvitationService.getMyInvitations())
                    .thenReturn(Collections.emptyList());

            mockMvc.perform(get("/api/invitations/my")
                            .with(jwt().jwt(jwt)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(0));

            verify(householdInvitationService).getMyInvitations();
        }

        @Test
        @DisplayName("should support filtering by status")
        void shouldSupportFilteringByStatus() throws Exception {
            List<HouseholdInvitationResponse> myInvitations = List.of(sampleInvitationResponse);

            when(householdInvitationService.getMyInvitationsByStatus(InvitationStatus.PENDING))
                    .thenReturn(myInvitations);

            mockMvc.perform(get("/api/invitations/my")
                            .param("status", "PENDING")
                            .with(jwt().jwt(jwt)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1));

            verify(householdInvitationService).getMyInvitationsByStatus(InvitationStatus.PENDING);
        }

        @Test
        @DisplayName("should return 401 when not authenticated")
        void shouldReturn401WhenNotAuthenticated() throws Exception {
            mockMvc.perform(get("/api/invitations/my"))
                    .andExpect(status().isUnauthorized());

            verifyNoInteractions(householdInvitationService);
        }
    }

    @Nested
    @DisplayName("POST /api/invitations/{invitationId}/accept")
    class AcceptInvitation {

        @Test
        @DisplayName("should accept invitation when valid")
        void shouldAcceptInvitationWhenValid() throws Exception {
            HouseholdInvitationResponse acceptedResponse = HouseholdInvitationResponse.builder()
                    .id(invitationId)
                    .status(InvitationStatus.ACCEPTED)
                    .build();

            when(householdInvitationService.acceptInvitation(invitationId))
                    .thenReturn(acceptedResponse);

            mockMvc.perform(post("/api/invitations/{invitationId}/accept", invitationId)
                            .with(jwt().jwt(jwt)))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.status").value("ACCEPTED"));

            verify(householdInvitationService).acceptInvitation(invitationId);
        }

        @Test
        @DisplayName("should return 404 when invitation not found")
        void shouldReturn404WhenInvitationNotFound() throws Exception {
            when(householdInvitationService.acceptInvitation(invitationId))
                    .thenThrow(new NotFoundException("Invitation not found with ID: " + invitationId));

            mockMvc.perform(post("/api/invitations/{invitationId}/accept", invitationId)
                            .with(jwt().jwt(jwt)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error_code").value("RESOURCE_NOT_FOUND"));

            verify(householdInvitationService).acceptInvitation(invitationId);
        }

        @Test
        @DisplayName("should return 403 when not invited user")
        void shouldReturn403WhenNotInvitedUser() throws Exception {
            when(householdInvitationService.acceptInvitation(invitationId))
                    .thenThrow(new InsufficientPermissionException("You are not the invited user for this invitation"));

            mockMvc.perform(post("/api/invitations/{invitationId}/accept", invitationId)
                            .with(jwt().jwt(jwt)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error_code").value("INSUFFICIENT_PERMISSION"));

            verify(householdInvitationService).acceptInvitation(invitationId);
        }

        @Test
        @DisplayName("should return 409 when invitation already processed")
        void shouldReturn409WhenInvitationAlreadyProcessed() throws Exception {
            when(householdInvitationService.acceptInvitation(invitationId))
                    .thenThrow(new ResourceStateException("Invitation has already been processed. Current status: ACCEPTED"));

            mockMvc.perform(post("/api/invitations/{invitationId}/accept", invitationId)
                            .with(jwt().jwt(jwt)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error_code").value("RESOURCE_STATE_CONFLICT"));

            verify(householdInvitationService).acceptInvitation(invitationId);
        }

        @Test
        @DisplayName("should return 409 when invitation expired")
        void shouldReturn409WhenInvitationExpired() throws Exception {
            when(householdInvitationService.acceptInvitation(invitationId))
                    .thenThrow(new ResourceStateException("Invitation has expired"));

            mockMvc.perform(post("/api/invitations/{invitationId}/accept", invitationId)
                            .with(jwt().jwt(jwt)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error_code").value("RESOURCE_STATE_CONFLICT"));

            verify(householdInvitationService).acceptInvitation(invitationId);
        }

        @Test
        @DisplayName("should return 409 when user already member of household")
        void shouldReturn409WhenUserAlreadyMemberOfHousehold() throws Exception {
            when(householdInvitationService.acceptInvitation(invitationId))
                    .thenThrow(new ConflictException("You are already a member of this household"));

            mockMvc.perform(post("/api/invitations/{invitationId}/accept", invitationId)
                            .with(jwt().jwt(jwt)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error_code").value("RESOURCE_CONFLICT"));

            verify(householdInvitationService).acceptInvitation(invitationId);
        }

        @Test
        @DisplayName("should return 401 when not authenticated")
        void shouldReturn401WhenNotAuthenticated() throws Exception {
            mockMvc.perform(post("/api/invitations/{invitationId}/accept", invitationId))
                    .andExpect(status().isUnauthorized());

            verifyNoInteractions(householdInvitationService);
        }

        @Test
        @DisplayName("should return 400 when invalid UUID format provided")
        void shouldReturn400WhenInvalidUuidFormatProvided() throws Exception {
            mockMvc.perform(post("/api/invitations/{invitationId}/accept", "invalid-uuid")
                            .with(jwt().jwt(jwt)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(householdInvitationService);
        }
    }

    @Nested
    @DisplayName("POST /api/invitations/{invitationId}/decline")
    class DeclineInvitation {

        @Test
        @DisplayName("should decline invitation when valid")
        void shouldDeclineInvitationWhenValid() throws Exception {
            HouseholdInvitationResponse declinedResponse = HouseholdInvitationResponse.builder()
                    .id(invitationId)
                    .status(InvitationStatus.DECLINED)
                    .build();

            when(householdInvitationService.declineInvitation(invitationId))
                    .thenReturn(declinedResponse);

            mockMvc.perform(post("/api/invitations/{invitationId}/decline", invitationId)
                            .with(jwt().jwt(jwt)))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.status").value("DECLINED"));

            verify(householdInvitationService).declineInvitation(invitationId);
        }

        @Test
        @DisplayName("should return 404 when invitation not found")
        void shouldReturn404WhenInvitationNotFound() throws Exception {
            when(householdInvitationService.declineInvitation(invitationId))
                    .thenThrow(new NotFoundException("Invitation not found with ID: " + invitationId));

            mockMvc.perform(post("/api/invitations/{invitationId}/decline", invitationId)
                            .with(jwt().jwt(jwt)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error_code").value("RESOURCE_NOT_FOUND"));

            verify(householdInvitationService).declineInvitation(invitationId);
        }

        @Test
        @DisplayName("should return 403 when not invited user")
        void shouldReturn403WhenNotInvitedUser() throws Exception {
            when(householdInvitationService.declineInvitation(invitationId))
                    .thenThrow(new InsufficientPermissionException("You are not the invited user for this invitation"));

            mockMvc.perform(post("/api/invitations/{invitationId}/decline", invitationId)
                            .with(jwt().jwt(jwt)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error_code").value("INSUFFICIENT_PERMISSION"));

            verify(householdInvitationService).declineInvitation(invitationId);
        }

        @Test
        @DisplayName("should return 409 when invitation already processed")
        void shouldReturn409WhenInvitationAlreadyProcessed() throws Exception {
            when(householdInvitationService.declineInvitation(invitationId))
                    .thenThrow(new ResourceStateException("Invitation has already been processed. Current status: DECLINED"));

            mockMvc.perform(post("/api/invitations/{invitationId}/decline", invitationId)
                            .with(jwt().jwt(jwt)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error_code").value("RESOURCE_STATE_CONFLICT"));

            verify(householdInvitationService).declineInvitation(invitationId);
        }

        @Test
        @DisplayName("should return 401 when not authenticated")
        void shouldReturn401WhenNotAuthenticated() throws Exception {
            mockMvc.perform(post("/api/invitations/{invitationId}/decline", invitationId))
                    .andExpect(status().isUnauthorized());

            verifyNoInteractions(householdInvitationService);
        }

        @Test
        @DisplayName("should return 400 when invalid UUID format provided")
        void shouldReturn400WhenInvalidUuidFormatProvided() throws Exception {
            mockMvc.perform(post("/api/invitations/{invitationId}/decline", "invalid-uuid")
                            .with(jwt().jwt(jwt)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(householdInvitationService);
        }
    }

    @Nested
    @DisplayName("DELETE /api/households/{householdId}/invitations/{invitationId}")
    class CancelInvitation {

        @Test
        @DisplayName("should cancel invitation when user has permission")
        void shouldCancelInvitationWhenUserHasPermission() throws Exception {
            doNothing().when(householdInvitationService).cancelInvitation(householdId, invitationId);

            mockMvc.perform(delete("/api/households/{householdId}/invitations/{invitationId}", householdId, invitationId)
                            .with(jwt().jwt(jwt)))
                    .andExpect(status().isNoContent());

            verify(securityService).canModifyHousehold(householdId);
            verify(householdInvitationService).cancelInvitation(householdId, invitationId);
        }

        @Test
        @DisplayName("should return 404 when invitation not found")
        void shouldReturn404WhenInvitationNotFound() throws Exception {
            doThrow(new NotFoundException("Invitation not found with ID: " + invitationId))
                    .when(householdInvitationService).cancelInvitation(householdId, invitationId);

            mockMvc.perform(delete("/api/households/{householdId}/invitations/{invitationId}", householdId, invitationId)
                            .with(jwt().jwt(jwt)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error_code").value("RESOURCE_NOT_FOUND"));

            verify(householdInvitationService).cancelInvitation(householdId, invitationId);
        }

        @Test
        @DisplayName("should return 403 when user lacks permission")
        void shouldReturn403WhenUserLacksPermission() throws Exception {
            when(securityService.canModifyHousehold(householdId)).thenReturn(false);

            mockMvc.perform(delete("/api/households/{householdId}/invitations/{invitationId}", householdId, invitationId)
                            .with(jwt().jwt(jwt)))
                    .andExpect(status().isForbidden());

            verify(securityService).canModifyHousehold(householdId);
            verifyNoInteractions(householdInvitationService);
        }

        @Test
        @DisplayName("should return 409 when invitation cannot be cancelled")
        void shouldReturn409WhenInvitationCannotBeCancelled() throws Exception {
            doThrow(new ResourceStateException("Cannot cancel processed invitation. Current status: ACCEPTED"))
                    .when(householdInvitationService).cancelInvitation(householdId, invitationId);

            mockMvc.perform(delete("/api/households/{householdId}/invitations/{invitationId}", householdId, invitationId)
                            .with(jwt().jwt(jwt)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error_code").value("RESOURCE_STATE_CONFLICT"));

            verify(householdInvitationService).cancelInvitation(householdId, invitationId);
        }

        @Test
        @DisplayName("should return 401 when not authenticated")
        void shouldReturn401WhenNotAuthenticated() throws Exception {
            mockMvc.perform(delete("/api/households/{householdId}/invitations/{invitationId}", householdId, invitationId))
                    .andExpect(status().isUnauthorized());

            verifyNoInteractions(householdInvitationService);
        }

        @Test
        @DisplayName("should return 400 when invalid UUID format provided")
        void shouldReturn400WhenInvalidUuidFormatProvided() throws Exception {
            mockMvc.perform(delete("/api/households/{householdId}/invitations/{invitationId}", "invalid-uuid", "invalid-uuid")
                            .with(jwt().jwt(jwt)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(householdInvitationService);
        }
    }

    @Nested
    @DisplayName("PUT /api/households/{householdId}/invitations/{invitationId}")
    class UpdateInvitation {

        @Test
        @DisplayName("should update invitation when valid request provided")
        void shouldUpdateInvitationWhenValidRequestProvided() throws Exception {
            HouseholdInvitationUpdateRequest request = HouseholdInvitationUpdateRequest.builder()
                    .proposedRole(HouseholdRole.ADMIN)
                    .expiresAt(LocalDateTime.now().plusDays(14))
                    .build();

            HouseholdInvitationResponse updatedResponse = HouseholdInvitationResponse.builder()
                    .id(invitationId)
                    .proposedRole(HouseholdRole.ADMIN)
                    .status(InvitationStatus.PENDING)
                    .build();

            when(householdInvitationService.updateInvitation(eq(householdId), eq(invitationId), any(HouseholdInvitationUpdateRequest.class)))
                    .thenReturn(updatedResponse);

            mockMvc.perform(put("/api/households/{householdId}/invitations/{invitationId}", householdId, invitationId)
                            .with(jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.proposedRole").value("ADMIN"));

            verify(securityService).canModifyHousehold(householdId);
            verify(householdInvitationService).updateInvitation(eq(householdId), eq(invitationId), any(HouseholdInvitationUpdateRequest.class));
        }

        @Test
        @DisplayName("should return 403 when user lacks permission")
        void shouldReturn403WhenUserLacksPermission() throws Exception {
            when(securityService.canModifyHousehold(householdId)).thenReturn(false);

            HouseholdInvitationUpdateRequest request = HouseholdInvitationUpdateRequest.builder()
                    .proposedRole(HouseholdRole.ADMIN)
                    .build();

            mockMvc.perform(put("/api/households/{householdId}/invitations/{invitationId}", householdId, invitationId)
                            .with(jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());

            verify(securityService).canModifyHousehold(householdId);
            verifyNoInteractions(householdInvitationService);
        }

        @Test
        @DisplayName("should return 404 when invitation not found")
        void shouldReturn404WhenInvitationNotFound() throws Exception {
            HouseholdInvitationUpdateRequest request = HouseholdInvitationUpdateRequest.builder()
                    .proposedRole(HouseholdRole.ADMIN)
                    .build();

            when(householdInvitationService.updateInvitation(eq(householdId), eq(invitationId), any(HouseholdInvitationUpdateRequest.class)))
                    .thenThrow(new NotFoundException("Invitation not found with ID: " + invitationId));

            mockMvc.perform(put("/api/households/{householdId}/invitations/{invitationId}", householdId, invitationId)
                            .with(jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error_code").value("RESOURCE_NOT_FOUND"));

            verify(householdInvitationService).updateInvitation(eq(householdId), eq(invitationId), any(HouseholdInvitationUpdateRequest.class));
        }

        @Test
        @DisplayName("should return 401 when not authenticated")
        void shouldReturn401WhenNotAuthenticated() throws Exception {
            HouseholdInvitationUpdateRequest request = HouseholdInvitationUpdateRequest.builder()
                    .proposedRole(HouseholdRole.ADMIN)
                    .build();

            mockMvc.perform(put("/api/households/{householdId}/invitations/{invitationId}", householdId, invitationId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());

            verifyNoInteractions(householdInvitationService);
        }
    }

    @Nested
    @DisplayName("POST /api/households/{householdId}/invitations/{invitationId}/resend")
    class ResendInvitation {

        @Test
        @DisplayName("should resend invitation when valid request provided")
        void shouldResendInvitationWhenValidRequestProvided() throws Exception {
            ResendInvitationRequest request = ResendInvitationRequest.builder()
                    .expiresAt(LocalDateTime.now().plusDays(7))
                    .build();

            when(householdInvitationService.resendInvitation(eq(householdId), eq(invitationId), any(ResendInvitationRequest.class)))
                    .thenReturn(sampleInvitationResponse);

            mockMvc.perform(post("/api/households/{householdId}/invitations/{invitationId}/resend", householdId, invitationId)
                            .with(jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.status").value("PENDING"));

            verify(securityService).canModifyHousehold(householdId);
            verify(householdInvitationService).resendInvitation(eq(householdId), eq(invitationId), any(ResendInvitationRequest.class));
        }

        @Test
        @DisplayName("should return 403 when user lacks permission")
        void shouldReturn403WhenUserLacksPermission() throws Exception {
            when(securityService.canModifyHousehold(householdId)).thenReturn(false);

            ResendInvitationRequest request = ResendInvitationRequest.builder()
                    .expiresAt(LocalDateTime.now().plusDays(7))
                    .build();

            mockMvc.perform(post("/api/households/{householdId}/invitations/{invitationId}/resend", householdId, invitationId)
                            .with(jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());

            verify(securityService).canModifyHousehold(householdId);
            verifyNoInteractions(householdInvitationService);
        }

        @Test
        @DisplayName("should return 404 when invitation not found")
        void shouldReturn404WhenInvitationNotFound() throws Exception {
            ResendInvitationRequest request = ResendInvitationRequest.builder()
                    .expiresAt(LocalDateTime.now().plusDays(7))
                    .build();

            when(householdInvitationService.resendInvitation(eq(householdId), eq(invitationId), any(ResendInvitationRequest.class)))
                    .thenThrow(new NotFoundException("Invitation not found with ID: " + invitationId));

            mockMvc.perform(post("/api/households/{householdId}/invitations/{invitationId}/resend", householdId, invitationId)
                            .with(jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error_code").value("RESOURCE_NOT_FOUND"));

            verify(householdInvitationService).resendInvitation(eq(householdId), eq(invitationId), any(ResendInvitationRequest.class));
        }

        @Test
        @DisplayName("should return 409 when invitation cannot be resent")
        void shouldReturn409WhenInvitationCannotBeResent() throws Exception {
            ResendInvitationRequest request = ResendInvitationRequest.builder()
                    .expiresAt(LocalDateTime.now().plusDays(7))
                    .build();

            when(householdInvitationService.resendInvitation(eq(householdId), eq(invitationId), any(ResendInvitationRequest.class)))
                    .thenThrow(new ResourceStateException("Can only resend pending invitations. Current status: ACCEPTED"));

            mockMvc.perform(post("/api/households/{householdId}/invitations/{invitationId}/resend", householdId, invitationId)
                            .with(jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error_code").value("RESOURCE_STATE_CONFLICT"));

            verify(householdInvitationService).resendInvitation(eq(householdId), eq(invitationId), any(ResendInvitationRequest.class));
        }

        @Test
        @DisplayName("should return 401 when not authenticated")
        void shouldReturn401WhenNotAuthenticated() throws Exception {
            ResendInvitationRequest request = ResendInvitationRequest.builder()
                    .expiresAt(LocalDateTime.now().plusDays(7))
                    .build();

            mockMvc.perform(post("/api/households/{householdId}/invitations/{invitationId}/resend", householdId, invitationId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());

            verifyNoInteractions(householdInvitationService);
        }
    }

    @Nested
    @DisplayName("Internal Error Handling")
    class InternalErrorHandling {

        @Test
        @DisplayName("should return 500 when unexpected service exception occurs")
        void shouldReturn500WhenUnexpectedExceptionOccurs() throws Exception {
            when(householdInvitationService.getMyInvitations())
                    .thenThrow(new RuntimeException("Database connection failed"));

            mockMvc.perform(get("/api/invitations/my")
                            .with(jwt().jwt(jwt)))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.error_code").value("INTERNAL_SERVER_ERROR"))
                    .andExpect(jsonPath("$.correlation_id").exists());
        }
    }

    @Nested
    @DisplayName("Error Response Format")
    class ErrorResponseFormat {

        @Test
        @DisplayName("should include correlation_id in all error responses")
        void shouldIncludeCorrelationIdInErrors() throws Exception {
            when(householdInvitationService.getInvitationById(householdId, invitationId))
                    .thenThrow(new NotFoundException("Invitation not found with ID: " + invitationId));

            mockMvc.perform(get("/api/households/{householdId}/invitations/{invitationId}", householdId, invitationId)
                            .with(jwt().jwt(jwt)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.correlation_id").exists())
                    .andExpect(jsonPath("$.timestamp").exists())
                    .andExpect(jsonPath("$.message").exists())
                    .andExpect(jsonPath("$.error_code").exists());
        }

        @Test
        @DisplayName("should include message in validation errors")
        void shouldIncludeMessageInValidationErrors() throws Exception {
            HouseholdInvitationRequest invalidRequest = HouseholdInvitationRequest.builder()
                    .invitedUserEmail("invalid-email")
                    .proposedRole(HouseholdRole.MEMBER)
                    .build();

            mockMvc.perform(post("/api/households/{householdId}/invitations", householdId)
                            .with(jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(invalidRequest)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").isNotEmpty());
        }
    }
}