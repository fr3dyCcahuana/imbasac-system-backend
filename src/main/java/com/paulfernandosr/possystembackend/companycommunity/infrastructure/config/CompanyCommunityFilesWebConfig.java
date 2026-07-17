package com.paulfernandosr.possystembackend.companycommunity.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

@Configuration
public class CompanyCommunityFilesWebConfig implements WebMvcConfigurer {

    @Value("${app.files.company-community-dir:./storage/company-community}")
    private String companyCommunityDir;

    @Value("${app.files.company-community-public-path:/files/company-community}")
    private String publicPath;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = Paths.get(companyCommunityDir)
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
