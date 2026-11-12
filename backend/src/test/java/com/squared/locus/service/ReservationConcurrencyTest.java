package com.squared.locus.service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.squared.locus.dto.CreateReservationRequest;

// Spring integration setup
// Start controllers, services, repos, database configs and security configs
@SpringBootTest(properties = {
        "spring.config.import=", "locus.google.client-id=test",
        "locus.jwt.secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
})
// Allow us to send the HTTP requests internally instead of through a web server
@AutoConfigureMockMvc
class ReservationConcurrencyTest {
    // Use local PostgreSQL by default; override these with -Dtest.database.* when needed.
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getProperty(
                "test.database.url",
                "jdbc:postgresql://localhost:5432/locus_concurrency_test"));
        registry.add("spring.datasource.username", () -> System.getProperty(
                "test.database.username", System.getProperty("user.name", "postgres")));
        registry.add("spring.datasource.password", () -> System.getProperty(
                "test.database.password", ""));
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    UUID user;
    UUID spot;
    OffsetDateTime start;

    // Databse cleanup and seed before each test
    @BeforeEach
    void seed() {
        jdbc.execute("TRUNCATE reservation_idempotency, reservations, availability_windows, parking_spots, users CASCADE");
        user = UUID.randomUUID();
        spot = UUID.randomUUID();
        start = OffsetDateTime.now().plusDays(2).withNano(0);
        jdbc.update("INSERT INTO users(id, google_sub, email) VALUES (?, ?, ?)", user, user.toString(), "test@example.com");
        jdbc.update("INSERT INTO parking_spots(id, owner_id, title, address, price_per_hour) VALUES (?, ?, 'Test', 'Montreal', 10)", spot, user);
        jdbc.update("INSERT INTO availability_windows(spot_id, start_time, end_time) VALUES (?, ?, ?)", spot, start.minusHours(1), start.plusHours(8));
    }

    // Store the test's HTTP status code and response body
    record Result(int status, String body) {}

    // Create a reservation request
    private Result reserve(String key, UUID caller, UUID target, OffsetDateTime from, OffsetDateTime to) throws Exception {
        var request = post("/api/v1/parking-spots/{spotId}/reservations", target)
                .with(jwt().jwt(token -> token.subject(caller.toString())))
                .contentType("application/json")
                .content(mapper.writeValueAsString(new CreateReservationRequest(from, to)));
        if (key != null) request.header("Idempotency-Key", key);
        var response = mvc.perform(request).andReturn().getResponse();
        return new Result(response.getStatus(), response.getContentAsString());
    }

    // Execute two requests concurrently
    private List<Result> race(Callable<Result> first, Callable<Result> second) throws Exception {
        try (var executor = Executors.newFixedThreadPool(2)) {
            var ready = new CountDownLatch(2);
            var go = new CountDownLatch(1);
            var tasks = List.of(first, second).stream().map(task -> executor.submit(() -> {
                ready.countDown();
                if (!go.await(10, TimeUnit.SECONDS)) throw new AssertionError("Start barrier timed out");
                return task.call();
            })).toList();
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            return List.of(tasks.get(0).get(30, TimeUnit.SECONDS), tasks.get(1).get(30, TimeUnit.SECONDS));
        }
    }

    // Shows that findForUpdate blocks the second request and only creates 1 resrvation
    @Test
    void concurrentOverlapsProduceOneReservationAnd409() throws Exception {
        var results = race(
                () -> reserve("attempt-one", user, spot, start, start.plusHours(2)),
                () -> reserve("attempt-two", user, spot, start.plusHours(1), start.plusHours(3)));
        assertThat(results).extracting(Result::status).containsExactlyInAnyOrder(202, 409);
        assertThat(results.stream().filter(r -> r.status == 409).findFirst().orElseThrow().body)
                .contains("PARKING_SPOT_UNAVAILABLE");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM reservations", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM reservation_idempotency", Integer.class)).isEqualTo(1);
    }

    // Since both requests use the same user, same parking spot, same time range and same idempotency key
    // We should be able to assert only 1 reservation is in the database
    @Test
    void concurrentIdenticalRetriesReturnSameResponse() throws Exception {
        Callable<Result> attempt = () -> reserve("same-key-123", user, spot, start, start.plusHours(1));
        var results = race(attempt, attempt);
        assertThat(results).extracting(Result::status).containsExactly(202, 202);
        assertThat(results.get(0).body).isEqualTo(results.get(1).body);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM reservations", Integer.class)).isEqualTo(1);
        // Replay returns the stored response even after the live reservation changes.
        jdbc.update("UPDATE reservations SET status = 'CONFIRMED'");
        assertThat(attempt.call()).isEqualTo(results.get(0));
    }

    // Both requests use the same idempotency key, but they use different time intervals.
    @Test
    void concurrentKeyReuseWithDifferentBodiesConflicts() throws Exception {
        var results = race(
                () -> reserve("reused-key", user, spot, start, start.plusHours(1)),
                () -> reserve("reused-key", user, spot, start, start.plusHours(2)));
        assertThat(results).extracting(Result::status).containsExactlyInAnyOrder(202, 409);
        assertThat(results.stream().filter(r -> r.status == 409).findFirst().orElseThrow().body)
                .contains("IDEMPOTENCY_KEY_MISMATCH");
    }
}
