package org.cubord.cubordbackend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.cubord.cubordbackend.domain.PantryItem;

import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PantryItemResponse {
    private UUID id;
    private UUID productId;
    private UUID locationId;
    private LocalDate expirationDate;
    private Integer quantity;
    private String unitOfMeasure;
    private String notes;

    public PantryItemResponse(PantryItem pantryItem) {
        this.id = pantryItem.getId();
        this.productId = pantryItem.getProduct().getId();
        this.locationId = pantryItem.getLocation().getId();
        this.expirationDate = pantryItem.getExpirationDate();
        this.quantity = pantryItem.getQuantity();
        this.unitOfMeasure = pantryItem.getUnitOfMeasure();
        this.notes = pantryItem.getNotes();
    }
}