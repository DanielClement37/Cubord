package org.cubord.cubordbackend.service;

import org.cubord.cubordbackend.domain.*;
import org.cubord.cubordbackend.dto.LocationRequest;
import org.cubord.cubordbackend.dto.LocationResponse;
import org.cubord.cubordbackend.dto.LocationUpdateRequest;
import org.cubord.cubordbackend.exception.ConflictException;
import org.cubord.cubordbackend.exception.NotFoundException;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
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
        // Reset all mocks before each test
        reset(locationRepository, householdRepository, householdMemberRepository, userService, token);
        
        userId = UUID.randomUUID();
        householdId = UUID.randomUUID();
        locationId = UUID.randomUUID();

        setupUser();
        setupHousehold();
        setupHouseholdMember();
        setupLocation();
        setupLocationRequest();
        setupLocationUpdateRequest();
    }

    private void setupUser() {
        testUser = new User();
        testUser.setId(userId);
        testUser.setUsername("testuser");
        testUser.setEmail("test@example.com");
        testUser.setDisplayName("Test User");
    }

    private void setupHousehold() {
        testHousehold = new Household();
        testHousehold.setId(householdId);
        testHousehold.setName("Test Household");
        testHousehold.setCreatedAt(LocalDateTime.now());
        testHousehold.setUpdatedAt(LocalDateTime.now());
    }

    private void setupHouseholdMember() {
        testMember = new HouseholdMember();
        testMember.setId(UUID.randomUUID());
        testMember.setUser(testUser);
        testMember.setHousehold(testHousehold);
        testMember.setRole(HouseholdRole.MEMBER);
        testMember.setCreatedAt(LocalDateTime.now());
        testMember.setUpdatedAt(LocalDateTime.now());
    }

    private void setupLocation() {
        testLocation = new Location();
        testLocation.setId(locationId);
        testLocation.setName("Kitchen");
        testLocation.setDescription("Main kitchen area");
        testLocation.setHousehold(testHousehold);
        testLocation.setCreatedAt(LocalDateTime.now());
        testLocation.setUpdatedAt(LocalDateTime.now());
    }

    private void setupLocationRequest() {
        locationRequest = LocationRequest.builder()
                .name("New Location")
                .description("A new storage location")
                .householdId(householdId)
                .build();
    }

    private void setupLocationUpdateRequest() {
        locationUpdateRequest = LocationUpdateRequest.builder()
                .name("Updated Location")
                .description("Updated description")
                .build();
    }

    private HouseholdMember createMemberWithRole(HouseholdRole role) {
        HouseholdMember member = new HouseholdMember();
        member.setId(UUID.randomUUID());
        member.setUser(testUser);
        member.setHousehold(testHousehold);
        member.setRole(role);
        member.setCreatedAt(LocalDateTime.now());
        member.setUpdatedAt(LocalDateTime.now());
        return member;
    }

    @Nested
    @DisplayName("Create Location Tests")
    class CreateLocationTests {

        @Test
        @DisplayName("Should create location successfully when user is household member")
        void should_CreateLocationSuccessfully_When_UserIsHouseholdMember() {
            // Given
            when(userService.getCurrentUser(token)).thenReturn(testUser);
            when(householdRepository.findById(householdId)).thenReturn(Optional.of(testHousehold));
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, userId))
                    .thenReturn(Optional.of(testMember));
            when(locationRepository.existsByHouseholdIdAndName(householdId, locationRequest.getName()))
                    .thenReturn(false);
            when(locationRepository.save(any(Location.class))).thenAnswer(invocation -> {
                Location savedLocation = invocation.getArgument(0);
                savedLocation.setId(locationId);
                return savedLocation;
            });

            // When
            LocationResponse result = locationService.createLocation(locationRequest, token);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getName()).isEqualTo(locationRequest.getName());
            assertThat(result.getDescription()).isEqualTo(locationRequest.getDescription());
            assertThat(result.getHouseholdId()).isEqualTo(householdId);
            
            verify(userService).getCurrentUser(token);
            verify(householdRepository).findById(householdId);
            verify(householdMemberRepository).findByHouseholdIdAndUserId(householdId, userId);
            verify(locationRepository).existsByHouseholdIdAndName(householdId, locationRequest.getName());
            verify(locationRepository).save(any(Location.class));
        }

        @Test
        @DisplayName("Should throw NotFoundException when household doesn't exist")
        void should_ThrowNotFoundException_When_HouseholdDoesntExist() {
            // Given
            when(userService.getCurrentUser(token)).thenReturn(testUser);
            when(householdRepository.findById(householdId)).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> locationService.createLocation(locationRequest, token))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("Household not found");
                    
            verify(userService).getCurrentUser(token);
            verify(householdRepository).findById(householdId);
            verifyNoInteractions(locationRepository);
        }

        @Test
        @DisplayName("Should throw AccessDeniedException when user is not household member")
        void should_ThrowAccessDeniedException_When_UserIsNotHouseholdMember() {
            // Given
            when(userService.getCurrentUser(token)).thenReturn(testUser);
            when(householdRepository.findById(householdId)).thenReturn(Optional.of(testHousehold));
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, userId))
                    .thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> locationService.createLocation(locationRequest, token))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("You don't have permission to access this resource");
                    
            verify(householdMemberRepository).findByHouseholdIdAndUserId(householdId, userId);
            verifyNoInteractions(locationRepository);
        }

        @Test
        @DisplayName("Should throw ConflictException when location name already exists")
        void should_ThrowConflictException_When_LocationNameAlreadyExists() {
            // Given
            when(userService.getCurrentUser(token)).thenReturn(testUser);
            when(householdRepository.findById(householdId)).thenReturn(Optional.of(testHousehold));
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, userId))
                    .thenReturn(Optional.of(testMember));
            when(locationRepository.existsByHouseholdIdAndName(householdId, locationRequest.getName()))
                    .thenReturn(true);

            // When & Then
            assertThatThrownBy(() -> locationService.createLocation(locationRequest, token))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("Location name already exists");
                    
            verify(locationRepository).existsByHouseholdIdAndName(householdId, locationRequest.getName());
            verify(locationRepository, never()).save(any(Location.class));
        }

        @Test
        @DisplayName("Should create location with null description")
        void should_CreateLocation_When_DescriptionIsNull() {
            // Given
            LocationRequest requestWithNullDescription = LocationRequest.builder()
                    .name("New Location")
                    .description(null)
                    .householdId(householdId)
                    .build();

            when(userService.getCurrentUser(token)).thenReturn(testUser);
            when(householdRepository.findById(householdId)).thenReturn(Optional.of(testHousehold));
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, userId))
                    .thenReturn(Optional.of(testMember));
            when(locationRepository.existsByHouseholdIdAndName(householdId, requestWithNullDescription.getName()))
                    .thenReturn(false);
            when(locationRepository.save(any(Location.class))).thenReturn(testLocation);

            // When & Then
            assertThatCode(() -> locationService.createLocation(requestWithNullDescription, token))
                    .doesNotThrowAnyException();
                    
            verify(locationRepository).save(any(Location.class));
        }

        @Test
        @DisplayName("Should create location with empty description")
        void should_CreateLocation_When_DescriptionIsEmpty() {
            // Given
            LocationRequest requestWithEmptyDescription = LocationRequest.builder()
                    .name("New Location")
                    .description("")
                    .householdId(householdId)
                    .build();

            when(userService.getCurrentUser(token)).thenReturn(testUser);
            when(householdRepository.findById(householdId)).thenReturn(Optional.of(testHousehold));
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, userId))
                    .thenReturn(Optional.of(testMember));
            when(locationRepository.existsByHouseholdIdAndName(householdId, requestWithEmptyDescription.getName()))
                    .thenReturn(false);
            when(locationRepository.save(any(Location.class))).thenReturn(testLocation);

            // When & Then
            assertThatCode(() -> locationService.createLocation(requestWithEmptyDescription, token))
                    .doesNotThrowAnyException();
                    
            verify(locationRepository).save(any(Location.class));
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException when token is null")
        void should_ThrowIllegalArgumentException_When_TokenIsNull() {
            // When & Then
            assertThatThrownBy(() -> locationService.createLocation(locationRequest, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException when request is null")
        void should_ThrowIllegalArgumentException_When_RequestIsNull() {
            // When & Then
            assertThatThrownBy(() -> locationService.createLocation(null, token))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("Get Location By ID Tests")
    class GetLocationByIdTests {

        @Test
        @DisplayName("Should get location by ID when user has access")
        void should_GetLocationById_When_UserHasAccess() {
            // Given
            when(userService.getCurrentUser(token)).thenReturn(testUser);
            when(locationRepository.findById(locationId)).thenReturn(Optional.of(testLocation));
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, userId))
                    .thenReturn(Optional.of(testMember));

            // When
            LocationResponse result = locationService.getLocationById(locationId, token);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(locationId);
            assertThat(result.getName()).isEqualTo(testLocation.getName());
            assertThat(result.getDescription()).isEqualTo(testLocation.getDescription());
            
            verify(userService).getCurrentUser(token);
            verify(locationRepository).findById(locationId);
            verify(householdMemberRepository).findByHouseholdIdAndUserId(householdId, userId);
        }

        @Test
        @DisplayName("Should throw NotFoundException when location doesn't exist")
        void should_ThrowNotFoundException_When_LocationDoesntExist() {
            // Given
            when(userService.getCurrentUser(token)).thenReturn(testUser);
            when(locationRepository.findById(locationId)).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> locationService.getLocationById(locationId, token))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("Location not found");
                    
            verify(locationRepository).findById(locationId);
            verifyNoInteractions(householdMemberRepository);
        }

        @Test
        @DisplayName("Should throw AccessDeniedException when user doesn't have access")
        void should_ThrowAccessDeniedException_When_UserDoesntHaveAccess() {
            // Given
            when(userService.getCurrentUser(token)).thenReturn(testUser);
            when(locationRepository.findById(locationId)).thenReturn(Optional.of(testLocation));
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, userId))
                    .thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> locationService.getLocationById(locationId, token))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("You don't have permission to access this resource");
                    
            verify(householdMemberRepository).findByHouseholdIdAndUserId(householdId, userId);
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException when location ID is null")
        void should_ThrowIllegalArgumentException_When_LocationIdIsNull() {
            // When & Then
            assertThatThrownBy(() -> locationService.getLocationById(null, token))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("Get Locations By Household Tests")
    class GetLocationsByHouseholdTests {

        @Test
        @DisplayName("Should get all locations by household when user is member")
        void should_GetAllLocationsByHousehold_When_UserIsMember() {
            // Given
            List<Location> locations = Arrays.asList(testLocation);
            when(userService.getCurrentUser(token)).thenReturn(testUser);
            when(householdRepository.findById(householdId)).thenReturn(Optional.of(testHousehold));
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, userId))
                    .thenReturn(Optional.of(testMember));
            when(locationRepository.findByHouseholdId(householdId)).thenReturn(locations);

            // When
            List<LocationResponse> result = locationService.getLocationsByHousehold(householdId, token);

            // Then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getId()).isEqualTo(locationId);
            assertThat(result.get(0).getName()).isEqualTo(testLocation.getName());
            
            verify(householdRepository).findById(householdId);
            verify(householdMemberRepository).findByHouseholdIdAndUserId(householdId, userId);
            verify(locationRepository).findByHouseholdId(householdId);
        }

        @Test
        @DisplayName("Should get locations with sorting")
        void should_GetLocationsWithSorting_When_SortProvided() {
            // Given
            Sort sort = Sort.by("name");
            List<Location> locations = Arrays.asList(testLocation);
            when(userService.getCurrentUser(token)).thenReturn(testUser);
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, userId))
                    .thenReturn(Optional.of(testMember));
            when(locationRepository.findByHouseholdId(householdId, sort)).thenReturn(locations);

            // When
            List<LocationResponse> result = locationService.getLocationsByHousehold(householdId, sort, token);

            // Then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getId()).isEqualTo(locationId);
            
            verify(householdMemberRepository).findByHouseholdIdAndUserId(householdId, userId);
            verify(locationRepository).findByHouseholdId(householdId, sort);
        }

        @Test
        @DisplayName("Should get locations with pagination")
        void should_GetLocationsWithPagination_When_PageableProvided() {
            // Given
            Pageable pageable = PageRequest.of(0, 10, Sort.by("name"));
            Page<Location> locationPage = new PageImpl<>(Arrays.asList(testLocation));
            when(userService.getCurrentUser(token)).thenReturn(testUser);
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, userId))
                    .thenReturn(Optional.of(testMember));
            when(locationRepository.findByHouseholdId(householdId, pageable)).thenReturn(locationPage);

            // When
            Page<LocationResponse> result = locationService.getLocationsByHousehold(householdId, pageable, token);

            // Then
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getId()).isEqualTo(locationId);
            
            verify(locationRepository).findByHouseholdId(householdId, pageable);
        }

        @Test
        @DisplayName("Should return empty list when household has no locations")
        void should_ReturnEmptyList_When_HouseholdHasNoLocations() {
            // Given
            when(userService.getCurrentUser(token)).thenReturn(testUser);
            when(householdRepository.findById(householdId)).thenReturn(Optional.of(testHousehold));
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, userId))
                    .thenReturn(Optional.of(testMember));
            when(locationRepository.findByHouseholdId(householdId)).thenReturn(Collections.emptyList());

            // When
            List<LocationResponse> result = locationService.getLocationsByHousehold(householdId, token);

            // Then
            assertThat(result).isEmpty();
            
            verify(locationRepository).findByHouseholdId(householdId);
        }

        @Test
        @DisplayName("Should throw NotFoundException when household doesn't exist")
        void should_ThrowNotFoundException_When_HouseholdDoesntExistForGetLocations() {
            // Given
            when(userService.getCurrentUser(token)).thenReturn(testUser);
            when(householdRepository.findById(householdId)).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> locationService.getLocationsByHousehold(householdId, token))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("Household not found");
                    
            verify(householdRepository).findById(householdId);
            verifyNoInteractions(locationRepository);
        }

        @Test
        @DisplayName("Should throw AccessDeniedException when user is not household member")
        void should_ThrowAccessDeniedException_When_UserIsNotHouseholdMemberForGetLocations() {
            // Given
            when(userService.getCurrentUser(token)).thenReturn(testUser);
            when(householdRepository.findById(householdId)).thenReturn(Optional.of(testHousehold));
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, userId))
                    .thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> locationService.getLocationsByHousehold(householdId, token))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("You don't have permission to access this resource");
                    
            verify(householdMemberRepository).findByHouseholdIdAndUserId(householdId, userId);
            verifyNoInteractions(locationRepository);
        }
    }

    @Nested
    @DisplayName("Update Location Tests")
    class UpdateLocationTests {

        @Test
        @DisplayName("Should update location when user is household member")
        void should_UpdateLocation_When_UserIsHouseholdMember() {
            // Given
            Location updatedLocation = new Location();
            updatedLocation.setId(locationId);
            updatedLocation.setName(locationUpdateRequest.getName());
            updatedLocation.setDescription(locationUpdateRequest.getDescription());
            updatedLocation.setHousehold(testHousehold);
            updatedLocation.setCreatedAt(testLocation.getCreatedAt());
            updatedLocation.setUpdatedAt(LocalDateTime.now());

            when(userService.getCurrentUser(token)).thenReturn(testUser);
            when(locationRepository.findById(locationId)).thenReturn(Optional.of(testLocation));
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, userId))
                    .thenReturn(Optional.of(testMember));
            when(locationRepository.save(any(Location.class))).thenReturn(updatedLocation);

            // When
            LocationResponse result = locationService.updateLocation(locationId, locationUpdateRequest, token);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getName()).isEqualTo(locationUpdateRequest.getName());
            assertThat(result.getDescription()).isEqualTo(locationUpdateRequest.getDescription());
            
            verify(locationRepository).findById(locationId);
            verify(householdMemberRepository).findByHouseholdIdAndUserId(householdId, userId);
            verify(locationRepository).save(any(Location.class));
        }

        @Test
        @DisplayName("Should update location with only name change")
        void should_UpdateLocationWithOnlyNameChange_When_DescriptionIsNull() {
            // Given
            LocationUpdateRequest requestWithNameOnly = LocationUpdateRequest.builder()
                    .name("Updated Name Only")
                    .description(null)
                    .build();

            Location updatedLocation = new Location();
            updatedLocation.setId(locationId);
            updatedLocation.setName("Updated Name Only");
            updatedLocation.setDescription(testLocation.getDescription()); // Keep original description
            updatedLocation.setHousehold(testHousehold);

            when(userService.getCurrentUser(token)).thenReturn(testUser);
            when(locationRepository.findById(locationId)).thenReturn(Optional.of(testLocation));
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, userId))
                    .thenReturn(Optional.of(testMember));
            when(locationRepository.save(any(Location.class))).thenReturn(updatedLocation);

            // When
            LocationResponse result = locationService.updateLocation(locationId, requestWithNameOnly, token);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getName()).isEqualTo("Updated Name Only");
            
            verify(locationRepository).save(any(Location.class));
        }

        @Test
        @DisplayName("Should update location with only description change")
        void should_UpdateLocationWithOnlyDescriptionChange_When_NameIsNull() {
            // Given
            LocationUpdateRequest requestWithDescriptionOnly = LocationUpdateRequest.builder()
                    .name(null)
                    .description("Updated description only")
                    .build();

            Location updatedLocation = new Location();
            updatedLocation.setId(locationId);
            updatedLocation.setName(testLocation.getName()); // Keep original name
            updatedLocation.setDescription("Updated description only");
            updatedLocation.setHousehold(testHousehold);

            when(userService.getCurrentUser(token)).thenReturn(testUser);
            when(locationRepository.findById(locationId)).thenReturn(Optional.of(testLocation));
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, userId))
                    .thenReturn(Optional.of(testMember));
            when(locationRepository.save(any(Location.class))).thenReturn(updatedLocation);

            // When
            LocationResponse result = locationService.updateLocation(locationId, requestWithDescriptionOnly, token);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getDescription()).isEqualTo("Updated description only");
            
            verify(locationRepository).save(any(Location.class));
        }

        @Test
        @DisplayName("Should throw NotFoundException when location doesn't exist")
        void should_ThrowNotFoundException_When_LocationDoesntExistForUpdate() {
            // Given
            when(userService.getCurrentUser(token)).thenReturn(testUser);
            when(locationRepository.findById(locationId)).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> locationService.updateLocation(locationId, locationUpdateRequest, token))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("Location not found");
                    
            verify(locationRepository).findById(locationId);
            verifyNoInteractions(householdMemberRepository);
            verify(locationRepository, never()).save(any(Location.class));
        }

        @Test
        @DisplayName("Should throw AccessDeniedException when user doesn't have access")
        void should_ThrowAccessDeniedException_When_UserDoesntHaveAccessForUpdate() {
            // Given
            when(userService.getCurrentUser(token)).thenReturn(testUser);
            when(locationRepository.findById(locationId)).thenReturn(Optional.of(testLocation));
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, userId))
                    .thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> locationService.updateLocation(locationId, locationUpdateRequest, token))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("You don't have permission to access this resource");
                    
            verify(householdMemberRepository).findByHouseholdIdAndUserId(householdId, userId);
            verify(locationRepository, never()).save(any(Location.class));
        }

        @Test
        @DisplayName("Should throw ConflictException when new name conflicts with existing location")
        void should_ThrowConflictException_When_NewNameConflictsWithExistingLocation() {
            // Given
            when(userService.getCurrentUser(token)).thenReturn(testUser);
            when(locationRepository.findById(locationId)).thenReturn(Optional.of(testLocation));
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, userId))
                    .thenReturn(Optional.of(testMember));
            when(locationRepository.existsByHouseholdIdAndName(householdId, locationUpdateRequest.getName()))
                    .thenReturn(true);

            // When & Then
            assertThatThrownBy(() -> locationService.updateLocation(locationId, locationUpdateRequest, token))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("Location name already exists");
                    
            verify(locationRepository).existsByHouseholdIdAndName(householdId, locationUpdateRequest.getName());
            verify(locationRepository, never()).save(any(Location.class));
        }

        @Test
        @DisplayName("Should allow update when name conflicts with same location")
        void should_AllowUpdate_When_NameConflictsWithSameLocation() {
            // Given - Update with same name should be allowed
            LocationUpdateRequest sameNameRequest = LocationUpdateRequest.builder()
                    .name(testLocation.getName()) // Same name as current location
                    .description("Updated description")
                    .build();

            when(userService.getCurrentUser(token)).thenReturn(testUser);
            when(locationRepository.findById(locationId)).thenReturn(Optional.of(testLocation));
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, userId))
                    .thenReturn(Optional.of(testMember));
            when(locationRepository.save(any(Location.class))).thenReturn(testLocation);

            // When & Then
            assertThatCode(() -> locationService.updateLocation(locationId, sameNameRequest, token))
                    .doesNotThrowAnyException();
                    
            verify(locationRepository).save(any(Location.class));
        }
    }

    @Nested
    @DisplayName("Edge Cases and Error Handling")
    class EdgeCasesAndErrorHandling {

        @Test
        @DisplayName("Should handle very long location names")
        void should_HandleVeryLongLocationNames() {
            // Given
            String longName = "A".repeat(300); // Assuming max length is 255
            LocationRequest longNameRequest = LocationRequest.builder()
                    .name(longName)
                    .description("Test description")
                    .householdId(householdId)
                    .build();

            when(userService.getCurrentUser(token)).thenReturn(testUser);
            when(householdRepository.findById(householdId)).thenReturn(Optional.of(testHousehold));
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, userId))
                    .thenReturn(Optional.of(testMember));

            // The service should either handle this gracefully or throw a validation exception
            // This test assumes validation happens at the service layer
            assertThatCode(() -> locationService.createLocation(longNameRequest, token))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should handle location names with special characters")
        void should_HandleLocationNamesWithSpecialCharacters() {
            // Given
            LocationRequest specialCharRequest = LocationRequest.builder()
                    .name("Location with éñøügh spëcîàl characters! @#$%")
                    .description("Test description")
                    .householdId(householdId)
                    .build();

            when(userService.getCurrentUser(token)).thenReturn(testUser);
            when(householdRepository.findById(householdId)).thenReturn(Optional.of(testHousehold));
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, userId))
                    .thenReturn(Optional.of(testMember));
            when(locationRepository.existsByHouseholdIdAndName(householdId, specialCharRequest.getName()))
                    .thenReturn(false);
            when(locationRepository.save(any(Location.class))).thenReturn(testLocation);

            // When & Then
            assertThatCode(() -> locationService.createLocation(specialCharRequest, token))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should handle empty location name")
        void should_HandleEmptyLocationName() {
            // Given
            LocationRequest emptyNameRequest = LocationRequest.builder()
                    .name("")
                    .description("Test description")
                    .householdId(householdId)
                    .build();

            // This should be caught by validation, but testing service behavior
            when(userService.getCurrentUser(token)).thenReturn(testUser);

            // When & Then - This might throw a validation exception or be handled gracefully
            assertThatCode(() -> locationService.createLocation(emptyNameRequest, token))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should handle concurrent access scenarios")
        void should_HandleConcurrentAccessScenarios() {
            // Given - Simulate a scenario where location is deleted between find and save
            when(userService.getCurrentUser(token)).thenReturn(testUser);
            when(locationRepository.findById(locationId)).thenReturn(Optional.of(testLocation));
            when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, userId))
                    .thenReturn(Optional.of(testMember));
            when(locationRepository.save(any(Location.class)))
                    .thenThrow(new RuntimeException("Concurrent modification"));

            // When & Then
            assertThatThrownBy(() -> locationService.updateLocation(locationId, locationUpdateRequest, token))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Concurrent modification");
        }
    }
}