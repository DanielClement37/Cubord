package org.cubord.cubordbackend.repository;

import org.cubord.cubordbackend.domain.InvitationStatus;
import org.cubord.cubordbackend.domain.HouseholdInvitation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface HouseholdInvitationRepository extends JpaRepository<HouseholdInvitation, UUID> {
    List<HouseholdInvitation> findByHouseholdId(UUID householdId);
    List<HouseholdInvitation> findByHouseholdIdAndStatus(UUID householdId, InvitationStatus status);
    List<HouseholdInvitation> findByInvitedUserIdAndStatus(UUID invitedUserId, InvitationStatus status);
    boolean existsByHouseholdIdAndInvitedUserIdAndStatus(UUID householdId, UUID invitedUserId, InvitationStatus status);
    List<HouseholdInvitation> findByStatusAndExpiresAtBefore(InvitationStatus invitationStatus, LocalDateTime currentTime);
}
