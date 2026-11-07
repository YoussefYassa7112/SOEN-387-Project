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

    @PostMapping("/google")
    public ResponseEntity<AuthService.TokenResult> googleLogin(
            @Valid @RequestBody GoogleLoginRequest request) {

        return ResponseEntity.ok(
                authService.signInWithGoogle(request.idToken())
        );
    }

    public record GoogleLoginRequest(
            @NotBlank String idToken
    ) {}
}
