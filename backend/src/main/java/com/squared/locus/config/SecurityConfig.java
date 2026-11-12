package com.squared.locus.config;

import java.util.List;

import org.slf4j.MDC;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.squared.locus.controller.ApiError;
import com.squared.locus.controller.CorrelationIdFilter;

import jakarta.servlet.DispatcherType;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http)
            throws Exception {

        return http
                .csrf(csrf -> csrf.disable())

                // Allow requests from the React frontend
                .cors(cors -> cors.configurationSource(
                        corsConfigurationSource()
                ))

                // Stateless authentication using JWT
                .sessionManagement(session -> session
                        .sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS
                        ))
                        

                .authorizeHttpRequests(auth -> auth

                        // Allow Spring to render error responses
                        .dispatcherTypeMatchers(
                                DispatcherType.ERROR
                        ).permitAll()

                        // Google login endpoint
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/auth/google"
                        ).permitAll()

                        // Public parking search
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/parking-spots",
                                "/api/v1/parking-spots/*",
                                "/api/v1/parking-spots/*/availability"
                        ).permitAll()

                        // Health and API documentation
                        .requestMatchers(
                                "/actuator/health",
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/v3/api-docs/**",
                                "/openapi.yaml"
                        ).permitAll()

                        // All other endpoints require authentication
                        .anyRequest().authenticated()
                )

                // Missing token: answer 401 with the ApiError JSON from openapi.yaml
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(
                                unauthorizedEntryPoint()
                        )
                )

                // Validate Locus JWT bearer tokens
                // Invalid or expired token: same 401 ApiError JSON
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(Customizer.withDefaults())
                        .authenticationEntryPoint(unauthorizedEntryPoint())
                )

                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())

                .build();
    }

    // Writes the 401 error body in the same ApiError shape as every other error.
    private AuthenticationEntryPoint unauthorizedEntryPoint() {
        return (request, response, authException) -> {
            ApiError error = new ApiError(
                    "AUTHENTICATION_REQUIRED",
                    "A valid bearer token is required.",
                    MDC.get(CorrelationIdFilter.MDC_KEY),
                    null
            );
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            new ObjectMapper().writeValue(response.getOutputStream(), error);
        };
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {

        CorsConfiguration config = new CorsConfiguration();

        config.setAllowedOrigins(
                List.of("http://localhost:5173")
        );

        config.setAllowedMethods(
                List.of(
                        "GET",
                        "POST",
                        "PUT",
                        "PATCH",
                        "DELETE",
                        "OPTIONS"
                )
        );

        config.setAllowedHeaders(
                List.of(
                        "Authorization",
                        "Content-Type",
                        "Idempotency-Key"
                )
        );

        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();

        source.registerCorsConfiguration("/**", config);

        return source;
    }
}
