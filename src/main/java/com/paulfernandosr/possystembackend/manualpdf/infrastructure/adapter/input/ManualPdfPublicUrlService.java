package com.paulfernandosr.possystembackend.manualpdf.infrastructure.adapter.input;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@Component
public class ManualPdfPublicUrlService {

    @Value("${app.files.manual-pdfs-public-path:/files/manual-pdfs}")
    private String publicPath;

    public String toPublicUrl(String storedValue) {
        if (storedValue == null || storedValue.isBlank()) return null;

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

    public String pdfPreviewUrl(String fileKey) {
        return toPublicUrl(fileKey);
    }

    public String pdfDownloadUrl(String fileKey) {
        return toPublicUrl(fileKey);
    }

    public String imageUrl(String fileKey) {
        return toPublicUrl(fileKey);
    }
}
