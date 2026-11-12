package com.squared.locus.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.squared.locus.model.ParkingSpot;

import jakarta.persistence.LockModeType;

public interface ParkingSpotRepository extends JpaRepository<ParkingSpot, UUID> {
    // Added for pessimistic locking to prevent race conditions when updating a parking spot.
    // While request A locks the parking spot, request B will wait until A is done and the lock is released.    
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from ParkingSpot p where p.id = :id")
    // Loads a parking spot while placing the database write lock on that row
    // Used to create a reservation
    Optional<ParkingSpot> findForUpdate(@Param("id") UUID id);

    List<ParkingSpot> findAllByOwnerIdOrderByCreatedAtDesc(UUID ownerId);

    Optional<ParkingSpot> findByIdAndActiveTrue(UUID id);

    // Loads each spot together with its windows, so there is no extra query per spot.
    @Query("select distinct p from ParkingSpot p left join fetch p.availabilityWindows "
            + "where p.active = true order by p.createdAt desc, p.id")
    List<ParkingSpot> findAllActiveWithWindows();

    // Intervals are half-open, so a booking ending at 8 PM does not clash with one starting at 8 PM.
    @Query(value = "select distinct r.spot_id from reservations r "
            + "where r.status in ('PENDING_PAYMENT', 'CONFIRMED') "
            + "and r.start_time < :endTime and r.end_time > :startTime", nativeQuery = true)
    List<UUID> findReservedSpotIds(@Param("startTime") OffsetDateTime startTime,
                                   @Param("endTime") OffsetDateTime endTime);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from ParkingSpot p where p.id = :id and p.ownerId = :ownerId")
    Optional<ParkingSpot> findOwnedForUpdate(@Param("id") UUID id, @Param("ownerId") UUID ownerId);
}
