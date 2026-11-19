-- V4 (LOCUS-38): the payment outbox.
-- Creating a reservation also inserts one row here, in the same transaction.
-- A background worker reads these rows and calls Stripe outside the HTTP request.

CREATE TABLE payment_outbox (
    id                 BIGSERIAL    PRIMARY KEY,
    reservation_id     UUID         NOT NULL UNIQUE REFERENCES reservations (id),
    payment_method     VARCHAR(100) NOT NULL,        -- Stripe test card, e.g. pm_card_visa
    status             VARCHAR(20)  NOT NULL DEFAULT 'PENDING'
                       CHECK (status IN ('PENDING', 'DONE', 'FAILED')),
    attempts           INT          NOT NULL DEFAULT 0,
    next_attempt_at    TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    payment_intent_id  VARCHAR(100),                 -- Stripe's id, once Stripe answered
    last_error         VARCHAR(500),
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Used by the worker: "which pending payments are due now?"
CREATE INDEX idx_payment_outbox_due ON payment_outbox (status, next_attempt_at);
