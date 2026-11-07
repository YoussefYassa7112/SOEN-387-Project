-- V2: the core booking tables. Parking spots, the time windows when each spot
-- can be booked, and the reservations made on them.
-- V1 (users) runs first because Flyway applies files in version order,
-- so the users table always exists before this file runs.

CREATE TABLE parking_spots (
    id              UUID           PRIMARY KEY,
    owner_id        UUID           NOT NULL REFERENCES users (id),
    title           VARCHAR(120)   NOT NULL,
    description     VARCHAR(2000),
    address         VARCHAR(300)   NOT NULL,
    photo_url       TEXT,
    price_per_hour  NUMERIC(10, 2) NOT NULL CHECK (price_per_hour > 0),
    parking_type    VARCHAR(20)    CHECK (parking_type IN ('driveway', 'privateLot', 'garage', 'other')),
    restrictions    VARCHAR(1000),
    active          BOOLEAN        NOT NULL DEFAULT TRUE,  -- false = deactivated, no new reservations
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_parking_spots_owner ON parking_spots (owner_id);

-- When the owner says the spot can be booked. A spot has one or more windows.
CREATE TABLE availability_windows (
    id          BIGSERIAL   PRIMARY KEY,
    spot_id     UUID        NOT NULL REFERENCES parking_spots (id) ON DELETE CASCADE,
    start_time  TIMESTAMPTZ NOT NULL,
    end_time    TIMESTAMPTZ NOT NULL,
    CHECK (end_time > start_time)
);

CREATE INDEX idx_availability_windows_spot ON availability_windows (spot_id);

-- A reservation books one parking spot for one time interval.
-- The interval is half-open [start_time, end_time), so a booking that ends at 8 PM
-- does not conflict with one that starts at 8 PM.
--
-- The rule "no two active reservations of the same spot may overlap" is checked by the
-- application while it holds a lock on the parking_spots row (Chapter 7, pessimistic locking).
CREATE TABLE reservations (
    id           UUID           PRIMARY KEY,
    spot_id      UUID           NOT NULL REFERENCES parking_spots (id),
    user_id      UUID           NOT NULL REFERENCES users (id),
    start_time   TIMESTAMPTZ    NOT NULL,
    end_time     TIMESTAMPTZ    NOT NULL,
    status       VARCHAR(20)    NOT NULL
                 CHECK (status IN ('PENDING_PAYMENT', 'CONFIRMED', 'PAYMENT_FAILED', 'CANCELLED')),
    total_price  NUMERIC(10, 2) NOT NULL CHECK (total_price >= 0),
    currency     CHAR(3)        NOT NULL DEFAULT 'CAD',
    created_at   TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (end_time > start_time)
);

-- Used by the overlap check: "find this spot's reservations that touch this interval".
CREATE INDEX idx_reservations_spot_time ON reservations (spot_id, start_time, end_time);

-- Used by "list my reservations".
CREATE INDEX idx_reservations_user ON reservations (user_id);
