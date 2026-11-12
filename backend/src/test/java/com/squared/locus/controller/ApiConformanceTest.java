package com.squared.locus.controller;

import com.atlassian.oai.validator.OpenApiInteractionValidator;
import com.atlassian.oai.validator.report.LevelResolver;
import com.atlassian.oai.validator.report.ValidationReport;
import com.squared.locus.config.JwtConfig;
import com.squared.locus.config.SecurityConfig;
import com.squared.locus.domain.ConflictException;
import com.squared.locus.domain.InvalidRequestException;
import com.squared.locus.domain.NotFoundException;
import com.squared.locus.dto.*;
import com.squared.locus.service.AuthService;
import com.squared.locus.service.ForbiddenException;
import com.squared.locus.service.ParkingSpotService;
import com.squared.locus.service.ReservationService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static com.atlassian.oai.validator.mockmvc.OpenApiValidationMatchers.openApi;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * LOCUS-09: API conformance tests.
 *
 * Every test sends a request to one endpoint and then checks the request and the
 * response against our published contract (src/main/resources/static/openapi.yaml).
 * If a status code, a field name, a field type or the error shape is different from
 * the contract, the test fails.
 *
 * The services are mocked, so these tests do not need the Neon database.
 * Only the web layer runs: controllers, the real security config and the error handler.
 */
@WebMvcTest(
        controllers = {AuthController.class, ParkingSpotController.class, ReservationController.class},
        properties = {
                "spring.config.import=",
                "locus.google.client-id=test",
                "locus.jwt.secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
        })
@Import({SecurityConfig.class, JwtConfig.class})
class ApiConformanceTest {

    // The validator reads the contract file from the classpath.
    // 1. We turn off its token check because MockMvc's jwt() logs the user in without
    //    sending an Authorization header. The 401 tests below check the token rule instead.
    // 2. We turn off its extra "no unknown fields" guess. It breaks ParkingSpot, which is
    //    built with allOf in the contract. Schemas that say additionalProperties: false
    //    (Reservation, ApiError, ...) are still checked.
    private final OpenApiInteractionValidator contract = OpenApiInteractionValidator
            .createFor("static/openapi.yaml")
            .withLevelResolver(LevelResolver.create()
                    .withLevel("validation.request.security", ValidationReport.Level.IGNORE)
                    .withLevel("validation.schema.additionalProperties", ValidationReport.Level.IGNORE)
                    .build())
            .build();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private ParkingSpotService parkingSpotService;

    @MockitoBean
    private ReservationService reservationService;

    private final UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private final UUID spotId = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private final UUID reservationId = UUID.fromString("44444444-4444-4444-4444-444444444444");

    private final OffsetDateTime start = OffsetDateTime.parse("2026-12-01T09:00:00-05:00");
    private final OffsetDateTime end = OffsetDateTime.parse("2026-12-01T18:00:00-05:00");

    private final String spotBody = """
            {
                "title": "Driveway near Concordia",
                "address": "1455 De Maisonneuve Blvd W, Montreal",
                "pricePerHour": 10.00,
                "parkingType": "driveway",
                "availabilityWindows": [
                    { "startTime": "2026-12-01T09:00:00-05:00", "endTime": "2026-12-01T18:00:00-05:00" }
                ]
            }
            """;

    private final String intervalBody = """
            { "startTime": "2026-12-01T10:00:00-05:00", "endTime": "2026-12-01T12:00:00-05:00" }
            """;

    // ---- Sample objects that the mocked services return ----

    // The optional fields description, photoUrl and restrictions are left empty (null)
    // on purpose, to check that empty fields still follow the contract.
    private ParkingSpotResponse sampleSpot() {
        return new ParkingSpotResponse(
                spotId, userId,
                "Driveway near Concordia",
                null,
                "1455 De Maisonneuve Blvd W, Montreal",
                null,
                new BigDecimal("10.00"),
                "driveway",
                null,
                true,
                OffsetDateTime.parse("2026-11-01T12:00:00-05:00"),
                List.of(new AvailabilityPeriodResponse(start, end)));
    }

    private ReservationResponse sampleReservation() {
        return new ReservationResponse(
                reservationId, spotId, userId,
                OffsetDateTime.parse("2026-12-01T10:00:00-05:00"),
                OffsetDateTime.parse("2026-12-01T12:00:00-05:00"),
                "pendingPayment",
                new BigDecimal("20.00"),
                "CAD",
                OffsetDateTime.parse("2026-11-01T12:00:00-05:00"));
    }

    // ---- Authentication ----

