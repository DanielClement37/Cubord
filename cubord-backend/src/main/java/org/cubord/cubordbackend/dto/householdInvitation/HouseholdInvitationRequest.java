package org.cubord.cubordbackend.dto.householdInvitation;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.cubord.cubordbackend.domain.HouseholdRole;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HouseholdInvitationRequest {
    @Email(message = "Valid email is required")
    private String invitedUserEmail;
    
    private UUID invitedUserId; // Optional: if inviting by user ID instead of email
    
    @NotNull(message = "Role is required")
    private HouseholdRole proposedRole;
    
    private LocalDateTime expiresAt; // Optional: custom expiry date
}
