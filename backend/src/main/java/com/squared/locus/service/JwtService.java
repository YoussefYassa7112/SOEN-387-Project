package com.squared.locus.service;

import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

// Service to generate JSON Web Tokens (JWTs) for authenticated users. 
// JWTs are used for securely transmitting information between parties as a JSON object. 
// This service is responsible for creating JWTs that include claims such as the issuer, 
// subject (user ID), issued at time, and expiration time.
@Service
public class JwtService {
    
    // The JwtEncoder is a component that handles the encoding of JWTs. It takes the claims and header information and produces a signed JWT string.
    private final JwtEncoder jwtEncoder;
    // The issuer is a string that identifies the principal that issued the JWT. 
    private final String issuer;
    // How long the JWT is valid for.
    private final long expirationSeconds;

    public JwtService(
            JwtEncoder jwtEncoder,
            // Tells Spring Boot to retrieve the issuer and expiration time from the application's configuration.
            @Value("${locus.jwt.issuer}") String issuer,
            @Value("${locus.jwt.expiration-seconds}")
                    long expirationSeconds) {

        this.jwtEncoder = jwtEncoder;
        this.issuer = issuer;
        this.expirationSeconds = expirationSeconds;
    }

    // Generates a JWT for a given user ID. The token includes claims such as the issuer, subject (user ID), issued at time, and expiration time.
    public String generateToken(String userId) {

        Instant now = Instant.now();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(userId)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(expirationSeconds))
                .build();
        // Creates a JWS (JSON Web Signature) header that specifies the algorithm used for signing the JWT. 
        JwsHeader header = JwsHeader
                .with(MacAlgorithm.HS256)
                .build();
        // Encodes the claims and header into a signed JWT string using the JwtEncoder. 
        return jwtEncoder.encode(
                JwtEncoderParameters.from(header, claims)
        ).getTokenValue();
    }
}
