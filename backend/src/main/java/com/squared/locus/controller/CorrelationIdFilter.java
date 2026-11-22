package com.squared.locus.controller;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Gives every request a correlation id.
 *
 * If the client sends a safe X-Correlation-Id header we reuse it, otherwise we create one.
 * The id is put in the logging context (MDC) so every log line of this request carries it,
 * sent back in the response header, and copied into error bodies by GlobalExceptionHandler.
 * At the end of the request we write one log line with the method, path, status and time,
 * so every request shows up in the logs with its id (Chapter 11).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";

    private static final Logger log = LoggerFactory.getLogger(CorrelationIdFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String correlationId = request.getHeader(HEADER);

        // Never trust the header blindly: only short ids with letters, digits, '-' or '_'.
        if (correlationId == null || !correlationId.matches("[A-Za-z0-9_-]{1,64}")) {
            correlationId = "req_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        }

        MDC.put(MDC_KEY, correlationId);
        response.setHeader(HEADER, correlationId);
        long startMillis = System.currentTimeMillis();
        try {
            chain.doFilter(request, response);
        } finally {
            // Only the path, never the query string or headers: those can hold personal data or tokens.
            log.info("{} {} -> {} in {} ms", request.getMethod(), request.getRequestURI(),
                    response.getStatus(), System.currentTimeMillis() - startMillis);
            MDC.remove(MDC_KEY); // threads are reused, so always clean up
        }
    }
}
