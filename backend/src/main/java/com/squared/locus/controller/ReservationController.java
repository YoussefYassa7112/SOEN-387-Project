package com.squared.locus.controller;

import com.squared.locus.dto.CreateReservationRequest;
import com.squared.locus.dto.ReservationResponse;
import com.squared.locus.service.ReservationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class ReservationController {
    private final ReservationService reservationService;

    public ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    // LOCUS-26: 202 because the reservation waits for payment (spec: pendingPayment).
    @PostMapping("/parking-spots/{spotId}/reservations")
    public ResponseEntity<ReservationResponse> create(
            @PathVariable UUID spotId,
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateReservationRequest request) {
        UUID userId = UUID.fromString(jwt.getSubject());
        ReservationResponse reservation = reservationService.create(spotId, userId, idempotencyKey, request);
        return ResponseEntity
                .accepted()
                .location(URI.create("/api/v1/reservations/" + reservation.reservationId()))
                .body(reservation);
    }

    // LOCUS-29: the owner check is in the service, not here.
    @GetMapping("/reservations/{reservationId}")
    public ReservationResponse getById(
            @PathVariable UUID reservationId,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return reservationService.getById(reservationId, userId);
    }

    // LOCUS-31: cancel one of my reservations. The rules are in the service.
    @DeleteMapping("/reservations/{reservationId}")
    public ReservationResponse cancel(
            @PathVariable UUID reservationId,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return reservationService.cancel(reservationId, userId);
    }

    // LOCUS-30: the signed-in driver's reservations.
    @GetMapping("/me/reservations")
    public List<ReservationResponse> listMine(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return reservationService.listMine(userId);
    }
}
