package org.cubord.cubordbackend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.Min;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdatePantryItemRequest {
    private UUID locationId;
    
    @Min(0)
    private Integer quantity;
    
    private String unitOfMeasure;
    private LocalDate expirationDate;
    private String notes;
}