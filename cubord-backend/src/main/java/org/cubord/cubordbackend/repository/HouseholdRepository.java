package org.cubord.cubordbackend.repository;
import org.cubord.cubordbackend.domain.Household;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface HouseholdRepository extends JpaRepository<Household, UUID> {
    Optional<Household> findByName(String name);
    List<Household> findByNameContainingIgnoreCase(String namePattern);
    List<Household> findByCreatedAtAfter(LocalDateTime date);
    List<Household> findByMembersUserId(UUID userId);
    List<Household> findByMembersUserIdAndNameContainingIgnoreCase(UUID userId, String namePattern);
    boolean existsByName(String name);
}