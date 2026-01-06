package org.cubord.cubordbackend.dto.product;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
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
    @Pattern(regexp = "^[0-9]+$", message = "UPC must contain digits only")
    @Size(min = 8, max = 14, message = "UPC must be between 8 and 14 digits")
    private String upc;
    
    @NotBlank(message = "Product name cannot be blank")
    private String name;
    
    private String brand;
    private String category;
    
    @Positive(message = "Default expiration days must be positive")
    private Integer defaultExpirationDays;
}
