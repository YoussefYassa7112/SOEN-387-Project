package com.squared.locus.controller;

import com.squared.locus.config.JwtConfig;
import com.squared.locus.config.SecurityConfig;
import com.squared.locus.dto.CreateParkingSpotRequest;
import com.squared.locus.service.ParkingSpotService;
import com.squared.locus.service.ReservationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// LOCUS-08: bad, missing or malicious input always gets a clean error in the ApiError
// shape, never a crash and never a stack trace (course brief, non-functional requirements).
@WebMvcTest(controllers = {ParkingSpotController.class, ReservationController.class}, properties = {
        "spring.config.import=", "locus.jwt.secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "locus.jwt.issuer=locus-api"
})
@Import({SecurityConfig.class, JwtConfig.class})
class MaliciousInputTest {

    @Autowired MockMvc mvc;
    @MockitoBean ParkingSpotService spots;
    @MockitoBean ReservationService reservations;

    private final UUID user = UUID.randomUUID();

    @Test
    void pathIdThatIsNotAUuid() throws Exception {
        expectCleanError(mvc.perform(get("/api/v1/parking-spots/1 OR 1=1")), 400, "INVALID_PARAMETER");
        verifyNoInteractions(spots);
    }

    @Test
    void queryValueWithTheWrongType() throws Exception {
        expectCleanError(mvc.perform(get("/api/v1/parking-spots").param("maxPricePerHour", "free")),
                400, "INVALID_PARAMETER");
    }

    @Test
    void bodyThatIsNotJson() throws Exception {
        expectCleanError(mvc.perform(asUser(post("/api/v1/parking-spots"))
                .header("Idempotency-Key", "attack-key-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ this is not json")), 400, "MALFORMED_REQUEST_BODY");
        verifyNoInteractions(spots);
    }

    @Test
    void numberFieldWithText() throws Exception {
        expectCleanError(mvc.perform(asUser(post("/api/v1/parking-spots"))
                .header("Idempotency-Key", "attack-key-2")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"x\",\"address\":\"y\",\"pricePerHour\":\"DROP TABLE users\","
                        + "\"availabilityWindows\":[]}")), 400, "MALFORMED_REQUEST_BODY");
        verifyNoInteractions(spots);
    }

    @Test
    void hugeTextFieldIsRejectedByValidation() throws Exception {
        String hugeTitle = "A".repeat(100_000);
        expectCleanError(mvc.perform(asUser(post("/api/v1/parking-spots"))
                .header("Idempotency-Key", "attack-key-3")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"" + hugeTitle + "\",\"address\":\"1 Main St\",\"pricePerHour\":5,"
                        + "\"availabilityWindows\":[{\"startTime\":\"2026-12-01T10:00:00Z\","
                        + "\"endTime\":\"2026-12-01T12:00:00Z\"}]}")), 400, "VALIDATION_FAILED");
        verifyNoInteractions(spots);
    }

    @Test
    void missingIdempotencyKey() throws Exception {
        expectCleanError(mvc.perform(asUser(post("/api/v1/parking-spots/{id}/reservations", UUID.randomUUID()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"startTime\":\"2026-12-01T10:00:00Z\",\"endTime\":\"2026-12-01T12:00:00Z\"}")),
                400, "MISSING_HEADER");
        verifyNoInteractions(reservations);
    }

    @Test
    void wrongContentType() throws Exception {
        expectCleanError(mvc.perform(asUser(post("/api/v1/parking-spots"))
                .header("Idempotency-Key", "attack-key-4")
                .contentType(MediaType.TEXT_XML)
                .content("<spot/>")), 415, "UNSUPPORTED_MEDIA_TYPE");
    }

    @Test
    void wrongHttpMethod() throws Exception {
        expectCleanError(mvc.perform(asUser(put("/api/v1/me/parking-spots"))), 405, "METHOD_NOT_ALLOWED");
    }

    @Test
    void suspiciousUrlIsRejectedCleanly() throws Exception {
        // An encoded "../" is a classic path-traversal trick; the security firewall blocks it.
        expectCleanError(mvc.perform(get("/api/v1/parking-spots/%2e%2e/admin")), 400, "INVALID_REQUEST_PATH");
    }

    @Test
    void unexpectedBugGivesAGeneric500WithoutDetails() throws Exception {
        when(spots.create(any(), any(), any(CreateParkingSpotRequest.class)))
                .thenThrow(new IllegalStateException("secret internal detail: db password is hunter2"));

        ResultActions result = mvc.perform(asUser(post("/api/v1/parking-spots"))
                .header("Idempotency-Key", "attack-key-5")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"x\",\"address\":\"1 Main St\",\"pricePerHour\":5,"
                        + "\"availabilityWindows\":[{\"startTime\":\"2026-12-01T10:00:00Z\","
                        + "\"endTime\":\"2026-12-01T12:00:00Z\"}]}"));

        expectCleanError(result, 500, "INTERNAL_ERROR");
        assertThat(result.andReturn().getResponse().getContentAsString()).doesNotContain("hunter2");
    }

    private MockHttpServletRequestBuilder asUser(MockHttpServletRequestBuilder request) {
        return request.with(jwt().jwt(token -> token.subject(user.toString())));
    }

    // Every error: the right status, our error code, and nothing that looks like Java internals.
    private void expectCleanError(ResultActions result, int status, String code) throws Exception {
        result.andExpect(status().is(status)).andExpect(jsonPath("$.code").value(code));
        String body = result.andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("Exception", "at com.", "at org.", "java.", "trace");
    }
}
