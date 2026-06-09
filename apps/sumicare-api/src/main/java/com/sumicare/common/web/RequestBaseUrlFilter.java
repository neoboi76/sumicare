package com.sumicare.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestBaseUrlFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            RequestBaseUrlContext.set(resolveOrigin(request));
            chain.doFilter(request, response);
        } finally {
            RequestBaseUrlContext.clear();
        }
    }

    private String resolveOrigin(HttpServletRequest request) {
        String origin = request.getHeader("Origin");
        if (origin != null && !origin.isBlank()) {
            return trimTrailingSlash(origin.trim());
        }
        String host = firstHeaderValue(request.getHeader("X-Forwarded-Host"));
        String proto = firstHeaderValue(request.getHeader("X-Forwarded-Proto"));
        if (host == null || host.isBlank()) {
            host = request.getHeader("Host");
        }
        if (host == null || host.isBlank()) {
            return "";
        }
        String scheme = proto != null && !proto.isBlank() ? proto : request.getScheme();
        return scheme + "://" + host;
    }

    private String firstHeaderValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        int comma = value.indexOf(',');
        return (comma > 0 ? value.substring(0, comma) : value).trim();
    }

    private String trimTrailingSlash(String value) {
        return value.replaceAll("/+$", "");
    }
}
