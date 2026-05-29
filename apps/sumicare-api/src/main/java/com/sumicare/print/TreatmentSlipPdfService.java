package com.sumicare.print;

import com.sumicare.common.config.AppProperties;
import com.sumicare.common.util.QrCodeUtil;
import com.sumicare.transaction.domain.TreatmentSlip;
import com.sumicare.transaction.repository.TreatmentSlipRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Component
public class TreatmentSlipPdfService {

    private static final ZoneId MANILA = ZoneId.of("Asia/Manila");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("MMM d, yyyy");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("h:mm a");

    private final PdfRenderer pdfRenderer;
    private final TreatmentSlipRepository slipRepository;
    private final AppProperties appProperties;

    public TreatmentSlipPdfService(PdfRenderer pdfRenderer, TreatmentSlipRepository slipRepository,
                                   AppProperties appProperties) {
        this.pdfRenderer = pdfRenderer;
        this.slipRepository = slipRepository;
        this.appProperties = appProperties;
    }

    public byte[] renderSlip(UUID slipId) {
        TreatmentSlip slip = slipRepository.findById(slipId).orElseThrow();
        boolean vip = slip.isVip();

        String customerName = slip.getClientNickname() == null ? "" : slip.getClientNickname();
        String therapist = slip.getPrimaryTherapistNickname() == null ? "" : slip.getPrimaryTherapistNickname();
        if (slip.getSecondaryTherapistNickname() != null && !slip.getSecondaryTherapistNickname().isBlank()) {
            therapist += " / " + slip.getSecondaryTherapistNickname();
        }
        String lockerDisplay = rawLocker(slip.getLockerNumber());
        String othersDisplay = othersWithExtension(slip.getExtensionMinutes(), slip.getOthersAddOn());

        StringBuilder sb = new StringBuilder();
        sb.append(CSS_PREFIX);
        sb.append("<div class=\"slip\">");

        if (vip) {
            sb.append("<div class=\"tag\">VIP TREATMENT SLIP</div>");
            sb.append("<div class=\"vip-top\">");
            sb.append(vipRow("JACUZZI TIME",
                    slip.getJacuzziMinutes() != null ? slip.getJacuzziMinutes() + " min" : ""));
            sb.append(vipRow("MASSAGE TIME",
                    slip.getMassageMinutes() != null ? slip.getMassageMinutes() + " min" : ""));
            sb.append(vipRow("ROOM", slip.getRoomNumber() == null ? "" : slip.getRoomNumber()));
            sb.append(wineRow(slip.getWineIncluded()));
            sb.append("</div>");
        } else {
            String treatTime = slip.getTreatmentMinutes() != null ? slip.getTreatmentMinutes() + " min" : "";
            String pax = Integer.toString(slip.getPax() == null ? 1 : slip.getPax());
            sb.append("<div class=\"row row-2\">")
              .append(cell("Treatment Time", treatTime))
              .append(cell("Pax", pax))
              .append("</div>");
        }

        sb.append("<div class=\"brand\">LASEMA</div>");

        sb.append("<div class=\"row row-2\">")
          .append(cell("Customer Name", customerName))
          .append(cell("Nationality", slip.getNationality() == null ? "" : slip.getNationality()))
          .append("</div>");

        sb.append("<div class=\"row row-2\">")
          .append(cell("TS #", slip.getTsn() == null ? "" : slip.getTsn()))
          .append(cell("Date", slip.getCreatedAt() == null ? "" :
                  slip.getCreatedAt().atZoneSameInstant(MANILA).format(DATE_FMT)))
          .append("</div>");

        if (vip) {
            sb.append("<div class=\"row row-1\">")
              .append(cell("Locker Key #", lockerDisplay))
              .append("</div>");
        } else {
            sb.append("<div class=\"row row-2\">")
              .append(cell("Locker Key #", lockerDisplay))
              .append(cell("Room", slip.getRoomNumber() == null ? "" : slip.getRoomNumber()))
              .append("</div>");
        }

        sb.append("<div class=\"row row-2\">")
          .append(cell("Start Time", slip.getStartTime() == null ? "" :
                  slip.getStartTime().atZoneSameInstant(MANILA).format(TIME_FMT)))
          .append(cell("End Time", slip.getEndTime() == null ? "" :
                  slip.getEndTime().atZoneSameInstant(MANILA).format(TIME_FMT)))
          .append("</div>");

        sb.append("<div class=\"row row-2\">")
          .append(cell("Therapist", therapist))
          .append("<div class=\"cell\"><span class=\"label\">Signature</span><div class=\"value\">&#160;</div></div>")
          .append("</div>");

        sb.append("<div class=\"row row-treat\">")
          .append("<div class=\"cell t1\">").append(cellInner("Treatment",
                  slip.getServiceName() == null ? "" : slip.getServiceName())).append("</div>")
          .append("<div class=\"cell t2\">").append(cellInner("OR #",
                  slip.getOrNumber() == null ? "" : slip.getOrNumber())).append("</div>")
          .append("</div>");

        sb.append("<div class=\"row row-treat\">")
          .append("<div class=\"cell t1\">").append(cellInner("Others / Add On", othersDisplay)).append("</div>")
          .append("<div class=\"cell t2\">").append(cellInner("Add-on OR #",
                  slip.getAddOnOrNumber() == null ? "" : slip.getAddOnOrNumber())).append("</div>")
          .append("</div>");

        sb.append("<div class=\"row row-treat\">")
          .append("<div class=\"cell t1\">").append(cellInner("Remarks",
                  slip.getRemarks() == null ? "" : slip.getRemarks())).append("</div>")
          .append("<div class=\"cell t2\">").append(cellInner("Total", fmt(slip.getTotalAmount()))).append("</div>")
          .append("</div>");

        sb.append("<div class=\"row row-1\">")
          .append(cell("Waiver", slip.isWaiverAccepted() ? "Accepted" : "Pending"))
          .append("</div>");

        sb.append("<div class=\"waiver-title\">WAIVER</div>");
        sb.append("<div class=\"waiver\">")
          .append("I understand that the massage or body scrub received is provided for the basic purpose of relaxation ")
          .append("and relief of MUSCULAR TENSION. If I experience any pain or discomfort during the session, ")
          .append("I will immediately inform the practitioner so that pressure and/or strokes may be adjusted to my level of comfort. ")
          .append("I further understand that the massage/body scrub is not a substitute for medical examination, diagnosis, or treatment, ")
          .append("and that I should see a qualified physician for any physical/mental ailment. ")
          .append("THERE SHALL BE NO LIABILITY ON THE PRACTITIONER'S PART OR THE LASEMA Management. ")
          .append("Any illicit or sexually suggestive remarks or advances will result in immediate termination of the session, ")
          .append("with payment forfeited.")
          .append("</div>");

        sb.append("<div class=\"sig\">Signature over printed name</div>");

        String feedbackUrl = appProperties.app().publicBaseUrl() + "/feedback?slip="
                + java.net.URLEncoder.encode(slip.getTsn() == null ? "" : slip.getTsn(),
                        java.nio.charset.StandardCharsets.UTF_8);
        String qrDataUri = QrCodeUtil.pngDataUri(feedbackUrl, 180);
        sb.append("<div class=\"qr-block\"><div class=\"qr-label\">Scan to share feedback</div>")
          .append("<img src=\"").append(qrDataUri).append("\" width=\"90\" height=\"90\"/></div>");
        sb.append("</div></body></html>");

        return pdfRenderer.renderHtml(sb.toString());
    }

