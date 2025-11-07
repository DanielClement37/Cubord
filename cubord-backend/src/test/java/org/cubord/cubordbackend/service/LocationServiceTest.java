package org.cubord.cubordbackend.service;

import org.cubord.cubordbackend.domain.*;
import org.cubord.cubordbackend.dto.location.LocationRequest;
import org.cubord.cubordbackend.dto.location.LocationResponse;
import org.cubord.cubordbackend.dto.location.LocationUpdateRequest;
import org.cubord.cubordbackend.exception.ConflictException;
import org.cubord.cubordbackend.exception.DataIntegrityException;
import org.cubord.cubordbackend.exception.InsufficientPermissionException;
import org.cubord.cubordbackend.exception.NotFoundException;
import org.cubord.cubordbackend.exception.ValidationException;
import org.cubord.cubordbackend.repository.HouseholdMemberRepository;
import org.cubord.cubordbackend.repository.HouseholdRepository;
import org.cubord.cubordbackend.repository.LocationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LocationServiceTest {

    @Mock
    private LocationRepository locationRepository;

    @Mock
    private HouseholdRepository householdRepository;

    @Mock
    private HouseholdMemberRepository householdMemberRepository;

    @Mock
    private UserService userService;

    @Mock
    private JwtAuthenticationToken token;

    @InjectMocks
    private LocationService locationService;

    private User testUser;
    private Household testHousehold;
    private HouseholdMember testMember;
    private Location testLocation;
    private LocationRequest locationRequest;
    private LocationUpdateRequest locationUpdateRequest;
    private UUID userId;
    private UUID householdId;
    private UUID locationId;

    @BeforeEach
    void setUp() {
        reset(locationRepository, householdRepository, householdMemberRepository, userService, token);

        userId = UUID.randomUUID();
        householdId = UUID.randomUUID();
        locationId = UUID.randomUUID();

        setupTestData();
    }

    private void setupTestData() {
        testUser = new User();
        testUser.setId(userId);
        testUser.setUsername("testuser");
        testUser.setEmail("test@example.com");
        testUser.setDisplayName("Test User");

        testHousehold = new Household();
        testHousehold.setId(householdId);
        testHousehold.setName("Test Household");

        testMember = new HouseholdMember();
        testMember.setId(UUID.randomUUID());
        testMember.setUser(testUser);
        testMember.setHousehold(testHousehold);
        testMember.setRole(HouseholdRole.MEMBER);

        testLocation = new Location();
        testLocation.setId(locationId);
        testLocation.setName("Kitchen");
        testLocation.setDescription("Main kitchen area");
        testLocation.setHousehold(testHousehold);
        testLocation.setCreatedAt(LocalDateTime.now());
        testLocation.setUpdatedAt(LocalDateTime.now());

        locationRequest = LocationRequest.builder()
                .name("New Location")
                .description("A new storage location")
                .householdId(householdId)
                .build();

        locationUpdateRequest = LocationUpdateRequest.builder()
                .name("Updated Location")
                .description("Updated description")
                .build();
    }

    @Nested
    @DisplayName("Create Location Tests")
    class CreateLocationTests {

        @Test
        @DisplayName("should create location successfully when valid data provided")
        void shouldCreateLocationWhenValidDataProvided() {
            // Given
            when(locationRepository.existsByHouseholdIdAndName(householdId, locationRequest.getName()))
                    .thenReturn(false);
            when(householdRepository.findById(householdId)).thenReturn(Optional.of(testHousehold));
            when(locationRepository.save(any(Location.class))).thenReturn(testLocation);

            // When
            LocationResponse result = locationService.createLocation(locationRequest, token);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getName()).isEqualTo(testLocation.getName());
            verify(locationRepository).save(any(Location.class));
        }

        @Test
        @DisplayName("should throw ConflictException when location name already exists")
        void shouldThrowConflictExceptionWhenLocationNameExists() {
            // Given
            when(locationRepository.existsByHouseholdIdAndName(householdId, locationRequest.getName()))
                    .thenReturn(true);

            // When & Then
            assertThatThrownBy(() -> locationService.createLocation(locationRequest, token))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("Location with name '" + locationRequest.getName() + "' already exists");

            verify(locationRepository, never()).save(any(Location.class));
        }

        @Test
        @DisplayName("should throw NotFoundException when household not found")
        void shouldThrowNotFoundExceptionWhenHouseholdNotFound() {
            // Given
            when(locationRepository.existsByHouseholdIdAndName(householdId, locationRequest.getName()))
                    .thenReturn(false);
            when(householdRepository.findById(householdId)).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> locationService.createLocation(locationRequest, token))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("Household");

            verify(locationRepository, never()).save(any(Location.class));
        }

        @Test
        @DisplayName("should throw ValidationException when request is null")
        void shouldThrowValidationExceptionWhenRequestIsNull() {
            // When & Then
            assertThatThrownBy(() -> locationService.createLocation(null, token))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Location request cannot be null");

            verify(locationRepository, never()).save(any(Location.class));
        }

        @Test
        @DisplayName("should throw ValidationException when token is null")
        void shouldThrowValidationExceptionWhenTokenIsNull() {
            // When & Then
            assertThatThrownBy(() -> locationService.createLocation(locationRequest, null))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Authentication token cannot be null");

            verify(locationRepository, never()).save(any(Location.class));
        }

        @Test
        @DisplayName("should throw DataIntegrityException when save operation fails")
        void shouldThrowDataIntegrityExceptionWhenSaveOperationFails() {
            // Given
            when(locationRepository.existsByHouseholdIdAndName(householdId, locationRequest.getName()))
                    .thenReturn(false);
            when(householdRepository.findById(householdId)).thenReturn(Optional.of(testHousehold));
            when(locationRepository.save(any(Location.class)))
                    .thenThrow(new RuntimeException("Database error"));

            // When & Then
            assertThatThrownBy(() -> locationService.createLocation(locationRequest, token))
                    .isInstanceOf(DataIntegrityException.class)
                    .hasMessageContaining("Failed to save location");

            verify(locationRepository).save(any(Location.class));
        }
    }

    @Nested
    @DisplayName("Get Location Tests")
    class GetLocationTests {

        @Test
        @DisplayName("should get location by ID when user has access")
        void shouldGetLocationByIdWhenUserHasAccess() {
            // Given
            when(userService.getCurrentUser(token)).thenReturn(testUser);
            when(locationRepository.findById(locationId)).thenReturn(Optional.of(testLocation));
            when(householdRepository.existsById(householdId)).thenReturn(true);
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, userId))
                    .thenReturn(Optional.of(testMember));

            // When
            LocationResponse result = locationService.getLocationById(locationId, token);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(locationId);
            verify(locationRepository).findById(locationId);
        }

        @Test
        @DisplayName("should throw NotFoundException when location not found")
        void shouldThrowNotFoundExceptionWhenLocationNotFound() {
            // Given
            when(userService.getCurrentUser(token)).thenReturn(testUser);
            when(locationRepository.findById(locationId)).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> locationService.getLocationById(locationId, token))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("Location");

            verify(userService).getCurrentUser(token);
            verify(locationRepository).findById(locationId);
        }

        @Test
        @DisplayName("should throw ValidationException when location ID is null")
        void shouldThrowValidationExceptionWhenLocationIdIsNull() {
            // When & Then
            assertThatThrownBy(() -> locationService.getLocationById(null, token))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Location ID cannot be null");

            verify(locationRepository, never()).findById(any());
        }

        @Test
        @DisplayName("should throw ValidationException when token is null")
        void shouldThrowValidationExceptionWhenTokenIsNull() {
            // When & Then
            assertThatThrownBy(() -> locationService.getLocationById(locationId, null))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Authentication token cannot be null");

            verify(locationRepository, never()).findById(any());
        }

        @Test
        @DisplayName("should get locations by household when user has access")
        void shouldGetLocationsByHouseholdWhenUserHasAccess() {
            // Given
            List<Location> locations = List.of(testLocation);
            when(userService.getCurrentUser(token)).thenReturn(testUser);
            when(householdRepository.existsById(householdId)).thenReturn(true);
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, userId))
                    .thenReturn(Optional.of(testMember));
            when(locationRepository.findByHouseholdId(householdId, Sort.by("name")))
                    .thenReturn(locations);

            // When
            List<LocationResponse> result = locationService.getLocationsByHousehold(householdId, token);

            // Then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getId()).isEqualTo(locationId);
            verify(locationRepository).findByHouseholdId(householdId, Sort.by("name"));
        }

        @Test
        @DisplayName("should throw ValidationException when household ID is null")
        void shouldThrowValidationExceptionWhenHouseholdIdIsNull() {
            // When & Then
            assertThatThrownBy(() -> locationService.getLocationsByHousehold(null, token))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Household ID cannot be null");

            verify(locationRepository, never()).findByHouseholdId(any(), any());
        }
    }

    @Nested
    @DisplayName("Update Location Tests")
    class UpdateLocationTests {

        @Test
        @DisplayName("should update location when valid data provided")
        void shouldUpdateLocationWhenValidDataProvided() {
            // Given
            when(locationRepository.findById(locationId)).thenReturn(Optional.of(testLocation));
            when(locationRepository.existsByHouseholdIdAndName(householdId, locationUpdateRequest.getName()))
                    .thenReturn(false);
            when(locationRepository.save(any(Location.class))).thenReturn(testLocation);

            // When
            LocationResponse result = locationService.updateLocation(locationId, locationUpdateRequest, token);

            // Then
            assertThat(result).isNotNull();
            verify(locationRepository).save(any(Location.class));
        }

        @Test
        @DisplayName("should throw NotFoundException when location not found")
        void shouldThrowNotFoundExceptionWhenLocationNotFound() {
            // Given
            when(locationRepository.findById(locationId)).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> locationService.updateLocation(locationId, locationUpdateRequest, token))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("Location");

            verify(locationRepository).findById(locationId);
            verify(locationRepository, never()).save(any(Location.class));
        }

        @Test
        @DisplayName("should throw ConflictException when new name already exists")
        void shouldThrowConflictExceptionWhenNewNameExists() {
            // Given
            when(locationRepository.findById(locationId)).thenReturn(Optional.of(testLocation));
            when(locationRepository.existsByHouseholdIdAndName(householdId, locationUpdateRequest.getName()))
                    .thenReturn(true);

            // When & Then
            assertThatThrownBy(() -> locationService.updateLocation(locationId, locationUpdateRequest, token))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("Location with name '" + locationUpdateRequest.getName() + "' already exists");

            verify(locationRepository, never()).save(any(Location.class));
        }

        @Test
        @DisplayName("should throw ValidationException when location ID is null")
        void shouldThrowValidationExceptionWhenLocationIdIsNull() {
            // When & Then
            assertThatThrownBy(() -> locationService.updateLocation(null, locationUpdateRequest, token))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Location ID cannot be null");

            verify(locationRepository, never()).findById(any());
            verify(locationRepository, never()).save(any(Location.class));
        }

        @Test
        @DisplayName("should throw ValidationException when request is null")
        void shouldThrowValidationExceptionWhenRequestIsNull() {
            // When & Then
            assertThatThrownBy(() -> locationService.updateLocation(locationId, null, token))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Update request cannot be null");

            verify(locationRepository, never()).findById(any());
            verify(locationRepository, never()).save(any(Location.class));
        }

        @Test
        @DisplayName("should throw DataIntegrityException when save operation fails")
        void shouldThrowDataIntegrityExceptionWhenSaveOperationFails() {
            // Given
            when(locationRepository.findById(locationId)).thenReturn(Optional.of(testLocation));
            when(locationRepository.existsByHouseholdIdAndName(householdId, locationUpdateRequest.getName()))
                    .thenReturn(false);
            when(locationRepository.save(any(Location.class)))
                    .thenThrow(new RuntimeException("Database error"));

            // When & Then
            assertThatThrownBy(() -> locationService.updateLocation(locationId, locationUpdateRequest, token))
                    .isInstanceOf(DataIntegrityException.class)
                    .hasMessageContaining("Failed to update location");

            verify(locationRepository).save(any(Location.class));
        }
    }

    @Nested
    @DisplayName("Patch Location Tests")
    class PatchLocationTests {

        @Test
        @DisplayName("should patch location when valid data provided")
        void shouldPatchLocationWhenValidDataProvided() {
            // Given
            Map<String, Object> patchData = Map.of("name", "Updated Kitchen");
            when(locationRepository.findById(locationId)).thenReturn(Optional.of(testLocation));
            when(locationRepository.existsByHouseholdIdAndName(householdId, "Updated Kitchen"))
                    .thenReturn(false);
            when(locationRepository.save(any(Location.class))).thenReturn(testLocation);

            // When
            LocationResponse result = locationService.patchLocation(locationId, patchData, token);

            // Then
            assertThat(result).isNotNull();
            verify(locationRepository).save(any(Location.class));
        }

        @Test
        @DisplayName("should throw NotFoundException when location not found")
        void shouldThrowNotFoundExceptionWhenLocationNotFound() {
            // Given
            Map<String, Object> patchData = Map.of("name", "Updated Kitchen");
            when(locationRepository.findById(locationId)).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> locationService.patchLocation(locationId, patchData, token))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("Location");

            verify(locationRepository).findById(locationId);
            verify(locationRepository, never()).save(any(Location.class));
        }

        @Test
        @DisplayName("should throw ValidationException when location ID is null")
        void shouldThrowValidationExceptionWhenLocationIdIsNull() {
            // Given
            Map<String, Object> patchData = Map.of("name", "Updated Kitchen");

            // When & Then
            assertThatThrownBy(() -> locationService.patchLocation(null, patchData, token))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Location ID cannot be null");

            verify(locationRepository, never()).findById(any());
            verify(locationRepository, never()).save(any(Location.class));
        }

        @Test
        @DisplayName("should throw ValidationException when patch data is null")
        void shouldThrowValidationExceptionWhenPatchDataIsNull() {
            // When & Then
            assertThatThrownBy(() -> locationService.patchLocation(locationId, null, token))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Patch data cannot be null");

            verify(locationRepository, never()).findById(any());
            verify(locationRepository, never()).save(any(Location.class));
        }

        @Test
        @DisplayName("should throw ValidationException when patch data is empty")
        void shouldThrowValidationExceptionWhenPatchDataIsEmpty() {
            // Given
            Map<String, Object> emptyPatchData = Map.of();

            // When & Then
            assertThatThrownBy(() -> locationService.patchLocation(locationId, emptyPatchData, token))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Patch data cannot be empty");

            verify(locationRepository, never()).findById(any());
            verify(locationRepository, never()).save(any(Location.class));
        }

        @Test
        @DisplayName("should ignore invalid fields in patch data")
        void shouldIgnoreInvalidFieldsInPatchData() {
            // Given
            Map<String, Object> patchData = Map.of(
                    "description", "Valid Description",
                    "invalidField", "value"
            );
            when(locationRepository.findById(locationId)).thenReturn(Optional.of(testLocation));
            when(locationRepository.save(any(Location.class))).thenReturn(testLocation);

            // When
            LocationResponse result = locationService.patchLocation(locationId, patchData, token);

            // Then
            assertThat(result).isNotNull();
            verify(locationRepository).save(any(Location.class));
        }

        @Test
        @DisplayName("should throw ValidationException when name is not a string")
        void shouldThrowValidationExceptionWhenNameIsNotString() {
            // Given
            Map<String, Object> patchData = Map.of("name", 123);
            when(locationRepository.findById(locationId)).thenReturn(Optional.of(testLocation));

            // When & Then
            assertThatThrownBy(() -> locationService.patchLocation(locationId, patchData, token))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Name must be a string");

            verify(locationRepository, never()).save(any(Location.class));
        }

        @Test
        @DisplayName("should throw ValidationException when description is not a string")
        void shouldThrowValidationExceptionWhenDescriptionIsNotString() {
            // Given
            Map<String, Object> patchData = Map.of("description", 123);
            when(locationRepository.findById(locationId)).thenReturn(Optional.of(testLocation));

            // When & Then
            assertThatThrownBy(() -> locationService.patchLocation(locationId, patchData, token))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Description must be a string");

            verify(locationRepository, never()).save(any(Location.class));
        }

        @Test
        @DisplayName("should throw ConflictException when new name already exists")
        void shouldThrowConflictExceptionWhenNewNameExists() {
            // Given
            Map<String, Object> patchData = Map.of("name", "Existing Location");
            when(locationRepository.findById(locationId)).thenReturn(Optional.of(testLocation));
            when(locationRepository.existsByHouseholdIdAndName(householdId, "Existing Location"))
                    .thenReturn(true);

            // When & Then
            assertThatThrownBy(() -> locationService.patchLocation(locationId, patchData, token))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("Location with name 'Existing Location' already exists");

            verify(locationRepository, never()).save(any(Location.class));
        }

        @Test
        @DisplayName("should throw DataIntegrityException when save operation fails")
        void shouldThrowDataIntegrityExceptionWhenSaveOperationFails() {
            // Given
            Map<String, Object> patchData = Map.of("name", "Updated Kitchen");
            when(locationRepository.findById(locationId)).thenReturn(Optional.of(testLocation));
            when(locationRepository.existsByHouseholdIdAndName(householdId, "Updated Kitchen"))
                    .thenReturn(false);
            when(locationRepository.save(any(Location.class)))
                    .thenThrow(new RuntimeException("Database error"));

            // When & Then
            assertThatThrownBy(() -> locationService.patchLocation(locationId, patchData, token))
                    .isInstanceOf(DataIntegrityException.class)
                    .hasMessageContaining("Failed to update location");

            verify(locationRepository).save(any(Location.class));
        }
    }

    @Nested
    @DisplayName("Delete Location Tests")
    class DeleteLocationTests {

        @Test
        @DisplayName("should delete location when found")
        void shouldDeleteLocationWhenFound() {
            // Given
            when(locationRepository.findById(locationId)).thenReturn(Optional.of(testLocation));
            doNothing().when(locationRepository).delete(any(Location.class));

            // When
            locationService.deleteLocation(locationId, token);

            // Then
            verify(locationRepository).findById(locationId);
            verify(locationRepository).delete(eq(testLocation));
        }

        @Test
        @DisplayName("should throw NotFoundException when location not found")
        void shouldThrowNotFoundExceptionWhenLocationNotFound() {
            // Given
            when(locationRepository.findById(locationId)).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> locationService.deleteLocation(locationId, token))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("Location");

            verify(locationRepository).findById(locationId);
            verify(locationRepository, never()).delete(any(Location.class));
        }

        @Test
        @DisplayName("should throw ValidationException when location ID is null")
        void shouldThrowValidationExceptionWhenLocationIdIsNull() {
            // When & Then
            assertThatThrownBy(() -> locationService.deleteLocation(null, token))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Location ID cannot be null");

            verify(locationRepository, never()).findById(any());
            verify(locationRepository, never()).delete(any(Location.class));
        }

        @Test
        @DisplayName("should throw ValidationException when token is null")
        void shouldThrowValidationExceptionWhenTokenIsNull() {
            // When & Then
            assertThatThrownBy(() -> locationService.deleteLocation(locationId, null))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Authentication token cannot be null");

            verify(locationRepository, never()).findById(any());
            verify(locationRepository, never()).delete(any(Location.class));
        }

        @Test
        @DisplayName("should throw DataIntegrityException when deletion fails")
        void shouldThrowDataIntegrityExceptionWhenDeletionFails() {
            // Given
            when(locationRepository.findById(locationId)).thenReturn(Optional.of(testLocation));
            doThrow(new RuntimeException("Foreign key constraint violation"))
                    .when(locationRepository).delete(any(Location.class));

            // When & Then
            assertThatThrownBy(() -> locationService.deleteLocation(locationId, token))
                    .isInstanceOf(DataIntegrityException.class)
                    .hasMessageContaining("Failed to delete location");

            verify(locationRepository).findById(locationId);
            verify(locationRepository).delete(eq(testLocation));
        }
    }

    @Nested
    @DisplayName("Search Location Tests")
    class SearchLocationTests {

        @Test
        @DisplayName("should search locations when user has access")
        void shouldSearchLocationsWhenUserHasAccess() {
            // Given
            String searchTerm = "kitchen";
            List<Location> locations = List.of(testLocation);
            when(userService.getCurrentUser(token)).thenReturn(testUser);
            when(householdRepository.existsById(householdId)).thenReturn(true);
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, userId))
                    .thenReturn(Optional.of(testMember));
            when(locationRepository.searchByNameOrDescription(householdId, searchTerm))
                    .thenReturn(locations);

            // When
            List<LocationResponse> result = locationService.searchLocations(householdId, searchTerm, token);

            // Then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getId()).isEqualTo(locationId);
            verify(locationRepository).searchByNameOrDescription(householdId, searchTerm);
        }

        @Test
        @DisplayName("should throw ValidationException when household ID is null")
        void shouldThrowValidationExceptionWhenHouseholdIdIsNull() {
            // When & Then
            assertThatThrownBy(() -> locationService.searchLocations(null, "search", token))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Household ID cannot be null");

            verify(locationRepository, never()).searchByNameOrDescription(any(), any());
        }

        @Test
        @DisplayName("should throw ValidationException when search term is null")
        void shouldThrowValidationExceptionWhenSearchTermIsNull() {
            // When & Then
            assertThatThrownBy(() -> locationService.searchLocations(householdId, null, token))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Search term cannot be null or empty");

            verify(locationRepository, never()).searchByNameOrDescription(any(), any());
        }

        @Test
        @DisplayName("should throw ValidationException when search term is empty")
        void shouldThrowValidationExceptionWhenSearchTermIsEmpty() {
            // When & Then
            assertThatThrownBy(() -> locationService.searchLocations(householdId, "  ", token))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Search term cannot be null or empty");

            verify(locationRepository, never()).searchByNameOrDescription(any(), any());
        }
    }

    @Nested
    @DisplayName("Name Availability Tests")
    class NameAvailabilityTests {

        @Test
        @DisplayName("should return true when name is available")
        void shouldReturnTrueWhenNameIsAvailable() {
            // Given
            String locationName = "New Location";
            when(userService.getCurrentUser(token)).thenReturn(testUser);
            when(householdRepository.existsById(householdId)).thenReturn(true);
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, userId))
                    .thenReturn(Optional.of(testMember));
            when(locationRepository.existsByHouseholdIdAndName(householdId, locationName))
                    .thenReturn(false);

            // When
            boolean result = locationService.isLocationNameAvailable(householdId, locationName, token);

            // Then
            assertThat(result).isTrue();
            verify(locationRepository).existsByHouseholdIdAndName(householdId, locationName);
        }

        @Test
        @DisplayName("should return false when name is not available")
        void shouldReturnFalseWhenNameIsNotAvailable() {
            // Given
            String locationName = "Kitchen";
            when(userService.getCurrentUser(token)).thenReturn(testUser);
            when(householdRepository.existsById(householdId)).thenReturn(true);
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, userId))
                    .thenReturn(Optional.of(testMember));
            when(locationRepository.existsByHouseholdIdAndName(householdId, locationName))
                    .thenReturn(true);

            // When
            boolean result = locationService.isLocationNameAvailable(householdId, locationName, token);

            // Then
            assertThat(result).isFalse();
            verify(locationRepository).existsByHouseholdIdAndName(householdId, locationName);
        }

        @Test
        @DisplayName("should throw ValidationException when household ID is null")
        void shouldThrowValidationExceptionWhenHouseholdIdIsNull() {
            // When & Then
            assertThatThrownBy(() -> locationService.isLocationNameAvailable(null, "name", token))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Household ID cannot be null");

            verify(locationRepository, never()).existsByHouseholdIdAndName(any(), any());
        }

        @Test
        @DisplayName("should throw ValidationException when name is null")
        void shouldThrowValidationExceptionWhenNameIsNull() {
            // When & Then
            assertThatThrownBy(() -> locationService.isLocationNameAvailable(householdId, null, token))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Location name cannot be null or empty");

            verify(locationRepository, never()).existsByHouseholdIdAndName(any(), any());
        }

        @Test
        @DisplayName("should throw ValidationException when name is empty")
        void shouldThrowValidationExceptionWhenNameIsEmpty() {
            // When & Then
            assertThatThrownBy(() -> locationService.isLocationNameAvailable(householdId, "  ", token))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Location name cannot be null or empty");

            verify(locationRepository, never()).existsByHouseholdIdAndName(any(), any());
        }
    }

    @Nested
    @DisplayName("Access Control Tests")
    class AccessControlTests {

        @Test
        @DisplayName("should throw InsufficientPermissionException when user is not household member")
        void shouldThrowInsufficientPermissionExceptionWhenUserIsNotMember() {
            // Given
            when(userService.getCurrentUser(token)).thenReturn(testUser);
            when(householdRepository.existsById(householdId)).thenReturn(true);
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, userId))
                    .thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> locationService.getLocationsByHousehold(householdId, token))
                    .isInstanceOf(InsufficientPermissionException.class)
                    .hasMessageContaining("Insufficient permission");

            verify(householdMemberRepository).findByHouseholdIdAndUserId(householdId, userId);
        }

        @Test
        @DisplayName("should throw NotFoundException when household not found")
        void shouldThrowNotFoundExceptionWhenHouseholdNotFound() {
            // Given
            when(userService.getCurrentUser(token)).thenReturn(testUser);
            when(householdRepository.existsById(householdId)).thenReturn(false);

            // When & Then
            assertThatThrownBy(() -> locationService.getLocationsByHousehold(householdId, token))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("Household");

            verify(householdRepository).existsById(householdId);
            verify(householdMemberRepository, never()).findByHouseholdIdAndUserId(any(), any());
        }
    }
}