package org.cubord.cubordbackend.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class PantryItemResponse {
    private UUID id;
    private UUID productId;
    private UUID locationId;
    private LocalDate expirationDate;
    private Integer quantity;
    private String unitOfMeasure;
    private String notes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}