
package org.cubord.cubordbackend.dto.householdInvitation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.cubord.cubordbackend.domain.HouseholdRole;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HouseholdInvitationUpdateRequest {
    private HouseholdRole proposedRole;
    private LocalDateTime expiresAt;
}
