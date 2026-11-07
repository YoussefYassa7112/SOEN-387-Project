package com.squared.locus.domain;

/**
 * The input breaks a business rule, for example an end time before the start time
 * (maps to 400). The client must fix the request before retrying.
 */
public class InvalidRequestException extends DomainException {

    public InvalidRequestException(String code, String message) {
        super(code, message);
    }
}
