package com.paulfernandosr.possystembackend.promotion.infrastructure.adapter.input;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@Component
public class PromotionImagePublicUrlService {

    @Value("${app.files.promotions-images-public-path:/images/promotions}")
    private String publicPath;

    private String contextPath() {
        return ServletUriComponentsBuilder
                .fromCurrentContextPath()
                .build()
                .toUriString();
    }

    public String toPublicUrl(String storedValue) {
        if (storedValue == null || storedValue.isBlank()) return null;

        // Backward compatible con URLs antiguas
        if (storedValue.startsWith("http://") || storedValue.startsWith("https://")) {
            return storedValue;
        }

        String key = storedValue.replaceFirst("^/+", "");
        String path = publicPath.replaceAll("/$", "") + "/" + key;

        return ServletUriComponentsBuilder
                .fromCurrentContextPath()
                .path(path.startsWith("/") ? path : "/" + path)
                .toUriString();
    }

    public String toStorageKey(String value) {
        if (value == null || value.isBlank()) return null;

        String clean = value.trim();
        String normalizedPublicPath = publicPath.replaceAll("/+$", "");
        String currentContext = contextPath().replaceAll("/+$", "");

        if (clean.startsWith(currentContext + normalizedPublicPath + "/")) {
            return clean.substring((currentContext + normalizedPublicPath + "/").length()).replaceFirst("^/+", "");
        }

        if (clean.startsWith(normalizedPublicPath + "/")) {
            return clean.substring((normalizedPublicPath + "/").length()).replaceFirst("^/+", "");
        }

        if (clean.startsWith("/")) {
            clean = clean.replaceFirst("^/+", "");
            String pathWithoutSlash = normalizedPublicPath.replaceFirst("^/+", "");
            if (clean.startsWith(pathWithoutSlash + "/")) {
                return clean.substring((pathWithoutSlash + "/").length()).replaceFirst("^/+", "");
            }
        }

        return clean;
    }
}
