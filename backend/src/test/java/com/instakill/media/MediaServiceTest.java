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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
    void uploadRealImageProcessesBeforeSaving() throws Exception {
        BufferedImage rawImage = new BufferedImage(600, 300, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rawImage.createGraphics();
        g.setColor(Color.RED);
        g.fillRect(0, 0, 600, 300);
        g.dispose();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(rawImage, "png", out);

        MockMultipartFile file = new MockMultipartFile("file", "banner.png", "image/png", out.toByteArray());
        StoredMedia expected = new StoredMedia("uuid.png", "/media/uuid.png");
        when(mediaStorage.store(any(MultipartFile.class))).thenReturn(expected);

        StoredMedia result = mediaService.upload(file);

        assertThat(result).isEqualTo(expected);
        ArgumentCaptor<MultipartFile> storedCaptor = ArgumentCaptor.forClass(MultipartFile.class);
        verify(mediaStorage).store(storedCaptor.capture());

        BufferedImage storedImage = ImageIO.read(new ByteArrayInputStream(storedCaptor.getValue().getBytes()));
        assertThat(storedImage).isNotNull();
        assertThat(storedImage.getWidth()).isEqualTo(300);
        assertThat(storedImage.getHeight()).isEqualTo(150);
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

    @Test
    void loadAsResourceDelegatesToStorage() {
        org.springframework.core.io.Resource mockResource = org.mockito.Mockito.mock(org.springframework.core.io.Resource.class);
        when(mediaStorage.loadAsResource("sample.mp4")).thenReturn(mockResource);

        org.springframework.core.io.Resource result = mediaService.loadAsResource("sample.mp4");

        assertThat(result).isEqualTo(mockResource);
        verify(mediaStorage).loadAsResource("sample.mp4");
    }
}
