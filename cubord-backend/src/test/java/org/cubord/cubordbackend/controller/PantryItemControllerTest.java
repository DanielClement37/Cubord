
package org.cubord.cubordbackend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.cubord.cubordbackend.config.TestSecurityConfig;
import org.cubord.cubordbackend.dto.location.LocationResponse;
import org.cubord.cubordbackend.dto.pantryItem.CreatePantryItemRequest;
import org.cubord.cubordbackend.dto.pantryItem.PantryItemResponse;
import org.cubord.cubordbackend.dto.pantryItem.UpdatePantryItemRequest;
import org.cubord.cubordbackend.dto.product.ProductResponse;
import org.cubord.cubordbackend.exception.*;
import org.cubord.cubordbackend.security.SecurityService;
import org.cubord.cubordbackend.service.PantryItemService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for {@link PantryItemController}.
 *
 * <p>Tests verify:</p>
 * <ul>
 *   <li>Endpoint functionality and response formats</li>
 *   <li>Authorization via {@code @PreAuthorize} with mocked SecurityService</li>
 *   <li>Proper exception handling and HTTP status codes</li>
 *   <li>Input validation</li>
 * </ul>
 */
@WebMvcTest(PantryItemController.class)
@Import(TestSecurityConfig.class)
@ActiveProfiles("test")
class PantryItemControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private PantryItemService pantryItemService;

    @MockitoBean(name = "security")
    private SecurityService securityService;

    @MockitoBean
    private org.cubord.cubordbackend.security.HouseholdPermissionEvaluator householdPermissionEvaluator;

    private UUID householdId;
    private UUID locationId;
    private UUID productId;
    private UUID pantryItemId;
    private UUID userId;

    private Jwt jwt;
    private PantryItemResponse pantryItemResponse;
    private CreatePantryItemRequest createRequest;
    private UpdatePantryItemRequest updateRequest;

    @BeforeEach
    void setUp() {
        householdId = UUID.randomUUID();
        locationId = UUID.randomUUID();
        productId = UUID.randomUUID();
        pantryItemId = UUID.randomUUID();
        userId = UUID.randomUUID();

        // Create JWT token
        jwt = Jwt.withTokenValue("test-token")
                .header("alg", "HS256")
                .subject(userId.toString())
                .claim("email", "test@example.com")
                .claim("name", "Test User")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        // Create test data
        ProductResponse product = ProductResponse.builder()
                .id(productId)
                .name("Test Product")
                .brand("Test Brand")
                .category("Test Category")
                .build();

        LocationResponse location = LocationResponse.builder()
                .id(locationId)
                .name("Test Location")
                .householdId(householdId)
                .build();

        pantryItemResponse = PantryItemResponse.builder()
                .id(pantryItemId)
                .product(product)
                .location(location)
                .quantity(5)
                .expirationDate(LocalDate.now().plusDays(7))
                .notes("Test notes")
                .createdAt(LocalDateTime.now().minusDays(1))
                .updatedAt(LocalDateTime.now())
                .build();

        createRequest = CreatePantryItemRequest.builder()
                .productId(productId)
                .locationId(locationId)
                .quantity(5)
                .expirationDate(LocalDate.now().plusDays(7))
                .notes("Test notes")
                .build();

        updateRequest = UpdatePantryItemRequest.builder()
                .locationId(locationId)
                .quantity(10)
                .expirationDate(LocalDate.now().plusDays(14))
                .notes("Updated notes")
                .build();

        // Default security service mock behavior
        when(securityService.canAccessPantryItem(pantryItemId)).thenReturn(true);
        when(securityService.canAccessLocationForPantryItem(locationId)).thenReturn(true);
        when(securityService.canAccessHousehold(householdId)).thenReturn(true);
    }

    @Nested
    @DisplayName("POST /api/pantry-items")
    class CreatePantryItem {

        @Test
        @DisplayName("should create pantry item successfully")
        void shouldCreatePantryItemSuccessfully() throws Exception {
            when(pantryItemService.createPantryItem(any(CreatePantryItemRequest.class)))
                    .thenReturn(pantryItemResponse);

            mockMvc.perform(post("/api/pantry-items")
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(createRequest)))
                    .andExpect(status().isCreated())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.id").value(pantryItemId.toString()))
                    .andExpect(jsonPath("$.product.id").value(productId.toString()))
                    .andExpect(jsonPath("$.location.id").value(locationId.toString()))
                    .andExpect(jsonPath("$.quantity").value(5))
                    .andExpect(jsonPath("$.notes").value("Test notes"));

            verify(pantryItemService).createPantryItem(any(CreatePantryItemRequest.class));
        }

        @Test
        @DisplayName("should return 400 for invalid request")
        void shouldReturn400ForInvalidRequest() throws Exception {
            CreatePantryItemRequest invalidRequest = CreatePantryItemRequest.builder()
                    .quantity(-1) // Invalid quantity
                    .build();

            mockMvc.perform(post("/api/pantry-items")
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(invalidRequest)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(pantryItemService);
        }

        @Test
        @DisplayName("should return 401 when not authenticated")
        void shouldReturn401WhenNotAuthenticated() throws Exception {
            mockMvc.perform(post("/api/pantry-items")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(createRequest)))
                    .andExpect(status().isUnauthorized());

            verifyNoInteractions(pantryItemService);
        }

        @Test
        @DisplayName("should return 403 when user lacks access to location")
        void shouldReturn403WhenUserLacksAccessToLocation() throws Exception {
            when(pantryItemService.createPantryItem(any(CreatePantryItemRequest.class)))
                    .thenThrow(new InsufficientPermissionException("You do not have access to this location"));

            mockMvc.perform(post("/api/pantry-items")
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(createRequest)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error_code").value("INSUFFICIENT_PERMISSION"));

            verify(pantryItemService).createPantryItem(any(CreatePantryItemRequest.class));
        }

        @Test
        @DisplayName("should return 404 when location not found")
        void shouldReturn404WhenLocationNotFound() throws Exception {
            when(pantryItemService.createPantryItem(any(CreatePantryItemRequest.class)))
                    .thenThrow(new NotFoundException("Location not found with ID: " + locationId));

            mockMvc.perform(post("/api/pantry-items")
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(createRequest)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error_code").value("RESOURCE_NOT_FOUND"));

            verify(pantryItemService).createPantryItem(any(CreatePantryItemRequest.class));
        }

        @Test
        @DisplayName("should return 404 when product not found")
        void shouldReturn404WhenProductNotFound() throws Exception {
            when(pantryItemService.createPantryItem(any(CreatePantryItemRequest.class)))
                    .thenThrow(new NotFoundException("Product not found with ID: " + productId));

            mockMvc.perform(post("/api/pantry-items")
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(createRequest)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error_code").value("RESOURCE_NOT_FOUND"));

            verify(pantryItemService).createPantryItem(any(CreatePantryItemRequest.class));
        }

        @Test
        @DisplayName("should return 409 when data integrity fails")
        void shouldReturn409WhenDataIntegrityFails() throws Exception {
            when(pantryItemService.createPantryItem(any(CreatePantryItemRequest.class)))
                    .thenThrow(new DataIntegrityException("Failed to create pantry item: constraint violation"));

            mockMvc.perform(post("/api/pantry-items")
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(createRequest)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error_code").value("DATA_INTEGRITY_VIOLATION"));

            verify(pantryItemService).createPantryItem(any(CreatePantryItemRequest.class));
        }
    }

    @Nested
    @DisplayName("GET /api/pantry-items/{id}")
    class GetPantryItemById {

        @Test
        @DisplayName("should return pantry item by id")
        void shouldReturnPantryItemById() throws Exception {
            when(pantryItemService.getPantryItemById(eq(pantryItemId)))
                    .thenReturn(pantryItemResponse);

            mockMvc.perform(get("/api/pantry-items/{id}", pantryItemId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(header().string("Cache-Control", "max-age=60"))
                    .andExpect(jsonPath("$.id").value(pantryItemId.toString()))
                    .andExpect(jsonPath("$.product.name").value("Test Product"))
                    .andExpect(jsonPath("$.location.name").value("Test Location"));

            verify(securityService).canAccessPantryItem(pantryItemId);
            verify(pantryItemService).getPantryItemById(eq(pantryItemId));
        }

        @Test
        @DisplayName("should return 403 when user lacks access to pantry item")
        void shouldReturn403WhenUserLacksAccess() throws Exception {
            when(securityService.canAccessPantryItem(pantryItemId)).thenReturn(false);

            mockMvc.perform(get("/api/pantry-items/{id}", pantryItemId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isForbidden());

            verify(securityService).canAccessPantryItem(pantryItemId);
            verifyNoInteractions(pantryItemService);
        }

        @Test
        @DisplayName("should return 404 when pantry item not found")
        void shouldReturn404WhenPantryItemNotFound() throws Exception {
            when(pantryItemService.getPantryItemById(eq(pantryItemId)))
                    .thenThrow(new NotFoundException("Pantry item not found with ID: " + pantryItemId));

            mockMvc.perform(get("/api/pantry-items/{id}", pantryItemId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error_code").value("RESOURCE_NOT_FOUND"));

            verify(pantryItemService).getPantryItemById(eq(pantryItemId));
        }

        @Test
        @DisplayName("should return 400 for invalid UUID format")
        void shouldReturn400ForInvalidUuidFormat() throws Exception {
            mockMvc.perform(get("/api/pantry-items/{id}", "invalid-uuid")
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(pantryItemService);
        }

        @Test
        @DisplayName("should return 401 when not authenticated")
        void shouldReturn401WhenNotAuthenticated() throws Exception {
            mockMvc.perform(get("/api/pantry-items/{id}", pantryItemId))
                    .andExpect(status().isUnauthorized());

            verifyNoInteractions(pantryItemService);
        }
    }

    @Nested
    @DisplayName("PUT /api/pantry-items/{id}")
    class UpdatePantryItem {

        @Test
        @DisplayName("should update pantry item successfully")
        void shouldUpdatePantryItemSuccessfully() throws Exception {
            PantryItemResponse updatedResponse = PantryItemResponse.builder()
                    .id(pantryItemId)
                    .product(pantryItemResponse.getProduct())
                    .location(pantryItemResponse.getLocation())
                    .quantity(10)
                    .expirationDate(LocalDate.now().plusDays(14))
                    .notes("Updated notes")
                    .createdAt(pantryItemResponse.getCreatedAt())
                    .updatedAt(LocalDateTime.now())
                    .build();

            when(pantryItemService.updatePantryItem(eq(pantryItemId), any(UpdatePantryItemRequest.class)))
                    .thenReturn(updatedResponse);

            mockMvc.perform(put("/api/pantry-items/{id}", pantryItemId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(updateRequest)))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.id").value(pantryItemId.toString()))
                    .andExpect(jsonPath("$.quantity").value(10))
                    .andExpect(jsonPath("$.notes").value("Updated notes"));

            verify(securityService).canAccessPantryItem(pantryItemId);
            verify(pantryItemService).updatePantryItem(eq(pantryItemId), any(UpdatePantryItemRequest.class));
        }

        @Test
        @DisplayName("should return 403 when user lacks access")
        void shouldReturn403WhenUserLacksAccess() throws Exception {
            when(securityService.canAccessPantryItem(pantryItemId)).thenReturn(false);

            mockMvc.perform(put("/api/pantry-items/{id}", pantryItemId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(updateRequest)))
                    .andExpect(status().isForbidden());

            verify(securityService).canAccessPantryItem(pantryItemId);
            verifyNoInteractions(pantryItemService);
        }

        @Test
        @DisplayName("should return 400 for invalid update request")
        void shouldReturn400ForInvalidUpdateRequest() throws Exception {
            UpdatePantryItemRequest invalidRequest = UpdatePantryItemRequest.builder()
                    .quantity(-1) // Invalid quantity
                    .build();

            mockMvc.perform(put("/api/pantry-items/{id}", pantryItemId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(invalidRequest)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(pantryItemService);
        }

        @Test
        @DisplayName("should return 404 when pantry item not found")
        void shouldReturn404WhenPantryItemNotFound() throws Exception {
            when(pantryItemService.updatePantryItem(eq(pantryItemId), any(UpdatePantryItemRequest.class)))
                    .thenThrow(new NotFoundException("Pantry item not found with ID: " + pantryItemId));

            mockMvc.perform(put("/api/pantry-items/{id}", pantryItemId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(updateRequest)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error_code").value("RESOURCE_NOT_FOUND"));

            verify(pantryItemService).updatePantryItem(eq(pantryItemId), any(UpdatePantryItemRequest.class));
        }

        @Test
        @DisplayName("should return 403 when moving to inaccessible location")
        void shouldReturn403WhenMovingToInaccessibleLocation() throws Exception {
            when(pantryItemService.updatePantryItem(eq(pantryItemId), any(UpdatePantryItemRequest.class)))
                    .thenThrow(new InsufficientPermissionException("You do not have access to the target location"));

            mockMvc.perform(put("/api/pantry-items/{id}", pantryItemId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(updateRequest)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error_code").value("INSUFFICIENT_PERMISSION"));

            verify(pantryItemService).updatePantryItem(eq(pantryItemId), any(UpdatePantryItemRequest.class));
        }
    }

    @Nested
    @DisplayName("PATCH /api/pantry-items/{id}")
    class PatchPantryItem {

        @Test
        @DisplayName("should partially update pantry item")
        void shouldPartiallyUpdatePantryItem() throws Exception {
            Map<String, Object> patchRequest = Map.of(
                    "quantity", 15,
                    "notes", "Patched notes"
            );

            PantryItemResponse patchedResponse = PantryItemResponse.builder()
                    .id(pantryItemId)
                    .product(pantryItemResponse.getProduct())
                    .location(pantryItemResponse.getLocation())
                    .quantity(15)
                    .expirationDate(pantryItemResponse.getExpirationDate())
                    .notes("Patched notes")
                    .createdAt(pantryItemResponse.getCreatedAt())
                    .updatedAt(LocalDateTime.now())
                    .build();

            when(pantryItemService.patchPantryItem(eq(pantryItemId), any(Map.class)))
                    .thenReturn(patchedResponse);

            mockMvc.perform(patch("/api/pantry-items/{id}", pantryItemId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(patchRequest)))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.id").value(pantryItemId.toString()))
                    .andExpect(jsonPath("$.quantity").value(15))
                    .andExpect(jsonPath("$.notes").value("Patched notes"));

            verify(securityService).canAccessPantryItem(pantryItemId);
            verify(pantryItemService).patchPantryItem(eq(pantryItemId), any(Map.class));
        }

        @Test
        @DisplayName("should return 403 when user lacks access")
        void shouldReturn403WhenUserLacksAccess() throws Exception {
            when(securityService.canAccessPantryItem(pantryItemId)).thenReturn(false);

            Map<String, Object> patchRequest = Map.of("quantity", 15);

            mockMvc.perform(patch("/api/pantry-items/{id}", pantryItemId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(patchRequest)))
                    .andExpect(status().isForbidden());

            verify(securityService).canAccessPantryItem(pantryItemId);
            verifyNoInteractions(pantryItemService);
        }

        @Test
        @DisplayName("should return 400 when patch data is invalid")
        void shouldReturn400WhenPatchDataIsInvalid() throws Exception {
            Map<String, Object> invalidPatch = Map.of("quantity", -5);

            when(pantryItemService.patchPantryItem(eq(pantryItemId), any(Map.class)))
                    .thenThrow(new ValidationException("Quantity must be non-negative"));

            mockMvc.perform(patch("/api/pantry-items/{id}", pantryItemId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(invalidPatch)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error_code").value("VALIDATION_ERROR"));

            verify(pantryItemService).patchPantryItem(eq(pantryItemId), any(Map.class));
        }

        @Test
        @DisplayName("should return 404 when pantry item not found")
        void shouldReturn404WhenPantryItemNotFound() throws Exception {
            Map<String, Object> patchRequest = Map.of("quantity", 15);

            when(pantryItemService.patchPantryItem(eq(pantryItemId), any(Map.class)))
                    .thenThrow(new NotFoundException("Pantry item not found with ID: " + pantryItemId));

            mockMvc.perform(patch("/api/pantry-items/{id}", pantryItemId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(patchRequest)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error_code").value("RESOURCE_NOT_FOUND"));

            verify(pantryItemService).patchPantryItem(eq(pantryItemId), any(Map.class));
        }
    }

    @Nested
    @DisplayName("DELETE /api/pantry-items/{id}")
    class DeletePantryItem {

        @Test
        @DisplayName("should delete pantry item successfully")
        void shouldDeletePantryItemSuccessfully() throws Exception {
            doNothing().when(pantryItemService).deletePantryItem(eq(pantryItemId));

            mockMvc.perform(delete("/api/pantry-items/{id}", pantryItemId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isNoContent());

            verify(securityService).canAccessPantryItem(pantryItemId);
            verify(pantryItemService).deletePantryItem(eq(pantryItemId));
        }

        @Test
        @DisplayName("should return 403 when user lacks access")
        void shouldReturn403WhenUserLacksAccess() throws Exception {
            when(securityService.canAccessPantryItem(pantryItemId)).thenReturn(false);

            mockMvc.perform(delete("/api/pantry-items/{id}", pantryItemId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isForbidden());

            verify(securityService).canAccessPantryItem(pantryItemId);
            verifyNoInteractions(pantryItemService);
        }

        @Test
        @DisplayName("should return 404 when deleting non-existent item")
        void shouldReturn404WhenDeletingNonExistentItem() throws Exception {
            doThrow(new NotFoundException("Pantry item not found with ID: " + pantryItemId))
                    .when(pantryItemService).deletePantryItem(eq(pantryItemId));

            mockMvc.perform(delete("/api/pantry-items/{id}", pantryItemId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error_code").value("RESOURCE_NOT_FOUND"));

            verify(pantryItemService).deletePantryItem(eq(pantryItemId));
        }

        @Test
        @DisplayName("should return 401 when not authenticated")
        void shouldReturn401WhenNotAuthenticated() throws Exception {
            mockMvc.perform(delete("/api/pantry-items/{id}", pantryItemId))
                    .andExpect(status().isUnauthorized());

            verifyNoInteractions(pantryItemService);
        }
    }

    @Nested
    @DisplayName("POST /api/pantry-items/batch")
    class CreateMultiplePantryItems {

        @Test
        @DisplayName("should create multiple pantry items")
        void shouldCreateMultiplePantryItems() throws Exception {
            List<CreatePantryItemRequest> requests = List.of(createRequest);
            List<PantryItemResponse> responses = List.of(pantryItemResponse);

            when(pantryItemService.createMultiplePantryItems(anyList()))
                    .thenReturn(responses);

            mockMvc.perform(post("/api/pantry-items/batch")
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(requests)))
                    .andExpect(status().isCreated())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].id").value(pantryItemId.toString()));

            verify(pantryItemService).createMultiplePantryItems(anyList());
        }

        @Test
        @DisplayName("should return 400 for empty batch request")
        void shouldReturn400ForEmptyBatchRequest() throws Exception {
            List<CreatePantryItemRequest> emptyRequests = Collections.emptyList();

            mockMvc.perform(post("/api/pantry-items/batch")
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(emptyRequests)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(pantryItemService);
        }

        @Test
        @DisplayName("should return 400 when request contains invalid items")
        void shouldReturn400WhenRequestContainsInvalidItems() throws Exception {
            CreatePantryItemRequest invalidRequest = CreatePantryItemRequest.builder()
                    .quantity(-1)
                    .build();
            List<CreatePantryItemRequest> requests = List.of(invalidRequest);

            mockMvc.perform(post("/api/pantry-items/batch")
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(requests)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(pantryItemService);
        }

        @Test
        @DisplayName("should return 401 when not authenticated")
        void shouldReturn401WhenNotAuthenticated() throws Exception {
            List<CreatePantryItemRequest> requests = List.of(createRequest);

            mockMvc.perform(post("/api/pantry-items/batch")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(requests)))
                    .andExpect(status().isUnauthorized());

            verifyNoInteractions(pantryItemService);
        }
    }

    @Nested
    @DisplayName("DELETE /api/pantry-items/batch")
    class DeleteMultiplePantryItems {

        @Test
        @DisplayName("should delete multiple pantry items")
        void shouldDeleteMultiplePantryItems() throws Exception {
            List<UUID> itemIds = List.of(pantryItemId);
            when(pantryItemService.deleteMultiplePantryItems(eq(itemIds)))
                    .thenReturn(1);

            mockMvc.perform(delete("/api/pantry-items/batch")
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(itemIds)))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.deletedCount").value(1));

            verify(pantryItemService).deleteMultiplePantryItems(eq(itemIds));
        }

        @Test
        @DisplayName("should return 400 for empty batch delete request")
        void shouldReturn400ForEmptyBatchDeleteRequest() throws Exception {
            List<UUID> emptyIds = Collections.emptyList();

            mockMvc.perform(delete("/api/pantry-items/batch")
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(emptyIds)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(pantryItemService);
        }

        @Test
        @DisplayName("should handle partial deletion when some items inaccessible")
        void shouldHandlePartialDeletion() throws Exception {
            UUID inaccessibleItemId = UUID.randomUUID();
            List<UUID> itemIds = List.of(pantryItemId, inaccessibleItemId);
            when(pantryItemService.deleteMultiplePantryItems(eq(itemIds)))
                    .thenReturn(1); // Only 1 deleted

            mockMvc.perform(delete("/api/pantry-items/batch")
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(itemIds)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.deletedCount").value(1));

            verify(pantryItemService).deleteMultiplePantryItems(eq(itemIds));
        }
    }

    @Nested
    @DisplayName("GET /api/locations/{locationId}/pantry-items")
    class GetPantryItemsByLocation {

        @Test
        @DisplayName("should return pantry items by location")
        void shouldReturnPantryItemsByLocation() throws Exception {
            List<PantryItemResponse> items = List.of(pantryItemResponse);
            when(pantryItemService.getPantryItemsByLocation(eq(locationId)))
                    .thenReturn(items);

            mockMvc.perform(get("/api/locations/{locationId}/pantry-items", locationId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(header().string("Cache-Control", "max-age=60"))
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].id").value(pantryItemId.toString()))
                    .andExpect(jsonPath("$[0].location.id").value(locationId.toString()));

            verify(securityService).canAccessLocationForPantryItem(locationId);
            verify(pantryItemService).getPantryItemsByLocation(eq(locationId));
        }

        @Test
        @DisplayName("should return 403 when user lacks access to location")
        void shouldReturn403WhenUserLacksAccessToLocation() throws Exception {
            when(securityService.canAccessLocationForPantryItem(locationId)).thenReturn(false);

            mockMvc.perform(get("/api/locations/{locationId}/pantry-items", locationId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isForbidden());

            verify(securityService).canAccessLocationForPantryItem(locationId);
            verifyNoInteractions(pantryItemService);
        }

        @Test
        @DisplayName("should return empty list when no items in location")
        void shouldReturnEmptyListWhenNoItemsInLocation() throws Exception {
            when(pantryItemService.getPantryItemsByLocation(eq(locationId)))
                    .thenReturn(Collections.emptyList());

            mockMvc.perform(get("/api/locations/{locationId}/pantry-items", locationId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(0));

            verify(pantryItemService).getPantryItemsByLocation(eq(locationId));
        }

        @Test
        @DisplayName("should return 404 when location not found")
        void shouldReturn404WhenLocationNotFound() throws Exception {
            when(pantryItemService.getPantryItemsByLocation(eq(locationId)))
                    .thenThrow(new NotFoundException("Location not found with ID: " + locationId));

            mockMvc.perform(get("/api/locations/{locationId}/pantry-items", locationId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error_code").value("RESOURCE_NOT_FOUND"));

            verify(pantryItemService).getPantryItemsByLocation(eq(locationId));
        }
    }

    @Nested
    @DisplayName("GET /api/households/{householdId}/pantry-items")
    class GetPantryItemsByHousehold {

        @Test
        @DisplayName("should return paginated pantry items by household")
        void shouldReturnPaginatedPantryItemsByHousehold() throws Exception {
            List<PantryItemResponse> items = List.of(pantryItemResponse);
            Page<PantryItemResponse> page = new PageImpl<>(items, PageRequest.of(0, 20), 1);

            when(pantryItemService.getPantryItemsByHousehold(eq(householdId), any(Pageable.class)))
                    .thenReturn(page);

            mockMvc.perform(get("/api/households/{householdId}/pantry-items", householdId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                            .param("page", "0")
                            .param("size", "20"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(header().string("Cache-Control", "max-age=30"))
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.content.length()").value(1))
                    .andExpect(jsonPath("$.content[0].id").value(pantryItemId.toString()))
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.totalPages").value(1));

            verify(securityService).canAccessHousehold(householdId);
            verify(pantryItemService).getPantryItemsByHousehold(eq(householdId), any(Pageable.class));
        }

        @Test
        @DisplayName("should use default pagination when no params provided")
        void shouldUseDefaultPaginationWhenNoParamsProvided() throws Exception {
            List<PantryItemResponse> items = List.of(pantryItemResponse);
            Page<PantryItemResponse> page = new PageImpl<>(items, PageRequest.of(0, 20), 1);

            when(pantryItemService.getPantryItemsByHousehold(eq(householdId), any(Pageable.class)))
                    .thenReturn(page);

            mockMvc.perform(get("/api/households/{householdId}/pantry-items", householdId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray());

            verify(pantryItemService).getPantryItemsByHousehold(eq(householdId), any(Pageable.class));
        }

        @Test
        @DisplayName("should return 403 when user lacks access to household")
        void shouldReturn403WhenUserLacksAccessToHousehold() throws Exception {
            when(securityService.canAccessHousehold(householdId)).thenReturn(false);

            mockMvc.perform(get("/api/households/{householdId}/pantry-items", householdId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isForbidden());

            verify(securityService).canAccessHousehold(householdId);
            verifyNoInteractions(pantryItemService);
        }
    }

    @Nested
    @DisplayName("GET /api/households/{householdId}/pantry-items/low-stock")
    class GetLowStockItems {

        @Test
        @DisplayName("should return low stock items")
        void shouldReturnLowStockItems() throws Exception {
            List<PantryItemResponse> lowStockItems = List.of(pantryItemResponse);
            when(pantryItemService.getLowStockItems(eq(householdId), eq(10)))
                    .thenReturn(lowStockItems);

            mockMvc.perform(get("/api/households/{householdId}/pantry-items/low-stock", householdId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                            .param("threshold", "10"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(header().string("Cache-Control", "max-age=60"))
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].id").value(pantryItemId.toString()));

            verify(securityService).canAccessHousehold(householdId);
            verify(pantryItemService).getLowStockItems(eq(householdId), eq(10));
        }

        @Test
        @DisplayName("should use default threshold when not provided")
        void shouldUseDefaultThresholdWhenNotProvided() throws Exception {
            List<PantryItemResponse> lowStockItems = List.of(pantryItemResponse);
            when(pantryItemService.getLowStockItems(eq(householdId), eq(5)))
                    .thenReturn(lowStockItems);

            mockMvc.perform(get("/api/households/{householdId}/pantry-items/low-stock", householdId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray());

            verify(pantryItemService).getLowStockItems(eq(householdId), eq(5));
        }

        @Test
        @DisplayName("should return 403 when user lacks access")
        void shouldReturn403WhenUserLacksAccess() throws Exception {
            when(securityService.canAccessHousehold(householdId)).thenReturn(false);

            mockMvc.perform(get("/api/households/{householdId}/pantry-items/low-stock", householdId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isForbidden());

            verify(securityService).canAccessHousehold(householdId);
            verifyNoInteractions(pantryItemService);
        }

        @Test
        @DisplayName("should return 400 when threshold is negative")
        void shouldReturn400WhenThresholdIsNegative() throws Exception {
            when(pantryItemService.getLowStockItems(eq(householdId), eq(-1)))
                    .thenThrow(new ValidationException("Threshold cannot be negative"));

            mockMvc.perform(get("/api/households/{householdId}/pantry-items/low-stock", householdId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                            .param("threshold", "-1"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error_code").value("VALIDATION_ERROR"));

            verify(pantryItemService).getLowStockItems(eq(householdId), eq(-1));
        }
    }

    @Nested
    @DisplayName("GET /api/households/{householdId}/pantry-items/expiring")
    class GetExpiringItems {

        @Test
        @DisplayName("should return expiring items with date range")
        void shouldReturnExpiringItemsWithDateRange() throws Exception {
            LocalDate startDate = LocalDate.now();
            LocalDate endDate = LocalDate.now().plusDays(7);
            List<PantryItemResponse> expiringItems = List.of(pantryItemResponse);

            when(pantryItemService.getExpiringItems(eq(householdId), eq(startDate), eq(endDate)))
                    .thenReturn(expiringItems);

            mockMvc.perform(get("/api/households/{householdId}/pantry-items/expiring", householdId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                            .param("startDate", startDate.toString())
                            .param("endDate", endDate.toString()))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(header().string("Cache-Control", "max-age=300"))
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].id").value(pantryItemId.toString()));

            verify(securityService).canAccessHousehold(householdId);
            verify(pantryItemService).getExpiringItems(eq(householdId), eq(startDate), eq(endDate));
        }

        @Test
        @DisplayName("should use default date range when not provided")
        void shouldUseDefaultDateRangeWhenNotProvided() throws Exception {
            List<PantryItemResponse> expiringItems = List.of(pantryItemResponse);
            when(pantryItemService.getExpiringItems(eq(householdId), any(LocalDate.class), any(LocalDate.class)))
                    .thenReturn(expiringItems);

            mockMvc.perform(get("/api/households/{householdId}/pantry-items/expiring", householdId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray());

            verify(pantryItemService).getExpiringItems(eq(householdId), any(LocalDate.class), any(LocalDate.class));
        }

        @Test
        @DisplayName("should return 403 when user lacks access")
        void shouldReturn403WhenUserLacksAccess() throws Exception {
            when(securityService.canAccessHousehold(householdId)).thenReturn(false);

            mockMvc.perform(get("/api/households/{householdId}/pantry-items/expiring", householdId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isForbidden());

            verify(securityService).canAccessHousehold(householdId);
            verifyNoInteractions(pantryItemService);
        }
    }

    @Nested
    @DisplayName("GET /api/households/{householdId}/pantry-items/search")
    class SearchPantryItems {

        @Test
        @DisplayName("should return search results")
        void shouldReturnSearchResults() throws Exception {
            List<PantryItemResponse> searchResults = List.of(pantryItemResponse);
            when(pantryItemService.searchPantryItems(eq(householdId), eq("test")))
                    .thenReturn(searchResults);

            mockMvc.perform(get("/api/households/{householdId}/pantry-items/search", householdId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                            .param("query", "test"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(header().string("Cache-Control", "max-age=60"))
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].id").value(pantryItemId.toString()));

            verify(securityService).canAccessHousehold(householdId);
            verify(pantryItemService).searchPantryItems(eq(householdId), eq("test"));
        }

        @Test
        @DisplayName("should return 400 when query parameter is missing")
        void shouldReturn400WhenQueryParameterIsMissing() throws Exception {
            mockMvc.perform(get("/api/households/{householdId}/pantry-items/search", householdId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(pantryItemService);
        }

        @Test
        @DisplayName("should return empty results when no items match search")
        void shouldReturnEmptyResultsWhenNoItemsMatchSearch() throws Exception {
            when(pantryItemService.searchPantryItems(eq(householdId), eq("nonexistent")))
                    .thenReturn(Collections.emptyList());

            mockMvc.perform(get("/api/households/{householdId}/pantry-items/search", householdId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                            .param("query", "nonexistent"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(0));

            verify(pantryItemService).searchPantryItems(eq(householdId), eq("nonexistent"));
        }

        @Test
        @DisplayName("should return 403 when user lacks access")
        void shouldReturn403WhenUserLacksAccess() throws Exception {
            when(securityService.canAccessHousehold(householdId)).thenReturn(false);

            mockMvc.perform(get("/api/households/{householdId}/pantry-items/search", householdId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                            .param("query", "test"))
                    .andExpect(status().isForbidden());

            verify(securityService).canAccessHousehold(householdId);
            verifyNoInteractions(pantryItemService);
        }

        @Test
        @DisplayName("should return 400 when query is empty")
        void shouldReturn400WhenQueryIsEmpty() throws Exception {
            when(pantryItemService.searchPantryItems(eq(householdId), eq("")))
                    .thenThrow(new ValidationException("Search term cannot be null or empty"));

            mockMvc.perform(get("/api/households/{householdId}/pantry-items/search", householdId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                            .param("query", ""))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error_code").value("VALIDATION_ERROR"));

            verify(pantryItemService).searchPantryItems(eq(householdId), eq(""));
        }
    }

    @Nested
    @DisplayName("GET /api/households/{householdId}/pantry-items/statistics")
    class GetPantryStatistics {

        @Test
        @DisplayName("should return pantry statistics")
        void shouldReturnPantryStatistics() throws Exception {
            Map<String, Object> statistics = Map.of(
                    "totalItems", 10,
                    "totalProducts", 5,
                    "expiringItems", 2,
                    "lowStockItems", 1
            );

            when(pantryItemService.getPantryStatistics(eq(householdId)))
                    .thenReturn(statistics);

            mockMvc.perform(get("/api/households/{householdId}/pantry-items/statistics", householdId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(header().string("Cache-Control", "max-age=30"))
                    .andExpect(jsonPath("$.totalItems").value(10))
                    .andExpect(jsonPath("$.totalProducts").value(5))
                    .andExpect(jsonPath("$.expiringItems").value(2))
                    .andExpect(jsonPath("$.lowStockItems").value(1));

            verify(securityService).canAccessHousehold(householdId);
            verify(pantryItemService).getPantryStatistics(eq(householdId));
        }

        @Test
        @DisplayName("should return 403 when user lacks access")
        void shouldReturn403WhenUserLacksAccess() throws Exception {
            when(securityService.canAccessHousehold(householdId)).thenReturn(false);

            mockMvc.perform(get("/api/households/{householdId}/pantry-items/statistics", householdId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isForbidden());

            verify(securityService).canAccessHousehold(householdId);
            verifyNoInteractions(pantryItemService);
        }
    }

    @Nested
    @DisplayName("Authentication and Authorization Edge Cases")
    class AuthenticationAndAuthorizationEdgeCases {

        @Test
        @DisplayName("should return 401 when no authorization header provided")
        void shouldReturn401WhenNoAuthorizationHeader() throws Exception {
            mockMvc.perform(get("/api/pantry-items/{id}", pantryItemId))
                    .andExpect(status().isUnauthorized());

            verifyNoInteractions(pantryItemService);
        }

        @Test
        @DisplayName("should return 500 when SecurityService throws unexpected exception")
        void shouldReturn500WhenSecurityServiceFails() throws Exception {
            when(securityService.canAccessPantryItem(any()))
                    .thenThrow(new RuntimeException("Security context unavailable"));

            mockMvc.perform(get("/api/pantry-items/" + pantryItemId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isInternalServerError());

            verifyNoInteractions(pantryItemService);
        }

        @Test
        @DisplayName("should return 401 when authentication fails in service layer")
        void shouldReturn401WhenAuthenticationFailsInServiceLayer() throws Exception {
            when(pantryItemService.createPantryItem(any(CreatePantryItemRequest.class)))
                    .thenThrow(new AuthenticationRequiredException("No authenticated user found"));

            mockMvc.perform(post("/api/pantry-items")
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(createRequest)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error_code").value("AUTHENTICATION_REQUIRED"));

            verify(pantryItemService).createPantryItem(any(CreatePantryItemRequest.class));
        }
    }

    @Nested
    @DisplayName("Error Response Format")
    class ErrorResponseFormat {

        @Test
        @DisplayName("should include correlation_id in all error responses")
        void shouldIncludeCorrelationIdInErrors() throws Exception {
            when(pantryItemService.getPantryItemById(pantryItemId))
                    .thenThrow(new NotFoundException("Pantry item not found with ID: " + pantryItemId));

            mockMvc.perform(get("/api/pantry-items/{id}", pantryItemId)
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
            CreatePantryItemRequest invalidRequest = CreatePantryItemRequest.builder()
                    .quantity(-1)
                    .build();

            mockMvc.perform(post("/api/pantry-items")
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(invalidRequest)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").isNotEmpty());
        }

        @Test
        @DisplayName("should return 500 when unexpected service exception occurs")
        void shouldReturn500WhenUnexpectedExceptionOccurs() throws Exception {
            when(pantryItemService.getPantryItemById(pantryItemId))
                    .thenThrow(new RuntimeException("Database connection failed"));

            mockMvc.perform(get("/api/pantry-items/{id}", pantryItemId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.error_code").value("INTERNAL_SERVER_ERROR"))
                    .andExpect(jsonPath("$.correlation_id").exists());
        }
    }
}