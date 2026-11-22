package com.squared.locus.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.squared.locus.domain.ConflictException;
import com.squared.locus.domain.NotFoundException;
import com.squared.locus.model.Reservation;
import com.squared.locus.repository.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReservationServiceTest {
    private final ReservationRepository reservations = mock(ReservationRepository.class);
    private final ParkingSpotRepository spots = mock(ParkingSpotRepository.class);
    private final ReservationService service = new ReservationService(reservations,
            spots, mock(IdempotencyRepository.class), new ObjectMapper(),
            mock(PaymentOutboxRepository.class));
    private final UUID driver = UUID.randomUUID();
    private final UUID id = UUID.randomUUID();

    private Reservation reservation() {
        var start = OffsetDateTime.now().plusDays(2);
        return new Reservation(UUID.randomUUID(), driver, start, start.plusHours(1), BigDecimal.TEN);
    }

    @Test
    void driverCanReadOwnReservation() {
        var reservation = reservation();
        when(reservations.findById(id)).thenReturn(Optional.of(reservation));
        var response = service.getById(id, driver);
        assertThat(response.userId()).isEqualTo(driver);
        assertThat(response.spotId()).isEqualTo(reservation.getSpotId());
    }

    @Test
    void driverCannotReadAnotherDriversReservation() {
        when(reservations.findById(id)).thenReturn(Optional.of(reservation()));
        assertThatThrownBy(() -> service.getById(id, UUID.randomUUID()))
                .isInstanceOfSatisfying(ForbiddenException.class,
                        error -> assertThat(error.getCode()).isEqualTo("NOT_RESERVATION_OWNER"));
    }

    @Test
    void unknownReservationReturnsNotFound() {
        when(reservations.findById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getById(id, driver))
                .isInstanceOfSatisfying(NotFoundException.class,
                        error -> assertThat(error.getCode()).isEqualTo("RESERVATION_NOT_FOUND"));
    }

    // LOCUS-30
    @Test
    void driverSeesOnlyTheirOwnReservations() {
        when(reservations.findAllByUserIdOrderByStartTimeDesc(driver)).thenReturn(List.of(reservation(), reservation()));

        var mine = service.listMine(driver);

        assertThat(mine).hasSize(2);
        assertThat(mine).allMatch(r -> r.userId().equals(driver));
        verify(reservations).findAllByUserIdOrderByStartTimeDesc(driver);
    }

    // ---- LOCUS-31: cancel ----

    @Test
    void driverCanCancelAnUpcomingReservation() {
        var reservation = reservation();
        when(reservations.findById(id)).thenReturn(Optional.of(reservation));
        when(reservations.findStatusById(id)).thenReturn(Reservation.Status.CONFIRMED);
        when(reservations.save(reservation)).thenReturn(reservation);

        var response = service.cancel(id, driver);

        assertThat(response.status()).isEqualTo("cancelled");
        verify(spots).findForUpdate(reservation.getSpotId());   // took the spot's lock
    }

    @Test
    void driverCannotCancelAnotherDriversReservation() {
        when(reservations.findById(id)).thenReturn(Optional.of(reservation()));
        assertThatThrownBy(() -> service.cancel(id, UUID.randomUUID()))
                .isInstanceOfSatisfying(ForbiddenException.class,
                        error -> assertThat(error.getCode()).isEqualTo("NOT_RESERVATION_OWNER"));
    }

    @Test
    void cannotCancelAReservationThatAlreadyStarted() {
        var start = OffsetDateTime.now().minusMinutes(10);
        var started = new Reservation(UUID.randomUUID(), driver, start, start.plusHours(1), BigDecimal.TEN);
        when(reservations.findById(id)).thenReturn(Optional.of(started));
        when(reservations.findStatusById(id)).thenReturn(Reservation.Status.CONFIRMED);

        assertThatThrownBy(() -> service.cancel(id, driver))
                .isInstanceOfSatisfying(ConflictException.class,
                        error -> assertThat(error.getCode()).isEqualTo("RESERVATION_NOT_CANCELLABLE"));
    }

    @Test
    void cannotCancelTwice() {
        when(reservations.findById(id)).thenReturn(Optional.of(reservation()));
        when(reservations.findStatusById(id)).thenReturn(Reservation.Status.CANCELLED);

        assertThatThrownBy(() -> service.cancel(id, driver))
                .isInstanceOf(ConflictException.class);
    }
}
