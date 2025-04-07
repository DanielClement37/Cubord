package org.example.cubordbackend.dto;


import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

@Data
public class CreatePantryItemRequest {
    private UUID productId;
    private UUID locationId;
    private LocalDate expirationDate;
    private LocalDate purchaseDate;
    private Integer quantity;
    private String unitOfMeasure;
    private String notes;
}