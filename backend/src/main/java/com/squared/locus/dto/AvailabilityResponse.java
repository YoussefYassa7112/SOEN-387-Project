package com.squared.locus.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AvailabilityResponse(
        UUID spotId,
        OffsetDateTime startTime,
        OffsetDateTime endTime,
        boolean available,
        String reason
) {
}
