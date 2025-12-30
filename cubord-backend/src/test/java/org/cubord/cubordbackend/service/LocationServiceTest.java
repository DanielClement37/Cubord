
package org.cubord.cubordbackend.service;

import org.cubord.cubordbackend.domain.Household;
import org.cubord.cubordbackend.domain.Location;
import org.cubord.cubordbackend.domain.User;
import org.cubord.cubordbackend.domain.UserRole;
import org.cubord.cubordbackend.dto.location.LocationRequest;
import org.cubord.cubordbackend.dto.location.LocationResponse;
import org.cubord.cubordbackend.dto.location.LocationUpdateRequest;
import org.cubord.cubordbackend.exception.ConflictException;
import org.cubord.cubordbackend.exception.DataIntegrityException;
import org.cubord.cubordbackend.exception.NotFoundException;
import org.cubord.cubordbackend.exception.ValidationException;
import org.cubord.cubordbackend.repository.HouseholdRepository;
import org.cubord.cubordbackend.repository.LocationRepository;
import org.cubord.cubordbackend.security.SecurityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive test suite for LocationService following the modernized security architecture.
 *
 * <p>Test Structure:</p>
 * <ul>
 *   <li>No token validation tests (handled by Spring Security filters)</li>
 *   <li>Authorization tested via SecurityService mocking</li>
 *   <li>Focus on business logic and data validation</li>
 *   <li>@PreAuthorize enforcement verified in integration tests</li>
 * </ul>
 *
 * <h2>Coverage Areas</h2>
 * <ul>
 *   <li>Create operations with household access validation</li>
 *   <li>Query operations with authorization checks</li>
 *   <li>Update operations with conflict detection</li>
 *   <li>Delete operations with constraint handling</li>
 *   <li>Validation and error handling</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("LocationService Tests")
class LocationServiceTest {

    @Mock
    private LocationRepository locationRepository;

    @Mock
    private HouseholdRepository householdRepository;

    @Mock
    private SecurityService securityService;

    @InjectMocks
    private LocationService locationService;

    // Test data
    private UUID testUserId;
    private UUID adminUserId;
    private UUID householdId;
    private UUID locationId;
    private User testUser;
    private User adminUser;
    private Household testHousehold;
    private Location testLocation;
    private LocationRequest locationRequest;
    private LocationUpdateRequest locationUpdateRequest;
    private LocalDateTime fixedTime;

    @BeforeEach
    void setUp() {
        testUserId = UUID.randomUUID();
        adminUserId = UUID.randomUUID();
        householdId = UUID.randomUUID();
        locationId = UUID.randomUUID();
        fixedTime = LocalDateTime.of(2024, 1, 1, 12, 0);

        testUser = User.builder()
                .id(testUserId)
                .username("testuser")
                .email("test@example.com")
                .displayName("Test User")
                .role(UserRole.USER)
                .createdAt(fixedTime)
                .updatedAt(fixedTime)
                .build();

        adminUser = User.builder()
                .id(adminUserId)
                .username("adminuser")
                .email("admin@example.com")
                .displayName("Admin User")
                .role(UserRole.ADMIN)
                .createdAt(fixedTime)
                .updatedAt(fixedTime)
                .build();

        testHousehold = Household.builder()
                .id(householdId)
                .name("Test Household")
                .createdAt(fixedTime)
                .updatedAt(fixedTime)
                .build();

        testLocation = Location.builder()
                .id(locationId)
                .household(testHousehold)
                .name("Kitchen")
                .description("Main kitchen pantry")
                .createdAt(fixedTime)
                .updatedAt(fixedTime)
                .build();

        locationRequest = LocationRequest.builder()
                .householdId(householdId)
                .name("Kitchen")
                .description("Main kitchen pantry")
                .build();

        locationUpdateRequest = LocationUpdateRequest.builder()
                .name("Updated Kitchen")
                .description("Updated description")
                .build();
    }

