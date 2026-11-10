package com.squared.locus.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collections;

// Service to verify that a user who signed in using Google is actually authenticated by Google
// Created to answer 3 auth questions:
// Did Google actually authenticate this user?
// Is the Google ID token valid and intended for LOCUS?
// Has Google verified the user's email address?
@Service
public class GoogleTokenVerifierService {

    // GoogleIdTokenVerifier is a class provided by the Google API Client Library for Java that verifies the integrity and authenticity of a Google ID token.
    private final GoogleIdTokenVerifier verifier;

    // Constructor that initializes the GoogleIdTokenVerifier with the provided client ID.
    public GoogleTokenVerifierService(
        // Tells Spring Boot to retrieve the Google client ID from the application's configuration.
        //The client ID lets the backend check that the token was intended for LOCUS.
            @Value("${locus.google.client-id}") String clientId) {
        // Initializes the GoogleIdTokenVerifier with the provided client ID and sets up the necessary transport and JSON factory for token verification.
        this.verifier = new GoogleIdTokenVerifier.Builder(
                new NetHttpTransport(),
                GsonFactory.getDefaultInstance())
                .setAudience(Collections.singletonList(clientId))
                .build();
    }

    // Method other classes can call to verify a Google ID token. It checks if the token is valid, intended for LOCUS, and if the user's email is verified by Google.
    public GoogleIdToken.Payload verify(String idToken) {
        // Checks if the provided ID token is null or blank. If it is, throws a ResponseStatusException with a 400 Bad Request status and an appropriate error message.
        if (idToken == null || idToken.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Google ID token is required");
        }
        try {
            GoogleIdToken verified = verifier.verify(idToken);
            // Checks if the verified token is null, which indicates that the token is invalid. If it is null, throws a ResponseStatusException with a 401 Unauthorized status and an appropriate error message.
            if (verified == null) {
                throw new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Invalid Google ID token");
            }
            // Retrieves the payload from the verified token, which contains information about the user, such as their email address and whether it has been verified by Google.
            GoogleIdToken.Payload payload =
                    verified.getPayload();
            // Checks if the email associated with the token has been verified by Google. If it has not been verified, throws a ResponseStatusException with a 401 Unauthorized status and an appropriate error message.
            if (!Boolean.TRUE.equals(
                    payload.getEmailVerified())) {
                throw new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Google email is not verified");
            }
            // If all checks pass, returns the payload, which contains information about the authenticated user.
            return payload;
        // Catches exceptions related to IO or security issues during token verification and throws a ResponseStatusException with a 503 Service Unavailable status and an appropriate error message.
        } catch (IOException | GeneralSecurityException ex) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Google token verification unavailable");
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Malformed Google ID token");
        }
    }
}
