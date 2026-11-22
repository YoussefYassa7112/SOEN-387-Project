package com.squared.locus.conformance;

import com.atlassian.oai.validator.OpenApiInteractionValidator;
import com.atlassian.oai.validator.model.Request;
import com.atlassian.oai.validator.model.SimpleRequest;
import com.atlassian.oai.validator.model.SimpleResponse;
import com.atlassian.oai.validator.report.LevelResolver;
import com.atlassian.oai.validator.report.ValidationReport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * LOCUS-09: conformance suite against the RUNNING service.
 *
 * ApiConformanceTest checks the controllers inside the test with mocked services.
 * This class is different: it sends real HTTP requests to a backend that is already
 * running (local or deployed) and checks every answer against openapi.yaml.
 * It does not import any server code; it only knows the URL and the contract.
 *
 * It is skipped in a normal build. Run it with:
 *   mvnw test -Dtest=LiveApiConformanceTest -Dlive.baseUrl=http://localhost:8080
 * Add -Dlive.token=<jwt> to also check the endpoints that need a signed-in user.
 */
@EnabledIfSystemProperty(named = "live.baseUrl", matches = ".+")
class LiveApiConformanceTest {

    private static final String API = "/api/v1";

    // Same contract file the server publishes. Two checks are relaxed, as in ApiConformanceTest:
    // the token check (the 401 tests leave the token out on purpose and check the status
    // themselves) and unknown fields (ParkingSpot is built with allOf).
    private static final OpenApiInteractionValidator contract = OpenApiInteractionValidator
            .createFor("static/openapi.yaml")
            .withLevelResolver(LevelResolver.create()
                    .withLevel("validation.request.security", ValidationReport.Level.IGNORE)
                    .withLevel("validation.schema.additionalProperties", ValidationReport.Level.IGNORE)
                    .build())
            .build();

    private static final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();
    private static final ObjectMapper json = new ObjectMapper();
    // RFC 3339 with seconds, as the contract asks (2026-11-23T17:00:00Z)
    private static final DateTimeFormatter RFC_3339 = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

    private static String baseUrl;
    private static String token;

    @BeforeAll
    static void readSettings() {
        baseUrl = System.getProperty("live.baseUrl").replaceAll("/+$", "");
        token = System.getProperty("live.token", "");
    }

    // ---- public endpoints ----

    @Test
    void searchingSpotsMatchesTheContract() throws Exception {
        var response = send("GET", API + "/parking-spots", null, null, false);
        assertThat(response.statusCode()).isEqualTo(200);
    }

    @Test
    void searchingWithATimeRangeMatchesTheContract() throws Exception {
        var start = tomorrowAtNoon();
        var query = "startTime=" + encode(start) + "&endTime=" + encode(start.plusHours(2));
        var response = send("GET", API + "/parking-spots?" + query, null, null, false);
        assertThat(response.statusCode()).isEqualTo(200);
    }

    @Test
    void spotDetailsAndAvailabilityMatchTheContract() throws Exception {
        String spotId = firstSpotId();
        if (spotId == null) return;   // nothing listed yet, nothing to check

        assertThat(send("GET", API + "/parking-spots/" + spotId, null, null, false).statusCode())
                .isEqualTo(200);

        var start = tomorrowAtNoon();
        var query = "startTime=" + encode(start) + "&endTime=" + encode(start.plusHours(1));
        assertThat(send("GET", API + "/parking-spots/" + spotId + "/availability?" + query, null, null, false)
                .statusCode()).isEqualTo(200);
    }

    @Test
    void unknownSpotGives404WithTheErrorShape() throws Exception {
        var response = send("GET", API + "/parking-spots/" + UUID.randomUUID(), null, null, false);
        assertThat(response.statusCode()).isEqualTo(404);
    }

    @Test
    void badSpotIdGives400WithTheErrorShape() throws Exception {
        var response = send("GET", API + "/parking-spots/not-a-uuid", null, null, false);
        assertThat(response.statusCode()).isEqualTo(400);
    }

