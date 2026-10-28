package com.instakill.media.infrastructure;

import com.instakill.common.error.BadRequestException;
import com.instakill.media.domain.MediaStorage;
import com.instakill.media.domain.StoredMedia;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * Local filesystem adapter for {@link MediaStorage}.
 * Future adapters (e.g. S3MediaStorage) can replace or coexist with this adapter.
 */
@Component
public class FileSystemMediaStorage implements MediaStorage {

    private final Path rootLocation;

    public FileSystemMediaStorage(MediaProperties properties) {
        this.rootLocation = Paths.get(properties.basePath()).toAbsolutePath().normalize();
    }

    @PostConstruct
    public void init() {
        try {
            Files.createDirectories(this.rootLocation);
        } catch (IOException e) {
            throw new IllegalStateException("Could not create media directory: " + this.rootLocation, e);
        }
    }

    @Override
    public StoredMedia store(MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        String extension = StringUtils.getFilenameExtension(originalFilename);
        String safeExtension = (extension != null && !extension.isBlank()) ? "." + extension.toLowerCase().trim() : "";
        String filename = UUID.randomUUID() + safeExtension;

        Path targetLocation = this.rootLocation.resolve(filename).normalize();

        // Path traversal safeguard
        if (!targetLocation.startsWith(this.rootLocation)) {
            throw new BadRequestException("Invalid file path");
        }

        try (InputStream inputStream = file.getInputStream()) {
            Files.copy(inputStream, targetLocation, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException("Failed to store media file", e);
        }

        return new StoredMedia(filename, "/media/" + filename);
    }

    public Path getRootLocation() {
        return rootLocation;
    }
}
