package com.instakill.infrastructure.config;

import com.instakill.media.infrastructure.MediaProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class MediaWebMvcConfig implements WebMvcConfigurer {

    private final MediaProperties mediaProperties;

    public MediaWebMvcConfig(MediaProperties mediaProperties) {
        this.mediaProperties = mediaProperties;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path path = Paths.get(mediaProperties.basePath()).toAbsolutePath().normalize();
        String location = path.toUri().toString();
        if (!location.endsWith("/")) {
            location += "/";
        }
        registry.addResourceHandler("/media/**")
                .addResourceLocations(location);
    }
}
