package com.squared.locus.domain;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

// Expiry of unpaid holds: the 5-minute rule.
class PaymentHoldTest {
    private final OffsetDateTime now = OffsetDateTime.parse("2026-11-20T12:00:00-05:00");

    @Test
    void holdYoungerThanFiveMinutesIsStillValid() {
        assertThat(PaymentHold.isExpired(now.minusMinutes(4), now)).isFalse();
    }

    @Test
    void holdOlderThanFiveMinutesHasExpired() {
        assertThat(PaymentHold.isExpired(now.minusMinutes(6), now)).isTrue();
    }

    @Test
    void cutoffIsFiveMinutesAgo() {
        assertThat(PaymentHold.cutoff(now)).isEqualTo(now.minusMinutes(5));
    }
}
