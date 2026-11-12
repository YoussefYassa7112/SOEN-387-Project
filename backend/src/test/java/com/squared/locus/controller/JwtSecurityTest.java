package com.squared.locus.controller;

import com.squared.locus.config.*;
import com.squared.locus.service.*;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.test.web.servlet.MockMvc;
import java.time.Instant;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {ParkingSpotController.class, ReservationController.class}, properties = {
    "spring.config.import=", "locus.jwt.secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
    "locus.jwt.issuer=locus-api"
})
@Import({SecurityConfig.class, JwtConfig.class})
class JwtSecurityTest {
    @Autowired MockMvc mvc;
    @Autowired JwtEncoder encoder;
    @MockitoBean ParkingSpotService spots;
    @MockitoBean ReservationService reservations;
    private final UUID user = UUID.randomUUID();

    private String token(JwtEncoder signer, String issuer, Instant issued, Instant expires) {
        var claims = JwtClaimsSet.builder().issuer(issuer).subject(user.toString())
                .issuedAt(issued).expiresAt(expires).build();
        return signer.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    private String validToken() {
        return token(encoder, "locus-api", Instant.now(), Instant.now().plusSeconds(3600));
    }

    private void rejected(String bearer) throws Exception {
        var request = get("/api/v1/me/parking-spots");
        if (bearer != null) request.header("Authorization", "Bearer " + bearer);
        mvc.perform(request).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        verifyNoInteractions(spots, reservations);
    }

    @Test
    void missingTokenIsRejected() throws Exception { rejected(null); }

    @Test
    void malformedTokenIsRejected() throws Exception { rejected("not-a-jwt"); }

    @Test
    void forgedSignatureIsRejected() throws Exception {
        byte[] otherSecret = new byte[32];
        Arrays.fill(otherSecret, (byte) 7);
        var attacker = new NimbusJwtEncoder(new ImmutableSecret<>(otherSecret));
        rejected(token(attacker, "locus-api", Instant.now(), Instant.now().plusSeconds(3600)));
    }

    @Test
    void expiredTokenIsRejected() throws Exception {
        rejected(token(encoder, "locus-api", Instant.now().minusSeconds(7200),
                Instant.now().minusSeconds(3600)));
    }

    @Test
    void wrongIssuerIsRejected() throws Exception {
        rejected(token(encoder, "other-issuer", Instant.now(), Instant.now().plusSeconds(3600)));
    }

    @Test
    void validTokenUsesAuthenticatedUser() throws Exception {
        when(spots.getOwnerListings(user)).thenReturn(List.of());
        mvc.perform(get("/api/v1/me/parking-spots")
                        .header("Authorization", "Bearer " + validToken()))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
        verify(spots).getOwnerListings(user);
    }

    @Test
    void authenticatedDriverCannotReadAnotherDriversReservation() throws Exception {
        var id = UUID.randomUUID();
        when(reservations.getById(id, user)).thenThrow(new ForbiddenException(
                "NOT_RESERVATION_OWNER", "You can only view your own reservations."));
        mvc.perform(get("/api/v1/reservations/{id}", id)
                        .header("Authorization", "Bearer " + validToken()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_RESERVATION_OWNER"));
        verify(reservations).getById(id, user);
    }
}
