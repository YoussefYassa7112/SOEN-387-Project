package com.squared.locus.dto;

import java.time.OffsetDateTime;

public record AvailabilityPeriodResponse(
        Long id,
        OffsetDateTime startTime,
        OffsetDateTime endTime
) {
}