    @Test
    void signInWithGoogleReturnsToken() throws Exception {
        when(authService.signInWithGoogle("google-id-token"))
                .thenReturn(new AuthService.TokenResult("locus-jwt", "Bearer", 3600));

        mockMvc.perform(post("/api/v1/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"idToken\": \"google-id-token\" }"))
                .andExpect(status().isOk())
                .andExpect(openApi().isValid(contract));
    }

    @Test
    void signInWithoutIdTokenIsBadRequest() throws Exception {
        // This request breaks the contract on purpose, so we only check the response.
        mockMvc.perform(post("/api/v1/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ }"))
                .andExpect(status().isBadRequest())
                .andExpect(openApi().isValid(responseOnly()));
    }

    // ---- Parking spots ----

    @Test
    void searchParkingSpotsReturnsPage() throws Exception {
        when(parkingSpotService.search(any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(new ParkingSpotPageResponse(List.of(sampleSpot()), 1, 20, 0));

        mockMvc.perform(get("/api/v1/parking-spots").param("location", "Montreal"))
                .andExpect(status().isOk())
                .andExpect(openApi().isValid(contract));
    }

    @Test
    void searchWithBadFilterIsBadRequest() throws Exception {
        when(parkingSpotService.search(any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenThrow(new InvalidRequestException("INCOMPLETE_INTERVAL", "Send both startTime and endTime, or neither."));

        mockMvc.perform(get("/api/v1/parking-spots").param("startTime", "2026-12-01T10:00:00-05:00"))
                .andExpect(status().isBadRequest())
                .andExpect(openApi().isValid(contract));
    }

    @Test
    void createParkingSpotReturnsCreated() throws Exception {
        when(parkingSpotService.create(eq(userId), any(CreateParkingSpotRequest.class)))
                .thenReturn(sampleSpot());

        mockMvc.perform(post("/api/v1/parking-spots")
                        .with(jwt().jwt(token -> token.subject(userId.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(spotBody))
                .andExpect(status().isCreated())
                .andExpect(openApi().isValid(contract));
    }

    @Test
    void createParkingSpotWithoutTokenIsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/parking-spots")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(spotBody))
                .andExpect(status().isUnauthorized())
                .andExpect(openApi().isValid(contract));
    }

    @Test
    void createParkingSpotWithInvalidTokenIsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/parking-spots")
                        .header("Authorization", "Bearer not-a-real-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(spotBody))
                .andExpect(status().isUnauthorized())
                .andExpect(openApi().isValid(contract));
    }

    @Test
    void getParkingSpotReturnsDetails() throws Exception {
        when(parkingSpotService.getDetails(spotId)).thenReturn(sampleSpot());

        mockMvc.perform(get("/api/v1/parking-spots/{spotId}", spotId))
                .andExpect(status().isOk())
                .andExpect(openApi().isValid(contract));
    }

    @Test
    void getUnknownParkingSpotIsNotFound() throws Exception {
        when(parkingSpotService.getDetails(spotId))
                .thenThrow(new NotFoundException("SPOT_NOT_FOUND", "Active parking spot not found."));

        mockMvc.perform(get("/api/v1/parking-spots/{spotId}", spotId))
                .andExpect(status().isNotFound())
                .andExpect(openApi().isValid(contract));
    }

    @Test
    void updateParkingSpotReturnsSpot() throws Exception {
        when(parkingSpotService.update(eq(spotId), eq(userId), any(UpdateParkingSpotRequest.class)))
                .thenReturn(sampleSpot());

        mockMvc.perform(patch("/api/v1/parking-spots/{spotId}", spotId)
                        .with(jwt().jwt(token -> token.subject(userId.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"title\": \"Driveway near Concordia\" }"))
                .andExpect(status().isOk())
                .andExpect(openApi().isValid(contract));
    }

    @Test
    void updateSomeoneElsesParkingSpotIsForbidden() throws Exception {
        when(parkingSpotService.update(eq(spotId), eq(userId), any(UpdateParkingSpotRequest.class)))
                .thenThrow(new ForbiddenException("NOT_SPOT_OWNER", "You do not own this parking spot."));

        mockMvc.perform(patch("/api/v1/parking-spots/{spotId}", spotId)
                        .with(jwt().jwt(token -> token.subject(userId.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"title\": \"Not my spot\" }"))
                .andExpect(status().isForbidden())
                .andExpect(openApi().isValid(contract));
    }

    @Test
    void deactivateParkingSpotReturnsNoContent() throws Exception {
        mockMvc.perform(delete("/api/v1/parking-spots/{spotId}", spotId)
                        .with(jwt().jwt(token -> token.subject(userId.toString()))))
                .andExpect(status().isNoContent())
                .andExpect(openApi().isValid(contract));
    }

    @Test
    void checkAvailabilityReturnsSnapshot() throws Exception {
        when(parkingSpotService.checkAvailability(eq(spotId), any(), any()))
                .thenReturn(new AvailabilityResponse(spotId, start, end, true, null));

        mockMvc.perform(get("/api/v1/parking-spots/{spotId}/availability", spotId)
                        .param("startTime", "2026-12-01T09:00:00-05:00")
                        .param("endTime", "2026-12-01T18:00:00-05:00"))
                .andExpect(status().isOk())
                .andExpect(openApi().isValid(contract));
    }

    @Test
    void checkAvailabilityWithoutTimesIsBadRequest() throws Exception {
        // startTime and endTime are required, so only the response is checked.
        mockMvc.perform(get("/api/v1/parking-spots/{spotId}/availability", spotId))
                .andExpect(status().isBadRequest())
                .andExpect(openApi().isValid(responseOnly()));
    }

    @Test
    void setAvailabilityReturnsSpot() throws Exception {
        when(parkingSpotService.setAvailability(eq(spotId), eq(userId), any(SetAvailabilityRequest.class)))
                .thenReturn(sampleSpot());

        mockMvc.perform(put("/api/v1/parking-spots/{spotId}/availability", spotId)
                        .with(jwt().jwt(token -> token.subject(userId.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"periods\": [ " + intervalBody + " ] }"))
                .andExpect(status().isOk())
                .andExpect(openApi().isValid(contract));
    }

    @Test
    void setAvailabilityOnInactiveSpotIsConflict() throws Exception {
        when(parkingSpotService.setAvailability(eq(spotId), eq(userId), any(SetAvailabilityRequest.class)))
                .thenThrow(new ConflictException("SPOT_INACTIVE", "A deactivated parking spot cannot have its availability changed."));

        mockMvc.perform(put("/api/v1/parking-spots/{spotId}/availability", spotId)
                        .with(jwt().jwt(token -> token.subject(userId.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"periods\": [ " + intervalBody + " ] }"))
                .andExpect(status().isConflict())
                .andExpect(openApi().isValid(contract));
    }

    @Test
    void listMyParkingSpotsReturnsArray() throws Exception {
        when(parkingSpotService.getOwnerListings(userId)).thenReturn(List.of(sampleSpot()));

        mockMvc.perform(get("/api/v1/me/parking-spots")
                        .with(jwt().jwt(token -> token.subject(userId.toString()))))
                .andExpect(status().isOk())
                .andExpect(openApi().isValid(contract));
    }

    // ---- Reservations ----

    @Test
    void createReservationReturnsAccepted() throws Exception {
        when(reservationService.create(eq(spotId), eq(userId), eq("key-12345"), any(CreateReservationRequest.class)))
                .thenReturn(sampleReservation());

        mockMvc.perform(post("/api/v1/parking-spots/{spotId}/reservations", spotId)
                        .with(jwt().jwt(token -> token.subject(userId.toString())))
                        .header("Idempotency-Key", "key-12345")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(intervalBody))
                .andExpect(status().isAccepted())
                .andExpect(openApi().isValid(contract));
    }

    @Test
    void createOverlappingReservationIsConflict() throws Exception {
        when(reservationService.create(eq(spotId), eq(userId), eq("key-12345"), any(CreateReservationRequest.class)))
                .thenThrow(new ConflictException("PARKING_SPOT_UNAVAILABLE", "The parking spot is already reserved."));

        mockMvc.perform(post("/api/v1/parking-spots/{spotId}/reservations", spotId)
                        .with(jwt().jwt(token -> token.subject(userId.toString())))
                        .header("Idempotency-Key", "key-12345")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(intervalBody))
                .andExpect(status().isConflict())
                .andExpect(openApi().isValid(contract));
    }

    @Test
    void getReservationReturnsReservation() throws Exception {
        when(reservationService.getById(reservationId, userId)).thenReturn(sampleReservation());

        mockMvc.perform(get("/api/v1/reservations/{reservationId}", reservationId)
                        .with(jwt().jwt(token -> token.subject(userId.toString()))))
                .andExpect(status().isOk())
                .andExpect(openApi().isValid(contract));
    }

    @Test
    void getReservationWithoutTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/reservations/{reservationId}", reservationId))
                .andExpect(status().isUnauthorized())
                .andExpect(openApi().isValid(contract));
    }

    // Same contract, but the request checks are switched off.
    // Used only by tests that send a bad request on purpose.
    private OpenApiInteractionValidator responseOnly() {
        return OpenApiInteractionValidator
                .createFor("static/openapi.yaml")
                .withLevelResolver(LevelResolver.create()
                        .withLevel("validation.request", ValidationReport.Level.IGNORE)
                        .build())
                .build();
    }
}
