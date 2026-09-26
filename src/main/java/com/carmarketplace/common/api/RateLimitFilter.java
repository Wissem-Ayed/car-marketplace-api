package com.carmarketplace.common.api;

import com.carmarketplace.config.RateLimitProperties;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class RateLimitFilter extends OncePerRequestFilter {

    private static final Set<String> READ_METHODS = Set.of("GET", "HEAD", "OPTIONS");
    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final RateLimitProperties properties;
    private final JsonMapper jsonMapper;
    private final Cache<String, Bucket> readBuckets = newBucketCache();
    private final Cache<String, Bucket> writeBuckets = newBucketCache();

    public RateLimitFilter(RateLimitProperties properties, JsonMapper jsonMapper) {
        this.properties = properties;
        this.jsonMapper = jsonMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !properties.enabled() || !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        boolean write = !READ_METHODS.contains(request.getMethod());
        int limit = write ? properties.writesPerMinute() : properties.requestsPerMinute();
        Bucket bucket = (write ? writeBuckets : readBuckets).get(clientKey(request), key -> newBucket(limit));
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

        response.setHeader("RateLimit-Limit", String.valueOf(limit));
        response.setHeader("RateLimit-Remaining", String.valueOf(probe.getRemainingTokens()));
        if (probe.isConsumed()) {
            chain.doFilter(request, response);
            return;
        }
        long retryAfterSeconds = Math.max(1, TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill()) + 1);
        rejectTooManyRequests(request, response, retryAfterSeconds, write);
    }

    private void rejectTooManyRequests(HttpServletRequest request, HttpServletResponse response,
                                       long retryAfterSeconds, boolean write) throws IOException {
        Map<String, Object> problem = new LinkedHashMap<>();
        problem.put("type", "about:blank");
        problem.put("title", "Too many requests");
        problem.put("status", HttpStatus.TOO_MANY_REQUESTS.value());
        problem.put("detail", "Rate limit of %d %s per minute exceeded; retry in %d seconds".formatted(
                write ? properties.writesPerMinute() : properties.requestsPerMinute(),
                write ? "changes" : "requests", retryAfterSeconds));
        problem.put("instance", request.getRequestURI());
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds));
        response.setContentType("application/problem+json");
        jsonMapper.writeValue(response.getOutputStream(), problem);
    }

    private static String clientKey(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken token) {
            return "user:" + token.getToken().getSubject();
        }
        return "ip:" + request.getRemoteAddr();
    }

    private static Bucket newBucket(int limitPerMinute) {
        return Bucket.builder()
                .addLimit(limit -> limit.capacity(limitPerMinute).refillGreedy(limitPerMinute, WINDOW))
                .build();
    }

    private static Cache<String, Bucket> newBucketCache() {
        return Caffeine.newBuilder()
                .expireAfterAccess(Duration.ofMinutes(10))
                .maximumSize(100_000)
                .build();
    }
}
