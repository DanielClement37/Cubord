package org.cubord.cubordbackend.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreatePantryItemRequest {
    
    @NotNull(message = "Product ID is required")
    private UUID productId;
    
    @NotNull(message = "Location ID is required")
    private UUID locationId;
    
    private LocalDate expirationDate;
    private LocalDate purchaseDate;
    
    @Min(value = 0, message = "Quantity must be non-negative")
    private Integer quantity;
    
    private String unitOfMeasure;
    private String notes;
}