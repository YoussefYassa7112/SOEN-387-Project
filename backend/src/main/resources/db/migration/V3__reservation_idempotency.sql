-- A caller's key identifies one logical request, including its target spot.
CREATE TABLE reservation_idempotency (
    -- The user who made the request. This is a foreign key to the users table.
    user_id UUID NOT NULL REFERENCES users(id),
    -- The caller's key for this request. This is unique per user.
    idempotency_key VARCHAR(128) NOT NULL,
    -- A fingerprint of the request's parameters, to detect if the same request is being retried.
    request_fingerprint TEXT NOT NULL,
    -- The JSON response from the reservation creation, if it was successful. This is NULL if the request has not yet been completed.
    response_json TEXT,
    PRIMARY KEY (user_id, idempotency_key)
);
