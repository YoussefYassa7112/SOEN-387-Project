package com.squared.locus.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Chapter 10: liveness and readiness checks, used by Docker and Render.
// Starts the whole app against a real PostgreSQL (the same local test database as
// ReservationConcurrencyTest), because readiness checks that the database answers.
@SpringBootTest(properties = {
        "spring.config.import=", "locus.google.client-id=test",
        "locus.jwt.secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
})
@AutoConfigureMockMvc
class HealthCheckTest {

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getProperty(
                "test.database.url",
                "jdbc:postgresql://localhost:5432/locus_concurrency_test"));
        registry.add("spring.datasource.username", () -> System.getProperty(
                "test.database.username", System.getProperty("user.name", "postgres")));
        registry.add("spring.datasource.password", () -> System.getProperty(
                "test.database.password", ""));
    }

    @Autowired MockMvc mvc;

    @Test
    void livenessIsUpWithoutAToken() throws Exception {
        mvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void readinessIsUpWhenTheDatabaseAnswers() throws Exception {
        mvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void healthDoesNotShowInternalDetails() throws Exception {
        // show-details: never, so the database name and version are not visible to callers.
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components").doesNotExist());
    }
}
