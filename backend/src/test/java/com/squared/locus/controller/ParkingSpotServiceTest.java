package com.squared.locus.service;

import com.squared.locus.domain.ConflictException;
import com.squared.locus.domain.InvalidRequestException;
import com.squared.locus.domain.NotFoundException;
import com.squared.locus.dto.AvailabilityPeriodRequest;
import com.squared.locus.dto.SetAvailabilityRequest;
import com.squared.locus.dto.UpdateParkingSpotRequest;
import com.squared.locus.model.AvailabilityWindow;
import com.squared.locus.model.ParkingSpot;
import com.squared.locus.model.ParkingSpot.ParkingType;
import com.squared.locus.model.User;
import com.squared.locus.repository.ParkingSpotRepository;
import com.squared.locus.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ParkingSpotServiceTest {

    private static final OffsetDateTime START = OffsetDateTime.of(2026, 11, 14, 9, 0, 0, 0, ZoneOffset.UTC);

    @Mock
    private ParkingSpotRepository parkingSpotRepository;
    @Mock
    private UserRepository userRepository;

    private ParkingSpotService service;

    private User owner;
    private User stranger;
    private UUID ownerId;
    private UUID strangerId;
    private UUID spotId;
    private ParkingSpot spot;

    @BeforeEach
    void setUp() {
        service = new ParkingSpotService(parkingSpotRepository, userRepository);

        owner = new User("google-owner", "owner@example.com", "Owner");
        stranger = new User("google-stranger", "stranger@example.com", "Stranger");
        ownerId = owner.getId();
        strangerId = stranger.getId();

        spotId = UUID.randomUUID();

        spot = new ParkingSpot(
                ownerId,
                "Driveway near metro",
                "Fits one small car",
                "123 Rue Saint-Denis, Montreal",
                "https://example.com/photo.jpg",
                new BigDecimal("5.00"),
                ParkingType.DRIVEWAY,
                "No trucks"
        );
        spot.replaceAvailabilityWindows(List.of(
                new AvailabilityWindow(START, START.plusHours(4))
        ));
    }

    @ParameterizedTest
    @ValueSource(strings = {"update", "setAvailability", "deactivate"})
    void owner_canDoOperation(String operation) {
        whenUserExists(owner);
        whenSpotExists();
        when(parkingSpotRepository.findOwnedForUpdate(spotId, ownerId)).thenReturn(Optional.of(spot));
        when(parkingSpotRepository.save(any(ParkingSpot.class))).thenAnswer(call -> call.getArgument(0));

        runOperation(operation, ownerId);

        if (operation.equals("update")) {
            assertThat(spot.getTitle()).isEqualTo("New title");
            assertThat(spot.getPricePerHour()).isEqualByComparingTo("9.50");
        }
        if (operation.equals("setAvailability")) {
            assertThat(spot.getAvailabilityWindows()).hasSize(1);
            AvailabilityWindow newWindow = spot.getAvailabilityWindows().get(0);
            assertThat(newWindow.getStartTime()).isEqualTo(START.plusDays(1));
            assertThat(newWindow.getEndTime()).isEqualTo(START.plusDays(1).plusHours(3));
        }
        if (operation.equals("deactivate")) {
            assertThat(spot.isActive()).isFalse();
        }
        verify(parkingSpotRepository, times(1)).save(spot);
    }

    @ParameterizedTest
    @ValueSource(strings = {"update", "setAvailability", "deactivate"})
    void otherUser_isBlocked_nothingSaved_spotUnchanged(String operation) {
        whenUserExists(stranger);
        whenSpotExists();

        String titleBefore = spot.getTitle();
        BigDecimal priceBefore = spot.getPricePerHour();
        boolean activeBefore = spot.isActive();
        int windowCountBefore = spot.getAvailabilityWindows().size();
        OffsetDateTime windowStartBefore = spot.getAvailabilityWindows().get(0).getStartTime();

        assertThatThrownBy(() -> runOperation(operation, strangerId))
                .isInstanceOf(ForbiddenException.class)
                .hasFieldOrPropertyWithValue("code", "NOT_SPOT_OWNER");

        verify(parkingSpotRepository, never()).save(any());

        assertThat(spot.getTitle()).isEqualTo(titleBefore);
        assertThat(spot.getPricePerHour()).isEqualByComparingTo(priceBefore);
        assertThat(spot.isActive()).isEqualTo(activeBefore);
        assertThat(spot.getAvailabilityWindows()).hasSize(windowCountBefore);
        assertThat(spot.getAvailabilityWindows().get(0).getStartTime()).isEqualTo(windowStartBefore);
    }

    @ParameterizedTest
    @ValueSource(strings = {"update", "setAvailability", "deactivate"})
    void spotDoesNotExist_throwsSpotNotFound(String operation) {
        whenUserExists(owner);
        when(parkingSpotRepository.findById(spotId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> runOperation(operation, ownerId))
                .isInstanceOf(NotFoundException.class)
                .hasFieldOrPropertyWithValue("code", "SPOT_NOT_FOUND");

        verify(parkingSpotRepository, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"update", "setAvailability", "deactivate"})
    void userDoesNotExist_throwsNotFound(String operation) {
        when(userRepository.findById(ownerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> runOperation(operation, ownerId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> {
                    ResponseStatusException e = (ResponseStatusException) error;
                    assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                });

        verify(parkingSpotRepository, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"update", "setAvailability"})
    void inactiveSpot_cannotBeChanged(String operation) {
        spot.setActive(false);
        whenUserExists(owner);
        whenSpotExists();
        when(parkingSpotRepository.findOwnedForUpdate(spotId, ownerId)).thenReturn(Optional.of(spot));

        assertThatThrownBy(() -> runOperation(operation, ownerId))
                .isInstanceOf(ConflictException.class)
                .hasFieldOrPropertyWithValue("code", "SPOT_INACTIVE");

        verify(parkingSpotRepository, never()).save(any());
    }

    @Test
    void update_withNoFields_throwsEmptyUpdate() {
        whenUserExists(owner);

        UpdateParkingSpotRequest emptyRequest =
                new UpdateParkingSpotRequest(null, null, null, null, null, null, null, null);

        assertThatThrownBy(() -> service.update(spotId, ownerId, emptyRequest))
                .isInstanceOf(InvalidRequestException.class)
                .hasFieldOrPropertyWithValue("code", "EMPTY_UPDATE");

        verify(parkingSpotRepository, never()).save(any());
    }

    @Test
    void setAvailability_overlappingWindows_throwsInvalidAvailability() {
        whenUserExists(owner);

        SetAvailabilityRequest request = new SetAvailabilityRequest(List.of(
                new AvailabilityPeriodRequest(START, START.plusHours(3)),
                new AvailabilityPeriodRequest(START.plusHours(2), START.plusHours(5))
        ));

        assertThatThrownBy(() -> service.setAvailability(spotId, ownerId, request))
                .isInstanceOf(InvalidRequestException.class)
                .hasFieldOrPropertyWithValue("code", "INVALID_AVAILABILITY");

        verify(parkingSpotRepository, never()).save(any());
    }

    @Test
    void setAvailability_endBeforeStart_throwsInvalidAvailability() {
        whenUserExists(owner);

        SetAvailabilityRequest request = new SetAvailabilityRequest(List.of(
                new AvailabilityPeriodRequest(START.plusHours(3), START)
        ));

        assertThatThrownBy(() -> service.setAvailability(spotId, ownerId, request))
                .isInstanceOf(InvalidRequestException.class)
                .hasFieldOrPropertyWithValue("code", "INVALID_AVAILABILITY");

        verify(parkingSpotRepository, never()).save(any());
    }

    @Test
    void deactivate_alreadyInactiveSpot_isStillAllowed() {
        spot.setActive(false);
        whenUserExists(owner);
        whenSpotExists();
        when(parkingSpotRepository.findOwnedForUpdate(spotId, ownerId)).thenReturn(Optional.of(spot));
        when(parkingSpotRepository.save(any(ParkingSpot.class))).thenAnswer(call -> call.getArgument(0));

        service.deactivate(spotId, ownerId);

        assertThat(spot.isActive()).isFalse();
        verify(parkingSpotRepository).save(spot);
    }

    private void whenUserExists(User user) {
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
    }

    private void whenSpotExists() {
        when(parkingSpotRepository.findById(spotId)).thenReturn(Optional.of(spot));
    }

    private void runOperation(String operation, UUID callerId) {
        if (operation.equals("update")) {
            UpdateParkingSpotRequest request = new UpdateParkingSpotRequest(
                    "New title", null, null, null, new BigDecimal("9.50"), null, null, null);
            service.update(spotId, callerId, request);

        } else if (operation.equals("setAvailability")) {
            SetAvailabilityRequest request = new SetAvailabilityRequest(List.of(
                    new AvailabilityPeriodRequest(START.plusDays(1), START.plusDays(1).plusHours(3))
            ));
            service.setAvailability(spotId, callerId, request);

        } else if (operation.equals("deactivate")) {
            service.deactivate(spotId, callerId);

        } else {
            throw new IllegalArgumentException("Unknown operation: " + operation);
        }
    }
}