    // ==================== Test Utilities ====================

    private void mockAuthenticatedUser(UUID userId) {
        when(securityService.getCurrentUserId()).thenReturn(userId);
    }

    // ==================== Create Operation Tests ====================

    @Nested
    @DisplayName("createLocation")
    class CreateLocationTests {

        @Test
        @DisplayName("should create location successfully when user is household admin")
        void shouldCreateLocationSuccessfully() {
            // Given
            
            when(householdRepository.findById(householdId)).thenReturn(Optional.of(testHousehold));
            when(locationRepository.existsByHouseholdIdAndName(householdId, "Kitchen")).thenReturn(false);
            when(locationRepository.save(any(Location.class))).thenAnswer(inv -> {
                Location loc = inv.getArgument(0);
                loc.setId(locationId);
                loc.setCreatedAt(fixedTime);
                loc.setUpdatedAt(fixedTime);
                return loc;
            });

            // When
            LocationResponse response = locationService.createLocation(locationRequest);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo(locationId);
            assertThat(response.getHouseholdId()).isEqualTo(householdId);
            assertThat(response.getName()).isEqualTo("Kitchen");
            assertThat(response.getDescription()).isEqualTo("Main kitchen pantry");

            verify(securityService).getCurrentUserId();
            verify(householdRepository).findById(householdId);
            verify(locationRepository).existsByHouseholdIdAndName(householdId, "Kitchen");
            verify(locationRepository).save(argThat(location ->
                    location.getHousehold().getId().equals(householdId) &&
                            location.getName().equals("Kitchen") &&
                            location.getDescription().equals("Main kitchen pantry")
            ));
        }

        @Test
        @DisplayName("should trim whitespace from location name and description")
        void shouldTrimWhitespaceFromInputs() {
            // Given
            LocationRequest requestWithWhitespace = LocationRequest.builder()
                    .householdId(householdId)
                    .name("  Kitchen  ")
                    .description("  Main kitchen pantry  ")
                    .build();

            
            when(householdRepository.findById(householdId)).thenReturn(Optional.of(testHousehold));
            when(locationRepository.existsByHouseholdIdAndName(householdId, "Kitchen")).thenReturn(false);
            when(locationRepository.save(any(Location.class))).thenAnswer(inv -> {
                Location loc = inv.getArgument(0);
                loc.setId(locationId);
                return loc;
            });

            // When
            LocationResponse response = locationService.createLocation(requestWithWhitespace);

            // Then
            assertThat(response.getName()).isEqualTo("Kitchen");
            assertThat(response.getDescription()).isEqualTo("Main kitchen pantry");

            verify(locationRepository).save(argThat(location ->
                    location.getName().equals("Kitchen") &&
                            location.getDescription().equals("Main kitchen pantry")
            ));
        }

        @Test
        @DisplayName("should create location with null description")
        void shouldCreateLocationWithNullDescription() {
            // Given
            LocationRequest requestWithoutDescription = LocationRequest.builder()
                    .householdId(householdId)
                    .name("Kitchen")
                    .build();

            
            when(householdRepository.findById(householdId)).thenReturn(Optional.of(testHousehold));
            when(locationRepository.existsByHouseholdIdAndName(householdId, "Kitchen")).thenReturn(false);
            when(locationRepository.save(any(Location.class))).thenAnswer(inv -> {
                Location loc = inv.getArgument(0);
                loc.setId(locationId);
                return loc;
            });

            // When
            LocationResponse response = locationService.createLocation(requestWithoutDescription);

            // Then
            assertThat(response.getDescription()).isNull();

            verify(locationRepository).save(argThat(location ->
                    location.getDescription() == null
            ));
        }

