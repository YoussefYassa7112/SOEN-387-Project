package com.locus.repository;

import com.locus.domain.ParkingSpot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ParkingSpotRepository extends JpaRepository<ParkingSpot, UUID> {
    List<ParkingSpot> findAllByOwnerIdOrderByCreatedAtDesc(UUID ownerId);

    Optional<ParkingSpot> findByIdAndActiveTrue(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from ParkingSpot p where p.id = :id and p.ownerId = :ownerId")
    Optional<ParkingSpot> findOwnedForUpdate(@Param("id") UUID id, @Param("ownerId") UUID ownerId);
}
