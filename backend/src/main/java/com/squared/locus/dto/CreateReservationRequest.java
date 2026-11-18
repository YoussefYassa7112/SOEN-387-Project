package com.squared.locus.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.OffsetDateTime;

// No userId here: the driver is taken from the token, never from the request body.
// paymentMethod (optional, added in API 1.1.0): a Stripe test card such as pm_card_visa
// (succeeds) or pm_card_chargeDeclined (declined). Left out, pm_card_visa is used.
public record CreateReservationRequest(
        @NotNull OffsetDateTime startTime,
        @NotNull OffsetDateTime endTime,
        @Pattern(regexp = "pm_[A-Za-z0-9_]{1,96}", message = "paymentMethod must be a Stripe payment method id like pm_card_visa")
        String paymentMethod
) {
    // Old clients send only the two times; this keeps them working.
    public CreateReservationRequest(OffsetDateTime startTime, OffsetDateTime endTime) {
        this(startTime, endTime, null);
    }
}
