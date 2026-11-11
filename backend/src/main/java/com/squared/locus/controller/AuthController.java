package com.squared.locus.controller;

import com.squared.locus.service.AuthService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    // POST /api/v1/auth/google
    // Used @Valid to validate the request body and @RequestBody to bind the request body to the GoogleLoginRequest object.
    @PostMapping("/google")
    public ResponseEntity<AuthService.TokenResult> googleLogin(
            @Valid @RequestBody GoogleLoginRequest request) {

        // Delegates the actual authentication logic to the AuthService and returns the result as a ResponseEntity.
        return ResponseEntity.ok(
                authService.signInWithGoogle(request.idToken())
        );
    }

    // DTO for the request body of the Google login endpoint. (needs to reject missing or blank token)
    public record GoogleLoginRequest(
            @NotBlank String idToken
    ) {}
}
