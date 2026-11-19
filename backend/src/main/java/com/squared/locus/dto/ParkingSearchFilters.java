package com.squared.locus.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record ParkingSearchFilters(
        String location, OffsetDateTime startTime, OffsetDateTime endTime,
        BigDecimal maxPricePerHour, String parkingType, String destination) {
}
