package com.squared.locus.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "reservations")
@Getter
@NoArgsConstructor
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "spot_id", nullable = false)
    private UUID spotId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "start_time", nullable = false)
    private OffsetDateTime startTime;

    @Column(name = "end_time", nullable = false)
    private OffsetDateTime endTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status;

    @Column(name = "total_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalPrice;

    // The column is CHAR(3), not VARCHAR, so Hibernate's startup check needs to be told.
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    // A new reservation waits for payment (LOCUS-37 confirms it later).
    public Reservation(UUID spotId, UUID userId, OffsetDateTime startTime, OffsetDateTime endTime,
                       BigDecimal totalPrice) {
        this.spotId = spotId;
        this.userId = userId;
        this.startTime = startTime;
        this.endTime = endTime;
        this.totalPrice = totalPrice;
        this.status = Status.PENDING_PAYMENT;
        this.currency = "CAD";
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }

    // Stored as PENDING_PAYMENT in the database, sent as pendingPayment in the API.
    public enum Status {
        PENDING_PAYMENT("pendingPayment"),
        CONFIRMED("confirmed"),
        PAYMENT_FAILED("paymentFailed"),
        CANCELLED("cancelled");

        private final String apiValue;

        Status(String apiValue) {
            this.apiValue = apiValue;
        }

        public String getApiValue() {
            return apiValue;
        }
    }
}
