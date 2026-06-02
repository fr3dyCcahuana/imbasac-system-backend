package com.paulfernandosr.possystembackend.promotion.infrastructure.adapter.input;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@Component
public class PromotionImagePublicUrlService {

    @Value("${app.files.promotions-images-public-path:/images/promotions}")
    private String publicPath;

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
}
