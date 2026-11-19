package com.squared.locus.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

// LOCUS-52: finds an address's coordinates with Nominatim, the free OpenStreetMap search service.
// No API key and no cost, but its usage policy asks for:
//   - at most 1 request per second (see waitForOurTurn),
//   - a User-Agent that names our app,
//   - no bulk geocoding (we only call it when an owner saves an address, and we keep the result).
@Component
public class NominatimGeocoder implements Geocoder {

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))   // stability: never wait forever on the map service
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final String baseUrl;
    private final String userAgent;

    // When we last called Nominatim, used to stay under 1 request per second.
    private long lastCallMillis = 0;

    public NominatimGeocoder(@Value("${locus.geocoding.url:https://nominatim.openstreetmap.org}") String baseUrl,
                             @Value("${locus.geocoding.user-agent:Locus-SOEN387-student-project}") String userAgent) {
        this.baseUrl = baseUrl;
        this.userAgent = userAgent;
    }

    @Override
    public Coordinates findCoordinates(String address) {
        String url = baseUrl + "/search?format=jsonv2&limit=1&q="
                + URLEncoder.encode(address, StandardCharsets.UTF_8);

        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", userAgent)
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();

        try {
            waitForOurTurn();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return null;
            }

            // Nominatim answers with a list of places; we asked for only the best one.
            JsonNode places = objectMapper.readTree(response.body());
            if (!places.isArray() || places.isEmpty()) {
                return null;   // address not found
            }
            JsonNode best = places.get(0);
            double latitude = Double.parseDouble(best.get("lat").asText());
            double longitude = Double.parseDouble(best.get("lon").asText());
            return new Coordinates(latitude, longitude);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            // Timeout, no internet, or an answer we did not understand.
            // LOCUS-54: the spot is still saved, it just has no point on the map.
            return null;
        }
    }

    // Makes sure two calls are at least one second apart.
    // synchronized = only one thread at a time can be in here.
    private synchronized void waitForOurTurn() throws InterruptedException {
        long waitMillis = lastCallMillis + 1000 - System.currentTimeMillis();
        if (waitMillis > 0) {
            Thread.sleep(waitMillis);
        }
        lastCallMillis = System.currentTimeMillis();
    }
}
