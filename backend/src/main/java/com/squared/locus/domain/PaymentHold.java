package com.squared.locus.domain;

import java.time.Duration;
import java.time.OffsetDateTime;

// Expiry of unpaid holds: a pendingPayment reservation holds the spot for 5 minutes.
// After that it no longer blocks anyone, even if nothing has cleaned it up yet,
// so an abandoned or stuck payment can never block a spot forever.
public final class PaymentHold {

    public static final Duration DURATION = Duration.ofMinutes(5);

    private PaymentHold() {
    }

    // Unpaid reservations created before this moment have expired.
    public static OffsetDateTime cutoff(OffsetDateTime now) {
        return now.minus(DURATION);
    }

    public static boolean isExpired(OffsetDateTime createdAt, OffsetDateTime now) {
        return createdAt != null && createdAt.isBefore(cutoff(now));
    }
}
