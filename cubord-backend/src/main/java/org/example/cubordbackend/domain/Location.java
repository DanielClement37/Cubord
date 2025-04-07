package org.example.cubordbackend.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "locations")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Location {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private UUID id;

    // If you had a Household entity, you'd reference it:
    // @ManyToOne(fetch = FetchType.LAZY)
    // @JoinColumn(name = "household_id")
    // private Household household;

    private UUID householdId;

    private String name;
    private String locationType;

    private UUID parentLocationId;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    public void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}