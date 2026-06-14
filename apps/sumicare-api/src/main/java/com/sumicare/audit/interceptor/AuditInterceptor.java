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
        String fallback = method + " " + uri;
        if (uri == null || !"api".equals(seg(uri, 1))) {
            return new ResolvedTarget(null, null, fallback);
        }

        String[] segs = uri.split("/");
        return switch (segs.length > 2 ? segs[2] : "") {
            case "cashier" -> resolveCashier(method, segs, fallback);
            case "bookings" -> resolveBooking(method, segs, fallback);
            case "walk-in" -> new ResolvedTarget("BOOKING", null, "BOOKING.WALK_IN_CREATE");
            case "sessions" -> resolveSession(segs, fallback);
            case "treatment-slips" -> resolveSlip(method, segs, fallback);
            case "decking" -> resolveDecking(method, segs, fallback);
            case "therapists" -> resolveCrud("THERAPIST", method, segs, "DEACTIVATE", fallback);
            case "shifts" -> resolveCrud("SHIFT", method, segs, "DELETE", fallback);
            case "vouchers" -> resolveCrud("VOUCHER", method, segs, "DELETE", fallback);
            case "clients" -> resolveCrud("CLIENT", method, segs, "DEACTIVATE", fallback);
            case "users" -> resolveUser(method, segs, fallback);
            case "attendance" -> new ResolvedTarget("ATTENDANCE", null, "ATTENDANCE." + upper(seg(uri, 3)));
            case "organization" -> new ResolvedTarget("ORGANIZATION", null, "ORGANIZATION.BRANDING_UPDATE");
            case "content" -> resolveContent(uri, fallback);
            case "feedback" -> new ResolvedTarget("FEEDBACK", null, "FEEDBACK.MARK_ALL_READ");
            case "contact-messages" -> new ResolvedTarget("CONTACT_MESSAGE", seg(uri, 3), "CONTACT_MESSAGE.MARK_READ");
            case "reports" -> new ResolvedTarget("REPORT", null, "REPORT." + upper(seg(uri, 3)) + "_REGENERATE");
            case "admin" -> resolveAdmin(method, segs, fallback);
            default -> new ResolvedTarget(null, null, fallback);
        };
    }

    private ResolvedTarget resolveCashier(String method, String[] segs, String fallback) {
        String resource = segs.length > 3 ? segs[3] : "";
        if ("orders".equals(resource)) {
            String orderId = segs.length > 4 ? segs[4] : null;
            String sub = segs.length > 5 ? segs[5] : null;
            String action;
            if (sub == null) {
                action = "POST".equals(method) ? "ORDER.CREATE" : "ORDER.UPDATE";
            } else {
                action = switch (sub) {
                    case "payments" -> "ORDER.PAYMENT_RECORDED";
                    case "paymongo" -> "ORDER.PAYMENT_" + upper(segs.length > 6 ? segs[6] : "GATEWAY");
                    case "refund" -> "ORDER.REFUNDED";
                    case "mark-paid" -> "ORDER.MARK_PAID";
                    case "cancel" -> "ORDER.CANCELLED";
                    case "open" -> "ORDER.REOPENED";
                    case "cancel-payment" -> "ORDER.PAYMENT_CANCELLED";
                    default -> "ORDER." + upper(sub);
                };
            }
            return new ResolvedTarget("ORDER", orderId, action);
        }
        if ("packages".equals(resource)) {
            return resolveCrud("PACKAGE", method, segs, "DEACTIVATE", fallback);
        }
        if ("ledger".equals(resource)) {
            return new ResolvedTarget("LEDGER_ACCOUNT", null, "LEDGER.ACCOUNT_CREATE");
        }
        if ("discount-templates".equals(resource)) {
            return resolveCrud("DISCOUNT_TEMPLATE", method, segs, "DELETE", fallback);
        }
        return new ResolvedTarget(null, null, fallback);
    }

    private ResolvedTarget resolveBooking(String method, String[] segs, String fallback) {
        if (segs.length == 3) {
            return new ResolvedTarget("BOOKING", null, "BOOKING.CREATE");
        }
        if ("attendees".equals(segs[3])) {
            return new ResolvedTarget("SESSION", seg(segs, 4), "SESSION.START");
        }
        String bookingId = segs[3];
        if (segs.length > 4 && "sessions".equals(segs[4])) {
            return new ResolvedTarget("SESSION", bookingId, "SESSION.START");
        }
        return new ResolvedTarget("BOOKING", bookingId, "BOOKING.UPDATE");
    }

    private ResolvedTarget resolveSession(String[] segs, String fallback) {
        String sessionId = segs.length > 3 ? segs[3] : null;
        String sub = segs.length > 4 ? segs[4] : null;
        if (sub == null) {
            return new ResolvedTarget("SESSION", sessionId, fallback);
        }
        String action = switch (sub) {
            case "cancel" -> "SESSION.CANCEL";
            case "end" -> "SESSION.END";
            case "extend" -> "SESSION.EXTEND";
            case "adjust-times" -> "SESSION.ADJUST_TIMES";
            default -> "SESSION." + upper(sub);
        };
        return new ResolvedTarget("SESSION", sessionId, action);
    }

    private ResolvedTarget resolveSlip(String method, String[] segs, String fallback) {
        if (segs.length > 3 && "from-session".equals(segs[3])) {
            return new ResolvedTarget("TREATMENT_SLIP", seg(segs, 4), "TREATMENT_SLIP.CREATE");
        }
        String slipId = segs.length > 3 ? segs[3] : null;
        return new ResolvedTarget("TREATMENT_SLIP", slipId, "TREATMENT_SLIP.UPDATE");
    }

    private ResolvedTarget resolveDecking(String method, String[] segs, String fallback) {
        if (segs.length > 3 && "backup".equals(segs[3])) {
            return new ResolvedTarget("DECKING", seg(segs, 4), "DECKING.BACKUP_INSERT");
        }
        String therapistId = segs.length > 3 ? segs[3] : null;
        String sub = segs.length > 4 ? segs[4] : null;
        if (sub == null) {
            String action = "DELETE".equals(method) ? "DECKING.REMOVE" : "DECKING.ADD";
            return new ResolvedTarget("DECKING", therapistId, action);
        }
        String action = switch (sub) {
            case "skip" -> segs.length > 5 && "cancel".equals(segs[5]) ? "DECKING.SKIP_CANCEL" : "DECKING.SKIP";
            case "flag" -> "DECKING.FLAG";
            case "rotate" -> "DECKING.ROTATE";
            default -> "DECKING." + upper(sub);
        };
        return new ResolvedTarget("DECKING", therapistId, action);
    }

    private ResolvedTarget resolveUser(String method, String[] segs, String fallback) {
        if (segs.length == 3) {
            return new ResolvedTarget("USER", null, "USER.CREATE");
        }
        String userId = segs[3];
        if (segs.length == 4) {
            String action = "DELETE".equals(method) ? "USER.DEACTIVATE" : "USER.UPDATE";
            return new ResolvedTarget("USER", userId, action);
        }
        String sub = segs[4];
        String action = switch (sub) {
            case "reactivate" -> "USER.REACTIVATE";
            case "unlock" -> "USER.UNLOCK";
            case "profile" -> "USER.PROFILE_UPDATE";
            case "request-password-reset" -> "USER.PASSWORD_RESET_REQUEST";
            case "send-reset-link" -> "USER.SEND_RESET_LINK";
            default -> "USER." + upper(sub);
        };
        boolean selfScoped = "me".equals(userId);
        return new ResolvedTarget("USER", selfScoped ? null : userId, action);
    }

    private ResolvedTarget resolveContent(String uri, String fallback) {
        if (uri.endsWith("/upload")) {
            return new ResolvedTarget("CONTENT", null, "CONTENT.UPLOAD");
        }
        if (uri.contains("/blocks/")) {
            return new ResolvedTarget("CONTENT", seg(uri, 4), "CONTENT.UPDATE");
        }
        return new ResolvedTarget("CONTENT", null, "CONTENT.CREATE");
    }

    private ResolvedTarget resolveAdmin(String method, String[] segs, String fallback) {
        String resource = segs.length > 3 ? segs[3] : "";
        if ("rooms".equals(resource)) {
            String roomId = segs.length > 4 ? segs[4] : null;
            String sub = segs.length > 5 ? segs[5] : null;
            if (roomId == null) {
                return new ResolvedTarget("ROOM", null, "ROOM.CREATE");
            }
            if ("beds".equals(sub)) {
                return new ResolvedTarget("BED", null, "BED.CREATE");
            }
            if ("reactivate".equals(sub)) {
                return new ResolvedTarget("ROOM", roomId, "ROOM.REACTIVATE");
            }
            String action = "DELETE".equals(method) ? "ROOM.DEACTIVATE" : "ROOM.UPDATE";
            return new ResolvedTarget("ROOM", roomId, action);
        }
        if ("beds".equals(resource)) {
            String bedId = segs.length > 4 ? segs[4] : null;
            String sub = segs.length > 5 ? segs[5] : null;
            String action = "reactivate".equals(sub) ? "BED.REACTIVATE" : "BED.DEACTIVATE";
            return new ResolvedTarget("BED", bedId, action);
        }
        if ("services".equals(resource)) {
            return resolveCrud("SERVICE", method, segs, "DELETE", fallback);
        }
        return new ResolvedTarget(null, null, fallback);
    }

    private ResolvedTarget resolveCrud(String entity, String method, String[] segs, String deleteAction, String fallback) {
        boolean hasId = segs.length > 3;
        String id = hasId ? segs[3] : null;
        if (!hasId) {
            return new ResolvedTarget(entity, null, entity + ".CREATE");
        }
        if (segs.length > 4 && "reactivate".equals(segs[4])) {
            return new ResolvedTarget(entity, id, entity + ".REACTIVATE");
        }
        String action = switch (method) {
            case "POST", "PATCH", "PUT" -> entity + ".UPDATE";
            case "DELETE" -> entity + "." + deleteAction;
            default -> fallback;
        };
        return new ResolvedTarget(entity, id, action);
    }

    private String seg(String uri, int index) {
        if (uri == null) return null;
        String[] parts = uri.split("/");
        return index < parts.length ? parts[index] : null;
    }

    private String seg(String[] segs, int index) {
        return segs != null && index < segs.length ? segs[index] : null;
    }

    private String upper(String value) {
        return value == null ? "" : value.toUpperCase().replace('-', '_');
    }

    private UUID parseUuid(String value) {
        try {
            return value == null ? null : UUID.fromString(value);
        } catch (Exception e) {
            return null;
        }
    }
}
