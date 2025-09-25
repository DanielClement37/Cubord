
package org.cubord.cubordbackend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.cubord.cubordbackend.config.TestSecurityConfig;
import org.cubord.cubordbackend.domain.HouseholdRole;
import org.cubord.cubordbackend.domain.InvitationStatus;
import org.cubord.cubordbackend.dto.householdInvitation.HouseholdInvitationRequest;
import org.cubord.cubordbackend.dto.householdInvitation.HouseholdInvitationResponse;
import org.cubord.cubordbackend.dto.householdInvitation.HouseholdInvitationUpdateRequest;
import org.cubord.cubordbackend.dto.householdInvitation.ResendInvitationRequest;
import org.cubord.cubordbackend.exception.ConflictException;
import org.cubord.cubordbackend.exception.NotFoundException;
import org.cubord.cubordbackend.service.HouseholdInvitationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

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

@WebMvcTest(HouseholdInvitationController.class)
@Import(TestSecurityConfig.class)
@ActiveProfiles("test")
class HouseholdInvitationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private HouseholdInvitationService householdInvitationService;

    @Autowired
    private ObjectMapper objectMapper;

    private UUID householdId;
    private UUID invitationId;
    private UUID invitedUserId;
    private Jwt jwt;
    private HouseholdInvitationResponse sampleInvitationResponse;

    @BeforeEach
    void setUp() {
        householdId = UUID.randomUUID();
        invitationId = UUID.randomUUID();
        invitedUserId = UUID.randomUUID();
        
        jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("sub", "test-user")
                .claim("email", "test@example.com")
                .build();

        sampleInvitationResponse = HouseholdInvitationResponse.builder()
                .id(invitationId)
                .invitedUserId(invitedUserId)
                .invitedUserEmail("invited@example.com")
                .invitedUserName("Invited User")
                .householdId(householdId)
                .householdName("Test Household")
                .invitedByUserId(UUID.randomUUID())
                .invitedByUserName("Inviter User")
                .proposedRole(HouseholdRole.MEMBER)
                .status(InvitationStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusDays(7))
                .build();
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

            when(householdInvitationService.sendInvitation(eq(householdId), any(HouseholdInvitationRequest.class), any(JwtAuthenticationToken.class)))
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
        }

        @Test
        @DisplayName("should send invitation by user ID when provided")
        void shouldSendInvitationByUserIdWhenProvided() throws Exception {
            HouseholdInvitationRequest request = HouseholdInvitationRequest.builder()
                    .invitedUserId(invitedUserId)
                    .proposedRole(HouseholdRole.ADMIN)
                    .build();

            when(householdInvitationService.sendInvitation(eq(householdId), any(HouseholdInvitationRequest.class), any(JwtAuthenticationToken.class)))
                    .thenReturn(sampleInvitationResponse);

            mockMvc.perform(post("/api/households/{householdId}/invitations", householdId)
                            .with(jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.invitedUserId").value(invitedUserId.toString()));
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

            when(householdInvitationService.sendInvitation(eq(householdId), any(HouseholdInvitationRequest.class), any(JwtAuthenticationToken.class)))
                    .thenReturn(sampleInvitationResponse);

            mockMvc.perform(post("/api/households/{householdId}/invitations", householdId)
                            .with(jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated());
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
        }

        @Test
        @DisplayName("should return 400 when both email and userId are missing")
        void shouldReturn400WhenBothEmailAndUserIdAreMissing() throws Exception {
            HouseholdInvitationRequest request = HouseholdInvitationRequest.builder()
                    .proposedRole(HouseholdRole.MEMBER)
                    .build();

            // Mock the service to throw an exception for invalid input
            when(householdInvitationService.sendInvitation(eq(householdId), any(HouseholdInvitationRequest.class), any(JwtAuthenticationToken.class)))
                    .thenThrow(new IllegalArgumentException("Either invitedUserEmail or invitedUserId must be provided"));

            mockMvc.perform(post("/api/households/{householdId}/invitations", householdId)
                            .with(jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }


        @Test
        @DisplayName("should return 400 when expiry date is in the past")
        void shouldReturn400WhenExpiryDateIsInThePast() throws Exception {
            HouseholdInvitationRequest request = HouseholdInvitationRequest.builder()
                    .invitedUserEmail("invited@example.com")
                    .proposedRole(HouseholdRole.MEMBER)
                    .expiresAt(LocalDateTime.now().minusDays(1))
                    .build();

            when(householdInvitationService.sendInvitation(eq(householdId), any(HouseholdInvitationRequest.class), any(JwtAuthenticationToken.class)))
                    .thenThrow(new IllegalArgumentException("Expiry date cannot be in the past"));

            mockMvc.perform(post("/api/households/{householdId}/invitations", householdId)
                            .with(jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
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
        }

        @Test
        @DisplayName("should return 404 when household not found")
        void shouldReturn404WhenHouseholdNotFound() throws Exception {
            HouseholdInvitationRequest request = HouseholdInvitationRequest.builder()
                    .invitedUserEmail("invited@example.com")
                    .proposedRole(HouseholdRole.MEMBER)
                    .build();

            when(householdInvitationService.sendInvitation(eq(householdId), any(HouseholdInvitationRequest.class), any(JwtAuthenticationToken.class)))
                    .thenThrow(new NotFoundException("Household not found"));

            mockMvc.perform(post("/api/households/{householdId}/invitations", householdId)
                            .with(jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("should return 404 when invited user not found")
        void shouldReturn404WhenInvitedUserNotFound() throws Exception {
            HouseholdInvitationRequest request = HouseholdInvitationRequest.builder()
                    .invitedUserEmail("nonexistent@example.com")
                    .proposedRole(HouseholdRole.MEMBER)
                    .build();

            when(householdInvitationService.sendInvitation(eq(householdId), any(HouseholdInvitationRequest.class), any(JwtAuthenticationToken.class)))
                    .thenThrow(new NotFoundException("User not found"));

            mockMvc.perform(post("/api/households/{householdId}/invitations", householdId)
                            .with(jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("should return 403 when user lacks permission")
        void shouldReturn403WhenUserLacksPermission() throws Exception {
            HouseholdInvitationRequest request = HouseholdInvitationRequest.builder()
                    .invitedUserEmail("invited@example.com")
                    .proposedRole(HouseholdRole.MEMBER)
                    .build();

            when(householdInvitationService.sendInvitation(eq(householdId), any(HouseholdInvitationRequest.class), any(JwtAuthenticationToken.class)))
                    .thenThrow(new AccessDeniedException("You don't have permission to send invitations"));

            mockMvc.perform(post("/api/households/{householdId}/invitations", householdId)
                            .with(jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("should return 409 when user already invited or member")
        void shouldReturn409WhenUserAlreadyInvitedOrMember() throws Exception {
            HouseholdInvitationRequest request = HouseholdInvitationRequest.builder()
                    .invitedUserEmail("invited@example.com")
                    .proposedRole(HouseholdRole.MEMBER)
                    .build();

            when(householdInvitationService.sendInvitation(eq(householdId), any(HouseholdInvitationRequest.class), any(JwtAuthenticationToken.class)))
                    .thenThrow(new ConflictException("User already has a pending invitation or is already a member"));

            mockMvc.perform(post("/api/households/{householdId}/invitations", householdId)
                            .with(jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("should return 400 when trying to invite as OWNER")
        void shouldReturn400WhenTryingToInviteAsOwner() throws Exception {
            HouseholdInvitationRequest request = HouseholdInvitationRequest.builder()
                    .invitedUserEmail("invited@example.com")
                    .proposedRole(HouseholdRole.OWNER)
                    .build();

            when(householdInvitationService.sendInvitation(eq(householdId), any(HouseholdInvitationRequest.class), any(JwtAuthenticationToken.class)))
                    .thenThrow(new IllegalArgumentException("Cannot invite user as OWNER"));

            mockMvc.perform(post("/api/households/{householdId}/invitations", householdId)
                            .with(jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 400 when trying to invite self")
        void shouldReturn400WhenTryingToInviteSelf() throws Exception {
            HouseholdInvitationRequest request = HouseholdInvitationRequest.builder()
                    .invitedUserEmail("test@example.com") // Same as JWT email
                    .proposedRole(HouseholdRole.MEMBER)
                    .build();

            when(householdInvitationService.sendInvitation(eq(householdId), any(HouseholdInvitationRequest.class), any(JwtAuthenticationToken.class)))
                    .thenThrow(new IllegalArgumentException("Cannot invite yourself"));

            mockMvc.perform(post("/api/households/{householdId}/invitations", householdId)
                            .with(jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
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
        }
    }

    @Nested
    @DisplayName("GET /api/households/{householdId}/invitations")
    class GetHouseholdInvitations {
        
        @Test
        @DisplayName("should return all household invitations")
        void shouldReturnAllHouseholdInvitations() throws Exception {
            List<HouseholdInvitationResponse> invitations = List.of(sampleInvitationResponse);
            
            when(householdInvitationService.getHouseholdInvitations(eq(householdId), any(JwtAuthenticationToken.class)))
                    .thenReturn(invitations);

            mockMvc.perform(get("/api/households/{householdId}/invitations", householdId)
                            .with(jwt().jwt(jwt)))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].status").value("PENDING"));
        }

        @Test
        @DisplayName("should return empty list when no invitations exist")
        void shouldReturnEmptyListWhenNoInvitationsExist() throws Exception {
            when(householdInvitationService.getHouseholdInvitations(eq(householdId), any(JwtAuthenticationToken.class)))
                    .thenReturn(Collections.emptyList());

            mockMvc.perform(get("/api/households/{householdId}/invitations", householdId)
                            .with(jwt().jwt(jwt)))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(0));
        }

        @Test
        @DisplayName("should support filtering by status")
        void shouldSupportFilteringByStatus() throws Exception {
            List<HouseholdInvitationResponse> pendingInvitations = List.of(sampleInvitationResponse);
            
            when(householdInvitationService.getHouseholdInvitationsByStatus(eq(householdId), eq(InvitationStatus.PENDING), any(JwtAuthenticationToken.class)))
                    .thenReturn(pendingInvitations);

            mockMvc.perform(get("/api/households/{householdId}/invitations", householdId)
                            .param("status", "PENDING")
                            .with(jwt().jwt(jwt)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].status").value("PENDING"));
        }

        @Test
        @DisplayName("should support filtering by multiple statuses")
        void shouldSupportFilteringByMultipleStatuses() throws Exception {
            List<HouseholdInvitationResponse> invitations = List.of(sampleInvitationResponse);
            
            when(householdInvitationService.getHouseholdInvitationsByStatus(eq(householdId), eq(InvitationStatus.ACCEPTED), any(JwtAuthenticationToken.class)))
                    .thenReturn(invitations);

            mockMvc.perform(get("/api/households/{householdId}/invitations", householdId)
                            .param("status", "ACCEPTED")
                            .with(jwt().jwt(jwt)))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("should return 400 when invalid status provided")
        void shouldReturn400WhenInvalidStatusProvided() throws Exception {
            mockMvc.perform(get("/api/households/{householdId}/invitations", householdId)
                            .param("status", "INVALID_STATUS")
                            .with(jwt().jwt(jwt)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 401 when not authenticated")
        void shouldReturn401WhenNotAuthenticated() throws Exception {
            mockMvc.perform(get("/api/households/{householdId}/invitations", householdId))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("should return 404 when household not found")
        void shouldReturn404WhenHouseholdNotFound() throws Exception {
            when(householdInvitationService.getHouseholdInvitations(eq(householdId), any(JwtAuthenticationToken.class)))
                    .thenThrow(new NotFoundException("Household not found"));

            mockMvc.perform(get("/api/households/{householdId}/invitations", householdId)
                            .with(jwt().jwt(jwt)))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("should return 403 when user lacks permission")
        void shouldReturn403WhenUserLacksPermission() throws Exception {
            when(householdInvitationService.getHouseholdInvitations(eq(householdId), any(JwtAuthenticationToken.class)))
                    .thenThrow(new AccessDeniedException("You don't have permission to view invitations"));

            mockMvc.perform(get("/api/households/{householdId}/invitations", householdId)
                            .with(jwt().jwt(jwt)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("should return 400 when invalid UUID format provided")
        void shouldReturn400WhenInvalidUuidFormatProvided() throws Exception {
            mockMvc.perform(get("/api/households/{householdId}/invitations", "invalid-uuid")
                            .with(jwt().jwt(jwt)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("GET /api/households/{householdId}/invitations/{invitationId}")
    class GetInvitationById {

        @Test
        @DisplayName("should return invitation details when valid IDs provided")
        void shouldReturnInvitationDetailsWhenValidIdsProvided() throws Exception {
            when(householdInvitationService.getInvitationById(eq(householdId), eq(invitationId), any(JwtAuthenticationToken.class)))
                    .thenReturn(sampleInvitationResponse);

            mockMvc.perform(get("/api/households/{householdId}/invitations/{invitationId}", householdId, invitationId)
                            .with(jwt().jwt(jwt)))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.id").value(invitationId.toString()))
                    .andExpect(jsonPath("$.householdId").value(householdId.toString()));
        }

        @Test
        @DisplayName("should return 404 when invitation not found")
        void shouldReturn404WhenInvitationNotFound() throws Exception {
            when(householdInvitationService.getInvitationById(eq(householdId), eq(invitationId), any(JwtAuthenticationToken.class)))
                    .thenThrow(new NotFoundException("Invitation not found"));

            mockMvc.perform(get("/api/households/{householdId}/invitations/{invitationId}", householdId, invitationId)
                            .with(jwt().jwt(jwt)))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("should return 403 when user lacks permission")
        void shouldReturn403WhenUserLacksPermission() throws Exception {
            when(householdInvitationService.getInvitationById(eq(householdId), eq(invitationId), any(JwtAuthenticationToken.class)))
                    .thenThrow(new AccessDeniedException("You don't have permission to view this invitation"));

            mockMvc.perform(get("/api/households/{householdId}/invitations/{invitationId}", householdId, invitationId)
                            .with(jwt().jwt(jwt)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("should return 401 when not authenticated")
        void shouldReturn401WhenNotAuthenticated() throws Exception {
            mockMvc.perform(get("/api/households/{householdId}/invitations/{invitationId}", householdId, invitationId))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("should return 400 when invalid UUID format provided")
        void shouldReturn400WhenInvalidUuidFormatProvided() throws Exception {
            mockMvc.perform(get("/api/households/{householdId}/invitations/{invitationId}", "invalid-uuid", "invalid-uuid")
                            .with(jwt().jwt(jwt)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("GET /api/invitations/my")
    class GetMyInvitations {
        
        @Test
        @DisplayName("should return current user's pending invitations")
        void shouldReturnCurrentUsersPendingInvitations() throws Exception {
            List<HouseholdInvitationResponse> myInvitations = List.of(sampleInvitationResponse);
            
            when(householdInvitationService.getMyInvitations(any(JwtAuthenticationToken.class)))
                    .thenReturn(myInvitations);

            mockMvc.perform(get("/api/invitations/my")
                            .with(jwt().jwt(jwt)))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(1));
        }

        @Test
        @DisplayName("should return empty list when user has no invitations")
        void shouldReturnEmptyListWhenUserHasNoInvitations() throws Exception {
            when(householdInvitationService.getMyInvitations(any(JwtAuthenticationToken.class)))
                    .thenReturn(Collections.emptyList());

            mockMvc.perform(get("/api/invitations/my")
                            .with(jwt().jwt(jwt)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(0));
        }

        @Test
        @DisplayName("should support filtering by status")
        void shouldSupportFilteringByStatus() throws Exception {
            List<HouseholdInvitationResponse> myInvitations = List.of(sampleInvitationResponse);
            
            when(householdInvitationService.getMyInvitationsByStatus(eq(InvitationStatus.PENDING), any(JwtAuthenticationToken.class)))
                    .thenReturn(myInvitations);

            mockMvc.perform(get("/api/invitations/my")
                            .param("status", "PENDING")
                            .with(jwt().jwt(jwt)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1));
        }

        @Test
        @DisplayName("should return 401 when not authenticated")
        void shouldReturn401WhenNotAuthenticated() throws Exception {
            mockMvc.perform(get("/api/invitations/my"))
                    .andExpect(status().isUnauthorized());
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

            when(householdInvitationService.acceptInvitation(eq(invitationId), any(JwtAuthenticationToken.class)))
                    .thenReturn(acceptedResponse);

            mockMvc.perform(post("/api/invitations/{invitationId}/accept", invitationId)
                            .with(jwt().jwt(jwt)))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.status").value("ACCEPTED"));
        }

        @Test
        @DisplayName("should return 404 when invitation not found")
        void shouldReturn404WhenInvitationNotFound() throws Exception {
            when(householdInvitationService.acceptInvitation(eq(invitationId), any(JwtAuthenticationToken.class)))
                    .thenThrow(new NotFoundException("Invitation not found"));

            mockMvc.perform(post("/api/invitations/{invitationId}/accept", invitationId)
                            .with(jwt().jwt(jwt)))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("should return 403 when not invited user")
        void shouldReturn403WhenNotInvitedUser() throws Exception {
            when(householdInvitationService.acceptInvitation(eq(invitationId), any(JwtAuthenticationToken.class)))
                    .thenThrow(new AccessDeniedException("You are not the invited user"));

            mockMvc.perform(post("/api/invitations/{invitationId}/accept", invitationId)
                            .with(jwt().jwt(jwt)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("should return 400 when invitation already processed")
        void shouldReturn400WhenInvitationAlreadyProcessed() throws Exception {
            when(householdInvitationService.acceptInvitation(eq(invitationId), any(JwtAuthenticationToken.class)))
                    .thenThrow(new IllegalStateException("Invitation has already been processed"));

            mockMvc.perform(post("/api/invitations/{invitationId}/accept", invitationId)
                            .with(jwt().jwt(jwt)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 400 when invitation expired")
        void shouldReturn400WhenInvitationExpired() throws Exception {
            when(householdInvitationService.acceptInvitation(eq(invitationId), any(JwtAuthenticationToken.class)))
                    .thenThrow(new IllegalStateException("Invitation has expired"));

            mockMvc.perform(post("/api/invitations/{invitationId}/accept", invitationId)
                            .with(jwt().jwt(jwt)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 409 when user already member of household")
        void shouldReturn409WhenUserAlreadyMemberOfHousehold() throws Exception {
            when(householdInvitationService.acceptInvitation(eq(invitationId), any(JwtAuthenticationToken.class)))
                    .thenThrow(new ConflictException("User is already a member of this household"));

            mockMvc.perform(post("/api/invitations/{invitationId}/accept", invitationId)
                            .with(jwt().jwt(jwt)))
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("should return 401 when not authenticated")
        void shouldReturn401WhenNotAuthenticated() throws Exception {
            mockMvc.perform(post("/api/invitations/{invitationId}/accept", invitationId))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("should return 400 when invalid UUID format provided")
        void shouldReturn400WhenInvalidUuidFormatProvided() throws Exception {
            mockMvc.perform(post("/api/invitations/{invitationId}/accept", "invalid-uuid")
                            .with(jwt().jwt(jwt)))
                    .andExpect(status().isBadRequest());
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

            when(householdInvitationService.declineInvitation(eq(invitationId), any(JwtAuthenticationToken.class)))
                    .thenReturn(declinedResponse);

            mockMvc.perform(post("/api/invitations/{invitationId}/decline", invitationId)
                            .with(jwt().jwt(jwt)))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.status").value("DECLINED"));
        }

        @Test
        @DisplayName("should return 404 when invitation not found")
        void shouldReturn404WhenInvitationNotFound() throws Exception {
            when(householdInvitationService.declineInvitation(eq(invitationId), any(JwtAuthenticationToken.class)))
                    .thenThrow(new NotFoundException("Invitation not found"));

            mockMvc.perform(post("/api/invitations/{invitationId}/decline", invitationId)
                            .with(jwt().jwt(jwt)))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("should return 403 when not invited user")
        void shouldReturn403WhenNotInvitedUser() throws Exception {
            when(householdInvitationService.declineInvitation(eq(invitationId), any(JwtAuthenticationToken.class)))
                    .thenThrow(new AccessDeniedException("You are not the invited user"));

            mockMvc.perform(post("/api/invitations/{invitationId}/decline", invitationId)
                            .with(jwt().jwt(jwt)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("should return 400 when invitation already processed")
        void shouldReturn400WhenInvitationAlreadyProcessed() throws Exception {
            when(householdInvitationService.declineInvitation(eq(invitationId), any(JwtAuthenticationToken.class)))
                    .thenThrow(new IllegalStateException("Invitation has already been processed"));

            mockMvc.perform(post("/api/invitations/{invitationId}/decline", invitationId)
                            .with(jwt().jwt(jwt)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 401 when not authenticated")
        void shouldReturn401WhenNotAuthenticated() throws Exception {
            mockMvc.perform(post("/api/invitations/{invitationId}/decline", invitationId))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("should return 400 when invalid UUID format provided")
        void shouldReturn400WhenInvalidUuidFormatProvided() throws Exception {
            mockMvc.perform(post("/api/invitations/{invitationId}/decline", "invalid-uuid")
                            .with(jwt().jwt(jwt)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("DELETE /api/households/{householdId}/invitations/{invitationId}")
    class CancelInvitation {
        
        @Test
        @DisplayName("should cancel invitation when user has permission")
        void shouldCancelInvitationWhenUserHasPermission() throws Exception {
            doNothing().when(householdInvitationService).cancelInvitation(eq(householdId), eq(invitationId), any(JwtAuthenticationToken.class));

            mockMvc.perform(delete("/api/households/{householdId}/invitations/{invitationId}", householdId, invitationId)
                            .with(jwt().jwt(jwt)))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("should return 404 when invitation not found")
        void shouldReturn404WhenInvitationNotFound() throws Exception {
            doThrow(new NotFoundException("Invitation not found"))
                    .when(householdInvitationService).cancelInvitation(eq(householdId), eq(invitationId), any(JwtAuthenticationToken.class));

            mockMvc.perform(delete("/api/households/{householdId}/invitations/{invitationId}", householdId, invitationId)
                            .with(jwt().jwt(jwt)))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("should return 403 when user lacks permission")
        void shouldReturn403WhenUserLacksPermission() throws Exception {
            doThrow(new AccessDeniedException("You don't have permission to cancel this invitation"))
                    .when(householdInvitationService).cancelInvitation(eq(householdId), eq(invitationId), any(JwtAuthenticationToken.class));

            mockMvc.perform(delete("/api/households/{householdId}/invitations/{invitationId}", householdId, invitationId)
                            .with(jwt().jwt(jwt)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("should return 400 when invitation already processed")
        void shouldReturn400WhenInvitationAlreadyProcessed() throws Exception {
            doThrow(new IllegalStateException("Cannot cancel processed invitation"))
                    .when(householdInvitationService).cancelInvitation(eq(householdId), eq(invitationId), any(JwtAuthenticationToken.class));

            mockMvc.perform(delete("/api/households/{householdId}/invitations/{invitationId}", householdId, invitationId)
                            .with(jwt().jwt(jwt)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 401 when not authenticated")
        void shouldReturn401WhenNotAuthenticated() throws Exception {
            mockMvc.perform(delete("/api/households/{householdId}/invitations/{invitationId}", householdId, invitationId))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("should return 400 when invalid UUID format provided")
        void shouldReturn400WhenInvalidUuidFormatProvided() throws Exception {
            mockMvc.perform(delete("/api/households/{householdId}/invitations/{invitationId}", "invalid-uuid", "invalid-uuid")
                            .with(jwt().jwt(jwt)))
                    .andExpect(status().isBadRequest());
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
                    .build();

            when(householdInvitationService.updateInvitation(eq(householdId), eq(invitationId), any(HouseholdInvitationUpdateRequest.class), any(JwtAuthenticationToken.class)))
                    .thenReturn(updatedResponse);

            mockMvc.perform(put("/api/households/{householdId}/invitations/{invitationId}", householdId, invitationId)
                            .with(jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.proposedRole").value("ADMIN"));
        }

        @Test
        @DisplayName("should return 404 when invitation not found")
        void shouldReturn404WhenInvitationNotFound() throws Exception {
            HouseholdInvitationUpdateRequest request = HouseholdInvitationUpdateRequest.builder()
                    .proposedRole(HouseholdRole.ADMIN)
                    .build();

            when(householdInvitationService.updateInvitation(eq(householdId), eq(invitationId), any(HouseholdInvitationUpdateRequest.class), any(JwtAuthenticationToken.class)))
                    .thenThrow(new NotFoundException("Invitation not found"));

            mockMvc.perform(put("/api/households/{householdId}/invitations/{invitationId}", householdId, invitationId)
                            .with(jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("should return 400 when trying to update to OWNER role")
        void shouldReturn400WhenTryingToUpdateToOwnerRole() throws Exception {
            HouseholdInvitationUpdateRequest request = HouseholdInvitationUpdateRequest.builder()
                    .proposedRole(HouseholdRole.OWNER)
                    .build();

            when(householdInvitationService.updateInvitation(eq(householdId), eq(invitationId), any(HouseholdInvitationUpdateRequest.class), any(JwtAuthenticationToken.class)))
                    .thenThrow(new IllegalArgumentException("Cannot set role to OWNER"));

            mockMvc.perform(put("/api/households/{householdId}/invitations/{invitationId}", householdId, invitationId)
                            .with(jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
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
        }
    }

    @Nested
    @DisplayName("POST /api/households/{householdId}/invitations/{invitationId}/resend")
    class ResendInvitation {

        @Test
        @DisplayName("should resend invitation when valid")
        void shouldResendInvitationWhenValid() throws Exception {
            ResendInvitationRequest request = ResendInvitationRequest.builder()
                    .expiresAt(LocalDateTime.now().plusDays(7))
                    .build();

            when(householdInvitationService.resendInvitation(eq(householdId), eq(invitationId), any(ResendInvitationRequest.class), any(JwtAuthenticationToken.class)))
                    .thenReturn(sampleInvitationResponse);

            mockMvc.perform(post("/api/households/{householdId}/invitations/{invitationId}/resend", householdId, invitationId)
                            .with(jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("PENDING"));
        }

        @Test
        @DisplayName("should return 404 when invitation not found")
        void shouldReturn404WhenInvitationNotFound() throws Exception {
            ResendInvitationRequest request = ResendInvitationRequest.builder().build();

            when(householdInvitationService.resendInvitation(eq(householdId), eq(invitationId), any(ResendInvitationRequest.class), any(JwtAuthenticationToken.class)))
                    .thenThrow(new NotFoundException("Invitation not found"));

            mockMvc.perform(post("/api/households/{householdId}/invitations/{invitationId}/resend", householdId, invitationId)
                            .with(jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("should return 400 when invitation already accepted")
        void shouldReturn400WhenInvitationAlreadyAccepted() throws Exception {
            ResendInvitationRequest request = ResendInvitationRequest.builder().build();

            when(householdInvitationService.resendInvitation(eq(householdId), eq(invitationId), any(ResendInvitationRequest.class), any(JwtAuthenticationToken.class)))
                    .thenThrow(new IllegalStateException("Cannot resend accepted invitation"));

            mockMvc.perform(post("/api/households/{householdId}/invitations/{invitationId}/resend", householdId, invitationId)
                            .with(jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 401 when not authenticated")
        void shouldReturn401WhenNotAuthenticated() throws Exception {
            ResendInvitationRequest request = ResendInvitationRequest.builder().build();

            mockMvc.perform(post("/api/households/{householdId}/invitations/{invitationId}/resend", householdId, invitationId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());
        }
    }
}