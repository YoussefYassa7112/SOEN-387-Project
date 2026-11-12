package com.squared.locus.controller;

import com.squared.locus.dto.*;
import com.squared.locus.service.ForbiddenException;
import com.squared.locus.service.ParkingSpotService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ParkingSpotController.class)
class ParkingSpotSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ParkingSpotService parkingSpotService;

    private final UUID userA = UUID.fromString(
            "11111111-1111-1111-1111-111111111111");

    private final UUID userB = UUID.fromString(
            "22222222-2222-2222-2222-222222222222");

    private final UUID spotId = UUID.fromString(
            "33333333-3333-3333-3333-333333333333");


    // Valid creation request based on the OpenAPI contract.
    private final String createRequest = """
            {
                "title": "Parking Spot",
                "address": "Montreal",
                "pricePerHour": 10.00,
                "parkingType": "driveway",
                "availabilityWindows": [
                    {
                        "startTime": "2026-11-12T09:00:00-05:00",
                        "endTime": "2026-11-12T18:00:00-05:00"
                    }
                ]
            }
            """;


    // 1. Unauthenticated user cannot create a listing.

    @Test
    void unauthenticatedUserCannotCreateListing() throws Exception {

        mockMvc.perform(post("/api/v1/parking-spots")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequest))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(parkingSpotService);
    }


    // 2. Unauthenticated user cannot retrieve their own listings.

    @Test
    void unauthenticatedUserCannotGetOwnListings() throws Exception {

        mockMvc.perform(get("/api/v1/me/parking-spots"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(parkingSpotService);
    }


    // 3. Unauthenticated user cannot update a listing.

    @Test
    void unauthenticatedUserCannotUpdateListing() throws Exception {

        mockMvc.perform(patch("/api/v1/parking-spots/{spotId}", spotId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "title": "Updated Parking Spot"
                                }
                                """))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(parkingSpotService);
    }


    // 4. Unauthenticated user cannot deactivate a listing.

    @Test
    void unauthenticatedUserCannotDeactivateListing() throws Exception {

        mockMvc.perform(delete("/api/v1/parking-spots/{spotId}", spotId)
                        .with(csrf()))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(parkingSpotService);
    }


    // 5. Owner ID spoofing attempt.

    @Test
    void createListingUsesAuthenticatedUserIdNotSpoofedOwnerId()
            throws Exception {

        String spoofedRequest = """
                {
                    "ownerId": "22222222-2222-2222-2222-222222222222",
                    "title": "Parking Spot",
                    "address": "Montreal",
                    "pricePerHour": 10.00,
                    "parkingType": "driveway",
                    "availabilityWindows": [
                        {
                            "startTime": "2026-11-12T09:00:00-05:00",
                            "endTime": "2026-11-12T18:00:00-05:00"
                        }
                    ]
                }
                """;

        mockMvc.perform(post("/api/v1/parking-spots")
                .with(jwt().jwt(jwt ->
                        jwt.subject(userA.toString())))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(spoofedRequest));

        // The authenticated caller is userA.
        // The service must receive userA as the owner.
        verify(parkingSpotService).create(
                eq(userA),
                any(CreateParkingSpotRequest.class));
    }


    // 6. User A cannot update User B's listing.

    @Test
    void userCannotUpdateAnotherUsersListing() throws Exception {

        doThrow(new ForbiddenException(
                "NOT_OWNER",
                "You do not own this listing."))
                .when(parkingSpotService)
                .update(
                        eq(spotId),
                        eq(userA),
                        any(UpdateParkingSpotRequest.class));

        mockMvc.perform(patch("/api/v1/parking-spots/{spotId}", spotId)
                        .with(jwt().jwt(jwt ->
                                jwt.subject(userA.toString())))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "title": "Updated Parking Spot"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_OWNER"));

        verify(parkingSpotService).update(
                eq(spotId),
                eq(userA),
                any(UpdateParkingSpotRequest.class));
    }


    // 7. User A cannot modify User B's availability.

    @Test
    void userCannotModifyAnotherUsersAvailability() throws Exception {

        doThrow(new ForbiddenException(
                "NOT_OWNER",
                "You do not own this listing."))
                .when(parkingSpotService)
                .setAvailability(
                        eq(spotId),
                        eq(userA),
                        any(SetAvailabilityRequest.class));

        mockMvc.perform(put(
                        "/api/v1/parking-spots/{spotId}/availability",
                        spotId)
                        .with(jwt().jwt(jwt ->
                                jwt.subject(userA.toString())))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "periods": [
                                        {
                                            "startTime": "2026-11-12T09:00:00-05:00",
                                            "endTime": "2026-11-12T18:00:00-05:00"
                                        }
                                    ]
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_OWNER"));

        verify(parkingSpotService).setAvailability(
                eq(spotId),
                eq(userA),
                any(SetAvailabilityRequest.class));
    }


    // 8. User A cannot deactivate User B's listing.

    @Test
    void userCannotDeactivateAnotherUsersListing() throws Exception {

        doThrow(new ForbiddenException(
                "NOT_OWNER",
                "You do not own this listing."))
                .when(parkingSpotService)
                .deactivate(spotId, userA);

        mockMvc.perform(delete(
                        "/api/v1/parking-spots/{spotId}",
                        spotId)
                        .with(jwt().jwt(jwt ->
                                jwt.subject(userA.toString())))
                        .with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_OWNER"));

        verify(parkingSpotService).deactivate(spotId, userA);
    }
}