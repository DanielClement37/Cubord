package org.cubord.cubordbackend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.cubord.cubordbackend.config.TestSecurityConfig;
import org.cubord.cubordbackend.dto.HouseholdRequest;
import org.cubord.cubordbackend.dto.HouseholdResponse;
import org.cubord.cubordbackend.service.HouseholdService;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(HouseholdController.class)
@Import(TestSecurityConfig.class)
@ActiveProfiles("test")
class HouseholdControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private HouseholdService householdService;

    // Also need to mock any dependencies of SecurityConfig
    @MockitoBean
    private org.cubord.cubordbackend.security.HouseholdPermissionEvaluator householdPermissionEvaluator;

    private UUID sampleUserId;
    private UUID sampleHouseholdId;
    private Jwt jwt;

    @BeforeEach
    void setUp() {
        sampleUserId = UUID.randomUUID();
        sampleHouseholdId = UUID.randomUUID();
        LocalDateTime createdAt = LocalDateTime.now().minusDays(7);

        // Create sample household response
        HouseholdResponse sampleHouseholdResponse = HouseholdResponse.builder()
                .id(sampleHouseholdId)
                .name("Test Household")
                .createdAt(createdAt)
                .updatedAt(createdAt)
                .build();

        // Create mock JWT token with a future expiration time
        jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(sampleUserId.toString())
                .claim("email", "test@example.com")
                .claim("name", "Test User")
                .expiresAt(Instant.now().plusSeconds(3600)) // Add this to make sure token is valid
                .build();

        // Setup common mocks
        when(householdService.getHouseholdById(eq(sampleHouseholdId), any(JwtAuthenticationToken.class)))
                .thenReturn(sampleHouseholdResponse);
        when(householdService.getUserHouseholds(any(JwtAuthenticationToken.class)))
                .thenReturn(List.of(sampleHouseholdResponse));
                
        // Setup permission evaluator for test household
        when(householdPermissionEvaluator.hasViewPermission(any(), eq(sampleHouseholdId.toString())))
                .thenReturn(true);
        when(householdPermissionEvaluator.hasEditPermission(any(), eq(sampleHouseholdId.toString())))
                .thenReturn(true);
    }

    @Test
    @DisplayName("should return 401 when not authenticated")
    void shouldReturn401WhenNotAuthenticated() throws Exception {
        mockMvc.perform(get("/api/households"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(householdService);
    }

    @Test
    @DisplayName("should allow access with valid authentication")
    void shouldAllowAccessWithValidAuthentication() throws Exception {
        mockMvc.perform(get("/api/households")
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                .andExpect(status().isOk());

        verify(householdService).getUserHouseholds(any(JwtAuthenticationToken.class));
    }

    @Test
    @DisplayName("should return 401 for expired JWT token")
    void shouldReturn401ForExpiredJwtToken() throws Exception {
        // Create a token that is explicitly expired (in the past)
        Instant expiredTime = Instant.now().minusSeconds(3600);

        mockMvc.perform(get("/api/households")
                .with(SecurityMockMvcRequestPostProcessors.jwt()
                        .jwt(jwt -> jwt
                                .subject(sampleUserId.toString())
                                .expiresAt(expiredTime)
                                .issuedAt(expiredTime.minusSeconds(3600))
                        )))
                .andDo(MockMvcResultHandlers.print()) // Print detailed results for debugging
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(householdService);
    }

    @Test
    @DisplayName("should prevent accessing household details without permissions")
    void shouldPreventAccessingHouseholdDetailsWithoutPermissions() throws Exception {
        UUID restrictedHouseholdId = UUID.randomUUID();

        when(householdPermissionEvaluator.hasViewPermission(any(), eq(restrictedHouseholdId.toString())))
                .thenReturn(false);

        mockMvc.perform(get("/api/households/" + restrictedHouseholdId)
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                .andExpect(status().isForbidden());

        verifyNoInteractions(householdService);
    }

    @Test
    @DisplayName("should prevent updating household without permissions")
    void shouldPreventUpdatingHouseholdWithoutPermissions() throws Exception {
        UUID restrictedHouseholdId = UUID.randomUUID();
        HouseholdRequest updateRequest = new HouseholdRequest();
        updateRequest.setName("Updated Household");

        when(householdPermissionEvaluator.hasEditPermission(any(), eq(restrictedHouseholdId.toString())))
                .thenReturn(false);

        mockMvc.perform(put("/api/households/" + restrictedHouseholdId)
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(updateRequest)))
                .andExpect(status().isForbidden());

        verifyNoInteractions(householdService);
    }

    @Test
    @DisplayName("should prevent deleting household without permissions")
    void shouldPreventDeletingHouseholdWithoutPermissions() throws Exception {
        UUID restrictedHouseholdId = UUID.randomUUID();

        when(householdPermissionEvaluator.hasEditPermission(any(), eq(restrictedHouseholdId.toString())))
                .thenReturn(false);

        mockMvc.perform(delete("/api/households/" + restrictedHouseholdId)
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                .andExpect(status().isForbidden());

        verifyNoInteractions(householdService);
    }

    @Test
    @DisplayName("should allow household creation with valid authentication")
    void shouldAllowHouseholdCreationWithValidAuthentication() throws Exception {
        HouseholdRequest createRequest = new HouseholdRequest();
        createRequest.setName("New Household");

        HouseholdResponse createdResponse = HouseholdResponse.builder()
                .id(UUID.randomUUID())
                .name("New Household")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(householdService.createHousehold(any(HouseholdRequest.class), any(JwtAuthenticationToken.class)))
                .thenReturn(createdResponse);

        mockMvc.perform(post("/api/households")
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(createRequest)))
                .andExpect(status().isCreated());

        verify(householdService).createHousehold(any(HouseholdRequest.class), any(JwtAuthenticationToken.class));
    }

    @Test
    @DisplayName("should prevent household creation without authentication")
    void shouldPreventHouseholdCreationWithoutAuthentication() throws Exception {
        HouseholdRequest createRequest = new HouseholdRequest();
        createRequest.setName("New Household");

        mockMvc.perform(post("/api/households")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(createRequest)))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(householdService);
    }

    @Test
    @DisplayName("should prevent leaving household without permissions")
    void shouldPreventLeavingHouseholdWithoutPermissions() throws Exception {
        UUID restrictedHouseholdId = UUID.randomUUID();

        when(householdPermissionEvaluator.hasViewPermission(any(), eq(restrictedHouseholdId.toString())))
                .thenReturn(false);

        mockMvc.perform(post("/api/households/" + restrictedHouseholdId + "/leave")
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                .andExpect(status().isForbidden());

        verifyNoInteractions(householdService);
    }

    @Test
    @DisplayName("should prevent transferring ownership without permissions")
    void shouldPreventTransferringOwnershipWithoutPermissions() throws Exception {
        UUID restrictedHouseholdId = UUID.randomUUID();
        UUID newOwnerId = UUID.randomUUID();

        when(householdPermissionEvaluator.hasEditPermission(any(), eq(restrictedHouseholdId.toString())))
                .thenReturn(false);

        mockMvc.perform(post("/api/households/" + restrictedHouseholdId + "/transfer-ownership")
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(Map.of("newOwnerId", newOwnerId))))
                .andExpect(status().isForbidden());

        verifyNoInteractions(householdService);
    }
}