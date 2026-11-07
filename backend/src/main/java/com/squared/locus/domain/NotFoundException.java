package com.squared.locus.domain;

/** The requested thing does not exist (maps to 404). Example code: SPOT_NOT_FOUND. */
public class NotFoundException extends DomainException {

    public NotFoundException(String code, String message) {
        super(code, message);
    }
}