    @Test
    void bookingWithoutATokenGives401() throws Exception {
        var start = tomorrowAtNoon();
        String body = "{\"startTime\":\"" + RFC_3339.format(start)
                + "\",\"endTime\":\"" + RFC_3339.format(start.plusHours(1)) + "\"}";
        var response = send("POST", API + "/parking-spots/" + UUID.randomUUID() + "/reservations",
                body, "conformance-" + UUID.randomUUID(), false);
        assertThat(response.statusCode()).isEqualTo(401);
    }

    @Test
    void myReservationsWithoutATokenGives401() throws Exception {
        assertThat(send("GET", API + "/me/reservations", null, null, false).statusCode()).isEqualTo(401);
    }

    // ---- signed-in endpoints (only when -Dlive.token is given) ----

    @Test
    void myReservationsMatchTheContract() throws Exception {
        if (token.isBlank()) return;
        assertThat(send("GET", API + "/me/reservations", null, null, true).statusCode()).isEqualTo(200);
    }

    @Test
    void myListingsMatchTheContract() throws Exception {
        if (token.isBlank()) return;
        assertThat(send("GET", API + "/me/parking-spots", null, null, true).statusCode()).isEqualTo(200);
    }

    @Test
    void unknownReservationGives404() throws Exception {
        if (token.isBlank()) return;
        assertThat(send("GET", API + "/reservations/" + UUID.randomUUID(), null, null, true).statusCode())
                .isEqualTo(404);
    }

    // ---- helpers ----

    // Sends one real HTTP request, then checks the request and the answer against openapi.yaml.
    private HttpResponse<String> send(String method, String path, String body, String idempotencyKey,
                                      boolean signedIn) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(60))
                .header("Accept", "application/json");
        if (body != null) builder.header("Content-Type", "application/json");
        if (idempotencyKey != null) builder.header("Idempotency-Key", idempotencyKey);
        if (signedIn) builder.header("Authorization", "Bearer " + token);
        builder.method(method, body == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body));

        var response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());

        var request = new SimpleRequest.Builder(Request.Method.valueOf(method), pathOnly(path))
                .withBody(body);
        queryParams(path, request);
        if (body != null) request.withContentType("application/json");
        if (idempotencyKey != null) request.withHeader("Idempotency-Key", idempotencyKey);
        if (signedIn) request.withAuthorization("Bearer " + token);

        var answer = SimpleResponse.Builder.status(response.statusCode()).withBody(response.body());
        response.headers().firstValue("Content-Type").ifPresent(answer::withContentType);

        ValidationReport report = contract.validate(request.build(), answer.build());
        assertThat(report.hasErrors())
                .as(method + " " + path + " -> " + response.statusCode() + "\n" + report)
                .isFalse();
        return response;
    }

    private String firstSpotId() throws Exception {
        var response = send("GET", API + "/parking-spots", null, null, false);
        JsonNode items = json.readTree(response.body()).path("items");
        return items.isEmpty() ? null : items.get(0).path("spotId").asText();
    }

    private static String pathOnly(String path) {
        int question = path.indexOf('?');
        return question < 0 ? path : path.substring(0, question);
    }

    private static void queryParams(String path, SimpleRequest.Builder request) {
        int question = path.indexOf('?');
        if (question < 0) return;
        for (String pair : path.substring(question + 1).split("&")) {
            String[] parts = pair.split("=", 2);
            request.withQueryParam(parts[0], java.net.URLDecoder.decode(parts[1], java.nio.charset.StandardCharsets.UTF_8));
        }
    }

    private static String encode(OffsetDateTime time) {
        return java.net.URLEncoder.encode(RFC_3339.format(time), java.nio.charset.StandardCharsets.UTF_8);
    }

    private static OffsetDateTime tomorrowAtNoon() {
        return OffsetDateTime.now(ZoneOffset.UTC).plusDays(1).truncatedTo(ChronoUnit.DAYS).plusHours(17);
    }
}
