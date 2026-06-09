package com.sumicare.print;

import com.sumicare.booking.domain.Booking;
import com.sumicare.booking.repository.BookingRepository;
import com.sumicare.cashier.domain.Order;
import com.sumicare.cashier.domain.OrderItem;
import com.sumicare.cashier.domain.OrderItemAttendee;
import com.sumicare.cashier.domain.Package;
import com.sumicare.cashier.repository.OrderItemAttendeeRepository;
import com.sumicare.cashier.repository.OrderItemRepository;
import com.sumicare.cashier.repository.OrderRepository;
import com.sumicare.cashier.repository.PackageRepository;
import com.sumicare.cashier.service.PackageService;
import com.sumicare.common.util.BaseUrlResolver;
import com.sumicare.common.util.QrCodeUtil;
import com.sumicare.organization.domain.Organization;
import com.sumicare.organization.repository.OrganizationRepository;
import com.sumicare.pos.repository.PosTransactionRepository;
import com.sumicare.service_catalogue.domain.Service;
import com.sumicare.service_catalogue.repository.ServiceRepository;
import com.sumicare.user.repository.UserRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class ReceiptPdfService {

    private static final ZoneId MANILA = ZoneId.of("Asia/Manila");
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("MM/dd/yyyy h:mm a");
    private static final DateTimeFormatter SCHEDULE = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy h:mm a");

    private final PdfRenderer pdfRenderer;
    private final OrderRepository orderRepository;
    private final BookingRepository bookingRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderItemAttendeeRepository attendeeRepository;
    private final PackageRepository packageRepository;
    private final ServiceRepository serviceRepository;
    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;
    private final PosTransactionRepository transactionRepository;
    private final BaseUrlResolver baseUrlResolver;
    private final PackageService packageService;

    public ReceiptPdfService(PdfRenderer pdfRenderer,
                             OrderRepository orderRepository,
                             BookingRepository bookingRepository,
                             OrderItemRepository orderItemRepository,
                             OrderItemAttendeeRepository attendeeRepository,
                             PackageRepository packageRepository,
                             ServiceRepository serviceRepository,
                             OrganizationRepository organizationRepository,
                             UserRepository userRepository,
                             PosTransactionRepository transactionRepository,
                             BaseUrlResolver baseUrlResolver,
                             PackageService packageService) {
        this.pdfRenderer = pdfRenderer;
        this.orderRepository = orderRepository;
        this.bookingRepository = bookingRepository;
        this.orderItemRepository = orderItemRepository;
        this.attendeeRepository = attendeeRepository;
        this.packageRepository = packageRepository;
        this.serviceRepository = serviceRepository;
        this.organizationRepository = organizationRepository;
        this.userRepository = userRepository;
        this.transactionRepository = transactionRepository;
        this.baseUrlResolver = baseUrlResolver;
        this.packageService = packageService;
    }

    public byte[] renderReceipt(UUID orderId) {
        Order order = orderRepository.findById(orderId).orElseThrow();
        Organization org = organizationRepository.findById(order.getOrganizationId()).orElse(null);
        Booking booking = order.getBookingId() == null ? null
                : bookingRepository.findById(order.getBookingId()).orElse(null);
        String scheduleStr = booking != null && booking.getScheduledAt() != null
                ? booking.getScheduledAt().atZoneSameInstant(MANILA).format(SCHEDULE)
                : "";
        String cashierName = order.getCashierUserId() == null ? ""
                : userRepository.findById(order.getCashierUserId())
                    .map(u -> u.getDisplayName() != null && !u.getDisplayName().isBlank()
                            ? u.getDisplayName() : u.getUsername())
                    .orElse("");
        UUID lastEditorId = order.getLastEditedByUserId() != null ? order.getLastEditedByUserId() : order.getCashierUserId();
        String transactedByName = lastEditorId == null ? ""
                : userRepository.findById(lastEditorId)
                    .map(u -> u.getDisplayName() != null && !u.getDisplayName().isBlank()
                            ? u.getDisplayName() : u.getUsername())
                    .orElse("");

        List<OrderItem> items = orderItemRepository.findAllByOrderIdOrderByPosition(orderId);
        Map<UUID, List<OrderItemAttendee>> attendeesByItem = new HashMap<>();
        for (OrderItemAttendee a : attendeeRepository.findAllByOrderIdOrderByPosition(orderId)) {
            attendeesByItem.computeIfAbsent(a.getOrderItemId(), k -> new java.util.ArrayList<>()).add(a);
        }
        Map<Long, Package> pkgCache = new HashMap<>();
        Map<Long, Service> svcCache = new HashMap<>();

        StringBuilder lines = new StringBuilder();
        if (items.isEmpty()) {
            lines.append("<tr><td>Service</td><td class='qty'>1</td><td class='amt'>")
                 .append(fmt(order.getSubtotal())).append("</td></tr>");
        }
        for (OrderItem it : items) {
            Package pkg = pkgCache.computeIfAbsent(it.getPackageId(), id -> packageRepository.findById(id).orElse(null));
            String pkgName = pkg != null ? pkg.getName() : "Package";
            lines.append("<tr><td>").append(esc(pkgName)).append("</td><td class='qty'>")
                 .append(it.getQuantity()).append("</td><td class='amt'>")
                 .append(fmt(it.getLineTotal())).append("</td></tr>");
            if (pkg != null) {
                List<String> inclusions = packageService.deriveInclusions(pkg);
                if (!inclusions.isEmpty()) {
                    lines.append("<tr class='sub'><td colspan='3'>&#160;&#160;Includes: ")
                         .append(esc(String.join(" - ", inclusions)))
                         .append("</td></tr>");
                }
            }
            String itemRoomType = it.getRoomType() != null ? it.getRoomType() : "COMMON";
            BigDecimal itemRoomCharge = it.getRoomTypeCharge() != null ? it.getRoomTypeCharge() : BigDecimal.ZERO;
            if ("VIP".equalsIgnoreCase(itemRoomType)) {
                lines.append("<tr class='sub'><td>&#160;&#160;Room (VIP) included</td><td></td><td class='amt'>&#160;</td></tr>");
            } else if (itemRoomCharge.compareTo(BigDecimal.ZERO) > 0) {
                lines.append("<tr class='sub'><td>&#160;&#160;Room (").append(esc(itemRoomType)).append(")</td><td></td><td class='amt'>")
                     .append(fmt(itemRoomCharge)).append("</td></tr>");
            } else {
                lines.append("<tr class='sub'><td>&#160;&#160;Room (").append(esc(itemRoomType)).append(")</td><td></td><td class='amt'>&#160;</td></tr>");
            }
            List<OrderItemAttendee> atts = attendeesByItem.getOrDefault(it.getId(), List.of());
            for (OrderItemAttendee a : atts) {
                String svcName = "";
                if (a.getServiceId() != null) {
                    Service s = svcCache.computeIfAbsent(a.getServiceId(),
                            sid -> serviceRepository.findById(sid).orElse(null));
                    if (s != null) svcName = s.getName();
                }
                String gender = a.getClientGender() != null ? a.getClientGender() : "";
                String locker = a.getLockerNumber() != null ? "Locker " + a.getLockerNumber() : "";
                lines.append("<tr class='sub'><td>&#160;&#160;&#183; ").append(esc(svcName))
                     .append(" (").append(esc(gender)).append(") ").append(esc(locker))
                     .append("</td><td></td><td></td></tr>");
            }
        }

        if (order.getExtensionMinutes() > 0) {
            lines.append("<tr><td>Extension (+").append(order.getExtensionMinutes()).append(" min)</td><td class='qty'>1</td><td class='amt'>")
                 .append(fmt(order.getExtensionAmount())).append("</td></tr>");
        }

        BigDecimal total = order.getTotal() != null ? order.getTotal() : BigDecimal.ZERO;
        BigDecimal orderTax = Objects.requireNonNullElse(order.getTax(), BigDecimal.ZERO);
        String taxRow = orderTax.compareTo(BigDecimal.ZERO) > 0
                ? "<tr><td class=\"lbl\">Tax</td><td class=\"val\">" + fmt(orderTax) + "</td></tr>"
                : "";

        String feedbackUrl = baseUrlResolver.resolve() + "/feedback?or="
                + java.net.URLEncoder.encode(order.getOrNumber() == null ? "" : order.getOrNumber(),
                        java.nio.charset.StandardCharsets.UTF_8);
        String qrDataUri = QrCodeUtil.pngDataUri(feedbackUrl, 160);
        String now = OffsetDateTime.now().atZoneSameInstant(MANILA).format(STAMP);
        String html = """
            <!DOCTYPE html>
            <html><head><meta charset="UTF-8"/>
            <style>
              @page { size: 80mm 297mm; margin: 4mm; }
              body { font-family: 'Courier New', monospace; font-size: 8pt; color: #000; }
              .center { text-align: center; }
              .small { font-size: 7pt; }
              .bold { font-weight: bold; }
              hr { border: none; border-top: 1px dashed #000; margin: 4px 0; }
              table { width: 100%%; border-collapse: collapse; }
              td { padding: 1px 0; vertical-align: top; }
              td.qty { width: 16mm; text-align: right; }
              td.amt { width: 22mm; text-align: right; }
              .sub td { font-size: 7pt; padding-left: 4mm; }
              .totals td { padding: 1px 0; }
              .totals .lbl { text-align: left; }
              .totals .val { text-align: right; }
            </style></head><body>
              <div class="center bold">%s</div>
              <div class="center small">Owned and Operated by %s</div>
              <div class="center small">SumiCare POS — Powered by SumiCare</div>
              <hr/>
              <table class="small">
                <tr><td>Cashier:</td><td>%s</td></tr>
                <tr><td>Date / Time:</td><td>%s</td></tr>
                <tr><td>Schedule:</td><td>%s</td></tr>
                <tr><td>OR #:</td><td>%s</td></tr>
                <tr><td>Customer:</td><td>%s</td></tr>
                <tr><td>Transacted by:</td><td>%s</td></tr>
                <tr><td>Payment method:</td><td>%s</td></tr>
                <tr><td>Covers:</td><td>%s</td></tr>
              </table>
              <hr/>
              <table>
                %s
              </table>
              <hr/>
              <div class="center small bold">RECEIPT</div>
              <table class="totals small">
                <tr><td class="lbl">Subtotal</td><td class="val">%s</td></tr>
                <tr><td class="lbl">Discount</td><td class="val">%s</td></tr>
                %s
                <tr><td class="lbl bold">Bill Amount</td><td class="val bold">%s</td></tr>
                <tr><td class="lbl">Amount Paid</td><td class="val">%s</td></tr>
              </table>
              <hr/>
              <div class="center small">Share your feedback</div>
              <div class="center"><img src="%s" width="80" height="80"/></div>
              <div class="center small">Powered by SumiCare</div>
            </body></html>
            """.formatted(
                esc(org == null ? "La Sema Spa" : (org.getDisplayName() != null ? org.getDisplayName() : org.getSlug())),
                esc(org == null ? "" : (org.getSlug() == null ? "" : org.getSlug())),
                esc(cashierName),
                now,
                esc(scheduleStr),
                esc(order.getOrNumber() == null ? "" : order.getOrNumber()),
                esc(order.getTransactorName() == null ? "" : order.getTransactorName()),
                esc(transactedByName),
                esc(describePaymentMethods(orderId)),
                items.size() == 0 ? "1" : Integer.toString(items.size()),
                lines.toString(),
                fmt(order.getSubtotal()),
                fmt(order.getDiscount()),
                taxRow,
                fmt(total),
                fmt(order.getAmountPaid()),
                qrDataUri
        );

        return pdfRenderer.renderHtml(html);
    }

    private String fmt(BigDecimal v) {
        if (v == null) return "0.00";
        return v.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private String describePaymentMethods(UUID orderId) {
        var txs = transactionRepository.findAllByOrderId(orderId);
        java.util.LinkedHashSet<String> labels = new java.util.LinkedHashSet<>();
        for (var tx : txs) {
            String m = tx.getPaymentMethod();
            if (m == null || m.isBlank()) continue;
            labels.add(humanise(m));
        }
        return String.join(", ", labels);
    }

    private String humanise(String m) {
        return switch (m == null ? "" : m.toUpperCase()) {
            case "CASH" -> "Cash";
            case "GCASH" -> "GCash";
            case "CREDIT" -> "Credit card";
            case "DEBIT" -> "Debit card";
            case "CARD" -> "Card";
            default -> m;
        };
    }

    private String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
