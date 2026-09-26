package com.carmarketplace.common.api;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ProblemDetailsSecurityHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private static final String PROBLEM_JSON = "application/problem+json";

    private final AuthenticationEntryPoint bearerEntryPoint = new BearerTokenAuthenticationEntryPoint();
    private final AccessDeniedHandler bearerAccessDeniedHandler = new BearerTokenAccessDeniedHandler();
    private final JsonMapper jsonMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException exception) throws IOException, ServletException {
        bearerEntryPoint.commence(request, response, exception);
        write(request, response, HttpStatus.UNAUTHORIZED, "Authentication required",
                "A valid access token is required. Log in and send it as 'Authorization: Bearer <token>'.");
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException exception) throws IOException, ServletException {
        bearerAccessDeniedHandler.handle(request, response, exception);
        write(request, response, HttpStatus.FORBIDDEN, "Forbidden", "You are not allowed to perform this action.");
    }

    private void write(HttpServletRequest request, HttpServletResponse response, HttpStatus status,
                       String title, String detail) throws IOException {
        Map<String, Object> problem = new LinkedHashMap<>();
        problem.put("type", "about:blank");
        problem.put("title", title);
        problem.put("status", status.value());
        problem.put("detail", detail);
        problem.put("instance", request.getRequestURI());
        response.setStatus(status.value());
        response.setContentType(PROBLEM_JSON);
        jsonMapper.writeValue(response.getOutputStream(), problem);
    }
}
