package com.instakill.media.application;

import com.instakill.media.domain.ImageProcessor;
import com.instakill.media.domain.MediaStorage;
import com.instakill.media.domain.MediaValidator;
import com.instakill.media.domain.ProcessedImage;
import com.instakill.media.domain.StoredMedia;
import com.instakill.media.infrastructure.ProcessedMultipartFile;
import com.instakill.media.infrastructure.ThumbnailatorImageProcessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Arrays;

/**
 * Application service orchestrating media validation, image processing, storage, and retrieval operations.
 */
@Service
public class MediaService {

    private final MediaStorage mediaStorage;
    private final MediaValidator mediaValidator;
    private final ImageProcessor imageProcessor;

    @Autowired
    public MediaService(MediaStorage mediaStorage, MediaValidator mediaValidator, ImageProcessor imageProcessor) {
        this.mediaStorage = mediaStorage;
        this.mediaValidator = mediaValidator;
        this.imageProcessor = imageProcessor;
    }

    public MediaService(MediaStorage mediaStorage, MediaValidator mediaValidator) {
        this(mediaStorage, mediaValidator, new ThumbnailatorImageProcessor());
    }

    public StoredMedia upload(MultipartFile file) {
        mediaValidator.validateAndExtractExtension(file);
        MultipartFile fileToStore = file;
        if (imageProcessor != null) {
            ProcessedImage processed = imageProcessor.process(file);
            try {
                if (processed != null && !Arrays.equals(processed.data(), file.getBytes())) {
                    fileToStore = new ProcessedMultipartFile(file.getName(), processed);
                }
            } catch (IOException e) {
                fileToStore = new ProcessedMultipartFile(file.getName(), processed);
            }
        }
        return mediaStorage.store(fileToStore);
    }

    public Resource loadAsResource(String id) {
        return mediaStorage.loadAsResource(id);
    }
}
