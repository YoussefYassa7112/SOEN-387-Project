package com.squared.locus.controller;

import com.squared.locus.dto.ParkingSearchFilters;
import com.squared.locus.service.GeminiSearchService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ParkingSearchController {
    private final GeminiSearchService gemini;

    public ParkingSearchController(GeminiSearchService gemini) {
        this.gemini = gemini;
    }

    @PostMapping("/api/v1/parking-spots/interpret-search")
    public ParkingSearchFilters interpret(@Valid @RequestBody SearchRequest request) {
        return gemini.interpret(request.query());
    }

    public record SearchRequest(@NotBlank @Size(max = 300) String query) {
    }
}
