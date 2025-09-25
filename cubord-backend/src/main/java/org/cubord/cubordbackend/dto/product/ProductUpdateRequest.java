package org.cubord.cubordbackend.dto.product;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductUpdateRequest {
    
    @NotBlank(message = "Product name cannot be blank")
    private String name;
    
    private String brand;
    private String category;
    
    @Positive(message = "Default expiration days must be positive")
    private Integer defaultExpirationDays;
}
