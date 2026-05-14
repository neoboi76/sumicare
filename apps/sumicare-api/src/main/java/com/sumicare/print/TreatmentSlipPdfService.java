package com.sumicare.print;

import com.sumicare.transaction.domain.TreatmentSlip;
import com.sumicare.transaction.repository.TreatmentSlipRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Component
public class TreatmentSlipPdfService {

    private static final ZoneId MANILA = ZoneId.of("Asia/Manila");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    private final PdfRenderer pdfRenderer;
    private final TreatmentSlipRepository slipRepository;

    public TreatmentSlipPdfService(PdfRenderer pdfRenderer, TreatmentSlipRepository slipRepository) {
        this.pdfRenderer = pdfRenderer;
        this.slipRepository = slipRepository;
    }

    public byte[] renderSlip(UUID slipId) {
        TreatmentSlip slip = slipRepository.findById(slipId).orElseThrow();
        boolean vip = slip.isVip();
        String topLeftLabel = vip ? "Jacuzzi" : "Treatment Time";
        String topLeftValue = vip
                ? (slip.getJacuzziMinutes() != null ? slip.getJacuzziMinutes() + " min" : "")
                : (slip.getTreatmentMinutes() != null ? slip.getTreatmentMinutes() + " min" : "");
        String topRightLabel = vip ? "Massage" : "Pax";
        String topRightValue = vip
                ? (slip.getMassageMinutes() != null ? slip.getMassageMinutes() + " min" : "")
                : Integer.toString(slip.getPax() == null ? 1 : slip.getPax());

        String tag = vip ? "VIP TREATMENT SLIP" : "";

        String customerName = slip.getClientNickname() == null ? "" : slip.getClientNickname();
        String therapist = slip.getPrimaryTherapistNickname() == null ? "" : slip.getPrimaryTherapistNickname();
        if (slip.getSecondaryTherapistNickname() != null && !slip.getSecondaryTherapistNickname().isBlank()) {
            therapist += " / " + slip.getSecondaryTherapistNickname();
        }

        String html = """
            <!DOCTYPE html>
            <html><head><meta charset="UTF-8"/>
            <style>
              @page { size: A6 portrait; margin: 4mm; }
              body { font-family: Arial, Helvetica, sans-serif; font-size: 8pt; color: #000; }
              .slip { width: 100%%; border: 1.5px solid #000; }
              .row { display: table; width: 100%%; border-bottom: 1px solid #000; }
              .row:last-child { border-bottom: none; }
              .cell { display: table-cell; padding: 1.5mm 2mm; border-right: 1px solid #000; vertical-align: top; }
              .cell:last-child { border-right: none; }
              .row-2 .cell { width: 50%%; }
              .row-1 .cell { width: 100%%; }
              .row-treat .cell.t1 { width: 66%%; }
              .row-treat .cell.t2 { width: 34%%; }
              .label { font-size: 6.5pt; font-weight: 700; text-transform: uppercase; letter-spacing: 0.02em; display: block; }
              .value { font-size: 9pt; font-weight: 600; margin-top: 0.5mm; }
              .brand { text-align: center; font-weight: 800; font-size: 16pt; letter-spacing: 0.18em; padding: 2mm; border-bottom: 1.5px solid #000; }
              .tag { background: #000; color: #fff; text-align: center; font-weight: 800; letter-spacing: 0.2em; font-size: 8pt; padding: 1mm 0; }
              .waiver-title { text-align: center; font-weight: 800; letter-spacing: 0.1em; font-size: 8pt; padding-top: 1mm; }
              .waiver { padding: 2mm; font-size: 6pt; line-height: 1.35; text-align: justify; border-top: 1px solid #000; border-bottom: 1px solid #000; }
              .sig { padding: 8mm 2mm 1mm; text-align: center; font-size: 6.5pt; letter-spacing: 0.06em; text-transform: uppercase; border-top: 1px solid #000; margin-top: 3mm; }
            </style></head><body>
              <div class="slip">
                %s
                <div class="row row-2">
                  <div class="cell"><span class="label">%s</span><div class="value">%s</div></div>
                  <div class="cell"><span class="label">%s</span><div class="value">%s</div></div>
                </div>
                <div class="brand">LASEMA</div>
                <div class="row row-2">
                  <div class="cell"><span class="label">Customer Name</span><div class="value">%s</div></div>
                  <div class="cell"><span class="label">Nationality</span><div class="value">&#160;</div></div>
                </div>
                <div class="row row-2">
                  <div class="cell"><span class="label">TS #</span><div class="value">%s</div></div>
                  <div class="cell"><span class="label">Date</span><div class="value">%s</div></div>
                </div>
                <div class="row row-2">
                  <div class="cell"><span class="label">Locker Key #</span><div class="value">%s</div></div>
                  <div class="cell"><span class="label">Room</span><div class="value">%s</div></div>
                </div>
                <div class="row row-2">
                  <div class="cell"><span class="label">Start Time</span><div class="value">%s</div></div>
                  <div class="cell"><span class="label">End Time</span><div class="value">%s</div></div>
                </div>
                <div class="row row-2">
                  <div class="cell"><span class="label">Therapist</span><div class="value">%s</div></div>
                  <div class="cell"><span class="label">Signature</span><div class="value">&#160;</div></div>
                </div>
                <div class="row row-treat">
                  <div class="cell t1"><span class="label">Treatment</span><div class="value">%s</div></div>
                  <div class="cell t2"><span class="label">OR #</span><div class="value">%s</div></div>
                </div>
                <div class="row row-treat">
                  <div class="cell t1"><span class="label">Others / Add On</span><div class="value">%s</div></div>
                  <div class="cell t2"><span class="label">OR #</span><div class="value">%s</div></div>
                </div>
                <div class="row row-treat">
                  <div class="cell t1"><span class="label">Remarks</span><div class="value">%s</div></div>
                  <div class="cell t2"><span class="label">Total</span><div class="value">%s</div></div>
                </div>
                <div class="waiver-title">WAIVER</div>
                <div class="waiver">
                  I understand that the massage or body scrub received is provided for the basic purpose of relaxation and relief of MUSCULAR TENSION.
                  If I experience any pain or discomfort during the session, I will immediately inform the practitioner so that pressure and/or strokes may be adjusted to my level of comfort.
                  I further understand that the massage/body scrub is not a substitute for medical examination, diagnosis, or treatment, and that I should see a qualified physician for any physical/mental ailment.
                  THERE SHALL BE NO LIABILITY ON THE PRACTITIONER'S PART OR THE LASEMA Management.
                  Any illicit or sexually suggestive remarks or advances will result in immediate termination of the session, with payment forfeited.
                </div>
                <div class="sig">Signature over printed name</div>
              </div>
            </body></html>
            """.formatted(
                vip ? "<div class='tag'>" + tag + "</div>" : "",
                esc(topLeftLabel), esc(topLeftValue),
                esc(topRightLabel), esc(topRightValue),
                esc(customerName),
                esc(slip.getTsn() == null ? "" : slip.getTsn()),
                slip.getCreatedAt() == null ? "" : slip.getCreatedAt().atZoneSameInstant(MANILA).format(DATE_FMT),
                esc(slip.getLockerNumber() == null ? "" : slip.getLockerNumber()),
                esc(slip.getRoomNumber() == null ? "" : slip.getRoomNumber()),
                slip.getStartTime() == null ? "" : slip.getStartTime().atZoneSameInstant(MANILA).format(TIME_FMT),
                slip.getEndTime() == null ? "" : slip.getEndTime().atZoneSameInstant(MANILA).format(TIME_FMT),
                esc(therapist),
                esc(slip.getServiceName() == null ? "" : slip.getServiceName()),
                esc(slip.getOrNumber() == null ? "" : slip.getOrNumber()),
                esc(slip.getOthersAddOn() == null ? "" : slip.getOthersAddOn()),
                esc(slip.getAddOnOrNumber() == null ? "" : slip.getAddOnOrNumber()),
                esc(slip.getRemarks() == null ? "" : slip.getRemarks()),
                fmt(slip.getTotalAmount())
        );

        return pdfRenderer.renderHtml(html);
    }

    private String fmt(BigDecimal v) {
        if (v == null) return "";
        return "₱ " + v.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    @SuppressWarnings("unused")
    private static OffsetDateTime nowManila() { return OffsetDateTime.now(); }
}
