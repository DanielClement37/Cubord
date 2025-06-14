package org.cubord.cubordbackend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.cubord.cubordbackend.config.TestSecurityConfig;
import org.cubord.cubordbackend.dto.HouseholdRequest;
import org.cubord.cubordbackend.dto.HouseholdResponse;
import org.cubord.cubordbackend.exception.ForbiddenException;
import org.cubord.cubordbackend.exception.NotFoundException;
import org.cubord.cubordbackend.service.HouseholdService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(HouseholdController.class)
@Import(TestSecurityConfig.class)
@ActiveProfiles("test")
class HouseholdControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private HouseholdService householdService;

    // Need to mock any dependencies of SecurityConfig in TestSecurityConfig
    @MockitoBean
    private org.cubord.cubordbackend.security.HouseholdPermissionEvaluator householdPermissionEvaluator;

    private UUID sampleUserId;
    private UUID sampleHouseholdId;
    private Jwt jwt;
    private HouseholdResponse sampleHouseholdResponse;

    @BeforeEach
    void setUp() {
        sampleUserId = UUID.randomUUID();
        sampleHouseholdId = UUID.randomUUID();
        LocalDateTime createdAt = LocalDateTime.now().minusDays(7);

        // Create sample household response
        sampleHouseholdResponse = HouseholdResponse.builder()
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
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        // Setup common mocks
        when(householdService.getHouseholdById(eq(sampleHouseholdId), any(JwtAuthenticationToken.class)))
                .thenReturn(sampleHouseholdResponse);
        
        // Setup permission evaluator for test household
        when(householdPermissionEvaluator.hasViewPermission(any(), eq(sampleHouseholdId.toString())))
                .thenReturn(true);
        when(householdPermissionEvaluator.hasEditPermission(any(), eq(sampleHouseholdId.toString())))
                .thenReturn(true);
    }

    @Nested
    @DisplayName("GET /api/households/{id}")
    class GetHouseholdById {
        @Test
        @DisplayName("should return household details when valid ID provided")
        void shouldReturnHouseholdDetailsById() throws Exception {
            mockMvc.perform(get("/api/households/" + sampleHouseholdId)
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.id").value(sampleHouseholdId.toString()))
                    .andExpect(jsonPath("$.name").value("Test Household"));

            verify(householdService).getHouseholdById(eq(sampleHouseholdId), any(JwtAuthenticationToken.class));
        }

        @Test
        @DisplayName("should return 404 when household with ID not found")
        void shouldReturn404WhenHouseholdNotFound() throws Exception {
            UUID nonExistentId = UUID.randomUUID();
            String errorMessage = "Household not found";

            when(householdService.getHouseholdById(eq(nonExistentId), any(JwtAuthenticationToken.class)))
                    .thenThrow(new NotFoundException(errorMessage));
            
            when(householdPermissionEvaluator.hasViewPermission(any(), eq(nonExistentId.toString())))
                    .thenReturn(true);

            mockMvc.perform(get("/api/households/" + nonExistentId)
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isNotFound())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.message").value(errorMessage));

            verify(householdService).getHouseholdById(eq(nonExistentId), any(JwtAuthenticationToken.class));
        }

        @Test
        @DisplayName("should return 400 when invalid UUID format provided")
        void shouldReturn400WhenInvalidUuidProvided() throws Exception {
            mockMvc.perform(get("/api/households/invalid-uuid")
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(householdService);
        }
        
        @Test
        @DisplayName("should return 403 when user doesn't have permission to view household")
        void shouldReturn403WhenUserDoesntHavePermissionToViewHousehold() throws Exception {
            UUID restrictedHouseholdId = UUID.randomUUID();
            
            when(householdPermissionEvaluator.hasViewPermission(any(), eq(restrictedHouseholdId.toString())))
                    .thenReturn(false);
                    
            mockMvc.perform(get("/api/households/" + restrictedHouseholdId)
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isForbidden());
                    
            verifyNoInteractions(householdService);
        }
    }

    @Nested
    @DisplayName("GET /api/households")
    class GetUserHouseholds {
        @Test
        @DisplayName("should return all user's households")
        void shouldReturnAllUserHouseholds() throws Exception {
            List<HouseholdResponse> households = new ArrayList<>();
            households.add(sampleHouseholdResponse);
            
            UUID secondHouseholdId = UUID.randomUUID();
            HouseholdResponse secondHousehold = HouseholdResponse.builder()
                    .id(secondHouseholdId)
                    .name("Second Household")
                    .createdAt(LocalDateTime.now().minusDays(3))
                    .updatedAt(LocalDateTime.now().minusDays(1))
                    .build();
            households.add(secondHousehold);
            
            when(householdService.getUserHouseholds(any(JwtAuthenticationToken.class)))
                    .thenReturn(households);
                    
            mockMvc.perform(get("/api/households")
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].id").value(sampleHouseholdId.toString()))
                    .andExpect(jsonPath("$[1].id").value(secondHouseholdId.toString()));
                    
            verify(householdService).getUserHouseholds(any(JwtAuthenticationToken.class));
        }
        
        @Test
        @DisplayName("should return empty list when user has no households")
        void shouldReturnEmptyListWhenUserHasNoHouseholds() throws Exception {
            when(householdService.getUserHouseholds(any(JwtAuthenticationToken.class)))
                    .thenReturn(List.of());
                    
            mockMvc.perform(get("/api/households")
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(0));
                    
            verify(householdService).getUserHouseholds(any(JwtAuthenticationToken.class));
        }
    }

    @Nested
    @DisplayName("POST /api/households")
    class CreateHousehold {
        @Test
        @DisplayName("should create a new household when valid data provided")
        void shouldCreateNewHouseholdWhenValidDataProvided() throws Exception {
            HouseholdRequest request = new HouseholdRequest();
            request.setName("New Household");
            
            when(householdService.createHousehold(any(HouseholdRequest.class), any(JwtAuthenticationToken.class)))
                    .thenReturn(sampleHouseholdResponse);
                    
            mockMvc.perform(post("/api/households")
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.id").value(sampleHouseholdId.toString()))
                    .andExpect(jsonPath("$.name").value("Test Household"));
                    
            verify(householdService).createHousehold(any(HouseholdRequest.class), any(JwtAuthenticationToken.class));
        }
        
        @Test
        @DisplayName("should return 400 when request is invalid")
        void shouldReturn400WhenRequestIsInvalid() throws Exception {
            HouseholdRequest invalidRequest = new HouseholdRequest();
            // Not setting name which is required
            
            mockMvc.perform(post("/api/households")
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(invalidRequest)))
                    .andExpect(status().isBadRequest());
                    
            verifyNoInteractions(householdService);
        }
        
        @Test
        @DisplayName("should return 401 when not authenticated")
        void shouldReturn401WhenNotAuthenticated() throws Exception {
            HouseholdRequest request = new HouseholdRequest();
            request.setName("New Household");
            
            mockMvc.perform(post("/api/households")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());
                    
            verifyNoInteractions(householdService);
        }
        
        @Test
        @DisplayName("should return 409 when household name already exists")
        void shouldReturn409WhenHouseholdNameAlreadyExists() throws Exception {
            HouseholdRequest request = new HouseholdRequest();
            request.setName("Existing Household");
            
            when(householdService.createHousehold(any(HouseholdRequest.class), any(JwtAuthenticationToken.class)))
                    .thenThrow(new IllegalStateException("Household with this name already exists"));
                    
            mockMvc.perform(post("/api/households")
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(request)))
                    .andExpect(status().isConflict())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.message").value("Household with this name already exists"));
                    
            verify(householdService).createHousehold(any(HouseholdRequest.class), any(JwtAuthenticationToken.class));
        }
    }

    @Nested
    @DisplayName("PUT /api/households/{id}")
    class UpdateHousehold {
        @Test
        @DisplayName("should update household when valid data and authorization provided")
        void shouldUpdateHouseholdWhenValidDataAndAuthorizationProvided() throws Exception {
            HouseholdRequest updateRequest = new HouseholdRequest();
            updateRequest.setName("Updated Household");
            
            HouseholdResponse updatedResponse = HouseholdResponse.builder()
                    .id(sampleHouseholdId)
                    .name("Updated Household")
                    .createdAt(sampleHouseholdResponse.getCreatedAt())
                    .updatedAt(LocalDateTime.now())
                    .build();
                    
            when(householdService.updateHousehold(eq(sampleHouseholdId), any(HouseholdRequest.class), any(JwtAuthenticationToken.class)))
                    .thenReturn(updatedResponse);
                    
            mockMvc.perform(put("/api/households/" + sampleHouseholdId)
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(updateRequest)))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.id").value(sampleHouseholdId.toString()))
                    .andExpect(jsonPath("$.name").value("Updated Household"));
                    
            verify(householdService).updateHousehold(eq(sampleHouseholdId), any(HouseholdRequest.class), any(JwtAuthenticationToken.class));
        }
        
        @Test
        @DisplayName("should return 403 when user doesn't have permission to update household")
        void shouldReturn403WhenUserDoesntHavePermissionToUpdateHousehold() throws Exception {
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
        @DisplayName("should return 400 when update request is invalid")
        void shouldReturn400WhenUpdateRequestIsInvalid() throws Exception {
            HouseholdRequest invalidRequest = new HouseholdRequest();
            // Not setting name which is required
            
            mockMvc.perform(put("/api/households/" + sampleHouseholdId)
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(invalidRequest)))
                    .andExpect(status().isBadRequest());
                    
            verifyNoInteractions(householdService);
        }
        
        @Test
        @DisplayName("should return 404 when household to update not found")
        void shouldReturn404WhenHouseholdToUpdateNotFound() throws Exception {
            UUID nonExistentId = UUID.randomUUID();
            HouseholdRequest updateRequest = new HouseholdRequest();
            updateRequest.setName("Updated Household");
            
            when(householdPermissionEvaluator.hasEditPermission(any(), eq(nonExistentId.toString())))
                    .thenReturn(true);
                    
            when(householdService.updateHousehold(eq(nonExistentId), any(HouseholdRequest.class), any(JwtAuthenticationToken.class)))
                    .thenThrow(new NotFoundException("Household not found"));
                    
            mockMvc.perform(put("/api/households/" + nonExistentId)
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(updateRequest)))
                    .andExpect(status().isNotFound())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.message").value("Household not found"));
                    
            verify(householdService).updateHousehold(eq(nonExistentId), any(HouseholdRequest.class), any(JwtAuthenticationToken.class));
        }
        
        @Test
        @DisplayName("should return 409 when updated name conflicts with existing household")
        void shouldReturn409WhenUpdatedNameConflictsWithExistingHousehold() throws Exception {
            HouseholdRequest updateRequest = new HouseholdRequest();
            updateRequest.setName("Conflicting Name");
            
            when(householdService.updateHousehold(eq(sampleHouseholdId), any(HouseholdRequest.class), any(JwtAuthenticationToken.class)))
                    .thenThrow(new IllegalStateException("Household with this name already exists"));
                    
            mockMvc.perform(put("/api/households/" + sampleHouseholdId)
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(updateRequest)))
                    .andExpect(status().isConflict())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.message").value("Household with this name already exists"));
                    
            verify(householdService).updateHousehold(eq(sampleHouseholdId), any(HouseholdRequest.class), any(JwtAuthenticationToken.class));
        }
    }

    @Nested
    @DisplayName("PATCH /api/households/{id}")
    class PatchHousehold {
        @Test
        @DisplayName("should partially update household when valid data provided")
        void shouldPartiallyUpdateHouseholdWhenValidDataProvided() throws Exception {
            Map<String, Object> patchRequest = new HashMap<>();
            patchRequest.put("name", "Patched Household");
            
            HouseholdResponse patchedResponse = HouseholdResponse.builder()
                    .id(sampleHouseholdId)
                    .name("Patched Household")
                    .createdAt(sampleHouseholdResponse.getCreatedAt())
                    .updatedAt(LocalDateTime.now())
                    .build();
                    
            when(householdService.patchHousehold(eq(sampleHouseholdId), anyMap(), any(JwtAuthenticationToken.class)))
                    .thenReturn(patchedResponse);
                    
            mockMvc.perform(patch("/api/households/" + sampleHouseholdId)
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(patchRequest)))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.id").value(sampleHouseholdId.toString()))
                    .andExpect(jsonPath("$.name").value("Patched Household"));
                    
            verify(householdService).patchHousehold(eq(sampleHouseholdId), anyMap(), any(JwtAuthenticationToken.class));
        }
        
        @Test
        @DisplayName("should return 403 when user doesn't have permission to patch household")
        void shouldReturn403WhenUserDoesntHavePermissionToPatchHousehold() throws Exception {
            UUID restrictedHouseholdId = UUID.randomUUID();
            Map<String, Object> patchRequest = new HashMap<>();
            patchRequest.put("name", "Patched Household");
            
            when(householdPermissionEvaluator.hasEditPermission(any(), eq(restrictedHouseholdId.toString())))
                    .thenReturn(false);
                    
            mockMvc.perform(patch("/api/households/" + restrictedHouseholdId)
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(patchRequest)))
                    .andExpect(status().isForbidden());
                    
            verifyNoInteractions(householdService);
        }
        
        @Test
        @DisplayName("should return 404 when household to patch not found")
        void shouldReturn404WhenHouseholdToPatchNotFound() throws Exception {
            UUID nonExistentId = UUID.randomUUID();
            Map<String, Object> patchRequest = new HashMap<>();
            patchRequest.put("name", "Patched Household");
            
            when(householdPermissionEvaluator.hasEditPermission(any(), eq(nonExistentId.toString())))
                    .thenReturn(true);
                    
            when(householdService.patchHousehold(eq(nonExistentId), anyMap(), any(JwtAuthenticationToken.class)))
                    .thenThrow(new NotFoundException("Household not found"));
                    
            mockMvc.perform(patch("/api/households/" + nonExistentId)
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(patchRequest)))
                    .andExpect(status().isNotFound())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.message").value("Household not found"));
                    
            verify(householdService).patchHousehold(eq(nonExistentId), anyMap(), any(JwtAuthenticationToken.class));
        }
    }

    @Nested
    @DisplayName("DELETE /api/households/{id}")
    class DeleteHousehold {
        @Test
        @DisplayName("should delete household when authorized")
        void shouldDeleteHouseholdWhenAuthorized() throws Exception {
            doNothing().when(householdService).deleteHousehold(eq(sampleHouseholdId), any(JwtAuthenticationToken.class));
            
            mockMvc.perform(delete("/api/households/" + sampleHouseholdId)
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isNoContent());
                    
            verify(householdService).deleteHousehold(eq(sampleHouseholdId), any(JwtAuthenticationToken.class));
        }
        
        @Test
        @DisplayName("should return 403 when user doesn't have permission to delete household")
        void shouldReturn403WhenUserDoesntHavePermissionToDeleteHousehold() throws Exception {
            UUID restrictedHouseholdId = UUID.randomUUID();
            
            when(householdPermissionEvaluator.hasEditPermission(any(), eq(restrictedHouseholdId.toString())))
                    .thenReturn(false);
                    
            mockMvc.perform(delete("/api/households/" + restrictedHouseholdId)
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isForbidden());
                    
            verifyNoInteractions(householdService);
        }
        
        @Test
        @DisplayName("should return 404 when household to delete not found")
        void shouldReturn404WhenHouseholdToDeleteNotFound() throws Exception {
            UUID nonExistentId = UUID.randomUUID();
            
            when(householdPermissionEvaluator.hasEditPermission(any(), eq(nonExistentId.toString())))
                    .thenReturn(true);
                    
            doThrow(new NotFoundException("Household not found"))
                    .when(householdService).deleteHousehold(eq(nonExistentId), any(JwtAuthenticationToken.class));
                    
            mockMvc.perform(delete("/api/households/" + nonExistentId)
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isNotFound())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.message").value("Household not found"));
                    
            verify(householdService).deleteHousehold(eq(nonExistentId), any(JwtAuthenticationToken.class));
        }
    }
    
    @Nested
    @DisplayName("POST /api/households/{id}/leave")
    class LeaveHousehold {
        @Test
        @DisplayName("should allow user to leave household when they are a member")
        void shouldAllowUserToLeaveHouseholdWhenTheyAreMember() throws Exception {
            doNothing().when(householdService).leaveHousehold(eq(sampleHouseholdId), any(JwtAuthenticationToken.class));
            
            mockMvc.perform(post("/api/households/" + sampleHouseholdId + "/leave")
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isNoContent());
                    
            verify(householdService).leaveHousehold(eq(sampleHouseholdId), any(JwtAuthenticationToken.class));
        }
        
        @Test
        @DisplayName("should return 403 when user doesn't have access to household")
        void shouldReturn403WhenUserDoesntHaveAccessToHousehold() throws Exception {
            UUID restrictedHouseholdId = UUID.randomUUID();
            
            when(householdPermissionEvaluator.hasViewPermission(any(), eq(restrictedHouseholdId.toString())))
                    .thenReturn(false);
                    
            mockMvc.perform(post("/api/households/" + restrictedHouseholdId + "/leave")
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isForbidden());
                    
            verifyNoInteractions(householdService);
        }
        
        @Test
        @DisplayName("should return 404 when household doesn't exist")
        void shouldReturn404WhenHouseholdDoesntExist() throws Exception {
            UUID nonExistentId = UUID.randomUUID();
            
            when(householdPermissionEvaluator.hasViewPermission(any(), eq(nonExistentId.toString())))
                    .thenReturn(true);
                    
            doThrow(new NotFoundException("Household not found"))
                    .when(householdService).leaveHousehold(eq(nonExistentId), any(JwtAuthenticationToken.class));
                    
            mockMvc.perform(post("/api/households/" + nonExistentId + "/leave")
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isNotFound())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.message").value("Household not found"));
                    
            verify(householdService).leaveHousehold(eq(nonExistentId), any(JwtAuthenticationToken.class));
        }
        
        @Test
        @DisplayName("should return 400 when owner tries to leave household")
        void shouldReturn400WhenOwnerTriesToLeaveHousehold() throws Exception {
            doThrow(new IllegalStateException("Household owner cannot leave the household"))
                    .when(householdService).leaveHousehold(eq(sampleHouseholdId), any(JwtAuthenticationToken.class));
                    
            mockMvc.perform(post("/api/households/" + sampleHouseholdId + "/leave")
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.message").value("Household owner cannot leave the household"));
                    
            verify(householdService).leaveHousehold(eq(sampleHouseholdId), any(JwtAuthenticationToken.class));
        }
    }
    
    @Nested
    @DisplayName("POST /api/households/{id}/transfer-ownership")
    class TransferOwnership {
        @Test
        @DisplayName("should transfer household ownership when current user is owner")
        void shouldTransferHouseholdOwnershipWhenCurrentUserIsOwner() throws Exception {
            UUID newOwnerId = UUID.randomUUID();
            Map<String, UUID> request = Map.of("newOwnerId", newOwnerId);
            
            doNothing().when(householdService).transferOwnership(
                    eq(sampleHouseholdId), eq(newOwnerId), any(JwtAuthenticationToken.class));
                    
            mockMvc.perform(post("/api/households/" + sampleHouseholdId + "/transfer-ownership")
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(request)))
                    .andExpect(status().isNoContent());
                    
            verify(householdService).transferOwnership(
                    eq(sampleHouseholdId), eq(newOwnerId), any(JwtAuthenticationToken.class));
        }
        
        @Test
        @DisplayName("should return 403 when user is not household owner")
        void shouldReturn403WhenUserIsNotHouseholdOwner() throws Exception {
            UUID restrictedHouseholdId = UUID.randomUUID();
            UUID newOwnerId = UUID.randomUUID();
            Map<String, UUID> request = Map.of("newOwnerId", newOwnerId);
            
            when(householdPermissionEvaluator.hasEditPermission(any(), eq(restrictedHouseholdId.toString())))
                    .thenReturn(true);
                    
            doThrow(new ForbiddenException("Only the household owner can transfer ownership"))
                    .when(householdService).transferOwnership(
                            eq(restrictedHouseholdId), eq(newOwnerId), any(JwtAuthenticationToken.class));
                    
            mockMvc.perform(post("/api/households/" + restrictedHouseholdId + "/transfer-ownership")
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(request)))
                    .andExpect(status().isForbidden())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.message").value("Only the household owner can transfer ownership"));
                    
            verify(householdService).transferOwnership(
                    eq(restrictedHouseholdId), eq(newOwnerId), any(JwtAuthenticationToken.class));
        }
        
        @Test
        @DisplayName("should return 404 when new owner is not a member of household")
        void shouldReturn404WhenNewOwnerIsNotMemberOfHousehold() throws Exception {
            UUID newOwnerId = UUID.randomUUID();
            Map<String, UUID> request = Map.of("newOwnerId", newOwnerId);
            
            doThrow(new NotFoundException("User is not a member of this household"))
                    .when(householdService).transferOwnership(
                            eq(sampleHouseholdId), eq(newOwnerId), any(JwtAuthenticationToken.class));
                    
            mockMvc.perform(post("/api/households/" + sampleHouseholdId + "/transfer-ownership")
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(request)))
                    .andExpect(status().isNotFound())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.message").value("User is not a member of this household"));
                    
            verify(householdService).transferOwnership(
                    eq(sampleHouseholdId), eq(newOwnerId), any(JwtAuthenticationToken.class));
        }
        
        @Test
        @DisplayName("should return 400 when request is missing newOwnerId")
        void shouldReturn400WhenRequestIsMissingNewOwnerId() throws Exception {
            Map<String, Object> emptyRequest = new HashMap<>();
            
            mockMvc.perform(post("/api/households/" + sampleHouseholdId + "/transfer-ownership")
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(emptyRequest)))
                    .andExpect(status().isBadRequest());
                    
            verifyNoInteractions(householdService);
        }
    }
    
    @Nested
    @DisplayName("GET /api/households/search")
    class SearchHouseholds {
        @Test
        @DisplayName("should return matching households when search term provided")
        void shouldReturnMatchingHouseholdsWhenSearchTermProvided() throws Exception {
            List<HouseholdResponse> searchResults = new ArrayList<>();
            searchResults.add(sampleHouseholdResponse);
            
            when(householdService.searchHouseholds(eq("Test"), any(JwtAuthenticationToken.class)))
                    .thenReturn(searchResults);
                    
            mockMvc.perform(get("/api/households/search")
                        .param("term", "Test")
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].id").value(sampleHouseholdId.toString()));
                    
            verify(householdService).searchHouseholds(eq("Test"), any(JwtAuthenticationToken.class));
        }
        
        @Test
        @DisplayName("should return empty list when no matches found")
        void shouldReturnEmptyListWhenNoMatchesFound() throws Exception {
            when(householdService.searchHouseholds(eq("NonExistent"), any(JwtAuthenticationToken.class)))
                    .thenReturn(List.of());
                    
            mockMvc.perform(get("/api/households/search")
                        .param("term", "NonExistent")
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(0));
                    
            verify(householdService).searchHouseholds(eq("NonExistent"), any(JwtAuthenticationToken.class));
        }
        
        @Test
        @DisplayName("should return 400 when search term is missing")
        void shouldReturn400WhenSearchTermIsMissing() throws Exception {
            mockMvc.perform(get("/api/households/search")
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isBadRequest());
                    
            verifyNoInteractions(householdService);
        }
    }
}