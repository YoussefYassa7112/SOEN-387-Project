package com.squared.locus.domain;

/**
 * Base class for every business rule violation.
 *
 * Each exception carries a stable, machine-readable code (for example
 * PARKING_SPOT_UNAVAILABLE) that clients can rely on, plus a human message.
 * The domain only says WHAT went wrong. The interface layer decides which
 * HTTP status that means (Chapter 3 error model, Chapter 5 layers).
 */
public class DomainException extends RuntimeException {

    private final String code;

    public DomainException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
