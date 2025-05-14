package org.cubord.cubordbackend.dto;

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
public class HouseholdMemberResponse {
    private UUID id;
    private UUID userId;
    private String username;
    private UUID householdId;
    private String householdName;
    private HouseholdRole role;
    private LocalDateTime createdAt;
}