package com.instakill.infrastructure.config;

import com.instakill.media.infrastructure.FileSystemMediaStorage;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;

@Configuration
public class MediaWebMvcConfig implements WebMvcConfigurer {

    private final FileSystemMediaStorage fileSystemMediaStorage;

    public MediaWebMvcConfig(FileSystemMediaStorage fileSystemMediaStorage) {
        this.fileSystemMediaStorage = fileSystemMediaStorage;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path path = fileSystemMediaStorage.getRootLocation();
        String location = path.toUri().toString();
        if (!location.endsWith("/")) {
            location += "/";
        }
        registry.addResourceHandler("/media/**")
                .addResourceLocations(location);
    }
}
