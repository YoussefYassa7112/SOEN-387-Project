package com.squared.locus.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

// Optional fields that are not set (for example no photoUrl) are left out of the JSON,
// because openapi.yaml says they are strings, not null.
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ParkingSpotResponse(
        UUID spotId,
        UUID ownerId,
        String title,
        String description,
        String address,
        String photoUrl,
        BigDecimal pricePerHour,
        String parkingType,
        String restrictions,
        boolean active,
        OffsetDateTime createdAt,
        List<AvailabilityPeriodResponse> availabilityWindows
) {
}