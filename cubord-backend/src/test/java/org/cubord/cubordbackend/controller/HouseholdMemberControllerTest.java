package org.cubord.cubordbackend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.cubord.cubordbackend.config.TestSecurityConfig;
import org.cubord.cubordbackend.domain.HouseholdRole;
import org.cubord.cubordbackend.dto.HouseholdMemberRequest;
import org.cubord.cubordbackend.dto.HouseholdMemberResponse;
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
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(HouseholdMemberController.class)
@Import(TestSecurityConfig.class)
@ActiveProfiles("test")
class HouseholdMemberControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private HouseholdMemberService householdMemberService;

    // Need to mock any dependencies of SecurityConfig in TestSecurityConfig
    @MockitoBean
    private org.cubord.cubordbackend.security.HouseholdPermissionEvaluator householdPermissionEvaluator;

    private UUID sampleHouseholdId;
    private UUID sampleUserId;
    private UUID sampleMemberId;
    private Jwt jwt;
    private HouseholdMemberResponse sampleMemberResponse;

    @BeforeEach
    void setUp() {
        sampleUserId = UUID.randomUUID();
        sampleHouseholdId = UUID.randomUUID();
        sampleMemberId = UUID.randomUUID();
        LocalDateTime createdAt = LocalDateTime.now().minusDays(7);

        // Create sample member response
        sampleMemberResponse = HouseholdMemberResponse.builder()
                .id(sampleMemberId)
                .userId(sampleUserId)
                .username("testuser")
                .householdId(sampleHouseholdId)
                .householdName("Test Household")
                .role(HouseholdRole.MEMBER)
                .createdAt(createdAt)
                .build();

        // Create mock JWT token with a future expiration time
        jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(sampleUserId.toString())
                .claim("email", "test@example.com")
                .claim("name", "Test User")
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        // Setup permission evaluator for test household
        when(householdPermissionEvaluator.hasViewPermission(any(), eq(sampleHouseholdId.toString())))
                .thenReturn(true);
        when(householdPermissionEvaluator.hasEditPermission(any(), eq(sampleHouseholdId.toString())))
                .thenReturn(true);
    }

    @Nested
    @DisplayName("POST /api/households/{householdId}/members")
    class AddMember {
        @Test
        @DisplayName("should add member when valid request provided")
        void shouldAddMemberWhenValidRequestProvided() throws Exception {
            HouseholdMemberRequest request = HouseholdMemberRequest.builder()
                    .userId(sampleUserId)
                    .role(HouseholdRole.MEMBER)
                    .build();

            when(householdMemberService.addMemberToHousehold(
                    eq(sampleHouseholdId),
                    any(HouseholdMemberRequest.class),
                    any(JwtAuthenticationToken.class)))
                    .thenReturn(sampleMemberResponse);

            mockMvc.perform(post("/api/households/" + sampleHouseholdId + "/members")
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(new ObjectMapper().writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.id").value(sampleMemberId.toString()))
                    .andExpect(jsonPath("$.userId").value(sampleUserId.toString()))
                    .andExpect(jsonPath("$.username").value("testuser"))
                    .andExpect(jsonPath("$.householdId").value(sampleHouseholdId.toString()))
                    .andExpect(jsonPath("$.role").value("MEMBER"));

            verify(householdMemberService).addMemberToHousehold(
                    eq(sampleHouseholdId),
                    any(HouseholdMemberRequest.class),
                    any(JwtAuthenticationToken.class));
        }

        @Test
        @DisplayName("should return 400 when request is invalid")
        void shouldReturn400WhenRequestIsInvalid() throws Exception {
            HouseholdMemberRequest invalidRequest = HouseholdMemberRequest.builder()
                    // Missing required userId and role
                    .build();

            mockMvc.perform(post("/api/households/" + sampleHouseholdId + "/members")
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(new ObjectMapper().writeValueAsString(invalidRequest)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(householdMemberService);
        }

        @Test
        @DisplayName("should return 401 when not authenticated")
        void shouldReturn401WhenNotAuthenticated() throws Exception {
            HouseholdMemberRequest request = HouseholdMemberRequest.builder()
                    .userId(sampleUserId)
                    .role(HouseholdRole.MEMBER)
                    .build();

            mockMvc.perform(post("/api/households/" + sampleHouseholdId + "/members")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(new ObjectMapper().writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());

            verifyNoInteractions(householdMemberService);
        }

        @Test
        @DisplayName("should return 403 when user doesn't have permission to add members")
        void shouldReturn403WhenUserDoesntHavePermissionToAddMembers() throws Exception {
            UUID restrictedHouseholdId = UUID.randomUUID();
            HouseholdMemberRequest request = HouseholdMemberRequest.builder()
                    .userId(sampleUserId)
                    .role(HouseholdRole.MEMBER)
                    .build();

            when(householdMemberService.addMemberToHousehold(
                    eq(restrictedHouseholdId),
                    any(HouseholdMemberRequest.class),
                    any(JwtAuthenticationToken.class)))
                    .thenThrow(new AccessDeniedException("You don't have permission to add members to this household"));

            mockMvc.perform(post("/api/households/" + restrictedHouseholdId + "/members")
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(new ObjectMapper().writeValueAsString(request)))
                    .andExpect(status().isForbidden());

            verify(householdMemberService).addMemberToHousehold(
                    eq(restrictedHouseholdId),
                    any(HouseholdMemberRequest.class),
                    any(JwtAuthenticationToken.class));
        }

        @Test
        @DisplayName("should return 404 when household not found")
        void shouldReturn404WhenHouseholdNotFound() throws Exception {
            UUID nonExistentHouseholdId = UUID.randomUUID();
            HouseholdMemberRequest request = HouseholdMemberRequest.builder()
                    .userId(sampleUserId)
                    .role(HouseholdRole.MEMBER)
                    .build();

            when(householdMemberService.addMemberToHousehold(
                    eq(nonExistentHouseholdId),
                    any(HouseholdMemberRequest.class),
                    any(JwtAuthenticationToken.class)))
                    .thenThrow(new NotFoundException("Household not found"));

            mockMvc.perform(post("/api/households/" + nonExistentHouseholdId + "/members")
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(new ObjectMapper().writeValueAsString(request)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("Household not found"));

            verify(householdMemberService).addMemberToHousehold(
                    eq(nonExistentHouseholdId),
                    any(HouseholdMemberRequest.class),
                    any(JwtAuthenticationToken.class));
        }

        @Test
        @DisplayName("should return 409 when member already exists")
        void shouldReturn409WhenMemberAlreadyExists() throws Exception {
            HouseholdMemberRequest request = HouseholdMemberRequest.builder()
                    .userId(sampleUserId)
                    .role(HouseholdRole.MEMBER)
                    .build();

            when(householdMemberService.addMemberToHousehold(
                    eq(sampleHouseholdId),
                    any(HouseholdMemberRequest.class),
                    any(JwtAuthenticationToken.class)))
                    .thenThrow(new IllegalStateException("User is already a member of this household"));

            mockMvc.perform(post("/api/households/" + sampleHouseholdId + "/members")
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(new ObjectMapper().writeValueAsString(request)))
                    .andExpect(status().isConflict());

            verify(householdMemberService).addMemberToHousehold(
                    eq(sampleHouseholdId),
                    any(HouseholdMemberRequest.class),
                    any(JwtAuthenticationToken.class));
        }
    }

    @Nested
    @DisplayName("GET /api/households/{householdId}/members")
    class GetHouseholdMembers {
        @Test
        @DisplayName("should return all household members")
        void shouldReturnAllHouseholdMembers() throws Exception {
            List<HouseholdMemberResponse> members = new ArrayList<>();
            members.add(sampleMemberResponse);

            UUID secondMemberId = UUID.randomUUID();
            UUID secondUserId = UUID.randomUUID();
            HouseholdMemberResponse secondMember = HouseholdMemberResponse.builder()
                    .id(secondMemberId)
                    .userId(secondUserId)
                    .username("seconduser")
                    .householdId(sampleHouseholdId)
                    .householdName("Test Household")
                    .role(HouseholdRole.ADMIN)
                    .createdAt(LocalDateTime.now().minusDays(3))
                    .build();
            members.add(secondMember);

            when(householdMemberService.getHouseholdMembers(eq(sampleHouseholdId), any(JwtAuthenticationToken.class)))
                    .thenReturn(members);

            mockMvc.perform(get("/api/households/" + sampleHouseholdId + "/members")
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].id").value(sampleMemberId.toString()))
                    .andExpect(jsonPath("$[0].role").value("MEMBER"))
                    .andExpect(jsonPath("$[1].id").value(secondMemberId.toString()))
                    .andExpect(jsonPath("$[1].role").value("ADMIN"));

            verify(householdMemberService).getHouseholdMembers(eq(sampleHouseholdId), any(JwtAuthenticationToken.class));
        }

        @Test
        @DisplayName("should return empty list when household has no members")
        void shouldReturnEmptyListWhenHouseholdHasNoMembers() throws Exception {
            when(householdMemberService.getHouseholdMembers(eq(sampleHouseholdId), any(JwtAuthenticationToken.class)))
                    .thenReturn(List.of());

            mockMvc.perform(get("/api/households/" + sampleHouseholdId + "/members")
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(0));

            verify(householdMemberService).getHouseholdMembers(eq(sampleHouseholdId), any(JwtAuthenticationToken.class));
        }

        @Test
        @DisplayName("should return 401 when not authenticated")
        void shouldReturn401WhenNotAuthenticated() throws Exception {
            mockMvc.perform(get("/api/households/" + sampleHouseholdId + "/members"))
                    .andExpect(status().isUnauthorized());

            verifyNoInteractions(householdMemberService);
        }

        @Test
        @DisplayName("should return 403 when user doesn't have permission to view members")
        void shouldReturn403WhenUserDoesntHavePermissionToViewMembers() throws Exception {
            UUID restrictedHouseholdId = UUID.randomUUID();

            when(householdMemberService.getHouseholdMembers(eq(restrictedHouseholdId), any(JwtAuthenticationToken.class)))
                    .thenThrow(new AccessDeniedException("You don't have permission to view members of this household"));

            mockMvc.perform(get("/api/households/" + restrictedHouseholdId + "/members")
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isForbidden());

            verify(householdMemberService).getHouseholdMembers(eq(restrictedHouseholdId), any(JwtAuthenticationToken.class));
        }

        @Test
        @DisplayName("should return 404 when household not found")
        void shouldReturn404WhenHouseholdNotFound() throws Exception {
            UUID nonExistentHouseholdId = UUID.randomUUID();

            when(householdMemberService.getHouseholdMembers(eq(nonExistentHouseholdId), any(JwtAuthenticationToken.class)))
                    .thenThrow(new NotFoundException("Household not found"));

            mockMvc.perform(get("/api/households/" + nonExistentHouseholdId + "/members")
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("Household not found"));

            verify(householdMemberService).getHouseholdMembers(eq(nonExistentHouseholdId), any(JwtAuthenticationToken.class));
        }
    }

    @Nested
    @DisplayName("GET /api/households/{householdId}/members/{memberId}")
    class GetMemberById {
        @Test
        @DisplayName("should return member details when valid IDs provided")
        void shouldReturnMemberDetailsById() throws Exception {
            when(householdMemberService.getMemberById(
                    eq(sampleHouseholdId),
                    eq(sampleMemberId),
                    any(JwtAuthenticationToken.class)))
                    .thenReturn(sampleMemberResponse);

            mockMvc.perform(get("/api/households/" + sampleHouseholdId + "/members/" + sampleMemberId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.id").value(sampleMemberId.toString()))
                    .andExpect(jsonPath("$.userId").value(sampleUserId.toString()))
                    .andExpect(jsonPath("$.username").value("testuser"))
                    .andExpect(jsonPath("$.householdId").value(sampleHouseholdId.toString()))
                    .andExpect(jsonPath("$.role").value("MEMBER"));

            verify(householdMemberService).getMemberById(
                    eq(sampleHouseholdId),
                    eq(sampleMemberId),
                    any(JwtAuthenticationToken.class));
        }

        @Test
        @DisplayName("should return 404 when member not found")
        void shouldReturn404WhenMemberNotFound() throws Exception {
            UUID nonExistentMemberId = UUID.randomUUID();

            when(householdMemberService.getMemberById(
                    eq(sampleHouseholdId),
                    eq(nonExistentMemberId),
                    any(JwtAuthenticationToken.class)))
                    .thenThrow(new NotFoundException("Member not found"));

            mockMvc.perform(get("/api/households/" + sampleHouseholdId + "/members/" + nonExistentMemberId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("Member not found"));

            verify(householdMemberService).getMemberById(
                    eq(sampleHouseholdId),
                    eq(nonExistentMemberId),
                    any(JwtAuthenticationToken.class));
        }

        @Test
        @DisplayName("should return 400 when invalid UUID format provided")
        void shouldReturn400WhenInvalidUuidProvided() throws Exception {
            mockMvc.perform(get("/api/households/" + sampleHouseholdId + "/members/invalid-uuid")
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(householdMemberService);
        }
    }

    @Nested
    @DisplayName("DELETE /api/households/{householdId}/members/{memberId}")
    class RemoveMember {
        @Test
        @DisplayName("should remove member when valid IDs provided")
        void shouldRemoveMemberWhenValidIdsProvided() throws Exception {
            doNothing().when(householdMemberService)
                    .removeMember(eq(sampleHouseholdId), eq(sampleMemberId), any(JwtAuthenticationToken.class));

            mockMvc.perform(delete("/api/households/" + sampleHouseholdId + "/members/" + sampleMemberId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isNoContent());

            verify(householdMemberService).removeMember(
                    eq(sampleHouseholdId),
                    eq(sampleMemberId),
                    any(JwtAuthenticationToken.class));
        }

        @Test
        @DisplayName("should return 404 when member not found")
        void shouldReturn404WhenMemberNotFound() throws Exception {
            UUID nonExistentMemberId = UUID.randomUUID();

            doThrow(new NotFoundException("Member not found"))
                    .when(householdMemberService)
                    .removeMember(eq(sampleHouseholdId), eq(nonExistentMemberId), any(JwtAuthenticationToken.class));

            mockMvc.perform(delete("/api/households/" + sampleHouseholdId + "/members/" + nonExistentMemberId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("Member not found"));

            verify(householdMemberService).removeMember(
                    eq(sampleHouseholdId),
                    eq(nonExistentMemberId),
                    any(JwtAuthenticationToken.class));
        }

        @Test
        @DisplayName("should return 403 when user doesn't have permission to remove members")
        void shouldReturn403WhenUserDoesntHavePermissionToRemoveMembers() throws Exception {
            doThrow(new AccessDeniedException("You don't have permission to remove members from this household"))
                    .when(householdMemberService)
                    .removeMember(eq(sampleHouseholdId), eq(sampleMemberId), any(JwtAuthenticationToken.class));

            mockMvc.perform(delete("/api/households/" + sampleHouseholdId + "/members/" + sampleMemberId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isForbidden());

            verify(householdMemberService).removeMember(
                    eq(sampleHouseholdId),
                    eq(sampleMemberId),
                    any(JwtAuthenticationToken.class));
        }

        @Test
        @DisplayName("should return 409 when trying to remove owner")
        void shouldReturn409WhenTryingToRemoveOwner() throws Exception {
            doThrow(new IllegalStateException("Cannot remove the household owner"))
                    .when(householdMemberService)
                    .removeMember(eq(sampleHouseholdId), eq(sampleMemberId), any(JwtAuthenticationToken.class));

            mockMvc.perform(delete("/api/households/" + sampleHouseholdId + "/members/" + sampleMemberId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isConflict());

            verify(householdMemberService).removeMember(
                    eq(sampleHouseholdId),
                    eq(sampleMemberId),
                    any(JwtAuthenticationToken.class));
        }
    }

    @Nested
    @DisplayName("PUT /api/households/{householdId}/members/{memberId}/role")
    class UpdateMemberRole {
        @Test
        @DisplayName("should update member role when valid data provided")
        void shouldUpdateMemberRoleWhenValidDataProvided() throws Exception {
            Map<String, String> roleUpdate = new HashMap<>();
            roleUpdate.put("role", "ADMIN");

            HouseholdMemberResponse updatedResponse = HouseholdMemberResponse.builder()
                    .id(sampleMemberId)
                    .userId(sampleUserId)
                    .username("testuser")
                    .householdId(sampleHouseholdId)
                    .householdName("Test Household")
                    .role(HouseholdRole.ADMIN)
                    .createdAt(LocalDateTime.now().minusDays(7))
                    .build();

            when(householdMemberService.updateMemberRole(
                    eq(sampleHouseholdId),
                    eq(sampleMemberId),
                    eq(HouseholdRole.ADMIN),
                    any(JwtAuthenticationToken.class)))
                    .thenReturn(updatedResponse);

            mockMvc.perform(put("/api/households/" + sampleHouseholdId + "/members/" + sampleMemberId + "/role")
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(new ObjectMapper().writeValueAsString(roleUpdate)))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.id").value(sampleMemberId.toString()))
                    .andExpect(jsonPath("$.role").value("ADMIN"));

            verify(householdMemberService).updateMemberRole(
                    eq(sampleHouseholdId),
                    eq(sampleMemberId),
                    eq(HouseholdRole.ADMIN),
                    any(JwtAuthenticationToken.class));
        }

        @Test
        @DisplayName("should return 400 when role is invalid")
        void shouldReturn400WhenRoleIsInvalid() throws Exception {

            String body = """
                    { "role": "INVALID_ROLE" }
                    """;

            mockMvc.perform(put("/api/households/{hid}/members/{mid}/role", sampleHouseholdId, sampleMemberId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(householdMemberService);   // controller never reaches service
        }


        @Test
        @DisplayName("should return 400 when trying to set role to OWNER")
        void shouldReturn400WhenTryingToSetRoleToOwner() throws Exception {
            Map<String, String> ownerRoleUpdate = new HashMap<>();
            ownerRoleUpdate.put("role", "OWNER");

            when(householdMemberService.updateMemberRole(
                    eq(sampleHouseholdId),
                    eq(sampleMemberId),
                    eq(HouseholdRole.OWNER),
                    any(JwtAuthenticationToken.class)))
                    .thenThrow(new IllegalArgumentException("Cannot assign OWNER role directly"));

            mockMvc.perform(put("/api/households/" + sampleHouseholdId + "/members/" + sampleMemberId + "/role")
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(new ObjectMapper().writeValueAsString(ownerRoleUpdate)))
                    .andExpect(status().isBadRequest());

            verify(householdMemberService).updateMemberRole(
                    eq(sampleHouseholdId),
                    eq(sampleMemberId),
                    eq(HouseholdRole.OWNER),
                    any(JwtAuthenticationToken.class));
        }
    }
}
