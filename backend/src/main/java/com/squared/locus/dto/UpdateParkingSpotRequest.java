package com.squared.locus.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public record UpdateParkingSpotRequest(
        @Size(min = 1, max = 120) String title,
        @Size(max = 2000) String description,
        @Size(min = 1, max = 300) String address,
        @Pattern(regexp = "^https?://\\S+$", message = "photoUrl must be an http(s) URL") String photoUrl,
        @DecimalMin(value = "0.00", inclusive = false) @DecimalMax("99999999.99")
        @Digits(integer = 8, fraction = 2) BigDecimal pricePerHour,
        @Pattern(regexp = "driveway|privateLot|garage|other") String parkingType,
        @Size(max = 1000) String restrictions,
        @Size(min = 1) List<@Valid AvailabilityPeriodRequest> availabilityWindows
) {
        public boolean hasNoFields() {
                return title == null && description == null && address == null && photoUrl == null
                        && pricePerHour == null && parkingType == null && restrictions == null
                        && availabilityWindows == null;
        }
}