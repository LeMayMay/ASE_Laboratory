package com.instakill.media.domain;

import java.util.Objects;

/**
 * Value object carrying the result of {@link ImageProcessor#process}.
 *
 * @param data          the processed image bytes (or original bytes if no processing occurred)
 * @param originalFilename the original filename preserved from the upload
 * @param contentType   MIME type of the resulting image (e.g. "image/jpeg")
 */
public record ProcessedImage(byte[] data, String originalFilename, String contentType) {
    public ProcessedImage {
        Objects.requireNonNull(data, "data must not be null");
        Objects.requireNonNull(originalFilename, "originalFilename must not be null");
        Objects.requireNonNull(contentType, "contentType must not be null");
    }
}
