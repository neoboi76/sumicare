/*
 * Developed by the following authors:
 *     Lance Gabriel C. De La Paz (lgcdelapaz@mymail.mapua.edu.ph)
 *     Franz C. Pereira (fcpereira@mymail.mapua.edu.ph)
 *     Dino Alfred T. Timbol (dattimbol@mymail.mapua.edu.ph)
 */

package com.sumicare.common.util;

import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.URLConnection;
import java.util.Base64;

@Component
public class LogoResolver {

    private static final int MAX_HEIGHT = 100;

    public String dataUriOrNull(String logoUrl) {
        if (logoUrl == null || logoUrl.isBlank()) {
            return null;
        }
        if (logoUrl.startsWith("data:")) {
            return logoUrl;
        }
        URI uri;
        try {
            uri = URI.create(logoUrl.trim());
        } catch (Exception e) {
            return null;
        }
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            return null;
        }
        try {
            URLConnection connection = uri.toURL().openConnection();
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            try (InputStream in = connection.getInputStream()) {
                BufferedImage original = ImageIO.read(in);
                if (original == null) {
                    return null;
                }
                BufferedImage resized = resizeToMaxHeight(original, MAX_HEIGHT);
                String format = "png";
                String mime = "image/png";
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                ImageIO.write(resized, format, out);
                byte[] bytes = out.toByteArray();
                if (bytes.length == 0) {
                    return null;
                }
                return "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(bytes);
            }
        } catch (Exception e) {
            return null;
        }
    }

    private BufferedImage resizeToMaxHeight(BufferedImage image, int maxHeight) {
        int h = image.getHeight();
        int w = image.getWidth();
        if (h <= maxHeight) {
            return image;
        }
        double ratio = (double) maxHeight / h;
        int newW = (int) (w * ratio);
        int newH = maxHeight;
        BufferedImage resized = new BufferedImage(newW, newH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = resized.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(image, 0, 0, newW, newH, null);
        g.dispose();
        return resized;
    }
}
