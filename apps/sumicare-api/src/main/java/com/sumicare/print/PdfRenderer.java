/*
 * Developed by the following authors:
 *     Lance Gabriel C. De La Paz (lgcdelapaz@mymail.mapua.edu.ph)
 *     Franz C. Pereira (fcpereira@mymail.mapua.edu.ph)
 *     Dino Alfred T. Timbol (dattimbol@mymail.mapua.edu.ph)
 */

package com.sumicare.print;

import com.openhtmltopdf.extend.FSSupplier;
import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Component
public class PdfRenderer {

    private static final String FONT_FAMILY = "DejaVu Sans";
    private static final String REGULAR = "/fonts/DejaVuSans.ttf";
    private static final String BOLD = "/fonts/DejaVuSans-Bold.ttf";

    public byte[] renderHtml(String html) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.useFont(streamSupplier(REGULAR), FONT_FAMILY, 400, BaseRendererBuilder.FontStyle.NORMAL, true);
            builder.useFont(streamSupplier(BOLD), FONT_FAMILY, 700, BaseRendererBuilder.FontStyle.NORMAL, true);
            builder.withHtmlContent(html, null);
            builder.toStream(out);
            builder.run();
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to render PDF: " + e.getMessage(), e);
        }
    }

    public byte[] renderHtml(byte[] htmlBytes) {
        return renderHtml(new String(htmlBytes, StandardCharsets.UTF_8));
    }

    private FSSupplier<InputStream> streamSupplier(String resourcePath) {
        return () -> {
            InputStream stream = PdfRenderer.class.getResourceAsStream(resourcePath);
            if (stream == null) {
                throw new IllegalStateException("Missing bundled font resource: " + resourcePath);
            }
            return stream;
        };
    }
}
