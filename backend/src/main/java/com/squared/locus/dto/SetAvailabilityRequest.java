package com.locus.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record SetAvailabilityRequest(
        @NotEmpty List<@Valid AvailabilityPeriodRequest> periods
) {
}