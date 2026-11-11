package com.instakill.media.application;

import com.instakill.media.domain.ImageProcessor;
import com.instakill.media.domain.ProcessedImage;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Standalone application service encapsulating the image processing pipeline
 * (scaling to max 300px width while preserving aspect ratio and watermarking at the bottom-right corner).
 *
 * <p>Structured as an independent service with transport-agnostic byte-level entry points
 * so it can later be extracted into an isolated worker or invoked asynchronously via a message queue
 * (e.g. RabbitMQ, Kafka) without coupling to the HTTP layer or storage adapter.
 */
@Service
public class ImageProcessingService {

    private final ImageProcessor imageProcessor;

    public ImageProcessingService(ImageProcessor imageProcessor) {
        this.imageProcessor = imageProcessor;
    }

    public ProcessedImage process(byte[] data, String originalFilename, String contentType) {
        return imageProcessor.process(data, originalFilename, contentType);
    }

    public ProcessedImage process(MultipartFile file) {
        return imageProcessor.process(file);
    }
}
