package org.cubord.cubordbackend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.cubord.cubordbackend.config.TestSecurityConfig;
import org.cubord.cubordbackend.dto.HouseholdMemberRequest;
import org.cubord.cubordbackend.dto.HouseholdMemberResponse;
import org.cubord.cubordbackend.dto.HouseholdMemberRoleUpdateRequest;
import org.cubord.cubordbackend.domain.HouseholdRole;
import org.cubord.cubordbackend.exception.ConflictException;
import org.cubord.cubordbackend.exception.NotFoundException;
import org.cubord.cubordbackend.service.HouseholdMemberService;
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
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.Mockito.*;

@WebMvcTest(HouseholdMemberController.class)
@Import(TestSecurityConfig.class)
@ActiveProfiles("test")
class HouseholdMemberControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private HouseholdMemberService householdMemberService;

    private UUID sampleHouseholdId;
    private UUID sampleUserId;
    private UUID sampleMemberId;
    private Jwt jwt;
    private HouseholdMemberResponse sampleMemberResponse;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        sampleHouseholdId = UUID.randomUUID();
        sampleUserId = UUID.randomUUID();
        sampleMemberId = UUID.randomUUID();
        
        jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("sub", "test-user")
                .claim("email", "test@example.com")
                .build();

        sampleMemberResponse = new HouseholdMemberResponse();
        sampleMemberResponse.setId(sampleMemberId);
        sampleMemberResponse.setUserId(sampleUserId);
        sampleMemberResponse.setHouseholdId(sampleHouseholdId);
        sampleMemberResponse.setRole(HouseholdRole.MEMBER);
        sampleMemberResponse.setCreatedAt(LocalDateTime.now());
    }

    @Nested
    @DisplayName("POST /api/households/{householdId}/members")
    class AddMember {
        @Test
        @DisplayName("should add member when valid request provided")
        void shouldAddMemberWhenValidRequestProvided() throws Exception {
            HouseholdMemberRequest request = new HouseholdMemberRequest();
            request.setUserId(sampleUserId);
            request.setRole(HouseholdRole.MEMBER);

            when(householdMemberService.addMemberToHousehold(eq(sampleHouseholdId), any(HouseholdMemberRequest.class), any(JwtAuthenticationToken.class)))
                    .thenReturn(sampleMemberResponse);

            mockMvc.perform(post("/api/households/{householdId}/members", sampleHouseholdId)
                            .with(jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(new ObjectMapper().writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.userId").value(sampleUserId.toString()))
                    .andExpect(jsonPath("$.role").value("MEMBER"));
        }

        @Test
        @DisplayName("should return 400 when request is invalid")
        void shouldReturn400WhenRequestIsInvalid() throws Exception {
            HouseholdMemberRequest invalidRequest = new HouseholdMemberRequest();
            // Missing required fields

            mockMvc.perform(post("/api/households/{householdId}/members", sampleHouseholdId)
                            .with(jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(new ObjectMapper().writeValueAsString(invalidRequest)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 401 when not authenticated")
        void shouldReturn401WhenNotAuthenticated() throws Exception {
            HouseholdMemberRequest request = new HouseholdMemberRequest();
            request.setUserId(sampleUserId);
            request.setRole(HouseholdRole.MEMBER);

            mockMvc.perform(post("/api/households/{householdId}/members", sampleHouseholdId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(new ObjectMapper().writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("should return 404 when household not found")
        void shouldReturn404WhenHouseholdNotFound() throws Exception {
            HouseholdMemberRequest request = new HouseholdMemberRequest();
            request.setUserId(sampleUserId);
            request.setRole(HouseholdRole.MEMBER);

            when(householdMemberService.addMemberToHousehold(eq(sampleHouseholdId), any(HouseholdMemberRequest.class), any(JwtAuthenticationToken.class)))
                    .thenThrow(new NotFoundException("Household not found"));

            mockMvc.perform(post("/api/households/{householdId}/members", sampleHouseholdId)
                            .with(jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(new ObjectMapper().writeValueAsString(request)))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("should return 403 when user doesn't have permission")
        void shouldReturn403WhenUserDoesntHavePermission() throws Exception {
            HouseholdMemberRequest request = new HouseholdMemberRequest();
            request.setUserId(sampleUserId);
            request.setRole(HouseholdRole.MEMBER);

            when(householdMemberService.addMemberToHousehold(eq(sampleHouseholdId), any(HouseholdMemberRequest.class), any(JwtAuthenticationToken.class)))
                    .thenThrow(new AccessDeniedException("You don't have access to this household"));

            mockMvc.perform(post("/api/households/{householdId}/members", sampleHouseholdId)
                            .with(jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(new ObjectMapper().writeValueAsString(request)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("should return 409 when member already exists")
        void shouldReturn409WhenMemberAlreadyExists() throws Exception {
            HouseholdMemberRequest request = new HouseholdMemberRequest();
            request.setUserId(sampleUserId);
            request.setRole(HouseholdRole.MEMBER);

            when(householdMemberService.addMemberToHousehold(eq(sampleHouseholdId), any(HouseholdMemberRequest.class), any(JwtAuthenticationToken.class)))
                    .thenThrow(new ConflictException("User is already a member of this household"));

            mockMvc.perform(post("/api/households/{householdId}/members", sampleHouseholdId)
                            .with(jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(new ObjectMapper().writeValueAsString(request)))
                    .andExpect(status().isConflict());
        }
    }

    @Nested
    @DisplayName("GET /api/households/{householdId}/members")
    class GetHouseholdMembers {
        @Test
        @DisplayName("should return all household members")
        void shouldReturnAllHouseholdMembers() throws Exception {
            List<HouseholdMemberResponse> members = List.of(sampleMemberResponse);
            when(householdMemberService.getHouseholdMembers(eq(sampleHouseholdId), any(JwtAuthenticationToken.class)))
                    .thenReturn(members);

            mockMvc.perform(get("/api/households/{householdId}/members", sampleHouseholdId)
                            .with(jwt().jwt(jwt)))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(1));
        }

        @Test
        @DisplayName("should return empty list when household has no members")
        void shouldReturnEmptyListWhenHouseholdHasNoMembers() throws Exception {
            when(householdMemberService.getHouseholdMembers(eq(sampleHouseholdId), any(JwtAuthenticationToken.class)))
                    .thenReturn(Collections.emptyList());

            mockMvc.perform(get("/api/households/{householdId}/members", sampleHouseholdId)
                            .with(jwt().jwt(jwt)))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(0));
        }

        @Test
        @DisplayName("should return 401 when not authenticated")
        void shouldReturn401WhenNotAuthenticated() throws Exception {
            mockMvc.perform(get("/api/households/{householdId}/members", sampleHouseholdId))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("should return 404 when household not found")
        void shouldReturn404WhenHouseholdNotFound() throws Exception {
            when(householdMemberService.getHouseholdMembers(eq(sampleHouseholdId), any(JwtAuthenticationToken.class)))
                    .thenThrow(new NotFoundException("Household not found"));

            mockMvc.perform(get("/api/households/{householdId}/members", sampleHouseholdId)
                            .with(jwt().jwt(jwt)))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("should return 403 when user doesn't have permission")
        void shouldReturn403WhenUserDoesntHavePermission() throws Exception {
            when(householdMemberService.getHouseholdMembers(eq(sampleHouseholdId), any(JwtAuthenticationToken.class)))
                    .thenThrow(new AccessDeniedException("You don't have access to this household"));

            mockMvc.perform(get("/api/households/{householdId}/members", sampleHouseholdId)
                            .with(jwt().jwt(jwt)))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("GET /api/households/{householdId}/members/{memberId}")
    class GetMemberById {
        
        @Test
        @DisplayName("should return member details when valid IDs provided")
        void shouldReturnMemberDetailsWhenValidIdsProvided() throws Exception {
            when(householdMemberService.getMemberById(eq(sampleHouseholdId), eq(sampleMemberId), any(JwtAuthenticationToken.class)))
                    .thenReturn(sampleMemberResponse);

            mockMvc.perform(get("/api/households/{householdId}/members/{memberId}", sampleHouseholdId, sampleMemberId)
                            .with(jwt().jwt(jwt)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(sampleMemberId.toString()))
                    .andExpect(jsonPath("$.userId").value(sampleUserId.toString()));
        }

        @Test
        @DisplayName("should return 404 when household not found")
        void shouldReturn404WhenHouseholdNotFound() throws Exception {
            when(householdMemberService.getMemberById(eq(sampleHouseholdId), eq(sampleMemberId), any(JwtAuthenticationToken.class)))
                    .thenThrow(new NotFoundException("Household not found"));

            mockMvc.perform(get("/api/households/{householdId}/members/{memberId}", sampleHouseholdId, sampleMemberId)
                            .with(jwt().jwt(jwt)))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("should return 404 when member not found")
        void shouldReturn404WhenMemberNotFound() throws Exception {
            when(householdMemberService.getMemberById(eq(sampleHouseholdId), eq(sampleMemberId), any(JwtAuthenticationToken.class)))
                    .thenThrow(new NotFoundException("Member not found"));

            mockMvc.perform(get("/api/households/{householdId}/members/{memberId}", sampleHouseholdId, sampleMemberId)
                            .with(jwt().jwt(jwt)))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("should return 403 when user doesn't have permission")
        void shouldReturn403WhenUserDoesntHavePermission() throws Exception {
            when(householdMemberService.getMemberById(eq(sampleHouseholdId), eq(sampleMemberId), any(JwtAuthenticationToken.class)))
                    .thenThrow(new AccessDeniedException("You don't have permission"));

            mockMvc.perform(get("/api/households/{householdId}/members/{memberId}", sampleHouseholdId, sampleMemberId)
                            .with(jwt().jwt(jwt)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("should return 401 when not authenticated")
        void shouldReturn401WhenNotAuthenticated() throws Exception {
            mockMvc.perform(get("/api/households/{householdId}/members/{memberId}", sampleHouseholdId, sampleMemberId))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("should return 400 when invalid UUID format provided")
        void shouldReturn400WhenInvalidUuidProvided() throws Exception {
            mockMvc.perform(get("/api/households/{householdId}/members/{memberId}", "invalid-uuid", "invalid-uuid")
                            .with(jwt().jwt(jwt)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("DELETE /api/households/{householdId}/members/{memberId}")
    class RemoveMember {
        
        @Test
        @DisplayName("should remove member when valid IDs provided")
        void shouldRemoveMemberWhenValidIdsProvided() throws Exception {
            doNothing().when(householdMemberService).removeMember(eq(sampleHouseholdId), eq(sampleMemberId), any(JwtAuthenticationToken.class));

            mockMvc.perform(delete("/api/households/{householdId}/members/{memberId}", sampleHouseholdId, sampleMemberId)
                            .with(jwt().jwt(jwt)))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("should return 404 when household not found")
        void shouldReturn404WhenHouseholdNotFound() throws Exception {
            doThrow(new NotFoundException("Household not found"))
                    .when(householdMemberService).removeMember(eq(sampleHouseholdId), eq(sampleMemberId), any(JwtAuthenticationToken.class));

            mockMvc.perform(delete("/api/households/{householdId}/members/{memberId}", sampleHouseholdId, sampleMemberId)
                            .with(jwt().jwt(jwt)))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("should return 404 when member not found")
        void shouldReturn404WhenMemberNotFound() throws Exception {
            doThrow(new NotFoundException("Member not found"))
                    .when(householdMemberService).removeMember(eq(sampleHouseholdId), eq(sampleMemberId), any(JwtAuthenticationToken.class));

            mockMvc.perform(delete("/api/households/{householdId}/members/{memberId}", sampleHouseholdId, sampleMemberId)
                            .with(jwt().jwt(jwt)))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("should return 403 when user doesn't have permission")
        void shouldReturn403WhenUserDoesntHavePermission() throws Exception {
            doThrow(new AccessDeniedException("You don't have permission"))
                    .when(householdMemberService).removeMember(eq(sampleHouseholdId), eq(sampleMemberId), any(JwtAuthenticationToken.class));

            mockMvc.perform(delete("/api/households/{householdId}/members/{memberId}", sampleHouseholdId, sampleMemberId)
                            .with(jwt().jwt(jwt)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("should return 400 when trying to remove owner")
        void shouldReturn400WhenTryingToRemoveOwner() throws Exception {
            doThrow(new IllegalStateException("Cannot remove the owner"))
                    .when(householdMemberService).removeMember(eq(sampleHouseholdId), eq(sampleMemberId), any(JwtAuthenticationToken.class));

            mockMvc.perform(delete("/api/households/{householdId}/members/{memberId}", sampleHouseholdId, sampleMemberId)
                            .with(jwt().jwt(jwt)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 401 when not authenticated")
        void shouldReturn401WhenNotAuthenticated() throws Exception {
            mockMvc.perform(delete("/api/households/{householdId}/members/{memberId}", sampleHouseholdId, sampleMemberId))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("PUT /api/households/{householdId}/members/{memberId}/role")
    class UpdateMemberRole {
        
        @Test
        @DisplayName("should update member role when valid request provided")
        void shouldUpdateMemberRoleWhenValidRequestProvided() throws Exception {
            HouseholdMemberRoleUpdateRequest request = new HouseholdMemberRoleUpdateRequest();
            request.setRole(HouseholdRole.ADMIN);

            HouseholdMemberResponse updatedResponse = HouseholdMemberResponse.builder()
                    .id(sampleMemberId)
                    .userId(sampleUserId)
                    .role(HouseholdRole.ADMIN)
                    .build();

            when(householdMemberService.updateMemberRole(eq(sampleHouseholdId), eq(sampleMemberId), eq(HouseholdRole.ADMIN), any(JwtAuthenticationToken.class)))
                    .thenReturn(updatedResponse);

            mockMvc.perform(put("/api/households/{householdId}/members/{memberId}/role", sampleHouseholdId, sampleMemberId)
                            .with(jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(sampleMemberId.toString()))
                    .andExpect(jsonPath("$.role").value("ADMIN"));
        }

        @Test
        @DisplayName("should return 400 when request is invalid")
        void shouldReturn400WhenRequestIsInvalid() throws Exception {
            mockMvc.perform(put("/api/households/{householdId}/members/{memberId}/role", sampleHouseholdId, sampleMemberId)
                            .with(jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 404 when household not found")
        void shouldReturn404WhenHouseholdNotFound() throws Exception {
            HouseholdMemberRoleUpdateRequest request = new HouseholdMemberRoleUpdateRequest();
            request.setRole(HouseholdRole.ADMIN);

            when(householdMemberService.updateMemberRole(eq(sampleHouseholdId), eq(sampleMemberId), eq(HouseholdRole.ADMIN), any(JwtAuthenticationToken.class)))
                    .thenThrow(new NotFoundException("Household not found"));

            mockMvc.perform(put("/api/households/{householdId}/members/{memberId}/role", sampleHouseholdId, sampleMemberId)
                            .with(jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("should return 404 when member not found")
        void shouldReturn404WhenMemberNotFound() throws Exception {
            HouseholdMemberRoleUpdateRequest request = new HouseholdMemberRoleUpdateRequest();
            request.setRole(HouseholdRole.ADMIN);

            when(householdMemberService.updateMemberRole(eq(sampleHouseholdId), eq(sampleMemberId), eq(HouseholdRole.ADMIN), any(JwtAuthenticationToken.class)))
                    .thenThrow(new NotFoundException("Member not found"));

            mockMvc.perform(put("/api/households/{householdId}/members/{memberId}/role", sampleHouseholdId, sampleMemberId)
                            .with(jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("should return 403 when user doesn't have permission")
        void shouldReturn403WhenUserDoesntHavePermission() throws Exception {
            HouseholdMemberRoleUpdateRequest request = new HouseholdMemberRoleUpdateRequest();
            request.setRole(HouseholdRole.ADMIN);

            when(householdMemberService.updateMemberRole(eq(sampleHouseholdId), eq(sampleMemberId), eq(HouseholdRole.ADMIN), any(JwtAuthenticationToken.class)))
                    .thenThrow(new AccessDeniedException("You don't have permission"));

            mockMvc.perform(put("/api/households/{householdId}/members/{memberId}/role", sampleHouseholdId, sampleMemberId)
                            .with(jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("should return 400 when trying to set role to OWNER")
        void shouldReturn400WhenTryingToSetRoleToOwner() throws Exception {
            HouseholdMemberRoleUpdateRequest request = new HouseholdMemberRoleUpdateRequest();
            request.setRole(HouseholdRole.OWNER);

            when(householdMemberService.updateMemberRole(eq(sampleHouseholdId), eq(sampleMemberId), eq(HouseholdRole.OWNER), any(JwtAuthenticationToken.class)))
                    .thenThrow(new IllegalArgumentException("Cannot set role to OWNER"));

            mockMvc.perform(put("/api/households/{householdId}/members/{memberId}/role", sampleHouseholdId, sampleMemberId)
                            .with(jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 401 when not authenticated")
        void shouldReturn401WhenNotAuthenticated() throws Exception {
            HouseholdMemberRoleUpdateRequest request = new HouseholdMemberRoleUpdateRequest();
            request.setRole(HouseholdRole.ADMIN);

            mockMvc.perform(put("/api/households/{householdId}/members/{memberId}/role", sampleHouseholdId, sampleMemberId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());
        }
    }
}