package com.squared.locus.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.squared.locus.model.Reservation;

public interface ReservationRepository extends JpaRepository<Reservation, UUID> {
    // Answers: Does this parking spot already have a confirmed reservation, or an unpaid one whose
    // hold has not expired yet (see PaymentHold), that overlaps the requested time?
    @Query(value = "select exists(select 1 from reservations where spot_id = :spotId "
            + "and (status = 'CONFIRMED' or (status = 'PENDING_PAYMENT' and created_at > :holdCutoff)) "
            + "and start_time < :endTime and end_time > :startTime)", nativeQuery = true)
    boolean hasOverlap(@Param("spotId") UUID spotId,
                       @Param("startTime") OffsetDateTime startTime,
                       @Param("endTime") OffsetDateTime endTime,
                       @Param("holdCutoff") OffsetDateTime holdCutoff);

    // LOCUS-30: all reservations of one driver, the latest booking times first.
    // Spring builds the query from the method name.
    List<Reservation> findAllByUserIdOrderByStartTimeDesc(UUID userId);
}
