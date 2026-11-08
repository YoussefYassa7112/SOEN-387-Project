package com.squared.locus.service;

import com.squared.locus.domain.InvalidRequestException;
import com.squared.locus.domain.NotFoundException;
import com.squared.locus.dto.AvailabilityResponse;
import com.squared.locus.dto.ParkingSpotPageResponse;
import com.squared.locus.model.AvailabilityWindow;
import com.squared.locus.model.ParkingSpot;
import com.squared.locus.model.ParkingSpot.ParkingType;
import com.squared.locus.repository.ParkingSpotRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/** Tests the search rules without a database: the repository is a mock. */
@ExtendWith(MockitoExtension.class)
class ParkingSpotSearchTest {

    private static final OffsetDateTime NINE = OffsetDateTime.parse("2026-11-12T09:00:00-05:00");
    private static final OffsetDateTime NOON = OffsetDateTime.parse("2026-11-12T12:00:00-05:00");
    private static final OffsetDateTime FIVE = OffsetDateTime.parse("2026-11-12T17:00:00-05:00");

    @Mock
    private ParkingSpotRepository repository;

    @InjectMocks
    private ParkingSpotService service;

    @Test
    void browseReturnsOnePageAndTheTotal() {
        when(repository.findAllActiveWithWindows()).thenReturn(List.of(
                spot("1 Main St", "5.00", ParkingType.GARAGE),
                spot("2 Main St", "5.00", ParkingType.GARAGE),
                spot("3 Main St", "5.00", ParkingType.GARAGE)));

        ParkingSpotPageResponse page = service.search(null, null, null, null, null, 2, 1);

        assertEquals(2, page.items().size());
        assertEquals("2 Main St", page.items().get(0).address());
        assertEquals(3, page.total());
    }

    @Test
    void offsetPastTheEndReturnsAnEmptyPage() {
        when(repository.findAllActiveWithWindows()).thenReturn(List.of(spot("1 Main St", "5.00", ParkingType.GARAGE)));

        ParkingSpotPageResponse page = service.search(null, null, null, null, null, 20, 50);

        assertEquals(0, page.items().size());
        assertEquals(1, page.total());
    }

    @Test
    void limitOutsideTheAllowedRangeIsRejected() {
        assertThrows(InvalidRequestException.class,
                () -> service.search(null, null, null, null, null, 101, 0));
    }

    @Test
    void locationMatchesPartOfTheAddressIgnoringCase() {
        when(repository.findAllActiveWithWindows()).thenReturn(List.of(
                spot("1455 De Maisonneuve Blvd, Montreal", "5.00", ParkingType.GARAGE),
                spot("10 Bank St, Ottawa", "5.00", ParkingType.GARAGE)));

        ParkingSpotPageResponse page = service.search("montreal", null, null, null, null, 20, 0);

        assertEquals(1, page.total());
    }

    @Test
    void blankLocationIsRejected() {
        assertThrows(InvalidRequestException.class,
                () -> service.search("   ", null, null, null, null, 20, 0));
    }

    @Test
    void intervalKeepsOnlySpotsThatAreOpenAndNotReserved() {
        ParkingSpot free = spot("1 Main St", "5.00", ParkingType.GARAGE);
        ParkingSpot reserved = spot("2 Main St", "5.00", ParkingType.GARAGE);
        when(repository.findAllActiveWithWindows()).thenReturn(List.of(free, reserved));
        when(repository.findReservedSpotIds(NINE, NOON)).thenReturn(List.of(reserved.getId()));

        ParkingSpotPageResponse page = service.search(null, NINE, NOON, null, null, 20, 0);

        assertEquals(1, page.total());
        assertEquals("1 Main St", page.items().get(0).address());
    }

    @Test
    void intervalOutsideTheOwnersWindowsFindsNothing() {
        OffsetDateTime sixPm = FIVE.plusHours(1);
        when(repository.findAllActiveWithWindows()).thenReturn(List.of(spot("1 Main St", "5.00", ParkingType.GARAGE)));
        when(repository.findReservedSpotIds(NOON, sixPm)).thenReturn(List.of());

        ParkingSpotPageResponse page = service.search(null, NOON, sixPm, null, null, 20, 0);

        assertEquals(0, page.total());
    }

    @Test
    void sendingOnlyOneTimeIsRejected() {
        assertThrows(InvalidRequestException.class,
                () -> service.search(null, NINE, null, null, null, 20, 0));
    }

    @Test
    void endBeforeStartIsRejected() {
        assertThrows(InvalidRequestException.class,
                () -> service.search(null, NOON, NINE, null, null, 20, 0));
    }

    @Test
    void availableSpotHasNoReason() {
        ParkingSpot spot = spot("1 Main St", "5.00", ParkingType.GARAGE);
        when(repository.findById(spot.getId())).thenReturn(Optional.of(spot));
        when(repository.findReservedSpotIds(NINE, NOON)).thenReturn(List.of());

        AvailabilityResponse result = service.checkAvailability(spot.getId(), NINE, NOON);

        assertTrue(result.available());
        assertNull(result.reason());
    }

    @Test
    void reservedSpotSaysAlreadyReserved() {
        ParkingSpot spot = spot("1 Main St", "5.00", ParkingType.GARAGE);
        when(repository.findById(spot.getId())).thenReturn(Optional.of(spot));
        when(repository.findReservedSpotIds(NINE, NOON)).thenReturn(List.of(spot.getId()));

        AvailabilityResponse result = service.checkAvailability(spot.getId(), NINE, NOON);

        assertFalse(result.available());
        assertEquals("alreadyReserved", result.reason());
    }

    @Test
    void intervalOutsideWindowsSaysOutsideOwnerAvailability() {
        ParkingSpot spot = spot("1 Main St", "5.00", ParkingType.GARAGE);
        when(repository.findById(spot.getId())).thenReturn(Optional.of(spot));

        AvailabilityResponse result = service.checkAvailability(spot.getId(), NOON, FIVE.plusHours(1));

        assertEquals("outsideOwnerAvailability", result.reason());
    }

    @Test
    void deactivatedSpotSaysInactive() {
        ParkingSpot spot = spot("1 Main St", "5.00", ParkingType.GARAGE);
        spot.setActive(false);
        when(repository.findById(spot.getId())).thenReturn(Optional.of(spot));

        AvailabilityResponse result = service.checkAvailability(spot.getId(), NINE, NOON);

        assertEquals("inactive", result.reason());
    }

    @Test
    void unknownSpotIsNotFound() {
        UUID unknownId = UUID.randomUUID();
        when(repository.findById(unknownId)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.checkAvailability(unknownId, NINE, NOON));
    }

    private ParkingSpot spot(String address, String price, ParkingType type) {
        ParkingSpot spot = new ParkingSpot(UUID.randomUUID(), "Spot", null, address, null,
                new BigDecimal(price), type, null);
        spot.setId(UUID.randomUUID());
        spot.replaceAvailabilityWindows(List.of(new AvailabilityWindow(NINE, FIVE)));
        return spot;
    }
}
