package org.cubord.cubordbackend.security;

import lombok.RequiredArgsConstructor;
import org.cubord.cubordbackend.domain.Household;
import org.cubord.cubordbackend.repository.HouseholdMemberRepository;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class HouseholdPermissionEvaluator implements PermissionEvaluator {
    private final HouseholdMemberRepository householdMemberRepository;

    @Override
    public boolean hasPermission(Authentication authentication, Object targetDomainObject, Object permission) {
        if (!(authentication instanceof JwtAuthenticationToken) || !(targetDomainObject instanceof Household)) {
            return false;
        }

        JwtAuthenticationToken jwtToken = (JwtAuthenticationToken) authentication;
        Household household = (Household) targetDomainObject;
        UUID userId = UUID.fromString(jwtToken.getName());

        return householdMemberRepository.findByUserId(userId).stream()
            .anyMatch(member -> member.getHousehold().getId().equals(household.getId()));
    }

    @Override
    public boolean hasPermission(Authentication authentication, Serializable targetId, String targetType, Object permission) {
        if (!(authentication instanceof JwtAuthenticationToken) || !"Household".equals(targetType)) {
            return false;
        }

        JwtAuthenticationToken jwtToken = (JwtAuthenticationToken) authentication;
        UUID userId = UUID.fromString(jwtToken.getName());
        UUID householdId = (UUID) targetId;

        return householdMemberRepository.findByUserId(userId).stream()
            .anyMatch(member -> member.getHousehold().getId().equals(householdId));
    }
}