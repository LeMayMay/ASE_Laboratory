package com.instakill.media.domain;

import java.util.Objects;

public record StoredMedia(String filename, String mediaUrl) {
    public StoredMedia {
        Objects.requireNonNull(filename, "filename must not be null");
        Objects.requireNonNull(mediaUrl, "mediaUrl must not be null");
    }
}
