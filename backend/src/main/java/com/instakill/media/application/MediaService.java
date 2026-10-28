package com.instakill.media.application;

import com.instakill.media.domain.MediaStorage;
import com.instakill.media.domain.MediaValidator;
import com.instakill.media.domain.StoredMedia;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Application service orchestrating media validation, storage, and retrieval operations.
 */
@Service
public class MediaService {

    private final MediaStorage mediaStorage;
    private final MediaValidator mediaValidator;

    public MediaService(MediaStorage mediaStorage, MediaValidator mediaValidator) {
        this.mediaStorage = mediaStorage;
        this.mediaValidator = mediaValidator;
    }

    public StoredMedia upload(MultipartFile file) {
        mediaValidator.validateAndExtractExtension(file);
        return mediaStorage.store(file);
    }

    public Resource loadAsResource(String id) {
        return mediaStorage.loadAsResource(id);
    }
}
