package com.squared.locus.controller;

import com.squared.locus.domain.ConflictException;
import com.squared.locus.domain.NotFoundException;
import com.squared.locus.service.ForbiddenException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Checks that every kind of error comes back in the same ApiError shape,
 * with the right status, and never with a stack trace.
 * A small fake controller below throws each kind of error on purpose.
 *
 * @WithMockUser and csrf() get the requests past Spring Security,
 * because this test is only about the error responses, not about sign-in.
 */
@WebMvcTest(GlobalExceptionHandlerTest.FakeController.class)
@Import(GlobalExceptionHandlerTest.FakeController.class)
@WithMockUser
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void notFoundReturns404WithErrorShape() throws Exception {
        mockMvc.perform(get("/test/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SPOT_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Parking spot not found."))
                .andExpect(jsonPath("$.correlationId").isNotEmpty());
    }

    @Test
    void conflictReturns409() throws Exception {
        mockMvc.perform(get("/test/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PARKING_SPOT_UNAVAILABLE"));
    }

    @Test
    void forbiddenReturns403() throws Exception {
        mockMvc.perform(get("/test/forbidden"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_OWNER"));
    }

    @Test
    void invalidFieldReturns400WithFieldDetails() throws Exception {
        mockMvc.perform(post("/test/body").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": \"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details.fields.title").exists());
    }

    @Test
    void brokenJsonReturns400() throws Exception {
        mockMvc.perform(post("/test/body").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ this is not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST_BODY"));
    }

    @Test
    void unexpectedErrorReturns500WithoutStackTrace() throws Exception {
        mockMvc.perform(get("/test/crash"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(content().string(not(containsString("secret detail"))))
                .andExpect(content().string(not(containsString("Exception"))));
    }

    @Test
    void unknownPathReturns404() throws Exception {
        mockMvc.perform(get("/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void wrongMethodReturns405() throws Exception {
        mockMvc.perform(post("/test/not-found").with(csrf()))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    void correlationIdFromClientIsReused() throws Exception {
        mockMvc.perform(get("/test/not-found").header("X-Correlation-Id", "abc-123"))
                .andExpect(header().string("X-Correlation-Id", "abc-123"))
                .andExpect(jsonPath("$.correlationId").value("abc-123"));
    }

    @Test
    void unsafeCorrelationIdIsReplaced() throws Exception {
        mockMvc.perform(get("/test/not-found").header("X-Correlation-Id", "<script>"))
                .andExpect(jsonPath("$.correlationId").value(containsString("req_")));
    }

    /** Only used by this test. Each endpoint throws one kind of error. */
    @RestController
    static class FakeController {

        record Body(@NotBlank String title) {
        }

        @GetMapping("/test/not-found")
        void notFound() {
            throw new NotFoundException("SPOT_NOT_FOUND", "Parking spot not found.");
        }

        @GetMapping("/test/conflict")
        void conflict() {
            throw new ConflictException("PARKING_SPOT_UNAVAILABLE", "Already reserved.");
        }

        @GetMapping("/test/forbidden")
        void forbidden() {
            throw new ForbiddenException("NOT_OWNER", "You do not own this listing.");
        }

        @PostMapping("/test/body")
        void body(@Valid @RequestBody Body body) {
        }

        @GetMapping("/test/crash")
        void crash() {
            throw new IllegalStateException("secret detail");
        }
    }
}
