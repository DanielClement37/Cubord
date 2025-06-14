package org.cubord.cubordbackend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HouseholdRequest {
    @NotBlank(message = "Household name is required")
    @Size(min = 2, max = 50, message = "Household name must be between 2 and 50 characters")
    private String name;

}