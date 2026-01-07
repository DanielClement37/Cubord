package org.cubord.cubordbackend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.cubord.cubordbackend.config.TestSecurityConfig;
import org.cubord.cubordbackend.dto.location.LocationRequest;
import org.cubord.cubordbackend.dto.location.LocationResponse;
import org.cubord.cubordbackend.dto.location.LocationUpdateRequest;
import org.cubord.cubordbackend.exception.*;
import org.cubord.cubordbackend.security.SecurityService;
import org.cubord.cubordbackend.service.LocationService;
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
import java.util.*;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for {@link LocationController}.
 *
 * <p>Tests verify:</p>
 * <ul>
 *   <li>Endpoint functionality and response formats</li>
 *   <li>Authorization via {@code @PreAuthorize} with mocked SecurityService</li>
 *   <li>Proper exception handling and HTTP status codes</li>
 *   <li>Input validation</li>
 *   <li>Cache control headers for GET operations</li>
 * </ul>
 */
@WebMvcTest(LocationController.class)
@Import(TestSecurityConfig.class)
@ActiveProfiles("test")
class LocationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private LocationService locationService;

    @MockitoBean(name = "security")
    private SecurityService securityService;

    @MockitoBean
    private org.cubord.cubordbackend.security.HouseholdPermissionEvaluator householdPermissionEvaluator;

    private UUID currentUserId;
    private UUID householdId;
    private UUID otherHouseholdId;
    private UUID locationId;
    private LocationRequest sampleLocationRequest;
    private LocationResponse sampleLocationResponse;
    private Jwt jwt;

    @BeforeEach
    void setUp() {
        currentUserId = UUID.randomUUID();
        householdId = UUID.randomUUID();
        otherHouseholdId = UUID.randomUUID();
        locationId = UUID.randomUUID();

        sampleLocationRequest = LocationRequest.builder()
                .name("Kitchen")
                .description("Main kitchen area")
                .householdId(householdId)
                .build();

        sampleLocationResponse = LocationResponse.builder()
                .id(locationId)
                .name("Kitchen")
                .description("Main kitchen area")
                .householdId(householdId)
                .householdName("Test Household")
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
        when(securityService.canAccessHousehold(householdId)).thenReturn(true);
        when(securityService.canAccessHousehold(otherHouseholdId)).thenReturn(false);
        when(securityService.canModifyHousehold(householdId)).thenReturn(true);
        when(securityService.canModifyHousehold(otherHouseholdId)).thenReturn(false);
    }

    // ==================== Create Operations Tests ====================

    @Nested
    @DisplayName("POST /api/households/{householdId}/locations")
    class CreateLocation {

        @Test
        @DisplayName("should create location when user has admin permissions")
        void shouldCreateLocationWhenUserHasAdminPermissions() throws Exception {
            when(locationService.createLocation(any(LocationRequest.class)))
                    .thenReturn(sampleLocationResponse);

            mockMvc.perform(post("/api/households/{householdId}/locations", householdId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(sampleLocationRequest)))
                    .andExpect(status().isCreated())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.id").value(locationId.toString()))
                    .andExpect(jsonPath("$.name").value("Kitchen"))
                    .andExpect(jsonPath("$.description").value("Main kitchen area"))
                    .andExpect(jsonPath("$.householdId").value(householdId.toString()));

            verify(securityService).canModifyHousehold(householdId);
            verify(locationService).createLocation(any(LocationRequest.class));
        }

        @Test
        @DisplayName("should return 401 for unauthenticated request")
        void shouldReturn401ForUnauthenticatedRequest() throws Exception {
            mockMvc.perform(post("/api/households/{householdId}/locations", householdId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(sampleLocationRequest)))
                    .andExpect(status().isUnauthorized());

            verifyNoInteractions(locationService);
        }

        @Test
        @DisplayName("should return 403 when user lacks admin permissions")
        void shouldReturn403WhenUserLacksAdminPermissions() throws Exception {
            when(securityService.canModifyHousehold(otherHouseholdId)).thenReturn(false);

            LocationRequest request = LocationRequest.builder()
                    .name("Kitchen")
                    .description("Main kitchen")
                    .householdId(otherHouseholdId)
                    .build();

            mockMvc.perform(post("/api/households/{householdId}/locations", otherHouseholdId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());

            verify(securityService).canModifyHousehold(otherHouseholdId);
            verifyNoInteractions(locationService);
        }

        @Test
        @DisplayName("should return 400 when request body is invalid")
        void shouldReturn400WhenRequestBodyIsInvalid() throws Exception {
            LocationRequest invalidRequest = LocationRequest.builder()
                    .name("") // Invalid: empty name
                    .householdId(householdId)
                    .build();

            mockMvc.perform(post("/api/households/{householdId}/locations", householdId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(invalidRequest)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(locationService);
        }

        @Test
        @DisplayName("should return 400 when name is null")
        void shouldReturn400WhenNameIsNull() throws Exception {
            LocationRequest invalidRequest = LocationRequest.builder()
                    .description("Description without name")
                    .householdId(householdId)
                    .build();

            mockMvc.perform(post("/api/households/{householdId}/locations", householdId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(invalidRequest)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(locationService);
        }

        @Test
        @DisplayName("should return 400 when household ID is null")
        void shouldReturn400WhenHouseholdIdIsNull() throws Exception {
            LocationRequest invalidRequest = LocationRequest.builder()
                    .name("Kitchen")
                    .description("Main kitchen")
                    .build(); // Missing householdId

            mockMvc.perform(post("/api/households/{householdId}/locations", householdId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(invalidRequest)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(locationService);
        }

        @Test
        @DisplayName("should return 404 when household not found")
        void shouldReturn404WhenHouseholdNotFound() throws Exception {
            when(locationService.createLocation(any(LocationRequest.class)))
                    .thenThrow(new NotFoundException("Household not found with ID: " + householdId));

            mockMvc.perform(post("/api/households/{householdId}/locations", householdId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(sampleLocationRequest)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error_code").value("RESOURCE_NOT_FOUND"));

            verify(locationService).createLocation(any(LocationRequest.class));
        }

        @Test
        @DisplayName("should return 409 when location name already exists in household")
        void shouldReturn409WhenLocationNameAlreadyExists() throws Exception {
            when(locationService.createLocation(any(LocationRequest.class)))
                    .thenThrow(new ConflictException("Location with name 'Kitchen' already exists in this household"));

            mockMvc.perform(post("/api/households/{householdId}/locations", householdId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(sampleLocationRequest)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error_code").value("RESOURCE_CONFLICT"));

            verify(locationService).createLocation(any(LocationRequest.class));
        }

        @Test
        @DisplayName("should return 400 when validation exception occurs")
        void shouldReturn400WhenValidationExceptionOccurs() throws Exception {
            when(locationService.createLocation(any(LocationRequest.class)))
                    .thenThrow(new ValidationException("Location name cannot be empty"));

            mockMvc.perform(post("/api/households/{householdId}/locations", householdId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(sampleLocationRequest)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error_code").value("VALIDATION_ERROR"));

            verify(locationService).createLocation(any(LocationRequest.class));
        }

        @Test
        @DisplayName("should return 409 when data integrity exception occurs")
        void shouldReturn409WhenDataIntegrityExceptionOccurs() throws Exception {
            when(locationService.createLocation(any(LocationRequest.class)))
                    .thenThrow(new DataIntegrityException("Failed to create location: database constraint violation"));

            mockMvc.perform(post("/api/households/{householdId}/locations", householdId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(sampleLocationRequest)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error_code").value("DATA_INTEGRITY_VIOLATION"));

            verify(locationService).createLocation(any(LocationRequest.class));
        }
    }

    // ==================== Query Operations Tests ====================

    @Nested
    @DisplayName("GET /api/households/{householdId}/locations/{locationId}")
    class GetLocationById {

        @Test
        @DisplayName("should return location when user has access to household")
        void shouldReturnLocationWhenUserHasAccess() throws Exception {
            when(locationService.getLocationById(locationId))
                    .thenReturn(sampleLocationResponse);

            mockMvc.perform(get("/api/households/{householdId}/locations/{locationId}",
                            householdId, locationId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.id").value(locationId.toString()))
                    .andExpect(jsonPath("$.name").value("Kitchen"))
                    .andExpect(jsonPath("$.description").value("Main kitchen area"))
                    .andExpect(jsonPath("$.householdId").value(householdId.toString()))
                    .andExpect(header().string("Cache-Control", "max-age=300"));

            verify(securityService).canAccessHousehold(householdId);
            verify(locationService).getLocationById(locationId);
        }

        @Test
        @DisplayName("should return 401 for unauthenticated request")
        void shouldReturn401ForUnauthenticatedRequest() throws Exception {
            mockMvc.perform(get("/api/households/{householdId}/locations/{locationId}",
                            householdId, locationId))
                    .andExpect(status().isUnauthorized());

            verifyNoInteractions(locationService);
        }

        @Test
        @DisplayName("should return 403 when user lacks household access")
        void shouldReturn403WhenUserLacksAccess() throws Exception {
            mockMvc.perform(get("/api/households/{householdId}/locations/{locationId}",
                            otherHouseholdId, locationId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isForbidden());

            verify(securityService).canAccessHousehold(otherHouseholdId);
            verifyNoInteractions(locationService);
        }

        @Test
        @DisplayName("should return 404 when location not found")
        void shouldReturn404WhenLocationNotFound() throws Exception {
            when(locationService.getLocationById(locationId))
                    .thenThrow(new NotFoundException("Location not found with ID: " + locationId));

            mockMvc.perform(get("/api/households/{householdId}/locations/{locationId}",
                            householdId, locationId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error_code").value("RESOURCE_NOT_FOUND"));

            verify(locationService).getLocationById(locationId);
        }

        @Test
        @DisplayName("should return 400 when invalid UUID format provided")
        void shouldReturn400WhenInvalidUuidProvided() throws Exception {
            mockMvc.perform(get("/api/households/{householdId}/locations/invalid-uuid", householdId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(locationService);
        }
    }

    @Nested
    @DisplayName("GET /api/households/{householdId}/locations")
    class GetLocationsByHousehold {

        @Test
        @DisplayName("should return all locations when user has access to household")
        void shouldReturnAllLocations() throws Exception {
            LocationResponse location1 = LocationResponse.builder()
                    .id(UUID.randomUUID())
                    .name("Kitchen")
                    .householdId(householdId)
                    .build();

            LocationResponse location2 = LocationResponse.builder()
                    .id(UUID.randomUUID())
                    .name("Pantry")
                    .householdId(householdId)
                    .build();

            List<LocationResponse> locations = List.of(location1, location2);
            when(locationService.getLocationsByHousehold(householdId))
                    .thenReturn(locations);

            mockMvc.perform(get("/api/households/{householdId}/locations", householdId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].name").value("Kitchen"))
                    .andExpect(jsonPath("$[1].name").value("Pantry"))
                    .andExpect(header().string("Cache-Control", "max-age=120"));

            verify(securityService).canAccessHousehold(householdId);
            verify(locationService).getLocationsByHousehold(householdId);
        }

        @Test
        @DisplayName("should return empty list when household has no locations")
        void shouldReturnEmptyListWhenNoLocations() throws Exception {
            when(locationService.getLocationsByHousehold(householdId))
                    .thenReturn(Collections.emptyList());

            mockMvc.perform(get("/api/households/{householdId}/locations", householdId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(0));

            verify(locationService).getLocationsByHousehold(householdId);
        }

        @Test
        @DisplayName("should return 401 for unauthenticated request")
        void shouldReturn401ForUnauthenticatedRequest() throws Exception {
            mockMvc.perform(get("/api/households/{householdId}/locations", householdId))
                    .andExpect(status().isUnauthorized());

            verifyNoInteractions(locationService);
        }

        @Test
        @DisplayName("should return 403 when user lacks household access")
        void shouldReturn403WhenUserLacksAccess() throws Exception {
            mockMvc.perform(get("/api/households/{householdId}/locations", otherHouseholdId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isForbidden());

            verify(securityService).canAccessHousehold(otherHouseholdId);
            verifyNoInteractions(locationService);
        }
    }

    @Nested
    @DisplayName("GET /api/households/{householdId}/locations/search")
    class SearchLocations {

        @Test
        @DisplayName("should return matching locations when search query provided")
        void shouldReturnMatchingLocations() throws Exception {
            LocationResponse location1 = LocationResponse.builder()
                    .id(UUID.randomUUID())
                    .name("Kitchen Cabinet")
                    .householdId(householdId)
                    .build();

            LocationResponse location2 = LocationResponse.builder()
                    .id(UUID.randomUUID())
                    .name("Kitchen Counter")
                    .householdId(householdId)
                    .build();

            List<LocationResponse> locations = List.of(location1, location2);
            when(locationService.searchLocations(householdId, "kitchen"))
                    .thenReturn(locations);

            mockMvc.perform(get("/api/households/{householdId}/locations/search", householdId)
                            .param("query", "kitchen")
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].name").value("Kitchen Cabinet"))
                    .andExpect(jsonPath("$[1].name").value("Kitchen Counter"))
                    .andExpect(header().string("Cache-Control", "max-age=60"));

            verify(securityService).canAccessHousehold(householdId);
            verify(locationService).searchLocations(householdId, "kitchen");
        }

        @Test
        @DisplayName("should return empty list when no locations match query")
        void shouldReturnEmptyListWhenNoMatch() throws Exception {
            when(locationService.searchLocations(householdId, "nonexistent"))
                    .thenReturn(Collections.emptyList());

            mockMvc.perform(get("/api/households/{householdId}/locations/search", householdId)
                            .param("query", "nonexistent")
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(0));

            verify(locationService).searchLocations(householdId, "nonexistent");
        }

        @Test
        @DisplayName("should return 400 when query parameter is missing")
        void shouldReturn400WhenQueryParameterMissing() throws Exception {
            mockMvc.perform(get("/api/households/{householdId}/locations/search", householdId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(locationService);
        }

        @Test
        @DisplayName("should return 400 when query parameter is empty")
        void shouldReturn400WhenQueryParameterEmpty() throws Exception {
            mockMvc.perform(get("/api/households/{householdId}/locations/search", householdId)
                            .param("query", "")
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(locationService);
        }

        @Test
        @DisplayName("should return 401 for unauthenticated request")
        void shouldReturn401ForUnauthenticatedRequest() throws Exception {
            mockMvc.perform(get("/api/households/{householdId}/locations/search", householdId)
                            .param("query", "kitchen"))
                    .andExpect(status().isUnauthorized());

            verifyNoInteractions(locationService);
        }

        @Test
        @DisplayName("should return 403 when user lacks household access")
        void shouldReturn403WhenUserLacksAccess() throws Exception {
            mockMvc.perform(get("/api/households/{householdId}/locations/search", otherHouseholdId)
                            .param("query", "kitchen")
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isForbidden());

            verify(securityService).canAccessHousehold(otherHouseholdId);
            verifyNoInteractions(locationService);
        }

        @Test
        @DisplayName("should return 400 when validation exception occurs")
        void shouldReturn400WhenValidationExceptionOccurs() throws Exception {
            when(locationService.searchLocations(eq(householdId), anyString()))
                    .thenThrow(new ValidationException("Search term cannot be empty"));

            mockMvc.perform(get("/api/households/{householdId}/locations/search", householdId)
                            .param("query", " ")
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error_code").value("VALIDATION_ERROR"));
        }
    }

    @Nested
    @DisplayName("GET /api/households/{householdId}/locations/check-availability")
    class CheckLocationNameAvailability {

        @Test
        @DisplayName("should return true when location name is available")
        void shouldReturnTrueWhenAvailable() throws Exception {
            when(locationService.isLocationNameAvailable(householdId, "New Location"))
                    .thenReturn(true);

            mockMvc.perform(get("/api/households/{householdId}/locations/check-availability", householdId)
                            .param("name", "New Location")
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$").value(true))
                    .andExpect(header().string("Cache-Control", "max-age=30"));

            verify(securityService).canAccessHousehold(householdId);
            verify(locationService).isLocationNameAvailable(householdId, "New Location");
        }

        @Test
        @DisplayName("should return false when location name is not available")
        void shouldReturnFalseWhenNotAvailable() throws Exception {
            when(locationService.isLocationNameAvailable(householdId, "Kitchen"))
                    .thenReturn(false);

            mockMvc.perform(get("/api/households/{householdId}/locations/check-availability", householdId)
                            .param("name", "Kitchen")
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$").value(false));

            verify(locationService).isLocationNameAvailable(householdId, "Kitchen");
        }

        @Test
        @DisplayName("should return 400 when name parameter is missing")
        void shouldReturn400WhenNameParameterMissing() throws Exception {
            mockMvc.perform(get("/api/households/{householdId}/locations/check-availability", householdId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(locationService);
        }

        @Test
        @DisplayName("should return 400 when name parameter is empty")
        void shouldReturn400WhenNameParameterEmpty() throws Exception {
            mockMvc.perform(get("/api/households/{householdId}/locations/check-availability", householdId)
                            .param("name", "")
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(locationService);
        }

        @Test
        @DisplayName("should return 401 for unauthenticated request")
        void shouldReturn401ForUnauthenticatedRequest() throws Exception {
            mockMvc.perform(get("/api/households/{householdId}/locations/check-availability", householdId)
                            .param("name", "New Location"))
                    .andExpect(status().isUnauthorized());

            verifyNoInteractions(locationService);
        }

        @Test
        @DisplayName("should return 403 when user lacks household access")
        void shouldReturn403WhenUserLacksAccess() throws Exception {
            mockMvc.perform(get("/api/households/{householdId}/locations/check-availability", otherHouseholdId)
                            .param("name", "Kitchen")
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isForbidden());

            verify(securityService).canAccessHousehold(otherHouseholdId);
            verifyNoInteractions(locationService);
        }
    }

    // ==================== Update Operations Tests ====================

    @Nested
    @DisplayName("PUT /api/households/{householdId}/locations/{locationId}")
    class UpdateLocation {

        @Test
        @DisplayName("should update location when user has admin permissions")
        void shouldUpdateLocationWhenUserHasAdminPermissions() throws Exception {
            LocationUpdateRequest request = LocationUpdateRequest.builder()
                    .name("Updated Kitchen")
                    .description("Updated description")
                    .build();

            LocationResponse updatedResponse = LocationResponse.builder()
                    .id(locationId)
                    .name("Updated Kitchen")
                    .description("Updated description")
                    .householdId(householdId)
                    .updatedAt(LocalDateTime.now())
                    .build();

            when(locationService.updateLocation(eq(locationId), any(LocationUpdateRequest.class)))
                    .thenReturn(updatedResponse);

            mockMvc.perform(put("/api/households/{householdId}/locations/{locationId}",
                            householdId, locationId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.id").value(locationId.toString()))
                    .andExpect(jsonPath("$.name").value("Updated Kitchen"))
                    .andExpect(jsonPath("$.description").value("Updated description"));

            verify(securityService).canModifyHousehold(householdId);
            verify(locationService).updateLocation(eq(locationId), any(LocationUpdateRequest.class));
        }

        @Test
        @DisplayName("should return 401 for unauthenticated request")
        void shouldReturn401ForUnauthenticatedRequest() throws Exception {
            LocationUpdateRequest request = LocationUpdateRequest.builder()
                    .name("Updated Kitchen")
                    .build();

            mockMvc.perform(put("/api/households/{householdId}/locations/{locationId}",
                            householdId, locationId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());

            verifyNoInteractions(locationService);
        }

        @Test
        @DisplayName("should return 403 when user lacks admin permissions")
        void shouldReturn403WhenUserLacksAdminPermissions() throws Exception {
            LocationUpdateRequest request = LocationUpdateRequest.builder()
                    .name("Updated Kitchen")
                    .build();

            mockMvc.perform(put("/api/households/{householdId}/locations/{locationId}",
                            otherHouseholdId, locationId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());

            verify(securityService).canModifyHousehold(otherHouseholdId);
            verifyNoInteractions(locationService);
        }

        @Test
        @DisplayName("should return 400 when request body is invalid")
        void shouldReturn400WhenRequestBodyIsInvalid() throws Exception {
            LocationUpdateRequest invalidRequest = LocationUpdateRequest.builder()
                    .name("") // Invalid: empty name
                    .build();

            mockMvc.perform(put("/api/households/{householdId}/locations/{locationId}",
                            householdId, locationId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(invalidRequest)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(locationService);
        }

        @Test
        @DisplayName("should return 404 when location not found")
        void shouldReturn404WhenLocationNotFound() throws Exception {
            LocationUpdateRequest request = LocationUpdateRequest.builder()
                    .name("Updated Kitchen")
                    .build();

            when(locationService.updateLocation(eq(locationId), any(LocationUpdateRequest.class)))
                    .thenThrow(new NotFoundException("Location not found with ID: " + locationId));

            mockMvc.perform(put("/api/households/{householdId}/locations/{locationId}",
                            householdId, locationId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error_code").value("RESOURCE_NOT_FOUND"));

            verify(locationService).updateLocation(eq(locationId), any(LocationUpdateRequest.class));
        }

        @Test
        @DisplayName("should return 409 when updated name conflicts with existing location")
        void shouldReturn409WhenNameConflicts() throws Exception {
            LocationUpdateRequest request = LocationUpdateRequest.builder()
                    .name("Existing Name")
                    .build();

            when(locationService.updateLocation(eq(locationId), any(LocationUpdateRequest.class)))
                    .thenThrow(new ConflictException("Location with name 'Existing Name' already exists in this household"));

            mockMvc.perform(put("/api/households/{householdId}/locations/{locationId}",
                            householdId, locationId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error_code").value("RESOURCE_CONFLICT"));

            verify(locationService).updateLocation(eq(locationId), any(LocationUpdateRequest.class));
        }
    }

    @Nested
    @DisplayName("PATCH /api/households/{householdId}/locations/{locationId}")
    class PatchLocation {

        @Test
        @DisplayName("should patch location when user has admin permissions")
        void shouldPatchLocationWhenUserHasAdminPermissions() throws Exception {
            Map<String, Object> patchRequest = Map.of(
                    "name", "Patched Kitchen"
            );

            LocationResponse patchedResponse = LocationResponse.builder()
                    .id(locationId)
                    .name("Patched Kitchen")
                    .description("Main kitchen area") // Unchanged
                    .householdId(householdId)
                    .updatedAt(LocalDateTime.now())
                    .build();

            when(locationService.patchLocation(eq(locationId), anyMap()))
                    .thenReturn(patchedResponse);

            mockMvc.perform(patch("/api/households/{householdId}/locations/{locationId}",
                            householdId, locationId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(patchRequest)))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.id").value(locationId.toString()))
                    .andExpect(jsonPath("$.name").value("Patched Kitchen"))
                    .andExpect(jsonPath("$.description").value("Main kitchen area"));

            verify(securityService).canModifyHousehold(householdId);
            verify(locationService).patchLocation(eq(locationId), anyMap());
        }

        @Test
        @DisplayName("should patch only specified fields")
        void shouldPatchOnlySpecifiedFields() throws Exception {
            Map<String, Object> patchRequest = Map.of(
                    "description", "New description only"
            );

            LocationResponse patchedResponse = LocationResponse.builder()
                    .id(locationId)
                    .name("Kitchen") // Unchanged
                    .description("New description only")
                    .householdId(householdId)
                    .updatedAt(LocalDateTime.now())
                    .build();

            when(locationService.patchLocation(eq(locationId), anyMap()))
                    .thenReturn(patchedResponse);

            mockMvc.perform(patch("/api/households/{householdId}/locations/{locationId}",
                            householdId, locationId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(patchRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("Kitchen"))
                    .andExpect(jsonPath("$.description").value("New description only"));

            verify(locationService).patchLocation(eq(locationId), anyMap());
        }

        @Test
        @DisplayName("should return 401 for unauthenticated request")
        void shouldReturn401ForUnauthenticatedRequest() throws Exception {
            Map<String, Object> patchRequest = Map.of("name", "Patched Kitchen");

            mockMvc.perform(patch("/api/households/{householdId}/locations/{locationId}",
                            householdId, locationId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(patchRequest)))
                    .andExpect(status().isUnauthorized());

            verifyNoInteractions(locationService);
        }

        @Test
        @DisplayName("should return 403 when user lacks admin permissions")
        void shouldReturn403WhenUserLacksAdminPermissions() throws Exception {
            Map<String, Object> patchRequest = Map.of("name", "Patched Kitchen");

            mockMvc.perform(patch("/api/households/{householdId}/locations/{locationId}",
                            otherHouseholdId, locationId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(patchRequest)))
                    .andExpect(status().isForbidden());

            verify(securityService).canModifyHousehold(otherHouseholdId);
            verifyNoInteractions(locationService);
        }

        @Test
        @DisplayName("should return 404 when location not found")
        void shouldReturn404WhenLocationNotFound() throws Exception {
            Map<String, Object> patchRequest = Map.of("name", "Patched Kitchen");

            when(locationService.patchLocation(eq(locationId), anyMap()))
                    .thenThrow(new NotFoundException("Location not found with ID: " + locationId));

            mockMvc.perform(patch("/api/households/{householdId}/locations/{locationId}",
                            householdId, locationId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(patchRequest)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error_code").value("RESOURCE_NOT_FOUND"));

            verify(locationService).patchLocation(eq(locationId), anyMap());
        }

        @Test
        @DisplayName("should return 400 when patching unsupported field")
        void shouldReturn400WhenPatchingUnsupportedField() throws Exception {
            Map<String, Object> patchRequest = Map.of("unsupportedField", "value");

            when(locationService.patchLocation(eq(locationId), anyMap()))
                    .thenThrow(new ValidationException("Unsupported field for patching: unsupportedField"));

            mockMvc.perform(patch("/api/households/{householdId}/locations/{locationId}",
                            householdId, locationId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(patchRequest)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error_code").value("VALIDATION_ERROR"));

            verify(locationService).patchLocation(eq(locationId), anyMap());
        }
    }

    // ==================== Delete Operations Tests ====================

    @Nested
    @DisplayName("DELETE /api/households/{householdId}/locations/{locationId}")
    class DeleteLocation {

        @Test
        @DisplayName("should delete location when user has admin permissions")
        void shouldDeleteLocationWhenUserHasAdminPermissions() throws Exception {
            doNothing().when(locationService).deleteLocation(locationId);

            mockMvc.perform(delete("/api/households/{householdId}/locations/{locationId}",
                            householdId, locationId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isNoContent());

            verify(securityService).canModifyHousehold(householdId);
            verify(locationService).deleteLocation(locationId);
        }

        @Test
        @DisplayName("should return 401 for unauthenticated request")
        void shouldReturn401ForUnauthenticatedRequest() throws Exception {
            mockMvc.perform(delete("/api/households/{householdId}/locations/{locationId}",
                            householdId, locationId))
                    .andExpect(status().isUnauthorized());

            verifyNoInteractions(locationService);
        }

        @Test
        @DisplayName("should return 403 when user lacks admin permissions")
        void shouldReturn403WhenUserLacksAdminPermissions() throws Exception {
            mockMvc.perform(delete("/api/households/{householdId}/locations/{locationId}",
                            otherHouseholdId, locationId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isForbidden());

            verify(securityService).canModifyHousehold(otherHouseholdId);
            verifyNoInteractions(locationService);
        }

        @Test
        @DisplayName("should return 404 when location not found")
        void shouldReturn404WhenLocationNotFound() throws Exception {
            doThrow(new NotFoundException("Location not found with ID: " + locationId))
                    .when(locationService).deleteLocation(locationId);

            mockMvc.perform(delete("/api/households/{householdId}/locations/{locationId}",
                            householdId, locationId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error_code").value("RESOURCE_NOT_FOUND"));

            verify(locationService).deleteLocation(locationId);
        }

        @Test
        @DisplayName("should return 409 when location has associated pantry items")
        void shouldReturn409WhenLocationHasAssociatedItems() throws Exception {
            doThrow(new DataIntegrityException(
                    "Failed to delete location. Location may have associated pantry items that must be removed first."))
                    .when(locationService).deleteLocation(locationId);

            mockMvc.perform(delete("/api/households/{householdId}/locations/{locationId}",
                            householdId, locationId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error_code").value("DATA_INTEGRITY_VIOLATION"));

            verify(locationService).deleteLocation(locationId);
        }

        @Test
        @DisplayName("should return 400 when invalid UUID format provided")
        void shouldReturn400WhenInvalidUuidProvided() throws Exception {
            mockMvc.perform(delete("/api/households/{householdId}/locations/invalid-uuid",
                            householdId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(locationService);
        }
    }
}