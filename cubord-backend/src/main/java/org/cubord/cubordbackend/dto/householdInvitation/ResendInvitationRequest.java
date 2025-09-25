
package org.cubord.cubordbackend.dto.householdInvitation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResendInvitationRequest {
    private LocalDateTime expiresAt; // Optional: new expiry date
}
