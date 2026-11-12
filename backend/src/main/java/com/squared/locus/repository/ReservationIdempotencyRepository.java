package com.squared.locus.repository;

import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

// Stores and retrieves retry information for reservation requests.
// Makes reservation creation safe (idempotent) to retrying the same request multiple times.
@Repository
public class ReservationIdempotencyRepository {

    // Will be accessed with direct SQL queries, because we want to lock the row for the duration of the service transaction.
    private final JdbcTemplate jdbc;

    public ReservationIdempotencyRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // PostgreSQL waits for a concurrent insert of the same key to commit or roll back.
    // Both this claim and the reservation are committed in the service transaction.

    // If this is the first request with that user/key combination, a new row is inserted.
    // If the key already exists, nothing is inserted.
    // ON CONFLICT DO NOTHING: This avoids duplicate idempotency records without throwing an unnecessary database error.
    public Claim claim(UUID userId, String key, String fingerprint) {
        // Insert a new row if it doesn't exist, or do nothing if it does.
        jdbc.update("INSERT INTO reservation_idempotency "
                        + "(user_id, idempotency_key, request_fingerprint) VALUES (?, ?, ?) "
                        + "ON CONFLICT (user_id, idempotency_key) DO NOTHING",
                userId, key, fingerprint);
        // Select the row for update, locking it for the duration of the transaction.
        return jdbc.queryForObject("SELECT request_fingerprint, response_json "
                        + "FROM reservation_idempotency WHERE user_id = ? AND idempotency_key = ? FOR UPDATE",
                // Claims are returned as a record with the request fingerprint and the response JSON (if any).     
                (rs, row) -> new Claim(rs.getString(1), rs.getString(2)), userId, key);
    }

    // Updates the row with the response JSON after the reservation is successfully created.
    public void complete(UUID userId, String key, String responseJson) {
        jdbc.update("UPDATE reservation_idempotency SET response_json = ? "
                + "WHERE user_id = ? AND idempotency_key = ?", responseJson, userId, key);
    }

    // Represents a claim on a reservation idempotency key, to avoid returning databse row directly.
    public record Claim(String fingerprint, String responseJson) {}
}
