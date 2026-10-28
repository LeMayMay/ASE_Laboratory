package com.instakill.media;

import com.instakill.common.error.BadRequestException;
import com.instakill.media.application.MediaService;
import com.instakill.media.domain.MediaStorage;
import com.instakill.media.domain.MediaValidator;
import com.instakill.media.domain.StoredMedia;
import com.instakill.media.infrastructure.MediaProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MediaServiceTest {

    @Mock
    private MediaStorage mediaStorage;

    private MediaService mediaService;

    @BeforeEach
    void setUp() {
        MediaProperties properties = new MediaProperties("build/test-media", Set.of("jpg", "png", "mp4"));
        MediaValidator validator = new MediaValidator(properties);
        mediaService = new MediaService(mediaStorage, validator);
    }

    @Test
    void uploadValidFileDelegatesToStorage() {
        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", new byte[]{1, 2});
        StoredMedia expected = new StoredMedia("uuid.jpg", "/media/uuid.jpg");

        when(mediaStorage.store(file)).thenReturn(expected);

        StoredMedia result = mediaService.upload(file);

        assertThat(result).isEqualTo(expected);
        verify(mediaStorage).store(file);
    }

    @Test
    void uploadEmptyFileThrowsBadRequest() {
        MockMultipartFile file = new MockMultipartFile("file", "empty.jpg", "image/jpeg", new byte[0]);

        assertThatThrownBy(() -> mediaService.upload(file))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("File must not be empty");
    }

    @Test
    void uploadDisallowedExtensionThrowsBadRequest() {
        MockMultipartFile file = new MockMultipartFile("file", "file.pdf", "application/pdf", new byte[]{1, 2});

        assertThatThrownBy(() -> mediaService.upload(file))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Unsupported or missing file extension");
    }
}
