package com.instakill.media.domain;

import org.springframework.web.multipart.MultipartFile;

/**
 * Port interface for media storage operations (Hexagonal Architecture).
 * Implementations may target local disk, AWS S3, Google Cloud Storage, MinIO, etc.
 */
public interface MediaStorage {

    StoredMedia store(MultipartFile file);
}
