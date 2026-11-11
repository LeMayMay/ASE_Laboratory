package com.instakill.media.infrastructure;

import com.instakill.media.application.ImageProcessingService;
import com.instakill.media.domain.ProcessedImage;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class ThumbnailatorImageProcessorTest {

    private final ThumbnailatorImageProcessor processor = new ThumbnailatorImageProcessor();
    private final ImageProcessingService processingService = new ImageProcessingService(processor);

    @Test
    void scalesWideImageToMax300WidthPreservingAspectRatioAndAddsWatermark() throws IOException {
        // Create a 600x400 solid blue PNG image (3:2 aspect ratio)
        byte[] rawPng = createSolidImageBytes(600, 400, Color.BLUE, "png");
        MockMultipartFile upload = new MockMultipartFile("file", "wide.png", "image/png", rawPng);

        ProcessedImage processed = processingService.process(upload);

        BufferedImage output = ImageIO.read(new ByteArrayInputStream(processed.data()));
        assertThat(output).isNotNull();
        assertThat(output.getWidth()).isEqualTo(300);
        assertThat(output.getHeight()).isEqualTo(200); // 600:400 -> 300:200 preserves aspect ratio

        // Verify watermark modified pixels in the bottom-right corner while top-left remains untouched blue
        int topLeftRgb = output.getRGB(5, 5) & 0x00FFFFFF;
        assertThat(topLeftRgb).isEqualTo(Color.BLUE.getRGB() & 0x00FFFFFF);
        assertThat(hasNonUniformPixelsInBottomRight(output, Color.BLUE)).isTrue();
    }

    @Test
    void doesNotUpscaleSmallerImageButStillAppliesWatermark() throws IOException {
        // Create a 200x150 solid dark-green JPEG image
        byte[] rawJpg = createSolidImageBytes(200, 150, new Color(0, 100, 0), "jpg");

        ProcessedImage processed = processingService.process(rawJpg, "small.jpg", "image/jpeg");

        BufferedImage output = ImageIO.read(new ByteArrayInputStream(processed.data()));
        assertThat(output).isNotNull();
        assertThat(output.getWidth()).isEqualTo(200);
        assertThat(output.getHeight()).isEqualTo(150);
        assertThat(processed.data()).isNotEmpty();
    }

    @Test
    void passesNonImageFilesThroughUnchanged() {
        byte[] videoBytes = new byte[]{0x00, 0x00, 0x00, 0x20, 0x66, 0x74, 0x79, 0x70};
        MockMultipartFile videoFile = new MockMultipartFile("file", "clip.mp4", "video/mp4", videoBytes);

        ProcessedImage processed = processingService.process(videoFile);

        assertThat(processed.data()).isEqualTo(videoBytes);
        assertThat(processed.originalFilename()).isEqualTo("clip.mp4");
        assertThat(processed.contentType()).isEqualTo("video/mp4");
    }

    private byte[] createSolidImageBytes(int width, int height, Color color, String format) throws IOException {
        int type = "png".equalsIgnoreCase(format) ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
        BufferedImage img = new BufferedImage(width, height, type);
        Graphics2D g = img.createGraphics();
        try {
            g.setColor(color);
            g.fillRect(0, 0, width, height);
        } finally {
            g.dispose();
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, format, out);
        return out.toByteArray();
    }

    private boolean hasNonUniformPixelsInBottomRight(BufferedImage image, Color background) {
        int bgRgb = background.getRGB() & 0x00FFFFFF;
        int startX = image.getWidth() / 2;
        int startY = (image.getHeight() * 2) / 3;
        for (int y = startY; y < image.getHeight(); y++) {
            for (int x = startX; x < image.getWidth(); x++) {
                if ((image.getRGB(x, y) & 0x00FFFFFF) != bgRgb) {
                    return true;
                }
            }
        }
        return false;
    }
}
