package com.squared.locus.service;

import com.squared.locus.domain.ConflictException;
import com.squared.locus.domain.InvalidRequestException;
import com.squared.locus.domain.NotFoundException;
import com.squared.locus.domain.ReservationPrice;
import com.squared.locus.dto.CreateReservationRequest;
import com.squared.locus.dto.ReservationResponse;
import com.squared.locus.model.ParkingSpot;
import com.squared.locus.model.Reservation;
import com.squared.locus.repository.ParkingSpotRepository;
import com.squared.locus.repository.ReservationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Service
public class ReservationService {
    private final ReservationRepository reservationRepository;
    private final ParkingSpotRepository parkingSpotRepository;

    public ReservationService(ReservationRepository reservationRepository,
                              ParkingSpotRepository parkingSpotRepository) {
        this.reservationRepository = reservationRepository;
        this.parkingSpotRepository = parkingSpotRepository;
    }

    // LOCUS-26: reserve a spot for a start and end time.
    @Transactional
    public ReservationResponse create(UUID spotId, UUID userId, CreateReservationRequest request) {
        OffsetDateTime startTime = request.startTime();
        OffsetDateTime endTime = request.endTime();

        if (!endTime.isAfter(startTime)) {
            throw new InvalidRequestException("INVALID_INTERVAL", "endTime must be after startTime.");
        }
        if (!startTime.isAfter(OffsetDateTime.now())) {
            throw new InvalidRequestException("START_IN_PAST", "startTime must be in the future.");
        }

        ParkingSpot spot = parkingSpotRepository.findById(spotId)
                .orElseThrow(() -> new NotFoundException("SPOT_NOT_FOUND", "Parking spot not found."));

        if (!spot.isActive()) {
            throw new ConflictException("PARKING_SPOT_UNAVAILABLE", "This parking spot is no longer accepting reservations.");
        }
        if (!spot.isOpenDuring(startTime, endTime)) {
            throw new ConflictException("PARKING_SPOT_UNAVAILABLE", "The owner has not made the spot available for this whole interval.");
        }

        BigDecimal totalPrice = ReservationPrice.calculate(spot.getPricePerHour(), startTime, endTime);
        Reservation reservation = new Reservation(spotId, userId, startTime, endTime, totalPrice);

        return ReservationResponse.from(reservationRepository.save(reservation));
    }
}
