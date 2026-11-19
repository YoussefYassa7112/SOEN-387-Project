package com.squared.locus.service;

import com.squared.locus.integration.PaymentGateway;
import com.squared.locus.integration.PaymentResult;
import com.squared.locus.model.PaymentOutboxEvent;
import com.squared.locus.model.Reservation;
import com.squared.locus.repository.ParkingSpotRepository;
import com.squared.locus.repository.PaymentOutboxRepository;
import com.squared.locus.repository.ReservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// LOCUS-43: what happens for each payment result. Stripe is replaced by a fake gateway.
class PaymentProcessorTest {
    private final PaymentOutboxRepository outbox = mock(PaymentOutboxRepository.class);
    private final ReservationRepository reservations = mock(ReservationRepository.class);
    private final ParkingSpotRepository spots = mock(ParkingSpotRepository.class);
    private final PaymentGateway stripe = mock(PaymentGateway.class);
    private final PaymentProcessor processor = new PaymentProcessor(outbox, reservations, spots, stripe, 3);

    private Reservation reservation;
    private PaymentOutboxEvent event;

    @BeforeEach
    void setUp() {
        var start = OffsetDateTime.now().plusDays(1);
        reservation = new Reservation(UUID.randomUUID(), UUID.randomUUID(), start, start.plusHours(2),
                new BigDecimal("20.00"));
        event = new PaymentOutboxEvent(reservation.getId(), "pm_card_visa");
        when(outbox.findNextDueForUpdate()).thenReturn(Optional.of(event));
        when(reservations.findById(any())).thenReturn(Optional.of(reservation));
    }

    @Test
    void successfulPaymentConfirmsTheReservation() {
        when(stripe.charge(any(), any(), any(), any())).thenReturn(PaymentResult.succeeded("pi_123"));

        processor.processNext();

        assertThat(reservation.getStatus()).isEqualTo(Reservation.Status.CONFIRMED);
        assertThat(event.getStatus()).isEqualTo(PaymentOutboxEvent.Status.DONE);
        assertThat(event.getPaymentIntentId()).isEqualTo("pi_123");
    }

    // The spot is locked AFTER Stripe answers (bookings never wait on Stripe)
    // and BEFORE the reservation becomes confirmed (no booking can slip in between).
    @Test
    void spotIsLockedAfterStripeAnswersAndBeforeConfirming() {
        when(stripe.charge(any(), any(), any(), any())).thenReturn(PaymentResult.succeeded("pi_123"));
        List<Reservation.Status> statusWhenLocked = new ArrayList<>();
        when(spots.findForUpdate(reservation.getSpotId())).thenAnswer(call -> {
            statusWhenLocked.add(reservation.getStatus());
            return Optional.empty();
        });

        processor.processNext();

        InOrder order = inOrder(stripe, spots);
        order.verify(stripe).charge(any(), any(), any(), any());
        order.verify(spots).findForUpdate(reservation.getSpotId());
        assertThat(statusWhenLocked).containsExactly(Reservation.Status.PENDING_PAYMENT);
        assertThat(reservation.getStatus()).isEqualTo(Reservation.Status.CONFIRMED);
    }

    @Test
    void declinedCardFailsTheReservationAndFreesTheSpot() {
        when(stripe.charge(any(), any(), any(), any())).thenReturn(PaymentResult.declined("pi_456", "Your card was declined."));

        processor.processNext();

        assertThat(reservation.getStatus()).isEqualTo(Reservation.Status.PAYMENT_FAILED);
        assertThat(event.getStatus()).isEqualTo(PaymentOutboxEvent.Status.FAILED);
    }

    @Test
    void timeoutIsRetriedLaterAndTheReservationStaysPending() {
        when(stripe.charge(any(), any(), any(), any())).thenReturn(PaymentResult.retry("Read timed out"));

        processor.processNext();

        assertThat(reservation.getStatus()).isEqualTo(Reservation.Status.PENDING_PAYMENT);
        assertThat(event.getStatus()).isEqualTo(PaymentOutboxEvent.Status.PENDING);
        assertThat(event.getAttempts()).isEqualTo(1);
        assertThat(event.getNextAttemptAt()).isAfter(OffsetDateTime.now());
    }

    @Test
    void repeatedTimeoutsEventuallyGiveUp() {
        when(stripe.charge(any(), any(), any(), any())).thenReturn(PaymentResult.retry("Read timed out"));

        processor.processNext();
        processor.processNext();
        processor.processNext();   // 3rd attempt = max-attempts in this test

        assertThat(reservation.getStatus()).isEqualTo(Reservation.Status.PAYMENT_FAILED);
        assertThat(event.getStatus()).isEqualTo(PaymentOutboxEvent.Status.FAILED);
        verify(stripe, times(3)).charge(any(), any(), any(), any());
    }

    @Test
    void expiredHoldIsNotChargedAndFreesTheSpot() {
        // Created 6 minutes ago: past the 5-minute hold.
        ReflectionTestUtils.setField(reservation, "createdAt", OffsetDateTime.now().minusMinutes(6));

        processor.processNext();

        verifyNoInteractions(stripe);
        assertThat(reservation.getStatus()).isEqualTo(Reservation.Status.PAYMENT_FAILED);
        assertThat(event.getStatus()).isEqualTo(PaymentOutboxEvent.Status.FAILED);
    }

    @Test
    void holdStillValidIsCharged() {
        ReflectionTestUtils.setField(reservation, "createdAt", OffsetDateTime.now().minusMinutes(4));
        when(stripe.charge(any(), any(), any(), any())).thenReturn(PaymentResult.succeeded("pi_789"));

        processor.processNext();

        assertThat(reservation.getStatus()).isEqualTo(Reservation.Status.CONFIRMED);
    }

    @Test
    void nothingDueMeansNothingToDo() {
        when(outbox.findNextDueForUpdate()).thenReturn(Optional.empty());

        assertThat(processor.processNext()).isFalse();
        verifyNoInteractions(stripe);
    }
}
