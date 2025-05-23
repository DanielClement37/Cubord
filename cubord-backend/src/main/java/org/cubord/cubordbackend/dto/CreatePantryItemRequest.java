package org.cubord.cubordbackend.dto;

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
    private UUID productId;
    private UUID locationId;
    private LocalDate expirationDate;
    private LocalDate purchaseDate;
    private Integer quantity;
    private String unitOfMeasure;
    private String notes;
}