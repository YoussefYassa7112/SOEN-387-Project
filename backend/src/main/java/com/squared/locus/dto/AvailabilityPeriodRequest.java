package com.locus.api.dto;

import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;

public record AvailabilityPeriodRequest(
        @NotNull OffsetDateTime startTime,
        @NotNull OffsetDateTime endTime
) {
}