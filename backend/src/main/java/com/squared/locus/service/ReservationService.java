package com.squared.locus.service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.squared.locus.domain.ConflictException;
import com.squared.locus.domain.InvalidRequestException;
import com.squared.locus.domain.NotFoundException;
import com.squared.locus.domain.PaymentHold;
import com.squared.locus.domain.ReservationPrice;
import com.squared.locus.dto.CreateReservationRequest;
import com.squared.locus.dto.ReservationResponse;
import com.squared.locus.model.ParkingSpot;
import com.squared.locus.model.Reservation;
import com.squared.locus.model.PaymentOutboxEvent;
import com.squared.locus.repository.ParkingSpotRepository;
import com.squared.locus.repository.PaymentOutboxRepository;
import com.squared.locus.repository.ReservationIdempotencyRepository;
import com.squared.locus.repository.ReservationRepository;

@Service
public class ReservationService {
    // Saves reservation and checks time overlaps
    private final ReservationRepository reservationRepository;
    // Loads and locks the parking spot were going to find
    private final ParkingSpotRepository parkingSpotRepository;
    // Prevent duplicate retries
    private final ReservationIdempotencyRepository idempotencyRepository;
    // Serializes and deserializing stored reservation responses
    private final ObjectMapper objectMapper;
    // LOCUS-38: "this reservation needs to be paid", picked up later by PaymentWorker
    private final PaymentOutboxRepository paymentOutboxRepository;

    // Stripe's always-succeeds test card, used when the client does not choose one.
    static final String DEFAULT_TEST_CARD = "pm_card_visa";

    public ReservationService(ReservationRepository reservationRepository,
                              ParkingSpotRepository parkingSpotRepository,
                              ReservationIdempotencyRepository idempotencyRepository,
                              ObjectMapper objectMapper,
                              PaymentOutboxRepository paymentOutboxRepository) {
        this.reservationRepository = reservationRepository;
        this.parkingSpotRepository = parkingSpotRepository;
        this.idempotencyRepository = idempotencyRepository;
        this.objectMapper = objectMapper;
        this.paymentOutboxRepository = paymentOutboxRepository;
    }

    // LOCUS-26: reserve a spot for a start and end time.
    // Run inside one database transaction (any step fails, rollback)
    // Makes claiming the idempotency key, locking the parking spot, checking availability, checking overlapping reservations
    // saving the reservation and storing the original response be treated as 1 unit.
    @Transactional
    public ReservationResponse create(UUID spotId, UUID userId, String idempotencyKey,
                                      CreateReservationRequest request) {
        // 1. Validate the idempotency key received by the controller
        if (idempotencyKey == null || idempotencyKey.isBlank()
                || idempotencyKey.length() < 8 || idempotencyKey.length() > 128) {
            throw new InvalidRequestException("INVALID_IDEMPOTENCY_KEY",
                    "Idempotency-Key must contain 8 to 128 characters and cannot be blank.");
        }
        OffsetDateTime startTime = request.startTime();
        OffsetDateTime endTime = request.endTime();
        String paymentMethod = request.paymentMethod() == null ? DEFAULT_TEST_CARD : request.paymentMethod();

        // 2. Create the request fingerprint to be able to identify what the idempotency key was originally used for
        // Include the HTTP operation, parking spot, times and the card
        String fingerprint = "POST|" + spotId + "|" + startTime.toInstant() + "|" + endTime.toInstant()
                + "|" + paymentMethod;
        // 3. Claim the idempotency key
        var claim = idempotencyRepository.claim(userId, idempotencyKey, fingerprint);
        // 4. If second request has a different fingerprint, reject it
        if (!claim.fingerprint().equals(fingerprint)) {
            throw new ConflictException("IDEMPOTENCY_KEY_MISMATCH",
                    "The idempotency key was already used for a different request.");
        }
        // 5. If already successfull, Replay before time/state validation: a successful response remains replayable later.
        if (claim.responseJson() != null) {
            try {
                return objectMapper.readerFor(ReservationResponse.class)
                        .without(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
                        .readValue(claim.responseJson());
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("Cannot read stored reservation response", e);
            }
        }

        // Validate reservation interval
        if (!endTime.isAfter(startTime)) {
            throw new InvalidRequestException("INVALID_INTERVAL", "endTime must be after startTime.");
        }
        if (!startTime.isAfter(OffsetDateTime.now())) {
            throw new InvalidRequestException("START_IN_PAST", "startTime must be in the future.");
        }

        // Load and lock the parking spot
        // The @Transactional annotation keeps this lock active until the reservation process finishes
        ParkingSpot spot = parkingSpotRepository.findForUpdate(spotId)
                .orElseThrow(() -> new NotFoundException("SPOT_NOT_FOUND", "Parking spot not found."));

        if (!spot.isActive()) {
            throw new ConflictException("PARKING_SPOT_UNAVAILABLE", "This parking spot is no longer accepting reservations.");
        }
        if (!spot.isOpenDuring(startTime, endTime)) {
            throw new ConflictException("PARKING_SPOT_UNAVAILABLE", "The owner has not made the spot available for this whole interval.");
        }
        if (reservationRepository.hasOverlap(spotId, startTime, endTime, PaymentHold.cutoff(OffsetDateTime.now()))) {
            throw new ConflictException("PARKING_SPOT_UNAVAILABLE",
                    "This parking spot is already reserved during the requested interval.");
        }

        BigDecimal totalPrice = ReservationPrice.calculate(spot.getPricePerHour(), startTime, endTime);
        Reservation reservation = new Reservation(spotId, userId, startTime, endTime, totalPrice);

        ReservationResponse response = ReservationResponse.from(reservationRepository.saveAndFlush(reservation));

        // LOCUS-38: in the same transaction, so there is never a reservation without its payment
        // request (or a payment request without its reservation). Stripe is NOT called here.
        paymentOutboxRepository.save(new PaymentOutboxEvent(reservation.getId(), paymentMethod));

        try {
            idempotencyRepository.complete(userId, idempotencyKey, objectMapper.writeValueAsString(response));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot store reservation response", e);
        }
        return response;
    }

    // LOCUS-29: only the driver who made the reservation can see it.
    @Transactional(readOnly = true)
    public ReservationResponse getById(UUID reservationId, UUID userId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new NotFoundException("RESERVATION_NOT_FOUND", "Reservation not found."));

        if (!reservation.getUserId().equals(userId)) {
            throw new ForbiddenException("NOT_RESERVATION_OWNER", "You can only view your own reservations.");
        }
        return ReservationResponse.from(reservation);
    }
}
