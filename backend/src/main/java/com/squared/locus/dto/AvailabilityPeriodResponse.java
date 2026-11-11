package com.squared.locus.dto;

import java.time.OffsetDateTime;

public record AvailabilityPeriodResponse(
        OffsetDateTime startTime,
        OffsetDateTime endTime
) {
}
