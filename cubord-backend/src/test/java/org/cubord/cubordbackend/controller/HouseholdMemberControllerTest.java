
package org.cubord.cubordbackend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.cubord.cubordbackend.config.TestSecurityConfig;
import org.cubord.cubordbackend.domain.HouseholdRole;
import org.cubord.cubordbackend.dto.householdMember.HouseholdMemberRequest;
import org.cubord.cubordbackend.dto.householdMember.HouseholdMemberResponse;
import org.cubord.cubordbackend.dto.householdMember.HouseholdMemberRoleUpdateRequest;
import org.cubord.cubordbackend.exception.*;
import org.cubord.cubordbackend.security.SecurityService;
import org.cubord.cubordbackend.service.HouseholdMemberService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for {@link HouseholdMemberController}.
 *
 * <p>Tests verify:</p>
 * <ul>
 *   <li>Endpoint functionality and response formats</li>
 *   <li>Authorization via {@code @PreAuthorize} with mocked SecurityService</li>
 *   <li>Proper exception handling and HTTP status codes</li>
 *   <li>Input validation</li>
 * </ul>
 */
@WebMvcTest(HouseholdMemberController.class)
@Import(TestSecurityConfig.class)
@ActiveProfiles("test")
class HouseholdMemberControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private HouseholdMemberService householdMemberService;

    @MockitoBean(name = "security")
    private SecurityService securityService;

    @MockitoBean
    private org.cubord.cubordbackend.security.HouseholdPermissionEvaluator householdPermissionEvaluator;

    private UUID currentUserId;
    private UUID householdId;
    private UUID memberId;
    private UUID userToAddId;
    private Jwt jwt;
    private HouseholdMemberResponse sampleMemberResponse;

    @BeforeEach
    void setUp() {
        currentUserId = UUID.randomUUID();
        householdId = UUID.randomUUID();
        memberId = UUID.randomUUID();
        userToAddId = UUID.randomUUID();

        jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(currentUserId.toString())
                .claim("email", "admin@example.com")
                .claim("name", "Admin User")
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        sampleMemberResponse = HouseholdMemberResponse.builder()
                .id(memberId)
                .userId(userToAddId)
                .username("testuser")
                .householdId(householdId)
                .householdName("Test Household")
                .role(HouseholdRole.MEMBER)
                .createdAt(LocalDateTime.now())
                .build();

        // Default security service behavior: current user is an admin of the household
        when(securityService.canManageHouseholdMembers(householdId)).thenReturn(true);
        when(securityService.canAccessHousehold(householdId)).thenReturn(true);
    }

    @Nested
    @DisplayName("POST /api/households/{householdId}/members")
    class AddMember {

        @Test
        @DisplayName("should add member when valid request provided")
        void shouldAddMemberWhenValidRequestProvided() throws Exception {
            HouseholdMemberRequest request = new HouseholdMemberRequest();
            request.setUserId(userToAddId);
            request.setRole(HouseholdRole.MEMBER);

            when(householdMemberService.addMemberToHousehold(eq(householdId), any(HouseholdMemberRequest.class)))
                    .thenReturn(sampleMemberResponse);

            mockMvc.perform(post("/api/households/{householdId}/members", householdId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.id").value(memberId.toString()))
                    .andExpect(jsonPath("$.userId").value(userToAddId.toString()))
                    .andExpect(jsonPath("$.role").value("MEMBER"))
                    .andExpect(jsonPath("$.householdId").value(householdId.toString()));

            verify(securityService).canManageHouseholdMembers(householdId);
            verify(householdMemberService).addMemberToHousehold(eq(householdId), any(HouseholdMemberRequest.class));
        }

        @Test
        @DisplayName("should add member with ADMIN role")
        void shouldAddMemberWithAdminRole() throws Exception {
            HouseholdMemberRequest request = new HouseholdMemberRequest();
            request.setUserId(userToAddId);
            request.setRole(HouseholdRole.ADMIN);

            HouseholdMemberResponse adminResponse = HouseholdMemberResponse.builder()
                    .id(memberId)
                    .userId(userToAddId)
                    .role(HouseholdRole.ADMIN)
                    .householdId(householdId)
                    .createdAt(LocalDateTime.now())
                    .build();

            when(householdMemberService.addMemberToHousehold(eq(householdId), any(HouseholdMemberRequest.class)))
                    .thenReturn(adminResponse);

            mockMvc.perform(post("/api/households/{householdId}/members", householdId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.role").value("ADMIN"));

            verify(householdMemberService).addMemberToHousehold(eq(householdId), any(HouseholdMemberRequest.class));
        }

        @Test
        @DisplayName("should return 400 when request is missing required fields")
        void shouldReturn400WhenRequestIsInvalid() throws Exception {
            HouseholdMemberRequest invalidRequest = new HouseholdMemberRequest();
            // Missing userId and role

            mockMvc.perform(post("/api/households/{householdId}/members", householdId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(invalidRequest)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(householdMemberService);
        }

        @Test
        @DisplayName("should return 400 when trying to set role to OWNER")
        void shouldReturn400WhenTryingToSetRoleToOwner() throws Exception {
            HouseholdMemberRequest request = new HouseholdMemberRequest();
            request.setUserId(userToAddId);
            request.setRole(HouseholdRole.OWNER);

            when(householdMemberService.addMemberToHousehold(eq(householdId), any(HouseholdMemberRequest.class)))
                    .thenThrow(new ValidationException("Cannot set role to OWNER"));

            mockMvc.perform(post("/api/households/{householdId}/members", householdId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error_code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.message").value("Cannot set role to OWNER"));

            verify(householdMemberService).addMemberToHousehold(eq(householdId), any(HouseholdMemberRequest.class));
        }

        @Test
        @DisplayName("should return 401 when not authenticated")
        void shouldReturn401WhenNotAuthenticated() throws Exception {
            HouseholdMemberRequest request = new HouseholdMemberRequest();
            request.setUserId(userToAddId);
            request.setRole(HouseholdRole.MEMBER);

            mockMvc.perform(post("/api/households/{householdId}/members", householdId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());

            verifyNoInteractions(householdMemberService);
        }

        @Test
        @DisplayName("should return 403 when user lacks permission")
        void shouldReturn403WhenUserLacksPermission() throws Exception {
            HouseholdMemberRequest request = new HouseholdMemberRequest();
            request.setUserId(userToAddId);
            request.setRole(HouseholdRole.MEMBER);

            when(securityService.canManageHouseholdMembers(householdId)).thenReturn(false);

            mockMvc.perform(post("/api/households/{householdId}/members", householdId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());

            verify(securityService).canManageHouseholdMembers(householdId);
            verifyNoInteractions(householdMemberService);
        }

        @Test
        @DisplayName("should return 404 when household not found")
        void shouldReturn404WhenHouseholdNotFound() throws Exception {
            HouseholdMemberRequest request = new HouseholdMemberRequest();
            request.setUserId(userToAddId);
            request.setRole(HouseholdRole.MEMBER);

            when(householdMemberService.addMemberToHousehold(eq(householdId), any(HouseholdMemberRequest.class)))
                    .thenThrow(new NotFoundException("Household not found"));

            mockMvc.perform(post("/api/households/{householdId}/members", householdId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error_code").value("RESOURCE_NOT_FOUND"))
                    .andExpect(jsonPath("$.message").value("Household not found"));

            verify(householdMemberService).addMemberToHousehold(eq(householdId), any(HouseholdMemberRequest.class));
        }

        @Test
        @DisplayName("should return 404 when user to add not found")
        void shouldReturn404WhenUserNotFound() throws Exception {
            HouseholdMemberRequest request = new HouseholdMemberRequest();
            request.setUserId(userToAddId);
            request.setRole(HouseholdRole.MEMBER);

            when(householdMemberService.addMemberToHousehold(eq(householdId), any(HouseholdMemberRequest.class)))
                    .thenThrow(new NotFoundException("User not found"));

            mockMvc.perform(post("/api/households/{householdId}/members", householdId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error_code").value("RESOURCE_NOT_FOUND"))
                    .andExpect(jsonPath("$.message").value("User not found"));
        }

        @Test
        @DisplayName("should return 409 when user is already a member")
        void shouldReturn409WhenUserAlreadyMember() throws Exception {
            HouseholdMemberRequest request = new HouseholdMemberRequest();
            request.setUserId(userToAddId);
            request.setRole(HouseholdRole.MEMBER);

            when(householdMemberService.addMemberToHousehold(eq(householdId), any(HouseholdMemberRequest.class)))
                    .thenThrow(new ConflictException("User is already a member of this household"));

            mockMvc.perform(post("/api/households/{householdId}/members", householdId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error_code").value("RESOURCE_CONFLICT"))
                    .andExpect(jsonPath("$.message").value("User is already a member of this household"));
        }

        @Test
        @DisplayName("should return 400 when invalid UUID format provided")
        void shouldReturn400WhenInvalidUuidProvided() throws Exception {
            HouseholdMemberRequest request = new HouseholdMemberRequest();
            request.setUserId(userToAddId);
            request.setRole(HouseholdRole.MEMBER);

            mockMvc.perform(post("/api/households/{householdId}/members", "invalid-uuid")
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(householdMemberService);
        }
    }

    @Nested
    @DisplayName("GET /api/households/{householdId}/members")
    class GetHouseholdMembers {

        @Test
        @DisplayName("should return all household members")
        void shouldReturnAllHouseholdMembers() throws Exception {
            HouseholdMemberResponse member1 = HouseholdMemberResponse.builder()
                    .id(UUID.randomUUID())
                    .userId(UUID.randomUUID())
                    .username("user1")
                    .role(HouseholdRole.OWNER)
                    .householdId(householdId)
                    .createdAt(LocalDateTime.now())
                    .build();

            HouseholdMemberResponse member2 = HouseholdMemberResponse.builder()
                    .id(UUID.randomUUID())
                    .userId(UUID.randomUUID())
                    .username("user2")
                    .role(HouseholdRole.MEMBER)
                    .householdId(householdId)
                    .createdAt(LocalDateTime.now())
                    .build();

            List<HouseholdMemberResponse> members = List.of(member1, member2);
            when(householdMemberService.getHouseholdMembers(householdId)).thenReturn(members);

            mockMvc.perform(get("/api/households/{householdId}/members", householdId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].username").value("user1"))
                    .andExpect(jsonPath("$[0].role").value("OWNER"))
                    .andExpect(jsonPath("$[1].username").value("user2"))
                    .andExpect(jsonPath("$[1].role").value("MEMBER"));

            verify(securityService).canAccessHousehold(householdId);
            verify(householdMemberService).getHouseholdMembers(householdId);
        }

        @Test
        @DisplayName("should return empty list when household has no members")
        void shouldReturnEmptyListWhenNoMembers() throws Exception {
            when(householdMemberService.getHouseholdMembers(householdId)).thenReturn(Collections.emptyList());

            mockMvc.perform(get("/api/households/{householdId}/members", householdId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(0));

            verify(householdMemberService).getHouseholdMembers(householdId);
        }

        @Test
        @DisplayName("should return 401 when not authenticated")
        void shouldReturn401WhenNotAuthenticated() throws Exception {
            mockMvc.perform(get("/api/households/{householdId}/members", householdId))
                    .andExpect(status().isUnauthorized());

            verifyNoInteractions(householdMemberService);
        }

        @Test
        @DisplayName("should return 403 when user is not a household member")
        void shouldReturn403WhenNotHouseholdMember() throws Exception {
            when(securityService.canAccessHousehold(householdId)).thenReturn(false);

            mockMvc.perform(get("/api/households/{householdId}/members", householdId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isForbidden());

            verify(securityService).canAccessHousehold(householdId);
            verifyNoInteractions(householdMemberService);
        }

        @Test
        @DisplayName("should return 400 when invalid household UUID provided")
        void shouldReturn400WhenInvalidHouseholdUuid() throws Exception {
            mockMvc.perform(get("/api/households/{householdId}/members", "invalid-uuid")
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(householdMemberService);
        }
    }

    @Nested
    @DisplayName("GET /api/households/{householdId}/members/{memberId}")
    class GetMemberById {

        @Test
        @DisplayName("should return member details when valid IDs provided")
        void shouldReturnMemberDetailsWhenValidIdsProvided() throws Exception {
            when(householdMemberService.getMemberById(householdId, memberId)).thenReturn(sampleMemberResponse);

            mockMvc.perform(get("/api/households/{householdId}/members/{memberId}", householdId, memberId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.id").value(memberId.toString()))
                    .andExpect(jsonPath("$.userId").value(userToAddId.toString()))
                    .andExpect(jsonPath("$.username").value("testuser"))
                    .andExpect(jsonPath("$.householdId").value(householdId.toString()))
                    .andExpect(jsonPath("$.role").value("MEMBER"));

            verify(securityService).canAccessHousehold(householdId);
            verify(householdMemberService).getMemberById(householdId, memberId);
        }

        @Test
        @DisplayName("should return 401 when not authenticated")
        void shouldReturn401WhenNotAuthenticated() throws Exception {
            mockMvc.perform(get("/api/households/{householdId}/members/{memberId}", householdId, memberId))
                    .andExpect(status().isUnauthorized());

            verifyNoInteractions(householdMemberService);
        }

        @Test
        @DisplayName("should return 403 when user is not a household member")
        void shouldReturn403WhenNotHouseholdMember() throws Exception {
            when(securityService.canAccessHousehold(householdId)).thenReturn(false);

            mockMvc.perform(get("/api/households/{householdId}/members/{memberId}", householdId, memberId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isForbidden());

            verify(securityService).canAccessHousehold(householdId);
            verifyNoInteractions(householdMemberService);
        }

        @Test
        @DisplayName("should return 404 when member not found")
        void shouldReturn404WhenMemberNotFound() throws Exception {
            when(householdMemberService.getMemberById(householdId, memberId))
                    .thenThrow(new NotFoundException("Member not found"));

            mockMvc.perform(get("/api/households/{householdId}/members/{memberId}", householdId, memberId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error_code").value("RESOURCE_NOT_FOUND"))
                    .andExpect(jsonPath("$.message").value("Member not found"));

            verify(householdMemberService).getMemberById(householdId, memberId);
        }

        @Test
        @DisplayName("should return 404 when member is not from specified household")
        void shouldReturn404WhenMemberNotFromHousehold() throws Exception {
            when(householdMemberService.getMemberById(householdId, memberId))
                    .thenThrow(new NotFoundException("Member is not from the specified household"));

            mockMvc.perform(get("/api/households/{householdId}/members/{memberId}", householdId, memberId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error_code").value("RESOURCE_NOT_FOUND"));
        }

        @Test
        @DisplayName("should return 400 when invalid UUID format provided")
        void shouldReturn400WhenInvalidUuidProvided() throws Exception {
            mockMvc.perform(get("/api/households/{householdId}/members/{memberId}", "invalid-uuid", "invalid-uuid")
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
            doNothing().when(householdMemberService).removeMember(householdId, memberId);

            mockMvc.perform(delete("/api/households/{householdId}/members/{memberId}", householdId, memberId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isNoContent());

            verify(securityService).canManageHouseholdMembers(householdId);
            verify(householdMemberService).removeMember(householdId, memberId);
        }

        @Test
        @DisplayName("should return 401 when not authenticated")
        void shouldReturn401WhenNotAuthenticated() throws Exception {
            mockMvc.perform(delete("/api/households/{householdId}/members/{memberId}", householdId, memberId))
                    .andExpect(status().isUnauthorized());

            verifyNoInteractions(householdMemberService);
        }

        @Test
        @DisplayName("should return 403 when user lacks permission")
        void shouldReturn403WhenUserLacksPermission() throws Exception {
            when(securityService.canManageHouseholdMembers(householdId)).thenReturn(false);

            mockMvc.perform(delete("/api/households/{householdId}/members/{memberId}", householdId, memberId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isForbidden());

            verify(securityService).canManageHouseholdMembers(householdId);
            verifyNoInteractions(householdMemberService);
        }

        @Test
        @DisplayName("should return 404 when member not found")
        void shouldReturn404WhenMemberNotFound() throws Exception {
            doThrow(new NotFoundException("Member not found"))
                    .when(householdMemberService).removeMember(householdId, memberId);

            mockMvc.perform(delete("/api/households/{householdId}/members/{memberId}", householdId, memberId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error_code").value("RESOURCE_NOT_FOUND"))
                    .andExpect(jsonPath("$.message").value("Member not found"));

            verify(householdMemberService).removeMember(householdId, memberId);
        }

        @Test
        @DisplayName("should return 404 when member is not from specified household")
        void shouldReturn404WhenMemberNotFromHousehold() throws Exception {
            doThrow(new NotFoundException("Member is not from the specified household"))
                    .when(householdMemberService).removeMember(householdId, memberId);

            mockMvc.perform(delete("/api/households/{householdId}/members/{memberId}", householdId, memberId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error_code").value("RESOURCE_NOT_FOUND"));
        }

        @Test
        @DisplayName("should return 409 when trying to remove the owner")
        void shouldReturn409WhenTryingToRemoveOwner() throws Exception {
            doThrow(new ResourceStateException("Cannot remove the owner from the household"))
                    .when(householdMemberService).removeMember(householdId, memberId);

            mockMvc.perform(delete("/api/households/{householdId}/members/{memberId}", householdId, memberId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error_code").value("INVALID_RESOURCE_STATE"))
                    .andExpect(jsonPath("$.message").value("Cannot remove the owner from the household"));
        }

        @Test
        @DisplayName("should return 403 when admin tries to remove another admin")
        void shouldReturn403WhenAdminTriesToRemoveAnotherAdmin() throws Exception {
            doThrow(new InsufficientPermissionException("Admin cannot remove another admin"))
                    .when(householdMemberService).removeMember(householdId, memberId);

            mockMvc.perform(delete("/api/households/{householdId}/members/{memberId}", householdId, memberId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error_code").value("INSUFFICIENT_PERMISSION"))
                    .andExpect(jsonPath("$.message").value("Admin cannot remove another admin"));
        }

        @Test
        @DisplayName("should return 400 when invalid UUID format provided")
        void shouldReturn400WhenInvalidUuidProvided() throws Exception {
            mockMvc.perform(delete("/api/households/{householdId}/members/{memberId}", "invalid-uuid", "invalid-uuid")
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(householdMemberService);
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
                    .id(memberId)
                    .userId(userToAddId)
                    .username("testuser")
                    .householdId(householdId)
                    .role(HouseholdRole.ADMIN)
                    .createdAt(LocalDateTime.now())
                    .build();

            when(householdMemberService.updateMemberRole(householdId, memberId, HouseholdRole.ADMIN))
                    .thenReturn(updatedResponse);

            mockMvc.perform(put("/api/households/{householdId}/members/{memberId}/role", householdId, memberId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.id").value(memberId.toString()))
                    .andExpect(jsonPath("$.role").value("ADMIN"));

            verify(securityService).canManageHouseholdMembers(householdId);
            verify(householdMemberService).updateMemberRole(householdId, memberId, HouseholdRole.ADMIN);
        }

        @Test
        @DisplayName("should update member role from ADMIN to MEMBER")
        void shouldUpdateMemberRoleFromAdminToMember() throws Exception {
            HouseholdMemberRoleUpdateRequest request = new HouseholdMemberRoleUpdateRequest();
            request.setRole(HouseholdRole.MEMBER);

            HouseholdMemberResponse updatedResponse = HouseholdMemberResponse.builder()
                    .id(memberId)
                    .userId(userToAddId)
                    .role(HouseholdRole.MEMBER)
                    .householdId(householdId)
                    .createdAt(LocalDateTime.now())
                    .build();

            when(householdMemberService.updateMemberRole(householdId, memberId, HouseholdRole.MEMBER))
                    .thenReturn(updatedResponse);

            mockMvc.perform(put("/api/households/{householdId}/members/{memberId}/role", householdId, memberId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.role").value("MEMBER"));
        }

        @Test
        @DisplayName("should return 400 when request is missing role")
        void shouldReturn400WhenRequestIsInvalid() throws Exception {
            HouseholdMemberRoleUpdateRequest invalidRequest = new HouseholdMemberRoleUpdateRequest();
            // Missing role

            mockMvc.perform(put("/api/households/{householdId}/members/{memberId}/role", householdId, memberId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(invalidRequest)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(householdMemberService);
        }

        @Test
        @DisplayName("should return 400 when trying to set role to OWNER")
        void shouldReturn400WhenTryingToSetRoleToOwner() throws Exception {
            HouseholdMemberRoleUpdateRequest request = new HouseholdMemberRoleUpdateRequest();
            request.setRole(HouseholdRole.OWNER);

            when(householdMemberService.updateMemberRole(householdId, memberId, HouseholdRole.OWNER))
                    .thenThrow(new ValidationException("Cannot set role to OWNER"));

            mockMvc.perform(put("/api/households/{householdId}/members/{memberId}/role", householdId, memberId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error_code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.message").value("Cannot set role to OWNER"));

            verify(householdMemberService).updateMemberRole(householdId, memberId, HouseholdRole.OWNER);
        }

        @Test
        @DisplayName("should return 401 when not authenticated")
        void shouldReturn401WhenNotAuthenticated() throws Exception {
            HouseholdMemberRoleUpdateRequest request = new HouseholdMemberRoleUpdateRequest();
            request.setRole(HouseholdRole.ADMIN);

            mockMvc.perform(put("/api/households/{householdId}/members/{memberId}/role", householdId, memberId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());

            verifyNoInteractions(householdMemberService);
        }

        @Test
        @DisplayName("should return 403 when user lacks permission")
        void shouldReturn403WhenUserLacksPermission() throws Exception {
            HouseholdMemberRoleUpdateRequest request = new HouseholdMemberRoleUpdateRequest();
            request.setRole(HouseholdRole.ADMIN);

            when(securityService.canManageHouseholdMembers(householdId)).thenReturn(false);

            mockMvc.perform(put("/api/households/{householdId}/members/{memberId}/role", householdId, memberId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());

            verify(securityService).canManageHouseholdMembers(householdId);
            verifyNoInteractions(householdMemberService);
        }

        @Test
        @DisplayName("should return 404 when member not found")
        void shouldReturn404WhenMemberNotFound() throws Exception {
            HouseholdMemberRoleUpdateRequest request = new HouseholdMemberRoleUpdateRequest();
            request.setRole(HouseholdRole.ADMIN);

            when(householdMemberService.updateMemberRole(householdId, memberId, HouseholdRole.ADMIN))
                    .thenThrow(new NotFoundException("Member not found"));

            mockMvc.perform(put("/api/households/{householdId}/members/{memberId}/role", householdId, memberId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error_code").value("RESOURCE_NOT_FOUND"))
                    .andExpect(jsonPath("$.message").value("Member not found"));
        }

        @Test
        @DisplayName("should return 403 when admin tries to update another admin's role")
        void shouldReturn403WhenAdminTriesToUpdateAnotherAdmin() throws Exception {
            HouseholdMemberRoleUpdateRequest request = new HouseholdMemberRoleUpdateRequest();
            request.setRole(HouseholdRole.MEMBER);

            when(householdMemberService.updateMemberRole(householdId, memberId, HouseholdRole.MEMBER))
                    .thenThrow(new InsufficientPermissionException("Admin cannot update another admin's role"));

            mockMvc.perform(put("/api/households/{householdId}/members/{memberId}/role", householdId, memberId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error_code").value("INSUFFICIENT_PERMISSION"))
                    .andExpect(jsonPath("$.message").value("Admin cannot update another admin&#39;s role"));
        }

        @Test
        @DisplayName("should return 400 when invalid UUID format provided")
        void shouldReturn400WhenInvalidUuidProvided() throws Exception {
            HouseholdMemberRoleUpdateRequest request = new HouseholdMemberRoleUpdateRequest();
            request.setRole(HouseholdRole.ADMIN);

            mockMvc.perform(put("/api/households/{householdId}/members/{memberId}/role", "invalid-uuid", "invalid-uuid")
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(householdMemberService);
        }
    }

    @Nested
    @DisplayName("Error Response Format")
    class ErrorResponseFormat {

        @Test
        @DisplayName("should include correlation_id in all error responses")
        void shouldIncludeCorrelationIdInErrors() throws Exception {
            when(householdMemberService.getMemberById(householdId, memberId))
                    .thenThrow(new NotFoundException("Member not found"));

            mockMvc.perform(get("/api/households/{householdId}/members/{memberId}", householdId, memberId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.correlation_id").exists())
                    .andExpect(jsonPath("$.timestamp").exists())
                    .andExpect(jsonPath("$.message").exists())
                    .andExpect(jsonPath("$.error_code").exists());
        }

        @Test
        @DisplayName("should include message in validation errors")
        void shouldIncludeMessageInValidationErrors() throws Exception {
            HouseholdMemberRequest invalidRequest = new HouseholdMemberRequest();
            // Missing required fields

            mockMvc.perform(post("/api/households/{householdId}/members", householdId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(invalidRequest)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").isNotEmpty());
        }
    }

    @Nested
    @DisplayName("Internal Error Handling")
    class InternalErrorHandling {

        @Test
        @DisplayName("should return 500 when unexpected service exception occurs")
        void shouldReturn500WhenUnexpectedExceptionOccurs() throws Exception {
            when(householdMemberService.getHouseholdMembers(householdId))
                    .thenThrow(new RuntimeException("Database connection failed"));

            mockMvc.perform(get("/api/households/{householdId}/members", householdId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.error_code").value("INTERNAL_SERVER_ERROR"))
                    .andExpect(jsonPath("$.correlation_id").exists());
        }

        @Test
        @DisplayName("should return 500 when SecurityService throws unexpected exception")
        void shouldReturn500WhenSecurityServiceFails() throws Exception {
            when(securityService.canAccessHousehold(any()))
                    .thenThrow(new RuntimeException("Security context unavailable"));

            mockMvc.perform(get("/api/households/{householdId}/members", householdId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isInternalServerError());

            verifyNoInteractions(householdMemberService);
        }
    }
}