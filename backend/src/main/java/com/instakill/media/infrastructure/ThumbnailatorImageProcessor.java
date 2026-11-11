package com.instakill.media.infrastructure;

import com.instakill.media.domain.ImageProcessor;
import com.instakill.media.domain.ProcessedImage;
import net.coobird.thumbnailator.Thumbnails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.Set;

/**
 * Image processing adapter backed by the Thumbnailator library.
 *
 * <p>Processing pipeline (applied only to recognised image types):
 * <ol>
 *   <li>Scale to a maximum width of {@value #MAX_WIDTH} px, preserving aspect ratio.</li>
 *   <li>Stamp a semi-transparent watermark text ({@value #WATERMARK_TEXT}) at the bottom-right corner.</li>
 * </ol>
 *
 * <p>Non-image uploads (video, etc.) are returned unchanged so that
 * {@link com.instakill.media.application.MediaService} can store them as-is.
 */
@Component
public class ThumbnailatorImageProcessor implements ImageProcessor {

    private static final Logger log = LoggerFactory.getLogger(ThumbnailatorImageProcessor.class);

    /** Maximum output width in pixels. Height is scaled proportionally. */
    public static final int MAX_WIDTH = 300;

    /** Watermark text stamped onto every processed image. */
    public static final String WATERMARK_TEXT = "Instakill Demo";

    /** MIME types treated as images eligible for processing. */
    private static final Set<String> IMAGE_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/jpg", "image/png", "image/gif", "image/webp"
    );

    /** File extensions treated as images when MIME type is absent or generic. */
    private static final Set<String> IMAGE_EXTENSIONS = Set.of(
            "jpg", "jpeg", "png", "gif", "webp"
    );

    @Override
    public ProcessedImage process(MultipartFile file) {
        try {
            return process(
                    file.getBytes(),
                    file.getOriginalFilename(),
                    file.getContentType()
            );
        } catch (IOException e) {
            throw new RuntimeException("Failed to read uploaded file bytes", e);
        }
    }

    @Override
    public ProcessedImage process(byte[] inputBytes, String originalFilename, String contentType) {
        String safeFilename = (originalFilename != null && !originalFilename.isBlank()) ? originalFilename : "upload";
        String safeContentType = (contentType != null && !contentType.isBlank()) ? contentType : "application/octet-stream";

        if (!isEligibleImage(safeContentType, safeFilename)) {
            return new ProcessedImage(inputBytes, safeFilename, safeContentType);
        }

        try {
            BufferedImage originalImage = ImageIO.read(new ByteArrayInputStream(inputBytes));
            if (originalImage == null) {
                log.warn("ImageIO could not decode '{}'; storing raw bytes.", safeFilename);
                return new ProcessedImage(inputBytes, safeFilename, safeContentType);
            }

            // ── Step 1: Scale to max width 300px while preserving aspect ratio ────────
            int originalWidth = originalImage.getWidth();
            BufferedImage scaled;
            if (originalWidth > MAX_WIDTH) {
                scaled = Thumbnails.of(originalImage)
                        .width(MAX_WIDTH)
                        .keepAspectRatio(true)
                        .asBufferedImage();
            } else {
                scaled = originalImage;
            }

            // ── Step 2: Watermark at bottom-right corner ──────────────────────────────
            String outputFormat = resolveOutputFormat(safeContentType, safeFilename);
            boolean supportsAlpha = "png".equals(outputFormat) || "gif".equals(outputFormat);
            BufferedImage watermarked = applyWatermark(scaled, WATERMARK_TEXT, supportsAlpha);

            // ── Step 3: Encode back to bytes ──────────────────────────────────────────
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            boolean written = ImageIO.write(watermarked, outputFormat, out);
            if (!written || out.size() == 0) {
                log.warn("ImageIO writer unavailable for format '{}'; falling back to PNG", outputFormat);
                out.reset();
                ImageIO.write(watermarked, "png", out);
            }

            log.debug("Processed image '{}': {}x{} -> {}x{} with watermark",
                    safeFilename,
                    originalWidth, originalImage.getHeight(),
                    watermarked.getWidth(), watermarked.getHeight());

            return new ProcessedImage(
                    out.toByteArray(),
                    safeFilename,
                    safeContentType
            );
        } catch (IOException e) {
            throw new RuntimeException("Failed to process image '" + safeFilename + "'", e);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────────

    private boolean isEligibleImage(String contentType, String filename) {
        if (contentType != null && IMAGE_CONTENT_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
            return true;
        }
        String ext = StringUtils.getFilenameExtension(filename);
        return ext != null && IMAGE_EXTENSIONS.contains(ext.toLowerCase(Locale.ROOT).trim());
    }

    /**
     * Draws semi-transparent watermark text at the bottom-right corner.
     * Uses {@code TYPE_INT_ARGB} for alpha-capable formats (PNG/GIF) and {@code TYPE_INT_RGB}
     * for opaque formats (JPEG) so {@link ImageIO#write} always succeeds.
     */
    private BufferedImage applyWatermark(BufferedImage source, String text, boolean supportsAlpha) {
        int imageType = supportsAlpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
        BufferedImage result = new BufferedImage(source.getWidth(), source.getHeight(), imageType);

        Graphics2D g2d = result.createGraphics();
        try {
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            if (!supportsAlpha) {
                g2d.setColor(Color.WHITE);
                g2d.fillRect(0, 0, source.getWidth(), source.getHeight());
            }
            g2d.drawImage(source, 0, 0, null);

            int fontSize = Math.max(10, source.getWidth() / 15);
            Font font = new Font(Font.SANS_SERIF, Font.BOLD, fontSize);
            g2d.setFont(font);
            FontMetrics fm = g2d.getFontMetrics();

            int textWidth = fm.stringWidth(text);
            int padding = Math.max(4, fontSize / 3);

            int x = Math.max(2, source.getWidth() - textWidth - padding);
            int y = Math.max(fm.getAscent(), source.getHeight() - padding);

            // Shadow for legibility on light backgrounds
            g2d.setColor(new Color(0, 0, 0, 120));
            g2d.drawString(text, x + 1, y + 1);

            // Main watermark text (semi-transparent white)
            g2d.setColor(new Color(255, 255, 255, 190));
            g2d.drawString(text, x, y);
        } finally {
            g2d.dispose();
        }
        return result;
    }

    /**
     * Maps a MIME content type or filename extension to the format name expected by {@link ImageIO}.
     */
    private String resolveOutputFormat(String contentType, String filename) {
        if (contentType != null) {
            switch (contentType.toLowerCase(Locale.ROOT)) {
                case "image/png", "image/webp" -> { return "png"; }
                case "image/gif" -> { return "gif"; }
                case "image/jpeg", "image/jpg" -> { return "jpg"; }
            }
        }
        String ext = StringUtils.getFilenameExtension(filename);
        if (ext != null) {
            return switch (ext.toLowerCase(Locale.ROOT).trim()) {
                case "png", "webp" -> "png";
                case "gif" -> "gif";
                default -> "jpg";
            };
        }
        return "jpg";
    }
}
