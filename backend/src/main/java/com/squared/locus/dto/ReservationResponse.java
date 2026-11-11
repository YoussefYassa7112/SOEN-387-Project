package com.squared.locus.dto;

import com.squared.locus.model.Reservation;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

// LOCUS-27: the driver gets the reservation ID and status back.
public record ReservationResponse(
        UUID reservationId,
        UUID spotId,
        UUID userId,
        OffsetDateTime startTime,
        OffsetDateTime endTime,
        String status,
        BigDecimal totalPrice,
        String currency,
        OffsetDateTime createdAt
) {
    public static ReservationResponse from(Reservation reservation) {
        return new ReservationResponse(
                reservation.getId(),
                reservation.getSpotId(),
                reservation.getUserId(),
                reservation.getStartTime(),
                reservation.getEndTime(),
                reservation.getStatus().getApiValue(),
                reservation.getTotalPrice(),
                reservation.getCurrency(),
                reservation.getCreatedAt()
        );
    }
}
