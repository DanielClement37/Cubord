package org.cubord.cubordbackend.service;

import lombok.RequiredArgsConstructor;
import org.cubord.cubordbackend.domain.Household;
import org.cubord.cubordbackend.domain.HouseholdRole;
import org.cubord.cubordbackend.dto.HouseholdRequest;
import org.cubord.cubordbackend.dto.HouseholdResponse;
import org.cubord.cubordbackend.repository.HouseholdMemberRepository;
import org.cubord.cubordbackend.repository.HouseholdRepository;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class HouseholdService {

    private final HouseholdRepository householdRepository;
    private final HouseholdMemberRepository householdMemberRepository;
    private final UserService userService;

    @Transactional
    public HouseholdResponse createHousehold(HouseholdRequest request, JwtAuthenticationToken token) {
        return new HouseholdResponse(); // Stub implementation
    }

    @Transactional(readOnly = true)
    public HouseholdResponse getHouseholdById(UUID householdId, JwtAuthenticationToken token) {
        return new HouseholdResponse(); // Stub implementation
    }

    @Transactional(readOnly = true)
    public List<HouseholdResponse> getUserHouseholds(JwtAuthenticationToken token) {
        return Collections.emptyList(); // Stub implementation
    }

    @Transactional
    public HouseholdResponse updateHousehold(UUID householdId, HouseholdRequest request, JwtAuthenticationToken token) {
        return new HouseholdResponse(); // Stub implementation
    }

    @Transactional
    public void deleteHousehold(UUID householdId, JwtAuthenticationToken token) {
        // Stub implementation
    }

    @Transactional
    public void leaveHousehold(UUID householdId, JwtAuthenticationToken token) {
        // Stub implementation
    }

    @Transactional
    public void transferOwnership(UUID householdId, UUID newOwnerId, JwtAuthenticationToken token) {
        // Stub implementation
    }

    @Transactional(readOnly = true)
    public List<HouseholdResponse> searchHouseholds(String searchTerm, JwtAuthenticationToken token) {
        return Collections.emptyList(); // Stub implementation
    }

    @Transactional
    public void changeMemberRole(UUID householdId, UUID memberId, HouseholdRole role, JwtAuthenticationToken token) {
        // Stub implementation
    }
}