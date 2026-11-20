package com.squared.locus.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.Refill;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Per-user rate limit for authenticated requests.
 * Runs after Spring Security, so the JWT is already validated.
 * Anonymous requests skip this check and are covered by RateLimitFilter.
 */
@Component
public class UserRateLimitInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(UserRateLimitInterceptor.class);

    private final ObjectMapper objectMapper;
    private final Supplier<Bucket> generalBucket;
    private final Supplier<Bucket> expensiveBucket;

    private final Cache<String, Bucket> generalBuckets = Caffeine.newBuilder()
            .maximumSize(100_000)
            .expireAfterAccess(30, TimeUnit.MINUTES)
            .build();

    private final Cache<String, Bucket> expensiveBuckets = Caffeine.newBuilder()
            .maximumSize(100_000)
            .expireAfterAccess(30, TimeUnit.MINUTES)
            .build();

    public UserRateLimitInterceptor(ObjectMapper objectMapper,
                                    @Value("${locus.rate-limit.user.capacity:60}") long capacity,
                                    @Value("${locus.rate-limit.user.refill-period:PT1M}") Duration period,
                                    @Value("${locus.rate-limit.expensive.capacity:10}") long expensiveCapacity,
                                    @Value("${locus.rate-limit.expensive.refill-period:PT1M}") Duration expensivePeriod) {
        this.objectMapper = objectMapper;
        this.generalBucket = () -> newBucket(capacity, period);
        this.expensiveBucket = () -> newBucket(expensiveCapacity, expensivePeriod);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        String userId = currentUserId();
        if (userId == null) {
            return true; // anonymous: RateLimitFilter already applied
        }

        String path = request.getRequestURI();
        boolean expensive = isExpensive(path);
        Cache<String, Bucket> cache = expensive ? expensiveBuckets : generalBuckets;
        Supplier<Bucket> newBucket = expensive ? expensiveBucket : generalBucket;

        Bucket bucket = cache.get(userId, k -> newBucket.get());
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (probe.isConsumed()) {
            return true;
        }

        long retryAfter = Math.max(1, probe.getNanosToWaitForRefill() / 1_000_000_000L);
        log.warn("User rate limit exceeded for user {} on {} (expensive={})", userId, path, expensive);
        writeRateLimited(response, retryAfter);
        return false; // stops the request before the controller runs
    }

    private String currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
            return jwt.getSubject();
        }
        return null;
    }

    // Only authenticated paths. /auth/google is anonymous, so it is never counted here.
    private boolean isExpensive(String path) {
        return path.endsWith("/interpret-search")
                || path.endsWith("/lookup-event")
                || path.endsWith("/event-parking");
    }

    private static Bucket newBucket(long capacity, Duration period) {
        return Bucket.builder()
                .addLimit(Bandwidth.classic(capacity, Refill.greedy(capacity, period)))
                .build();
    }

    private void writeRateLimited(HttpServletResponse response, long retryAfter) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("Retry-After", String.valueOf(retryAfter));
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        ApiError body = new ApiError(
                "RATE_LIMITED",
                "Too many requests. Try again in " + retryAfter + " seconds.",
                MDC.get(CorrelationIdFilter.MDC_KEY),
                null);
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}