package org.cubord.cubordbackend.repository;

import org.cubord.cubordbackend.domain.Location;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface LocationRepository extends JpaRepository<Location, UUID> {
    // e.g., List<Location> findByHouseholdId(Long householdId);
}
