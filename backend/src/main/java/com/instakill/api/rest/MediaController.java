package com.instakill.api.rest;

import com.instakill.common.error.BadRequestException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/media")
public class MediaController {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "jpg", "jpeg", "png", "gif", "webp",
            "mp4", "mov", "webm", "mkv"
    );

    private final Path mediaPath;

    public MediaController(@Value("${media.base-path:media}") String mediaBasePath) {
        this.mediaPath = Paths.get(mediaBasePath).toAbsolutePath().normalize();
        initDirectory();
    }

    private void initDirectory() {
        try {
            Files.createDirectories(this.mediaPath);
        } catch (IOException e) {
            throw new IllegalStateException("Could not create media directory: " + this.mediaPath, e);
        }
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public MediaResponse uploadMedia(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("File must not be empty");
        }

        String originalFilename = file.getOriginalFilename();
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = StringUtils.getFilenameExtension(originalFilename);
            if (extension != null) {
                extension = extension.toLowerCase(Locale.ROOT).trim();
            } else {
                extension = "";
            }
        }

        if (extension.isEmpty() || !ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BadRequestException("Unsupported or missing file extension. Allowed extensions: " + ALLOWED_EXTENSIONS);
        }

        String filename = UUID.randomUUID() + "." + extension;
        Path targetLocation = this.mediaPath.resolve(filename).normalize();

        // Extra safeguard: prevent path traversal
        if (!targetLocation.startsWith(this.mediaPath)) {
            throw new BadRequestException("Invalid file path");
        }

        try (InputStream inputStream = file.getInputStream()) {
            Files.copy(inputStream, targetLocation, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException("Failed to store media file", e);
        }

        return new MediaResponse("/media/" + filename);
    }
}
