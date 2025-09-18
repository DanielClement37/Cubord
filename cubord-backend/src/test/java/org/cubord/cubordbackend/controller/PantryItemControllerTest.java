package org.cubord.cubordbackend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.cubord.cubordbackend.config.TestSecurityConfig;
import org.cubord.cubordbackend.dto.*;
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

@WebMvcTest(PantryItemController.class)
@Import(TestSecurityConfig.class)
@ActiveProfiles("test")
class PantryItemControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PantryItemService pantryItemService;

    @Autowired
    private ObjectMapper objectMapper;

    private final UUID householdId = UUID.randomUUID();
    private final UUID locationId = UUID.randomUUID();
    private final UUID productId = UUID.randomUUID();
    private final UUID pantryItemId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    
    private Jwt jwt;
    private PantryItemResponse pantryItemResponse;
    private CreatePantryItemRequest createRequest;
    private UpdatePantryItemRequest updateRequest;

    @BeforeEach
    void setUp() {
        // Create JWT token
        jwt = Jwt.withTokenValue("test-token")
                .header("alg", "RS256")
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
                .unitOfMeasure("units")
                .expirationDate(LocalDate.now().plusDays(7))
                .notes("Test notes")
                .createdAt(LocalDateTime.now().minusDays(1))
                .updatedAt(LocalDateTime.now())
                .build();

        createRequest = CreatePantryItemRequest.builder()
                .productId(productId)
                .locationId(locationId)
                .quantity(5)
                .unitOfMeasure("units")
                .expirationDate(LocalDate.now().plusDays(7))
                .notes("Test notes")
                .build();

        updateRequest = UpdatePantryItemRequest.builder()
                .locationId(locationId)
                .quantity(10)
                .unitOfMeasure("units")
                .expirationDate(LocalDate.now().plusDays(14))
                .notes("Updated notes")
                .build();
    }

    @Nested
    @DisplayName("POST /api/pantry-items")
    class CreatePantryItem {
        
        @Test
        @DisplayName("should create pantry item successfully")
        void shouldCreatePantryItemSuccessfully() throws Exception {
            when(pantryItemService.createPantryItem(any(CreatePantryItemRequest.class), any()))
                    .thenReturn(pantryItemResponse);

            mockMvc.perform(post("/api/pantry-items")
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(createRequest)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(pantryItemId.toString()))
                    .andExpect(jsonPath("$.product.id").value(productId.toString()))
                    .andExpect(jsonPath("$.location.id").value(locationId.toString()))
                    .andExpect(jsonPath("$.quantity").value(5))
                    .andExpect(jsonPath("$.unitOfMeasure").value("units"))
                    .andExpect(jsonPath("$.notes").value("Test notes"));

            verify(pantryItemService).createPantryItem(any(CreatePantryItemRequest.class), any());
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

            verify(pantryItemService, never()).createPantryItem(any(), any());
        }

        @Test
        @DisplayName("should require authentication")
        void shouldRequireAuthentication() throws Exception {
            mockMvc.perform(post("/api/pantry-items")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(createRequest)))
                    .andExpect(status().isUnauthorized());

            verify(pantryItemService, never()).createPantryItem(any(), any());
        }
    }

    @Nested
    @DisplayName("GET /api/pantry-items/{id}")
    class GetPantryItemById {
        
        @Test
        @DisplayName("should return pantry item by id")
        void shouldReturnPantryItemById() throws Exception {
            when(pantryItemService.getPantryItemById(eq(pantryItemId), any()))
                    .thenReturn(pantryItemResponse);

            mockMvc.perform(get("/api/pantry-items/{id}", pantryItemId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(pantryItemId.toString()))
                    .andExpect(jsonPath("$.product.name").value("Test Product"))
                    .andExpect(jsonPath("$.location.name").value("Test Location"));

            verify(pantryItemService).getPantryItemById(eq(pantryItemId), any());
        }

        @Test
        @DisplayName("should return 404 when pantry item not found")
        void shouldReturn404WhenPantryItemNotFound() throws Exception {
            when(pantryItemService.getPantryItemById(eq(pantryItemId), any()))
                    .thenThrow(new org.cubord.cubordbackend.exception.NotFoundException("Pantry item not found"));

            mockMvc.perform(get("/api/pantry-items/{id}", pantryItemId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isNotFound());

            verify(pantryItemService).getPantryItemById(eq(pantryItemId), any());
        }

        @Test
        @DisplayName("should return 400 for invalid UUID format")
        void shouldReturn400ForInvalidUuidFormat() throws Exception {
            mockMvc.perform(get("/api/pantry-items/{id}", "invalid-uuid")
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isBadRequest());

            verify(pantryItemService, never()).getPantryItemById(any(), any());
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
                    .unitOfMeasure("units")
                    .expirationDate(LocalDate.now().plusDays(14))
                    .notes("Updated notes")
                    .createdAt(pantryItemResponse.getCreatedAt())
                    .updatedAt(LocalDateTime.now())
                    .build();

            when(pantryItemService.updatePantryItem(eq(pantryItemId), any(UpdatePantryItemRequest.class), any()))
                    .thenReturn(updatedResponse);

            mockMvc.perform(put("/api/pantry-items/{id}", pantryItemId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(updateRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(pantryItemId.toString()))
                    .andExpect(jsonPath("$.quantity").value(10))
                    .andExpect(jsonPath("$.notes").value("Updated notes"));

            verify(pantryItemService).updatePantryItem(eq(pantryItemId), any(UpdatePantryItemRequest.class), any());
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

            verify(pantryItemService, never()).updatePantryItem(any(), any(), any());
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
                    .unitOfMeasure(pantryItemResponse.getUnitOfMeasure())
                    .expirationDate(pantryItemResponse.getExpirationDate())
                    .notes("Patched notes")
                    .createdAt(pantryItemResponse.getCreatedAt())
                    .updatedAt(LocalDateTime.now())
                    .build();

            when(pantryItemService.patchPantryItem(eq(pantryItemId), any(Map.class), any()))
                    .thenReturn(patchedResponse);

            mockMvc.perform(patch("/api/pantry-items/{id}", pantryItemId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(patchRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(pantryItemId.toString()))
                    .andExpect(jsonPath("$.quantity").value(15))
                    .andExpect(jsonPath("$.notes").value("Patched notes"));

            verify(pantryItemService).patchPantryItem(eq(pantryItemId), any(Map.class), any());
        }
    }

    @Nested
    @DisplayName("DELETE /api/pantry-items/{id}")
    class DeletePantryItem {
        
        @Test
        @DisplayName("should delete pantry item successfully")
        void shouldDeletePantryItemSuccessfully() throws Exception {
            doNothing().when(pantryItemService).deletePantryItem(eq(pantryItemId), any());

            mockMvc.perform(delete("/api/pantry-items/{id}", pantryItemId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isNoContent());

            verify(pantryItemService).deletePantryItem(eq(pantryItemId), any());
        }

        @Test
        @DisplayName("should return 404 when deleting non-existent item")
        void shouldReturn404WhenDeletingNonExistentItem() throws Exception {
            doThrow(new org.cubord.cubordbackend.exception.NotFoundException("Pantry item not found"))
                    .when(pantryItemService).deletePantryItem(eq(pantryItemId), any());

            mockMvc.perform(delete("/api/pantry-items/{id}", pantryItemId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isNotFound());

            verify(pantryItemService).deletePantryItem(eq(pantryItemId), any());
        }
    }

    @Nested
    @DisplayName("GET /api/locations/{locationId}/pantry-items")
    class GetPantryItemsByLocation {
        
        @Test
        @DisplayName("should return pantry items by location")
        void shouldReturnPantryItemsByLocation() throws Exception {
            List<PantryItemResponse> items = List.of(pantryItemResponse);
            when(pantryItemService.getPantryItemsByLocation(eq(locationId), any()))
                    .thenReturn(items);

            mockMvc.perform(get("/api/locations/{locationId}/pantry-items", locationId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].id").value(pantryItemId.toString()))
                    .andExpect(jsonPath("$[0].location.id").value(locationId.toString()));

            verify(pantryItemService).getPantryItemsByLocation(eq(locationId), any());
        }

        @Test
        @DisplayName("should return empty list when no items in location")
        void shouldReturnEmptyListWhenNoItemsInLocation() throws Exception {
            when(pantryItemService.getPantryItemsByLocation(eq(locationId), any()))
                    .thenReturn(Collections.emptyList());

            mockMvc.perform(get("/api/locations/{locationId}/pantry-items", locationId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(0));

            verify(pantryItemService).getPantryItemsByLocation(eq(locationId), any());
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
            
            when(pantryItemService.getPantryItemsByHousehold(eq(householdId), any(Pageable.class), any()))
                    .thenReturn(page);

            mockMvc.perform(get("/api/households/{householdId}/pantry-items", householdId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                            .param("page", "0")
                            .param("size", "20"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.content.length()").value(1))
                    .andExpect(jsonPath("$.content[0].id").value(pantryItemId.toString()))
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.totalPages").value(1));

            verify(pantryItemService).getPantryItemsByHousehold(eq(householdId), any(Pageable.class), any());
        }

        @Test
        @DisplayName("should use default pagination when no params provided")
        void shouldUseDefaultPaginationWhenNoParamsProvided() throws Exception {
            List<PantryItemResponse> items = List.of(pantryItemResponse);
            Page<PantryItemResponse> page = new PageImpl<>(items, PageRequest.of(0, 20), 1);
            
            when(pantryItemService.getPantryItemsByHousehold(eq(householdId), any(Pageable.class), any()))
                    .thenReturn(page);

            mockMvc.perform(get("/api/households/{householdId}/pantry-items", householdId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray());

            verify(pantryItemService).getPantryItemsByHousehold(eq(householdId), any(Pageable.class), any());
        }
    }

    @Nested
    @DisplayName("GET /api/households/{householdId}/pantry-items/low-stock")
    class GetLowStockItems {
        
        @Test
        @DisplayName("should return low stock items")
        void shouldReturnLowStockItems() throws Exception {
            List<PantryItemResponse> lowStockItems = List.of(pantryItemResponse);
            when(pantryItemService.getLowStockItems(eq(householdId), eq(10), any()))
                    .thenReturn(lowStockItems);

            mockMvc.perform(get("/api/households/{householdId}/pantry-items/low-stock", householdId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                            .param("threshold", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].id").value(pantryItemId.toString()));

            verify(pantryItemService).getLowStockItems(eq(householdId), eq(10), any());
        }

        @Test
        @DisplayName("should use default threshold when not provided")
        void shouldUseDefaultThresholdWhenNotProvided() throws Exception {
            List<PantryItemResponse> lowStockItems = List.of(pantryItemResponse);
            when(pantryItemService.getLowStockItems(eq(householdId), eq(5), any()))
                    .thenReturn(lowStockItems);

            mockMvc.perform(get("/api/households/{householdId}/pantry-items/low-stock", householdId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray());

            verify(pantryItemService).getLowStockItems(eq(householdId), eq(5), any());
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
            
            when(pantryItemService.getExpiringItems(eq(householdId), eq(startDate), eq(endDate), any()))
                    .thenReturn(expiringItems);

            mockMvc.perform(get("/api/households/{householdId}/pantry-items/expiring", householdId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                            .param("startDate", startDate.toString())
                            .param("endDate", endDate.toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].id").value(pantryItemId.toString()));

            verify(pantryItemService).getExpiringItems(eq(householdId), eq(startDate), eq(endDate), any());
        }

        @Test
        @DisplayName("should use default date range when not provided")
        void shouldUseDefaultDateRangeWhenNotProvided() throws Exception {
            List<PantryItemResponse> expiringItems = List.of(pantryItemResponse);
            when(pantryItemService.getExpiringItems(eq(householdId), any(LocalDate.class), any(LocalDate.class), any()))
                    .thenReturn(expiringItems);

            mockMvc.perform(get("/api/households/{householdId}/pantry-items/expiring", householdId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray());

            verify(pantryItemService).getExpiringItems(eq(householdId), any(LocalDate.class), any(LocalDate.class), any());
        }
    }

    @Nested
    @DisplayName("GET /api/households/{householdId}/pantry-items/search")
    class SearchPantryItems {
        
        @Test
        @DisplayName("should return search results")
        void shouldReturnSearchResults() throws Exception {
            List<PantryItemResponse> searchResults = List.of(pantryItemResponse);
            when(pantryItemService.searchPantryItems(eq(householdId), eq("test"), any()))
                    .thenReturn(searchResults);

            mockMvc.perform(get("/api/households/{householdId}/pantry-items/search", householdId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                            .param("query", "test"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].id").value(pantryItemId.toString()));

            verify(pantryItemService).searchPantryItems(eq(householdId), eq("test"), any());
        }

        @Test
        @DisplayName("should return 400 when query parameter is missing")
        void shouldReturn400WhenQueryParameterIsMissing() throws Exception {
            mockMvc.perform(get("/api/households/{householdId}/pantry-items/search", householdId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isBadRequest());

            verify(pantryItemService, never()).searchPantryItems(any(), any(), any());
        }

        @Test
        @DisplayName("should return empty results when no items match search")
        void shouldReturnEmptyResultsWhenNoItemsMatchSearch() throws Exception {
            when(pantryItemService.searchPantryItems(eq(householdId), eq("nonexistent"), any()))
                    .thenReturn(Collections.emptyList());

            mockMvc.perform(get("/api/households/{householdId}/pantry-items/search", householdId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                            .param("query", "nonexistent"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(0));

            verify(pantryItemService).searchPantryItems(eq(householdId), eq("nonexistent"), any());
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
            
            when(pantryItemService.createMultiplePantryItems(anyList(), any()))
                    .thenReturn(responses);

            mockMvc.perform(post("/api/pantry-items/batch")
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(requests)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].id").value(pantryItemId.toString()));

            verify(pantryItemService).createMultiplePantryItems(anyList(), any());
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

            verify(pantryItemService, never()).createMultiplePantryItems(anyList(), any());
        }
    }

    @Nested
    @DisplayName("DELETE /api/pantry-items/batch")
    class DeleteMultiplePantryItems {
        
        @Test
        @DisplayName("should delete multiple pantry items")
        void shouldDeleteMultiplePantryItems() throws Exception {
            List<UUID> itemIds = List.of(pantryItemId);
            when(pantryItemService.deleteMultiplePantryItems(eq(itemIds), any()))
                    .thenReturn(1);

            mockMvc.perform(delete("/api/pantry-items/batch")
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(itemIds)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.deletedCount").value(1));

            verify(pantryItemService).deleteMultiplePantryItems(eq(itemIds), any());
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
            
            when(pantryItemService.getPantryStatistics(eq(householdId), any()))
                    .thenReturn(statistics);

            mockMvc.perform(get("/api/households/{householdId}/pantry-items/statistics", householdId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalItems").value(10))
                    .andExpect(jsonPath("$.totalProducts").value(5))
                    .andExpect(jsonPath("$.expiringItems").value(2))
                    .andExpect(jsonPath("$.lowStockItems").value(1));

            verify(pantryItemService).getPantryStatistics(eq(householdId), any());
        }
    }

    @Nested
    @DisplayName("Security Tests")
    class SecurityTests {
        
        @Test
        @DisplayName("should return 401 when not authenticated")
        void shouldReturn401WhenNotAuthenticated() throws Exception {
            mockMvc.perform(get("/api/pantry-items/{id}", pantryItemId))
                    .andExpect(status().isUnauthorized());

            verify(pantryItemService, never()).getPantryItemById(any(), any());
        }

        @Test
        @DisplayName("should return 403 when access denied")
        void shouldReturn403WhenAccessDenied() throws Exception {
            when(pantryItemService.getPantryItemById(eq(pantryItemId), any()))
                    .thenThrow(new org.springframework.security.access.AccessDeniedException("Access denied"));

            mockMvc.perform(get("/api/pantry-items/{id}", pantryItemId)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                    .andExpect(status().isForbidden());

            verify(pantryItemService).getPantryItemById(eq(pantryItemId), any());
        }
    }
}
