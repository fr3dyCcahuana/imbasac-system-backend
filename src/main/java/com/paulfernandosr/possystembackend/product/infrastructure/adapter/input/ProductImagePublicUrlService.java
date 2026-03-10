package com.paulfernandosr.possystembackend.product.infrastructure.adapter.input;

import com.paulfernandosr.possystembackend.product.domain.ProductImage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.Collection;

@Component
public class ProductImagePublicUrlService {

    @Value("${app.files.products-images-public-path:/images/products}")
    private String publicPath;

    public String toPublicUrl(String storedValue) {
        if (storedValue == null || storedValue.isBlank()) return null;

        // Backward compatible: si aún existe un registro viejo con http://...
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

    public void enrich(ProductImage img) {
        if (img == null) return;
        img.setImageUrl(toPublicUrl(img.getImageUrl()));
    }

    public void enrich(Collection<ProductImage> images) {
        if (images == null) return;
        for (ProductImage img : images) enrich(img);
    }
}