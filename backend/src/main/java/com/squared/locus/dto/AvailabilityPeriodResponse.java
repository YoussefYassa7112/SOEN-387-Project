package com.locus.api.dto;

import java.time.OffsetDateTime;

public record AvailabilityPeriodResponse(
        Long id,
        OffsetDateTime startTime,
        OffsetDateTime endTime
) {
}