        @Test
        @DisplayName("should throw ValidationException when request is null")
        void shouldThrowValidationExceptionWhenRequestIsNull() {
            // When/Then
            assertThatThrownBy(() -> locationService.createLocation(null))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Location request cannot be null");

            verify(securityService, never()).getCurrentUserId();
            verify(locationRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw ValidationException when household ID is null")
        void shouldThrowValidationExceptionWhenHouseholdIdIsNull() {
            // Given
            LocationRequest invalidRequest = LocationRequest.builder()
                    .name("Kitchen")
                    .build();

            // When/Then
            assertThatThrownBy(() -> locationService.createLocation(invalidRequest))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Household ID cannot be null");

            verify(locationRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw ValidationException when name is null")
        void shouldThrowValidationExceptionWhenNameIsNull() {
            // Given
            LocationRequest invalidRequest = LocationRequest.builder()
                    .householdId(householdId)
                    .build();

            // When/Then
            assertThatThrownBy(() -> locationService.createLocation(invalidRequest))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Location name cannot be null or empty");

            verify(locationRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw ValidationException when name is empty")
        void shouldThrowValidationExceptionWhenNameIsEmpty() {
            // Given
            LocationRequest invalidRequest = LocationRequest.builder()
                    .householdId(householdId)
                    .name("   ")
                    .build();

            // When/Then
            assertThatThrownBy(() -> locationService.createLocation(invalidRequest))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Location name cannot be null or empty");

            verify(locationRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw NotFoundException when household doesn't exist")
        void shouldThrowNotFoundExceptionWhenHouseholdDoesntExist() {
            // Given
            
            when(householdRepository.findById(householdId)).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> locationService.createLocation(locationRequest))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("Household not found");

            verify(householdRepository).findById(householdId);
            verify(locationRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw ConflictException when location name already exists in household")
        void shouldThrowConflictExceptionWhenNameExists() {
            // Given
            
            when(householdRepository.findById(householdId)).thenReturn(Optional.of(testHousehold));
            when(locationRepository.existsByHouseholdIdAndName(householdId, "Kitchen")).thenReturn(true);

            // When/Then
            assertThatThrownBy(() -> locationService.createLocation(locationRequest))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("Location with name 'Kitchen' already exists in this household");

            verify(locationRepository).existsByHouseholdIdAndName(householdId, "Kitchen");
            verify(locationRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw DataIntegrityException when save fails")
        void shouldThrowDataIntegrityExceptionWhenSaveFails() {
            // Given
            
            when(householdRepository.findById(householdId)).thenReturn(Optional.of(testHousehold));
            when(locationRepository.existsByHouseholdIdAndName(householdId, "Kitchen")).thenReturn(false);
            when(locationRepository.save(any(Location.class)))
                    .thenThrow(new RuntimeException("Database error"));

            // When/Then
            assertThatThrownBy(() -> locationService.createLocation(locationRequest))
                    .isInstanceOf(DataIntegrityException.class)
                    .hasMessageContaining("Failed to create location");

            verify(locationRepository).save(any(Location.class));
        }
    }

    // ==================== Query Operation Tests ====================

    @Nested
    @DisplayName("getLocationById")
    class GetLocationByIdTests {

        @Test
        @DisplayName("should retrieve location successfully when user has access")
        void shouldGetLocationByIdSuccessfully() {
            // Given
            mockAuthenticatedUser(testUserId);
            when(locationRepository.findById(locationId)).thenReturn(Optional.of(testLocation));

            // When
            LocationResponse response = locationService.getLocationById(locationId);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo(locationId);
            assertThat(response.getName()).isEqualTo("Kitchen");
            assertThat(response.getDescription()).isEqualTo("Main kitchen pantry");
            assertThat(response.getHouseholdId()).isEqualTo(householdId);

            verify(securityService).getCurrentUserId();
            verify(locationRepository).findById(locationId);
        }

        @Test
        @DisplayName("should throw ValidationException when location ID is null")
        void shouldThrowValidationExceptionWhenLocationIdIsNull() {
            // When/Then
            assertThatThrownBy(() -> locationService.getLocationById(null))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Location ID cannot be null");

            verify(locationRepository, never()).findById(any());
        }

        @Test
        @DisplayName("should throw NotFoundException when location doesn't exist")
        void shouldThrowNotFoundExceptionWhenLocationDoesntExist() {
            // Given
            mockAuthenticatedUser(testUserId);
            when(locationRepository.findById(locationId)).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> locationService.getLocationById(locationId))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("Location not found");

            verify(locationRepository).findById(locationId);
        }
    }

    @Nested
    @DisplayName("getLocationsByHousehold")
    class GetLocationsByHouseholdTests {

        @Test
        @DisplayName("should retrieve all locations for household")
        void shouldGetLocationsByHouseholdSuccessfully() {
            // Given

            Location location2 = Location.builder()
                    .id(UUID.randomUUID())
                    .household(testHousehold)
                    .name("Fridge")
                    .description("Refrigerator")
                    .createdAt(fixedTime)
                    .updatedAt(fixedTime)
                    .build();

            List<Location> locations = Arrays.asList(testLocation, location2);
            when(locationRepository.findByHouseholdId(householdId)).thenReturn(locations);

            // When
            List<LocationResponse> responses = locationService.getLocationsByHousehold(householdId);

            // Then
            assertThat(responses).hasSize(2);
            assertThat(responses.get(0).getName()).isEqualTo("Kitchen");
            assertThat(responses.get(1).getName()).isEqualTo("Fridge");

            verify(securityService).getCurrentUserId();
            verify(locationRepository).findByHouseholdId(householdId);
        }

        @Test
        @DisplayName("should return empty list when household has no locations")
        void shouldReturnEmptyListWhenNoLocations() {
            // Given
            when(locationRepository.findByHouseholdId(householdId)).thenReturn(Collections.emptyList());

            // When
            List<LocationResponse> responses = locationService.getLocationsByHousehold(householdId);

            // Then
            assertThat(responses).isEmpty();

            verify(locationRepository).findByHouseholdId(householdId);
        }

        @Test
        @DisplayName("should throw ValidationException when household ID is null")
        void shouldThrowValidationExceptionWhenHouseholdIdIsNull() {
            // When/Then
            assertThatThrownBy(() -> locationService.getLocationsByHousehold(null))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Household ID cannot be null");

            verify(locationRepository, never()).findByHouseholdId(any());
        }
    }

    @Nested
    @DisplayName("searchLocations")
    class SearchLocationsTests {

        @Test
        @DisplayName("should search locations successfully")
        void shouldSearchLocationsSuccessfully() {
            // Given
            String searchTerm = "kit";

            when(locationRepository.searchByNameOrDescription(householdId, searchTerm))
                    .thenReturn(Collections.singletonList(testLocation));

            // When
            List<LocationResponse> responses = locationService.searchLocations(householdId, searchTerm);

            // Then
            assertThat(responses).hasSize(1);
            assertThat(responses.getFirst().getName()).isEqualTo("Kitchen");

            verify(securityService).getCurrentUserId();
            verify(locationRepository).searchByNameOrDescription(householdId, searchTerm);
        }

        @Test
        @DisplayName("should trim search term whitespace")
        void shouldTrimSearchTermWhitespace() {
            // Given
            String searchTerm = "  kit  ";

            when(locationRepository.searchByNameOrDescription(householdId, "kit"))
                    .thenReturn(Collections.singletonList(testLocation));

            // When
            List<LocationResponse> responses = locationService.searchLocations(householdId, searchTerm);

            // Then
            assertThat(responses).hasSize(1);

            verify(locationRepository).searchByNameOrDescription(householdId, "kit");
        }

        @Test
        @DisplayName("should throw ValidationException when household ID is null")
        void shouldThrowValidationExceptionWhenHouseholdIdIsNull() {
            // When/Then
            assertThatThrownBy(() -> locationService.searchLocations(null, "search"))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Household ID cannot be null");

            verify(locationRepository, never()).searchByNameOrDescription(any(), any());
        }

        @Test
        @DisplayName("should throw ValidationException when search term is null")
        void shouldThrowValidationExceptionWhenSearchTermIsNull() {
            // When/Then
            assertThatThrownBy(() -> locationService.searchLocations(householdId, null))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Search term cannot be null or empty");

            verify(locationRepository, never()).searchByNameOrDescription(any(), any());
        }

        @Test
        @DisplayName("should throw ValidationException when search term is empty")
        void shouldThrowValidationExceptionWhenSearchTermIsEmpty() {
            // When/Then
            assertThatThrownBy(() -> locationService.searchLocations(householdId, "   "))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Search term cannot be null or empty");

            verify(locationRepository, never()).searchByNameOrDescription(any(), any());
        }
    }

    @Nested
    @DisplayName("isLocationNameAvailable")
    class IsLocationNameAvailableTests {

        @Test
        @DisplayName("should return true when name is available")
        void shouldReturnTrueWhenNameIsAvailable() {
            // Given
            String name = "New Location";
            when(locationRepository.existsByHouseholdIdAndName(householdId, name)).thenReturn(false);

            // When
            boolean result = locationService.isLocationNameAvailable(householdId, name);

            // Then
            assertThat(result).isTrue();

            verify(locationRepository).existsByHouseholdIdAndName(householdId, name);
        }

        @Test
        @DisplayName("should return false when name already exists")
        void shouldReturnFalseWhenNameExists() {
            // Given
            String name = "Kitchen";
            when(locationRepository.existsByHouseholdIdAndName(householdId, name)).thenReturn(true);

            // When
            boolean result = locationService.isLocationNameAvailable(householdId, name);

            // Then
            assertThat(result).isFalse();

            verify(locationRepository).existsByHouseholdIdAndName(householdId, name);
        }

        @Test
        @DisplayName("should trim name whitespace before checking")
        void shouldTrimNameWhitespace() {
            // Given
            String name = "  Kitchen  ";
            when(locationRepository.existsByHouseholdIdAndName(householdId, "Kitchen")).thenReturn(false);

            // When
            boolean result = locationService.isLocationNameAvailable(householdId, name);

            // Then
            assertThat(result).isTrue();

            verify(locationRepository).existsByHouseholdIdAndName(householdId, "Kitchen");
        }

        @Test
        @DisplayName("should throw ValidationException when household ID is null")
        void shouldThrowValidationExceptionWhenHouseholdIdIsNull() {
            // When/Then
            assertThatThrownBy(() -> locationService.isLocationNameAvailable(null, "Kitchen"))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Household ID cannot be null");

            verify(locationRepository, never()).existsByHouseholdIdAndName(any(), any());
        }

        @Test
        @DisplayName("should throw ValidationException when name is null")
        void shouldThrowValidationExceptionWhenNameIsNull() {
            // When/Then
            assertThatThrownBy(() -> locationService.isLocationNameAvailable(householdId, null))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Location name cannot be null or empty");

            verify(locationRepository, never()).existsByHouseholdIdAndName(any(), any());
        }

        @Test
        @DisplayName("should throw ValidationException when name is empty")
        void shouldThrowValidationExceptionWhenNameIsEmpty() {
            // When/Then
            assertThatThrownBy(() -> locationService.isLocationNameAvailable(householdId, "   "))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Location name cannot be null or empty");

            verify(locationRepository, never()).existsByHouseholdIdAndName(any(), any());
        }
    }

    // ==================== Update Operation Tests ====================

    @Nested
    @DisplayName("updateLocation")
    class UpdateLocationTests {

        @Test
        @DisplayName("should update location name and description successfully")
        void shouldUpdateLocationSuccessfully() {
            // Given
            
            when(locationRepository.findById(locationId)).thenReturn(Optional.of(testLocation));
            when(locationRepository.existsByHouseholdIdAndName(householdId, "Updated Kitchen")).thenReturn(false);
            when(locationRepository.save(any(Location.class))).thenAnswer(inv -> inv.getArgument(0));

            // When
            LocationResponse response = locationService.updateLocation(locationId, locationUpdateRequest);

            // Then
            assertThat(response.getName()).isEqualTo("Updated Kitchen");
            assertThat(response.getDescription()).isEqualTo("Updated description");

            verify(locationRepository).findById(locationId);
            verify(locationRepository).existsByHouseholdIdAndName(householdId, "Updated Kitchen");
            verify(locationRepository).save(argThat(location ->
                    location.getName().equals("Updated Kitchen") &&
                            location.getDescription().equals("Updated description")
            ));
        }

        @Test
        @DisplayName("should update only description when name is same")
        void shouldUpdateOnlyDescription() {
            // Given
            LocationUpdateRequest updateRequest = LocationUpdateRequest.builder()
                    .name("Kitchen")  // Same as current
                    .description("New description")
                    .build();

            
            when(locationRepository.findById(locationId)).thenReturn(Optional.of(testLocation));
            when(locationRepository.save(any(Location.class))).thenAnswer(inv -> inv.getArgument(0));

            // When
            LocationResponse response = locationService.updateLocation(locationId, updateRequest);

            // Then
            assertThat(response.getName()).isEqualTo("Kitchen");
            assertThat(response.getDescription()).isEqualTo("New description");

            // Should not check for name conflicts since name didn't change
            verify(locationRepository, never()).existsByHouseholdIdAndName(any(), any());
        }

        @Test
        @DisplayName("should trim whitespace from updated values")
        void shouldTrimWhitespaceFromUpdates() {
            // Given
            LocationUpdateRequest updateRequest = LocationUpdateRequest.builder()
                    .name("  Updated Kitchen  ")
                    .description("  Updated description  ")
                    .build();

            
            when(locationRepository.findById(locationId)).thenReturn(Optional.of(testLocation));
            when(locationRepository.existsByHouseholdIdAndName(householdId, "Updated Kitchen")).thenReturn(false);
            when(locationRepository.save(any(Location.class))).thenAnswer(inv -> inv.getArgument(0));

            // When
            LocationResponse response = locationService.updateLocation(locationId, updateRequest);

            // Then
            verify(locationRepository).save(argThat(location ->
                    location.getName().equals("Updated Kitchen") &&
                            location.getDescription().equals("Updated description")
            ));
        }

        @Test
        @DisplayName("should throw ValidationException when location ID is null")
        void shouldThrowValidationExceptionWhenLocationIdIsNull() {
            // When/Then
            assertThatThrownBy(() -> locationService.updateLocation(null, locationUpdateRequest))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Location ID cannot be null");

            verify(locationRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw ValidationException when update request is null")
        void shouldThrowValidationExceptionWhenRequestIsNull() {
            // When/Then
            assertThatThrownBy(() -> locationService.updateLocation(locationId, null))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Update request cannot be null");

            verify(locationRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw NotFoundException when location doesn't exist")
        void shouldThrowNotFoundExceptionWhenLocationDoesntExist() {
            // Given
            
            when(locationRepository.findById(locationId)).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> locationService.updateLocation(locationId, locationUpdateRequest))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("Location not found");

            verify(locationRepository).findById(locationId);
            verify(locationRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw ConflictException when new name already exists")
        void shouldThrowConflictExceptionWhenNewNameExists() {
            // Given
            
            when(locationRepository.findById(locationId)).thenReturn(Optional.of(testLocation));
            when(locationRepository.existsByHouseholdIdAndName(householdId, "Updated Kitchen")).thenReturn(true);

            // When/Then
            assertThatThrownBy(() -> locationService.updateLocation(locationId, locationUpdateRequest))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("Location with name 'Updated Kitchen' already exists");

            verify(locationRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw DataIntegrityException when save fails")
        void shouldThrowDataIntegrityExceptionWhenSaveFails() {
            // Given
            
            when(locationRepository.findById(locationId)).thenReturn(Optional.of(testLocation));
            when(locationRepository.existsByHouseholdIdAndName(householdId, "Updated Kitchen")).thenReturn(false);
            when(locationRepository.save(any(Location.class)))
                    .thenThrow(new RuntimeException("Database error"));

            // When/Then
            assertThatThrownBy(() -> locationService.updateLocation(locationId, locationUpdateRequest))
                    .isInstanceOf(DataIntegrityException.class)
                    .hasMessageContaining("Failed to update location");

            verify(locationRepository).save(any());
        }
    }

    @Nested
    @DisplayName("patchLocation")
    class PatchLocationTests {

        @Test
        @DisplayName("should patch location name successfully")
        void shouldPatchNameSuccessfully() {
            // Given
            Map<String, Object> patchData = Map.of("name", "Patched Kitchen");

            when(locationRepository.findById(locationId)).thenReturn(Optional.of(testLocation));
            when(locationRepository.existsByHouseholdIdAndName(householdId, "Patched Kitchen")).thenReturn(false);
            when(locationRepository.save(any(Location.class))).thenAnswer(inv -> inv.getArgument(0));

            // When
            LocationResponse response = locationService.patchLocation(locationId, patchData);

            // Then
            assertThat(response.getName()).isEqualTo("Patched Kitchen");

            verify(locationRepository).save(argThat(location ->
                    location.getName().equals("Patched Kitchen")
            ));
        }

        @Test
        @DisplayName("should patch location description successfully")
        void shouldPatchDescriptionSuccessfully() {
            // Given
            Map<String, Object> patchData = Map.of("description", "Patched description");

            when(locationRepository.findById(locationId)).thenReturn(Optional.of(testLocation));
            when(locationRepository.save(any(Location.class))).thenAnswer(inv -> inv.getArgument(0));

            // When
            LocationResponse response = locationService.patchLocation(locationId, patchData);

            // Then
            assertThat(response.getDescription()).isEqualTo("Patched description");

            verify(locationRepository).save(argThat(location ->
                    location.getDescription().equals("Patched description")
            ));
        }

        @Test
        @DisplayName("should patch multiple fields successfully")
        void shouldPatchMultipleFieldsSuccessfully() {
            // Given
            Map<String, Object> patchData = new HashMap<>();
            patchData.put("name", "Patched Kitchen");
            patchData.put("description", "Patched description");

            when(locationRepository.findById(locationId)).thenReturn(Optional.of(testLocation));
            when(locationRepository.existsByHouseholdIdAndName(householdId, "Patched Kitchen")).thenReturn(false);
            when(locationRepository.save(any(Location.class))).thenAnswer(inv -> inv.getArgument(0));

            // When
            LocationResponse response = locationService.patchLocation(locationId, patchData);

            // Then
            assertThat(response.getName()).isEqualTo("Patched Kitchen");
            assertThat(response.getDescription()).isEqualTo("Patched description");
        }

        @Test
        @DisplayName("should set description to null when patched with null value")
        void shouldSetDescriptionToNull() {
            // Given
            Map<String, Object> patchData = new HashMap<>();
            patchData.put("description", null);

            when(locationRepository.findById(locationId)).thenReturn(Optional.of(testLocation));
            when(locationRepository.save(any(Location.class))).thenAnswer(inv -> inv.getArgument(0));

            // When
            locationService.patchLocation(locationId, patchData);

            // Then
            verify(locationRepository).save(argThat(location ->
                    location.getDescription() == null
            ));
        }

        @Test
        @DisplayName("should throw ValidationException when location ID is null")
        void shouldThrowValidationExceptionWhenLocationIdIsNull() {
            // Given
            Map<String, Object> patchData = Map.of("name", "Patched");

            // When/Then
            assertThatThrownBy(() -> locationService.patchLocation(null, patchData))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Location ID cannot be null");

            verify(locationRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw ValidationException when patch data is null")
        void shouldThrowValidationExceptionWhenPatchDataIsNull() {
            // When/Then
            assertThatThrownBy(() -> locationService.patchLocation(locationId, null))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Patch data cannot be null or empty");

            verify(locationRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw ValidationException when patch data is empty")
        void shouldThrowValidationExceptionWhenPatchDataIsEmpty() {
            // When/Then
            assertThatThrownBy(() -> locationService.patchLocation(locationId, Collections.emptyMap()))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Patch data cannot be null or empty");

            verify(locationRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw ValidationException when unsupported field is patched")
        void shouldThrowValidationExceptionForUnsupportedField() {
            // Given
            Map<String, Object> patchData = Map.of("unsupportedField", "value");

            when(locationRepository.findById(locationId)).thenReturn(Optional.of(testLocation));

            // When/Then
            assertThatThrownBy(() -> locationService.patchLocation(locationId, patchData))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Unsupported field for patching: unsupportedField");

            verify(locationRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw ConflictException when patched name already exists")
        void shouldThrowConflictExceptionWhenPatchedNameExists() {
            // Given
            Map<String, Object> patchData = Map.of("name", "Existing Location");

            when(locationRepository.findById(locationId)).thenReturn(Optional.of(testLocation));
            when(locationRepository.existsByHouseholdIdAndName(householdId, "Existing Location")).thenReturn(true);

            // When/Then
            assertThatThrownBy(() -> locationService.patchLocation(locationId, patchData))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("Location with name 'Existing Location' already exists");

            verify(locationRepository, never()).save(any());
        }
    }

    // ==================== Delete Operation Tests ====================

    @Nested
    @DisplayName("deleteLocation")
    class DeleteLocationTests {

        @Test
        @DisplayName("should delete location successfully")
        void shouldDeleteLocationSuccessfully() {
            // Given
            when(locationRepository.findById(locationId)).thenReturn(Optional.of(testLocation));
            doNothing().when(locationRepository).delete(testLocation);

            // When
            locationService.deleteLocation(locationId);

            // Then
            verify(securityService).getCurrentUserId();
            verify(locationRepository).findById(locationId);
            verify(locationRepository).delete(testLocation);
        }

        @Test
        @DisplayName("should throw ValidationException when location ID is null")
        void shouldThrowValidationExceptionWhenLocationIdIsNull() {
            // When/Then
            assertThatThrownBy(() -> locationService.deleteLocation(null))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Location ID cannot be null");

            verify(locationRepository, never()).delete(any());
        }

        @Test
        @DisplayName("should throw NotFoundException when location doesn't exist")
        void shouldThrowNotFoundExceptionWhenLocationDoesntExist() {
            // Given
            when(locationRepository.findById(locationId)).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> locationService.deleteLocation(locationId))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("Location not found");

            verify(locationRepository).findById(locationId);
            verify(locationRepository, never()).delete(any());
        }

        @Test
        @DisplayName("should throw DataIntegrityException when deletion fails due to constraints")
        void shouldThrowDataIntegrityExceptionWhenDeletionFails() {
            // Given
            when(locationRepository.findById(locationId)).thenReturn(Optional.of(testLocation));
            doThrow(new RuntimeException("Constraint violation"))
                    .when(locationRepository).delete(testLocation);

            // When/Then
            assertThatThrownBy(() -> locationService.deleteLocation(locationId))
                    .isInstanceOf(DataIntegrityException.class)
                    .hasMessageContaining("Failed to delete location")
                    .hasMessageContaining("Location may have associated pantry items");

            verify(locationRepository).delete(testLocation);
        }
    }
}