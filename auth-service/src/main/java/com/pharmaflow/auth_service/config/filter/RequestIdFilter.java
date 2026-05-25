package com.pharmaflow.auth_service.config.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER_REQUEST_ID = "X-Request-Id";
    public static final String MDC_REQUEST_ID = "requestId";

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        String requestId = request.getHeader(HEADER_REQUEST_ID);
        if (requestId == null || requestId.isBlank() || !isValidUuid(requestId)) {
            requestId = UUID.randomUUID().toString();
        }

        try {
            MDC.put(MDC_REQUEST_ID, requestId);
            response.setHeader(HEADER_REQUEST_ID, requestId);
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_REQUEST_ID);
        }
    }

    private static boolean isValidUuid(String value) {
        try {
            UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }
}
