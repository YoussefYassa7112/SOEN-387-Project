package com.squared.locus.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

class RateLimitingTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String ME = "/api/v1/me/parking-spots";
    private static final String EXPENSIVE = "/api/v1/parking-spots/interpret-search";
    private static final String PUBLIC = "/api/v1/public";

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // ---- Test scenarios ----

    @Test
    void ipLimitBlocksAnonymousClientAfterCapacityIsUsed() throws Exception {
        MockMvc mvc = mockMvc(true, 3, 100, 100);

        for (int i = 0; i < 3; i++) {
            assertThat(call(mvc, "GET", PUBLIC, null, "10.0.0.1", null).getResponse().getStatus()).isEqualTo(200);
        }

        MvcResult blocked = call(mvc, "GET", PUBLIC, null, "10.0.0.1", "test-corr-1");
        assertThat(blocked.getResponse().getStatus()).isEqualTo(429);
        assertThat(blocked.getResponse().getHeader("Retry-After")).isNotNull();
        assertThat(Long.parseLong(blocked.getResponse().getHeader("Retry-After"))).isPositive();
        assertThat(blocked.getResponse().getHeader("X-Correlation-Id")).isEqualTo("test-corr-1");

        JsonNode body = MAPPER.readTree(blocked.getResponse().getContentAsString());
        assertThat(body.get("code").asText()).isEqualTo("RATE_LIMITED");
        assertThat(body.get("correlationId").asText()).isEqualTo("test-corr-1");
        assertThat(body.has("details")).isFalse();
    }

    @Test
    void ipLimitIsSeparatePerAddress() throws Exception {
        MockMvc mvc = mockMvc(true, 2, 100, 100);

        call(mvc, "GET", PUBLIC, null, "10.0.0.1", null);
        call(mvc, "GET", PUBLIC, null, "10.0.0.1", null);
        assertThat(call(mvc, "GET", PUBLIC, null, "10.0.0.1", null).getResponse().getStatus()).isEqualTo(429);

        assertThat(call(mvc, "GET", PUBLIC, null, "10.0.0.2", null).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void userLimitIsPerUserEvenFromTheSameIp() throws Exception {
        MockMvc mvc = mockMvc(true, 100, 2, 100);

        assertThat(call(mvc, "GET", ME, "alice", "10.0.0.1", null).getResponse().getStatus()).isEqualTo(200);
        assertThat(call(mvc, "GET", ME, "alice", "10.0.0.1", null).getResponse().getStatus()).isEqualTo(200);

        MvcResult blocked = call(mvc, "GET", ME, "alice", "10.0.0.1", null);
        assertThat(blocked.getResponse().getStatus()).isEqualTo(429);
        JsonNode body = MAPPER.readTree(blocked.getResponse().getContentAsString());
        assertThat(body.get("code").asText()).isEqualTo("RATE_LIMITED");

        // Bob shares the same IP but has his own bucket.
        assertThat(call(mvc, "GET", ME, "bob", "10.0.0.1", null).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void anonymousRequestsSkipTheUserLimit() throws Exception {
        MockMvc mvc = mockMvc(true, 100, 1, 1);

        for (int i = 0; i < 5; i++) {
            assertThat(call(mvc, "GET", PUBLIC, null, "10.0.0.1", null).getResponse().getStatus()).isEqualTo(200);
        }
    }

    @Test
    void expensiveEndpointHasItsOwnStricterLimit() throws Exception {
        MockMvc mvc = mockMvc(true, 100, 100, 2);

        assertThat(call(mvc, "POST", EXPENSIVE, "alice", "10.0.0.1", null).getResponse().getStatus()).isEqualTo(200);
        assertThat(call(mvc, "POST", EXPENSIVE, "alice", "10.0.0.1", null).getResponse().getStatus()).isEqualTo(200);
        assertThat(call(mvc, "POST", EXPENSIVE, "alice", "10.0.0.1", null).getResponse().getStatus()).isEqualTo(429);

        // The general bucket is untouched, so a normal endpoint still works for Alice.
        assertThat(call(mvc, "GET", ME, "alice", "10.0.0.1", null).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void disabledFlagTurnsOffIpLimit() throws Exception {
        MockMvc mvc = mockMvc(false, 1, 100, 100);

        for (int i = 0; i < 5; i++) {
            assertThat(call(mvc, "GET", PUBLIC, null, "10.0.0.1", null).getResponse().getStatus()).isEqualTo(200);
        }
    }

    // ---- Test setup ----

    /** Builds the real filters and interceptor with small limits and a refill period too long to matter. */
    private MockMvc mockMvc(boolean enabled, long ipCapacity, long userCapacity, long expensiveCapacity) {
        Duration slowRefill = Duration.ofHours(1);

        RateLimitFilter ipFilter = new RateLimitFilter(
                MAPPER, enabled, ipCapacity, ipCapacity, slowRefill);
        UserRateLimitInterceptor userInterceptor = new UserRateLimitInterceptor(
                MAPPER, userCapacity, slowRefill, expensiveCapacity, slowRefill);

        return MockMvcBuilders.standaloneSetup(new TestController())
                .addFilters(new CorrelationIdFilter(), ipFilter, new FakeAuthFilter())
                .addInterceptors(userInterceptor)
                .build();
    }

    private MvcResult call(MockMvc mvc, String method, String path, String user, String ip, String correlationId)
            throws Exception {
        MockHttpServletRequestBuilder request = "POST".equals(method) ? post(path) : get(path);
        request.with(r -> {
            r.setRemoteAddr(ip);
            return r;
        });
        if (user != null) {
            request.header("X-Test-User", user);
        }
        if (correlationId != null) {
            request.header(CorrelationIdFilter.HEADER, correlationId);
        }
        return mvc.perform(request).andReturn();
    }

    /** Stands in for Spring Security: turns the X-Test-User header into an authenticated JWT. */
    static class FakeAuthFilter extends OncePerRequestFilter {
        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                        FilterChain chain) throws ServletException, IOException {
            String user = request.getHeader("X-Test-User");
            if (user != null) {
                Jwt jwt = Jwt.withTokenValue("test-token")
                        .header("alg", "none")
                        .subject(user)
                        .build();
                SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
            }
            try {
                chain.doFilter(request, response);
            } finally {
                SecurityContextHolder.clearContext();
            }
        }
    }

    @RestController
    static class TestController {
        @GetMapping(PUBLIC)
        String publicEndpoint() { return "ok"; }

        @GetMapping(ME)
        String myListings() { return "ok"; }

        @PostMapping(EXPENSIVE)
        String interpret() { return "ok"; }
    }
}