    private static final String CSS_PREFIX = """
            <!DOCTYPE html>
            <html><head><meta charset="UTF-8"/>
            <style>
              @page { size: A5 portrait; margin: 5mm; }
              html, body { margin: 0; padding: 0; height: 200mm; max-height: 200mm; overflow: hidden; }
              body { font-family: 'DejaVu Sans', 'Liberation Sans', Arial, Helvetica, sans-serif; font-size: 7.5pt; color: #000; }
              .slip { width: 100%; height: 198mm; max-height: 198mm; overflow: hidden; border: 1.25px solid #000; page-break-inside: avoid; box-sizing: border-box; }
              .row { display: table; width: 100%; border-bottom: 1px solid #000; }
              .row:last-child { border-bottom: none; }
              .cell { display: table-cell; padding: 1mm 1.75mm; border-right: 1px solid #000; vertical-align: top; line-height: 1.18; }
              .cell:last-child { border-right: none; }
              .row-2 .cell { width: 50%; }
              .row-1 .cell { width: 100%; }
              .row-treat .cell.t1 { width: 66%; }
              .row-treat .cell.t2 { width: 34%; }
              .label { font-size: 6pt; font-weight: 700; text-transform: uppercase; letter-spacing: 0.02em; display: block; }
              .value { font-size: 8pt; font-weight: 600; margin-top: 0.3mm; word-break: break-word; overflow-wrap: anywhere; }
              .row-treat .cell.t1, .row-treat .cell.t2 { overflow: hidden; }
              .cell { vertical-align: middle; }
              .brand { text-align: center; font-weight: 800; font-size: 13pt; letter-spacing: 0.18em; padding: 1.25mm; border-bottom: 1.25px solid #000; }
              .tag { background: #000; color: #fff; text-align: center; font-weight: 800; letter-spacing: 0.2em; font-size: 7pt; padding: 0.75mm 0; }
              .vip-top { border-bottom: 1.25px solid #000; }
              .vip-row { display: flex; justify-content: space-between; align-items: baseline; padding: 0.75mm 1.75mm; border-bottom: 1px solid #000; }
              .vip-row:last-child { border-bottom: none; }
              .vip-lbl { font-size: 6pt; font-weight: 700; text-transform: uppercase; letter-spacing: 0.04em; }
              .vip-val { font-size: 8pt; font-weight: 600; }
              .sep { opacity: 0.5; font-weight: 400; }
              .active-choice { font-weight: 800; text-decoration: underline; text-underline-offset: 1px; }
              .waiver-title { text-align: center; font-weight: 800; letter-spacing: 0.1em; font-size: 7pt; padding-top: 0.5mm; }
              .waiver { padding: 1.25mm 1.75mm; font-size: 5.4pt; line-height: 1.22; text-align: justify; border-top: 1px solid #000; border-bottom: 1px solid #000; }
              .sig { padding: 3mm 1.75mm 0.75mm; text-align: center; font-size: 6pt; letter-spacing: 0.06em; text-transform: uppercase; border-top: 1px solid #000; margin-top: 1.5mm; }
              .qr-block { padding: 1mm; text-align: center; border-top: 1px solid #000; }
              .qr-label { font-size: 5.5pt; letter-spacing: 0.05em; text-transform: uppercase; margin-bottom: 0.5mm; }
              .qr-block img { width: 64px; height: 64px; }
            </style></head><body>
            """;

