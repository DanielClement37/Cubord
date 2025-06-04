package org.cubord.cubordbackend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserUpdateRequest {
    
    @Size(min = 2, max = 50, message = "Display name must be between 2 and 50 characters")
    private String displayName;
    
    @Email(message = "Email must be valid")
    private String email;
    
    // This field is included for completeness but should be ignored during updates
    private String username;
}