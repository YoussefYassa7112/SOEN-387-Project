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

    @Transactional
    public TokenResult signInWithGoogle(String idToken) {

        GoogleIdToken.Payload payload =
                googleVerifier.verify(idToken);

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

        return new TokenResult(
                accessToken,
                "Bearer",
                expirationSeconds
        );
    }

    public record TokenResult(
            String accessToken,
            String tokenType,
            long expiresIn
    ) {}
}
