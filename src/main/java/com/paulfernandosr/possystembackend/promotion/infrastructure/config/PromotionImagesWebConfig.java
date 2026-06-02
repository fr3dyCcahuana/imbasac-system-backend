package com.paulfernandosr.possystembackend.promotion.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

@Configuration
public class PromotionImagesWebConfig implements WebMvcConfigurer {

    @Value("${app.files.promotions-images-dir:uploads/promotions}")
    private String promotionsImagesDir;

    @Value("${app.files.promotions-images-public-path:/images/promotions}")
    private String publicPath;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = Paths.get(promotionsImagesDir)
                .toAbsolutePath()
                .normalize()
                .toUri()
                .toString();

        if (!location.endsWith("/")) location += "/";
        String handler = publicPath.endsWith("/") ? publicPath + "**" : publicPath + "/**";

        registry.addResourceHandler(handler)
                .addResourceLocations(location);
    }
}
