package com.squared.locus.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.MDC;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

// Chapter 11: every request gets a correlation id, it goes back in the response,
// and the log line written for the request carries it.
@ExtendWith(OutputCaptureExtension.class)
class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @Test
    void reusesASafeIdSentByTheClient(CapturedOutput output) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/parking-spots");
        request.addHeader("X-Correlation-Id", "demo-123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        String[] idSeenByTheApp = new String[1];

        // The "rest of the app": remembers the id that log lines written during the request carry.
        filter.doFilter(request, response, (req, res) -> idSeenByTheApp[0] = MDC.get("correlationId"));

        assertThat(response.getHeader("X-Correlation-Id")).isEqualTo("demo-123");
        assertThat(idSeenByTheApp[0]).isEqualTo("demo-123");
        assertThat(output).contains("GET /api/v1/parking-spots -> 200");
    }

    @Test
    void createsANewIdWhenTheClientSendsNone() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(new MockHttpServletRequest("GET", "/"), response, new MockFilterChain());

        assertThat(response.getHeader("X-Correlation-Id")).matches("req_[0-9a-f]{12}");
    }

    @Test
    void replacesAnUnsafeIdFromTheClient() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/");
        // Someone trying to inject a fake log line through the header.
        request.addHeader("X-Correlation-Id", "abc\n{\"level\":\"ERROR\"}");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader("X-Correlation-Id")).startsWith("req_");
    }

    @Test
    void cleansUpAfterTheRequest() throws Exception {
        filter.doFilter(new MockHttpServletRequest("GET", "/"), new MockHttpServletResponse(), new MockFilterChain());

        assertThat(MDC.get("correlationId")).isNull();   // the next request on this thread starts clean
    }
}
