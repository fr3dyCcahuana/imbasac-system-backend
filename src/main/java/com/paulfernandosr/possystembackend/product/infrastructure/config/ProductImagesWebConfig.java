package com.paulfernandosr.possystembackend.product.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

@Configuration
public class ProductImagesWebConfig implements WebMvcConfigurer {

    @Value("${app.files.products-images-dir}")
    private String productsImagesDir;

    @Value("${app.files.products-images-public-path:/images/products}")
    private String publicPath;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = Paths.get(productsImagesDir)
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