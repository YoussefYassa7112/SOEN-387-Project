package com.squared.locus.service;

import com.squared.locus.domain.DomainException;

/**
 * The caller is signed in but is not allowed to touch this resource,
 * for example cancelling another user's reservation (maps to 403).
 *
 * It lives in the service layer because ownership checks happen there,
 * after the resource is loaded (Chapter 8).
 */
public class ForbiddenException extends DomainException {

    public ForbiddenException(String code, String message) {
        super(code, message);
    }
}
