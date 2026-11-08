package com.squared.locus.repository;

import com.squared.locus.model.ParkingSpot;
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

    // Loads each spot together with its windows, so there is no extra query per spot.
    @Query("select distinct p from ParkingSpot p left join fetch p.availabilityWindows "
            + "where p.active = true order by p.createdAt desc, p.id")
    List<ParkingSpot> findAllActiveWithWindows();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from ParkingSpot p where p.id = :id and p.ownerId = :ownerId")
    Optional<ParkingSpot> findOwnedForUpdate(@Param("id") UUID id, @Param("ownerId") UUID ownerId);
}
