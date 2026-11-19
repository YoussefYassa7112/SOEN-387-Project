package com.squared.locus.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;

// LOCUS-38: "this reservation still needs to be paid". One row per reservation.
@Entity
@Table(name = "payment_outbox")
@Getter
@NoArgsConstructor
public class PaymentOutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reservation_id", nullable = false, unique = true)
    private UUID reservationId;

    @Column(name = "payment_method", nullable = false, length = 100)
    private String paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at", nullable = false)
    private OffsetDateTime nextAttemptAt;

    @Column(name = "payment_intent_id", length = 100)
    private String paymentIntentId;

    @Column(name = "last_error", length = 500)
    private String lastError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    public PaymentOutboxEvent(UUID reservationId, String paymentMethod) {
        this.reservationId = reservationId;
        this.paymentMethod = paymentMethod;
        this.status = Status.PENDING;
        this.attempts = 0;
        this.nextAttemptAt = OffsetDateTime.now();
        this.createdAt = OffsetDateTime.now();
    }

    public void markDone(String paymentIntentId) {
        this.status = Status.DONE;
        this.paymentIntentId = paymentIntentId;
        this.lastError = null;
    }

    public void markFailed(String paymentIntentId, String error) {
        this.status = Status.FAILED;
        this.paymentIntentId = paymentIntentId;
        this.lastError = shorten(error);
    }

    // A temporary problem (timeout, Stripe down): try again later, waiting twice as long each time.
    // 1st retry after 5s, then 10s, 20s, 40s... (this is called "exponential backoff").
    public void scheduleRetry(String error) {
        this.attempts++;
        this.lastError = shorten(error);
        long waitSeconds = 5 * (long) Math.pow(2, attempts - 1);
        this.nextAttemptAt = OffsetDateTime.now().plus(Duration.ofSeconds(waitSeconds));
    }

    // The last_error column holds at most 500 characters.
    private static String shorten(String text) {
        if (text == null || text.length() <= 500) {
            return text;
        }
        return text.substring(0, 500);
    }

    public enum Status { PENDING, DONE, FAILED }
}
