package com.squared.locus.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.OffsetDateTime;

// LOCUS-28: hourly price x exact time booked, rounded to cents (2.5 h at $8/h = $20.00).
public final class ReservationPrice {

    private static final BigDecimal SECONDS_PER_HOUR = BigDecimal.valueOf(3600);

    private ReservationPrice() {
    }

    public static BigDecimal calculate(BigDecimal pricePerHour, OffsetDateTime startTime, OffsetDateTime endTime) {
        if (pricePerHour == null || pricePerHour.signum() < 0) {
            throw new InvalidRequestException("INVALID_PRICE", "The hourly price must be zero or more.");
        }
        if (startTime == null || endTime == null || !endTime.isAfter(startTime)) {
            throw new InvalidRequestException("INVALID_INTERVAL", "endTime must be after startTime.");
        }

        long seconds = Duration.between(startTime, endTime).getSeconds();
        return pricePerHour
                .multiply(BigDecimal.valueOf(seconds))
                .divide(SECONDS_PER_HOUR, 2, RoundingMode.HALF_UP);
    }
}
