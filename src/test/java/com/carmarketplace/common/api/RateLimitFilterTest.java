package com.carmarketplace.common.api;

import com.carmarketplace.config.RateLimitProperties;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitFilterTest {

    private final RateLimitFilter filter =
            new RateLimitFilter(new RateLimitProperties(true, 3, 1), JsonMapper.builder().build());

    @Test
    void letsRequestsThroughUntilTheLimitThenAnswers429() throws Exception {
        for (int remaining = 2; remaining >= 0; remaining--) {
            MockHttpServletResponse allowed = send("GET", "/api/v1/cars", "10.0.0.1");
            assertThat(allowed.getStatus()).isEqualTo(200);
            assertThat(allowed.getHeader("RateLimit-Limit")).isEqualTo("3");
            assertThat(allowed.getHeader("RateLimit-Remaining")).isEqualTo(String.valueOf(remaining));
        }

        MockHttpServletResponse rejected = send("GET", "/api/v1/cars", "10.0.0.1");

        assertThat(rejected.getStatus()).isEqualTo(429);
        assertThat(rejected.getContentType()).isEqualTo("application/problem+json");
        assertThat(Integer.parseInt(rejected.getHeader("Retry-After"))).isBetween(1, 60);
        assertThat(rejected.getContentAsString(StandardCharsets.UTF_8))
                .contains("\"title\":\"Too many requests\"")
                .contains("Rate limit of 3 requests per minute exceeded");
    }

    @Test
    void appliesAStricterLimitToChanges() throws Exception {
        assertThat(send("POST", "/api/v1/cars", "10.0.0.2").getStatus()).isEqualTo(200);
        assertThat(send("POST", "/api/v1/cars", "10.0.0.2").getStatus()).isEqualTo(429);
        assertThat(send("GET", "/api/v1/cars", "10.0.0.2").getStatus()).isEqualTo(200);
    }

    @Test
    void keepsSeparateBudgetsPerClient() throws Exception {
        send("POST", "/api/v1/cars", "10.0.0.3");

        assertThat(send("POST", "/api/v1/cars", "10.0.0.3").getStatus()).isEqualTo(429);
        assertThat(send("POST", "/api/v1/cars", "10.0.0.4").getStatus()).isEqualTo(200);
    }

    @Test
    void neverLimitsHealthChecksOrDocumentation() throws Exception {
        for (int i = 0; i < 10; i++) {
            assertThat(send("GET", "/actuator/health", "10.0.0.5").getStatus()).isEqualTo(200);
        }
    }

    private MockHttpServletResponse send(String method, String uri, String clientIp) throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.setRemoteAddr(clientIp);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }
}
