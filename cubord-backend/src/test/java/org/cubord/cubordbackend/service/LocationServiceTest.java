package org.cubord.cubordbackend.service;

import org.cubord.cubordbackend.domain.*;
import org.cubord.cubordbackend.dto.LocationRequest;
import org.cubord.cubordbackend.dto.LocationResponse;
import org.cubord.cubordbackend.dto.LocationUpdateRequest;
import org.cubord.cubordbackend.exception.ConflictException;
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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
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
        @DisplayName("Should create location successfully")
        void should_CreateLocation_When_ValidDataProvided() {
            // Given
            when(userService.getCurrentUser(token)).thenReturn(testUser);
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
        @DisplayName("Should throw ConflictException when location name already exists")
        void should_ThrowConflictException_When_LocationNameExists() {
            // Given
            when(userService.getCurrentUser(token)).thenReturn(testUser);
            when(locationRepository.existsByHouseholdIdAndName(householdId, locationRequest.getName()))
                    .thenReturn(true);

            // When & Then
            assertThrows(ConflictException.class, 
                    () -> locationService.createLocation(locationRequest, token));
        }
    }

    @Nested
    @DisplayName("Get Location Tests")
    class GetLocationTests {

        @Test
        @DisplayName("Should get location by ID successfully")
        void should_GetLocationById_When_UserHasAccess() {
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
        }

        @Test
        @DisplayName("Should get locations by household successfully")
        void should_GetLocationsByHousehold_When_UserHasAccess() {
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
            assertThat(result.getFirst().getId()).isEqualTo(locationId);
        }
    }

    @Nested
    @DisplayName("Update Location Tests")
    class UpdateLocationTests {

        @Test
        @DisplayName("Should update location successfully")
        void should_UpdateLocation_When_ValidDataProvided() {
            // Given
            when(userService.getCurrentUser(token)).thenReturn(testUser);
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
    }

    @Nested
    @DisplayName("Patch Location Tests")
    class PatchLocationTests {

        @Test
        @DisplayName("Should patch location successfully")
        void should_PatchLocation_When_ValidDataProvided() {
            // Given
            Map<String, Object> patchData = Map.of("name", "Updated Kitchen");
            when(userService.getCurrentUser(token)).thenReturn(testUser);
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
    }

    @Nested
    @DisplayName("Delete Location Tests")
    class DeleteLocationTests {

        @Test
        @DisplayName("Should delete location successfully")
        void should_DeleteLocation_When_UserHasAccess() {
            // Given
            when(userService.getCurrentUser(token)).thenReturn(testUser);
            when(locationRepository.findById(locationId)).thenReturn(Optional.of(testLocation));

            // When
            locationService.deleteLocation(locationId, token);

            // Then
            verify(locationRepository).delete(testLocation);
        }
    }

    @Nested
    @DisplayName("Search Location Tests")
    class SearchLocationTests {

        @Test
        @DisplayName("Should search locations successfully")
        void should_SearchLocations_When_UserHasAccess() {
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
            assertThat(result.getFirst().getId()).isEqualTo(locationId);
        }
    }

    @Nested
    @DisplayName("Name Availability Tests")
    class NameAvailabilityTests {

        @Test
        @DisplayName("Should return true when name is available")
        void should_ReturnTrue_When_NameIsAvailable() {
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
        }

        @Test
        @DisplayName("Should return false when name is not available")
        void should_ReturnFalse_When_NameIsNotAvailable() {
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
        }
    }

    @Nested
    @DisplayName("Access Control Tests")
    class AccessControlTests {

        @Test
        @DisplayName("Should throw AccessDeniedException when user is not household member")
        void should_ThrowAccessDeniedException_When_UserIsNotMember() {
            // Given
            when(userService.getCurrentUser(token)).thenReturn(testUser);
            when(householdRepository.existsById(householdId)).thenReturn(true);
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, userId))
                    .thenReturn(Optional.empty());

            // When & Then
            assertThrows(AccessDeniedException.class,
                    () -> locationService.getLocationsByHousehold(householdId, token));
        }
    }
}