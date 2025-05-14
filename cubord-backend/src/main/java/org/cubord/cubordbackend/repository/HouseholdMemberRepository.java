package org.cubord.cubordbackend.repository;

import org.cubord.cubordbackend.domain.HouseholdMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface HouseholdMemberRepository extends JpaRepository<HouseholdMember, UUID> {
    List<HouseholdMember> findByUserId(UUID userId);
    List<HouseholdMember> findByHouseholdId(UUID householdId);
    Optional<HouseholdMember> findByHouseholdIdAndUserId(UUID householdId, UUID userId);

}