package com.squared.locus.dto;

import java.util.List;

public record ParkingSpotPageResponse(
        List<ParkingSpotResponse> items,
        int total,
        int limit,
        int offset
) {
}
