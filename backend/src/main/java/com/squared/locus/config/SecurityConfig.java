package com.squared.locus.config;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

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

                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(
                                new HttpStatusEntryPoint(
                                        HttpStatus.UNAUTHORIZED
                                )
                        )
                )

                // Validate Locus JWT bearer tokens
                .oauth2ResourceServer(oauth ->
                        oauth.jwt(Customizer.withDefaults())
                )

                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())

                .build();
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
