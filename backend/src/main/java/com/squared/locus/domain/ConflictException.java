package com.squared.locus.domain;

/**
 * The request is valid but clashes with the current state (maps to 409).
 * Example code: PARKING_SPOT_UNAVAILABLE when the interval is already booked.
 */
public class ConflictException extends DomainException {

    public ConflictException(String code, String message) {
        super(code, message);
    }
}
