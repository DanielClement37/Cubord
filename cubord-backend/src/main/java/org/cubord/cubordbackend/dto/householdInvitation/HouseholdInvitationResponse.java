package org.cubord.cubordbackend.dto.householdInvitation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.cubord.cubordbackend.domain.HouseholdRole;
import org.cubord.cubordbackend.domain.InvitationStatus;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HouseholdInvitationResponse {
    private UUID id;
    private UUID invitedUserId;
    private String invitedUserEmail;
    private String invitedUserName;
    private UUID householdId;
    private String householdName;
    private UUID invitedByUserId;
    private String invitedByUserName;
    private HouseholdRole proposedRole;
    private InvitationStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
}
