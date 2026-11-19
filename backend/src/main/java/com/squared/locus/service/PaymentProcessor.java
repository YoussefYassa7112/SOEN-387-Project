package com.squared.locus.service;

import com.squared.locus.domain.PaymentHold;
import com.squared.locus.integration.PaymentGateway;
import com.squared.locus.integration.PaymentResult;
import com.squared.locus.model.PaymentOutboxEvent;
import com.squared.locus.model.Reservation;
import com.squared.locus.repository.ParkingSpotRepository;
import com.squared.locus.repository.PaymentOutboxRepository;
import com.squared.locus.repository.ReservationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Optional;

// LOCUS-39 to 42: handles ONE pending payment per call.
@Service
public class PaymentProcessor {
    private static final Logger log = LoggerFactory.getLogger(PaymentProcessor.class);

    private final PaymentOutboxRepository outboxRepository;
    private final ReservationRepository reservationRepository;
    private final ParkingSpotRepository parkingSpotRepository;
    private final PaymentGateway paymentGateway;
    private final int maxAttempts;

    public PaymentProcessor(PaymentOutboxRepository outboxRepository,
                            ReservationRepository reservationRepository,
                            ParkingSpotRepository parkingSpotRepository,
                            PaymentGateway paymentGateway,
                            @Value("${locus.payments.max-attempts:5}") int maxAttempts) {
        this.outboxRepository = outboxRepository;
        this.reservationRepository = reservationRepository;
        this.parkingSpotRepository = parkingSpotRepository;
        this.paymentGateway = paymentGateway;
        this.maxAttempts = maxAttempts;
    }

    // Returns false when there is nothing left to do right now.
    @Transactional
    public boolean processNext() {
        Optional<PaymentOutboxEvent> next = outboxRepository.findNextDueForUpdate();
        if (next.isEmpty()) {
            return false;
        }
        PaymentOutboxEvent event = next.get();
        Reservation reservation = reservationRepository.findById(event.getReservationId()).orElseThrow();

        // Already settled some other way (e.g. cancelled): nothing to charge.
        if (reservation.getStatus() != Reservation.Status.PENDING_PAYMENT) {
            event.markDone(null);
            return true;
        }

        // The 5-minute hold ran out before we could charge: the spot may already belong
        // to someone else, so do not charge at all.
        if (PaymentHold.isExpired(reservation.getCreatedAt(), OffsetDateTime.now())) {
            reservation.failPayment();
            event.markFailed(null, "Payment hold expired before the charge was sent.");
            log.warn("Payment hold expired for reservation {}; not charged", reservation.getId());
            return true;
        }

        PaymentResult result = paymentGateway.charge(
                reservation.getId(), reservation.getTotalPrice(), reservation.getCurrency(), event.getPaymentMethod());

        // Lock the parking spot, the same lock new bookings and cancellations take. Until this
        // transaction ends, nothing else can change who holds this spot, so a new booking cannot
        // slip in before we save "confirmed", and a cancellation cannot be overwritten.
        // Taken only after Stripe answered, so bookings never wait on Stripe.
        parkingSpotRepository.findForUpdate(reservation.getSpotId());

        // LOCUS-31: the driver may have cancelled while Stripe was answering. Read the status again.
        if (reservationRepository.findStatusById(reservation.getId()) != Reservation.Status.PENDING_PAYMENT) {
            if (result.outcome() == PaymentResult.Outcome.SUCCEEDED) {
                event.markFailed(result.paymentIntentId(), "NEEDS_REFUND: paid after the reservation was cancelled.");
                log.error("NEEDS_REFUND: reservation {} was paid after it was cancelled (Stripe {})",
                        reservation.getId(), result.paymentIntentId());
            } else {
                event.markDone(null);
            }
            return true;
        }

        switch (result.outcome()) {
            case SUCCEEDED -> {
                if (PaymentHold.isExpired(reservation.getCreatedAt(), OffsetDateTime.now())) {
                    // Paid, but the hold expired while Stripe was answering: the spot may be taken.
                    // Confirming could double-book, so this needs a refund. Left to a human (Chapter 9).
                    reservation.failPayment();
                    event.markFailed(result.paymentIntentId(), "NEEDS_REFUND: paid after the payment hold expired.");
                    log.error("NEEDS_REFUND: reservation {} was paid after its hold expired (Stripe {})",
                            reservation.getId(), result.paymentIntentId());
                } else {
                    reservation.confirmPayment();                               // LOCUS-41
                    event.markDone(result.paymentIntentId());
                    log.info("Payment succeeded for reservation {}", reservation.getId());
                }
            }
            case DECLINED -> {
                reservation.failPayment();                                      // LOCUS-42
                event.markFailed(result.paymentIntentId(), result.message());
                log.warn("Payment declined for reservation {}: {}", reservation.getId(), result.message());
            }
            case RETRY -> {
                event.scheduleRetry(result.message());
                if (event.getAttempts() >= maxAttempts) {
                    reservation.failPayment();                                  // LOCUS-42: gave up
                    event.markFailed(null, "Gave up after " + maxAttempts + " attempts: " + result.message());
                    log.warn("Payment gave up for reservation {}", reservation.getId());
                } else {
                    log.warn("Payment will retry for reservation {} (attempt {}): {}",
                            reservation.getId(), event.getAttempts(), result.message());
                }
            }
        }
        return true;
    }
}
