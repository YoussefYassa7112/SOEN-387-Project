package com.squared.locus.controller;

import com.squared.locus.dto.*;
import com.squared.locus.service.ParkingSpotService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

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

    // LOCUS-15: owner creates a listing.
    @PostMapping("/parking-spots")
    @ResponseStatus(HttpStatus.CREATED)
    public ParkingSpotResponse create(@Valid @RequestBody CreateParkingSpotRequest request) {
        return parkingSpotService.create(request);
    }

    // LOCUS-16: replace the listing's complete availability schedule.
    @PutMapping("/parking-spots/{spotId}/availability")
    public ParkingSpotResponse setAvailability(
            @PathVariable UUID spotId,
            @RequestParam @NotNull UUID ownerId,
            @Valid @RequestBody SetAvailabilityRequest request) {
        return parkingSpotService.setAvailability(spotId, ownerId, request);
    }

    // LOCUS-17: replace listing details.
    @PutMapping("/parking-spots/{spotId}")
    public ParkingSpotResponse update(
            @PathVariable UUID spotId,
            @RequestParam @NotNull UUID ownerId,
            @Valid @RequestBody UpdateParkingSpotRequest request) {
        return parkingSpotService.update(spotId, ownerId, request);
    }

    // LOCUS-18: deactivate; the listing remains in the database.
    @PatchMapping("/parking-spots/{spotId}/deactivate")
    public ParkingSpotResponse deactivate(
            @PathVariable UUID spotId,
            @RequestParam @NotNull UUID ownerId) {
        return parkingSpotService.deactivate(spotId, ownerId);
    }

    // LOCUS-19: public details for an active listing.
    @GetMapping("/parking-spots/{spotId}")
    public ParkingSpotResponse getDetails(@PathVariable UUID spotId) {
        return parkingSpotService.getDetails(spotId);
    }

    // LOCUS-20: owner's listings, including deactivated listings.
    @GetMapping("/owners/{ownerId}/parking-spots")
    public List<ParkingSpotResponse> getOwnerListings(@PathVariable UUID ownerId) {
        return parkingSpotService.getOwnerListings(ownerId);
    }

    // LOCUS-21, 22 and 23: browse active listings, optionally by location and time interval.
    @GetMapping("/parking-spots")
    public ParkingSpotPageResponse search(
            @RequestParam(required = false) String location,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime endTime,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        return parkingSpotService.search(location, startTime, endTime, limit, offset);
    }
}
