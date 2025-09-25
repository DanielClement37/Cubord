package org.cubord.cubordbackend.dto.householdMember;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.cubord.cubordbackend.domain.HouseholdRole;

/**
 * Request body for changing a member’s role in a household.
 * Example JSON: { "role": "ADMIN" }
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HouseholdMemberRoleUpdateRequest {

    @NotNull(message = "Role is required")
    private HouseholdRole role;
}
