package org.cubord.cubordbackend.dto.pantryItem;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.cubord.cubordbackend.dto.location.LocationResponse;
import org.cubord.cubordbackend.dto.product.ProductResponse;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PantryItemResponse {
    private UUID id;
    private ProductResponse product;
    private LocationResponse location;
    private LocalDate expirationDate;
    private Integer quantity;
    private String unitOfMeasure;
    private String notes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}