package com.instakill.media.domain;

import org.springframework.web.multipart.MultipartFile;

/**
 * Port interface for image pre-processing before storage.
 * Implementations may resize, watermark, compress, or convert images.
 *
 * <p>Provides both a transport-independent byte-level contract (suitable for
 * standalone execution via message queues or background workers) and a convenience
 * overload for web {@link MultipartFile} uploads.
 *
 * <p>Non-image files (video, etc.) are passed through unchanged.
 */
public interface ImageProcessor {

    /**
     * Process raw bytes and metadata without any web/servlet dependency.
     * Can be invoked by an independent worker or message-queue consumer.
     *
     * @param data             the raw file bytes
     * @param originalFilename the original filename
     * @param contentType      the MIME type of the file
     * @return a {@link ProcessedImage} containing the transformed bytes and metadata
     */
    ProcessedImage process(byte[] data, String originalFilename, String contentType);

    /**
     * Process the given file and return a (possibly transformed) representation.
     * Implementations must return {@code file} unchanged for non-image types.
     *
     * @param file the uploaded multipart file
     * @return a {@link ProcessedImage} containing the transformed bytes and metadata
     */
    ProcessedImage process(MultipartFile file);
}
