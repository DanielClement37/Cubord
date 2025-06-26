package org.cubord.cubordbackend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.cubord.cubordbackend.config.TestSecurityConfig;
import org.cubord.cubordbackend.domain.HouseholdRole;
import org.cubord.cubordbackend.dto.HouseholdMemberRequest;
import org.cubord.cubordbackend.dto.HouseholdMemberResponse;
import org.cubord.cubordbackend.service.HouseholdMemberService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.result.MockMvcResultHandlers;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(HouseholdMemberController.class)
@Import(TestSecurityConfig.class)
@ActiveProfiles("test")
class HouseholdMemberControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private HouseholdMemberService householdMemberService;

    // Also need to mock any dependencies of SecurityConfig
    @MockitoBean
    private org.cubord.cubordbackend.security.HouseholdPermissionEvaluator householdPermissionEvaluator;

    private UUID sampleUserId;
    private UUID sampleHouseholdId;
    private UUID sampleMemberId;
    private Jwt jwt;

    @BeforeEach
    void setUp() {
        sampleUserId = UUID.randomUUID();
        sampleHouseholdId = UUID.randomUUID();
        sampleMemberId = UUID.randomUUID();
        LocalDateTime createdAt = LocalDateTime.now().minusDays(7);

        // Create sample member response
        HouseholdMemberResponse sampleMemberResponse = HouseholdMemberResponse.builder()
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

        // Setup common mocks
        when(householdMemberService.getHouseholdMembers(eq(sampleHouseholdId), any(JwtAuthenticationToken.class)))
                .thenReturn(List.of(sampleMemberResponse));
        when(householdMemberService.getMemberById(eq(sampleHouseholdId), eq(sampleMemberId), any(JwtAuthenticationToken.class)))
                .thenReturn(sampleMemberResponse);
        when(householdMemberService.addMemberToHousehold(
                eq(sampleHouseholdId), 
                any(HouseholdMemberRequest.class), 
                any(JwtAuthenticationToken.class)))
                .thenReturn(sampleMemberResponse);
        when(householdMemberService.updateMemberRole(
                eq(sampleHouseholdId), 
                eq(sampleMemberId), 
                any(HouseholdRole.class), 
                any(JwtAuthenticationToken.class)))
                .thenReturn(sampleMemberResponse);

        // Setup permission evaluator for test household
        when(householdPermissionEvaluator.hasViewPermission(any(), eq(sampleHouseholdId.toString())))
                .thenReturn(true);
        when(householdPermissionEvaluator.hasEditPermission(any(), eq(sampleHouseholdId.toString())))
                .thenReturn(true);
    }

    @Test
    @DisplayName("should return 401 when not authenticated for GET members")
    void shouldReturn401WhenNotAuthenticatedForGetMembers() throws Exception {
        mockMvc.perform(get("/api/households/" + sampleHouseholdId + "/members"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(householdMemberService);
    }

    @Test
    @DisplayName("should return 401 when not authenticated for POST member")
    void shouldReturn401WhenNotAuthenticatedForPostMember() throws Exception {
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
    @DisplayName("should return 401 when not authenticated for GET member by ID")
    void shouldReturn401WhenNotAuthenticatedForGetMemberById() throws Exception {
        mockMvc.perform(get("/api/households/" + sampleHouseholdId + "/members/" + sampleMemberId))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(householdMemberService);
    }

    @Test
    @DisplayName("should return 401 when not authenticated for DELETE member")
    void shouldReturn401WhenNotAuthenticatedForDeleteMember() throws Exception {
        mockMvc.perform(delete("/api/households/" + sampleHouseholdId + "/members/" + sampleMemberId))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(householdMemberService);
    }

    @Test
    @DisplayName("should return 401 when not authenticated for PUT member role")
    void shouldReturn401WhenNotAuthenticatedForPutMemberRole() throws Exception {
        Map<String, String> roleUpdate = new HashMap<>();
        roleUpdate.put("role", "ADMIN");

        mockMvc.perform(put("/api/households/" + sampleHouseholdId + "/members/" + sampleMemberId + "/role")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(roleUpdate)))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(householdMemberService);
    }

    @Test
    @DisplayName("should allow access with valid authentication for GET members")
    void shouldAllowAccessWithValidAuthenticationForGetMembers() throws Exception {
        mockMvc.perform(get("/api/households/" + sampleHouseholdId + "/members")
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                .andExpect(status().isOk());

        verify(householdMemberService).getHouseholdMembers(eq(sampleHouseholdId), any(JwtAuthenticationToken.class));
    }

    @Test
    @DisplayName("should allow access with valid authentication for POST member")
    void shouldAllowAccessWithValidAuthenticationForPostMember() throws Exception {
        HouseholdMemberRequest request = HouseholdMemberRequest.builder()
                .userId(sampleUserId)
                .role(HouseholdRole.MEMBER)
                .build();

        mockMvc.perform(post("/api/households/" + sampleHouseholdId + "/members")
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(request)))
                .andExpect(status().isCreated());

        verify(householdMemberService).addMemberToHousehold(
                eq(sampleHouseholdId), 
                any(HouseholdMemberRequest.class), 
                any(JwtAuthenticationToken.class));
    }

    @Test
    @DisplayName("should return 401 for expired JWT token")
    void shouldReturn401ForExpiredJwtToken() throws Exception {
        // Create a token that is explicitly expired (in the past)
        Instant expiredTime = Instant.now().minusSeconds(3600);

        mockMvc.perform(get("/api/households/" + sampleHouseholdId + "/members")
                .with(SecurityMockMvcRequestPostProcessors.jwt()
                        .jwt(jwt -> jwt
                                .subject(sampleUserId.toString())
                                .expiresAt(expiredTime)
                                .issuedAt(expiredTime.minusSeconds(3600))
                        )))
                .andDo(MockMvcResultHandlers.print()) // Print detailed results for debugging
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(householdMemberService);
    }

    @Test
    @DisplayName("should prevent accessing members without view permissions")
    void shouldPreventAccessingMembersWithoutViewPermissions() throws Exception {
        UUID restrictedHouseholdId = UUID.randomUUID();

        when(householdPermissionEvaluator.hasViewPermission(any(), eq(restrictedHouseholdId.toString())))
                .thenReturn(false);

        mockMvc.perform(get("/api/households/" + restrictedHouseholdId + "/members")
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                .andExpect(status().isForbidden());

        verifyNoInteractions(householdMemberService);
    }

    @Test
    @DisplayName("should prevent adding members without edit permissions")
    void shouldPreventAddingMembersWithoutEditPermissions() throws Exception {
        UUID restrictedHouseholdId = UUID.randomUUID();
        HouseholdMemberRequest request = HouseholdMemberRequest.builder()
                .userId(sampleUserId)
                .role(HouseholdRole.MEMBER)
                .build();

        when(householdPermissionEvaluator.hasEditPermission(any(), eq(restrictedHouseholdId.toString())))
                .thenReturn(false);

        mockMvc.perform(post("/api/households/" + restrictedHouseholdId + "/members")
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(request)))
                .andExpect(status().isForbidden());

        verifyNoInteractions(householdMemberService);
    }

    @Test
    @DisplayName("should prevent removing members without edit permissions")
    void shouldPreventRemovingMembersWithoutEditPermissions() throws Exception {
        UUID restrictedHouseholdId = UUID.randomUUID();

        when(householdPermissionEvaluator.hasEditPermission(any(), eq(restrictedHouseholdId.toString())))
                .thenReturn(false);

        mockMvc.perform(delete("/api/households/" + restrictedHouseholdId + "/members/" + sampleMemberId)
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                .andExpect(status().isForbidden());

        verifyNoInteractions(householdMemberService);
    }

    @Test
    @DisplayName("should prevent updating member roles without edit permissions")
    void shouldPreventUpdatingMemberRolesWithoutEditPermissions() throws Exception {
        UUID restrictedHouseholdId = UUID.randomUUID();
        Map<String, String> roleUpdate = new HashMap<>();
        roleUpdate.put("role", "ADMIN");

        when(householdPermissionEvaluator.hasEditPermission(any(), eq(restrictedHouseholdId.toString())))
                .thenReturn(false);

        mockMvc.perform(put("/api/households/" + restrictedHouseholdId + "/members/" + sampleMemberId + "/role")
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(roleUpdate)))
                .andExpect(status().isForbidden());

        verifyNoInteractions(householdMemberService);
    }

    @Test
    @DisplayName("should allow member operations with valid authentication and permissions")
    void shouldAllowMemberOperationsWithValidAuthenticationAndPermissions() throws Exception {
        // Test GET member by ID
        mockMvc.perform(get("/api/households/" + sampleHouseholdId + "/members/" + sampleMemberId)
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                .andExpect(status().isOk());

        verify(householdMemberService).getMemberById(eq(sampleHouseholdId), eq(sampleMemberId), any(JwtAuthenticationToken.class));

        // Test DELETE member
        doNothing().when(householdMemberService).removeMember(eq(sampleHouseholdId), eq(sampleMemberId), any(JwtAuthenticationToken.class));

        mockMvc.perform(delete("/api/households/" + sampleHouseholdId + "/members/" + sampleMemberId)
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                .andExpect(status().isNoContent());

        verify(householdMemberService).removeMember(eq(sampleHouseholdId), eq(sampleMemberId), any(JwtAuthenticationToken.class));

        // Test PUT member role
        Map<String, String> roleUpdate = new HashMap<>();
        roleUpdate.put("role", "ADMIN");

        mockMvc.perform(put("/api/households/" + sampleHouseholdId + "/members/" + sampleMemberId + "/role")
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(roleUpdate)))
                .andExpect(status().isOk());

        verify(householdMemberService).updateMemberRole(
                eq(sampleHouseholdId), 
                eq(sampleMemberId), 
                eq(HouseholdRole.ADMIN), 
                any(JwtAuthenticationToken.class));
    }

    @Test
    @DisplayName("should prevent member creation without authentication")
    void shouldPreventMemberCreationWithoutAuthentication() throws Exception {
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
    @DisplayName("should prevent member deletion without authentication")
    void shouldPreventMemberDeletionWithoutAuthentication() throws Exception {
        mockMvc.perform(delete("/api/households/" + sampleHouseholdId + "/members/" + sampleMemberId))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(householdMemberService);
    }

    @Test
    @DisplayName("should prevent role updates without authentication")
    void shouldPreventRoleUpdatesWithoutAuthentication() throws Exception {
        Map<String, String> roleUpdate = new HashMap<>();
        roleUpdate.put("role", "ADMIN");

        mockMvc.perform(put("/api/households/" + sampleHouseholdId + "/members/" + sampleMemberId + "/role")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(roleUpdate)))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(householdMemberService);
    }
}
