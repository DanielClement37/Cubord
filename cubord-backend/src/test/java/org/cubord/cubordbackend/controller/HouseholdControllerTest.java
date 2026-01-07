package org.cubord.cubordbackend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.cubord.cubordbackend.config.TestSecurityConfig;
import org.cubord.cubordbackend.dto.household.HouseholdRequest;
import org.cubord.cubordbackend.dto.household.HouseholdResponse;
import org.cubord.cubordbackend.exception.*;
import org.cubord.cubordbackend.security.SecurityService;
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
 * Unit tests for {@link HouseholdController}.
 *
 * <p>Tests verify:</p>
 * <ul>
 *   <li>Endpoint functionality and response formats</li>
 *   <li>Authorization via {@code @PreAuthorize} with mocked SecurityService</li>
 *   <li>Proper exception handling and HTTP status codes</li>
 *   <li>Input validation</li>
 * </ul>
 */
@WebMvcTest(HouseholdController.class)
@Import(TestSecurityConfig.class)
@ActiveProfiles("test")
class HouseholdControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private HouseholdService householdService;

    @MockitoBean(name = "security")
    private SecurityService securityService;

    @MockitoBean
    private org.cubord.cubordbackend.security.HouseholdPermissionEvaluator householdPermissionEvaluator;

    private UUID currentUserId;
    private UUID sampleHouseholdId;
    private UUID otherHouseholdId;
    private HouseholdResponse sampleHouseholdResponse;
    private HouseholdRequest sampleHouseholdRequest;
    private Jwt jwt;

    @BeforeEach
    void setUp() {
        currentUserId = UUID.randomUUID();
        sampleHouseholdId = UUID.randomUUID();
        otherHouseholdId = UUID.randomUUID();

        sampleHouseholdRequest = HouseholdRequest.builder()
                .name("Test Household")
                .build();

        sampleHouseholdResponse = HouseholdResponse.builder()
                .id(sampleHouseholdId)
                .name("Test Household")
                .createdAt(LocalDateTime.now().minusDays(7))
                .updatedAt(LocalDateTime.now())
                .build();

        jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(currentUserId.toString())
                .claim("email", "test@example.com")
                .claim("name", "Test User")
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        // Default security service mock behavior
        when(securityService.canAccessHousehold(sampleHouseholdId)).thenReturn(true);
        when(securityService.canAccessHousehold(otherHouseholdId)).thenReturn(false);
        when(securityService.canModifyHousehold(sampleHouseholdId)).thenReturn(true);
        when(securityService.canModifyHousehold(otherHouseholdId)).thenReturn(false);
        when(securityService.isHouseholdOwner(sampleHouseholdId)).thenReturn(true);
        when(securityService.isHouseholdOwner(otherHouseholdId)).thenReturn(false);
    }

    // ==================== Create Operations Tests ====================

    @Nested
    @DisplayName("POST /api/households")
    class CreateHousehold {

        @Test
        @DisplayName("should create household for authenticated user")
        void shouldCreateHouseholdForAuthenticatedUser() throws Exception {
            when(householdService.createHousehold(any(HouseholdRequest.class)))
                    .thenReturn(sampleHouseholdResponse);

            mockMvc.perform(post("/api/households")
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(sampleHouseholdRequest)))
                    .andExpect(status().isCreated())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.id").value(sampleHouseholdId.toString()))
                    .andExpect(jsonPath("$.name").value("Test Household"));

            verify(householdService).createHousehold(any(HouseholdRequest.class));
        }

        @Test
        @DisplayName("should return 401 for unauthenticated request")
        void shouldReturn401ForUnauthenticatedRequest() throws Exception {
            mockMvc.perform(post("/api/households")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(sampleHouseholdRequest)))
                    .andExpect(status().isUnauthorized());

            verifyNoInteractions(householdService);
        }

        @Test
        @DisplayName("should return 400 when request body is invalid")
        void shouldReturn400WhenRequestBodyIsInvalid() throws Exception {
            HouseholdRequest invalidRequest = HouseholdRequest.builder()
                    .name("") // Invalid: empty name
                    .build();

            mockMvc.perform(post("/api/households")
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(invalidRequest)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(householdService);
        }

        @Test
        @DisplayName("should return 400 when name is null")
        void shouldReturn400WhenNameIsNull() throws Exception {
            HouseholdRequest invalidRequest = HouseholdRequest.builder()
                    .name(null)
                    .build();

            mockMvc.perform(post("/api/households")
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(invalidRequest)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(householdService);
        }

        @Test
        @DisplayName("should return 409 when household name already exists")
        void shouldReturn409WhenHouseholdNameAlreadyExists() throws Exception {
            when(householdService.createHousehold(any(HouseholdRequest.class)))
                    .thenThrow(new ConflictException("Household with name 'Test Household' already exists"));

            mockMvc.perform(post("/api/households")
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(sampleHouseholdRequest)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error_code").value("RESOURCE_CONFLICT"));

            verify(householdService).createHousehold(any(HouseholdRequest.class));
        }

        @Test
        @DisplayName("should return 400 when validation exception occurs")
        void shouldReturn400WhenValidationExceptionOccurs() throws Exception {
            when(householdService.createHousehold(any(HouseholdRequest.class)))
                    .thenThrow(new ValidationException("Household name cannot be null or empty"));

            mockMvc.perform(post("/api/households")
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(sampleHouseholdRequest)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error_code").value("VALIDATION_ERROR"));

            verify(householdService).createHousehold(any(HouseholdRequest.class));
        }

        @Test
        @DisplayName("should return 409 when data integrity exception occurs")
        void shouldReturn409WhenDataIntegrityExceptionOccurs() throws Exception {
            when(householdService.createHousehold(any(HouseholdRequest.class)))
                    .thenThrow(new DataIntegrityException("Failed to create household"));

            mockMvc.perform(post("/api/households")
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(sampleHouseholdRequest)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error_code").value("DATA_INTEGRITY_VIOLATION"));

            verify(householdService).createHousehold(any(HouseholdRequest.class));
        }
    }

    // ==================== Query Operations Tests ====================

    @Nested
    @DisplayName("GET /api/households")
    class GetUserHouseholds {

        @Test
        @DisplayName("should return all user's households")
        void shouldReturnAllUserHouseholds() throws Exception {
            List<HouseholdResponse> households = List.of(sampleHouseholdResponse);
            when(householdService.getUserHouseholds()).thenReturn(households);

            mockMvc.perform(get("/api/households")
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(header().string("Cache-Control", "max-age=120"))
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].id").value(sampleHouseholdId.toString()))
                    .andExpect(jsonPath("$[0].name").value("Test Household"));

            verify(householdService).getUserHouseholds();
        }

        @Test
        @DisplayName("should return empty list when user has no households")
        void shouldReturnEmptyListWhenUserHasNoHouseholds() throws Exception {
            when(householdService.getUserHouseholds()).thenReturn(Collections.emptyList());

            mockMvc.perform(get("/api/households")
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(0));

            verify(householdService).getUserHouseholds();
        }

        @Test
        @DisplayName("should return 401 for unauthenticated request")
        void shouldReturn401ForUnauthenticatedRequest() throws Exception {
            mockMvc.perform(get("/api/households"))
                    .andExpect(status().isUnauthorized());

            verifyNoInteractions(householdService);
        }

        @Test
        @DisplayName("should return 401 when authentication fails")
        void shouldReturn401WhenAuthenticationFails() throws Exception {
            when(householdService.getUserHouseholds())
                    .thenThrow(new AuthenticationRequiredException("No authenticated user found"));

            mockMvc.perform(get("/api/households")
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error_code").value("AUTHENTICATION_REQUIRED"));

            verify(householdService).getUserHouseholds();
        }
    }

    @Nested
    @DisplayName("GET /api/households/{id}")
    class GetHouseholdById {

        @Test
        @DisplayName("should return household details when user is a member")
        void shouldReturnHouseholdDetailsWhenUserIsMember() throws Exception {
            when(householdService.getHouseholdById(sampleHouseholdId))
                    .thenReturn(sampleHouseholdResponse);

            mockMvc.perform(get("/api/households/" + sampleHouseholdId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(header().string("Cache-Control", "max-age=300"))
                    .andExpect(jsonPath("$.id").value(sampleHouseholdId.toString()))
                    .andExpect(jsonPath("$.name").value("Test Household"));

            verify(securityService).canAccessHousehold(sampleHouseholdId);
            verify(householdService).getHouseholdById(sampleHouseholdId);
        }

        @Test
        @DisplayName("should return 403 when user is not a member")
        void shouldReturn403WhenUserIsNotMember() throws Exception {
            mockMvc.perform(get("/api/households/" + otherHouseholdId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isForbidden());

            verify(securityService).canAccessHousehold(otherHouseholdId);
            verifyNoInteractions(householdService);
        }

        @Test
        @DisplayName("should return 404 when household not found")
        void shouldReturn404WhenHouseholdNotFound() throws Exception {
            when(householdService.getHouseholdById(sampleHouseholdId))
                    .thenThrow(new NotFoundException("Household", sampleHouseholdId));

            mockMvc.perform(get("/api/households/" + sampleHouseholdId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error_code").value("RESOURCE_NOT_FOUND"));

            verify(householdService).getHouseholdById(sampleHouseholdId);
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
        @DisplayName("should return 401 for unauthenticated request")
        void shouldReturn401ForUnauthenticatedRequest() throws Exception {
            mockMvc.perform(get("/api/households/" + sampleHouseholdId))
                    .andExpect(status().isUnauthorized());

            verifyNoInteractions(householdService);
        }
    }

    @Nested
    @DisplayName("GET /api/households/search")
    class SearchHouseholds {

        @Test
        @DisplayName("should return matching households for authenticated user")
        void shouldReturnMatchingHouseholdsForAuthenticatedUser() throws Exception {
            String searchQuery = "Test";
            List<HouseholdResponse> results = List.of(sampleHouseholdResponse);
            when(householdService.searchHouseholds(searchQuery)).thenReturn(results);

            mockMvc.perform(get("/api/households/search")
                            .param("query", searchQuery)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(header().string("Cache-Control", "max-age=60"))
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].name").value("Test Household"));

            verify(householdService).searchHouseholds(searchQuery);
        }

        @Test
        @DisplayName("should return empty list when no households match")
        void shouldReturnEmptyListWhenNoHouseholdsMatch() throws Exception {
            String searchQuery = "NonExistent";
            when(householdService.searchHouseholds(searchQuery)).thenReturn(Collections.emptyList());

            mockMvc.perform(get("/api/households/search")
                            .param("query", searchQuery)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(0));

            verify(householdService).searchHouseholds(searchQuery);
        }

        @Test
        @DisplayName("should return 400 when query parameter is missing")
        void shouldReturn400WhenQueryParameterIsMissing() throws Exception {
            mockMvc.perform(get("/api/households/search")
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(householdService);
        }

        @Test
        @DisplayName("should return 400 when query parameter is blank")
        void shouldReturn400WhenQueryParameterIsBlank() throws Exception {
            when(householdService.searchHouseholds("   "))
                    .thenThrow(new ValidationException("Search term cannot be null or empty"));

            mockMvc.perform(get("/api/households/search")
                            .param("query", "   ")
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error_code").value("VALIDATION_ERROR"));
        }

        @Test
        @DisplayName("should return 401 for unauthenticated request")
        void shouldReturn401ForUnauthenticatedRequest() throws Exception {
            mockMvc.perform(get("/api/households/search")
                            .param("query", "Test"))
                    .andExpect(status().isUnauthorized());

            verifyNoInteractions(householdService);
        }
    }

    // ==================== Update Operations Tests ====================

    @Nested
    @DisplayName("PUT /api/households/{id}")
    class UpdateHousehold {

        @Test
        @DisplayName("should update household when user has modify permission")
        void shouldUpdateHouseholdWhenUserHasModifyPermission() throws Exception {
            HouseholdRequest updateRequest = HouseholdRequest.builder()
                    .name("Updated Household")
                    .build();

            HouseholdResponse updatedResponse = HouseholdResponse.builder()
                    .id(sampleHouseholdId)
                    .name("Updated Household")
                    .createdAt(LocalDateTime.now().minusDays(7))
                    .updatedAt(LocalDateTime.now())
                    .build();

            when(householdService.updateHousehold(eq(sampleHouseholdId), any(HouseholdRequest.class)))
                    .thenReturn(updatedResponse);

            mockMvc.perform(put("/api/households/" + sampleHouseholdId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(updateRequest)))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.id").value(sampleHouseholdId.toString()))
                    .andExpect(jsonPath("$.name").value("Updated Household"));

            verify(securityService).canModifyHousehold(sampleHouseholdId);
            verify(householdService).updateHousehold(eq(sampleHouseholdId), any(HouseholdRequest.class));
        }

        @Test
        @DisplayName("should return 403 when user lacks modify permission")
        void shouldReturn403WhenUserLacksModifyPermission() throws Exception {
            HouseholdRequest updateRequest = HouseholdRequest.builder()
                    .name("Updated Household")
                    .build();

            mockMvc.perform(put("/api/households/" + otherHouseholdId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(updateRequest)))
                    .andExpect(status().isForbidden());

            verify(securityService).canModifyHousehold(otherHouseholdId);
            verifyNoInteractions(householdService);
        }

        @Test
        @DisplayName("should return 404 when household not found")
        void shouldReturn404WhenHouseholdNotFound() throws Exception {
            HouseholdRequest updateRequest = HouseholdRequest.builder()
                    .name("Updated Household")
                    .build();

            when(householdService.updateHousehold(eq(sampleHouseholdId), any(HouseholdRequest.class)))
                    .thenThrow(new NotFoundException("Household", sampleHouseholdId));

            mockMvc.perform(put("/api/households/" + sampleHouseholdId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(updateRequest)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error_code").value("RESOURCE_NOT_FOUND"));

            verify(householdService).updateHousehold(eq(sampleHouseholdId), any(HouseholdRequest.class));
        }

        @Test
        @DisplayName("should return 409 when new name already exists")
        void shouldReturn409WhenNewNameAlreadyExists() throws Exception {
            HouseholdRequest updateRequest = HouseholdRequest.builder()
                    .name("Existing Name")
                    .build();

            when(householdService.updateHousehold(eq(sampleHouseholdId), any(HouseholdRequest.class)))
                    .thenThrow(new ConflictException("Household with name 'Existing Name' already exists"));

            mockMvc.perform(put("/api/households/" + sampleHouseholdId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(updateRequest)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error_code").value("RESOURCE_CONFLICT"));

            verify(householdService).updateHousehold(eq(sampleHouseholdId), any(HouseholdRequest.class));
        }

        @Test
        @DisplayName("should return 400 when request is invalid")
        void shouldReturn400WhenRequestIsInvalid() throws Exception {
            HouseholdRequest invalidRequest = HouseholdRequest.builder()
                    .name("") // Invalid
                    .build();

            mockMvc.perform(put("/api/households/" + sampleHouseholdId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(invalidRequest)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(householdService);
        }

        @Test
        @DisplayName("should return 400 when invalid UUID format provided")
        void shouldReturn400WhenInvalidUuidProvided() throws Exception {
            HouseholdRequest updateRequest = HouseholdRequest.builder()
                    .name("Updated Name")
                    .build();

            mockMvc.perform(put("/api/households/invalid-uuid")
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(updateRequest)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(householdService);
        }

        @Test
        @DisplayName("should return 401 for unauthenticated request")
        void shouldReturn401ForUnauthenticatedRequest() throws Exception {
            HouseholdRequest updateRequest = HouseholdRequest.builder()
                    .name("Updated Name")
                    .build();

            mockMvc.perform(put("/api/households/" + sampleHouseholdId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(updateRequest)))
                    .andExpect(status().isUnauthorized());

            verifyNoInteractions(householdService);
        }
    }

    // ==================== Delete Operations Tests ====================

    @Nested
    @DisplayName("DELETE /api/households/{id}")
    class DeleteHousehold {

        @Test
        @DisplayName("should delete household when user is owner")
        void shouldDeleteHouseholdWhenUserIsOwner() throws Exception {
            doNothing().when(householdService).deleteHousehold(sampleHouseholdId);

            mockMvc.perform(delete("/api/households/" + sampleHouseholdId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isNoContent());

            verify(securityService).isHouseholdOwner(sampleHouseholdId);
            verify(householdService).deleteHousehold(sampleHouseholdId);
        }

        @Test
        @DisplayName("should return 403 when user is not owner")
        void shouldReturn403WhenUserIsNotOwner() throws Exception {
            mockMvc.perform(delete("/api/households/" + otherHouseholdId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isForbidden());

            verify(securityService).isHouseholdOwner(otherHouseholdId);
            verifyNoInteractions(householdService);
        }

        @Test
        @DisplayName("should return 404 when household not found")
        void shouldReturn404WhenHouseholdNotFound() throws Exception {
            doThrow(new NotFoundException("Household", sampleHouseholdId))
                    .when(householdService).deleteHousehold(sampleHouseholdId);

            mockMvc.perform(delete("/api/households/" + sampleHouseholdId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error_code").value("RESOURCE_NOT_FOUND"));

            verify(householdService).deleteHousehold(sampleHouseholdId);
        }

        @Test
        @DisplayName("should return 400 when invalid UUID format provided")
        void shouldReturn400WhenInvalidUuidProvided() throws Exception {
            mockMvc.perform(delete("/api/households/invalid-uuid")
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(householdService);
        }

        @Test
        @DisplayName("should return 401 for unauthenticated request")
        void shouldReturn401ForUnauthenticatedRequest() throws Exception {
            mockMvc.perform(delete("/api/households/" + sampleHouseholdId))
                    .andExpect(status().isUnauthorized());

            verifyNoInteractions(householdService);
        }

        @Test
        @DisplayName("should return 409 when data integrity exception occurs")
        void shouldReturn409WhenDataIntegrityExceptionOccurs() throws Exception {
            doThrow(new DataIntegrityException("Cannot delete household with active members"))
                    .when(householdService).deleteHousehold(sampleHouseholdId);

            mockMvc.perform(delete("/api/households/" + sampleHouseholdId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error_code").value("DATA_INTEGRITY_VIOLATION"));

            verify(householdService).deleteHousehold(sampleHouseholdId);
        }
    }

    // ==================== Member Operations Tests ====================

    @Nested
    @DisplayName("POST /api/households/{id}/leave")
    class LeaveHousehold {

        @Test
        @DisplayName("should allow member to leave household")
        void shouldAllowMemberToLeaveHousehold() throws Exception {
            doNothing().when(householdService).leaveHousehold(sampleHouseholdId);

            mockMvc.perform(post("/api/households/" + sampleHouseholdId + "/leave")
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isNoContent());

            verify(securityService).canAccessHousehold(sampleHouseholdId);
            verify(householdService).leaveHousehold(sampleHouseholdId);
        }

        @Test
        @DisplayName("should return 403 when user is not a member")
        void shouldReturn403WhenUserIsNotMember() throws Exception {
            mockMvc.perform(post("/api/households/" + otherHouseholdId + "/leave")
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isForbidden());

            verify(securityService).canAccessHousehold(otherHouseholdId);
            verifyNoInteractions(householdService);
        }

        @Test
        @DisplayName("should return 400 when owner tries to leave")
        void shouldReturn400WhenOwnerTriesToLeave() throws Exception {
            doThrow(new ResourceStateException("Household owners cannot leave. Transfer ownership or delete the household instead."))
                    .when(householdService).leaveHousehold(sampleHouseholdId);

            mockMvc.perform(post("/api/households/" + sampleHouseholdId + "/leave")
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error_code").value("RESOURCE_STATE_CONFLICT"));

            verify(householdService).leaveHousehold(sampleHouseholdId);
        }

        @Test
        @DisplayName("should return 404 when household not found")
        void shouldReturn404WhenHouseholdNotFound() throws Exception {
            doThrow(new NotFoundException("Household", sampleHouseholdId))
                    .when(householdService).leaveHousehold(sampleHouseholdId);

            mockMvc.perform(post("/api/households/" + sampleHouseholdId + "/leave")
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error_code").value("RESOURCE_NOT_FOUND"));

            verify(householdService).leaveHousehold(sampleHouseholdId);
        }

        @Test
        @DisplayName("should return 400 when invalid UUID format provided")
        void shouldReturn400WhenInvalidUuidProvided() throws Exception {
            mockMvc.perform(post("/api/households/invalid-uuid/leave")
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(householdService);
        }

        @Test
        @DisplayName("should return 401 for unauthenticated request")
        void shouldReturn401ForUnauthenticatedRequest() throws Exception {
            mockMvc.perform(post("/api/households/" + sampleHouseholdId + "/leave"))
                    .andExpect(status().isUnauthorized());

            verifyNoInteractions(householdService);
        }

        @Test
        @DisplayName("should return 409 when data integrity exception occurs")
        void shouldReturn409WhenDataIntegrityExceptionOccurs() throws Exception {
            doThrow(new DataIntegrityException("Failed to remove household membership"))
                    .when(householdService).leaveHousehold(sampleHouseholdId);

            mockMvc.perform(post("/api/households/" + sampleHouseholdId + "/leave")
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error_code").value("DATA_INTEGRITY_VIOLATION"));

            verify(householdService).leaveHousehold(sampleHouseholdId);
        }
    }
}