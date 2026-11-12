package com.squared.locus.service;

import com.fasterxml.jackson.databind.ObjectMapper;
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
    private final ReservationService service = new ReservationService(reservations,
            mock(ParkingSpotRepository.class), mock(ReservationIdempotencyRepository.class), new ObjectMapper());
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
}
