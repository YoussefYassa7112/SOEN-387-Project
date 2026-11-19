package com.squared.locus.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.squared.locus.dto.ParkingSearchFilters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

@Service
public class GeminiSearchService {
    private static final Logger log = LoggerFactory.getLogger(GeminiSearchService.class);

    private static final String FALLBACK_MODEL = "gemini-3.5-flash-lite";

    private static final Map<String, Object> FILTER_SCHEMA = Map.of(
            "type", "object",
            "properties", Map.of(
                    "location", Map.of("type", List.of("string", "null")),
                    "destination", Map.of("type", List.of("string", "null")),
                    "startTime", Map.of("type", List.of("string", "null")),
                    "endTime", Map.of("type", List.of("string", "null")),
                    "maxPricePerHour", Map.of("type", List.of("number", "null")),
                    "parkingType", Map.of("type", List.of("string", "null"))
            )
    );

    private final RestClient client;
    private final ObjectMapper mapper;
    private final ParkingSpotService parkingSpots;
    private final String apiKey;
    private final String model;

    @Autowired
    public GeminiSearchService(ObjectMapper mapper, ParkingSpotService parkingSpots,
                               @Value("${GEMINI_API_KEY:}") String apiKey,
                               @Value("${GEMINI_MODEL:gemini-3.1-flash-lite}") String model) {
        this(mapper, parkingSpots, apiKey, model, createClient());
    }

    GeminiSearchService(ObjectMapper mapper, ParkingSpotService parkingSpots,
                        String apiKey, String model, RestClient client) {
        this.client = client;
        this.mapper = mapper;
        this.parkingSpots = parkingSpots;
        this.apiKey = apiKey;
        this.model = model;
    }

    private static RestClient createClient() {
        var requests = new SimpleClientHttpRequestFactory();
        requests.setConnectTimeout(Duration.ofSeconds(5));
        requests.setReadTimeout(Duration.ofSeconds(25));
        return RestClient.builder()
                .baseUrl("https://generativelanguage.googleapis.com")
                .requestFactory(requests)
                .build();
    }

    public ParkingSearchFilters interpret(String query) {
        var now = ZonedDateTime.now(ZoneId.of("America/Toronto"));
        String instructions = """
                Extract parking filters from the driver's message. The current Toronto time is %s.
                Use location for a street, city, or neighbourhood that the driver wants to match against listing addresses.
                Use destination instead for a named venue, landmark, building, or a specific address the driver wants to park near.
                For example, "go to the Bell Center" means destination "Bell Centre, Montréal" and location null.
                Do not turn a venue name into a street name, invent a street address, or return both fields.
                Use a number for the hourly price cap.
                Use ISO 8601 timestamps with the Toronto offset for the requested date.
                For an explicit time range with no date, use its next occurrence in Toronto.
                Set both timestamps to null unless the user supplies enough information for a full interval.
                Allowed parking types are driveway, privateLot, garage, and other.
                Use null for each unspecified or unsupported filter. Do not invent values.
                """.formatted(now);
        ParkingSearchFilters filters = extract(instructions, query, FILTER_SCHEMA, ParkingSearchFilters.class);
        if (filters.destination() != null && !filters.destination().isBlank()) {
            filters = new ParkingSearchFilters(null, filters.startTime(), filters.endTime(), filters.maxPricePerHour(),
                    filters.parkingType(), filters.destination().trim());
        }
        parkingSpots.validateSearchFilters(filters);
        return filters;
    }

    <T> T extract(String instructions, String input, Map<String, Object> schema, Class<T> resultType) {
        if (apiKey.isBlank()) {
            log.warn("Gemini request skipped because GEMINI_API_KEY is unset");
            throw new Unavailable();
        }

        var body = Map.of(
                "systemInstruction", Map.of("parts", List.of(Map.of("text", instructions))),
                "contents", List.of(Map.of("parts", List.of(Map.of("text", input)))),
                "generationConfig", Map.of(
                        "responseMimeType", "application/json",
                        "responseJsonSchema", schema,
                        "maxOutputTokens", 1024,
                        "thinkingConfig", Map.of("thinkingLevel", "MINIMAL")
                )
        );

        String requestedModel = model;
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                JsonNode answer = generate(requestedModel, body);
                return readResult(answer, resultType);
            } catch (RestClientResponseException e) {
                int status = e.getStatusCode().value();
                log.warn("Gemini {} returned HTTP {} for {} on attempt {}",
                        requestedModel, status, resultType.getSimpleName(), attempt);
                if (status != 503) {
                    break;
                }
                requestedModel = FALLBACK_MODEL;
            } catch (JsonProcessingException | Unavailable e) {
                log.warn("Gemini returned unreadable {} on attempt {} ({})",
                        resultType.getSimpleName(), attempt, e.getClass().getSimpleName());
            } catch (RestClientException e) {
                log.warn("Gemini {} request failed ({})", resultType.getSimpleName(), e.getClass().getSimpleName());
                break;
            }
        }
        throw new Unavailable();
    }

    private <T> T readResult(JsonNode answer, Class<T> resultType) throws JsonProcessingException {
        if (answer == null) {
            throw new Unavailable();
        }
        StringBuilder json = new StringBuilder();
        for (JsonNode part : answer.at("/candidates/0/content/parts")) {
            if (!part.path("thought").asBoolean()) {
                json.append(part.path("text").asText());
            }
        }
        T result = mapper.readValue(json.toString(), resultType);
        if (result == null) {
            throw new Unavailable();
        }
        return result;
    }

    private JsonNode generate(String requestedModel, Map<String, ?> body) {
        return client.post()
                .uri("/v1beta/models/{model}:generateContent", requestedModel)
                .header("x-goog-api-key", apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class);
    }

    public static class Unavailable extends RuntimeException {
    }
}
