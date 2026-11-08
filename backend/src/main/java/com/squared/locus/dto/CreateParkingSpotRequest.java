package com.locus.api.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateParkingSpotRequest(
        // Placeholder until the authentication milestone: the owner will come from the logged-in user.
        @NotNull UUID ownerId,
        @NotBlank @Size(max = 120) String title,
        @Size(max = 2000) String description,
        @NotBlank @Size(max = 300) String address,
        String photoUrl,
        @NotNull @DecimalMin(value = "0.00", inclusive = false) @DecimalMax("99999999.99")
        @Digits(integer = 8, fraction = 2) BigDecimal pricePerHour,
        @Pattern(regexp = "driveway|privateLot|garage|other") String parkingType,
        @Size(max = 1000) String restrictions
) {
}