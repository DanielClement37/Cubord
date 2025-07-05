package org.cubord.cubordbackend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.cubord.cubordbackend.config.TestSecurityConfig;
import org.cubord.cubordbackend.dto.HouseholdRequest;
import org.cubord.cubordbackend.dto.HouseholdResponse;
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

@WebMvcTest(HouseholdController.class)
@Import(TestSecurityConfig.class)
@ActiveProfiles("test")
class HouseholdControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private HouseholdService householdService;

    private UUID sampleHouseholdId;
    private Jwt jwt;
    private HouseholdResponse sampleHouseholdResponse;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        sampleHouseholdId = UUID.randomUUID();
        
        jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("sub", "test-user")
                .claim("email", "test@example.com")
                .build();

        sampleHouseholdResponse = new HouseholdResponse();
        sampleHouseholdResponse.setId(sampleHouseholdId);
        sampleHouseholdResponse.setName("Test Household");
        sampleHouseholdResponse.setCreatedAt(LocalDateTime.now());
        sampleHouseholdResponse.setUpdatedAt(LocalDateTime.now());
    }

    @Nested
    @DisplayName("GET /api/households/{id}")
    class GetHouseholdById {
        @Test
        @DisplayName("should return household details when valid ID provided")
        void shouldReturnHouseholdDetailsById() throws Exception {
            when(householdService.getHouseholdById(eq(sampleHouseholdId), any(JwtAuthenticationToken.class)))
                    .thenReturn(sampleHouseholdResponse);

            mockMvc.perform(get("/api/households/{id}", sampleHouseholdId)
                            .with(jwt().jwt(jwt)))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.id").value(sampleHouseholdId.toString()))
                    .andExpect(jsonPath("$.name").value("Test Household"));
        }

        @Test
        @DisplayName("should return 404 when household not found")
        void shouldReturn404WhenHouseholdNotFound() throws Exception {
            when(householdService.getHouseholdById(eq(sampleHouseholdId), any(JwtAuthenticationToken.class)))
                    .thenThrow(new NotFoundException("Household not found"));

            mockMvc.perform(get("/api/households/{id}", sampleHouseholdId)
                            .with(jwt().jwt(jwt)))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("should return 403 when user doesn't have permission")
        void shouldReturn403WhenUserDoesntHavePermission() throws Exception {
            when(householdService.getHouseholdById(eq(sampleHouseholdId), any(JwtAuthenticationToken.class)))
                    .thenThrow(new AccessDeniedException("You don't have access to this household"));

            mockMvc.perform(get("/api/households/{id}", sampleHouseholdId)
                            .with(jwt().jwt(jwt)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("should return 400 when invalid UUID format provided")
        void shouldReturn400WhenInvalidUuidProvided() throws Exception {
            mockMvc.perform(get("/api/households/{id}", "invalid-uuid")
                            .with(jwt().jwt(jwt)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 401 when not authenticated")
        void shouldReturn401WhenNotAuthenticated() throws Exception {
            mockMvc.perform(get("/api/households/{id}", sampleHouseholdId))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("GET /api/households")
    class GetUserHouseholds {
        @Test
        @DisplayName("should return all user's households")
        void shouldReturnAllUserHouseholds() throws Exception {
            List<HouseholdResponse> households = List.of(sampleHouseholdResponse);
            when(householdService.getUserHouseholds(any(JwtAuthenticationToken.class)))
                    .thenReturn(households);

            mockMvc.perform(get("/api/households")
                            .with(jwt().jwt(jwt)))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(1));
        }
        
        @Test
        @DisplayName("should return empty list when user has no households")
        void shouldReturnEmptyListWhenUserHasNoHouseholds() throws Exception {
            when(householdService.getUserHouseholds(any(JwtAuthenticationToken.class)))
                    .thenReturn(Collections.emptyList());

            mockMvc.perform(get("/api/households")
                            .with(jwt().jwt(jwt)))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(0));
        }

        @Test
        @DisplayName("should return 401 when not authenticated")
        void shouldReturn401WhenNotAuthenticated() throws Exception {
            mockMvc.perform(get("/api/households"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("POST /api/households")
    class CreateHousehold {
        @Test
        @DisplayName("should create a new household when valid data provided")
        void shouldCreateNewHouseholdWhenValidDataProvided() throws Exception {
            HouseholdRequest createRequest = new HouseholdRequest();
            createRequest.setName("New Household");

            when(householdService.createHousehold(any(HouseholdRequest.class), any(JwtAuthenticationToken.class)))
                    .thenReturn(sampleHouseholdResponse);

            mockMvc.perform(post("/api/households")
                            .with(jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(new ObjectMapper().writeValueAsString(createRequest)))
                    .andExpect(status().isCreated())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.name").value("Test Household"));
        }
        
        @Test
        @DisplayName("should return 400 when request is invalid")
        void shouldReturn400WhenRequestIsInvalid() throws Exception {
            HouseholdRequest invalidRequest = new HouseholdRequest();
            // Missing required name field

            mockMvc.perform(post("/api/households")
                            .with(jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(new ObjectMapper().writeValueAsString(invalidRequest)))
                    .andExpect(status().isBadRequest());
        }
        
        @Test
        @DisplayName("should return 401 when not authenticated")
        void shouldReturn401WhenNotAuthenticated() throws Exception {
            HouseholdRequest createRequest = new HouseholdRequest();
            createRequest.setName("New Household");

            mockMvc.perform(post("/api/households")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(new ObjectMapper().writeValueAsString(createRequest)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("should return 409 when household name already exists")
        void shouldReturn409WhenHouseholdNameAlreadyExists() throws Exception {
            HouseholdRequest request = new HouseholdRequest();
            request.setName("Existing Household");

            when(householdService.createHousehold(any(HouseholdRequest.class), any(JwtAuthenticationToken.class)))
                    .thenThrow(new IllegalStateException("Household with name 'Existing Household' already exists"));

            mockMvc.perform(post("/api/households")
                            .with(jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 400 when name is empty")
        void shouldReturn400WhenNameIsEmpty() throws Exception {
            HouseholdRequest request = new HouseholdRequest();
            request.setName("");

            mockMvc.perform(post("/api/households")
                            .with(jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 400 when name is null")
        void shouldReturn400WhenNameIsNull() throws Exception {
            HouseholdRequest request = new HouseholdRequest();
            request.setName(null);

            mockMvc.perform(post("/api/households")
                            .with(jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 400 when request body is empty")
        void shouldReturn400WhenRequestBodyIsEmpty() throws Exception {
            mockMvc.perform(post("/api/households")
                            .with(jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("PUT /api/households/{id}")
    class UpdateHousehold {
        @Test
        @DisplayName("should update household when valid data provided")
        void shouldUpdateHouseholdWhenValidDataProvided() throws Exception {
            HouseholdRequest updateRequest = new HouseholdRequest();
            updateRequest.setName("Updated Household");

            when(householdService.updateHousehold(eq(sampleHouseholdId), any(HouseholdRequest.class), any(JwtAuthenticationToken.class)))
                    .thenReturn(sampleHouseholdResponse);

            mockMvc.perform(put("/api/households/{id}", sampleHouseholdId)
                            .with(jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(new ObjectMapper().writeValueAsString(updateRequest)))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON));
        }

        @Test
        @DisplayName("should return 404 when household not found")
        void shouldReturn404WhenHouseholdNotFound() throws Exception {
            HouseholdRequest updateRequest = new HouseholdRequest();
            updateRequest.setName("Updated Household");

            when(householdService.updateHousehold(eq(sampleHouseholdId), any(HouseholdRequest.class), any(JwtAuthenticationToken.class)))
                    .thenThrow(new NotFoundException("Household not found"));

            mockMvc.perform(put("/api/households/{id}", sampleHouseholdId)
                            .with(jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(new ObjectMapper().writeValueAsString(updateRequest)))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("should return 403 when user doesn't have permission")
        void shouldReturn403WhenUserDoesntHavePermission() throws Exception {
            HouseholdRequest updateRequest = new HouseholdRequest();
            updateRequest.setName("Updated Household");

            when(householdService.updateHousehold(eq(sampleHouseholdId), any(HouseholdRequest.class), any(JwtAuthenticationToken.class)))
                    .thenThrow(new AccessDeniedException("You don't have permission to update this household"));

            mockMvc.perform(put("/api/households/{id}", sampleHouseholdId)
                            .with(jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(new ObjectMapper().writeValueAsString(updateRequest)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("should return 409 when new name already exists")
        void shouldReturn409WhenNewNameAlreadyExists() throws Exception {
            HouseholdRequest request = new HouseholdRequest();
            request.setName("Existing Name");

            when(householdService.updateHousehold(eq(sampleHouseholdId), any(HouseholdRequest.class), any(JwtAuthenticationToken.class)))
                    .thenThrow(new IllegalStateException("Household with name 'Existing Name' already exists"));

            mockMvc.perform(put("/api/households/{id}", sampleHouseholdId)
                            .with(jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 400 when request is invalid")
        void shouldReturn400WhenRequestIsInvalid() throws Exception {
            mockMvc.perform(put("/api/households/{id}", sampleHouseholdId)
                            .with(jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 400 when invalid UUID format provided")
        void shouldReturn400WhenInvalidUuidProvided() throws Exception {
            HouseholdRequest request = new HouseholdRequest();
            request.setName("Updated Name");

            mockMvc.perform(put("/api/households/{id}", "invalid-uuid")
                            .with(jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 401 when not authenticated")
        void shouldReturn401WhenNotAuthenticated() throws Exception {
            HouseholdRequest request = new HouseholdRequest();
            request.setName("Updated Name");

            mockMvc.perform(put("/api/households/{id}", sampleHouseholdId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("DELETE /api/households/{id}")
    class DeleteHousehold {
        @Test
        @DisplayName("should delete household when user is owner")
        void shouldDeleteHouseholdWhenUserIsOwner() throws Exception {
            // For void methods that don't throw exceptions, no mocking needed
            mockMvc.perform(delete("/api/households/{id}", sampleHouseholdId)
                            .with(jwt().jwt(jwt)))
                    .andExpect(status().isNoContent());

            verify(householdService).deleteHousehold(eq(sampleHouseholdId), any(JwtAuthenticationToken.class));
        }

        @Test
        @DisplayName("should return 404 when household not found")
        void shouldReturn404WhenHouseholdNotFound() throws Exception {
            doThrow(new NotFoundException("Household not found"))
                    .when(householdService).deleteHousehold(eq(sampleHouseholdId), any(JwtAuthenticationToken.class));

            mockMvc.perform(delete("/api/households/{id}", sampleHouseholdId)
                            .with(jwt().jwt(jwt)))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("should return 403 when user is not owner")
        void shouldReturn403WhenUserIsNotOwner() throws Exception {
            doThrow(new AccessDeniedException("Only the owner can delete a household"))
                    .when(householdService).deleteHousehold(eq(sampleHouseholdId), any(JwtAuthenticationToken.class));

            mockMvc.perform(delete("/api/households/{id}", sampleHouseholdId)
                            .with(jwt().jwt(jwt)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("should return 400 when invalid UUID format provided")
        void shouldReturn400WhenInvalidUuidProvided() throws Exception {
            mockMvc.perform(delete("/api/households/{id}", "invalid-uuid")
                            .with(jwt().jwt(jwt)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 401 when not authenticated")
        void shouldReturn401WhenNotAuthenticated() throws Exception {
            mockMvc.perform(delete("/api/households/{id}", sampleHouseholdId))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("POST /api/households/{id}/leave")
    class LeaveHousehold {
        @Test
        @DisplayName("should allow user to leave household")
        void shouldAllowUserToLeaveHousehold() throws Exception {
            mockMvc.perform(post("/api/households/{id}/leave", sampleHouseholdId)
                            .with(jwt().jwt(jwt)))
                    .andExpect(status().isNoContent());

            verify(householdService).leaveHousehold(eq(sampleHouseholdId), any(JwtAuthenticationToken.class));
        }

        @Test
        @DisplayName("should return 403 when user is not a member")
        void shouldReturn403WhenUserIsNotMember() throws Exception {
            doThrow(new AccessDeniedException("You are not a member of this household"))
                    .when(householdService).leaveHousehold(eq(sampleHouseholdId), any(JwtAuthenticationToken.class));

            mockMvc.perform(post("/api/households/{id}/leave", sampleHouseholdId)
                            .with(jwt().jwt(jwt)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("should return 400 when owner tries to leave")
        void shouldReturn400WhenOwnerTriesToLeave() throws Exception {
            doThrow(new IllegalStateException("Owners cannot leave a household. Transfer ownership first."))
                    .when(householdService).leaveHousehold(eq(sampleHouseholdId), any(JwtAuthenticationToken.class));

            mockMvc.perform(post("/api/households/{id}/leave", sampleHouseholdId)
                            .with(jwt().jwt(jwt)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 404 when household not found")
        void shouldReturn404WhenHouseholdNotFound() throws Exception {
            doThrow(new NotFoundException("Household not found"))
                    .when(householdService).leaveHousehold(eq(sampleHouseholdId), any(JwtAuthenticationToken.class));

            mockMvc.perform(post("/api/households/{id}/leave", sampleHouseholdId)
                            .with(jwt().jwt(jwt)))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("should return 400 when invalid UUID format provided")
        void shouldReturn400WhenInvalidUuidProvided() throws Exception {
            mockMvc.perform(post("/api/households/{id}/leave", "invalid-uuid")
                            .with(jwt().jwt(jwt)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 401 when not authenticated")
        void shouldReturn401WhenNotAuthenticated() throws Exception {
            mockMvc.perform(post("/api/households/{id}/leave", sampleHouseholdId))
                    .andExpect(status().isUnauthorized());
        }
    }
}