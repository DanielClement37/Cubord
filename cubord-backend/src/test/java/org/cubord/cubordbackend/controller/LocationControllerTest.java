
package org.cubord.cubordbackend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.cubord.cubordbackend.dto.LocationRequest;
import org.cubord.cubordbackend.dto.LocationResponse;
import org.cubord.cubordbackend.dto.LocationUpdateRequest;
import org.cubord.cubordbackend.service.LocationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(LocationController.class)
class LocationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LocationService locationService;

    @Autowired
    private ObjectMapper objectMapper;

    private final UUID householdId = UUID.randomUUID();
    private final UUID locationId = UUID.randomUUID();

    @Test
    void createLocation_ShouldReturnCreatedLocation() throws Exception {
        // Arrange
        LocationRequest request = new LocationRequest();
        request.setName("Kitchen");
        request.setDescription("Main kitchen area");

        LocationResponse expectedResponse = new LocationResponse();
        expectedResponse.setId(locationId);
        expectedResponse.setName("Kitchen");
        expectedResponse.setDescription("Main kitchen area");
        expectedResponse.setHouseholdId(householdId);

        when(locationService.createLocation(any(LocationRequest.class), any(JwtAuthenticationToken.class)))
                .thenReturn(expectedResponse);

        // Act & Assert
        mockMvc.perform(post("/api/households/{householdId}/locations", householdId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(locationId.toString()))
                .andExpect(jsonPath("$.name").value("Kitchen"))
                .andExpect(jsonPath("$.description").value("Main kitchen area"))
                .andExpect(jsonPath("$.householdId").value(householdId.toString()));
    }

    @Test
    void getLocationById_ShouldReturnLocation() throws Exception {
        // Arrange
        LocationResponse expectedResponse = new LocationResponse();
        expectedResponse.setId(locationId);
        expectedResponse.setName("Kitchen");
        expectedResponse.setDescription("Main kitchen area");
        expectedResponse.setHouseholdId(householdId);

        when(locationService.getLocationById(eq(locationId), any(JwtAuthenticationToken.class)))
                .thenReturn(expectedResponse);

        // Act & Assert
        mockMvc.perform(get("/api/households/{householdId}/locations/{locationId}", householdId, locationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(locationId.toString()))
                .andExpect(jsonPath("$.name").value("Kitchen"))
                .andExpect(jsonPath("$.description").value("Main kitchen area"))
                .andExpect(jsonPath("$.householdId").value(householdId.toString()));
    }

    @Test
    void getLocationsByHousehold_ShouldReturnLocationsList() throws Exception {
        // Arrange
        LocationResponse location1 = new LocationResponse();
        location1.setId(UUID.randomUUID());
        location1.setName("Kitchen");
        location1.setHouseholdId(householdId);

        LocationResponse location2 = new LocationResponse();
        location2.setId(UUID.randomUUID());
        location2.setName("Pantry");
        location2.setHouseholdId(householdId);

        List<LocationResponse> expectedResponse = List.of(location1, location2);

        when(locationService.getLocationsByHousehold(eq(householdId), any(JwtAuthenticationToken.class)))
                .thenReturn(expectedResponse);

        // Act & Assert
        mockMvc.perform(get("/api/households/{householdId}/locations", householdId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("Kitchen"))
                .andExpect(jsonPath("$[1].name").value("Pantry"));
    }

    @Test
    void updateLocation_ShouldReturnUpdatedLocation() throws Exception {
        // Arrange
        LocationUpdateRequest request = new LocationUpdateRequest();
        request.setName("Updated Kitchen");
        request.setDescription("Updated kitchen area");

        LocationResponse expectedResponse = new LocationResponse();
        expectedResponse.setId(locationId);
        expectedResponse.setName("Updated Kitchen");
        expectedResponse.setDescription("Updated kitchen area");
        expectedResponse.setHouseholdId(householdId);

        when(locationService.updateLocation(eq(locationId), any(LocationUpdateRequest.class), any(JwtAuthenticationToken.class)))
                .thenReturn(expectedResponse);

        // Act & Assert
        mockMvc.perform(put("/api/households/{householdId}/locations/{locationId}", householdId, locationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(locationId.toString()))
                .andExpect(jsonPath("$.name").value("Updated Kitchen"))
                .andExpect(jsonPath("$.description").value("Updated kitchen area"))
                .andExpect(jsonPath("$.householdId").value(householdId.toString()));
    }

    @Test
    void patchLocation_ShouldReturnPatchedLocation() throws Exception {
        // Arrange
        Map<String, Object> patchRequest = Map.of(
                "name", "Patched Kitchen",
                "description", "Patched description"
        );

        LocationResponse expectedResponse = new LocationResponse();
        expectedResponse.setId(locationId);
        expectedResponse.setName("Patched Kitchen");
        expectedResponse.setDescription("Patched description");
        expectedResponse.setHouseholdId(householdId);

        when(locationService.patchLocation(eq(locationId), any(Map.class), any(JwtAuthenticationToken.class)))
                .thenReturn(expectedResponse);

        // Act & Assert
        mockMvc.perform(patch("/api/households/{householdId}/locations/{locationId}", householdId, locationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(patchRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(locationId.toString()))
                .andExpect(jsonPath("$.name").value("Patched Kitchen"))
                .andExpect(jsonPath("$.description").value("Patched description"))
                .andExpect(jsonPath("$.householdId").value(householdId.toString()));
    }

    @Test
    void deleteLocation_ShouldReturnNoContent() throws Exception {
        // Arrange
        doNothing().when(locationService).deleteLocation(eq(locationId), any(JwtAuthenticationToken.class));

        // Act & Assert
        mockMvc.perform(delete("/api/households/{householdId}/locations/{locationId}", householdId, locationId))
                .andExpect(status().isNoContent());
    }

    @Test
    void searchLocations_ShouldReturnMatchingLocations() throws Exception {
        // Arrange
        LocationResponse location1 = new LocationResponse();
        location1.setId(UUID.randomUUID());
        location1.setName("Kitchen Cabinet");
        location1.setHouseholdId(householdId);

        LocationResponse location2 = new LocationResponse();
        location2.setId(UUID.randomUUID());
        location2.setName("Kitchen Counter");
        location2.setHouseholdId(householdId);

        List<LocationResponse> expectedResponse = List.of(location1, location2);

        when(locationService.searchLocations(eq(householdId), eq("kitchen"), any(JwtAuthenticationToken.class)))
                .thenReturn(expectedResponse);

        // Act & Assert
        mockMvc.perform(get("/api/households/{householdId}/locations/search", householdId)
                        .param("query", "kitchen"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("Kitchen Cabinet"))
                .andExpect(jsonPath("$[1].name").value("Kitchen Counter"));
    }

    @Test
    void isLocationNameAvailable_ShouldReturnTrueWhenAvailable() throws Exception {
        // Arrange
        when(locationService.isLocationNameAvailable(eq(householdId), eq("New Location"), any(JwtAuthenticationToken.class)))
                .thenReturn(true);

        // Act & Assert
        mockMvc.perform(get("/api/households/{householdId}/locations/check-availability", householdId)
                        .param("name", "New Location"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value(true));
    }

    @Test
    void isLocationNameAvailable_ShouldReturnFalseWhenNotAvailable() throws Exception {
        // Arrange
        when(locationService.isLocationNameAvailable(eq(householdId), eq("Kitchen"), any(JwtAuthenticationToken.class)))
                .thenReturn(false);

        // Act & Assert
        mockMvc.perform(get("/api/households/{householdId}/locations/check-availability", householdId)
                        .param("name", "Kitchen"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value(false));
    }

    @Test
    void createLocation_WithInvalidRequest_ShouldReturnBadRequest() throws Exception {
        // Arrange
        LocationRequest invalidRequest = new LocationRequest();
        // Empty name should trigger validation error

        // Act & Assert
        mockMvc.perform(post("/api/households/{householdId}/locations", householdId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateLocation_WithInvalidRequest_ShouldReturnBadRequest() throws Exception {
        // Arrange
        LocationUpdateRequest invalidRequest = new LocationUpdateRequest();
        // Empty name should trigger validation error

        // Act & Assert
        mockMvc.perform(put("/api/households/{householdId}/locations/{locationId}", householdId, locationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());
    }
}
