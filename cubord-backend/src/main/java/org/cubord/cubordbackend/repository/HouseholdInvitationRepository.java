package org.cubord.cubordbackend.repository;

import org.cubord.cubordbackend.domain.InvitationStatus;
import org.cubord.cubordbackend.domain.HouseholdInvitation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    /**
     * Find invitations by email address (for users who don't have an account yet).
     */
    List<HouseholdInvitation> findByInvitedEmailIgnoreCaseAndStatus(String invitedEmail, InvitationStatus status);

    /**
     * Check if a pending invitation exists for an email address.
     */
    boolean existsByHouseholdIdAndInvitedEmailIgnoreCaseAndStatus(UUID householdId, String invitedEmail, InvitationStatus status);

    /**
     * Find all pending invitations for either a user ID or an email address.
     * Used during login/registration to find all relevant invitations.
     */
    @Query("SELECT i FROM HouseholdInvitation i WHERE i.status = :status AND " +
            "(i.invitedUser.id = :userId OR LOWER(i.invitedEmail) = LOWER(:email))")
    List<HouseholdInvitation> findPendingInvitationsForUserOrEmail(
            @Param("userId") UUID userId,
            @Param("email") String email,
            @Param("status") InvitationStatus status);

    /**
     * Link email-based invitations to a newly registered user.
     * Updates all pending email invitations to point to the new user account.
     */
    @Modifying
    @Query("UPDATE HouseholdInvitation i SET i.invitedUser = :user, i.invitedEmail = NULL, i.updatedAt = :now " +
            "WHERE LOWER(i.invitedEmail) = LOWER(:email) AND i.status = :status")
    int linkEmailInvitationsToUser(
            @Param("user") org.cubord.cubordbackend.domain.User user,
            @Param("email") String email,
            @Param("status") InvitationStatus status,
            @Param("now") LocalDateTime now);
}
