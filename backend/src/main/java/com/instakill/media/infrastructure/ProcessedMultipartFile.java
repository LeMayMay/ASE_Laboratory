package com.instakill.media.infrastructure;

import com.instakill.media.domain.ProcessedImage;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Adapter that wraps a {@link ProcessedImage} value object as a {@link MultipartFile},
 * allowing the processed bytes to flow into {@link com.instakill.media.domain.MediaStorage}
 * without coupling the storage port to the image processing domain.
 */
public class ProcessedMultipartFile implements MultipartFile {

    private final ProcessedImage processed;
    private final String paramName;

    public ProcessedMultipartFile(String paramName, ProcessedImage processed) {
        this.paramName = paramName;
        this.processed = processed;
    }

    @Override public String getName()             { return paramName; }
    @Override public String getOriginalFilename() { return processed.originalFilename(); }
    @Override public String getContentType()      { return processed.contentType(); }
    @Override public boolean isEmpty()            { return processed.data().length == 0; }
    @Override public long getSize()               { return processed.data().length; }
    @Override public byte[] getBytes()            { return processed.data(); }
    @Override public InputStream getInputStream() { return new ByteArrayInputStream(processed.data()); }

    @Override
    public void transferTo(java.io.File dest) throws IOException, IllegalStateException {
        try (var out = new java.io.FileOutputStream(dest)) {
            out.write(processed.data());
        }
    }
}
