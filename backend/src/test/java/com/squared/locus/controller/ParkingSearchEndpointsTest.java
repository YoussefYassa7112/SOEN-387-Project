package com.squared.locus.controller;

import com.squared.locus.config.SecurityConfig;
import com.squared.locus.dto.AvailabilityResponse;
import com.squared.locus.dto.ParkingSpotPageResponse;
import com.squared.locus.service.ParkingSpotService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Checks the HTTP side of search. No token is sent because these endpoints are public. */
@WebMvcTest(ParkingSpotController.class)
@Import(SecurityConfig.class)
class ParkingSearchEndpointsTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ParkingSpotService service;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void searchIsPublicAndUsesTheDefaultPaging() throws Exception {
        when(service.search(null, null, null, null, null, 20, 0))
                .thenReturn(new ParkingSpotPageResponse(List.of(), 0, 20, 0));

        mockMvc.perform(get("/api/v1/parking-spots"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.limit").value(20))
                .andExpect(jsonPath("$.offset").value(0));
    }

    @Test
    void badDateIsA400() throws Exception {
        mockMvc.perform(get("/api/v1/parking-spots").param("startTime", "tomorrow"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
    }

    @Test
    void availabilityIsPublicAndReturnsTheResult() throws Exception {
        UUID spotId = UUID.randomUUID();
        OffsetDateTime start = OffsetDateTime.parse("2026-11-12T09:00:00-05:00");
        OffsetDateTime end = OffsetDateTime.parse("2026-11-12T12:00:00-05:00");
        when(service.checkAvailability(spotId, start, end))
                .thenReturn(new AvailabilityResponse(spotId, start, end, false, "alreadyReserved"));

        mockMvc.perform(get("/api/v1/parking-spots/" + spotId + "/availability")
                        .param("startTime", "2026-11-12T09:00:00-05:00")
                        .param("endTime", "2026-11-12T12:00:00-05:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.reason").value("alreadyReserved"));
    }

    @Test
    void availabilityNeedsBothTimes() throws Exception {
        mockMvc.perform(get("/api/v1/parking-spots/" + UUID.randomUUID() + "/availability")
                        .param("startTime", "2026-11-12T09:00:00-05:00"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_PARAMETER"));
    }
}
