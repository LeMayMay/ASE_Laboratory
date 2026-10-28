package com.instakill.media.infrastructure;

import com.instakill.common.error.BadRequestException;
import com.instakill.common.error.NotFoundException;
import com.instakill.media.domain.MediaStorage;
import com.instakill.media.domain.StoredMedia;
import jakarta.annotation.PostConstruct;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Local filesystem adapter for {@link MediaStorage}.
 * Future adapters (e.g. S3MediaStorage) can replace or coexist with this adapter.
 */
@Component
public class FileSystemMediaStorage implements MediaStorage {

    private final Path rootLocation;

    public FileSystemMediaStorage(MediaProperties properties) {
        Path configuredPath = Paths.get(properties.basePath());
        if (!configuredPath.isAbsolute()) {
            Path backendSubdir = Paths.get("backend", properties.basePath());
            if (Files.exists(backendSubdir) || Files.exists(Paths.get("backend"))) {
                configuredPath = backendSubdir;
            }
        }
        this.rootLocation = configuredPath.toAbsolutePath().normalize();
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

    @Override
    public Resource loadAsResource(String id) {
        if (id == null || id.isBlank()) {
            throw new NotFoundException("Media ID must not be empty");
        }

        // Prevent path traversal
        if (id.contains("..") || id.contains("/") || id.contains("\\")) {
            throw new NotFoundException("Invalid media ID");
        }

        try {
            // 1. Try exact match (e.g. filename has extension: "sample.mp4")
            Path file = this.rootLocation.resolve(id).normalize();
            if (Files.exists(file) && Files.isReadable(file)) {
                Resource resource = new UrlResource(file.toUri());
                if (resource.exists() && resource.isReadable()) {
                    return resource;
                }
            }

            // 2. If no exact match and ID has no dot, search for ID with any extension (e.g. "uuid" -> "uuid.mp4")
            if (!id.contains(".")) {
                try (Stream<Path> stream = Files.list(this.rootLocation)) {
                    var match = stream
                            .filter(Files::isRegularFile)
                            .filter(p -> {
                                String name = p.getFileName().toString();
                                return name.startsWith(id + ".") || name.equals(id);
                            })
                            .findFirst();

                    if (match.isPresent()) {
                        Resource resource = new UrlResource(match.get().toUri());
                        if (resource.exists() && resource.isReadable()) {
                            return resource;
                        }
                    }
                }
            }

            throw new NotFoundException("Media not found: " + id);
        } catch (MalformedURLException e) {
            throw new NotFoundException("Media not found: " + id);
        } catch (IOException e) {
            throw new RuntimeException("Error reading media file: " + id, e);
        }
    }

    @Override
    public Path getRootLocation() {
        return rootLocation;
    }
}
