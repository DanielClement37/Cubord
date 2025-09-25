package org.cubord.cubordbackend.dto.product;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.cubord.cubordbackend.domain.ProductDataSource;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductResponse {
    
    private UUID id;
    private String upc;
    private String name;
    private String brand;
    private String category;
    private Integer defaultExpirationDays;
    private ProductDataSource dataSource;
    private Boolean requiresApiRetry;
    private Integer retryAttempts;
    private LocalDateTime lastRetryAttempt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
