package com.squared.locus.service;

import com.squared.locus.domain.InvalidRequestException;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    private ParkingSpot spot(String address, String price, ParkingType type) {
        ParkingSpot spot = new ParkingSpot(UUID.randomUUID(), "Spot", null, address, null,
                new BigDecimal(price), type, null);
        spot.setId(UUID.randomUUID());
        spot.replaceAvailabilityWindows(List.of(new AvailabilityWindow(NINE, FIVE)));
        return spot;
    }
}
