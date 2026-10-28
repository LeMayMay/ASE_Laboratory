package com.instakill.media.domain;

import com.instakill.common.error.BadRequestException;
import com.instakill.media.infrastructure.MediaProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.Locale;

/**
 * Encapsulates validation policies for incoming media uploads.
 */
@Component
public class MediaValidator {

    private final MediaProperties properties;

    public MediaValidator(MediaProperties properties) {
        this.properties = properties;
    }

    public String validateAndExtractExtension(MultipartFile file) {
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

        if (extension.isEmpty() || !properties.allowedExtensions().contains(extension)) {
            throw new BadRequestException("Unsupported or missing file extension. Allowed extensions: " + properties.allowedExtensions());
        }

        return extension;
    }
}
