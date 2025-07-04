package org.cubord.cubordbackend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LocationResponse {
    
    private UUID id;
    private String name;
    private String description;
    private UUID householdId;
    private String householdName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