    private String vipRow(String label, String value) {
        return "<div class=\"vip-row\"><span class=\"vip-lbl\">" + esc(label) +
               "</span><span class=\"vip-val\">" + esc(value) + "</span></div>";
    }

    private String wineRow(Boolean wineIncluded) {
        String yes = Boolean.TRUE.equals(wineIncluded)
                ? "<span class=\"active-choice\">Yes</span>"
                : "<span>Yes</span>";
        String no = Boolean.FALSE.equals(wineIncluded)
                ? "<span class=\"active-choice\">No</span>"
                : "<span>No</span>";
        return "<div class=\"vip-row\"><span class=\"vip-lbl\">WINE</span>" +
               "<span class=\"vip-val\">" + yes + " <span class=\"sep\">/</span> " + no + "</span></div>";
    }

    private String cell(String label, String value) {
        return "<div class=\"cell\">" + cellInner(label, value) + "</div>";
    }

    private String rawLocker(String locker) {
        if (locker == null || locker.isBlank()) {
            return "";
        }
        return locker.trim().replaceFirst("^[MFmf]", "");
    }

    private String othersWithExtension(Integer extensionMinutes, String othersAddOn) {
        String extension = extensionMinutes != null && extensionMinutes > 0
                ? "Extension: +" + extensionMinutes + " min"
                : "Extension: None";
        if (othersAddOn != null && !othersAddOn.isBlank() && !othersAddOn.startsWith("Massage extension")) {
            return extension + " / " + othersAddOn;
        }
        return extension;
    }

    private String cellInner(String label, String value) {
        return "<span class=\"label\">" + esc(label) + "</span><div class=\"value\">" + esc(value) + "</div>";
    }

    private String fmt(BigDecimal v) {
        if (v == null) return "";
        return "₱ " + v.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
