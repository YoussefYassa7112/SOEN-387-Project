package com.squared.locus.dto;

import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;

// No userId here: the driver is taken from the token, never from the request body.
public record CreateReservationRequest(
        @NotNull OffsetDateTime startTime,
        @NotNull OffsetDateTime endTime
) {
}
