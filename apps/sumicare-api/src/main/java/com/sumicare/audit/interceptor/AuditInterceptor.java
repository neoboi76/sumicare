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

        ResolvedTarget target = resolveTarget(request.getMethod(), request.getRequestURI());
        auditService.record(organizationId, actorId, ap.role(),
                target.action,
                target.entity,
                target.targetId,
                null,
                request.getRemoteAddr());
    }

    private record ResolvedTarget(String entity, String targetId, String action) {}

    private ResolvedTarget resolveTarget(String method, String uri) {
        String defaultAction = method + " " + uri;
        if (uri == null) return new ResolvedTarget(null, null, defaultAction);

        String[] segs = uri.split("/");
        if (segs.length >= 5 && "api".equals(segs[1]) && "cashier".equals(segs[2]) && "orders".equals(segs[3])) {
            String orderId = segs[4];
            String action;
            if (segs.length == 5) action = "ORDER.CREATE";
            else if (segs.length >= 6) {
                String sub = segs[5];
                action = switch (sub) {
                    case "payments" -> "ORDER.PAYMENT_RECORDED";
                    case "mark-paid" -> "ORDER.MARK_PAID";
                    case "cancel" -> "ORDER.CANCEL";
                    case "items" -> "DELETE".equals(method) ? "ORDER.ITEM_REMOVED" : "ORDER.ITEM_ADDED";
                    case "adjustments" -> "ORDER.ADJUSTMENT_APPLIED";
                    default -> "ORDER." + sub.toUpperCase();
                };
            } else {
                action = defaultAction;
            }
            return new ResolvedTarget("ORDER", orderId, action);
        }
        if (segs.length >= 4 && "api".equals(segs[1]) && "treatment-slips".equals(segs[2])) {
            String slipId = segs[3];
            String action = switch (method) {
                case "PATCH" -> "SLIP.UPDATE";
                case "POST" -> "SLIP.CREATE";
                default -> defaultAction;
            };
            return new ResolvedTarget("TREATMENT_SLIP", slipId, action);
        }
        if (segs.length >= 5 && "api".equals(segs[1]) && "contact-messages".equals(segs[2])) {
            return new ResolvedTarget("CONTACT_MESSAGE", segs[3], "CONTACT_MESSAGE.MARK_READ");
        }
        return new ResolvedTarget(null, null, defaultAction);
    }

    private UUID parseUuid(String value) {
        try {
            return value == null ? null : UUID.fromString(value);
        } catch (Exception e) {
            return null;
        }
    }
}
