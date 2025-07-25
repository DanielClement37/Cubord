package org.cubord.cubordbackend.dto;

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
public class ProductRequest {
    
    @NotBlank(message = "UPC cannot be blank")
    private String upc;
    
    @NotBlank(message = "Product name cannot be blank")
    private String name;
    
    private String brand;
    private String category;
    
    @Positive(message = "Default expiration days must be positive")
    private Integer defaultExpirationDays;
}
