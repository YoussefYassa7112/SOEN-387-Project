package com.squared.locus.service;
import com.squared.locus.repository.UserRepository;
import com.squared.locus.model.User;
import com.squared.locus.domain.ConflictException;
import com.squared.locus.domain.InvalidRequestException;
import com.squared.locus.domain.NotFoundException;
import com.squared.locus.domain.PaymentHold;
import com.squared.locus.dto.*;
import com.squared.locus.integration.Coordinates;
import com.squared.locus.integration.Geocoder;
import com.squared.locus.model.AvailabilityWindow;
import com.squared.locus.model.ParkingSpot;
import com.squared.locus.model.ParkingSpot.ParkingType;
import com.squared.locus.model.Reservation;
import com.squared.locus.repository.IdempotencyRepository;
import com.squared.locus.repository.ParkingSpotRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class ParkingSpotService {
    private final ParkingSpotRepository parkingSpotRepository;
    private final UserRepository userRepository;
    private final Geocoder geocoder;
    private final IdempotencyRepository idempotencyRepository;
    private final ObjectMapper objectMapper;

    public ParkingSpotService(ParkingSpotRepository parkingSpotRepository, UserRepository userRepository,
                              Geocoder geocoder, IdempotencyRepository idempotencyRepository,
                              ObjectMapper objectMapper) {
        this.parkingSpotRepository = parkingSpotRepository;
        this.userRepository = userRepository;
        this.geocoder = geocoder;
        this.idempotencyRepository = idempotencyRepository;
        this.objectMapper = objectMapper;
    }

    // LOCUS-15: create a listing. Like booking, this POST needs an Idempotency-Key (Chapter 3):
    // if the owner's connection drops and the app sends the same request again, they get the
    // first listing back instead of a duplicate. The key and the listing are saved in the same
    // transaction, so either both exist or neither does.
    @Transactional
    public ParkingSpotResponse create(UUID callerId, String idempotencyKey, CreateParkingSpotRequest request) {
        User owner = findUser(callerId);
        checkIdempotencyKey(idempotencyKey);

        // The fingerprint is the whole request body, so the same key with a different body is caught.
        String fingerprint = "CREATE_SPOT|" + toJson(request);
        var claim = idempotencyRepository.claim(owner.getId(), idempotencyKey, fingerprint);
        if (!claim.fingerprint().equals(fingerprint)) {
            throw new ConflictException("IDEMPOTENCY_KEY_MISMATCH",
                    "The idempotency key was already used for a different request.");
        }
        if (claim.responseJson() != null) {
            return readStoredResponse(claim.responseJson());   // a retry: send back the first answer
        }

        List<AvailabilityWindow> windows = toValidatedWindows(request.availabilityWindows());
        // LOCUS-52: turn the address into map coordinates (null if it cannot be found).
        Coordinates coordinates = geocoder.findCoordinates(request.address());

        ParkingSpot spot = new ParkingSpot(
                owner.getId(),
                request.title(),
                request.description(),
                request.address(),
                request.photoUrl(),
                request.pricePerHour(),
                toParkingType(request.parkingType()),
                request.restrictions()
        );
        spot.replaceAvailabilityWindows(windows);
        setCoordinates(spot, coordinates);
        ParkingSpotResponse response = toResponse(parkingSpotRepository.saveAndFlush(spot));

        idempotencyRepository.complete(owner.getId(), idempotencyKey, toJson(response));
        return response;
    }

    private void checkIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()
                || idempotencyKey.length() < 8 || idempotencyKey.length() > 128) {
            throw new InvalidRequestException("INVALID_IDEMPOTENCY_KEY",
                    "Idempotency-Key must contain 8 to 128 characters and cannot be blank.");
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot write JSON", e);
        }
    }

    private ParkingSpotResponse readStoredResponse(String json) {
        try {
            // Keep the time zones exactly as they were stored, so the replay is the same answer.
            return objectMapper.readerFor(ParkingSpotResponse.class)
                    .without(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
                    .readValue(json);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot read stored parking spot response", e);
        }
    }

    @Transactional
    public ParkingSpotResponse update(UUID spotId, UUID ownerId, UpdateParkingSpotRequest request) {
        User owner = findUser(ownerId);
        ownerId = owner.getId();
        if (request.hasNoFields()) {
            throw new InvalidRequestException("EMPTY_UPDATE", "Send at least one field to update.");
        }
        List<AvailabilityWindow> windows = request.availabilityWindows() == null
                ? null
                : toValidatedWindows(request.availabilityWindows());

        // LOCUS-52: a new address needs new coordinates. We ask the map service BEFORE
        // locking the spot row, so the lock is never held while we wait on another
        // service (Chapter 7: keep locked transactions short).
        Coordinates coordinates = null;
        if (request.address() != null) {
            coordinates = geocoder.findCoordinates(request.address());
        }

        ParkingSpot spot = getOwnedSpotForUpdate(spotId, ownerId);
        if (!spot.isActive()) {
            throw new ConflictException("SPOT_INACTIVE", "A deactivated parking spot cannot be updated.");
        }

        if (request.title() != null) spot.setTitle(request.title());
        if (request.description() != null) spot.setDescription(request.description());
        if (request.address() != null) {
            spot.setAddress(request.address());
            setCoordinates(spot, coordinates);
        }
        if (request.photoUrl() != null) spot.setPhotoUrl(request.photoUrl());
        if (request.pricePerHour() != null) spot.setPricePerHour(request.pricePerHour());
        if (request.parkingType() != null) spot.setParkingType(toParkingType(request.parkingType()));
        if (request.restrictions() != null) spot.setRestrictions(request.restrictions());
        if (windows != null) spot.replaceAvailabilityWindows(windows); // replaces the complete schedule

        return toResponse(parkingSpotRepository.save(spot));
    }


    @Transactional
    public ParkingSpotResponse setAvailability(UUID spotId, UUID ownerId, SetAvailabilityRequest request) {
        User owner = findUser(ownerId);
        ownerId = owner.getId();
        List<AvailabilityWindow> windows = toValidatedWindows(request.periods());

        ParkingSpot spot = getOwnedSpotForUpdate(spotId, ownerId);
        if (!spot.isActive()) {
            throw new ConflictException("SPOT_INACTIVE", "A deactivated parking spot cannot have its availability changed.");
        }

        spot.replaceAvailabilityWindows(windows);
        return toResponse(parkingSpotRepository.save(spot));
    }


    @Transactional
    public void deactivate(UUID spotId, UUID ownerId) {
        User owner = findUser(ownerId);
        ownerId = owner.getId();
        ParkingSpot spot = getOwnedSpotForUpdate(spotId, ownerId);
        spot.setActive(false);
        parkingSpotRepository.save(spot);
    }


    @Transactional(readOnly = true)
    public ParkingSpotResponse getDetails(UUID spotId) {
        ParkingSpot spot = parkingSpotRepository.findByIdAndActiveTrue(spotId)
                .orElseThrow(() -> new NotFoundException("SPOT_NOT_FOUND", "Active parking spot not found."));
        return toResponse(spot);
    }


    @Transactional(readOnly = true)
    public List<ParkingSpotResponse> getOwnerListings(UUID ownerId) {
        User owner = findUser(ownerId);
        ownerId = owner.getId();
        return parkingSpotRepository.findAllByOwnerIdOrderByCreatedAtDesc(owner.getId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ParkingSpotPageResponse search(String location, OffsetDateTime startTime, OffsetDateTime endTime,
                                          BigDecimal maxPricePerHour, String parkingType,
                                          int limit, int offset) {
        return search(location, startTime, endTime, maxPricePerHour, parkingType, limit, offset, "default");
    }

    @Transactional(readOnly = true)
    public ParkingSpotPageResponse search(String location, OffsetDateTime startTime, OffsetDateTime endTime,
                                          BigDecimal maxPricePerHour, String parkingType,
                                          int limit, int offset, String sort) {
        return search(location, startTime, endTime, maxPricePerHour, parkingType, limit, offset, sort, null, null);
    }

    @Transactional(readOnly = true)
    public ParkingSpotPageResponse search(String location, OffsetDateTime startTime, OffsetDateTime endTime,
                                         BigDecimal maxPricePerHour, String parkingType, int limit, int offset,
                                         String sort, Double destinationLatitude, Double destinationLongitude) {
        checkPaging(limit, offset);
        ParkingType wantedType = validateSearchFilters(
                new ParkingSearchFilters(location, startTime, endTime, maxPricePerHour, parkingType, null));
        boolean closest = "closest".equals(sort);
        if (closest) {
            if (destinationLatitude == null || !(Math.abs(destinationLatitude) <= 90)
                    || destinationLongitude == null || !(Math.abs(destinationLongitude) <= 180)) {
                throw new InvalidRequestException("INVALID_DESTINATION", "Enter valid venue latitude and longitude.");
            }
        } else if (!"default".equals(sort) && !"cheapest".equals(sort)) {
            throw new InvalidRequestException("INVALID_SORT", "Sort must be default, cheapest, or closest.");
        }

        List<UUID> reservedSpotIds = new ArrayList<>();
        if (startTime != null) {
            reservedSpotIds = parkingSpotRepository.findReservedSpotIds(startTime, endTime, PaymentHold.cutoff(OffsetDateTime.now()));
        }

        List<ParkingSpot> matches = new ArrayList<>();
        for (ParkingSpot spot : parkingSpotRepository.findAllActiveWithWindows()) {
            if (matchesLocation(spot, location)
                    && matchesInterval(spot, startTime, endTime, reservedSpotIds)
                    && matchesMaxPrice(spot, maxPricePerHour)
                    && (wantedType == null || wantedType == spot.getParkingType())
                    && (!closest || (spot.getLatitude() != null && spot.getLongitude() != null))) {
                matches.add(spot);
            }
        }

        if ("cheapest".equals(sort)) {
            matches.sort(Comparator.comparing(ParkingSpot::getPricePerHour));
        } else if (closest) {
            matches.sort(Comparator.comparingDouble(spot -> distanceFrom(spot, destinationLatitude, destinationLongitude)));
        }

        int from = Math.min(offset, matches.size());
        int to = Math.min(from + limit, matches.size());

        List<ParkingSpotResponse> items = new ArrayList<>();
        for (ParkingSpot spot : matches.subList(from, to)) {
            items.add(toResponse(spot));
        }
        return new ParkingSpotPageResponse(items, matches.size(), limit, offset);
    }

    @Transactional(readOnly = true)
    public AvailabilityResponse checkAvailability(UUID spotId, OffsetDateTime startTime, OffsetDateTime endTime) {
        checkInterval(startTime, endTime);

        ParkingSpot spot = parkingSpotRepository.findById(spotId)
                .orElseThrow(() -> new NotFoundException("SPOT_NOT_FOUND", "Parking spot not found."));

        String reason = null;
        if (!spot.isActive()) {
            reason = "inactive";
        } else if (!spot.isOpenDuring(startTime, endTime)) {
            reason = "outsideOwnerAvailability";
        } else if (parkingSpotRepository.findReservedSpotIds(startTime, endTime, PaymentHold.cutoff(OffsetDateTime.now())).contains(spotId)) {
            reason = "alreadyReserved";
        }

        return new AvailabilityResponse(spotId, startTime, endTime, reason == null, reason);
    }

    // The upcoming times that are already taken on this spot, so the booking page can grey them out.
    // Only the times are returned, never who booked them.
    @Transactional(readOnly = true)
    public List<AvailabilityPeriodResponse> getBookedTimes(UUID spotId) {
        if (!parkingSpotRepository.existsById(spotId)) {
            throw new NotFoundException("SPOT_NOT_FOUND", "Parking spot not found.");
        }
        OffsetDateTime now = OffsetDateTime.now();
        return parkingSpotRepository.findBookedTimes(spotId, now, PaymentHold.cutoff(now),
                Reservation.Status.CONFIRMED, Reservation.Status.PENDING_PAYMENT);
    }

    ParkingType validateSearchFilters(ParkingSearchFilters filters) {
        checkLocation(filters.location());
        checkLocation(filters.destination());
        checkOptionalInterval(filters.startTime(), filters.endTime());
        checkMaxPrice(filters.maxPricePerHour());
        return parseParkingTypeFilter(filters.parkingType());
    }

    private double distanceFrom(ParkingSpot spot, double destinationLatitude, double destinationLongitude) {
        // Haversine distance in kilometres.
        double latitude = Math.toRadians(spot.getLatitude());
        double destination = Math.toRadians(destinationLatitude);
        double latHalfSine = Math.sin((latitude - destination) / 2);
        double lonHalfSine = Math.sin(Math.toRadians(spot.getLongitude() - destinationLongitude) / 2);
        double a = latHalfSine * latHalfSine + Math.cos(latitude) * Math.cos(destination) * lonHalfSine * lonHalfSine;
        return 12742 * Math.asin(Math.sqrt(Math.min(1, a)));
    }

    private void checkPaging(int limit, int offset) {
        if (limit < 1 || limit > 100) {
            throw new InvalidRequestException("INVALID_LIMIT", "limit must be between 1 and 100.");
        }
        if (offset < 0) {
            throw new InvalidRequestException("INVALID_OFFSET", "offset cannot be negative.");
        }
    }

    private void checkLocation(String location) {
        if (location != null && (location.isBlank() || location.length() > 200)) {
            throw new InvalidRequestException("INVALID_LOCATION", "location must be 1 to 200 characters.");
        }
    }

    private boolean matchesLocation(ParkingSpot spot, String location) {
        if (location == null) {
            return true;
        }
        return spot.getAddress().toLowerCase().contains(location.trim().toLowerCase());
    }

    private void checkOptionalInterval(OffsetDateTime startTime, OffsetDateTime endTime) {
        if (startTime == null && endTime == null) {
            return;
        }
        if (startTime == null || endTime == null) {
            throw new InvalidRequestException("INCOMPLETE_INTERVAL", "Send both startTime and endTime, or neither.");
        }
        checkInterval(startTime, endTime);
    }

    private void checkInterval(OffsetDateTime startTime, OffsetDateTime endTime) {
        if (!endTime.isAfter(startTime)) {
            throw new InvalidRequestException("INVALID_INTERVAL", "endTime must be after startTime.");
        }
    }

    private boolean matchesInterval(ParkingSpot spot, OffsetDateTime startTime, OffsetDateTime endTime,
                                    List<UUID> reservedSpotIds) {
        if (startTime == null) {
            return true;
        }
        return spot.isOpenDuring(startTime, endTime) && !reservedSpotIds.contains(spot.getId());
    }

    private void checkMaxPrice(BigDecimal maxPricePerHour) {
        if (maxPricePerHour != null && maxPricePerHour.signum() < 0) {
            throw new InvalidRequestException("INVALID_PRICE", "maxPricePerHour cannot be negative.");
        }
    }

    private boolean matchesMaxPrice(ParkingSpot spot, BigDecimal maxPricePerHour) {
        if (maxPricePerHour == null) {
            return true;
        }
        return spot.getPricePerHour().compareTo(maxPricePerHour) <= 0;
    }

    private ParkingType parseParkingTypeFilter(String parkingType) {
        if (parkingType == null) {
            return null;
        }
        try {
            return ParkingType.fromDbValue(parkingType);
        } catch (IllegalArgumentException e) {
            throw new InvalidRequestException("INVALID_PARKING_TYPE", "parkingType must be driveway, privateLot, garage or other.");
        }
    }

    private ParkingSpot getOwnedSpotForUpdate(UUID spotId, UUID ownerId) {
        ParkingSpot existing = parkingSpotRepository.findById(spotId)
                .orElseThrow(() -> new NotFoundException("SPOT_NOT_FOUND", "Parking spot not found."));
        if (!existing.getOwnerId().equals(ownerId)) {
            throw new ForbiddenException("NOT_SPOT_OWNER", "You do not own this parking spot.");
        }
        return parkingSpotRepository.findOwnedForUpdate(spotId, ownerId)
                .orElseThrow(() -> new NotFoundException("SPOT_NOT_FOUND", "Parking spot not found."));
    }

    // Saves the point on the map, or clears it when the address was not found,
    // so an old point is never shown for a new address.
    private void setCoordinates(ParkingSpot spot, Coordinates coordinates) {
        if (coordinates == null) {
            spot.setLatitude(null);
            spot.setLongitude(null);
        } else {
            spot.setLatitude(coordinates.latitude());
            spot.setLongitude(coordinates.longitude());
        }
    }

    private ParkingType toParkingType(String value) {
        return value == null ? null : ParkingType.fromDbValue(value);
    }

    private ParkingSpotResponse toResponse(ParkingSpot spot) {
        List<AvailabilityPeriodResponse> availability = spot.getAvailabilityWindows()
                .stream()
                .sorted(Comparator.comparing(AvailabilityWindow::getStartTime))
                .map(w -> new AvailabilityPeriodResponse(w.getStartTime(), w.getEndTime()))
                .toList();

        return new ParkingSpotResponse(
                spot.getId(),
                spot.getOwnerId(),
                spot.getTitle(),
                spot.getDescription(),
                spot.getAddress(),
                spot.getLatitude(),
                spot.getLongitude(),
                spot.getPhotoUrl(),
                spot.getPricePerHour(),
                spot.getParkingType() == null ? null : spot.getParkingType().getDbValue(),
                spot.getRestrictions(),
                spot.isActive(),
                spot.getCreatedAt(),
                availability
        );
    }

    private List<AvailabilityWindow> toValidatedWindows(List<AvailabilityPeriodRequest> requested) {
        List<AvailabilityPeriodRequest> periods = new ArrayList<>(requested);
        periods.sort(Comparator.comparing(AvailabilityPeriodRequest::startTime));

        for (int i = 0; i < periods.size(); i++) {
            AvailabilityPeriodRequest current = periods.get(i);
            if (!current.startTime().isBefore(current.endTime())) {
                throw new InvalidRequestException("INVALID_AVAILABILITY",
                        "Each availability window must end after it starts.");
            }
            if (i > 0 && periods.get(i - 1).endTime().isAfter(current.startTime())) {
                throw new InvalidRequestException("INVALID_AVAILABILITY",
                        "Availability windows cannot overlap.");
            }
        }
        return periods.stream()
                .map(p -> new AvailabilityWindow(p.startTime(), p.endTime()))
                .toList();
    }

    private User findUser(UUID callerId) {
        return userRepository.findById(callerId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "User not found"
                ));
    }
}