package com.paulfernandosr.possystembackend.manualpdf.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

@Configuration
public class ManualPdfWebConfig implements WebMvcConfigurer {

    @Value("${app.files.manual-pdfs-dir}")
    private String manualPdfsDir;

    @Value("${app.files.manual-pdfs-public-path:/files/manual-pdfs}")
    private String publicPath;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = Paths.get(manualPdfsDir)
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
