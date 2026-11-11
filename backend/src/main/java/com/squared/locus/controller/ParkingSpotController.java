package com.squared.locus.controller;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import com.squared.locus.dto.*;
import com.squared.locus.service.ParkingSpotService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import java.math.BigDecimal;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@Validated
public class ParkingSpotController {
    private final ParkingSpotService parkingSpotService;

    public ParkingSpotController(ParkingSpotService parkingSpotService) {
        this.parkingSpotService = parkingSpotService;
    }

    // LOCUS-15: owner creates a listing (availabilityWindows required).
    @PostMapping("/parking-spots")
    public ResponseEntity<ParkingSpotResponse> create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateParkingSpotRequest request) {

        UUID callerId = UUID.fromString(jwt.getSubject());

        ParkingSpotResponse created = parkingSpotService.create(callerId, request);

        return ResponseEntity
                .created(URI.create("/api/v1/parking-spots/" + created.spotId()))
                .body(created);
    }

    // LOCUS 17: partial update of details and/or the complete availability schedule.
    @PatchMapping("/parking-spots/{spotId}")
    public ParkingSpotResponse update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID spotId,
            @Valid @RequestBody UpdateParkingSpotRequest request) {
        UUID ownerId = UUID.fromString(jwt.getSubject());
        return parkingSpotService.update(spotId, ownerId, request);
    }

    // LOCUS-16: replace the listing's complete availability schedule.
    @PutMapping("/parking-spots/{spotId}/availability")
    public ParkingSpotResponse setAvailability(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID spotId,
            @Valid @RequestBody SetAvailabilityRequest request) {
        UUID ownerId = UUID.fromString(jwt.getSubject());
        return parkingSpotService.setAvailability(spotId, ownerId, request);
    }

    // LOCUS-18: deactivate; the listing remains in the database.
    @DeleteMapping("/parking-spots/{spotId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID spotId) {
        UUID ownerId = UUID.fromString(jwt.getSubject());
        parkingSpotService.deactivate(spotId, ownerId);
    }

    // LOCUS-19: public details for an active listing.
    @GetMapping("/parking-spots/{spotId}")
    public ParkingSpotResponse getDetails(@PathVariable UUID spotId) {
        return parkingSpotService.getDetails(spotId);
    }

    // LOCUS-20: current owner's listings, including deactivated listings.
    @GetMapping("/me/parking-spots")
    public List<ParkingSpotResponse> getMyListings(@AuthenticationPrincipal Jwt jwt) {
        UUID ownerId = UUID.fromString(jwt.getSubject());
        return parkingSpotService.getOwnerListings(ownerId);
    }

    // LOCUS-21, 22, 23 and 25: browse active listings, with optional search filters.
    @GetMapping("/parking-spots")
    public ParkingSpotPageResponse search(
            @RequestParam(required = false) String location,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime endTime,
            @RequestParam(required = false) BigDecimal maxPricePerHour,
            @RequestParam(required = false) String parkingType,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        return parkingSpotService.search(location, startTime, endTime, maxPricePerHour, parkingType, limit, offset);
    }

    // LOCUS-24: check one spot for one time interval.
    @GetMapping("/parking-spots/{spotId}/availability")
    public AvailabilityResponse checkAvailability(
            @PathVariable UUID spotId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime startTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime endTime) {
        return parkingSpotService.checkAvailability(spotId, startTime, endTime);
    }
}
