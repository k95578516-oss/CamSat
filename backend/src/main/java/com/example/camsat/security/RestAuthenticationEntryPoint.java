package com.example.camsat.security;

import com.example.camsat.web.ApiErrorBody;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {
    private final ObjectMapper mapper;
    public RestAuthenticationEntryPoint(ObjectMapper mapper) { this.mapper = mapper; }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException ex) throws IOException {
        Object reason = request.getAttribute(JwtAuthenticationFilter.AUTH_ERROR_ATTRIBUTE);
        String message = "expired".equals(reason) ? "Your session has expired. Sign in again."
                : "invalid".equals(reason) ? "Your session is not valid. Sign in again."
                : "Authentication is required.";
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        mapper.writeValue(response.getOutputStream(), ApiErrorBody.of(401, "Unauthorized", message, null));
    }
}
