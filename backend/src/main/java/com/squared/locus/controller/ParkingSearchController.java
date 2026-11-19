package com.squared.locus.controller;

import com.squared.locus.dto.ParkingSearchFilters;
import com.squared.locus.service.GeminiSearchService;
import com.squared.locus.service.EventSearchService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ParkingSearchController {
    private final GeminiSearchService gemini;

    private final EventSearchService events;

    public ParkingSearchController(GeminiSearchService gemini, EventSearchService events) {
        this.gemini = gemini;
        this.events = events;
    }

    @PostMapping("/api/v1/parking-spots/interpret-search")
    public ParkingSearchFilters interpret(@Valid @RequestBody SearchRequest request) {
        return gemini.interpret(request.query());
    }

    @PostMapping("/api/v1/parking-spots/lookup-event")
    public EventSearchService.EventDetails lookupEvent(@Valid @RequestBody SearchRequest request) {
        return events.search(request.query().trim());
    }

    @PostMapping("/api/v1/parking-spots/event-parking")
    public EventSearchService.EventParking rankEventParking(@Valid @RequestBody EventSearchService.EventParkingRequest request) {
        return events.rankParking(request);
    }

    public record SearchRequest(@NotBlank @Size(max = 300) String query) {
    }
}
