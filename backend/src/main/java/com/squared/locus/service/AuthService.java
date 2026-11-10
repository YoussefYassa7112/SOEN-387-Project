package com.squared.locus.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.squared.locus.model.User;
import com.squared.locus.repository.UserRepository;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final GoogleTokenVerifierService googleVerifier;
    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final long expirationSeconds;

    // Constructor that initializes the AuthService with the necessary dependencies: 
    // GoogleTokenVerifierService for verifying Google ID tokens, 
    // UserRepository for accessing user data, JwtService for generating JWTs, 
    // and expirationSeconds for setting the token expiration time.
    public AuthService(
            GoogleTokenVerifierService googleVerifier,
            UserRepository userRepository,
            JwtService jwtService,
            @Value("${locus.jwt.expiration-seconds}")
                    long expirationSeconds) {

        this.googleVerifier = googleVerifier;
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.expirationSeconds = expirationSeconds;
    }

    // Method that handles the sign-in process for users using Google authentication.
    // @Transactional ensures that the entire sign-in process is treated as a single transaction,
    // meaning that if any part of the process fails, all changes made during the transaction will
    // be rolled back to maintain data integrity.
    @Transactional
    public TokenResult signInWithGoogle(String idToken) {

        // Verifies the provided Google ID token using the GoogleTokenVerifierService.
        GoogleIdToken.Payload payload =
                googleVerifier.verify(idToken);
        // Once verified, read the Google identity
        String googleSub = payload.getSubject();
        String email = payload.getEmail();
        String name = (String) payload.get("name");

        User user = userRepository
                .findByGoogleSub(googleSub)
                .orElseGet(() -> userRepository.save(
                        new User(googleSub, email, name)
                ));

        user.updateProfile(email, name);

        String accessToken =
                jwtService.generateToken(
                        user.getId().toString()
                );
        // Returns a TokenResult containing the generated access token, token type, and expiration time.
        // So that the client can use this information to authenticate subsequent requests to the backend.        
        return new TokenResult(
                accessToken,
                "Bearer",
                expirationSeconds
        );
    }

    // DTO for the result of the sign-in process, containing the access token, token type, and expiration time.
    public record TokenResult(
            String accessToken,
            String tokenType,
            long expiresIn
    ) {}
}
