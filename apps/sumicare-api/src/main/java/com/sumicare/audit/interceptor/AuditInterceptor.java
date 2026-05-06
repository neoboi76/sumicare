package com.sumicare.audit.interceptor;

import com.sumicare.audit.service.AuditService;
import com.sumicare.auth.filter.JwtAuthenticationFilter.AuthenticatedPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Set;
import java.util.UUID;

@Component
public class AuditInterceptor implements HandlerInterceptor {

    private static final Set<String> MUTATING_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

    private final AuditService auditService;

    public AuditInterceptor(AuditService auditService) {
        this.auditService = auditService;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        if (!MUTATING_METHODS.contains(request.getMethod())) return;
        if (response.getStatus() >= 400) return;
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) return;
        Object principal = authentication.getPrincipal();
        if (!(principal instanceof AuthenticatedPrincipal ap)) return;
        UUID actorId = parseUuid(ap.userId());
        UUID organizationId = parseUuid(ap.organizationId());
        if (actorId == null) return;
        auditService.record(organizationId, actorId, ap.role(),
                request.getMethod() + " " + request.getRequestURI(),
                null, null, null, request.getRemoteAddr());
    }

    private UUID parseUuid(String value) {
        try {
            return value == null ? null : UUID.fromString(value);
        } catch (Exception e) {
            return null;
        }
    }
}
