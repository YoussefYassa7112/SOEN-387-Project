package com.squared.locus.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record ParkingSpotResponse(
        UUID id,
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