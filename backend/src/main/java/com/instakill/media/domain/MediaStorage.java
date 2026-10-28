package com.instakill.media.domain;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;

/**
 * Port interface for media storage operations (Hexagonal Architecture).
 * Implementations may target local disk, AWS S3, Google Cloud Storage, MinIO, etc.
 */
public interface MediaStorage {

    StoredMedia store(MultipartFile file);

    Resource loadAsResource(String filename);

    Path getRootLocation();
}
