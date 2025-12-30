package org.cubord.cubordbackend.repository;
import org.cubord.cubordbackend.domain.Household;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface HouseholdRepository extends JpaRepository<Household, UUID> {
    Optional<Household> findByName(String name);
    List<Household> findByNameContainingIgnoreCase(String namePattern);
    List<Household> findByMembersUserId(UUID userId);
    boolean existsByName(String name);

    boolean existsByNameAndMembersUserId(String name, UUID userId);
    List<Household> findAllByMembersUserId(UUID userId);
    List<Household> findAllByNameContainingIgnoreCaseAndMembersUserId(String namePattern, UUID userId);
}