package com.squared.locus.service;

import com.squared.locus.domain.InvalidRequestException;
import com.squared.locus.dto.*;
import com.squared.locus.model.AvailabilityWindow;
import com.squared.locus.model.ParkingSpot;
import com.squared.locus.model.ParkingSpot.ParkingType;
import com.squared.locus.repository.ParkingSpotRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class ParkingSpotService {
    private final ParkingSpotRepository parkingSpotRepository;

    public ParkingSpotService(ParkingSpotRepository parkingSpotRepository) {
        this.parkingSpotRepository = parkingSpotRepository;
    }

    @Transactional
    public ParkingSpotResponse create(CreateParkingSpotRequest request) {
        ParkingSpot spot = new ParkingSpot(
                request.ownerId(),
                request.title(),
                request.description(),
                request.address(),
                request.photoUrl(),
                request.pricePerHour(),
                toParkingType(request.parkingType()),
                request.restrictions()
        );
        return toResponse(parkingSpotRepository.save(spot));
    }

    @Transactional
    public ParkingSpotResponse update(UUID spotId, UUID ownerId, UpdateParkingSpotRequest request) {
        ParkingSpot spot = getOwnedSpotForUpdate(spotId, ownerId);
        if (!spot.isActive()) {
            throw new BusinessRuleException("A deactivated parking spot cannot be updated.");
        }

        spot.setTitle(request.title());
        spot.setDescription(request.description());
        spot.setAddress(request.address());
        spot.setPhotoUrl(request.photoUrl());
        spot.setPricePerHour(request.pricePerHour());
        spot.setParkingType(toParkingType(request.parkingType()));
        spot.setRestrictions(request.restrictions());

        return toResponse(parkingSpotRepository.save(spot));
    }

    @Transactional
    public ParkingSpotResponse setAvailability(UUID spotId, UUID ownerId, SetAvailabilityRequest request) {
        ParkingSpot spot = getOwnedSpotForUpdate(spotId, ownerId);
        if (!spot.isActive()) {
            throw new BusinessRuleException("A deactivated parking spot cannot have its availability changed.");
        }

        List<AvailabilityPeriodRequest> periods = new ArrayList<>(request.periods());
        periods.sort(Comparator.comparing(AvailabilityPeriodRequest::startTime));

        for (int i = 0; i < periods.size(); i++) {
            AvailabilityPeriodRequest current = periods.get(i);
            if (!current.startTime().isBefore(current.endTime())) {
                throw new BusinessRuleException("Each availability period must end after it starts.");
            }
            if (i > 0 && periods.get(i - 1).endTime().isAfter(current.startTime())) {
                throw new BusinessRuleException("Availability periods cannot overlap.");
            }
        }

        // PUT replaces the complete availability schedule; orphanRemoval deletes the old rows.
        List<AvailabilityWindow> windows = periods.stream()
                .map(p -> new AvailabilityWindow(p.startTime(), p.endTime()))
                .toList();
        spot.replaceAvailabilityWindows(windows);

        return toResponse(parkingSpotRepository.save(spot));
    }

    @Transactional
    public ParkingSpotResponse deactivate(UUID spotId, UUID ownerId) {
        ParkingSpot spot = getOwnedSpotForUpdate(spotId, ownerId);
        spot.setActive(false);
        return toResponse(parkingSpotRepository.save(spot));
    }

    @Transactional(readOnly = true)
    public ParkingSpotResponse getDetails(UUID spotId) {
        ParkingSpot spot = parkingSpotRepository.findByIdAndActiveTrue(spotId)
                .orElseThrow(() -> new ResourceNotFoundException("Active parking spot not found."));
        return toResponse(spot);
    }

    @Transactional(readOnly = true)
    public List<ParkingSpotResponse> getOwnerListings(UUID ownerId) {
        return parkingSpotRepository.findAllByOwnerIdOrderByCreatedAtDesc(ownerId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ParkingSpotPageResponse search(int limit, int offset) {
        checkPaging(limit, offset);

        List<ParkingSpot> matches = parkingSpotRepository.findAllActiveWithWindows();

        int from = Math.min(offset, matches.size());
        int to = Math.min(from + limit, matches.size());

        List<ParkingSpotResponse> items = new ArrayList<>();
        for (ParkingSpot spot : matches.subList(from, to)) {
            items.add(toResponse(spot));
        }
        return new ParkingSpotPageResponse(items, matches.size(), limit, offset);
    }

    private void checkPaging(int limit, int offset) {
        if (limit < 1 || limit > 100) {
            throw new InvalidRequestException("INVALID_LIMIT", "limit must be between 1 and 100.");
        }
        if (offset < 0) {
            throw new InvalidRequestException("INVALID_OFFSET", "offset cannot be negative.");
        }
    }

    private ParkingSpot getOwnedSpotForUpdate(UUID spotId, UUID ownerId) {
        return parkingSpotRepository.findOwnedForUpdate(spotId, ownerId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Parking spot not found for this owner."
                ));
    }

    private ParkingType toParkingType(String value) {
        return value == null ? null : ParkingType.fromDbValue(value);
    }

    private ParkingSpotResponse toResponse(ParkingSpot spot) {
        List<AvailabilityPeriodResponse> availability = spot.getAvailabilityWindows()
                .stream()
                .sorted(Comparator.comparing(AvailabilityWindow::getStartTime))
                .map(period -> new AvailabilityPeriodResponse(
                        period.getId(), period.getStartTime(), period.getEndTime()
                ))
                .toList();

        return new ParkingSpotResponse(
                spot.getId(),
                spot.getOwnerId(),
                spot.getTitle(),
                spot.getDescription(),
                spot.getAddress(),
                spot.getPhotoUrl(),
                spot.getPricePerHour(),
                spot.getParkingType() == null ? null : spot.getParkingType().getDbValue(),
                spot.getRestrictions(),
                spot.isActive(),
                spot.getCreatedAt(),
                availability
        );
    }
}